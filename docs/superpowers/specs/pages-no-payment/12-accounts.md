# Accounts 页面设计

## 路由与权限

- 路由：`/accounts`
- 权限：`ADMIN`

## 页面目标

- 管理上游账号池与连通性，支持批量运维动作。

## 核心模块

- 账号列表（多条件筛选）
- 新建/编辑账号弹窗
- 单账号测试与临时禁用
- OAuth 启动与兑换
- 批量测试/删除/调度开关

## 接口契约

- `GET /api/admin/accounts`
- `GET /api/admin/accounts/{id}`
- `POST /api/admin/accounts`
- `PUT /api/admin/accounts/{id}`
- `DELETE /api/admin/accounts/{id}`
- `POST /api/admin/accounts/{id}/test`
- `POST /api/admin/accounts/oauth/start`
- `POST /api/admin/accounts/oauth/exchange`
- `POST /api/admin/accounts/batch/toggle-schedule`
- `POST /api/admin/accounts/batch/delete`
- `POST /api/admin/accounts/batch/test`
- 辅助：`GET /api/admin/groups`、`GET /api/admin/ips`

## 关键交互

- 批量动作前必须有选择项。
- OAuth 相关字段校验与错误回显明确。

## 状态与异常

- 测试中显示进行态。
- 接口异常提示并保留当前筛选条件。

## 验收标准

- 账号池可支撑监控页和路由服务稳定运行。
