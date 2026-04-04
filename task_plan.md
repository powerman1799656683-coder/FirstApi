# Task Plan

## Session: 2026-03-30 Server Init And Deployment

### Goal
- 检查 `185.200.64.83:2222` 上的服务器是否为未初始化状态。
- 如服务器残留业务数据或旧部署，完成可审计的初始化清理。
- 将当前 `D:\FirstApi` 项目部署到该服务器，并验证服务可启动。

### Phases
| Phase | Status | Notes |
|-------|--------|-------|
| 1. 读取本地部署要求与运行依赖 | completed | 已确认 MySQL、本机反向代理、`FIRSTAPI_*` / `MYSQL_*` 环境变量与本地打包产物 |
| 2. 远程检查服务器现状 | in_progress | `22/2222` 均在 SSH 握手前被远端关闭，尚无法进入 shell 盘点 |
| 3. 判断是否需要初始化并执行清理 | pending | 仅清理部署相关残留，保留必要系统组件 |
| 4. 准备并上传部署产物 | pending | 本地构建、打包、传输到远端 |
| 5. 配置远端运行环境并启动服务 | pending | 安装缺失依赖，配置进程或反向代理 |
| 6. 验证部署结果并记录 | pending | 核对端口、接口、页面、日志与持久化状态 |

### Evidence Targets
- `backend/pom.xml`
- `backend/src/main/resources/application.properties`
- `backend/DEPLOY.md`
- `frontend/package.json`
- 远端系统信息、进程清单、目录结构、端口监听、数据库状态

### Risks To Validate
- 远端缺失 Java / Maven / Node / MySQL / Nginx 等必需组件
- 本地构建依赖 JDK 版本与远端实际可用版本不一致
- 仓库存在必须的敏感配置，但当前未提供生产环境变量
- “初始化”范围不明确，误删非本项目数据的风险需要严格限制
- 服务器可能已有其他业务监听 80/443/8080/5173 等常用端口

## Goal
- 系统性审查“用户 token 计费与余额扣减”链路，确认是否存在漏扣、重复扣、扣错、超扣、配置不生效或展示与实际不一致的问题。

## Phases
| Phase | Status | Notes |
|-------|--------|-------|
| 1. 识别扣费入口与数据流 | completed | 已确认主链路在 `RelayService -> RelayRecordService -> UserService` |
| 2. 审查定价来源与 usage 提取 | completed | 已检查 `CostCalculationService`、`UpstreamHttpClient`、`ClaudeRelayAdapter` |
| 3. 审查余额校验与扣减一致性 | in_progress | 正在核对余额门槛、异常吞掉、并发与负余额边界 |
| 4. 审查配置项是否真实生效 | in_progress | 正在核对 `billingType` / `billingAmount` / `rate` 的实际作用 |
| 5. 汇总结论与风险等级 | pending | 输出问题列表、影响范围、验证建议 |

## Evidence Targets
- `backend/src/main/java/com/firstapi/backend/service/RelayService.java`
- `backend/src/main/java/com/firstapi/backend/service/RelayRecordService.java`
- `backend/src/main/java/com/firstapi/backend/service/UserService.java`
- `backend/src/main/java/com/firstapi/backend/service/CostCalculationService.java`
- `backend/src/main/java/com/firstapi/backend/service/UpstreamHttpClient.java`
- `backend/src/main/java/com/firstapi/backend/service/GroupService.java`
- `backend/src/main/java/com/firstapi/backend/repository/GroupRepository.java`
- `backend/src/main/java/com/firstapi/backend/service/MyRecordsService.java`

## Risks To Validate
- 余额检查仅判断 `> 0` 导致超扣/负余额
- 扣费失败被吞掉但调用记录仍落库
- 分组计费配置仅保存不生效
- usage 缺失或未定价时出现漏扣
- 用户表与认证表映射失配导致扣费链断裂
