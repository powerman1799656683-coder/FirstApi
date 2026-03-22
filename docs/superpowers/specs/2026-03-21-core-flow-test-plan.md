# API 中转核心流程测试方案

**日期**: 2026-03-21
**适用版本**: FirstApi（Spring Boot 4 + React 19）
**覆盖范围**: 账号添加 → 分组配置 → API Key 生成 → 调度 → 冷却切换 → 自动恢复 → Token 计费 全链路

---

## 目录

1. [测试环境准备](#1-测试环境准备)
2. [账号添加](#2-账号添加)
3. [分组配置与账号类型绑定](#3-分组配置与账号类型绑定)
4. [用户生成 API Key](#4-用户生成-api-key)
5. [请求调度](#5-请求调度)
6. [账号冷却（不可用触发）](#6-账号冷却不可用触发)
7. [账号切换](#7-账号切换)
8. [自动恢复](#8-自动恢复)
9. [Token 统计与费用计算](#9-token-统计与费用计算)
10. [完整端到端链路回归](#10-完整端到端链路回归)
11. [附录：curl / SQL 速查](#11-附录curl--sql-速查)

---

## 1. 测试环境准备

### 1.1 基础服务

| 服务 | 地址 | 说明 |
|------|------|------|
| 后端 API | http://localhost:8080 | Spring Boot |
| 前端 | http://localhost:5173 | Vite 开发模式 |
| MySQL | 127.0.0.1:3306 / firstapi | 存储账号、记录 |

### 1.2 测试账号

| 角色 | 用户名 | 密码 |
|------|--------|------|
| 管理员 | admin | AdminPass123! |
| 普通用户 | member | UserPass123! |

### 1.3 数据清理 SQL（每次测试前执行）

```sql
-- 清理冷却状态（保留账号数据）
UPDATE accounts SET quota_exhausted=0, quota_fail_count=0,
  quota_next_retry_at=NULL, quota_last_reason=NULL,
  quota_updated_at=NULL, temp_disabled=0;

-- 清理 relay 记录
TRUNCATE TABLE relay_records;
```

### 1.4 快速登录（获取会话 Cookie）

```bash
curl -c cookies.txt -X POST http://localhost:8080/api/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"username":"admin","password":"AdminPass123!"}'
```

### 1.5 关键探测配置（测试自动恢复时必须开启）

```yaml
# application.yml 或环境变量
app:
  relay:
    probe-enabled: true
    probe-interval-ms: 10000
    probe-read-timeout-ms: 10000
    probe-claude-model: claude-haiku-4-5-20251001
```

---

## 2. 账号添加

### 2.1 测试目标

验证管理员可以成功添加 Claude / OpenAI 账号，账号字段落库正确，冷却字段初始值为空。

### 2.2 测试数据规划

准备至少 **3 个** Claude 账号（模拟多账号调度场景）：

| 账号 | 平台 | 类型 | 优先级 | 并发 |
|------|------|------|--------|------|
| claude-a | anthropic | Claude Code | 1 | 5 |
| claude-b | anthropic | Claude Code | 1 | 5 |
| claude-c | anthropic | Claude Max (Plus) | 2 | 3 |

### 2.3 测试用例

**TC-ACC-01：正常添加账号**

1. 管理员登录后台
2. 进入"账号管理" → 新增账号
3. 填写：平台=anthropic，类型=Claude Code，API Key=`sk-ant-xxxx`，优先级=1，并发=5
4. 保存

验证点：
- [ ] 账号列表出现新记录
- [ ] `quota_exhausted = 0`，`quota_fail_count = 0`，`quota_next_retry_at = NULL`
- [ ] `temp_disabled = 0`，`priority_value = 1`，`concurrency = 5`
- [ ] API Key 在数据库中以加密形式存储（非明文）

---

**TC-ACC-02：添加 OpenAI 账号**

1. 平台=openai，类型=ChatGPT Plus，API Key=`sk-xxxx`，并发=3
2. 保存

验证点：
- [ ] 账号正常入库，`platform = 'openai'`
- [ ] 账号类型值在 `allowedAccountTypes('openai')` 白名单内

---

**TC-ACC-03：非法账号类型被拒绝**

1. 平台=anthropic，账号类型=ChatGPT Plus（类型与平台不匹配）

验证点：
- [ ] 后端返回 400 / 前端给出错误提示
- [ ] 不写入数据库

---

**TC-ACC-04：添加重复 API Key**

1. 用相同的 API Key 再次添加

验证点：
- [ ] 系统给出明确提示（重复冲突）
- [ ] 数据库不产生重复记录

---

## 3. 分组配置与账号类型绑定

### 3.1 测试目标

验证分组的平台、账号类型、模型路由、倍率设置正确，与账号池正确关联。

### 3.2 测试数据规划

| 分组名 | 平台 | 账号类型 | 倍率 |
|--------|------|----------|------|
| claude-code-group | anthropic | Claude Code | 1.0 |
| claude-max-group | anthropic | Claude Max (Plus) | 1.5 |
| openai-group | openai | ChatGPT Plus | 1.0 |

### 3.3 测试用例

**TC-GRP-01：创建分组并绑定账号类型**

1. 进入"分组管理" → 新增分组
2. 填写：名称=claude-code-group，平台=anthropic，账号类型=Claude Code，倍率=1.0，开启模型路由
3. 保存

验证点：
- [ ] `platform = 'anthropic'`，`account_type = 'Claude Code'`
- [ ] `rate_value = '1.0'`，`model_routing = 1`

---

**TC-GRP-02：平台与账号类型不匹配被拒绝**

1. 平台=anthropic，账号类型=ChatGPT Plus

验证点：
- [ ] 前端或后端拒绝，给出错误提示
- [ ] 不产生脏数据

---

**TC-GRP-03：倍率非法值验证**

1. 倍率输入负数或字母

验证点：
- [ ] 前端阻止提交 或 后端返回 400

---

**TC-GRP-04：分组账号关联验证**

1. 确认 claude-code-group 关联 claude-a、claude-b 两个账号

验证点（数据库）：
```sql
SELECT * FROM account_group_bindings WHERE group_id = {gid};
```
- [ ] 返回 claude-a 和 claude-b 的记录

---

## 4. 用户生成 API Key

### 4.1 测试目标

验证普通用户可以生成 API Key，Key 与分组正确绑定，状态为正常，且可用于发起中转请求。

### 4.2 测试用例

**TC-KEY-01：普通用户生成 API Key**

1. member 账号登录
2. 进入"我的 API Key" → 新增
3. 名称=test-key，关联分组=claude-code-group
4. 保存，复制生成的 key

验证点：
- [ ] 返回完整 key（格式类似 `sk-xxxxx`）
- [ ] `api_keys.status = '正常'`，`group_id` 指向 claude-code-group
- [ ] `last_used = NULL`（初始未使用）

---

**TC-KEY-02：禁用 Key 后无法访问**

1. 管理员将 key 状态改为禁用
2. 使用该 key 发起请求

验证点：
- [ ] 返回 401，错误码 `invalid_api_key`

---

**TC-KEY-03：无效 Bearer Token 被拒绝**

```bash
curl -X POST http://localhost:8080/v1/chat/completions \
  -H 'Authorization: Bearer invalid-token-xxx' \
  -H 'Content-Type: application/json' \
  -d '{"model":"claude-opus-4-5","messages":[{"role":"user","content":"hi"}]}'
```

验证点：
- [ ] 返回 401

---

## 5. 请求调度

### 5.1 测试目标

验证请求能被正确路由到对应平台账号，按 tier 优先级 + 轮询调度，并发控制生效。

### 5.2 路由规则（来自 RelayModelRouter）

```
claude-*         → provider: claude → 匹配 anthropic 分组
gpt-*, o1*, o3*  → provider: openai → 匹配 openai 分组
其他             → 400 unsupported_model
```

**Tier 优先级**：
- Claude：pro > max5 > max20
- OpenAI：plus > pro

### 5.3 测试用例

**TC-SCH-01：基础路由 - Claude 模型**

```bash
curl -X POST http://localhost:8080/v1/chat/completions \
  -H "Authorization: Bearer {test-key}" \
  -H "Content-Type: application/json" \
  -d '{"model":"claude-opus-4-5","messages":[{"role":"user","content":"say hi"}],"max_tokens":10}'
```

验证点：
- [ ] 返回 200，响应体包含 `choices[0].message.content`（OpenAI 格式兼容）
- [ ] `relay_records` 新增记录，`provider = 'claude'`，`upstream_status = 200`
- [ ] `api_keys.last_used` 被更新

---

**TC-SCH-02：不支持的模型被拒绝**

```bash
-d '{"model":"unknown-model-xyz","messages":[...]}'
```

验证点：
- [ ] 返回 400，错误码 `unsupported_model`

---

**TC-SCH-03：分组平台不匹配**

1. 用 openai-group 的 API Key，发送 `model: claude-opus-4-5` 请求

验证点：
- [ ] 返回 403，错误码 `group_platform_mismatch`

---

**TC-SCH-04：轮询调度验证（多账号）**

1. 连续发送 6 次请求（claude-code-group 有 claude-a、claude-b 两账号）

验证点（数据库）：
```sql
SELECT account_id, COUNT(*) FROM relay_records GROUP BY account_id;
```
- [ ] claude-a 和 claude-b 各被选中约 3 次（轮询均衡）

---

**TC-SCH-05：并发控制验证**

1. 设置 claude-a 的 `concurrency = 2`
2. 并发发送 5 个请求（需配合慢响应模拟）

验证点：
- [ ] 最多 2 个请求同时命中 claude-a
- [ ] 超出的请求自动分流到 claude-b

---

**TC-SCH-06：无可用账号时的响应**

1. 将所有 claude 账号设置 `temp_disabled = 1`
2. 发送请求

验证点：
- [ ] 返回 503 或 429，提示无可用账号
- [ ] 错误不是 500 内部错误

---

**TC-SCH-07：流式请求正常转发**

```bash
curl -X POST http://localhost:8080/v1/chat/completions \
  -H "Authorization: Bearer {test-key}" \
  -d '{"model":"claude-opus-4-5","stream":true,"messages":[{"role":"user","content":"count to 3"}],"max_tokens":30}'
```

验证点：
- [ ] 响应 `Content-Type` 为 `text/event-stream`
- [ ] 逐行返回 `data: {...}` 格式的 SSE 事件
- [ ] 最后以 `data: [DONE]` 结束
- [ ] `relay_records` 中 token 和 cost 正常记录

---

## 6. 账号冷却（不可用触发）

### 6.1 测试目标

验证上游返回不同错误码时，系统正确触发对应的冷却策略。

### 6.2 冷却策略对照表

| 上游响应 | 冷却类型 | 冷却时长 | 存储位置 |
|----------|----------|----------|----------|
| 402 (billing) | 持久化冷却 | 30min（初次） | `accounts.quota_exhausted` |
| 429 + quota_exceeded | 持久化冷却 | 30min（初次） | `accounts.quota_exhausted` |
| 429 + rate_limit_exceeded | 内存冷却 | 取 retry-after | `RelayAccountSelector.cooldowns` |
| 503 / 529 (overload) | 内存冷却 | 30 秒 | `RelayAccountSelector.cooldowns` |

### 6.3 持久化冷却退避时间表

| quota_fail_count | 冷却时长 |
|-----------------|----------|
| 1 | 30 分钟 |
| 2 | 2 小时 |
| 3 | 6 小时 |
| ≥ 4 | 24 小时 |

### 6.4 测试用例

**TC-COOL-01：402 触发持久化冷却**

1. Mock 上游或手动令账号的 API Key 失效（billing 级别失败）
2. 发送请求触发 402

验证点（数据库）：
```sql
SELECT quota_exhausted, quota_fail_count, quota_next_retry_at, quota_last_reason
FROM accounts WHERE id = {account_id};
```
- [ ] `quota_exhausted = 1`
- [ ] `quota_fail_count = 1`
- [ ] `quota_next_retry_at ≈ NOW() + 30分钟`（±10s）
- [ ] `quota_last_reason` 含 `billing_error`
- [ ] 后续请求不再选中该账号

---

**TC-COOL-02：429 + quota_exceeded 触发持久化冷却**

Mock 响应：
```json
{"error":{"type":"invalid_request_error","code":"insufficient_quota","message":"..."}}
```
HTTP 状态码：429

验证点：
- [ ] 同 TC-COOL-01，`quota_exhausted = 1`
- [ ] `relay_records.upstream_status = 429`

---

**TC-COOL-03：429 + rate_limit 触发内存冷却**

Mock 响应：
```json
{"error":{"type":"rate_limit_error","code":"rate_limit_exceeded"}}
```
响应头：`Retry-After: 60`

验证点：
- [ ] `accounts` 表中 `quota_exhausted` **仍为 0**（数据库不变）
- [ ] 内存 cooldowns 中出现该账号，until ≈ now + 60s
- [ ] 60 秒内，该账号不被选中

---

**TC-COOL-04：503 触发短期内存冷却**

Mock 上游返回 503

验证点：
- [ ] 内存冷却 30 秒（`until ≈ now + 30s`）
- [ ] 30 秒后自动恢复（无需探测）

---

**TC-COOL-05：冷却退避递增验证**

手动操作数据库模拟多次失败：

```sql
-- 模拟第 2 次失败
UPDATE accounts SET quota_fail_count=1 WHERE id={id};
```

然后触发冷却，验证：

| quota_fail_count 触发前 | 预期 quota_next_retry_at 间隔 |
|------------------------|------------------------------|
| 0 → 1 | +30 分钟 |
| 1 → 2 | +2 小时 |
| 2 → 3 | +6 小时 |
| 3 → 4 | +24 小时 |

---

## 7. 账号切换

### 7.1 测试目标

验证活跃账号冷却后，系统自动切换到下一个可用账号，对调用方透明。

### 7.2 测试用例

**TC-SWITCH-01：单账号冷却后切换备用账号**

前置条件：claude-code-group 有 claude-a（正常）、claude-b（正常）

操作：
1. 手动将 claude-a 设为持久化冷却：
   ```sql
   UPDATE accounts SET quota_exhausted=1, quota_fail_count=1,
     quota_next_retry_at=DATE_ADD(NOW(), INTERVAL 30 MINUTE)
   WHERE id={claude-a-id};
   ```
2. 发送请求

验证点：
- [ ] 请求命中 claude-b（claude-a 被跳过）
- [ ] `relay_records.account_id = claude-b-id`
- [ ] 响应正常 200

---

**TC-SWITCH-02：所有账号冷却时的优雅降级**

1. 将所有账号设为 `quota_exhausted = 1`
2. 发送请求

验证点：
- [ ] 返回 503 或 429，错误信息指示无可用账号
- [ ] 错误码合理（不是 500）

---

**TC-SWITCH-03：Tier 优先级切换**

前置条件：
- claude-a（tier 含 pro）
- claude-b（tier 含 max5）

操作：
1. 正常发送请求，确认命中 claude-a（pro 优先）
2. 将 claude-a 设为冷却
3. 再次发送请求

验证点：
- [ ] 冷却前：`relay_records.account_id = claude-a-id`
- [ ] 冷却后：`relay_records.account_id = claude-b-id`（自动降级到 max5 tier）

---

**TC-SWITCH-04：过期账号不被选中**

1. 设置 claude-a：`expiry_time = '2025-01-01T00:00'`，`auto_suspend_expiry = 1`
2. 发送请求

验证点：
- [ ] claude-a 不被选中
- [ ] 请求命中 claude-b

---

**TC-SWITCH-05：切换后原账号冷却期间不被误选**

1. claude-a 冷却，claude-b 正常
2. 连续发送 10 次请求

验证点（数据库）：
```sql
SELECT account_id, COUNT(*) FROM relay_records GROUP BY account_id;
```
- [ ] 所有记录 `account_id = claude-b-id`（无 claude-a）

---

## 8. 自动恢复

### 8.1 测试目标

验证 CooldownProbeService 能按时发探测请求，成功后清除冷却状态，账号重新参与调度。

### 8.2 探测时机

| 冷却类型 | 探测触发时间 |
|----------|-------------|
| 内存冷却 | `probeAt = max(until - 10s, now + seconds/2)` |
| 持久化冷却 | `quota_next_retry_at - 10秒` |

### 8.3 探测请求内容

```
POST {upstream_base_url}/v1/messages   (Claude)
POST {upstream_base_url}/v1/chat/completions  (OpenAI)

Body: {"model":"claude-haiku-4-5-20251001","max_tokens":1,"messages":[{"role":"user","content":"hi"}]}
```

### 8.4 测试用例

**TC-RECOVER-01：内存冷却自动恢复**

1. 触发 claude-a 内存冷却（rate_limit，60s）
2. 等待 ≈50 秒（probeAt = until - 10s）
3. 观察应用日志中的探测请求

验证点：
- [ ] 日志出现探测日志，model=claude-haiku-4-5-20251001
- [ ] 探测成功后：cooldowns 中移除 claude-a
- [ ] 后续正常请求重新命中 claude-a

---

**TC-RECOVER-02：持久化冷却自动恢复（加速测试）**

1. 手动设置 retry_at 为近期（加速等待）：
   ```sql
   UPDATE accounts
   SET quota_exhausted=1, quota_fail_count=1,
       quota_next_retry_at=DATE_ADD(NOW(), INTERVAL 20 SECOND)
   WHERE id={claude-a-id};
   ```
2. 等待 ≈10 秒（retry_at - 10s 触发探测）
3. 确认上游账号实际可用

验证点（数据库）：
```sql
SELECT quota_exhausted, quota_fail_count, quota_next_retry_at FROM accounts WHERE id={id};
```
- [ ] `quota_exhausted = 0`
- [ ] `quota_fail_count = 0`
- [ ] `quota_next_retry_at = NULL`
- [ ] 后续请求重新命中 claude-a

---

**TC-RECOVER-03：探测失败 → 延长冷却**

1. 设置 claude-a 持久化冷却，fail_count=1，retry_at=近期
2. 确认账号上游仍返回 quota 错误（账号实际未恢复）

验证点：
- [ ] `quota_fail_count` 递增到 2
- [ ] `quota_next_retry_at` 延长约 2 小时
- [ ] `quota_exhausted` 仍为 1

---

**TC-RECOVER-04：探测发现 rate_limit（quota 已恢复）→ 降级内存冷却**

前提：账号处于持久化冷却，但上游 quota 已恢复（只是当前 rate limit）

1. 探测时上游返回 429 + rate_limit_exceeded（非 quota 类错误）

验证点：
- [ ] `quota_exhausted` 清除为 0
- [ ] 账号切换为内存冷却（cooldowns 中出现记录）
- [ ] `quota_fail_count` 不再递增

---

**TC-RECOVER-05：probe-enabled=false 时不自动恢复**

1. 关闭 `probe-enabled: false`
2. 触发持久化冷却，等待 retry_at 过期

验证点：
- [ ] 日志无探测记录
- [ ] `quota_exhausted` 不自动清除
- [ ] 只有手动干预（数据库修改）才能恢复

---

## 9. Token 统计与费用计算

### 9.1 测试目标

验证每次请求后，token 用量被正确记录，费用按定价规则 × 分组倍率正确计算并持久化。

### 9.2 计费公式

```
cost = (prompt_tokens × input_price + completion_tokens × output_price) / 1,000,000 × group_ratio
```

> input_price / output_price 单位为 **USD / 1M tokens**

### 9.3 pricing_status 枚举说明

| 值 | 含义 |
|----|------|
| MATCHED | 找到匹配定价规则，cost 计算成功 |
| NOT_FOUND | 无匹配定价规则，cost = NULL |
| USAGE_MISSING | 上游未返回 usage，cost = NULL |

### 9.4 测试用例

**TC-COST-01：基础 Token 记录**

发送请求后查询：
```sql
SELECT prompt_tokens, completion_tokens, total_tokens, usage_json
FROM relay_records ORDER BY created_at DESC LIMIT 1;
```

验证点：
- [ ] `prompt_tokens > 0`
- [ ] `completion_tokens > 0`
- [ ] `total_tokens = prompt_tokens + completion_tokens`
- [ ] `usage_json` 非空，包含上游原始 usage 快照

---

**TC-COST-02：费用计算 - 有匹配定价规则**

前提：定价规则表中存在 `claude-opus-4-5`（如 input=15.0，output=75.0）

验证点：
- [ ] `pricing_status = 'MATCHED'`
- [ ] `pricing_rule_id` 指向正确规则
- [ ] `input_price = 15.0`，`output_price = 75.0`（快照值与规则一致）
- [ ] `group_ratio = 1.0`（当前分组倍率）
- [ ] `cost ≈ (prompt_tokens × 15.0 + completion_tokens × 75.0) / 1000000 × 1.0`（手工验算）

---

**TC-COST-03：分组倍率生效**

1. 将 claude-code-group 的倍率改为 2.0
2. 发送相同请求，记录新 cost

验证点：
- [ ] `group_ratio = 2.0`
- [ ] 新 `cost ≈ 旧 cost × 2`（在同等 token 用量下）

---

**TC-COST-04：无匹配定价规则**

1. 发送 `model=claude-unknown-model`（定价表不存在）

验证点：
- [ ] `pricing_status = 'NOT_FOUND'`
- [ ] `cost = NULL`
- [ ] 请求本身仍然正常转发（计费异常不影响业务）

---

**TC-COST-05：上游未返回 usage**

1. Mock 上游响应不含 `usage` 字段

验证点：
- [ ] `pricing_status = 'USAGE_MISSING'`
- [ ] `prompt_tokens = NULL`，`completion_tokens = NULL`，`cost = NULL`

---

**TC-COST-06：流式请求 Token 统计**

```bash
curl -X POST http://localhost:8080/v1/chat/completions \
  -H "Authorization: Bearer {test-key}" \
  -d '{"model":"claude-opus-4-5","stream":true,"messages":[{"role":"user","content":"count 1 2 3"}],"max_tokens":20}'
```

验证点：
- [ ] `relay_records` 中 token 正常记录（从最后一个 chunk 提取 usage）
- [ ] `cost` 正常计算
- [ ] `latency_ms` 记录从发出到最后一个 chunk 的完整耗时

---

**TC-COST-07：定价快照与规则解耦（历史数据不受影响）**

1. 记录一条请求，记下 `pricing_rule_id` 和 `input_price`
2. 修改该规则的价格
3. 查看原记录

验证点：
- [ ] 原记录的 `input_price`、`output_price` **不随规则修改而变化**（快照已固化）
- [ ] 新请求才使用新价格

---

## 10. 完整端到端链路回归

### 10.1 黄金链路测试（完整场景）

**场景描述**：

| 步骤 | 操作 | 关键验证点 |
|------|------|-----------|
| 1 | 管理员添加 3 个 Claude Code 账号（A/B/C） | 账号正确入库，冷却字段为空 |
| 2 | 创建分组绑定 3 个账号，倍率=1.5 | 分组配置正确 |
| 3 | member 用户生成 API Key，关联该分组 | status=正常，group_id 正确 |
| 4 | 连续发送 9 次请求 | A/B/C 各被选中约 3 次（轮询） |
| 5 | 账号 A 触发 quota 冷却 | quota_exhausted=1，fail_count=1，retry_at≈now+30m |
| 6 | 再次发送 10 次请求 | relay_records 中 account_id 只有 B、C |
| 7 | 加速 retry_at（设为近期），等待探测恢复 A | quota_exhausted=0，fail_count=0 |
| 8 | 再次发送请求，验证 A 重新参与 | relay_records 中重新出现 A |
| 9 | 查看所有记录的费用计算 | 全部 pricing_status=MATCHED，cost 含 1.5 倍率 |

---

### 10.2 稳定性回归

**TC-E2E-STAB-01：100 次连续请求**

- 连续发送 100 次正常请求
- 验证：成功率 100%，无 5xx，relay_records 100 条，cost 均有值

**TC-E2E-STAB-02：混合模型请求**

- 交替发送 claude-opus-4-5 和 gpt-4o 请求
- 验证：各自命中正确平台账号，互不干扰

**TC-E2E-STAB-03：账号恢复后的调度回归**

1. 所有账号冷却 → 验证 503
2. 手动清除冷却状态
3. 发送请求 → 验证正常调度恢复

---

## 11. 附录：curl / SQL 速查

### 发送非流式 Claude 请求

```bash
curl -X POST http://localhost:8080/v1/chat/completions \
  -H "Authorization: Bearer sk-YOUR-API-KEY" \
  -H "Content-Type: application/json" \
  -d '{
    "model": "claude-opus-4-5",
    "messages": [{"role": "user", "content": "hello, say 3 words"}],
    "max_tokens": 20
  }'
```

### 发送流式请求

```bash
curl -X POST http://localhost:8080/v1/chat/completions \
  -H "Authorization: Bearer sk-YOUR-API-KEY" \
  -H "Content-Type: application/json" \
  -d '{
    "model": "claude-opus-4-5",
    "stream": true,
    "messages": [{"role": "user", "content": "count to 5"}],
    "max_tokens": 50
  }'
```

### 查看账号冷却状态

```sql
SELECT id, platform, account_type, quota_exhausted, quota_fail_count,
       quota_next_retry_at, quota_last_reason, temp_disabled, priority_value
FROM accounts
ORDER BY id;
```

### 查看最近转发记录

```sql
SELECT id, account_id, model, provider, upstream_status,
       prompt_tokens, completion_tokens, cost,
       pricing_status, group_ratio, latency_ms, created_at
FROM relay_records
ORDER BY created_at DESC
LIMIT 20;
```

### 手动设置持久化冷却（加速测试冷却逻辑）

```sql
UPDATE accounts
SET quota_exhausted = 1,
    quota_fail_count = 1,
    quota_next_retry_at = DATE_ADD(NOW(), INTERVAL 30 MINUTE),
    quota_last_reason = 'manual_test_billing_error'
WHERE id = {account_id};
```

### 手动加速恢复（触发探测）

```sql
-- 设置 retry_at 为 20 秒后，等 10 秒探测触发
UPDATE accounts
SET quota_next_retry_at = DATE_ADD(NOW(), INTERVAL 20 SECOND)
WHERE id = {account_id};
```

### 手动清除所有冷却（全量重置）

```sql
UPDATE accounts
SET quota_exhausted=0, quota_fail_count=0,
    quota_next_retry_at=NULL, quota_last_reason=NULL,
    quota_updated_at=NULL, temp_disabled=0;
```

---

*文档生成于 2026-03-21，覆盖 RelayService / QuotaStateManager / CooldownProbeService / RelayAccountSelector / RelayRecordService 核心链路。*
