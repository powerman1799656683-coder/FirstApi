# FirstApi 逐页面设计文档（无支付）

本目录按“每个页面一个文件”拆分，适合并行开发与评审。

## 公共页面

- `01-login.md`
- `02-register.md`
- `03-login-legacy.md`
- `04-register-legacy.md`

## 管理页面

- `05-dashboard.md`
- `06-monitor-system.md`
- `07-monitor-accounts.md`
- `08-monitor-legacy.md`
- `09-users.md`
- `10-groups.md`
- `11-subscriptions.md`
- `12-accounts.md`
- `13-ips.md`
- `14-announcements.md`
- `15-records.md`
- `16-settings.md`

## 用户页面

- `17-my-api-keys.md`
- `18-my-records.md`
- `19-my-subscription.md`
- `20-profile.md`

## 统一约束

- 不包含支付能力，不允许新增支付入口。
- 路由权限遵循 `ADMIN` / `USER` 既有守卫。
- 每页实现需覆盖加载态、空态、错误态。
