# OpenAI 账号冷却策略设计

> 日期：2026-03-21
> 状态：Aligned（已根据代码实现校正 2026-03-21）

## 一、官方错误码

来源：https://developers.openai.com/api/docs/guides/error-codes

| HTTP 状态码 | error.type | error.code | 含义 |
|------------|-----------|-----------|------|
| 401 | — | `invalid_api_key` | API key 无效 |
| 403 | — | — | 地区不支持 |
| 429 | `insufficient_quota` | `insufficient_quota` | credits 耗尽 / 月度上限 |
| 429 | `insufficient_quota` | `billing_hard_limit_reached` | 账单硬限额已达上限 |
| 429 | `rate_limit_exceeded` | `rate_limit_exceeded` | RPM/TPM 频率过高 |
| 500 | — | — | 服务器内部错误 |
| 503 | — | — | 服务器过载 |

**注意：OpenAI 不返回 402 状态码。**

### 错误响应格式

额度耗尽：
```json
{
  "error": {
    "message": "You exceeded your current quota, please check your plan and billing details.",
    "type": "insufficient_quota",
    "param": null,
    "code": "insufficient_quota"
  }
}
```

账单硬限额：
```json
{
  "error": {
    "message": "Billing hard limit has been reached.",
    "type": "insufficient_quota",
    "param": null,
    "code": "billing_hard_limit_reached"
  }
}
```

速率限制：
```json
{
  "error": {
    "message": "Rate limit reached for gpt-4 in organization org-xxx on requests per min (RPM)...",
    "type": "rate_limit_exceeded",
    "param": null,
    "code": "rate_limit_exceeded"
  }
}
```

---

## 二、429 `insufficient_quota` 详细分析

### 2.1 触发场景

- API credits 余额用完
- 月度消费达到设定的 spending limit
- 免费试用额度用完
- 账单硬限额 (`billing_hard_limit_reached`) 已达上限

### 2.2 特点

- error.type **和** error.code 都是 `insufficient_quota`，可双重确认
- **不返回 `retry-after` header** — 因为这不是时间窗口问题
- **不会自动恢复** — 必须充值或提升 spending limit
- 即使账户有余额，如果 API key 绑定的 project/organization 额度用完，也会触发

### 2.3 常见误判情况

根据 OpenAI 社区反馈，以下情况也会返回 `insufficient_quota`：
- API key 创建在旧的免费试用 organization 下，而 credits 充值在新 organization
- Credits 已过期（OpenAI credits 有有效期）
- 组织的 spending limit 设置过低

这些都不是临时问题，需要人工排查。但也有可能管理员充值后恢复，因此采用渐进退避而非永久冻结。

---

## 三、429 `rate_limit_exceeded` 详细分析

### 3.1 触发场景

- RPM (Requests Per Minute) 超限
- TPM (Tokens Per Minute) 超限
- RPD (Requests Per Day) 超限（部分模型/Tier）
- 图片 API 的 images per minute 超限

### 3.2 返回的 Header

| Header | 含义 | 示例 |
|--------|------|------|
| `retry-after` | 需等待的秒数 | `20` |
| `x-ratelimit-limit-requests` | RPM 上限 | `10000` |
| `x-ratelimit-remaining-requests` | RPM 剩余 | `0` |
| `x-ratelimit-reset-requests` | RPM 重置时间 | `120ms` |
| `x-ratelimit-limit-tokens` | TPM 上限 | `2000000` |
| `x-ratelimit-remaining-tokens` | TPM 剩余 | `0` |
| `x-ratelimit-reset-tokens` | TPM 重置时间 | `8ms` |

### 3.3 特点

- 是**临时限制**，等待后自动恢复
- `retry-after` 精确告知等待时间
- 各 Tier 的限制不同，高 Tier 用户限制更宽松
- 失败的请求也计入限速窗口

### 3.4 各 Tier 的速率限制示例（GPT-4o 为例，仅供参考）

| Tier | RPM | TPM | RPD |
|------|-----|-----|-----|
| Free | 3 | 200 | 200 |
| Tier 1 | 500 | 30,000 | 500 |
| Tier 2 | 5,000 | 450,000 | — |
| Tier 3 | 5,000 | 800,000 | — |
| Tier 4 | 10,000 | 2,000,000 | — |
| Tier 5 | 10,000 | 30,000,000 | — |

