
## FirstApi 项目 - 订阅计费系统文件清单

### 1. 订阅服务层 (Service)

**SubscriptionService.java** - 订阅额度管理
- deductQuota(uid, groupId, cost) - 从订阅额度中扣费
- hasQuotaRemaining(uid, groupId) - 检查额度是否充足  
- getActiveSubscription(uid, groupId) - 获取活跃订阅
- 精度: 10位小数

**UserService.java** - 用户余额管理
- deductByAuthUserId(authUserId, cost) - 从余额中扣费 [有bug: 无下限保护]
- checkBalanceByAuthUserId(authUserId) - 检查余额是否 > 0
- adjustBalance(id, amount) - 手动调整余额
- parseBalance() / formatBalance() - 解析和格式化余额

**RelayRecordService.java** - 核心扣费处理 [★★★ 最重要]
- record(apiKey, route, result, model, group) - 扣费主入口
  * 第 115-126 行: 扣费优先级判断
  * 关键逻辑: hasQuotaRemaining() ? deductQuota() : deductByAuthUserId()
  * 问题: 扣费异常被静默吞掉

**CostCalculationService.java** - 成本计算与定价匹配
- calculate(inputPrice, outputPrice, promptTokens, completionTokens, groupRatio)
  * 公式: (inputTokens × inputPrice + outputTokens × outputPrice) ÷ 1M × ratio
  * 精度: 10位小数
- matchPricing(modelName)
  * 优先级: 精确匹配 > 前缀匹配 > 平台默认 > 未找到
- inferProvider(modelName)
  * 识别 gpt-, o1, o3, codex → OpenAI
  * 识别 claude- → Anthropic

**DailyQuotaService.java** - 每日配额检查
- checkDailyQuota(ownerId, dailyLimit) - 检查今日是否超额
- addCost(ownerId, cost) - 记录今日消费
- getTodayUsage(ownerId) - 查询今日已消费
- listUserQuotaSummary() - 用户配额摘要

**MySubscriptionService.java** - 用户套餐展示
- getSubscription() - 获取用户订阅信息
- renew() - 续费

### 2. 数据访问层 (Repository)

**SubscriptionRepository.java** - 订阅数据访问
- findActiveByUidAndGroup(uid, groupId) - 查询活跃订阅 [核心查询]
- findActiveListByUid(uid) - 查询所有活跃订阅

**UserRepository.java** - 用户数据访问
- findById(), findByUsername() - 基本查询

**RelayRecordRepository.java** - 用量记录持久化
- save(item) - 保存用量记录

**DailyQuotaUsageRepository.java** - 每日用量数据访问
- getTotalUsedCost(ownerId, date) - 查询指定日期已消费
- addCost(ownerId, date, cost) - 累加消费
- addCostByOwner(ownerId, date, cost) - 用户级别累加

**GroupRepository.java** - 分组数据访问

**ModelPricingRepository.java** - 定价规则查询
- findAllEnabledEffective() - 查询所有启用的定价

### 3. 数据模型 (Model)

**SubscriptionItem.java** - 订阅配置
- usage_text: 格式 "¥X.XXXXXXXXXX / ¥Y.XXXXXXXXXX"
- progress_value: 0-100 (%)
- daily_limit: 每日限额 (可选)
- status_name: "正常" = 有效

**UserItem.java** - 用户信息
- balance: 格式 "¥X.XXXXXXXXXX" (10位小数)

**RelayRecordItem.java** - 用量记录
- cost: 消耗金额 (BigDecimal, 10位小数)
- promptTokens, completionTokens, totalTokens
- inputPrice, outputPrice (定价快照)
- pricingStatus: MATCHED / NOT_FOUND / USAGE_MISSING
- createdAtTs: 时间戳

**GroupItem.java** - 分组配置
- rate: 倍率 (有实现)
- billingType, billingAmount: (无实现) [待完成]

### 4. Claude API 中转

