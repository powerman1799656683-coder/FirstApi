# Token 用量统计与费用计算设计文档

> 日期：2026-03-21
> 状态：Draft v4（二次落地审核修订）

---

## 1. 背景与现状

系统的 LLM 中继层（Relay）已经能够：
- 从上游 OpenAI / Claude API 响应中提取 `prompt_tokens`、`completion_tokens`、`total_tokens`
- 将 token 数写入 `relay_records` 表

但存在以下缺失：

| 能力 | 现状 |
|------|------|
| 费用计算 | **未实现** — 无模型定价，无计算公式 |
| 管理员记录页 | 硬编码假数据（`RecordsService`） |
| 用户记录页 | 硬编码假数据（`MyRecordsService`） |
| Dashboard 趋势/模型分布 | 返回空列表 |
| OpenAI 非流式 token 解析 | **已实现**（`UpstreamHttpClient.populateUsage()` 自动解析 OpenAI 格式 usage），但未保存 `usageJson` 原始快照 |
| OpenAI 流式 token 解析 | **未实现** — 流式响应未注入 `stream_options`，`populateUsage()` 无法解析 SSE 格式，token 字段为 null |

---

## 2. 计费方式

采用 **直接单价** 方式，每个模型存输入价和输出价（¥/1M tokens），统一使用 **CNY** 币种，和主流中转站定价页一致。

### 核心公式

```
费用(¥) = prompt_tokens × 输入单价 ÷ 1,000,000
         + completion_tokens × 输出单价 ÷ 1,000,000
```

如果有分组倍率（`GroupItem.rate`，类型为 `String`，需转换为 `BigDecimal`），最终计费：

```
最终费用 = 费用 × 分组倍率
```

> **注意**：`GroupItem.rate` 是 `String` 类型。计算时需 `new BigDecimal(rate)` 转换，null/空字符串/<=0/非法格式视为无倍率（不乘）。

### 示例

用户调用 `claude-sonnet-4`，消耗 prompt=1000, completion=500：

```
费用 = 1000 × 21.12 ÷ 1,000,000 + 500 × 105.6 ÷ 1,000,000
     = 0.02112 + 0.0528
     = ¥0.07392
```

### 精度与舍入策略

- 价格与费用高精度存储：`input_price`/`output_price` 至少 `decimal(18,8)`，`cost` 至少 `decimal(20,10)`；展示时再按 2 或 6 位舍入，避免累计误差。
- 统一 CNY 存储，不做多币种换算，无需汇率字段。
- 若上游区分缓存/推理/工具等 token，优先以可计费 usage 口径计算，并保留 `usage_json` 以便后续调整口径。
- 未匹配定价或 usage 缺失时，`cost` 保持 `null`，并写入 `pricing_status` 以区分 `NOT_FOUND`、`USAGE_MISSING` 等状态；`prompt_tokens` 或 `completion_tokens` 任一缺失即视为 `USAGE_MISSING`。若 usage 缺失与定价缺失同时出现，**优先记为 `USAGE_MISSING`**。
- 系统无需默认值：新增字段由服务端显式写入，不依赖数据库默认值。

---

## 3. 数据库变更

### 3.1 新增 `model_pricing` 表

```sql
CREATE TABLE IF NOT EXISTS model_pricing (
    id             bigint        NOT NULL AUTO_INCREMENT,
    model_name     varchar(128)  NOT NULL COMMENT '模型名称，支持尾部 * 通配',
    input_price    decimal(18,8) NOT NULL COMMENT '输入单价（每 1M tokens，CNY）',
    output_price   decimal(18,8) NOT NULL COMMENT '输出单价（每 1M tokens，CNY）',
    currency       varchar(8)    NOT NULL COMMENT '币种，固定 CNY',
    enabled        tinyint(1)    NOT NULL COMMENT '是否启用',
    effective_from datetime      NOT NULL COMMENT '生效时间',
    created_at     datetime      NOT NULL COMMENT '创建时间（服务端显式写入）',
    updated_at     datetime      NOT NULL COMMENT '更新时间（服务端显式写入）',
    PRIMARY KEY (`id`),
    KEY `idx_model_pricing_name` (`model_name`),
    KEY `idx_model_pricing_enabled` (`enabled`),
    UNIQUE KEY `uk_model_pricing_name_effective` (`model_name`, `effective_from`)
);
```

**匹配规则**（Java 层实现，非 SQL）：
> 当前仅按 `model_name` 匹配，默认跨 `provider` 不冲突；如未来发生冲突，扩展 `model_pricing` 增加 `provider_name`，并按 `provider_name + model_name` 优先匹配。
1. 查询所有 `enabled=1` 且 `effective_from <= now` 的记录，加载到内存缓存
2. **精确匹配优先**：`model_name` 完全等于请求模型名
3. **通配匹配**：仅当 `model_name` 以 `*` 结尾时走通配，匹配逻辑为 `requestModel.startsWith(modelName.substring(0, modelName.length() - 1))`，最长前缀优先
4. 同一 `model_name` 若有多条记录，按 `effective_from DESC` 取最近生效的一条
5. 未匹配 → `pricing_status=NOT_FOUND`，费用为 null，不阻断请求

**预置数据**（价格统一按 CNY 计，USD→CNY 汇率按 7.04 换算，公式：`CNY = USD × 7.04`，单位均为每 1M tokens）：

