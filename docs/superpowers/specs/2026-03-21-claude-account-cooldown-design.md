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

## 3. 主流处理原则（本项目采用）
- 仅当 **HTTP 429 且 `error.type = rate_limit_error`** 时触发冷却。
- 402 `billing_error`：计费问题，**不自动恢复**，仅管理员处理。
- 401 `authentication_error`：鉴权/Key 问题，**不触发冷却**，仅标记异常。
- 529 `overloaded_error` 与 5xx：平台问题，**不触发冷却**。
- 其他 4xx：请求错误，**不触发冷却**。

## 4. 冷却时间计算与恢复
- `retry-after` 在 HTTP 语义上可为秒数或 HTTP 日期，本实现支持两种解析。
- 若 `retry-after` 缺失或解析失败：**不自动恢复**，标记为需人工确认。
- 固定缓冲：**30 秒**。
- 探测提前量：**10 秒**。

时间计算：
- `retry_at` = 从 `retry-after` 解析出的最早可重试时间
- `recover_at = retry_at + 30s`
- `probe_at = retry_at + 20s`（即 `recover_at - 10s`）

## 5. 主动探测与恢复规则
- 到达 `probe_at` 时由系统发起**轻量探测请求**（最小输入、最小输出）。
- 探测成功（2xx 且非 429）：标记账号为“已探测通过”，到达 `recover_at` 时恢复可用。
- 探测返回 429 `rate_limit_error`：以新的 `retry-after` 重新计算 `retry_at/recover_at/probe_at`。
- 探测返回 401/402：标记为鉴权/计费异常，转人工处理。
- 探测返回 5xx/529：不恢复，保持冷却并记录告警；允许后续再次探测。
- 单账号仅保留一条探测任务；新的冷却时间以更晚的 `recover_at` 覆盖。

## 6. 可选扩展（默认关闭）
- 连续 429 过度冷却（限流频繁触发时增加额外冷却），默认不启用。

## 7. 冷却状态与字段建议
- `cooldown_status`：`AVAILABLE` / `COOLDOWN` / `PROBING` / `PROBED_OK`
- `cooldown_reason`：`claude_rate_limit` / `claude_billing_error` / `manual`
- `cooldown_until`：`recover_at`
- `cooldown_probe_at`：`probe_at`
- `cooldown_retry_at`：`retry_at`
- `last_error_status` / `last_error_type`
- `provider`：固定为 `claude`

## 8. 伪代码（摘要）
```java
private void handleClaudeError(RelayResult result) {
    int status = result.getStatusCode();
    String errorType = extractErrorType(result.getBody());

    if (status == 402 && "billing_error".equals(errorType)) {
        markManualReview(result.getAccountId(), "claude_billing_error");
        return;
    }

    if (status == 429 && "rate_limit_error".equals(errorType)) {
        RetryAfter ra = parseRetryAfter(result.getHeaders());
        if (!ra.isValid()) {
            markManualReview(result.getAccountId(), "claude_retry_after_missing");
            return;
        }
        Instant retryAt = ra.toInstant();
        Instant recoverAt = retryAt.plusSeconds(30);
        Instant probeAt = retryAt.plusSeconds(20);
        markAccountCooldown(result.getAccountId(), retryAt, probeAt, recoverAt, "claude_rate_limit");
        return;
    }

    // 401/403/404/500/529/400... 不触发冷却
}

private void runCooldownProbe(Account account) {
    if (Instant.now().isBefore(account.getCooldownProbeAt())) return;

    ProbeResult probe = sendLightweightProbe(account);
    if (probe.is2xx()) {
        markProbedOk(account.getId());
        // 到 recover_at 自动恢复
        return;
    }

    if (probe.is429RateLimit()) {
        RetryAfter ra = parseRetryAfter(probe.getHeaders());
        if (!ra.isValid()) {
            markManualReview(account.getId(), "claude_retry_after_missing");
            return;
        }
        Instant retryAt = ra.toInstant();
        Instant recoverAt = retryAt.plusSeconds(30);
        Instant probeAt = retryAt.plusSeconds(20);
        markAccountCooldown(account.getId(), retryAt, probeAt, recoverAt, "claude_rate_limit");
        return;
    }

    if (probe.is401() || probe.is402()) {
        markManualReview(account.getId(), "claude_auth_or_billing");
        return;
    }

    // 5xx/529: 平台问题，不恢复，等待后续探测
    markProbeFailed(account.getId(), "claude_platform_error");
}
```

## 9. 边界与注意事项
- `error.type` 可能扩展，未知类型视为不触发冷却。
- 流式响应可能在 200 后报错，需要单独捕获。
- 本策略**不设置默认值**：`retry-after` 缺失或不可解析时不自动恢复。
- 本文档仅覆盖 Claude；OpenAI 需单独制定策略。
