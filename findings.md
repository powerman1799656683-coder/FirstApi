# Findings & Decisions

## Session: 2026-03-30 Server Init And Deployment

### Requirements
- 连接 `root@185.200.64.83`，优先通过用户给出的 `2222` 端口检查服务器状态。
- 判断服务器是否为“未初始化”状态；如果已有旧数据或旧部署，需要做初始化清理。
- 将当前 `D:\FirstApi` 项目部署到该服务器并完成可用性验证。

### Research Findings
- 本地项目是 `Spring Boot 4.0.3 + MySQL + Vite/React` 的前后端一体部署模式。
- 公开部署文档位于 `backend/DEPLOY.md`，推荐流程是先构建前端并拷贝到 Spring Boot 静态目录，再启动 `backend-0.0.1-SNAPSHOT.jar`。
- 远端至少需要准备 `FIRSTAPI_ADMIN_PASSWORD`、`FIRSTAPI_DATA_SECRET`、`MYSQL_PASSWORD` 三个环境变量。
- `backend/src/main/resources/application.yml` 默认连接 `127.0.0.1:3306/${MYSQL_DATABASE:firstapi}`，说明远端通常需要本机 MySQL。
- `backend/pom.xml` 仍声明 `<java.version>25</java.version>`；此前本地记录显示项目可在 JDK 21 下通过命令行覆盖方式构建，但这仍是远端部署风险点。
- 当前本机已经安装 `LibericaJDK-25`，本轮可以直接在本地成功生成新的前端构建产物和 `backend-0.0.1-SNAPSHOT.jar`，说明“项目本身无法打包”不是当前阻塞点。
- 对 `185.200.64.83` 的 `22` 与 `2222` 端口测试结果一致：TCP 可建立，但服务端在返回任何 SSH banner 前主动关闭连接。
- 使用 OpenSSH 与 PuTTY `plink` 探测时都在握手前被远端关闭，当前无法进入认证阶段，尚不能执行服务器盘点或初始化。
- `80` 端口对 HTTP 请求表现为“建立连接后空回复（Empty reply from server）”；`443` 端口可建立 TCP，但 TLS 握手未成功。
- 这些公网入口行为说明目标 IP 前方存在异常网关、转发层或访问控制，不符合“纯空白、仅开 SSH 的新机”特征。

### Technical Decisions
| Decision | Rationale |
|----------|-----------|
| 先确认远程管理入口可用，再执行初始化或部署 | 当前最大阻塞点不是部署脚本，而是无法拿到服务器 shell |
| 不在无法登录的情况下盲目判断“可清空的数据范围” | 避免误判并误删可能存在的非本项目业务 |

### Issues Encountered
| Issue | Resolution |
|-------|------------|
| `2222` 端口不是可用 SSH 会话 | 增补检查 `22`、原始 TCP banner、HTTP/HTTPS 行为，确认不是本地 SSH 客户端问题 |
| 本地一体化打包命令超过 120 秒超时 | 复查产物时间戳后确认 `frontend/dist` 与后端 JAR 已成功生成，超时更像是命令尾部阻塞而非构建失败 |

## Requirements
- User wants the Claude relay smoke test actually run through locally.
- Smoke test should use the real Claude upstream key provided by the user.
- Minimize disruption to the currently running local environment.

## Research Findings
- Source code contains relay classes in `backend/src/main/java/com/firstapi/backend/controller/RelayController.java`, `backend/src/main/java/com/firstapi/backend/controller/RelayExceptionHandler.java`, and `backend/src/main/java/com/firstapi/backend/service/RelayService.java`.
- The currently running process on port `8080` is using `backend/target/backend-0.0.1-SNAPSHOT.jar`, but that jar does not contain relay classes when inspected.
- Current MySQL schema in the active environment does not have `relay_records`, which is present in `backend/src/main/resources/schema.sql`.
- Current `8080` instance is therefore not a valid target for relay smoke verification.
- `mvn -version` shows Maven is currently running on Java 8 (`1.8.0_482`), which is too old for `org.springframework.boot:spring-boot-maven-plugin:4.0.3`.
- A newer JDK is installed locally at `C:\Program Files\Microsoft\jdk-21.0.10.7-hotspot`.
- No JDK 25 installation was found locally, while `backend/pom.xml` sets `<java.version>25</java.version>`.
- After deleting stale `.class` files and recompiling under JDK 21, an isolated backend instance on port `8082` started successfully and created the `relay_records` table.
- Real non-stream Claude relay traffic reached Anthropic and was rejected with `authentication_error` / `invalid x-api-key`.
- Real stream Claude relay traffic returned `502 upstream_error` locally after the same upstream credential failure path.

