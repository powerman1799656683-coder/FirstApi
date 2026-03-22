# Subscriptions 页面设计

## 路由与权限

- 路由：`/subscriptions`
- 权限：`ADMIN`

## 页面目标

- 管理用户订阅关系、状态、额度与到期信息。

## 核心模块

- 订阅列表（搜索、状态筛选、排序、分页）
- 新建/编辑订阅弹窗
- 用户联想搜索
- 分组选择与配额字段

## 接口契约

- `GET /api/admin/subscriptions`
- `GET /api/admin/subscriptions/{id}`
- `POST /api/admin/subscriptions`
- `PUT /api/admin/subscriptions/{id}`
- `DELETE /api/admin/subscriptions/{id}`
- 辅助：`GET /api/admin/users`、`GET /api/admin/groups`

## 关键交互

- 用户搜索防抖。
- 编辑时保留当前订阅上下文。

## 状态与异常

- 列表请求失败提示。
- 保存失败在弹窗中显示错误信息。

## 验收标准

- 管理端订阅状态和用户端展示一致。
