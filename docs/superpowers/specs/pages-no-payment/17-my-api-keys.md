# MyApiKeys 页面设计

## 路由与权限

- 路由：`/my-api-keys`
- 权限：登录用户

## 页面目标

- 用户自助管理 API Key。

## 核心模块

- 列表与搜索
- 新建 Key
- 删除 Key
- 轮换 Key
- 复制 Key

## 接口契约

- `GET /api/user/api-keys`
- `GET /api/user/api-keys/{id}`
- `POST /api/user/api-keys`
- `PUT /api/user/api-keys/{id}`
- `DELETE /api/user/api-keys/{id}`
- `POST /api/user/api-keys/{id}/rotate`

## 关键交互

- 轮换后回显新 key 并提示复制。
- 删除前二次确认。

## 状态与异常

- 列表加载与局部刷新状态分离。
- 操作失败显示明确提示。

## 验收标准

- 用户可独立完成 key 生命周期管理。