## Technical Decisions
| Decision | Rationale |
|----------|-----------|
| Start a separate backend instance on another port | Avoid disturbing the existing local services while still testing the latest code |
| Use backend APIs to create temporary platform keys and accounts | Reuse existing encryption and persistence logic instead of manual DB writes |
| Clean temporary records immediately after tests | Real upstream credentials should not remain in local storage |
| Use the isolated `8082` runtime as the smoke-test evidence source | It is the only runtime confirmed to include relay classes and current schema |

## Issues Encountered
| Issue | Resolution |
|-------|------------|
| Current runtime and current source are out of sync | Rebuild and run a fresh instance from latest source |
| Previous smoke attempt timed out | Narrowed failure boundary before applying any fix |
| Default Maven runtime is too old for Spring Boot 4 build plugins | Use the installed JDK 21 only for the build and isolated runtime commands |
| Project build target is Java 25 but local machine lacks JDK 25 | Try a local CLI-only override to Java 21 without modifying repository files |
| Existing compiled classes were built for Java 25 (`major version 69`) | Removed stale `.class` files and recompiled under JDK 21 |
| Supplied Claude key failed official upstream authentication | Smoke test completed, but result is a verified upstream auth failure rather than a successful relay |

## Resources
- `D:/FirstApi/backend/src/main/java/com/firstapi/backend/controller/RelayController.java`
- `D:/FirstApi/backend/src/main/java/com/firstapi/backend/controller/RelayExceptionHandler.java`
- `D:/FirstApi/backend/src/main/java/com/firstapi/backend/service/RelayService.java`
- `D:/FirstApi/backend/src/main/resources/schema.sql`
- `D:/FirstApi/backend/DEPLOY.md`
- `C:/Program Files/Microsoft/jdk-21.0.10.7-hotspot`

## Visual/Browser Findings
- None yet.

## Session: 2026-03-23 Token Billing Audit

### Research Findings
- 用户实际扣费主链路为：`RelayService.checkUserBalance(...)` 预检查余额，`RelayRecordService.record(...)` 计算 cost 并调用 `UserService.deductByAuthUserId(...)` 扣减，再落 `relay_records` 记录。
- `CostCalculationService` 的实际计费公式是：`(promptTokens * inputPrice + completionTokens * outputPrice) / 1_000_000 * groupRate`，精度保留 10 位小数。
- `RelayRecordService` 实际只使用了分组 `rate`，未读取 `billingType` 或 `billingAmount`。
- `GroupService` / `GroupRepository` 会持久化 `billingType` 和 `billingAmount`，但当前主扣费链未消费这两个字段。
- `UserService.checkBalanceByAuthUserId(...)` 仅判断余额是否 `> 0`，并未校验扣费后是否仍为非负。
- `UserService.deductByAuthUserId(...)` 扣费时直接 `current.subtract(cost)`，没有余额下限保护。
- `RelayRecordService.record(...)` 在扣费异常时仅记录日志，随后仍会 `relayRecordRepository.save(item)`，因此存在“有消费记录但未实际扣到余额”的路径。
- `MyRecordsService` 和 `RecordsService` 的费用统计都来自 `relay_records.cost` 聚合，而不是来自实际扣款流水，因此当扣费失败被吞掉时，界面统计会继续显示已消费。
- 当前针对 `RelayRecordService` 的测试只覆盖 `createdAt` 落库，没有覆盖扣费成功、扣费失败、余额不足、usage 缺失、未定价等关键计费场景。

### Candidate Issues
- 余额可能被扣成负数。
- 扣费失败可能被静默吞掉，造成账实不一致。
- “订阅（配额）/ 标准（余额）” 分组计费类型当前看起来未接入真实扣费。
- 用户消费统计更像“按记录聚合”而非“按实际扣款聚合”。

### Verification Notes
- 尝试在 `backend/` 下使用本机 JDK 21 执行 `mvn -q '-Djava.version=21' '-Dtest=RelayRecordServiceTest,RelayServiceTest' test`。
- 命令未能完成，因为当前 `target` 内已有以更高 class file version（69，Java 25）编译的测试产物，Surefire 在 JDK 21 下直接中断。
- 因此本轮没有拿到一组“新鲜通过/失败”的单测结果，测试层面的结论仅限于源码覆盖面审查。
