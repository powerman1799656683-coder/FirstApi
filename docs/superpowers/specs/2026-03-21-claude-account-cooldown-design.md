# Claude (Anthropic) 账号冷却策略设计

## 0. 适用范围与目标
- 仅适用于 Claude (Anthropic) API；OpenAI 需单独适配。
- 目标：只对可客观判定的限流问题触发冷却，避免误伤用户请求错误或平台故障。
- 冷却期间账号不可被调度或分配。

## 1. 官方错误模型（摘要）
来源：https://platform.claude.com/docs/en/api/errors
- 错误响应为 JSON，包含顶层 `error` 对象与 `request_id`。
- `error.type` 可能随时间扩展；`invalid_request_error` 也可能覆盖未列出的 4xx。
- SSE 流式响应在返回 200 后仍可能出现错误（需额外处理）。

| HTTP 状态码 | error.type | 含义 |
|------------|-----------|------|
| 400 | `invalid_request_error` | 请求格式/内容有误（也可能覆盖其他 4xx） |
| 401 | `authentication_error` | API Key 无效或异常 |
| 402 | `billing_error` | 计费/支付问题 |
| 403 | `permission_error` | 无权限访问资源 |
| 404 | `not_found_error` | 资源不存在 |
| 413 | `request_too_large` | 请求体过大（标准接口上限 32MB） |
| 429 | `rate_limit_error` | 触发限流 |
| 500 | `api_error` | Claude 服务内部错误 |
| 529 | `overloaded_error` | 平台过载 |

## 2. Claude 限流信号（官方）
来源：https://platform.claude.com/docs/en/api/rate-limits
- `retry-after`：建议等待的秒数。
- 响应头包含限流配额与重置时间：
  - `anthropic-ratelimit-requests-*`
  - `anthropic-ratelimit-tokens-*`
  - `anthropic-ratelimit-input-tokens-*`
  - `anthropic-ratelimit-output-tokens-*`
- 重置时间为 RFC 3339 格式时间戳。

## 3. 冷却策略（已实现）

### 3.1 策略表

| 状态码 | error.type | 冷却方式 | 恢复方式 | 说明 |
|--------|-----------|---------|---------|------|
| 402 | `billing_error` | DB 持久化，指数退避（30m→2h→6h→24h） | 仅管理员手动恢复 / 成功请求自动清除 | 计费问题，需充值 |
| 429 | `rate_limit_error` | 内存冷却，`retry-after` 秒数；缺失时默认 120 秒 | 到期自动解除 | 临时限速 |
| 429 | `billing_error`（Claude 特有） | DB 持久化，指数退避 | 同 402 | Claude 429 返回 billing_error 时升级为持久冷却 |
| 529 / 503 | `overloaded_error` | 内存冷却，固定 30 秒 | 到期自动解除 | 平台过载，短暂减压（529 为 Claude，503 为 OpenAI，处理逻辑相同） |
| 401 | `authentication_error` | **不冻结** | — | 记录 WARN 日志，管理员关注 |
| 其他 4xx/5xx | — | **不冻结**（除非 `error.type` 或 `error.code` 匹配 quota 错误类型） | — | 请求级错误；仅检查 `error.type` / `error.code` 是否属于 `QUOTA_ERROR_TYPES`，不检查 `error.message` |

### 3.2 关键设计决策

1. **`retry-after` 缺失时回退到默认值 120 秒**（而非锁死等管理员）
   - 原因：CDN / 反代 / 网络中间层可能吞掉 `retry-after` header
   - 120 秒足够覆盖大多数临时限速窗口
   - `retry-after: 0` 或 HTTP 日期早于当前时间按 RFC 含义 "立即重试"，使用最小冷却 1 秒
2. **529 过载短暂冷却 30 秒**（而非完全不处理）
   - 原因：继续发送流量会加重上游过载
3. **主动探测（probe）机制——默认关闭，按需开启**
   - `CooldownProbeService` 在冷却到期前 10 秒向上游发送极小请求（`max_tokens=1`）探测恢复
   - 探测分两条路径：内存冷却探测（rate limit / overload）和持久冷却探测（quota exhausted）
   - 默认 `probeEnabled=false`（避免意外产生上游成本）；生产环境通过配置 `app.relay.probe-enabled=true` 显式开启
   - 探测模型可配置：`app.relay.probe-openai-model`（默认 `gpt-4o-mini`）、`app.relay.probe-claude-model`（默认 `claude-haiku-4-5-20251001`）
   - 探测间隔可配置：`app.relay.probe-interval-ms`（默认 10000ms）
   - 成功探测清除冷却状态；失败时保持或升级冷却
   - 补充：成功请求自动清除 `quotaExhausted` 状态（已有逻辑，与探测互补）
