# RegisterLegacy 页面设计

## 路由与权限

- 路由：`/register-legacy`
- 权限：未登录可访问；已登录不可访问。

## 页面目标

- 兼容历史注册入口，不新增业务能力。

## 核心模块

- 与 `Register` 页面等价的注册表单能力。

## 接口契约

- `POST /api/auth/register`

## 关键交互

- 注册成功跳转 `/my-api-keys`。

## 状态与异常

- 与 `Register` 保持一致。

## 下线策略

- 与 `LoginLegacy` 同步下线。
