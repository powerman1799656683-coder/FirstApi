# Users 页面设计

## 路由与权限

- 路由：`/users`
- 权限：`ADMIN`

## 页面目标

- 用户生命周期管理与运营动作执行。

## 核心模块

- 用户列表、搜索、防抖刷新、分页、排序
- 新建/编辑/详情弹窗
- 行内动作菜单（状态切换、分组调整、API Key 查看、余额调整）

## 接口契约

- `GET /api/admin/users`
- `GET /api/admin/users/{id}`
- `POST /api/admin/users`
- `PUT /api/admin/users/{id}`
- `DELETE /api/admin/users/{id}`
- `GET /api/admin/users/{id}/api-keys`
- `POST /api/admin/users/{id}/topup`
- `POST /api/admin/users/{id}/refund`

## 关键交互

- 搜索输入防抖请求。
- 菜单展开避免遮挡与误触。
- 余额调整校验金额 > 0。

## 状态与异常

- 请求失败统一 Toast。
- 弹窗内校验失败就地提示。

## 验收标准

- 高频运营动作可在单页闭环完成。
- 无支付流程依赖。
