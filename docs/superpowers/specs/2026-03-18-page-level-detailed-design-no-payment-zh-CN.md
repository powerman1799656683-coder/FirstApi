# FirstApi 逐页面详细设计文档（无支付版）

## 1. 文档说明

- 版本：v1.0
- 日期：2026-03-18
- 适用范围：当前 `frontend/src/pages` 与已启用路由
- 核心约束：支付相关页面、接口、配置、回调全部不在产品范围

## 2. 全局设计约定

### 2.1 权限模型

- `ADMIN`：可访问所有管理页和个人页。
- `USER`：仅可访问个人页。
- 未登录：仅可访问登录/注册页。

### 2.2 统一状态规范

- 加载态：页面首屏显示 loading；列表刷新显示局部 loading。
- 空态：列表无数据时显示空表/空卡片，不报错。
- 错误态：优先显示 Toast 或表单错误文案，不中断整页结构。

### 2.3 API 规范

- 前端统一通过 `api.get/post/put/del` 调用。
- 统一响应结构：`{ success, message, data }`。
- 鉴权失败：401 触发会话失效事件并回登录。

### 2.4 路由总览

- 公共：`/login`、`/register`、`/login-legacy`、`/register-legacy`
- 管理：`/dashboard`、`/monitor/system`、`/monitor/accounts`、`/users`、`/groups`、`/subscriptions`、`/accounts`、`/announcements`、`/ips`、`/records`、`/settings`
- 用户：`/my-api-keys`、`/my-records`、`/my-subscription`、`/profile`

## 3. 公共页面详细设计

## 3.1 登录页（Login）

- 路由与权限：`/login`，未登录可访问，已登录自动跳转首页或用户首页。
- 页面目标：完成账号登录与会话建立。
- 核心模块：用户名输入、密码输入、记住用户名、提交按钮、错误提示、注册入口。
- 数据接口：`POST /api/auth/login`。
- 关键交互：
  - 提交前校验用户名/密码非空。
  - 登录成功后按角色跳转：`ADMIN -> /`，`USER -> /my-api-keys`。
- 异常与边界：
  - 凭据错误显示后端 message。
  - 网络失败显示通用失败文案。
- 验收标准：
  - 成功写入会话并跳转正确页面。
  - 刷新后会话仍有效（依赖 cookie）。

## 3.2 注册页（Register）

- 路由与权限：`/register`，未登录可访问，已登录不可访问。
- 页面目标：创建普通用户并自动登录。
- 核心模块：用户名、密码、确认密码、提交按钮、错误提示、登录入口。
- 数据接口：`POST /api/auth/register`。
- 关键交互：
  - 前端校验必填项、两次密码一致。
  - 注册成功跳转 `/my-api-keys`。
- 异常与边界：
  - 后端校验失败（重复用户名/规则不合法）显示 message。
- 验收标准：
  - 注册成功后不需要二次登录。

## 3.3 旧登录页（LoginLegacy）

- 路由与权限：`/login-legacy`，仅迁移期保留。
- 页面目标：兼容历史入口，行为与 `Login` 等价。
- 设计要求：
  - 仅做兼容，不新增功能。
  - 与新版登录共享鉴权策略与跳转策略。
- 下线策略：完成用户迁移后删除路由与页面文件。

## 3.4 旧注册页（RegisterLegacy）

- 路由与权限：`/register-legacy`，仅迁移期保留。
- 页面目标：兼容历史注册入口。
- 设计要求：
  - 功能与 `Register` 对齐，不再演进。
- 下线策略：与 `LoginLegacy` 同步下线。

## 4. 管理页面详细设计

## 4.1 总览页（Dashboard）

- 路由与权限：`/dashboard`，`ADMIN`。
- 页面目标：展示平台级运营与稳定性核心指标。
- 核心模块：
  - 指标卡（请求量、成功率、Token、活跃用户、活跃账号等）
  - 趋势图（按时间窗口）
  - 模型/渠道分布
  - 近期重点事件
- 数据接口：`GET /api/admin/dashboard`。
- 关键交互：
  - 时间窗口切换触发全模块刷新。
  - 图表与表格维度保持一致。
- 异常与边界：
  - 无数据时返回空图表+0指标。
- 验收标准：
  - 页面所有核心模块由真实后端数据驱动。

## 4.2 系统监控（MonitorSystem）

- 路由与权限：`/monitor/system`，`ADMIN`。
- 页面目标：监控系统资源、服务状态、告警。
- 核心模块：
  - CPU/内存/JVM/DB 指标卡
  - 网络与节点信息
  - 系统告警表
- 当前接口现状：
  - 页面调用 `/api/admin/monitor/system`。
  - 后端当前主接口是 `/api/admin/monitor`。
- 设计要求：
  - 统一为明确可维护的监控接口契约（建议由后端补齐 `system` 聚合接口或前端改走统一聚合接口）。
- 验收标准：
  - 去除仅靠 mock fallback 的主流程依赖。