> 说明：以上为示例数据，可能随平台调整，不用于程序逻辑判断。

---

## 四、503 详细分析

### 4.1 触发场景

- OpenAI 服务器过载
- 流量突增保护
- 引擎维护中

### 4.2 特点

- **不是账号问题**，是平台问题
- 通常短时间内自动恢复
- **不应冻结账号**，仅做短暂内存级冷却减轻上游压力

---

## 五、冷却策略

### 5.1 两层冷却机制

本系统采用**两层冷却**，对应不同严重程度：

| 层级 | 存储位置 | 用途 | 恢复方式 |
|------|---------|------|---------|
| **内存冷却** | `RelayAccountSelector.cooldowns` (ConcurrentHashMap) | 短暂限速、过载 | 到期自动解除，支持探测提前恢复 |
| **持久冷却** | 数据库 `quota_*` 字段 | 额度耗尽、计费问题 | 渐进退避 + 到期自动探测恢复 |

### 5.2 策略表

| 状态码 | error.type / code | 冷却层级 | 冷却时长 | 恢复方式 | 说明 |
|--------|-------------------|---------|---------|---------|------|
| 429 | `insufficient_quota` 或 `billing_hard_limit_reached` | 持久冷却 | 渐进退避（见 5.3） | 到期后自动探测 + 探测通过后恢复 | 额度耗尽 |
| 429 | `rate_limit_exceeded` | 内存冷却 | `retry-after` 秒数；无 header 默认 120 秒 | 到期自动解除；支持探测提前恢复 | 临时限速 |
| 429 | 其他/无 type | 内存冷却 | 120 秒 | 到期自动解除 | 兜底处理 |
| 503 | — | 内存冷却 | 30 秒 | 到期自动解除 | 平台过载 |
| 401 | — | **不冻结** | — | 记录 WARN 日志，管理员检查 API key | key 失效需人工处理 |
| 403 | — | **不冻结** | — | 记录 WARN/告警，转人工处理 | 地区限制或权限不足 |
| 其他 4xx/5xx | error.type / error.code 命中 `QUOTA_ERROR_TYPES` | 持久冷却 | 渐进退避 | 到期后自动探测 | 仅按白名单判断额度问题 |
| 其他 | — | **不冻结** | — | — | 请求级错误 |

### 5.3 渐进退避（持久冷却）

对 `insufficient_quota` 等额度问题，采用渐进退避而非固定时长，原因：
- 管理员可能已充值但尚未通知系统
- `insufficient_quota` 可能由 organization/project 配置导致，不一定是真的没钱
- 避免误判导致账号被长期锁死

| 连续失败次数 | 冷却时长 |
|-------------|---------|
| 1 | 30 分钟 |
| 2 | 120 分钟（2 小时） |
| 3 | 360 分钟（6 小时） |
| 4+ | 1440 分钟（24 小时） |

成功请求（包括探测成功）会重置 `failCount` 为 0。

### 5.4 实现伪代码（与代码一致）

```java
// RelayService.handle429() — OpenAI 分支
if ("openai".equals(provider)) {
    boolean isQuotaExhausted = QUOTA_ERROR_TYPES.contains(errorType)
            || QUOTA_ERROR_TYPES.contains(extractErrorCode(result.getBody()));
    if (isQuotaExhausted) {
        // DB 持久化 + 渐进退避（30m→2h→6h→24h）
        markQuotaExhausted(accountId, "openai_insufficient_quota");
        return;
    }
    // 速率限制：内存冷却，retry-after 秒数（缺失默认 120s）
    int cooldownSeconds = parseRetryAfterSeconds(result);
    relayAccountSelector.cooldownAccount(accountId, cooldownSeconds);
}

// 529/503 过载：内存冷却 30s
// 401：仅日志，不冷却
```

### 5.5 retry-after 解析