4. **冷却分两层**
   - 短期限速（429 rate limit / 529 overload）：纯内存 `ConcurrentHashMap<Long, CooldownEntry>`，重启丢失可接受
   - 长期额度问题（402 billing / quota 关键字匹配）：DB 持久化 `quotaExhausted` + 指数退避

### 3.3 retry-after 解析

支持两种格式：
- **整数秒数**：`retry-after: 30` → 冷却 30 秒
- **整数 ≤ 0**：`retry-after: 0` → 按 RFC "立即重试"，冷却 1 秒（最小值）
- **HTTP 日期**：`retry-after: Sun, 06 Nov 1994 08:49:37 GMT` → 计算距当前时间的差值（上限 86400 秒）
- **HTTP 日期已过**：按 RFC "立即重试"，冷却 1 秒
- **解析失败**：回退到 `DEFAULT_RATE_LIMIT_COOLDOWN_SECONDS = 120`，记录 WARN 日志

### 3.4 并发安全

`RelayAccountSelector.cooldownAccount()` 使用 `ConcurrentHashMap.compute()` 而非 `put()`：
- 多线程同时触发冷却时，保留最长的冷却时间，不会被短冷却覆盖

## 4. 冷却状态与字段

### 短期冷却（内存）
- `RelayAccountSelector.cooldowns`: `ConcurrentHashMap<Long, CooldownEntry>`
- `CooldownEntry` 字段：
  - `until: Instant` — 冷却到期时间
  - `probeAt: Instant` — 探测开始时间（`= max(until - 10s, now + seconds/2)`）
  - `reason: String` — 冷却原因（如 `"claude_rate_limit"`, `"overload"`）
  - `provider: String` — 供应商标识（`"claude"` / `"openai"`）
  - `probing: volatile boolean` — 探测锁，防止并发探测同一账号
- 重启后自动清零（可接受：rate limit 通常几十秒到几分钟）

### 长期冷却（DB 持久化）
- `quotaExhausted`: boolean — 是否处于额度耗尽状态
- `quotaFailCount`: int — 连续失败次数（用于指数退避）
- `quotaLastReason`: String — 最近一次冷却原因
- `quotaNextRetryAt`: String — 下次可重试时间
- `quotaUpdatedAt`: String — 状态更新时间
- `provider`: String — 在 `RelayResult` 中标识

### 指数退避
| 连续失败次数 | 冷却时长 |
|------------|---------|
| 1 | 30 分钟 |
| 2 | 2 小时 |
| 3 | 6 小时 |
| 4+ | 24 小时 |

## 5. 数据采集

`UpstreamHttpClient` 在每次请求后自动捕获以下响应头到 `RelayResult.responseHeaders`：

| Header | 用途 |
|--------|------|
| `retry-after` | 限速冷却时间计算 |
| `anthropic-ratelimit-requests-*` | 请求级限流配额（可扩展用于监控） |
| `anthropic-ratelimit-tokens-*` | Token 级限流配额 |
| `anthropic-ratelimit-input-tokens-*` | 输入 Token 限流配额 |
| `anthropic-ratelimit-output-tokens-*` | 输出 Token 限流配额 |

## 6. 实现伪代码（与代码一致）

```java
// RelayService.syncQuotaRuntimeState()
if (result.isSuccess()) {
    clearQuotaStateIfRecovered(accountId);  // 成功请求自动清除 quota 状态
    return;
}
if (status == 402) {
    markQuotaExhausted(accountId, "billing_error:" + errorType);  // DB 持久化 + 指数退避
    return;
}
if (status == 429) {
    handle429(result, provider, errorType);  // 分 provider 处理，见下方
    return;
}
if (status == 529 || status == 503) {
    relayAccountSelector.cooldownAccount(accountId, 30, "overload", provider);  // 30s 内存冷却
    return;
}
if (status == 401) {
    LOGGER.warn(...);  // 仅日志，不冷却
    return;
}
// 其他 4xx/5xx: 仅当 error.type 或 error.code 匹配 QUOTA_ERROR_TYPES 时触发持久冷却
String reason = detectQuotaReason(result);  // 只检查 type/code，不检查 message
if (reason != null) {
    markQuotaExhausted(accountId, reason);
}

// ── handle429 分支逻辑 ──
void handle429(result, provider, errorType) {
    if ("openai".equals(provider)) {
        if (isQuotaError(errorType, errorCode)) {  // insufficient_quota / billing_hard_limit_reached
            markQuotaExhausted(accountId, "openai_insufficient_quota");  // 升级为持久冷却
        } else {
            int seconds = parseRetryAfterSeconds(result);  // 默认 120s
            cooldownAccount(accountId, seconds, "openai_rate_limit", "openai");
        }
        return;
    }
    if ("claude".equals(provider)) {
        if ("billing_error".equals(errorType)) {
            markQuotaExhausted(accountId, "claude_billing_error");  // 升级为持久冷却
            return;
        }
        int seconds = parseRetryAfterSeconds(result);
        cooldownAccount(accountId, seconds, "claude_rate_limit", "claude");
        return;
    }
    // 其他 provider: 通用 rate limit 处理
    int seconds = parseRetryAfterSeconds(result);
    cooldownAccount(accountId, seconds, "rate_limit", provider);
}
```

