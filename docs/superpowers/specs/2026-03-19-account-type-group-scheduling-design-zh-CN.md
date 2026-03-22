# 账号类型 + 分组调度实现文档（简化稳态版）

## 1. 目标与原则

### 1.1 目标

基于当前 `FirstApi` 代码结构，落地以下业务规则：

1. 一个账号只属于一个账号类型。
2. 一个账号类型下有多个账号。
3. 分组绑定一个平台，并且只选择一个账号类型。
4. 调度时只在该分组绑定的账号类型下选号，不做类型回退。
5. 账号额度不足时自动关闭；额度恢复后支持自动恢复与手动恢复。

### 1.2 设计原则

1. 最小改动：复用已有 `accounts.account_type` 字段，不新增复杂关系表。
2. 稳定优先：保持现有 `priority + round-robin + concurrency` 调度骨架。
3. 易观测：额度关闭/恢复状态可在后台明确看到并可人工干预。
4. 可迁移：兼容现有数据，分阶段切换。

---

## 2. 现状基线（与当前代码对应）

### 2.1 已有关键能力

1. 账号已有 `accountType` 字段（前后端均存在）：
   - `frontend/src/pages/Accounts.jsx`
   - `backend/src/main/java/com/firstapi/backend/model/AccountItem.java`
2. 调度器已有稳定骨架：
   - `backend/src/main/java/com/firstapi/backend/service/RelayAccountSelector.java`
   - 当前支持健康过滤、优先级、并发限制、轮询。
3. API Key 已包含分组字段：
   - `backend/src/main/java/com/firstapi/backend/model/ApiKeyItem.java` 的 `group`

### 2.2 现状问题

1. 分组未绑定账号类型，仅有平台和其他配置。
2. 分组页仍有“从分组复制账号”语义，不符合新业务模型。
3. 调度入口未按“API Key 分组 -> 分组账号类型”筛选账号。
4. 缺少“额度不足自动冷却 + 自动恢复 + 手动恢复”的闭环。

---

## 3. 目标业务模型（最终规则）

### 3.1 核心实体关系

1. `Account`（账号）
   - `platform`（平台）
   - `accountType`（账号类型，单值）
2. `Group`（分组）
   - `platform`（平台）
   - `accountType`（分组绑定的账号类型，单值）
3. `ApiKey`
   - `group`（请求使用的分组名）

### 3.2 调度输入约束

1. 每次请求必须带有效 API Key。
2. API Key 必须绑定有效分组名；为空或不存在直接拒绝请求。
3. 分组的平台必须与当前模型路由目标平台一致（不跨平台）。

---

## 4. 数据库与数据模型改造（最小版本）

## 4.1 `groups` 表新增字段

新增：

1. `account_type varchar(64) not null default 'Standard'`

说明：

1. 该字段表示分组绑定的唯一账号类型。
2. 和 `platform` 组合形成分组调度约束。

建议索引：

1. `idx_groups_platform_type(platform, account_type, status_name)`

## 4.2 `accounts` 表新增额度运行态字段

新增：

1. `quota_exhausted tinyint(1) not null default 0`
2. `quota_next_retry_at datetime null`
3. `quota_fail_count int not null default 0`
4. `quota_last_reason varchar(128) null`
5. `quota_updated_at datetime null`

说明：

1. 不替换现有 `status` / `temp_disabled`，而是叠加运行态字段，降低改造风险。
2. `quota_exhausted=1` 表示自动额度冷却中。

建议索引：

1. `idx_accounts_dispatch(platform, account_type, status_name, temp_disabled, quota_exhausted, priority_value)`

## 4.3 Java 模型字段补充

1. `GroupItem` 增加 `accountType`。
2. `GroupItem.Request` 增加 `accountType`。
3. `AccountItem` 增加：
   - `quotaExhausted`
   - `quotaNextRetryAt`
   - `quotaFailCount`
   - `quotaLastReason`
4. `AccountItem.Request` 可增加手动恢复相关字段（可选），推荐走专用接口。

---

## 5. 后端接口与校验设计

## 5.1 分组接口改造

接口：

1. `GET /api/admin/groups`
2. `POST /api/admin/groups`
3. `PUT /api/admin/groups/{id}`

新增/调整字段：

1. 请求体必须支持 `accountType`。
2. 创建和更新时校验：
   - `platform` 非空
   - `accountType` 非空
   - `accountType` 必须属于该平台允许集合