支持两种格式：
1. **整数秒**：`retry-after: 20` → 冷却 20 秒
2. **HTTP-date (RFC 7231)**：`retry-after: Sun, 06 Nov 1994 08:49:37 GMT` → 计算距当前时间差

边界处理：
- 缺失或为空：回退 120 秒
- 负数或零：回退 1 秒（`RetryAfterParser.java:51`）
- 超大值：上限 86400 秒（1 天）
- 无法解析：记录 WARN 日志，回退 120 秒

---

## 六、自动探测与恢复

参考 Claude 冷却策略的探测机制，OpenAI 账号同样支持自动探测恢复。

### 6.1 探测参数

#### 内存冷却的探测时间（`RelayAccountSelector.cooldownAccount()`）

```java
Instant until = now.plusSeconds(seconds);                      // 冷却截止
Instant probeEarly = until.minusSeconds(10);                   // 截止前 10 秒
Instant probeHalf = now.plusSeconds(Math.max(seconds / 2, 1)); // 冷却时长的一半
Instant probeAt = probeEarly.isAfter(probeHalf) ? probeEarly : probeHalf;
```

取 `until - 10s` 和 `seconds/2` 中**较晚的时间**，避免短冷却探测过早。

| 冷却时长 | until | probeAt | 说明 |
|---------|-------|---------|------|
| 120 秒 | now + 120s | now + 110s（until - 10s） | 正常：提前 10 秒探测 |
| 30 秒 | now + 30s | now + 20s（until - 10s） | 正常：提前 10 秒探测 |
| 10 秒 | now + 10s | now + 5s（seconds/2） | 短冷却：取一半避免太早 |
| 2 秒 | now + 2s | now + 1s（seconds/2） | 极短冷却：取一半 |

#### 持久冷却的探测时间（`CooldownProbeService.probePersistentCooldowns()`）

```java
Instant probeAt = toInstant(quotaNextRetryAt).minusSeconds(10);  // 重试时间前 10 秒
```

固定在 `quota_next_retry_at` 前 10 秒发起探测。

### 6.2 探测请求规格

```json
{
  "model": "gpt-4o-mini",
  "messages": [{"role": "user", "content": "hi"}],
  "max_tokens": 1
}
```
- 模型通过 `app.relay.probe-openai-model` 配置，默认 `gpt-4o-mini`（`RelayProperties.java:17`）
- 发送到 `{baseUrl}/v1/chat/completions`，使用 `Bearer` 认证（`CooldownProbeService.java:252-256`）
- 探测超时独立配置：`app.relay.probe-read-timeout-ms`，默认 10 秒
- 探测请求会使用账号配置的代理（若有），通过 `resolveProxySafe()` 解析
- 最小 token 输出，消耗可忽略
- 单账号同一时刻仅保留一条探测任务，避免探测风暴

### 6.3 内存冷却的探测恢复（rate_limit_exceeded / 503）

```
timeline （以 retry-after=120s 为例）:
  ── request fails (429 rate_limit) ──
  │
  ├─ now: cooldownAccount(120s)
  │   until = now + 120s
  │   probeAt = max(until - 10s, now + 60s) = now + 110s
  │
  │     ... wait 110s ...
  ├─ probeAt (now + 110s) ← CooldownProbeService 发起轻量探测
  │     ... wait 10s ...
  ├─ until (now + 120s) ← 到期自动解除（若探测未恢复或未启用）
  │
```

探测结果处理（`CooldownProbeService.handleMemoryProbeResult()`）：

| 探测结果 | 处理方式 | 代码位置 |
|---------|---------|---------|
| 2xx 成功 | 立即清除内存冷却（`removeCooldown`），账号恢复可用 | `:135-138` |
| 429 + 额度错误 | 移除内存冷却，**升级为持久冷却**（`markQuotaExhausted`） | `:144-149` |
| 429 限速（非额度） | 以新的 `retry-after` 重新计算冷却窗口 | `:152-157` |
| 401/403 | 记录 WARN 日志，不变更冷却状态，转人工处理 | `:160-162` |
| 503/500/其他 | 不恢复，保持冷却等待 `until` 自动解除 | `:165-166` |

### 6.4 持久冷却的探测恢复（insufficient_quota / billing_hard_limit）