| model_name | 输入单价(每1M) | 输出单价(每1M) | 币种 | 生效时间 | 来源/说明 |
|------------|---------------|---------------|------|----------|-----------|
| claude-3-5-haiku-* | 5.632 | 35.200 | CNY | 2026-03-21 | Claude 3.5 Haiku ($0.8/$5 per 1M → ×7.04) |
| claude-sonnet-4-* | 21.120 | 105.600 | CNY | 2026-03-21 | Claude Sonnet 4 ($3/$15 per 1M → ×7.04) |
| gpt-4o | 17.600 | 70.400 | CNY | 2026-03-21 | $2.5/$10 per 1M → ×7.04 |
| gpt-4o-mini | 1.056 | 4.224 | CNY | 2026-03-21 | $0.15/$0.6 per 1M → ×7.04 |
| gpt-4.1 | 14.080 | 56.320 | CNY | 2026-03-21 | $2/$8 per 1M → ×7.04 |
| gpt-4.1-mini | 2.816 | 11.264 | CNY | 2026-03-21 | $0.4/$1.6 per 1M → ×7.04 |
| gpt-4.1-nano | 0.704 | 2.816 | CNY | 2026-03-21 | $0.1/$0.4 per 1M → ×7.04 |
| o3 | 14.080 | 56.320 | CNY | 2026-03-21 | $2/$8 per 1M → ×7.04 |
| o3-mini | 7.744 | 30.976 | CNY | 2026-03-21 | $1.1/$4.4 per 1M → ×7.04 |
| o4-mini | 7.744 | 30.976 | CNY | 2026-03-21 | $1.1/$4.4 per 1M → ×7.04 |

**待确认（先不预置或置为 disabled）**：
- `claude-opus-4-*`
- `claude-opus-4-6-*`

> **注意**：Claude 系列使用尾部 `*` 通配以覆盖带日期后缀的版本号（如 `claude-sonnet-4-20260321`）。OpenAI 模型当前使用精确匹配，如后续出现日期后缀版本，可追加通配规则。以上价格需在上线前与官方最新定价核实。
> 时间字段（`effective_from` / `created_at` / `updated_at`）统一按 Asia/Shanghai 时区写入与解释；API 传输建议使用带时区的 ISO-8601 字符串。

### 3.2 `relay_records` 表扩展

> **迁移策略说明**：项目使用 `spring.sql.init.mode=always`，`schema.sql` 每次启动都会执行。`CREATE TABLE IF NOT EXISTS` 是幂等的，但 `ALTER TABLE ADD COLUMN` 不是——列已存在时会报错，导致应用启动失败。因此采用以下方案：
>
> 1. **schema.sql**：将新字段直接写入 `CREATE TABLE IF NOT EXISTS relay_records` 的完整定义中（新部署自动包含所有列）
> 2. **一次性迁移脚本**：提供独立的 `migration-v2-pricing.sql`，用于已有环境的 ALTER TABLE + 回填 + 索引创建，**手动执行一次**，不放入 schema.sql

#### 3.2.1 schema.sql 中的完整表定义（替换原 `relay_records` 建表语句）

```sql
create table if not exists `relay_records` (
    `id`                bigint not null auto_increment,
    `owner_id`          bigint not null,
    `api_key_id`        bigint not null,
    `provider_name`     varchar(32) not null,
    `account_id`        bigint not null,
    `model_name`        varchar(255) not null,
    `request_id`        varchar(128) null,
    `success`           tinyint(1) not null,
    `status_code`       int not null,
    `error_text`        text null,
    `latency_ms`        bigint not null,
    `prompt_tokens`     int null,
    `completion_tokens` int null,
    `total_tokens`      int null,
    `input_price`       decimal(18,8) null comment '输入单价快照（每 1M tokens，CNY）',
    `output_price`      decimal(18,8) null comment '输出单价快照（每 1M tokens，CNY）',
    `pricing_currency`  varchar(8) null comment '币种快照（当前固定 CNY）',
    `group_ratio`       decimal(18,8) null comment '分组倍率快照',
    `pricing_rule_id`   bigint null comment '命中定价规则ID',
    `pricing_rule_name` varchar(128) null comment '命中定价规则名（model_name）',
    `pricing_status`    varchar(32) null comment 'MATCHED/NOT_FOUND/USAGE_MISSING',
    `pricing_found`     tinyint(1) null comment '是否命中定价规则（辅助字段，解决 USAGE_MISSING 遮蔽 NOT_FOUND）',
    `cost`              decimal(20,10) null comment '最终费用（CNY）',
    `usage_json`        json null comment '上游 usage 原始快照',
    `created_at`        varchar(64) null,
    `created_at_ts`     datetime null comment '统计用时间字段',
    `updated_at`        timestamp not null default current_timestamp on update current_timestamp,
    primary key (`id`),
    key `idx_relay_records_created_at_ts` (`created_at_ts`),
    key `idx_relay_records_owner_created_at_ts` (`owner_id`, `created_at_ts`),
    key `idx_relay_records_model_created_at_ts` (`model_name`, `created_at_ts`)
) engine=InnoDB default charset=utf8mb4;
```

#### 3.2.2 一次性迁移脚本 `migration-v2-pricing.sql`（已有环境手动执行）

