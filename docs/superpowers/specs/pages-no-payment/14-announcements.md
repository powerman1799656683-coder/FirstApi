# Announcements 页面设计

## 路由与权限

- 路由：`/announcements`
- 权限：`ADMIN`

## 页面目标

- 维护站内公告内容与状态。

## 核心模块

- 公告列表、检索、排序
- 新建/编辑弹窗
- 删除动作

## 接口契约

- `GET /api/admin/announcements`
- `GET /api/admin/announcements/{id}`
- `POST /api/admin/announcements`
- `PUT /api/admin/announcements/{id}`
- `DELETE /api/admin/announcements/{id}`

## 关键交互

- 表单字段校验（标题、内容）。
- 保存后列表即时刷新。

## 状态与异常

- 空列表显示空态说明。
- 保存和删除失败显示错误提示。

## 验收标准

- 公告可完整 CRUD 且状态一致。