## 7. 与 OpenAI 策略的差异

| 维度 | Claude | OpenAI |
|------|--------|--------|
| 额度耗尽 | 无法从 429 区分（但 429+`billing_error` 会升级），402 可确认 | 429 + `insufficient_quota` / `billing_hard_limit_reached` 精确区分 |
| 速率限制 | 429 `rate_limit_error` + `retry-after` | 429 `rate_limit_exceeded` + `retry-after` |
| 计费问题 | 402 `billing_error` / 429 `billing_error` | 无 402（429+quota 覆盖） |
| 服务器过载 | 529 `overloaded_error` → 30s 冷却 | 503 → 30s 冷却 |
| retry-after 缺失 | 回退 120s | 回退 120s |

## 8. 自动恢复机制

### 恢复路径总览

系统共有 **四条** 恢复路径，覆盖内存冷却和持久冷却两个层级：

#### 路径 1：内存冷却到期自动解除
- **触发时机**：`RelayAccountSelector.getCooldownUntil()` 在读取时发现 `entry.until` 已过期
- **行为**：直接从 `ConcurrentHashMap` 中移除该 entry
- **适用于**：429 rate limit / 529 / 503 overload 的短期冷却

#### 路径 2：成功请求清除持久冷却
- **触发时机**：`syncQuotaRuntimeState()` 检测到请求成功（`result.isSuccess()`）
- **条件**：当前账号处于 `quotaExhausted=true` 状态
- **行为**：`QuotaStateManager.clearQuotaStateIfRecovered()` 清零所有 quota 字段（`quotaExhausted=false`, `quotaFailCount=0`, `quotaNextRetryAt=null`, `quotaLastReason=null`）
- **适用于**：已被调度到的 quota 账号（退避到期后被选中）

#### 路径 3：持久冷却懒清除
- **触发时机**：`RelayAccountSelector.isQuotaCooling()` 在账号调度时发现 `quotaNextRetryAt` 已过期
- **条件**：`quotaExhausted=true` 且 `quotaNextRetryAt < now`
- **行为**：立即清除 DB 中所有 quota 字段，使账号重新可被调度
- **并发安全**：多线程同时清除是幂等的，不会造成问题
- **作用**：确保 UI 显示与调度逻辑一致，无需等待下一次成功请求

#### 路径 4：探测服务主动恢复（需配置开启）
- **触发时机**：`CooldownProbeService` 定时任务扫描冷却账号
- **内存冷却探测**：在 `entry.probeAt` 时发送最小请求 → 成功则 `removeCooldown()`
- **持久冷却探测**：在 `quotaNextRetryAt - 10s` 时发送最小请求 → 成功则 `clearQuotaState()`
- **默认关闭**：需通过 `app.relay.probe-enabled=true` 显式开启
- **详细设计**：见第 10 节

### 不使用 `anthropic-ratelimit-*-reset` 头的原因
- 这些头提供配额窗口重置时间，不等于冷却结束时间
- `retry-after` 已足够准确地表达上游建议的等待时间
- 这些头更适合未来监控面板展示（v2 scope）

## 9. 边界与注意事项
- `error.type` 可能扩展，未知类型视为不触发冷却。
- 流式响应可能在 200 后报错 — 当前非流式冷却逻辑不覆盖此场景（v2 待处理）。
- 内存冷却重启丢失 — 对于短期 rate limit 可接受；长期 quota 问题使用 DB 持久化。
- 本文档仅覆盖 Claude；OpenAI 策略见 `2026-03-21-openai-account-cooldown-design.md`。

## 10. 主动探测机制详细设计

### 10.1 架构
- `CooldownProbeService`（`@Scheduled`）每隔 `probe-interval-ms` 扫描冷却账号
- 探测使用 `UpstreamHttpClient` 发送最小请求，复用账号的 proxy 配置
- 通过 `RetryAfterParser` 与主流程共享 `retry-after` 解析逻辑
- 通过 `QuotaStateManager` 与主流程共享 quota 状态管理逻辑