```sql
-- 1. 新增定价与统计字段（逐列 ADD，忽略已存在的列）
-- MySQL 不支持 IF NOT EXISTS 语法的 ADD COLUMN，需逐条执行，已存在则跳过报错即可

ALTER TABLE relay_records ADD COLUMN input_price        decimal(18,8) NULL COMMENT '输入单价快照（每 1M tokens，CNY）' AFTER total_tokens;
ALTER TABLE relay_records ADD COLUMN output_price       decimal(18,8) NULL COMMENT '输出单价快照（每 1M tokens，CNY）' AFTER input_price;
ALTER TABLE relay_records ADD COLUMN pricing_currency   varchar(8)    NULL COMMENT '币种快照（当前固定 CNY）' AFTER output_price;
ALTER TABLE relay_records ADD COLUMN group_ratio        decimal(18,8) NULL COMMENT '分组倍率快照' AFTER pricing_currency;
ALTER TABLE relay_records ADD COLUMN pricing_rule_id    bigint        NULL COMMENT '命中定价规则ID' AFTER group_ratio;
ALTER TABLE relay_records ADD COLUMN pricing_rule_name  varchar(128)  NULL COMMENT '命中定价规则名（model_name）' AFTER pricing_rule_id;
ALTER TABLE relay_records ADD COLUMN pricing_status     varchar(32)   NULL COMMENT 'MATCHED/NOT_FOUND/USAGE_MISSING' AFTER pricing_rule_name;
ALTER TABLE relay_records ADD COLUMN pricing_found      tinyint(1)    NULL COMMENT '是否命中定价规则' AFTER pricing_status;
ALTER TABLE relay_records ADD COLUMN cost               decimal(20,10) NULL COMMENT '最终费用（CNY）' AFTER pricing_found;
ALTER TABLE relay_records ADD COLUMN usage_json         json          NULL COMMENT '上游 usage 原始快照' AFTER cost;
ALTER TABLE relay_records ADD COLUMN created_at_ts      datetime      NULL COMMENT '统计用时间字段' AFTER created_at;

-- 2. 回填 created_at_ts（分批执行，每批 5000 条，避免长事务锁表）
-- 格式：yyyy/MM/dd HH:mm:ss（TimeSupport.nowDateTime() 生成，时区 Asia/Shanghai）
-- 注意：REPEAT/UNTIL 仅在存储过程内可用，此处用存储过程包装

DELIMITER $$
DROP PROCEDURE IF EXISTS backfill_created_at_ts$$
CREATE PROCEDURE backfill_created_at_ts()
BEGIN
    DECLARE affected INT DEFAULT 1;
    WHILE affected > 0 DO
        UPDATE relay_records
        SET created_at_ts = STR_TO_DATE(created_at, '%Y/%m/%d %H:%i:%s')
        WHERE created_at IS NOT NULL AND created_at_ts IS NULL
        LIMIT 5000;
        SET affected = ROW_COUNT();
    END WHILE;
END$$
DELIMITER ;

CALL backfill_created_at_ts();
DROP PROCEDURE IF EXISTS backfill_created_at_ts;

-- 3. 索引（IF NOT EXISTS 需 MySQL 8.0.29+，低版本去掉 IF NOT EXISTS 手动检查）
CREATE INDEX IF NOT EXISTS idx_relay_records_created_at_ts ON relay_records (created_at_ts);
CREATE INDEX IF NOT EXISTS idx_relay_records_owner_created_at_ts ON relay_records (owner_id, created_at_ts);
CREATE INDEX IF NOT EXISTS idx_relay_records_model_created_at_ts ON relay_records (model_name, created_at_ts);
```

> 快照单价与倍率到记录中，后续修改定价不影响历史记录；`pricing_status` 用于区分未定价或 usage 缺失。
>
> **注意**：原表 `updated_at` 列使用了 `default current_timestamp on update current_timestamp`，与"系统无需默认值"原则不一致。本次不做修改，后续可考虑统一。
>
> **`created_at_ts` 收敛计划**：`created_at`（varchar）与 `created_at_ts`（datetime）双写是过渡方案。待所有查询和展示迁移到 `created_at_ts` 后，可将 `created_at` 标记为废弃，最终在后续版本中移除。新记录在 `RelayRecordService` 中同时写入两个字段。

---

## 4. 后端服务设计

### 4.1 PricingStatus 枚举

```java
public enum PricingStatus {
    MATCHED,        // 成功匹配定价规则
    NOT_FOUND,      // 未找到匹配的定价规则
    USAGE_MISSING   // 上游未返回 token 用量
}
```

> 存入数据库时使用 `name()` 字符串；读取时建议使用 `PricingStatus.valueOf()` 并对未知值兜底（避免抛异常），如回落到 `NOT_FOUND` 或 `USAGE_MISSING`。

### 4.2 ModelPricingItem + ModelPricingRepository

```java
public class ModelPricingItem {
    private Long id;
    private String modelName;
    private BigDecimal inputPrice;
    private BigDecimal outputPrice;
    private String currency;
    private Boolean enabled;
    private LocalDateTime effectiveFrom;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
```

### 4.2.1 RelayRecordItem 模型类扩展

现有 `RelayRecordItem` 需要新增以下字段（getter/setter 省略）：

```java
// --- 以下为新增字段 ---
private BigDecimal inputPrice;       // 输入单价快照
private BigDecimal outputPrice;      // 输出单价快照
private String pricingCurrency;      // 币种快照
private BigDecimal groupRatio;       // 分组倍率快照
private Long pricingRuleId;          // 命中定价规则 ID
private String pricingRuleName;      // 命中定价规则名
private String pricingStatus;        // MATCHED / NOT_FOUND / USAGE_MISSING
private Boolean pricingFound;        // 是否命中定价规则（辅助字段）
private BigDecimal cost;             // 最终费用（CNY）
private String usageJson;            // 上游 usage 原始 JSON
private LocalDateTime createdAtTs;   // 统计用时间字段
```

### 4.2.2 RelayRecordRepository 映射变更

`toColumnValues()` 新增列映射（Java 字段名 → DB 列名）：

| Java 字段 | DB 列名 |
|-----------|---------|
| `inputPrice` | `input_price` |
| `outputPrice` | `output_price` |
| `pricingCurrency` | `pricing_currency` |
| `groupRatio` | `group_ratio` |
| `pricingRuleId` | `pricing_rule_id` |
| `pricingRuleName` | `pricing_rule_name` |
| `pricingStatus` | `pricing_status` |
| `pricingFound` | `pricing_found` |
| `cost` | `cost` |
| `usageJson` | `usage_json` |
| `createdAtTs` | `created_at_ts` |

