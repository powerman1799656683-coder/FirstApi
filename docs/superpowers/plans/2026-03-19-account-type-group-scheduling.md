# Account Type Group Scheduling Implementation Plan

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在现有 FirstApi 中实现“分组绑定单一账号类型 + 按分组类型调度 + 额度冷却自动/手动恢复”的后端闭环。

**Architecture:** 基于现有 `groups`、`accounts` 与 Relay 调度骨架最小改动扩展，不新增关系表。通过在分组层提供 `accountType` 约束、在账号层持久化额度冷却运行态，并将 Relay 入口串联 API Key 分组校验与平台/类型筛选，保证不发生类型回退。

**Tech Stack:** Spring Boot 4, JdbcTemplate, MySQL/H2, JUnit 5 + Mockito

---

### Task 1: 数据库与模型字段扩展

**Files:**
- Modify: `backend/src/main/resources/schema.sql`
- Modify: `backend/src/test/resources/schema-test.sql`
- Modify: `backend/src/main/java/com/firstapi/backend/model/GroupItem.java`
- Modify: `backend/src/main/java/com/firstapi/backend/model/AccountItem.java`
- Modify: `backend/src/main/java/com/firstapi/backend/repository/GroupRepository.java`
- Modify: `backend/src/main/java/com/firstapi/backend/repository/AccountRepository.java`

- [ ] **Step 1: 为新字段补失败测试（模型/仓储映射相关）**

Run: `mvn -Dtest=RelayAccountSelectorTest test`
Expected: 失败（缺少 accountType/quota 字段导致行为不满足）

- [ ] **Step 2: 在 schema 与 H2 schema 增加 groups.account_type + accounts quota 运行态字段**

- [ ] **Step 3: 补齐 GroupItem/AccountItem 与 Repository 列映射**

- [ ] **Step 4: 运行相关测试确保映射正确**

Run: `mvn -Dtest=RelayAccountSelectorTest,RelayControllerTest test`
Expected: 通过或只剩行为约束类失败

### Task 2: 分组 accountType 校验落地

**Files:**
- Modify: `backend/src/main/java/com/firstapi/backend/service/GroupService.java`
- Modify: `backend/src/main/java/com/firstapi/backend/model/GroupItem.java`
- Add: `backend/src/test/java/com/firstapi/backend/service/GroupServiceTest.java`

- [ ] **Step 1: 写失败测试（创建/更新分组必须提供合法 accountType，且受平台约束）**

Run: `mvn -Dtest=GroupServiceTest test`
Expected: 失败（当前未校验）

- [ ] **Step 2: 实现平台 -> accountType 白名单与默认值规则**

- [ ] **Step 3: 运行测试确认通过**

Run: `mvn -Dtest=GroupServiceTest test`
Expected: 全部通过

### Task 3: Relay 按 API Key 分组约束调度

**Files:**
- Modify: `backend/src/main/java/com/firstapi/backend/service/RelayService.java`
- Modify: `backend/src/main/java/com/firstapi/backend/service/RelayAccountSelector.java`
- Modify: `backend/src/main/java/com/firstapi/backend/service/OpenAiRelayAdapter.java`
- Modify: `backend/src/main/java/com/firstapi/backend/service/ClaudeRelayAdapter.java`
- Modify: `backend/src/test/java/com/firstapi/backend/service/RelayAccountSelectorTest.java`
- Modify: `backend/src/test/java/com/firstapi/backend/controller/RelayControllerTest.java`

- [ ] **Step 1: 写失败测试（缺组/组不存在/组平台不匹配时拒绝；仅同类型池调度）**

Run: `mvn -Dtest=RelayAccountSelectorTest,RelayControllerTest test`
Expected: 失败（现有逻辑未按 group.accountType 限制）

- [ ] **Step 2: 在 RelayService 串联 API Key group -> Group 查询 -> 平台校验 -> accountType 传递**

- [ ] **Step 3: 在 RelayAccountSelector 按 platform + accountType + 可用状态筛选，不做类型回退**

- [ ] **Step 4: 运行测试验证**

Run: `mvn -Dtest=RelayAccountSelectorTest,RelayControllerTest test`
Expected: 全部通过

### Task 4: 额度冷却持久化与恢复

**Files:**
- Modify: `backend/src/main/java/com/firstapi/backend/service/RelayService.java`
- Modify: `backend/src/main/java/com/firstapi/backend/service/RelayAccountSelector.java`
- Modify: `backend/src/main/java/com/firstapi/backend/service/AccountService.java`
- Modify: `backend/src/main/java/com/firstapi/backend/controller/AccountController.java`
- Modify: `backend/src/test/java/com/firstapi/backend/service/RelayAccountSelectorTest.java`
- Add: `backend/src/test/java/com/firstapi/backend/service/RelayServiceTest.java`
- Add: `backend/src/test/java/com/firstapi/backend/service/AccountQuotaRecoveryTest.java`

- [ ] **Step 1: 写失败测试（命中 quota 错误 -> 冷却；到期允许重试；手动恢复清状态）**

Run: `mvn -Dtest=RelayServiceTest,AccountQuotaRecoveryTest,RelayAccountSelectorTest test`
Expected: 失败（当前仍是内存 cooldown）

- [ ] **Step 2: 实现 quotaExhausted/backoff/retryAt 持久化逻辑与自动恢复清理**

- [ ] **Step 3: 增加手动恢复接口 `POST /api/admin/accounts/{id}/quota/recover`**

- [ ] **Step 4: 运行测试验证**

Run: `mvn -Dtest=RelayServiceTest,AccountQuotaRecoveryTest,RelayAccountSelectorTest test`
Expected: 全部通过

### Task 5: 回归验证

**Files:**
- Modify: `backend/src/test/java/com/firstapi/backend/service/AccountServiceTest.java` (如需适配构造器/行为)
- Modify: `backend/src/test/resources/schema-test.sql` (补充测试初始数据)

- [ ] **Step 1: 运行后端目标回归**

Run: `mvn -Dtest=GroupServiceTest,RelayAccountSelectorTest,RelayServiceTest,RelayControllerTest,AccountQuotaRecoveryTest,AccountServiceTest test`
Expected: 通过

- [ ] **Step 2: 运行后端完整测试（时间允许）**

Run: `mvn test`
Expected: 通过或记录已有非本次引入失败

