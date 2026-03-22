# 控制台信息架构与页面设计（无支付版）

## 1. 目标

- 基于现有 `FirstApi` 项目做结构优化，不做全量重写。
- 明确 Admin/User 的页面边界与路由权限。
- 支付能力完全移出产品范围，不保留隐藏入口或灰度开关。

## 2. 范围

### 在范围内

- 控制台信息架构重排
- 页面保留/重做/下线策略
- 路由与权限基线
- 分阶段实施与验收标准

### 不在范围内

- 任意支付功能（下单、支付回调、支付配置、支付页面）
- 优惠码/兑换码功能恢复

## 3. 信息架构

### Admin 侧导航

1. 总览
- `/dashboard`

2. 监控中心
- `/monitor/system`
- `/monitor/accounts`

3. 业务管理
- `/users`
- `/groups`
- `/subscriptions`

4. 资源管理
- `/accounts`
- `/ips`

5. 内容与审计
- `/announcements`
- `/records`

6. 系统
- `/settings`

### User 侧导航

1. API 使用
- `/my-api-keys`
- `/my-records`

2. 账户
- `/my-subscription`
- `/profile`

## 4. 页面策略

### 保留

- `Dashboard`
- `MonitorSystem`
- `MonitorAccounts`
- `Users`
- `Groups`
- `Subscriptions`
- `Accounts`
- `IPs`
- `Announcements`
- `Records`
- `Settings`
- `MyApiKeys`
- `MyRecords`
- `MySubscription`
- `Profile`

### 重做（保留页面名与主职责）

1. `Dashboard`
- 改为真实运营指标看板，减少演示性质内容。

2. `MonitorSystem` / `MonitorAccounts`
- 统一后端数据契约，减少 fallback 依赖。

3. `Settings`
- 聚焦系统与认证配置，移除重复业务管理能力。

4. `Users`
- 保留管理侧余额手工调整能力（如业务需要）。
- 移除与支付流程相关入口与文案。

### 下线/移除

- `MyPayment` 页面
- `PaymentOrders` 页面
- 全部支付后端模块（Controller/Service/Model/Repository/Gateway）
- 支付回调公共放行逻辑
- 支付设计文档

## 5. 路由与权限基线

### 公开路由

- `/login`
- `/register`
- `/login-legacy`（迁移期保留）
- `/register-legacy`（迁移期保留）

### 登录用户可访问

- `/my-api-keys`
- `/my-records`
- `/my-subscription`
- `/profile`

### 仅 Admin 可访问

- `/dashboard`
- `/monitor/system`
- `/monitor/accounts`
- `/users`
- `/groups`
- `/subscriptions`
- `/accounts`
- `/announcements`
- `/ips`
- `/records`
- `/settings`

### 明确禁止的支付路由

- `/my-payment`
- `/payment-orders`
- `/settings/payment`
- `/api/payment/*`
- `/api/user/payment/*`
- `/api/admin/payments*`

## 6. 实施顺序

1. 清理导航与路由一致性
- 去掉下线页入口与注释残留
- 统一角色守卫

2. 提升核心页可用性
- Dashboard 指标改造
- 监控页数据契约改造

3. 职责去重
- Settings 与业务页边界明确

4. 稳定性收口
- 统一加载/空态/错误态
- 清理死链和过期动作

## 7. 验收标准

1. 产品层面无支付入口、无支付路由、无支付 API。
2. Admin/User 导航清晰，路由与权限一致。
3. 关键页面可正常访问并符合其主职责。
4. 前后端构建通过，且无支付残留引用。