`ROW_MAPPER` 需同步扩展，对 `BigDecimal` 类型使用 `rs.getBigDecimal()`（自动处理 null），对 `LocalDateTime` 使用 `rs.getObject("created_at_ts", LocalDateTime.class)`。`usage_json` 读取为 `rs.getString("usage_json")`（JSON 列在 JDBC 中当字符串处理）。

### 4.3 CostCalculationService

```java
@Service
public class CostCalculationService {

    private static final BigDecimal ONE_MILLION = BigDecimal.valueOf(1_000_000);

    private final ModelPricingRepository pricingRepository;

    // 内存缓存：启动时加载，增删改时刷新
    private volatile List<ModelPricingItem> pricingCache;

    @PostConstruct
    public void refreshCache() {
        this.pricingCache = pricingRepository.findAllEnabledEffective();
    }

    /**
     * 定时刷新缓存（每 5 分钟），确保未来生效的定价到期后自动加载。
     * BackendApplication 已有 @EnableScheduling 注解，无需重复添加。
     */
    @Scheduled(fixedRate = 5 * 60 * 1000)
    public void scheduledRefresh() {
        refreshCache();
    }

    /**
     * 计算费用（CNY）。
     * 先完成全部乘加运算，最后统一除以 1M 并舍入，避免中间步骤多次舍入导致精度损失。
     */
    public BigDecimal calculate(BigDecimal inputPrice, BigDecimal outputPrice,
                                Integer promptTokens, Integer completionTokens,
                                BigDecimal groupRatio) {
        if (inputPrice == null || outputPrice == null) {
            return null;
        }
        if (promptTokens == null || completionTokens == null) {
            return null;
        }

        // 先算分子：in × inputPrice + out × outputPrice（无舍入）
        BigDecimal raw = BigDecimal.valueOf(promptTokens).multiply(inputPrice)
                .add(BigDecimal.valueOf(completionTokens).multiply(outputPrice));

        // 统一除以 1M
        BigDecimal cost = raw.divide(ONE_MILLION, 10, HALF_UP);

        if (groupRatio != null) {
            cost = cost.multiply(groupRatio).setScale(10, HALF_UP);
        }
        return cost;
    }

    /**
     * 匹配定价规则（从内存缓存中查找）。
     *
     * 匹配优先级：
     * 1. 精确匹配（model_name == requestModel）
     * 2. 通配匹配（model_name 以 * 结尾，requestModel 以去掉 * 的前缀开头，最长前缀优先）
     * 3. 同名多条取 effective_from 最近的
     * 4. 无匹配返回 null
     */
    public ModelPricingItem matchPricing(String requestModel) {
        if (requestModel == null || pricingCache == null) return null;

        ModelPricingItem exactMatch = null;
        ModelPricingItem wildcardMatch = null;
        int longestPrefix = -1;

        for (ModelPricingItem item : pricingCache) {
            String name = item.getModelName();
            if (name.equals(requestModel)) {
                // 精确匹配：取 effectiveFrom 最近的
                if (exactMatch == null || item.getEffectiveFrom().isAfter(exactMatch.getEffectiveFrom())) {
                    exactMatch = item;
                }
            } else if (name.endsWith("*")) {
                String prefix = name.substring(0, name.length() - 1);
                if (requestModel.startsWith(prefix)) {
                    if (prefix.length() > longestPrefix
                        || (prefix.length() == longestPrefix
                            && wildcardMatch != null
                            && item.getEffectiveFrom().isAfter(wildcardMatch.getEffectiveFrom()))) {
                        longestPrefix = prefix.length();
                        wildcardMatch = item;
                    }
                }
            }
        }
        return exactMatch != null ? exactMatch : wildcardMatch;
    }
}
```

> 缓存策略：定价数据量小且变更不频繁。`findAllEnabledEffective()` 只查询 `enabled=1 AND effective_from <= now` 的记录，缓存中不含未来生效价。三级刷新机制：
> 1. **启动时**：`@PostConstruct` 加载
> 2. **管理员操作时**：增删改定价后调用 `refreshCache()` 即时刷新
> 3. **定时刷新**：`@Scheduled(fixedRate = 5min)` 自动刷新，确保未来生效的定价到期后自动加载
>
> `BackendApplication` 已有 `@EnableScheduling` 注解，无需额外添加。如多实例部署，定时刷新已能保证各实例在 5 分钟内收敛一致。

### 4.4 RelayRecordService 改造

现有方法签名：`record(ApiKeyItem apiKey, RelayRoute route, RelayResult result, String model)`

改造后：

