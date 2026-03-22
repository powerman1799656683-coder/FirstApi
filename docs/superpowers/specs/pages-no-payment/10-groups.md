# Groups 页面设计

## 路由与权限

- 路由：`/groups`
- 权限：`ADMIN`

## 页面目标

- 维护分组配置、策略开关与分组状态。

## 核心模块

- 分组列表（平台/状态/类型筛选）
- 新建/编辑弹窗
- 策略字段（费率、回退组、模型路由开关等）

## 接口契约

- `GET /api/admin/groups`
- `GET /api/admin/groups/{id}`
- `POST /api/admin/groups`
- `PUT /api/admin/groups/{id}`
- `DELETE /api/admin/groups/{id}`

## 关键交互

- 分组名唯一校验。
- 回退组不能选自身。

## 状态与异常

- 保存失败显示后端 message。
- 删除前必须二次确认。

## 验收标准

- 变更可被订阅与账号路由正确消费。