```
timeline （首次额度耗尽）:
  ── request fails (429 insufficient_quota) ──
  │
  ├─ now: markQuotaExhausted(failCount=1, backoff=30min)
  │   quota_next_retry_at = now + 30min
  │   probeAt = quota_next_retry_at - 10s
  │
  │     ... wait 29min50s ...
  ├─ probeAt ← CooldownProbeService 发起轻量探测
  │     ... wait 10s ...
  ├─ quota_next_retry_at ← 若未恢复，惰性清除或等下次周期
  │
```

探测结果处理（`CooldownProbeService.handlePersistentProbeResult()`）：

| 探测结果 | 处理方式 | 代码位置 |
|---------|---------|---------|
| 2xx 成功 | 清除 DB quota 冷却状态（`clearQuotaState`），`failCount` 归零，账号恢复可用 | `:202-205` |
| 429 + 额度错误 | `failCount + 1`，重新计算退避时间 | `:211-215` |
| 429 限速（非额度） | **说明额度已恢复但触发限速**；清除 quota 冷却，转入内存冷却 | `:218-224` |
| 401/403 | 记录 WARN 日志，不变更状态，转人工处理 | `:227-228` |
| 503/500/其他 | 不变更状态，等待下次探测周期 | `:232-233` |

### 6.5 探测调度实现

已实现于 `CooldownProbeService.java`，核心入口：

```java
// CooldownProbeService.java:85-92
@Scheduled(fixedDelayString = "${app.relay.probe-interval-ms:10000}", initialDelay = 30000)
public void probeCooldownAccounts() {
    if (!relayProperties.isProbeEnabled()) {
        return;                         // 探测功能默认关闭
    }
    probeMemoryCooldowns();             // 1. 扫描内存冷却
    probePersistentCooldowns();         // 2. 扫描持久冷却
}
```

**探测开关**：通过配置 `app.relay.probe-enabled=true` 启用（默认关闭，`RelayProperties.java:14`）。
探测关闭时，冷却账号仅通过**自然到期**或**正常请求成功**恢复。

关键代码文件：
- 调度入口 + 结果处理：`CooldownProbeService.java`
- 内存冷却数据结构：`CooldownEntry.java`
- 持久冷却状态管理：`QuotaStateManager.java`

### 6.6 探测并发控制

- **内存冷却**：`CooldownEntry.tryStartProbing()` 使用 `synchronized` 保证同一账号同一时刻仅一个探测
- **持久冷却**：`ConcurrentHashMap<Long, Boolean> persistentProbing`，通过 `putIfAbsent` 原子操作
- 探测完成后在 `finally` 块中释放锁，确保异常时也能释放
- 新的冷却事件仅在 `until` 更晚时覆盖旧的（`RelayAccountSelector.java:195-198`）

### 6.7 三条恢复路径汇总

| 恢复路径 | 触发条件 | 适用冷却类型 | 说明 |
|---------|---------|------------|------|
| **正常请求成功** | 任何成功的转发请求（2xx） | 持久冷却 | `RelayService.syncQuotaRuntimeState()` 调用 `clearQuotaStateIfRecovered()` |
| **探测恢复** | `CooldownProbeService` 定时探测成功 | 内存冷却 + 持久冷却 | 需 `probe-enabled=true` |
| **自然到期（惰性清除）** | 冷却截止时间已过 | 内存冷却 + 持久冷却 | 下次 `selectAccount()` 时惰性清除 |

**惰性清除机制**（`RelayAccountSelector.java:369-390`）：
- 内存冷却：`getCooldownUntil()` 检查到 `until` 已过期时，从 `cooldowns` map 中移除
- 持久冷却：`isQuotaCooling()` 检查到 `quotaNextRetryAt` 已过期时，清除 DB quota 状态（`quotaExhausted=false`、`failCount=0`）
- 惰性恢复是幂等的，并发请求同时触发不会产生副作用

### 6.8 探测配置汇总