```java
public void record(ApiKeyItem apiKey, RelayRoute route, RelayResult result,
                   String model, GroupItem group) {
    // 从 GroupItem.rate（String）转换分组倍率
    BigDecimal groupRatio = null;
    if (group != null && group.getRate() != null && !group.getRate().isBlank()) {
        try {
            groupRatio = new BigDecimal(group.getRate().trim());
            if (groupRatio.compareTo(BigDecimal.ZERO) <= 0) {
                log.warn("分组倍率非正数，视为无倍率: groupId={}, rate={}",
                        group.getId(), group.getRate());
                groupRatio = null;
            }
        } catch (NumberFormatException e) {
            log.warn("分组倍率格式非法，视为无倍率: groupId={}, rate={}",
                    group.getId(), group.getRate());
        }
    }

    ModelPricingItem pricing = costService.matchPricing(model);
    boolean usageMissing = result.getPromptTokens() == null || result.getCompletionTokens() == null;

    PricingStatus pricingStatus;
    if (usageMissing) {
        pricingStatus = PricingStatus.USAGE_MISSING;
    } else if (pricing == null) {
        pricingStatus = PricingStatus.NOT_FOUND;
    } else {
        pricingStatus = PricingStatus.MATCHED;
    }

    // pricing_found 辅助字段：即使 pricingStatus 为 USAGE_MISSING，
    // 也能区分"有定价但缺 usage"与"定价和 usage 都缺失"
    boolean pricingFound = pricing != null;

    RelayRecordItem item = new RelayRecordItem();
    item.setOwnerId(apiKey.getOwnerId());
    item.setApiKeyId(apiKey.getId());
    item.setProvider(route.getProvider());        // 映射到 DB 列 provider_name
    item.setAccountId(result.getAccountId());
    item.setModel(model);                         // 映射到 DB 列 model_name
    item.setRequestId(result.getRequestId());
    item.setSuccess(result.isSuccess());
    item.setStatusCode(result.getStatusCode());
    item.setLatencyMs(result.getLatencyMs());
    item.setPromptTokens(result.getPromptTokens());
    item.setCompletionTokens(result.getCompletionTokens());
    item.setTotalTokens(result.getTotalTokens());
    item.setCreatedAt(TimeSupport.nowDateTime());
    item.setCreatedAtTs(LocalDateTime.now(ZoneId.of("Asia/Shanghai")));
    if (!result.isSuccess()) {
        item.setErrorText(result.getBody());
    }

    // 定价快照
    item.setInputPrice(pricing != null ? pricing.getInputPrice() : null);
    item.setOutputPrice(pricing != null ? pricing.getOutputPrice() : null);
    item.setPricingCurrency(pricing != null ? pricing.getCurrency() : null);
    item.setPricingRuleId(pricing != null ? pricing.getId() : null);
    item.setPricingRuleName(pricing != null ? pricing.getModelName() : null);
    item.setPricingStatus(pricingStatus.name());
    item.setPricingFound(pricingFound);
    item.setGroupRatio(groupRatio);
    item.setCost(pricingStatus == PricingStatus.MATCHED
        ? costService.calculate(pricing.getInputPrice(), pricing.getOutputPrice(),
                result.getPromptTokens(), result.getCompletionTokens(), groupRatio)
        : null);

    // usage_json 快照
    item.setUsageJson(result.getUsageJson());

    relayRecordRepository.save(item);
}
```

> **调用方变更**：`RelayService` 中已经 `resolveGroup(apiKey)` 拿到了 `GroupItem group`，只需在调用 `record()` 时传入即可：
> ```java
> // RelayService.relayChatCompletion() 和 relayResponsesApi() 中：
> GroupItem group = resolveGroup(apiKey);
> // ... 原有逻辑 ...
> relayRecordService.record(apiKey, route, result, model, group);
> ```

> **字段映射说明**：`RelayRecordItem` 中 `provider` / `model` 字段在 `RelayRecordRepository` 中映射到 DB 列 `provider_name` / `model_name`（通过 `toColumnValues()` 和 `ROW_MAPPER` 完成映射）。

#### 4.4.1 RelayResult 新增 usageJson 字段

`RelayResult` 新增字段用于承载上游 usage 原始 JSON：

```java
private String usageJson;  // 上游 usage 原始 JSON 快照

public String getUsageJson() { return usageJson; }
public void setUsageJson(String usageJson) { this.usageJson = usageJson; }
```

各层在解析 token 时同步保存原始 usage JSON：

- **UpstreamHttpClient.populateUsage()**：在已有 token 提取代码处补充 `result.setUsageJson(usageNode.toString())`（覆盖 OpenAI 非流式路径，见 Section 5.1）
- **ClaudeRelayAdapter（非流式）**：在已有 token 解析代码处，增加 `upstream.setUsageJson(openAiResponse.path("usage").toString())`（会覆盖 `populateUsage()` 设置的值，因为 Claude 响应格式不同）
- **ClaudeRelayAdapter（流式）**：在 `message_delta` 事件解析处，保存 `payload.path("usage").toString()`
- **OpenAI 流式**：从末尾 `usage` 数据块保存原始 JSON（见 Section 5.2，Phase 6 实现）

### 4.5 RelayRecordRepository 新增聚合查询

```java
BigDecimal sumCost();                               // 全局总费用
long sumTotalTokens();                              // 全局总 token
long countDistinctApiKeys();                        // 活跃 key 数
double avgLatencyMs();                              // 平均延迟
List<ModelStat> groupByModel();                     // 按模型聚合
List<DayStat> groupByDate(int days);                // 按日期聚合
long countByPricingStatus(String status);           // 按定价状态统计

BigDecimal sumCostByOwner(Long ownerId);            // 用户总费用
long sumTotalTokensByOwner(Long ownerId);
double avgLatencyMsByOwner(Long ownerId);
```

#### 聚合结果类型定义

```java
// 替代 Map<String,Object>，提供类型安全
public record ModelStat(String modelName, long callCount, long totalTokens, BigDecimal totalCost) {}
public record DayStat(String date, long callCount, long totalTokens, BigDecimal totalCost) {}
```

#### SQL 实现

