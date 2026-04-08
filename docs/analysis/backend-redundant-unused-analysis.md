# 后端冗余与无用代码分析

## 范围说明

- 本文档基于当前仓库源码做只读分析，不包含任何代码修改。
- 分析重点是后端旧接口链路、重复服务逻辑、疑似闲置方法和并存表结构。
- 由于当前仓库存在大量未提交改动，本文只记录高置信度结论和需要人工确认的候选项。

## 结论摘要

- 后端最明显的遗留链路是 `MySubscriptionController` + `MySubscriptionService` + `my_subscription` 表。
- 当前前端正式页面已经改为读取 `/api/user/quota/summary`，而不是 `/api/user/subscription`。
- `UserQuotaController` 中除 `/summary` 外，其余接口在当前前端源码里没有找到调用点，属于“仓库内疑似闲置 API”。
- `RecordsService` 和 `MyRecordsService` 存在高重复度的记录映射与格式化逻辑。
- `RecordsData` 和 `MyRecordsData` 也存在结构相近的重复 DTO 设计。

## 高置信度旧接口链路

### 1. `MySubscriptionController` 与 `MySubscriptionService`

**证据**

- `backend/src/main/java/com/firstapi/backend/controller/MySubscriptionController.java:11-29`
  - 暴露 `GET /api/user/subscription`
  - 暴露 `POST /api/user/subscription/renew`
- `backend/src/main/java/com/firstapi/backend/service/MySubscriptionService.java:18-88`
  - 通过 `my_subscription` 表读取和写入订阅展示数据
- `backend/src/main/resources/schema.sql:232`
  - 仍保留 `create table if not exists my_subscription`

**与现行链路的对照**

- 当前前端正式订阅页 `frontend/src/pages/MySubscription.jsx:366` 请求的是 `/user/quota/summary`
- 对应的新后端入口是 `backend/src/main/java/com/firstapi/backend/controller/UserQuotaController.java:47-73`
- schema 中同时存在：
  - `subscriptions` 表，见 `schema.sql:55`
  - `my_subscription` 表，见 `schema.sql:232`
  - `subscription_plans` 表，见 `schema.sql:375`

**判断**

- 从前端正式调用关系来看，`/api/user/subscription` 这条链路已经不是主链。
- `my_subscription` 更像旧的“展示型订阅数据表”，而不是当前正式配额体系的一部分。

**风险判断**

- 这组代码很像遗留实现，但是否能直接删除，还需要确认有没有外部调用方或历史兼容需求。

### 2. `renewForUser(Long userId)` 疑似闲置方法

**证据**

- `backend/src/main/java/com/firstapi/backend/service/MySubscriptionService.java:62-64` 定义了 `renewForUser(Long userId)`。
- 在当前仓库范围内，没有发现其它代码调用这个方法。

**判断**

- 这是一个高置信度的疑似闲置方法。
- 如果旧订阅链确认废弃，它会一起成为可清理对象。

## 仓库内疑似闲置 API

### `UserQuotaController` 中只有 `/summary` 被当前前端明确使用

**证据**

- `backend/src/main/java/com/firstapi/backend/controller/UserQuotaController.java` 定义了四组接口：
  - `GET /api/user/quota`，见 `:30-45`
  - `GET /api/user/quota/summary`，见 `:47-73`
  - `PUT /api/user/quota/daily-limit/{subscriptionId}`，见 `:75-113`
  - `GET /api/user/quota/usage-history`，见 `:115-125`
- 当前前端源码中，明确能找到调用的是：
  - `frontend/src/pages/MySubscription.jsx:366` -> `/user/quota/summary`
- 在当前前端源码里，没有找到 `/user/quota`、`/user/quota/daily-limit/...`、`/user/quota/usage-history` 的调用。

**判断**

- 从“当前仓库内前端消费关系”来看，`/summary` 是主链，其他三个接口处于未见调用状态。
- 这不等于它们一定无用，因为仍可能服务于外部客户端、未来功能或手工调试。

**风险判断**

- 这组接口应被归类为“疑似闲置 API”，而不是立即判定为死代码。

## 明显重复的服务逻辑

### 1. `RecordsService` 与 `MyRecordsService`

**证据**

- `backend/src/main/java/com/firstapi/backend/service/RecordsService.java:73-95`
  - 把 `RelayRecordItem` 映射成页面展示记录
- `backend/src/main/java/com/firstapi/backend/service/MyRecordsService.java:39-58`
  - 也在做同类映射
- 两个类都重复实现了以下逻辑：
  - token 数格式化
  - cost 字符串格式化
  - `USAGE_MISSING` / `NOT_FOUND` 的展示映射
  - 状态字段转成“成功 / 失败”
  - 时间字段兜底
- 两个类都各自维护了非常接近的帮助方法：
  - `formatTokens(...)`
  - `formatLatency(...)`
  - `formatNumber(...)`
  - `formatCost(...)`

**判断**

- 这不是正常的“不同业务类恰好相似”，而是明确的重复数据整形逻辑。
- 后续无论是增加新的 pricing 状态、调整金额精度，还是统一中文展示，两个类都需要同步改动。

**风险判断**

- 这类代码不是无用代码，但属于维护成本较高的重复实现。
- 它是后端最值得后续做抽取或共享封装的点之一。

## DTO / 展示模型层的重复

### 1. `RecordsData` 与 `MyRecordsData`

**证据**

- `backend/src/main/java/com/firstapi/backend/model/RecordsData.java`
- `backend/src/main/java/com/firstapi/backend/model/MyRecordsData.java`

**表现**

- 两者都维护了自己的 `StatCard`
- 两者都维护了自己的 `RecordItem`
- 字段不完全一致，但整体职责和展示语义高度相近

**判断**

- 这属于“结构重复”而不是“完全可合并”。
- 它说明 admin/user 两套记录展示在演进过程中逐渐长成了两套平行模型。

**风险判断**

- 短期内不一定值得强行合并。
- 但如果后续继续扩展记录页能力，这两个模型会持续制造同步成本。

## 并存表结构带来的遗留信号

### `subscriptions` / `my_subscription` / `subscription_plans`

**证据**

- `schema.sql:55` -> `subscriptions`
- `schema.sql:232` -> `my_subscription`
- `schema.sql:375` -> `subscription_plans`

**判断**

- 从命名和当前前端调用关系来看，这三者并不是单纯的同义重复。
- 但其中 `my_subscription` 所承载的旧展示型职责，已经很可能被新的“订阅 + 订阅等级 + 每日配额”链路取代。

**风险判断**

- 表结构层面的清理必须最后做，前提是先确认旧接口链路已无调用方。

## 本次不建议误判为冗余的部分

- `AnnouncementController` 与 `MyAnnouncementController`
- `RecordsController` 与 `MyRecordsController`
- `SubscriptionController` 与 `MySubscriptionController`

这些类在命名上看似成对出现，但并不都属于同一种冗余：

- `AnnouncementController` / `MyAnnouncementController` 更像 admin/user 视角分离
- `RecordsController` / `MyRecordsController` 的问题主要在内部格式化逻辑重复，不是控制器本身无用
- 真正更像旧链路的是 `MySubscriptionController` 这一条，因为前端正式页面已切换到新的配额摘要接口

## 建议的后续确认顺序

1. 先确认 `/api/user/subscription` 是否还有任何外部调用方。
2. 再确认 `my_subscription` 表是否仍承载生产数据。
3. 然后再评估 `UserQuotaController` 中三个未见前端消费的接口是否保留。
4. 最后再处理 `RecordsService` / `MyRecordsService` 这类活代码重复问题。