| 配置项 | 默认值 | 说明 |
|--------|--------|------|
| `app.relay.probe-enabled` | `false` | 是否启用探测（**默认关闭**） |
| `app.relay.probe-interval-ms` | `10000` | 探测扫描间隔（10 秒） |
| `app.relay.probe-read-timeout-ms` | `10000` | 探测请求读取超时 |
| `app.relay.probe-openai-model` | `gpt-4o-mini` | OpenAI 探测使用的模型 |
| `app.relay.probe-claude-model` | `claude-haiku-4-5-20251001` | Claude 探测使用的模型 |

---

## 七、`insufficient_quota` 与 `rate_limit_exceeded` 区分要点

| 特征 | `insufficient_quota` | `rate_limit_exceeded` |
|------|---------------------|----------------------|
| error.type | `insufficient_quota` | `rate_limit_exceeded` |
| error.code | `insufficient_quota` 或 `billing_hard_limit_reached` | `rate_limit_exceeded` |
| `retry-after` header | **无** | **有** |
| 是否自动恢复 | 否（需充值） | 是（等待即可） |
| error.message 关键词 | "exceeded your current quota" | "Rate limit reached" |
| 冷却策略 | 持久冷却 + 渐进退避 + 探测恢复 | 内存冷却 + retry-after + 探测恢复 |

两个字段（type 和 code）通常可以区分；若字段缺失或未知，按兜底规则处理。

---

## 八、需要从上游捕获的数据

| 数据 | 来源 | 获取方式 |
|------|------|---------|
| `retry-after` | Response header | `result.getResponseHeader("retry-after")` |
| error.type | Response body JSON `error.type` | `extractErrorType(result.getBody())` |
| error.code | Response body JSON `error.code` | `extractErrorCode(result.getBody())` |
| provider 标识 | Adapter 层设置 | `result.getProvider()` = `"openai"` |
| 全部 response headers | `UpstreamHttpClient` | `result.getResponseHeaders()` |

---

## 九、冷却状态字段

### 9.1 内存冷却字段（`RelayAccountSelector`）

| 字段 | 类型 | 说明 |
|------|------|------|
| `cooldowns` | `ConcurrentHashMap<Long, CooldownEntry>` | accountId → 冷却条目 |

`CooldownEntry` 字段（`CooldownEntry.java`）：

| 字段 | 类型 | 说明 |
|------|------|------|
| `until` | `Instant` | 冷却截止时间 |
| `probeAt` | `Instant` | 探测时间 = `max(until - 10s, now + seconds/2)` |
| `reason` | `String` | 冷却原因（如 `openai_rate_limit`、`overload`） |
| `provider` | `String` | 提供商标识（`openai`/`claude`），用于探测时选择请求格式 |
| `probing` | `volatile boolean` | 是否正在探测中（通过 `synchronized` 保证原子性） |

### 9.2 持久冷却字段（数据库 `AccountItem`）

| 字段 | 类型 | 说明 |
|------|------|------|
| `quota_exhausted` | boolean | 是否处于额度冷却 |
| `quota_fail_count` | int | 连续失败次数（决定退避时长） |
| `quota_last_reason` | String | 最后冷却原因，如 `openai_insufficient_quota` |
| `quota_next_retry_at` | String (UTC ISO-8601 datetime) | 下次可重试时间 |
| `quota_updated_at` | String (UTC ISO-8601 datetime) | 最后更新时间 |

---

## 十、与 Claude 策略的对比

| 维度 | Claude | OpenAI |
|------|--------|--------|
| 额度耗尽状态码 | 402 `billing_error` | 429 `insufficient_quota` |
| 速率限制状态码 | 429 `rate_limit_error` | 429 `rate_limit_exceeded` |
| 区分额度/限速 | 通过 status code 区分（402 vs 429） | 通过 error.type 区分（同为 429） |
| 服务器过载 | 529 `overloaded_error` | 503 |
| retry-after | 429 时有 | 仅 `rate_limit_exceeded` 时有 |
| 额度恢复方式 | 月度自动重置 / Tier 上限恢复 | 需充值，不自动恢复 |
| 额度冷却策略 | 渐进退避 + 探测恢复 | 渐进退避 + 探测恢复（相同机制） |
| 限速冷却策略 | 内存冷却 + retry-after + 探测 | 内存冷却 + retry-after + 探测（相同机制） |
| 过载冷却 | 30 秒内存冷却 | 30 秒内存冷却 |
| 探测参数 | probeAt = max(until-10s, seconds/2) | probeAt = max(until-10s, seconds/2)（相同机制） |