```sql
-- sumCost()
SELECT COALESCE(SUM(cost), 0) FROM relay_records;

-- sumTotalTokens()
SELECT COALESCE(SUM(total_tokens), 0) FROM relay_records;

-- countDistinctApiKeys()
SELECT COUNT(DISTINCT api_key_id) FROM relay_records;

-- avgLatencyMs()
SELECT COALESCE(AVG(latency_ms), 0) FROM relay_records;

-- groupByModel()
SELECT model_name, COUNT(*) AS call_count,
       COALESCE(SUM(total_tokens), 0) AS total_tokens,
       COALESCE(SUM(cost), 0) AS total_cost
FROM relay_records
GROUP BY model_name
ORDER BY call_count DESC;

-- groupByDate(days) — 最近 N 天，按 created_at_ts 聚合
SELECT DATE(created_at_ts) AS stat_date, COUNT(*) AS call_count,
       COALESCE(SUM(total_tokens), 0) AS total_tokens,
       COALESCE(SUM(cost), 0) AS total_cost
FROM relay_records
WHERE created_at_ts >= DATE_SUB(NOW(), INTERVAL ? DAY)
GROUP BY DATE(created_at_ts)
ORDER BY stat_date ASC;

-- countByPricingStatus(status)
SELECT COUNT(*) FROM relay_records WHERE pricing_status = ?;

-- sumCostByOwner(ownerId)
SELECT COALESCE(SUM(cost), 0) FROM relay_records WHERE owner_id = ?;

-- sumTotalTokensByOwner(ownerId)
SELECT COALESCE(SUM(total_tokens), 0) FROM relay_records WHERE owner_id = ?;

-- avgLatencyMsByOwner(ownerId)
SELECT COALESCE(AVG(latency_ms), 0) FROM relay_records WHERE owner_id = ?;
```

### 4.6 RecordsService / MyRecordsService / DashboardService

替换硬编码，改为查询 `relay_records` 表真实数据。

---

## 5. OpenAI Token 解析（usageJson 补充 + 流式）

> **现状**：`UpstreamHttpClient.populateUsage()` 已在 `postJson()` 中自动解析 OpenAI 格式的 `usage.prompt_tokens`/`completion_tokens`/`total_tokens`，因此 **OpenAI 非流式请求的 token 已正确提取**。但流式请求的 SSE 响应体无法被 `populateUsage()` 解析为 JSON，token 字段为 null。
>
> 另外，`populateUsage()` 仅提取 token 计数，未保存 `usageJson` 原始快照。

### 5.1 非流式 usageJson 保存（P0，Phase 2）

Token 提取已由 `UpstreamHttpClient.populateUsage()` 完成，**无需重复实现**。只需在 `populateUsage()` 中补充 `usageJson` 的保存：

```java
// UpstreamHttpClient.populateUsage() 中，现有 token 提取代码之后补充：
JsonNode usageNode = root.path("usage");
if (!usageNode.isMissingNode()) {
    result.setUsageJson(usageNode.toString());
}
```

> **注意**：`populateUsage()` 已有 try-catch 静默处理异常，`usageJson` 保存失败不会影响请求流程。

### 5.2 流式 Token 解析（P1，Phase 6）

1. 请求中注入 `"stream_options": { "include_usage": true }`
2. 在 `OpenAiRelayAdapter` 的 SSE 事件处理中，识别末尾包含 `usage` 的数据块（`choices` 为空数组的 chunk）
3. 提取 `prompt_tokens`、`completion_tokens` 设置到 `RelayResult`，同时保存 `usageJson`
4. 如流式中断或缺少 `usage`，记录 `pricing_status=USAGE_MISSING`，不阻断请求
5. 部分上游可能不支持 `stream_options`（返回 400），需在发送前检测并回退（不注入该字段），此时记录 `USAGE_MISSING` 并写入日志

---

## 6. 管理员定价管理页面

新增 `/admin/model-pricing` 页面。

### 6.1 后端 API

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/admin/model-pricing` | 列表（支持搜索） |
| POST | `/api/admin/model-pricing` | 新增（完成后调用 `costService.refreshCache()`） |
| PUT | `/api/admin/model-pricing/{id}` | 修改（完成后调用 `costService.refreshCache()`） |
| DELETE | `/api/admin/model-pricing/{id}` | 删除（完成后调用 `costService.refreshCache()`） |

### 6.2 前端页面

```
┌──────────────────────────────────────────────────────────┐
│  模型定价管理                                  [+ 新增]  │
├──────────────────────────────────────────────────────────┤
│  搜索框                                                  │
├──────────────────────────────────────────────────────────┤
│  ┌──────────────────┬───────────┬───────────┬───────┬──────────┬──────────┐ │
│  │ 模型              │ 输入/1M   │ 输出/1M   │ 币种  │ 生效时间 │ 操作     │ │
│  ├──────────────────┼───────────┼───────────┼───────┼──────────┼──────────┤ │
│  │ claude-sonnet-4-*│ 21.120    │ 105.600   │ CNY   │ 2026-03-21 │ 编辑 删除│ │
│  │ claude-3-5-haiku-*│ 5.632   │ 35.200    │ CNY   │ 2026-03-21 │ 编辑 删除│ │
│  │ gpt-4o           │ 17.600   │ 70.400    │ CNY   │ 2026-03-21 │ 编辑 删除│ │
│  └──────────────────┴───────────┴───────────┴───────┴──────────┴──────────┘ │
└──────────────────────────────────────────────────────────┘
```

**弹窗字段**：模型名称、输入单价、输出单价、币种、生效时间、启用状态（不设默认值；点击空白区域不自动关闭弹窗）

**布局要求**：搜索框与新增按钮不重叠，保持固定间距与对齐。

### 6.3 路由

`App.jsx` 添加 `/admin/model-pricing`，`Layout.jsx` 侧边栏添加「模型定价」。

---

## 7. 数据流总览

```
请求到达
  │
  ├─ 认证 + 路由 + 选账户
  │
  ├─ RelayService.resolveGroup(apiKey) → GroupItem（含 rate）
  │
  ├─ 上游调用 → 提取 prompt_tokens / completion_tokens
  │
  ├─ CostCalculationService.matchPricing(model)  [内存缓存]
  │       → input_price, output_price, currency, pricing_status
  │
  ├─ 费用 = in × input_price / 1M + out × output_price / 1M
  │       × groupRatio（高精度存储）
  │
  └─ 写入 relay_records（含定价快照 + pricing_status + cost + usage_json）
          → 管理员/用户页面查询展示