### 10.2 内存冷却探测
- 扫描 `RelayAccountSelector.cooldownEntries`
- 在 `CooldownEntry.probeAt`（= `until - 10s`，但不早于 `until - cooldown/2`）开始探测
- 成功 → 清除内存冷却；仍 429 → 重新计算冷却；quota 错误 → 升级为持久冷却

### 10.3 持久冷却探测
- 扫描 `quota_exhausted=true` 的账号（条件查询，非全表扫描）
- 在 `quotaNextRetryAt - 10s` 开始探测
- 成功 → 清除 DB quota 状态；仍 quota 错误 → 递增 failCount 延长退避

### 10.4 配置项
| 配置 | 默认值 | 说明 |
|------|--------|------|
| `app.relay.probe-enabled` | `false` | 是否开启探测 |
| `app.relay.probe-interval-ms` | `10000` | 探测扫描间隔 |
| `app.relay.probe-read-timeout-ms` | `10000` | 探测请求读超时 |
| `app.relay.probe-openai-model` | `gpt-4o-mini` | OpenAI 探测模型 |
| `app.relay.probe-claude-model` | `claude-haiku-4-5-20251001` | Claude 探测模型 |

## 11. 完整流程图

```
用户请求 → RelayService.relayChatCompletion()
                │
                ▼
    RelayAccountSelector.selectAccount()
                │
    ┌───────────┼──────────────────────┐
    │  过滤不可用账号：                 │
    │  - 内存冷却中？(cooldowns map)   │
    │  - DB quota 冷却中？             │
    │  - 禁用/异常/过期？             │
    │  - 并发数已满？                  │
    └───────────┼──────────────────────┘
                │
                ▼
    选中账号 → Adapter.relay() → UpstreamHttpClient.postJson()
                │                       │
                │               捕获 status / body / headers
                │                       │
                ▼                       ▼
    RelayService.syncQuotaRuntimeState(result)
                │
    ┌───────────┼──────────────────────────────────────────┐
    │                                                       │
    │  成功 → clearQuotaStateIfRecovered()  [路径2]         │
    │                                                       │
    │  402 → markQuotaExhausted()                           │
    │         └─ DB: quotaExhausted=true,                   │
    │            quotaFailCount++,                          │
    │            quotaNextRetryAt = now + 退避时间           │
    │                                                       │
    │  429 (Claude) ──┬─ billing_error → markQuotaExhausted │
    │                 └─ 其他 → cooldownAccount(retry-after) │
    │                                                       │
    │  429 (OpenAI) ──┬─ quota error → markQuotaExhausted   │
    │                 └─ 其他 → cooldownAccount(retry-after) │
    │                                                       │
    │  529/503 → cooldownAccount(30s)                       │
    │                                                       │
    │  401 → WARN 日志（不冷却）                             │
    │                                                       │
    │  其他 4xx/5xx → 检查 error.type/code                  │
    │         匹配 QUOTA_ERROR_TYPES → markQuotaExhausted   │
    │         不匹配 → 不处理                                │
    └──────────────────────────────────────────────────────┘

    ═══════════════════════════════════════════════
    恢复路径（并行运行）
    ═══════════════════════════════════════════════

    [路径1] 内存冷却到期
    getCooldownUntil() 发现 until 已过 → 自动移除 entry

    [路径3] 持久冷却懒清除
    isQuotaCooling() 发现 quotaNextRetryAt 已过 → 清除 DB quota 字段

    [路径4] CooldownProbeService（每 10s，需配置开启）
    ┌──────────────────────────────────────────────────────┐
    │  扫描内存冷却 (probeAt 已到 & until 未到)             │
    │    → 发送最小请求 (max_tokens=1, "hi")               │
    │    → 成功: removeCooldown()                          │
    │    → 429+quota: 升级为 markQuotaExhausted()          │
    │    → 429: 重新 cooldownAccount(新 retry-after)       │
    │    → 401/403: WARN 日志                              │
    │    → 其他: 保持冷却，等到期                            │
    │                                                      │
    │  扫描持久冷却 (quotaNextRetryAt - 10s 已到)           │
    │    → 发送最小请求                                     │
    │    → 成功: clearQuotaState()                         │
    │    → 429+quota: markQuotaExhausted() (failCount++)   │
    │    → 429: clearQuotaState() + cooldownAccount()      │
    │    → 401/403: WARN 日志                              │
    │    → 其他: 保持 quota 冷却                            │
    └──────────────────────────────────────────────────────┘
```

## 12. v2 待实现（可选扩展）
- [ ] SSE 流式中途 429 错误的冷却触发
- [ ] 管理员通知（WebSocket / webhook 推送冷却事件）
- [ ] 连续 429 递增冷却（限流频繁触发时指数增加冷却时间）
