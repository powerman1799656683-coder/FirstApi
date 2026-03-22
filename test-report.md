# API 中转核心流程测试报告

**测试日期**: 2026-03-21
**测试环境**: localhost:8080 (Backend) + localhost:3306 (MySQL)
**测试账号**: admin / AdminPass123!、member / UserPass123!
**测试限制**: 账号凭证为虚拟值，上游会返回 401，无法测试成功的 token 统计和流式响应

---

## 问题记录

| # | 测试用例 | 严重程度 | 问题描述 | 状态 |
|---|---------|---------|---------|------|
| 1 | TC-ACC-03 | **高** | 账号创建接口缺少**平台与账号类型匹配校验**。openai + Gemini Advanced 可以创建成功。（分组创建接口有此校验，但账号接口缺失） | 待修复 |
| 2 | TC-ACC-04 | **高** | 账号创建接口**不校验 credential 唯一性**，相同凭证可重复创建多个账号 | 待修复 |
| 3 | TC-ACC-03 | **中** | 当平台与类型不匹配的类型名包含"plus"/"code"时，优先触发 OAuth 校验而非平台匹配校验，**错误提示不准确**（显示"OAuth account does not accept plain credential"而非"类型不匹配"） | 待修复 |
| 4 | TC-GRP-03 | **高** | 分组倍率（rate）字段**不校验数据类型**，接受任意字符串（"abc"、"-2.5"），会在计费时导致异常 | 待修复 |
| 5 | TC-SCH-03 | **低** | 分组平台不匹配返回 **400** 而非测试方案预期的 **403**。功能正确但 HTTP 语义可斟酌 | 建议优化 |
| 6 | TC-RECOVER-05 | **中** | 测试方案认为 probe-enabled=false 时"只有手动干预才能恢复"，但实际存在**惰性自动恢复**机制（RetryAt 过期后下次请求触发 isInCooldown 时自动清除）。测试方案与实现不一致 | 需确认设计意图 |
| 7 | TC-GRP-04 | **低** | account_group_bindings 表为空，分组与账号的关联是通过 platform + account_type **隐式匹配**，非显式绑定。测试方案中的 SQL 查询不适用 | 文档差异 |

---

## 测试结果详情

### 2. 账号添加 (TC-ACC)

#### TC-ACC-01：正常添加账号 - PASS
- 现有 claude-a(41) / claude-b(42) / claude-c(43) 满足测试要求
- quota_exhausted=0, quota_fail_count=0, quota_next_retry_at=NULL
- temp_disabled=0, priority_value 和 concurrency 正确
- credential 以 `enc:` 前缀加密存储（AES-256-GCM）

#### TC-ACC-02：添加 OpenAI 账号 - PASS
- 成功创建 openai 平台 / ChatGPT Pro 类型账号
- platform=openai, accountType=ChatGPT Pro, authMethod=API Key

#### TC-ACC-03：非法账号类型被拒绝 - **FAIL**
- openai + Gemini Advanced：**创建成功**（应拒绝）
- anthropic + ChatGPT Plus：拒绝了但错误信息是 OAuth 相关而非类型不匹配

#### TC-ACC-04：添加重复 API Key - **FAIL**
- 相同 credential 创建了两个账号，**无唯一性校验**

---

### 3. 分组配置 (TC-GRP)

#### TC-GRP-01：创建分组并绑定账号类型 - PASS
- claude-code-group: platform=anthropic, accountType=Claude Code, rate=1.0, modelRouting=true
- claude-max-group: platform=anthropic, accountType=Claude Max (Plus), rate=1.5
- openai-group: platform=openai, accountType=ChatGPT Plus, rate=1.0

#### TC-GRP-02：平台与账号类型不匹配被拒绝 - PASS
- 返回清晰错误："accountType 非法，平台 anthropic 仅支持: Claude Code, Claude Max (Plus)"

#### TC-GRP-03：倍率非法值验证 - **FAIL**
- 负数 "-2.5"：创建成功（应拒绝）
- 字母 "abc"：创建成功且存入数据库（应拒绝）

#### TC-GRP-04：分组账号关联验证 - PASS（设计差异）
- 关联通过 platform + account_type 隐式匹配，非 account_group_bindings 表
- claude-code-group 正确匹配到 claude-a + claude-b（2个 anthropic/Claude Code 账号）

---

### 4. API Key (TC-KEY)

#### TC-KEY-01：普通用户生成 API Key - PASS
- member 成功创建 relay-test-key(67)，关联 claude-code-group(30)
- 返回完整 key：sk-firstapi-d481c58b866641acb521
- status=正常, lastUsed="-"（初始未使用）

#### TC-KEY-02：禁用 Key 后无法访问 - PASS
- PUT 更新 status="禁用" 成功
- 使用禁用 Key 发请求：返回 401 + invalid_api_key

#### TC-KEY-03：无效 Bearer Token 被拒绝 - PASS
- 返回 401 + invalid_api_key

---

### 5. 请求调度 (TC-SCH)

#### TC-SCH-01：基础路由 - Claude 模型 - PASS
- 请求正确路由到 Claude 上游（provider_name=claude）
- relay_records 记录完整（account_id, model_name, status_code, latency_ms）
- api_keys.last_used 被正确更新
- 注：上游返回 401 是虚拟凭证导致，路由逻辑本身正确

#### TC-SCH-02：不支持的模型被拒绝 - PASS
- 返回 400 + unsupported_model

#### TC-SCH-03：分组平台不匹配 - PASS（HTTP 码差异）
- openai-group key + claude 模型 → 返回 400 + group_platform_mismatch
- 注：返回 400 而非预期 403，功能正确

