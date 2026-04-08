# Progress Log

## Session: 2026-04-04

### Phase 1: Context Recovery
- **Status:** complete
- Actions taken:
  - Confirmed repository working directory and project structure.
  - Read repository instructions from `CLAUDE.md` and `AGENTS.md`.
  - Captured current dirty git status to avoid overwriting unrelated work.
  - Recreated missing `task_plan.md` for the current audit session.

### Phase 2: Frontend Scan
- **Status:** in_progress
- Actions taken:
  - Read `frontend/src/App.jsx` to identify the actual routed pages.
  - Confirmed legacy login/register pages are still mounted in the router.
  - Switched from `rg` to PowerShell-native discovery after `rg.exe` failed with `Access is denied`.

## Session: 2026-03-30

### Phase 1: Local Deployment Requirements
- **Status:** complete
- Actions taken:
  - 读取仓库 `AGENTS.md`，确认本仓库的构建、测试和部署约束。
  - 读取 `backend/DEPLOY.md`、`backend/pom.xml`、`frontend/package.json`、`backend/src/main/resources/application.yml` 的相关线索。
  - 确认项目部署形态为 Vite 前端构建后拷贝进 Spring Boot 静态资源，再由后端统一提供服务。
  - 确认远端部署最少需要 MySQL 和若干 `FIRSTAPI_*` / `MYSQL_*` 环境变量。

### Phase 2: Remote Access Probe
- **Status:** in_progress
- Actions taken:
  - 使用用户提供的 `root@185.200.64.83:2222` 通过 OpenSSH 探测，连接建立后立即被远端关闭。
  - 使用 `plink` 复测，结果相同，说明不是单一 SSH 客户端实现问题。
  - 对 `22` 与 `2222` 进行原始 TCP 探测，两个端口都没有返回 SSH banner。
  - 对公网入口做侧探测，发现 `80/443` 存在响应，但 `80` 为 Empty reply，`443` TLS 握手也未正常完成。

### Phase 3: Local Build Readiness
- **Status:** complete
- Actions taken:
  - 使用本机 `npm.cmd` 构建 `frontend/dist`。
  - 将前端构建产物复制到 `backend/src/main/resources/static`。
  - 使用本机 Maven 与 JDK 25 打包新的 `backend/target/backend-0.0.1-SNAPSHOT.jar`。
  - 复核产物时间戳，确认本地部署包已准备好。

## Current Blockers
- 无法获取可用 SSH shell，因此还不能盘点远端文件、进程、数据库和现有部署。
- 在无法登录前，不能安全执行用户要求的“初始化”操作。

## Session: 2026-03-16

### Phase 1: Discovery
- **Status:** complete
- **Started:** 2026-03-16 17:00
- Actions taken:
  - Inspected current runtime listeners and backend configuration.
  - Verified admin login works on the current `8080` instance.
  - Compared source relay classes with the current built jar and active schema.
- Files created/modified:
  - `task_plan.md` (created)
  - `findings.md` (created)
  - `progress.md` (created)

### Phase 2: Runtime Verification
- **Status:** complete
- Actions taken:
  - Confirmed `/v1/chat/completions` on `8080` hangs.
  - Confirmed current jar lacks relay classes.
  - Confirmed active schema lacks `relay_records`.
  - Removed temporary smoke records left by an earlier failed test attempt.
  - Ran `mvn -q -DskipTests package` and captured a build-environment failure.
  - Verified Maven is using Java 8 and located an installed JDK 21 for isolated use.
  - Verified no JDK 25 installation is available locally.
  - Confirmed stale `target/classes` were compiled for Java 25 (`major version 69`).
- Files created/modified:
  - `task_plan.md` (created)
  - `findings.md` (created)
  - `progress.md` (created)

### Phase 3: Fresh Runtime Setup
- **Status:** complete
- Actions taken:
  - Deleted stale `.class` files under `target/classes` and `target/test-classes`.
  - Recompiled backend and tests under JDK 21 with `-Djava.version=21`.
  - Started an isolated backend instance on `8082`.
  - Verified `/v1/chat/completions` on `8082` returns a fast `401` for a bad platform key.
  - Verified MySQL now contains the `relay_records` table.
- Files created/modified:
  - `.runlogs/backend-smoke-8082.out.log` (created during run)
  - `.runlogs/backend-smoke-8082.err.log` (created during run)

### Phase 4: Smoke Testing
- **Status:** complete
- Actions taken:
  - Logged into the isolated backend on `8082` as admin.
  - Created a temporary platform key and a temporary Anthropic account using the supplied upstream key.
  - Ran a non-stream relay request with model `claude-3-5-haiku-latest`.
  - Ran a stream relay request with the same model.
  - Queried `relay_records` to capture the upstream result.
- Files created/modified:
  - `progress.md` (updated)
  - `findings.md` (updated)

### Phase 5: Cleanup
- **Status:** complete
- Actions taken:
  - Deleted the temporary Anthropic account and temporary platform key.
  - Deleted the smoke-generated `relay_records` row.
  - Terminated the isolated backend process on `8082`.