## 4.3 账号监控（MonitorAccounts）

- 路由与权限：`/monitor/accounts`，`ADMIN`。
- 页面目标：监控账号池健康、吞吐、错误率与分布。
- 核心模块：
  - 活跃账号/异常账号/消耗汇总
  - Token 趋势图
  - 渠道占比图
  - 账号健康表
- 当前接口现状：
  - 页面调用 `/api/admin/monitor/accounts`，需与后端统一契约。
- 验收标准：
  - 账号级指标可落到具体账号记录，支持运维动作闭环。

## 4.4 统一监控旧页（Monitor，未挂主路由）

- 路由状态：当前未接入主路由，作为历史实现保留。
- 页面目标：曾用于综合监控聚合展示。
- 处理策略：
  - 不再作为主入口。
  - 复用其可用图表模块到 `MonitorSystem`/`MonitorAccounts`，后续可删除页面文件。

## 4.5 用户管理（Users）

- 路由与权限：`/users`，`ADMIN`。
- 页面目标：用户生命周期与运营动作管理。
- 核心模块：
  - 列表检索、分页、排序、状态筛选
  - 新建/编辑/查看弹窗
  - 操作菜单：禁用/启用、查看 API Key、分组调整、余额调整
- 数据接口：
  - `GET /api/admin/users`
  - `GET /api/admin/users/{id}`
  - `POST /api/admin/users`
  - `PUT /api/admin/users/{id}`
  - `DELETE /api/admin/users/{id}`
  - `GET /api/admin/users/{id}/api-keys`
  - `POST /api/admin/users/{id}/topup`
  - `POST /api/admin/users/{id}/refund`
- 关键交互：
  - 搜索防抖加载。
  - 行内菜单防遮挡处理。
  - 余额调整需金额合法校验并二次确认。
- 验收标准：
  - 运营高频动作在单页闭环，且无支付流程依赖。

## 4.6 分组管理（Groups）

- 路由与权限：`/groups`，`ADMIN`。
- 页面目标：管理模型分组、计费策略字段、分组状态与策略开关。
- 核心模块：列表筛选、分组编辑弹窗、策略开关、回退组选择。
- 数据接口：
  - `GET /api/admin/groups`
  - `POST /api/admin/groups`
  - `PUT /api/admin/groups/{id}`
  - `DELETE /api/admin/groups/{id}`
- 关键交互：
  - 分组名称唯一性校验。
  - 回退分组不可指向自身。
- 验收标准：
  - 分组配置变更可被用户订阅与账号路由正确消费。

## 4.7 订阅管理（Subscriptions）

- 路由与权限：`/subscriptions`，`ADMIN`。
- 页面目标：建立用户与分组订阅关系，管理额度/状态/到期信息。
- 核心模块：
  - 订阅列表
  - 用户搜索联想
  - 分组选择与订阅编辑
- 数据接口：
  - `GET /api/admin/subscriptions`
  - `GET /api/admin/subscriptions/{id}`
  - `POST /api/admin/subscriptions`
  - `PUT /api/admin/subscriptions/{id}`
  - `DELETE /api/admin/subscriptions/{id}`
  - 辅助接口：`GET /api/admin/users`、`GET /api/admin/groups`
- 关键交互：
  - 新建/编辑时对用户与分组做存在性校验。
- 验收标准：
  - 订阅状态与用户端“我的订阅”展示一致。

## 4.8 上游账号池（Accounts）

- 路由与权限：`/accounts`，`ADMIN`。
- 页面目标：管理上游账号、健康测试、批量动作、OAuth 换取流程。
- 核心模块：
  - 多条件筛选与表格
  - 新建/编辑
  - 单个测试/禁用切换
  - OAuth 启动与兑换
  - 批量启停调度、批量测试、批量删除
- 数据接口：
  - `GET /api/admin/accounts`
  - `POST /api/admin/accounts`
  - `PUT /api/admin/accounts/{id}`
  - `DELETE /api/admin/accounts/{id}`
  - `POST /api/admin/accounts/{id}/test`
  - `POST /api/admin/accounts/oauth/start`
  - `POST /api/admin/accounts/oauth/exchange`
  - `POST /api/admin/accounts/batch/toggle-schedule`
  - `POST /api/admin/accounts/batch/test`
  - `POST /api/admin/accounts/batch/delete`
  - 辅助接口：`GET /api/admin/groups`、`GET /api/admin/ips`
- 验收标准：
  - 账号池操作可支撑监控页与路由服务的稳定数据输入。

## 4.9 IP 管理（IPs）

- 路由与权限：`/ips`，`ADMIN`。
- 页面目标：代理/IP 节点配置与连通性测试。
- 核心模块：列表、检索、编辑、单测、全测。
- 数据接口：
  - `GET /api/admin/ips`
  - `POST /api/admin/ips`
  - `PUT /api/admin/ips/{id}`
  - `DELETE /api/admin/ips/{id}`
  - `POST /api/admin/ips/{id}/test`
  - `POST /api/admin/ips/test-all`