#### TC-SCH-04：轮询调度验证 - PASS
- 6 次请求：claude-a 3 次 + claude-b 3 次，完美均衡轮询
- 20 次请求：claude-a 10 次 + claude-b 10 次，持续均衡

#### TC-SCH-05：并发控制验证 - 未测试
- 需要真正的并发请求（串行 curl 无法触发并发限制）

#### TC-SCH-06：无可用账号时的响应 - PASS
- 所有账号 temp_disabled=1 → 返回 503 + no_upstream_account
- 所有账号 quota_exhausted=1 → 返回 503 + no_upstream_account
- 不是 500 内部错误

#### TC-SCH-07：流式请求正常转发 - 部分通过
- 流式请求路由正确（上游 401 是虚拟凭证问题）
- 无法验证 SSE 格式和 token 统计（需要有效凭证）

---

### 6. 账号冷却 (TC-COOL)

#### TC-COOL-01~02：402/429 持久化冷却 - 无法直接测试
- 需要 mock 上游或真实触发，通过数据库模拟验证了效果

#### 数据库模拟验证 - PASS
- 设置 quota_exhausted=1 后，账号在后续请求中被跳过
- 验证了 isInCooldown() 过滤逻辑正确

#### TC-COOL-05：冷却退避递增 - PASS（代码验证）
- backoffMinutes(1)=30, (2)=120, (3)=360, (>=4)=1440
- 代码实现与测试方案一致

---

### 7. 账号切换 (TC-SWITCH)

#### TC-SWITCH-01：单账号冷却后切换 - PASS
- claude-a 冷却后，6 次请求全部命中 claude-b

#### TC-SWITCH-02：所有账号冷却降级 - PASS
- 返回 503 + no_upstream_account

#### TC-SWITCH-04：过期账号不被选中 - PASS
- claude-a 设置 expiry_time=2025-01-01 + auto_suspend_expiry=1
- 4 次请求全部命中 claude-b

#### TC-SWITCH-05：冷却期间不被误选 - PASS（包含在 TC-SWITCH-01）
- claude-a 冷却中，连续多次请求只命中 claude-b

---

### 8. 自动恢复 (TC-RECOVER)

#### TC-RECOVER-02/05：持久化冷却恢复 - 部分通过
- probe-enabled 默认为 false，CooldownProbeService 不主动探测
- 但 RelayAccountSelector.isInCooldown() 有**惰性恢复**逻辑：
  - 当 quota_next_retry_at 过期后，下次请求时自动清除 quota_exhausted
  - 实测：设置 retry_at=5秒后，15秒后发请求，claude-a 自动恢复（quota_exhausted=0, fail_count=0）

---

### 9. Token 统计与费用计算 (TC-COST)

#### TC-COST-01：基础 Token 记录 - 部分通过
- 上游 401 无 usage → prompt_tokens/completion_tokens/total_tokens 均为 NULL
- pricing_status = USAGE_MISSING（正确）
- 需要成功请求才能验证 token 记录

#### TC-COST-02：定价规则匹配 - PASS
- claude-sonnet-4-5-20250514 正确匹配规则 claude-sonnet-4-* (id=2)
- input_price=21.12, output_price=105.60（CNY 计价）
- group_ratio=1.00（反映 claude-code-group 倍率）

#### TC-COST-04：无匹配定价规则 - 部分通过
- claude-opus-4-5-20250514 无匹配规则 → pricing_rule_id=NULL
- pricing_status=USAGE_MISSING（因上游 401，无法区分 NOT_FOUND）

---

### 10. 端到端链路回归

#### E2E-STAB-01：连续请求稳定性 - PASS
- 20 次连续请求：0 个 5xx，20 条 relay_records
- claude-a 10 次 + claude-b 10 次，完美轮询

#### E2E-STAB-03：冷却恢复后调度回归 - PASS
- 所有冷却 → 503 → 清除冷却 → 正常调度恢复 → 轮询均衡

---

## 测试总结

| 类别 | 总用例 | 通过 | 失败 | 未测试 | 部分通过 |
|------|--------|------|------|--------|---------|
| 账号添加 | 4 | 2 | 2 | 0 | 0 |
| 分组配置 | 4 | 3 | 1 | 0 | 0 |
| API Key | 3 | 3 | 0 | 0 | 0 |
| 请求调度 | 7 | 4 | 0 | 1 | 2 |
| 账号冷却 | 5 | 2 | 0 | 2 | 1 |
| 账号切换 | 5 | 4 | 0 | 1 | 0 |
| 自动恢复 | 5 | 1 | 0 | 3 | 1 |
| Token 计费 | 7 | 1 | 0 | 3 | 3 |
| 端到端 | 3 | 2 | 0 | 1 | 0 |
| **合计** | **43** | **22** | **3** | **11** | **7** |

### 需优先修复的问题（按严重程度排序）

1. **[高] 账号创建缺少平台-类型匹配校验**（TC-ACC-03）
2. **[高] 账号创建不校验 credential 唯一性**（TC-ACC-04）
3. **[高] 分组倍率字段不校验数据类型**（TC-GRP-03）
4. **[中] OAuth 校验优先级高于平台匹配导致错误提示不准确**（TC-ACC-03）
5. **[中] 惰性恢复机制与测试方案描述不一致**（TC-RECOVER-05）

### 无法测试的项目（需要真实凭证）

- TC-COST 的成功请求 token 统计和费用计算
- TC-SCH-07 流式响应 SSE 格式
- TC-COOL-01~04 上游错误触发的自动冷却
- TC-RECOVER-01~04 探测恢复流程
- TC-SCH-05 并发控制（需要并行请求）
