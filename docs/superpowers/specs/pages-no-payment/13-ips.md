# IPs 页面设计

## 路由与权限

- 路由：`/ips`
- 权限：`ADMIN`

## 页面目标

- 管理代理/IP 节点并执行可用性测试。

## 核心模块

- 列表与关键词检索
- 新建/编辑弹窗
- 单节点测试
- 全量测试

## 接口契约

- `GET /api/admin/ips`
- `GET /api/admin/ips/{id}`
- `POST /api/admin/ips`
- `PUT /api/admin/ips/{id}`
- `DELETE /api/admin/ips/{id}`
- `POST /api/admin/ips/{id}/test`
- `POST /api/admin/ips/test-all`

## 关键交互

- 测试按钮的进行态与结果反馈。
- 删除操作二次确认。

## 状态与异常

- 加载失败提示并可重试。
- 测试失败展示节点级错误信息。

## 验收标准

- 可快速定位不可用节点并支持修复闭环。