- Files created/modified:
  - `task_plan.md` (updated)
  - `findings.md` (updated)
  - `progress.md` (updated)

## Test Results
| Test | Input | Expected | Actual | Status |
|------|-------|----------|--------|--------|
| Current admin login | `POST /api/auth/login` on `8080` | `200` login success | Login succeeds | PASS |
| Current relay endpoint on stale runtime | `POST /v1/chat/completions` on `8080` | Fast error or success | Hangs until client timeout | FAIL |
| Current jar inspection | Search jar for relay classes | Relay classes present | Relay classes absent | FAIL |
| Backend package build | `mvn -q -DskipTests package` | Build completes | Fails because Spring Boot 4 plugin is loaded under Java 8 | FAIL |
| JDK inventory | Local JDK discovery | JDK 25 available or compatible alternative found | Only JDK 8 and JDK 21 are installed | FAIL |
| Recompile under JDK 21 | `mvn '-Djava.version=21' '-DskipTests' test-compile` | Classes and tests recompile | Recompiled successfully | PASS |
| Isolated relay endpoint guard | Bad key to `POST /v1/chat/completions` on `8082` | Fast `401` response | `401 invalid_api_key` returned | PASS |
| Claude non-stream smoke test | Real request through `8082` with supplied key | `200` OpenAI-style response | Upstream `401 authentication_error invalid x-api-key` | FAIL |
| Claude stream smoke test | Real stream request through `8082` with supplied key | SSE chunks and `[DONE]` | Local `502 upstream_error` after upstream credential failure path | FAIL |
| Cleanup verification | Query temp data and port `8082` | No temp rows, no listener | Temp rows removed and `8082` stopped listening | PASS |

## Session: 2026-03-23

### Phase 1: Billing Chain Discovery
- **Status:** complete
- Actions taken:
  - 搜索 token / billing / balance / cost / usage 相关后端代码。
  - 确认主扣费链路集中在 `RelayService`、`RelayRecordService`、`UserService`。
  - 确认费用展示来自 `relay_records` 聚合，而不是独立账单表。

### Phase 2: Pricing And Usage Audit
- **Status:** complete
- Actions taken:
  - 审查 `CostCalculationService` 的费用公式与精度。
  - 审查 `UpstreamHttpClient` 与 `ClaudeRelayAdapter` 的 usage 提取逻辑。
  - 审查分组 `rate`、`billingType`、`billingAmount` 在扣费链的参与情况。

### Phase 3: Consistency Audit
- **Status:** in_progress
- Actions taken:
  - 审查余额预检查与实际扣减是否一致。
  - 审查扣费异常是否会阻止调用记录落库。
  - 审查现有测试覆盖缺口，准备输出问题清单与风险等级。

### Verification Attempt
- 将 `JAVA_HOME` 临时指向 `C:\Program Files\Microsoft\jdk-21.0.10.7-hotspot`。
- 执行 `mvn -q '-Djava.version=21' '-Dtest=RelayRecordServiceTest,RelayServiceTest' test`。
- 结果：失败，原因不是新增代码，而是现有 `target` 中存在 class file version 69（Java 25）编译产物，当前 JDK 21 无法执行这些旧产物。

## Error Log
| Timestamp | Error | Attempt | Resolution |
|-----------|-------|---------|------------|
| 2026-03-16 17:12 | Smoke script timed out against current runtime | 1 | Investigated runtime/source mismatch instead of retrying blindly |
| 2026-03-16 17:18 | `relay_records` table missing in active schema | 1 | Plan to boot fresh latest backend instance |
| 2026-03-16 17:27 | Backend package build failed in Maven plugin loading | 1 | Identified Java 8 runtime mismatch; will rerun build with local JDK 21 |
| 2026-03-16 17:31 | Local machine has no JDK 25 for the configured build target | 1 | Try command-line override to Java 21 before considering repository changes |
| 2026-03-16 17:34 | `spring-boot:run` failed because stale classes were compiled for Java 25 | 1 | Removed stale `.class` files and recompiled under JDK 21 |
| 2026-03-16 17:38 | Non-stream real Claude request failed upstream auth | 1 | Verified the supplied key is rejected by official Anthropic auth |
| 2026-03-16 17:38 | Stream real Claude request returned local `502` | 1 | Secondary failure after upstream credential issue; no successful stream possible with current key |

## 5-Question Reboot Check
| Question | Answer |
|----------|--------|
| Where am I? | Phase 5: Cleanup completed |
| Where am I going? | Deliver verified smoke-test result to the user |
| What's the goal? | Verify Claude non-stream and stream smoke tests against the latest relay code with a real upstream key |
| What have I learned? | The fresh relay runtime works locally, but the supplied upstream key is rejected by official Anthropic authentication |
| What have I done? | Rebuilt classes under JDK 21, ran real relay smoke tests on `8082`, and cleaned all temporary artifacts |