**ClaudeRelayAdapter.java** - Claude 请求适配
- relay(request, route, accountType) - 非流式中转
- relayMessages(body, route, accountType, anthropicVersion) - Messages API 非流式
- relayMessagesStreaming(...) - Messages API 流式
- toClaudeRequest() - OpenAI → Claude 请求转换
- toOpenAiResponse() - Claude → OpenAI 响应转换

**RelayService.java** - 转发路由总控
- relayResponsesApi() - Responses API 非流式
- relayResponsesApiStreaming() - Responses API 流式
- relayChatCompletion() - Chat Completions 非流式
- relayChatCompletionStreaming() - Chat Completions 流式
- relayClaudeMessages() - Claude Messages 非流式
- relayClaudeMessagesStreaming() - Claude Messages 流式
- checkBilling() - 预检查 (余额/订阅/每日配额)

**OpenAiRelayAdapter.java** - OpenAI 请求适配

### 5. 数据库设计

**schema.sql** - 完整数据库架构

关键表:
- subscriptions: 订阅额度配置
  * usage_text: "¥已消耗 / ¥总额"
  * daily_limit: 每日限额
  * status_name: "正常" = 有效

- relay_records: API 调用记录
  * cost: 消耗金额
  * promptTokens, completionTokens
  * pricingStatus: 定价匹配状态

- daily_quota_usage: 每日消费统计
  * group_id: 0=用户汇总, >0=分组
  * used_cost: 当日消耗

- users: 用户余额
  * balance: "¥X.XXXXXXXXXX"

### 6. 控制器层 (Controller)

**SubscriptionController.java** - 订阅管理
- GET /api/admin/subscriptions
- POST /api/admin/subscriptions
- PUT /api/admin/subscriptions/{id}
- DELETE /api/admin/subscriptions/{id}

**MySubscriptionController.java** - 用户套餐展示
- GET /api/user/my-subscription

**UserQuotaController.java** - 每日配额查询
- GET /api/user/quota

**RelayController.java** - API 中转入口
- POST /v1/chat/completions
- POST /v1/messages (Claude)
- GET /v1/models

**UserController.java** - 用户管理
- CRUD 操作

---

## 扣费流程核心代码位置

**文件:** RelayRecordService.java (第 115-126 行)



---

## 费用计算关键参数

**精度:** BigDecimal, 10位小数
- subscriptions.usage_text: "¥0.1234567890 / ¥100.0000000000"
- users.balance: "¥0.1234567890"
- relay_records.cost: 0.1234567890 (BigDecimal)

**公式:**


---

## Codex 与 Claude 配置

**Codex 识别:**
- 文件: CostCalculationService.inferProvider() 第 73-74 行
- 识别规则: startsWith("codex") || startsWith("gpt-") → "OpenAI"
- 支持模型: codex-mini, gpt-5.3-codex

**Claude 支持:**
- 模型: claude-opus-4-6, claude-sonnet-4-6, claude-sonnet-3-5, claude-opus-3
- 中转: 自动格式转换 (OpenAI ↔ Claude)

---

## 已知问题列表

### 🔴 严重 (需立即修复)

1. **余额可能扣成负数**
   - 位置: UserService.java#deductByAuthUserId() 第 184 行
   - 代码: 
   - 问题: 无下限检查
   - 风险: 余额变成负数

2. **扣费异常被静默吞掉**
   - 位置: RelayRecordService.java#record() 第 115-126 行
   - 问题: 异常不反抛，仍然落记录
   - 风险: "有记录无扣费" 账实严重不符

### 🟠 中等 (应该修复)

3. **分组计费类型未实现**
   - 位置: GroupItem 定义，RelayRecordService 未用
   - 问题: billingType, billingAmount 有定义但无逻辑
   - 风险: 包年、企业折扣无法使用

4. **定价缓存无热更新**
   - 位置: CostCalculationService.refreshCache()
   - 问题: 仅启动时加载，修改定价需重启
   - 风险: 定价修改不实时生效

### 🟡 轻微 (可以改进)

5. **每日配额检查位置靠后**
   - 位置: DailyQuotaService.checkDailyQuota()
   - 问题: 位置在 RelayService.checkBilling() 后期
   - 风险: 超额请求仍会转发到上游

---

生成时间: 2026-04-01