- 验收标准：
  - 测试结果可快速定位不可用节点并反馈到账号池策略。

## 4.10 公告管理（Announcements）

- 路由与权限：`/announcements`，`ADMIN`。
- 页面目标：发布与维护站内公告。
- 核心模块：公告列表、检索、创建/编辑、删除。
- 数据接口：
  - `GET /api/admin/announcements`
  - `GET /api/admin/announcements/{id}`
  - `POST /api/admin/announcements`
  - `PUT /api/admin/announcements/{id}`
  - `DELETE /api/admin/announcements/{id}`
- 验收标准：
  - 管理端发布后用户端可见（若后续接入展示位）。

## 4.11 调用记录（Records）

- 路由与权限：`/records`，`ADMIN`。
- 页面目标：全局调用审计、模型分布、消耗趋势分析。
- 核心模块：
  - 指标卡
  - 模型分布图
  - Token 趋势图
  - 调用明细表（检索/排序/筛选）
- 数据接口：`GET /api/admin/records`。
- 验收标准：
  - 查询维度与字段命名与后端返回保持一致。

## 4.12 系统设置（Settings）

- 路由与权限：`/settings`，`ADMIN`。
- 页面目标：系统参数、认证参数、通用平台配置维护。
- 核心模块：
  - 基础设置 Tab
  - 接口设置 Tab
  - 认证设置 Tab
- 数据接口：
  - `GET /api/admin/settings`
  - `PUT /api/admin/settings`
  - 页面中现有账号操作调用 `/api/admin/accounts*`（建议长期收敛到 `Accounts` 页，避免职责重复）
- 验收标准：
  - 设置项可持久化并立即影响系统行为（在允许热更新范围内）。

## 5. 用户页面详细设计

## 5.1 我的 API Keys（MyApiKeys）

- 路由与权限：`/my-api-keys`，登录用户。
- 页面目标：用户自助管理 API Key。
- 核心模块：列表、搜索、创建、删除、轮换、复制。
- 数据接口：
  - `GET /api/user/api-keys`
  - `POST /api/user/api-keys`
  - `DELETE /api/user/api-keys/{id}`
  - `POST /api/user/api-keys/{id}/rotate`
- 验收标准：
  - 轮换后新 key 可立即用于调用，旧 key 失效策略可配置。

## 5.2 我的记录（MyRecords）

- 路由与权限：`/my-records`，登录用户。
- 页面目标：查看当前用户调用记录与使用情况。
- 核心模块：筛选、分页、时间范围查询、明细表。
- 数据接口：`GET /api/user/records`。
- 验收标准：
  - 数据仅返回当前用户，不泄露他人记录。

## 5.3 我的订阅（MySubscription）

- 路由与权限：`/my-subscription`，登录用户。
- 页面目标：查看订阅计划、资源用量、订阅历史。
- 核心模块：
  - 当前计划卡
  - 用量进度条
  - 请求统计
  - 历史记录
- 数据接口：
  - `GET /api/user/subscription`
  - `POST /api/user/subscription/renew`
- 说明：
  - 续费当前走非支付流程（系统内逻辑），后续若业务变更需单独评审，不引入支付模块。
- 验收标准：
  - 续费动作后到期时间与历史记录一致更新。

## 5.4 个人资料（Profile）

- 路由与权限：`/profile`，登录用户。
- 页面目标：维护个人信息与安全设置。
- 核心模块：
  - 基础资料编辑
  - 密码修改
  - 2FA 开关（当前为启用动作）
- 数据接口：
  - `GET /api/user/profile`
  - `PUT /api/user/profile`
  - `POST /api/user/profile/change-password`
  - `POST /api/user/profile/enable-2fa`
- 验收标准：
  - 敏感信息修改需清晰反馈并可回滚显示状态。

## 6. 非功能设计要求

### 6.1 性能

- 列表页在 1k 条以内保证可交互（筛选和排序响应可接受）。
- 重查询页面使用防抖和局部刷新，避免整页重渲染。

### 6.2 安全

- 管理接口必须在后端校验 `ADMIN`。
- 前端仅作为展示层，不以前端角色判断代替后端权限。
- 彻底禁止支付相关公开回调路径放行。

### 6.3 可测试性

- 每页至少覆盖：加载成功、空态、接口失败、核心操作成功/失败路径。
- 鉴权路径覆盖：未登录、普通用户访问管理页、管理员访问管理页。

## 7. 实施优先级

1. 路由与菜单一致性（消除死链、旧入口）
2. 监控页接口契约统一
3. Dashboard 指标口径统一
4. Settings 与 Accounts 职责拆分
5. 全页面状态与错误反馈标准化

## 8. 验收清单

1. 所有页面在各自角色下可正常访问。
2. 页面与接口映射关系与本文档一致。
3. 无支付相关页面、路由、接口、文档残留。
4. 前后端构建与核心测试通过。