---

## 十一、完整数据流图

```
正常请求 → RelayService.relay()
         → RelayAccountSelector.selectAccount() 过滤冷却账号
              ├─ isInCooldown(account):
              │   ├─ getCooldownUntil(): 检查内存冷却 (CooldownEntry)
              │   └─ isQuotaCooling(): 检查持久冷却 (DB quota_*)
              │        └─ 到期时惰性清除: quotaExhausted=false, failCount=0
              │
         → OpenAiRelayAdapter → UpstreamHttpClient.postJson()
              捕获 response headers (retry-after 等)
              │
         → RelayService.syncQuotaRuntimeState() 判定结果
              │
              ├─ 2xx 成功 → clearQuotaStateIfRecovered() → 清除持久冷却
              │
              ├─ 429 + insufficient_quota / billing_hard_limit
              │    → markQuotaExhausted() → DB持久冷却 (渐进退避)
              │
              ├─ 429 + rate_limit_exceeded
              │    → cooldownAccount(retry-after秒) → 内存冷却
              │
              ├─ 503 → cooldownAccount(30s) → 内存冷却
              │
              ├─ 401 → 仅日志，不冷却
              │
              └─ 其他 4xx/5xx → detectQuotaReason()
                   → 匹配 QUOTA_ERROR_TYPES 白名单则持久冷却

探测流程 (CooldownProbeService, 每10秒, 需 probe-enabled=true):
  probeCooldownAccounts()
    ├─ probeMemoryCooldowns()
    │    遍历 cooldowns map
    │    → 到达 probeAt 且未过期且未在探测中 → 发送轻量请求
    │    → 成功: removeCooldown(); 429额度: 升级持久冷却; 429限速: 重算冷却
    │
    └─ probePersistentCooldowns()
         查询 DB quotaExhausted=true
         → 在 quotaNextRetryAt 前10秒 → 发送轻量请求
         → 成功: clearQuotaState(); 429额度: failCount+1; 429限速: 清quota转内存
```

---

## 十二、边界与注意事项

1. **error.type 可能扩展**：未知 type 不触发冷却，避免误伤。仅 `QUOTA_ERROR_TYPES` 白名单（`insufficient_quota`、`billing_hard_limit_reached`、`quota_exceeded`）触发持久冷却。
2. **retry-after 缺失的兜底**：`rate_limit_exceeded` 无 retry-after 时默认 120 秒；`insufficient_quota` 无 retry-after 不影响（走渐进退避）。
3. **retry-after 边界值**：最大 86400 秒（1 天），零/负数回退 1 秒。
4. **`billing_hard_limit_reached`**：代码中 `QUOTA_ERROR_TYPES` 已包含此值，与 `insufficient_quota` 同等处理。
5. **探测请求幂等性**：探测使用最小 token（`max_tokens: 1`，model: `gpt-4o-mini`），消耗可忽略不计。
6. **探测并发控制**：单账号同一时刻仅允许一条探测任务，避免探测风暴。内存冷却用 `synchronized`，持久冷却用 `ConcurrentHashMap.putIfAbsent`。
7. **三条恢复路径**：正常请求成功（`clearQuotaStateIfRecovered`）、探测成功、惰性到期清除（`isQuotaCooling`）。惰性清除同时重置 `failCount=0`。
8. **探测功能默认关闭**：需配置 `app.relay.probe-enabled=true` 启用。关闭时仅依赖自然到期和正常请求成功恢复。
9. **探测使用账号代理**：探测请求通过 `resolveProxySafe()` 使用账号配置的代理，保持与正常请求一致的网络路径。
10. **冷却合并策略**：同一账号的新冷却事件仅在 `until` 更晚时覆盖旧冷却（`RelayAccountSelector.java:196`），保留较长的冷却。
11. **本文档仅覆盖 OpenAI**；Claude 策略见 `2026-03-21-claude-account-cooldown-design.md`。
