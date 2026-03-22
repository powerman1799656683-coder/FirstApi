# Settings 页面设计

## 路由与权限

- 路由：`/settings`
- 权限：`ADMIN`

## 页面目标

- 维护系统级配置与认证配置。

## 核心模块

- 基础设置 Tab
- 接口设置 Tab
- 认证设置 Tab

## 接口契约

- `GET /api/admin/settings`
- `PUT /api/admin/settings`
- 现有页面还调用 `/api/admin/accounts*` 做账号管理（建议逐步迁移回 `Accounts` 页）

## 关键交互

- 配置修改后统一保存动作。
- 保存成功显示短暂成功反馈。

## 状态与异常

- 保存中按钮禁用。
- 保存失败展示后端错误信息。

## 验收标准

- 设置项可稳定持久化。
- 与 `Accounts` 页职责边界清晰。