```

---

## 8. 前端适配

> **现状**：当前 `App.jsx` 中 `/records` 和 `/dashboard` 均重定向到 `/monitor/accounts`，没有独立页面。需要恢复路由指向实际组件。

### 8.1 路由变更（App.jsx）

| 路由 | 当前行为 | 改造后 |
|------|---------|--------|
| `/records` | `Navigate → /monitor/accounts` | 指向 `Records.jsx`（管理员用量记录页） |
| `/dashboard` | `Navigate → /monitor/accounts` | 指向 `Dashboard.jsx`（管理员 Dashboard） |
| `/my-records` | 指向 `MyRecords.jsx` | 不变，改造为真实数据 |
| `/admin/model-pricing` | 不存在 | 新增，指向 `ModelPricing.jsx`（Section 6） |

### 8.2 侧边栏变更（Layout.jsx）

管理员菜单 `adminMenu` 新增：
- 「调用记录」→ `/records`
- 「模型定价」→ `/admin/model-pricing`

### 8.3 页面改造

- **Records.jsx**：移除硬编码假数据，改为调用 `/api/admin/records` 获取真实数据；展示费用（¥）、token 消耗、`pricing_status` 状态；统计卡片（总费用、总 token、活跃 key 数、平均延迟）从聚合 API 获取
- **MyRecords.jsx**：移除硬编码假数据，改为调用 `/api/my-records` 获取当前用户记录；展示个人费用和 token 消耗
- **Dashboard.jsx**：`modelDistribution` 和 `trends` 改为从聚合 API 获取真实数据（按模型分布、按日趋势）
- 费用为 `null` 时显示为「未定价」或「usage 缺失」（根据 `pricing_status` 区分），并支持按 `pricing_status` 筛选

---

## 9. 实施阶段

| 阶段 | 内容 | 优先级 |
|------|------|--------|
| **Phase 1** | schema 变更：`model_pricing` 建表（schema.sql）+ `relay_records` 完整表定义更新（schema.sql）+ 已有环境执行 `migration-v2-pricing.sql`（ALTER + 回填 + 索引） | P0 |
| **Phase 2** | `PricingStatus` 枚举 + `RelayRecordItem` 模型扩展 + `RelayRecordRepository` 映射扩展 + `CostCalculationService`（含缓存 + 定时刷新）+ `RelayRecordService` 改造 + `RelayService` 调用方适配 + `RelayResult.usageJson` 字段 + `UpstreamHttpClient.populateUsage()` 补充 usageJson 保存 + ClaudeRelayAdapter 补充 usageJson 保存 | P0 |
| **Phase 3** | 模型定价管理页面（后端 CRUD + 缓存刷新 + 前端 `ModelPricing.jsx`） | P0 |
| **Phase 4** | `RecordsService` / `MyRecordsService` / `DashboardService` 替换硬编码 + 聚合查询方法实现 | P0 |
| **Phase 5** | 前端路由恢复（`/records`、`/dashboard`）+ 侧边栏更新 + `Records.jsx` / `MyRecords.jsx` / `Dashboard.jsx` 适配真实数据 | P0 |
| **Phase 6** | OpenAI 流式 token 解析（`stream_options` 注入 + SSE 末尾 usage 提取 + 兼容性回退） | P1 |

---

## 10. 生产落地风险与治理建议

### 10.1 P0 风险（上线前必须处理）

- 定价与汇率硬编码：当前按 `USD×7.04` 与预置价格计算，若官方调价会直接导致计费错误；上线前必须核对最新价格，并建立可更新机制（后台可配置或定期同步）。
- ~~定价缓存刷新~~：**已修复** — 增加 `@Scheduled(fixedRate = 5min)` 定时刷新。
- 大表变更与回填风险：`relay_records` 的 `ALTER TABLE`、回填 `created_at_ts`、新增索引在大表上可能锁表；迁移脚本已提供分批回填方案，建议离峰执行。

### 10.2 P1 风险（建议上线前处理）

- 仅按 `model_name` 匹配：跨 `provider` 的同名模型可能冲突，建议提前引入 `provider_name` 维度或统一命名规则。
- ~~`pricing_status` 单值遮蔽~~：**已修复** — 新增 `pricing_found` 辅助字段，即使 `pricing_status=USAGE_MISSING` 也能通过 `pricing_found=false` 区分根因。
- ~~`GroupItem.rate` 解析静默失败~~：**已修复** — 增加 `trim()` 和 `log.warn` 告警日志。
- 时区一致性：`effective_from` 与统计依赖 Asia/Shanghai，需确保应用、数据库会话与持久化策略一致。

### 10.3 P2 改进项（不影响上线）

- ~~`calculate()` 中多次舍入~~：**已修复** — 改为先做完乘加运算再统一除以 1M，仅一次 `divide` 舍入。
- OpenAI 流式 `include_usage` 支持度不一致时应记录日志并明确回退策略（Section 5.2 已补充兼容性回退方案）。

### 10.4 测试补充建议

- 定价匹配优先级：精确匹配、通配最长前缀、`effective_from` 最近生效。
- `pricing_status` 优先级：`USAGE_MISSING` > `NOT_FOUND` 的路径覆盖。
- 费率解析：`rate` 的空值、空白、非法格式、<=0。
- 成本精度与舍入：边界 token 与大数值场景。
- `created_at_ts` 回填正确性与按日聚合统计准确性。

### 10.5 需固化的策略

- 计费口径是否以“请求发生时的价格快照”为准，并在记录中保留价格与汇率来源，便于对账与审计。

---

## 附录 A：审核修订记录

### v2（Draft 已审核修订）

1. **字段映射说明**：`RelayRecordItem.provider`/`model` 在 Repository 中映射到 DB 列 `provider_name`/`model_name`，已在代码示例中加注释标注
2. **GroupItem.rate 类型**：从隐式当 `BigDecimal` 用改为显式 `String→BigDecimal` 转换，含 null/空值/格式异常处理
3. **created_at 回填**：明确格式为 `yyyy/MM/dd HH:mm:ss`（`TimeSupport.nowDateTime()`），给出 `STR_TO_DATE` 回填 SQL，新记录同时写入 `created_at` 和 `created_at_ts`
4. **去掉 fx_rate/fx_rate_at**：统一 CNY 存储，当前无多币种需求，移除无用列简化实现
5. **record() 签名**：改为接收 `GroupItem group`（而非 `BigDecimal groupRatio`），倍率转换在 `record()` 内完成；明确了 `RelayService` 中 `group` 的传入路径
6. **预置价格**：统一标注 USD 原价和 CNY 换算依据（汇率 7.04），Claude 模型标注需与官方最新定价核实
7. **通配符匹配**：给出完整 Java 实现，明确 `endsWith("*")` + `startsWith(prefix)` + 最长前缀优先的算法
8. **缓存策略**：`matchPricing()` 改为从内存缓存查找，`@PostConstruct` 加载 + 增删改时 `refreshCache()`
9. **PricingStatus 枚举**：新增枚举类替代散落的字符串常量
10. **updated_at 默认值**：标注现有表的 `default current_timestamp` 与"无默认值"原则的不一致，本次不改，后续统一
11. **USD→CNY 换算与单价单位**：统一按 `CNY = USD × 7.04`，单位均为每 1M tokens，并修正示例
12. **pricing_status 优先级**：`USAGE_MISSING` 优先于 `NOT_FOUND`，避免同时缺失时状态含混
13. **时间时区说明**：`effective_from`/`created_at`/`updated_at` 统一按 Asia/Shanghai 解释
14. **分组倍率校验**：倍率仅允许正数，非法或 <=0 视为无倍率

### v3（落地审核修订）

15. **OpenAI 非流式 token 遗漏**：背景现状表新增"OpenAI 非流式 token 解析未实现"，Section 5 扩展为同时覆盖非流式（P0, Phase 2）和流式（P1, Phase 6），非流式提取提级到 Phase 2
16. **schema 迁移策略**：将 `ALTER TABLE` 替换为"schema.sql 完整表定义 + 独立一次性迁移脚本"双轨方案，解决 `spring.sql.init.mode=always` 下 ALTER 不幂等的启动失败问题；迁移脚本提供分批回填方案
17. **RelayRecordItem 模型类扩展**：新增 Section 4.2.1，列出全部 11 个新增字段；Section 4.2.2 给出 Repository `toColumnValues()` 和 `ROW_MAPPER` 映射变更表
18. **usage_json 完整写入路径**：`RelayResult` 新增 `usageJson` 字段（Section 4.4.1），各 Adapter（Claude 非流式/流式、OpenAI 非流式/流式）均增加 usageJson 写入；`record()` 方法补充 `item.setUsageJson(result.getUsageJson())`
19. **前端路由恢复**：Section 8 改为明确 `/records`、`/dashboard` 路由恢复方案，补充侧边栏变更，说明各页面数据源切换
20. **聚合查询 SQL 实现**：Section 4.5 补充完整 SQL 语句、引入 `ModelStat`/`DayStat` record 类替代 `Map<String,Object>`
21. **calculate() 精度修复**：改为先做完乘加运算再统一 `divide(ONE_MILLION, 10, HALF_UP)`，消除中间步骤多次舍入误差
22. **缓存定时刷新**：新增 `@Scheduled(fixedRate = 5min)` 自动刷新，确保未来生效定价到期后自动加载；需 `@EnableScheduling`
23. **pricing_status 遮蔽修复**：新增 `pricing_found tinyint(1)` 辅助字段，区分"有定价但缺 usage"与"定价和 usage 都缺失"
24. **rate 解析日志**：倍率格式非法或 <=0 时增加 `log.warn` 告警，并对 rate 做 `trim()` 预处理
25. **created_at_ts 收敛计划**：明确双写是过渡方案，待迁移完成后废弃 `created_at varchar` 字段
26. **实施阶段更新**：Phase 表同步更新，反映 OpenAI 非流式提级、模型/Repository 扩展、路由恢复等变更

### v4（二次落地审核修订）

27. **OpenAI 非流式 token 现状纠正**：`UpstreamHttpClient.populateUsage()` 已在 `postJson()` 中自动解析 OpenAI 格式的 `usage`，非流式 token 实际已正确提取。Section 1 现状表和 Section 5.1 已纠正，Phase 2 移除重复的"OpenAI 非流式 token 解析"任务
28. **usageJson 保存位置纠正**：OpenAI 非流式路径的 `usageJson` 应在 `UpstreamHttpClient.populateUsage()` 中保存，而非 `OpenAiRelayAdapter`；Section 4.4.1 和 Section 5.1 已更新
29. **`@EnableScheduling` 已存在**：`BackendApplication` 已有此注解，移除所有"需要添加"的描述，Phase 3 移除重复提及
30. **迁移脚本 `REPEAT/UNTIL` 语法修复**：裸 SQL 文件不支持流程控制语法，改为存储过程包装（`CREATE PROCEDURE` + `WHILE` + `CALL` + `DROP`）
