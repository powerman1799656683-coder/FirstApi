# Profile 页面设计

## 路由与权限

- 路由：`/profile`
- 权限：登录用户

## 页面目标

- 用户维护个人信息与账户安全设置。

## 核心模块

- 基础资料编辑
- 密码修改
- 2FA 启用

## 接口契约

- `GET /api/user/profile`
- `PUT /api/user/profile`
- `POST /api/user/profile/change-password`
- `POST /api/user/profile/enable-2fa`

## 关键交互

- 密码修改需旧密码与新密码校验。
- 安全动作后显示明确结果反馈。

## 状态与异常

- 表单提交中禁用按钮。
- 后端校验失败显示字段级或弹窗级提示。

## 验收标准

- 用户可独立完成资料与安全配置维护。