平台账号类型集合建议统一放在后端常量（避免仅前端约束）：

1. Anthropic: `Claude Code`, `Claude Max (Plus)`
2. OpenAI: `ChatGPT Plus`, `ChatGPT Pro`
3. Gemini: `Gemini Advanced`
4. Antigravity: `Standard`

## 5.2 账号管理接口改造

接口：

1. `GET /api/admin/accounts` 返回额度运行态字段。
2. 新增手动恢复接口：
   - `POST /api/admin/accounts/{id}/quota/recover`
3. 可选新增批量恢复：
   - `POST /api/admin/accounts/batch/quota/recover`

手动恢复语义：

1. 将账号设置为可调度：
   - `quota_exhausted = 0`
   - `quota_next_retry_at = null`
   - `quota_fail_count = 0`
   - `quota_last_reason = null`

## 5.3 Relay 请求路径约束

在 `RelayService` 调度入口增加流程：

1. 通过 API Key 读取 `apiKey.group`。
2. 为空或不存在 -> 直接 401/403（按现有错误体系返回）。
3. 读取分组配置（`platform + accountType`）。
4. 当前模型路由平台与分组平台不一致 -> 400。
5. 调用选择器按“平台 + 账号类型 + 可用状态”选号。

---

## 6. 调度策略（不回退类型）

## 6.1 候选账号过滤条件

给定 `group.platform` + `group.accountType`，候选账号必须同时满足：

1. `account.platform == group.platform`
2. `account.accountType == group.accountType`
3. 非禁用状态（沿用当前 `status` 判定）
4. `tempDisabled = false`
5. 未过期（沿用 `expiryTime + autoSuspendExpiry`）
6. 若 `quotaExhausted = true`，则仅当 `quotaNextRetryAt <= now` 才允许重试进入候选

## 6.2 候选内选择策略

保持现有逻辑：

1. 先按 `priorityValue` 升序分层
2. 同优先级层 round-robin
3. 并发上限由 `concurrency` 控制

## 6.3 无可用账号行为

同类型池中无账号时，返回 `503 no_upstream_account`，不做跨类型/跨平台回退。

---

## 7. 额度不足自动关闭与恢复机制

## 7.1 触发条件（按你确认规则：仅看上游错误）

当上游返回以下错误之一，认定额度不足：

1. HTTP 状态码：`402` 或 `429`（限流需结合错误文案区分）
2. 错误文案包含关键字：
   - `insufficient_quota`
   - `quota exceeded`
   - `credit balance is too low`
   - `额度不足` / `余额不足`

注：关键词建议按 provider 维护白名单，避免误伤普通限流。

## 7.2 自动关闭动作

命中后立即更新账号：

1. `quotaExhausted = true`
2. `quotaFailCount += 1`
3. `quotaLastReason = detected_reason`
4. `quotaNextRetryAt = now + backoff(quotaFailCount)`

backoff 建议：

1. 第 1 次：30 分钟
2. 第 2 次：2 小时
3. 第 3 次：6 小时
4. 第 4 次及以上：24 小时

## 7.3 自动恢复机制

1. 当 `now >= quotaNextRetryAt` 时，账号可再次进入候选池进行“自然探测”。
2. 如果探测请求成功：
   - 清空额度冷却状态（恢复为可用）
3. 如果再次命中额度不足：
   - 继续进入下一档 backoff

说明：该机制无需额外定时任务，依赖实际请求流量自然恢复，最简单稳定。

## 7.4 手动恢复机制

管理员可在账号页点击“恢复调度”：

1. 立即清空额度冷却状态。
2. 下一个请求开始重新参与调度。

---

## 8. 前端改造清单

## 8.1 分组管理页（`frontend/src/pages/Groups.jsx`）

必改项：

1. 删除“从分组复制账号”表单项（`copyFromGroup`）。
2. 新增“账号类型”下拉，选项按平台联动。
3. 分组表格增加“账号类型”列。
4. 创建/编辑提交 payload 增加 `accountType`。

交互约束：

1. 平台变更时，账号类型自动重置为该平台默认值。
2. 编辑时允许改账号类型（建议允许，便于运营），但需提示生效影响。

## 8.2 账号管理页（`frontend/src/pages/Accounts.jsx`）

必改项：

1. 列表增加“额度状态”标签：
   - 正常
   - 额度冷却中
