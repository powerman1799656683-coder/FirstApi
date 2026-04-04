
# FirstApi 订阅计费完整分析

## 核心文件

### 订阅相关
- SubscriptionService.java (扣减订阅额度)
- SubscriptionRepository.java (活跃订阅查询)
- SubscriptionItem.java (数据模型)

### 计费相关 **[最关键]**
- RelayRecordService.java (核心扣费处理)
  - 关键代码在第 115-126 行
  - 扣费优先级: 订阅额度 > 用户余额
- CostCalculationService.java (成本计算+定价匹配)
- UserService.java (余额扣减)
- DailyQuotaService.java (每日配额检查)

### Claude API
- ClaudeRelayAdapter.java (Claude 中转)
- RelayService.java (路由总控)

### 数据库
- schema.sql (完整DDL)

## 扣费流程

API请求 → 预检查(余额/订阅/每日配额) → 转发上游 → 获取tokens → 计费:
- 有活跃订阅? → 扣订阅额度
- 否则 → 扣用户余额
→ 记录每日用量 → 落记录

## 成本计算

公式: (inputTokens × inputPrice + outputTokens × outputPrice) ÷ 1,000,000 × groupRatio

精度: BigDecimal 10位小数

## Codex 支持

识别: 以 'codex' 开头 → OpenAI
模型: codex-mini, gpt-5.3-codex
配置: model_pricing 表添加规则

## Claude API

支持: claude-opus-4-6, claude-sonnet-4-6, claude-sonnet-3-5, claude-opus-3

中转: OpenAI 格式 ↔ Claude 格式 (自动转换)

## 已知问题

1. [严重] 余额可能扣成负数 - UserService.deductByAuthUserId() 无下限检查
2. [严重] 扣费异常被吞掉 - RelayRecordService.record() 异常不反抛
3. [中等] 分组计费类型未实现 - billingType 字段有定义但无使用
4. [中等] 定价缓存无热更新 - 修改定价需重启
5. [轻微] 每日配额检查位置靠后

分析时间: 2026-04-01