2. 列表增加“下次重试时间”字段。
3. 行操作增加“手动恢复”按钮（仅对 `quotaExhausted=true` 显示）。
4. 可选新增筛选项：只看“额度冷却中”。

---

## 9. 与现有系统对接点（代码落点）

## 9.1 后端

1. 分组模型/服务/仓储
   - `backend/src/main/java/com/firstapi/backend/model/GroupItem.java`
   - `backend/src/main/java/com/firstapi/backend/service/GroupService.java`
   - `backend/src/main/java/com/firstapi/backend/repository/GroupRepository.java`
2. 账号模型/服务/仓储
   - `backend/src/main/java/com/firstapi/backend/model/AccountItem.java`
   - `backend/src/main/java/com/firstapi/backend/service/AccountService.java`
   - `backend/src/main/java/com/firstapi/backend/repository/AccountRepository.java`
3. Relay 路径
   - `backend/src/main/java/com/firstapi/backend/service/RelayService.java`
   - `backend/src/main/java/com/firstapi/backend/service/RelayAccountSelector.java`
   - `backend/src/main/java/com/firstapi/backend/service/OpenAiRelayAdapter.java`
   - `backend/src/main/java/com/firstapi/backend/service/ClaudeRelayAdapter.java`
4. 数据库
   - `backend/src/main/resources/schema.sql`

## 9.2 前端

1. 分组页
   - `frontend/src/pages/Groups.jsx`
2. 账号页
   - `frontend/src/pages/Accounts.jsx`

---

## 10. 数据迁移方案

## 10.1 `groups.account_type` 回填

根据平台回填默认账号类型：

1. Anthropic -> `Claude Code`
2. OpenAI -> `ChatGPT Plus`
3. Gemini -> `Gemini Advanced`
4. Antigravity -> `Standard`

## 10.2 兼容旧逻辑

1. `copyFromGroup` 前端字段删除。
2. `account_group_bindings` 暂不强制删除，先不作为调度主依据。
3. 旧接口字段保持兼容，新增字段向后兼容读取。

---

## 11. 测试与验收

## 11.1 后端测试

1. 分组创建/更新必须包含合法 `accountType`。
2. API Key 分组为空或不存在时拒绝请求。
3. 调度仅命中分组绑定类型账号。
4. 额度不足错误触发 `quotaExhausted`。
5. `quotaNextRetryAt` 到期后可再次参与调度。
6. 手动恢复接口能立即恢复账号可调度状态。

## 11.2 前端测试

1. 分组页不再出现“从分组复制账号”。
2. 分组页平台与账号类型联动正确。
3. 账号页正确显示“额度冷却中/下次重试”。
4. 手动恢复按钮调用接口后状态刷新。

## 11.3 验收标准

1. 请求只在 `分组平台 + 分组账号类型` 的账号池内调度。
2. 同类型账号池中额度不足账号会自动退出，恢复后自动或手动回归。
3. 全流程无类型回退行为，规则与产品定义一致。
4. 不影响现有核心调度稳定性（优先级、并发、轮询仍生效）。

---

## 12. 分阶段实施建议（低风险）

## 阶段 A：数据与后台能力

1. 加表字段与模型字段。
2. 打通分组 `accountType` 接口。
3. 打通账号额度冷却字段与手动恢复接口。

## 阶段 B：调度切换

1. Relay 按 API Key 分组加载 `platform + accountType`。
2. 选择器切到“同平台 + 同类型”过滤。
3. 接入额度不足识别与自动冷却。

## 阶段 C：前端收口

1. 分组页替换旧表单项。
2. 账号页增加额度状态与手动恢复操作。
3. 完整回归测试并灰度发布。

---

## 13. 风险与控制

1. 风险：错误把普通限流当额度不足。
   - 控制：按 provider 维护精确关键字，不仅看 429。
2. 风险：分组账号类型配置缺失导致无可用账号。
   - 控制：分组保存时强校验，提供默认值与后台巡检。
3. 风险：冷却时间过长影响可用性。
   - 控制：支持管理员手动恢复；backoff 设置上限 24h。

---

## 14. 最终结论

本方案不引入复杂新关系表，基于现有 `account_type` 直接实现“分组绑定单一账号类型 + 同类型池调度 + 额度冷却闭环”，是当前版本最容易实现且最不容易出现系统性问题的路径。

