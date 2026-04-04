# Repository Guidelines

## Project Structure & Module Organization
`backend/` contains the Spring Boot app. Main code lives under `backend/src/main/java/com/firstapi/backend`, split by `controller`, `service`, `repository`, `model`, `config`, `common`, and `util`. Runtime config and schema files live in `backend/src/main/resources`; packaged frontend files are served from `backend/src/main/resources/static/`.

`frontend/` contains the Vite + React admin UI. Use `src/pages` for route-level screens, `src/components` for shared UI, `src/auth` for auth flow, `src/locales` for translations, and `src/test` for frontend tests. Design notes and implementation plans live under `docs/superpowers/`.

Do not hand-edit compiled files under `backend/src/main/resources/static/assets`; update `frontend/src` and rebuild instead.

## Build, Test, and Development Commands
`cd frontend && npm run dev` starts the UI on `http://localhost:5173` and proxies `/api` to `:8080`.

`cd frontend && npm run build` creates `frontend/dist`. `cd frontend && npm run lint` runs ESLint. `cd frontend && npm test` runs Vitest. `cd frontend && npm run test:ui:mysql` runs the browser/MySQL UI flow.

`cd backend && mvn test` runs backend tests. `cd backend && mvn package -DskipTests` builds the JAR.

`powershell -File backend/run-integration.ps1` boots the backend against local MySQL and runs API/database checks. `powershell -File backend/run-ui-e2e.ps1` builds the frontend, copies assets into Spring Boot `static/`, and runs the UI E2E flow.

## Coding Style & Naming Conventions
Java uses 4-space indentation, `PascalCase` class names, `camelCase` methods/fields, and the `com.firstapi.backend` package root. React component and page files use `PascalCase` names such as `Users.jsx` and `AuthContext.jsx`; helpers use `camelCase`.

Keep frontend files ESLint-clean. Existing `.jsx` files use semicolons; match the surrounding file style and avoid unrelated reformatting.

## Testing Guidelines
Backend tests live in `backend/src/test/java` and follow `*Test.java`. Frontend tests live in `frontend/src/test` and follow `*.test.jsx` or `*.test.js`, using Vitest, Testing Library, and `jsdom`.

Add or update tests with every behavior change. Prefer controller/service/repository coverage on the backend and page/component coverage on the frontend.

## Commit & Pull Request Guidelines
Current history uses short imperative commit subjects such as `Add unified LLM relay design spec` and `Initial commit: YC-API HUB full-stack admin dashboard`. Keep subjects concise, imperative, and focused; use a colon only when extra context helps.

PRs should summarize affected areas, list verification commands, link the relevant issue or spec, and include screenshots for visible UI changes.

## Security & Configuration Tips
Local development assumes MySQL on `127.0.0.1:3306` with database `firstapi`; override with `MYSQL_USERNAME`, `MYSQL_PASSWORD`, and `MYSQL_DATABASE`. Do not commit real secrets. Review `FIRSTAPI_ADMIN_PASSWORD`, `FIRSTAPI_USER_PASSWORD`, and `FIRSTAPI_DATA_SECRET` before any public deployment.

## 默认账号说明
Admin 管理员账号：admin / AdminPass123!，不可修改 。
Member 普通用户账号：member / UserPass123!，不可修改。
需要使用账户进行测试时直接使用上面的账号进行测试就行。

## 启动与重启要求
除非明确要求重新部署或重启，否则不要主动重启项目，包括不要重启后端、前端或重新执行部署流程，只有在明确说明 “重新部署”、”重新启动前端”、”重新启动后端” 时才能执行。

新增/修改代码过程中出现乱码，应立即修复。

系统无需默认值。

前端规范：查询框样式应避免重叠；弹出窗口点击空白区域时不应自动关闭。

必须用中文回答。

- 管理员：
admin / AdminPass123!
 
- 普通用户：
member / UserPass123! 

- 优先保证：正确性 > 可维护性 > 开发速度。
- 修改代码时，优先沿用现有模式，不要为了“更优雅”而大幅重构。

## 工作方式
- 在动手修改前，先阅读相关文件与相邻实现，理解现有约定。
- 对跨文件或有风险的改动，先给出一个简短计划，再开始执行。
- 优先做最小可行修改，避免引入无关变更。
- 完成修改后，说明改了什么、为什么改、还有哪些风险点。

## 代码规范
- 函数应保持短小、单一职责。
- 非必要不要新增依赖；如需新增，先说明理由。
- 保持与现有代码风格一致，不要混入新的风格体系。
- 错误处理要明确，不要静默吞错。
- 日志应简洁，避免输出敏感信息。

## 命名约定
- 组件名使用 `[PascalCase]`
- 普通变量与函数使用 `[camelCase / snake_case]`
- 常量使用 `[UPPER_SNAKE_CASE]`
- 测试文件命名为：`[xxx.test.ts / test_xxx.py]`

## 目录约定
- `src/`：核心业务代码
- `tests/`：测试
- `scripts/`：脚本
- `docs/`：文档
- `[补充你自己的目录说明]`

## 测试要求
- 修改业务逻辑时，优先补充或更新测试。
- 如果没有补测试，需要明确说明原因。
- 先运行与改动相关的最小测试集，再决定是否跑全量测试。

## 提交要求
- 提交说明应包含：改动内容、改动原因、影响范围。
- 若存在 breaking change、迁移步骤或配置变更，必须明确指出。
- 不要顺手修复与当前任务无关的问题，除非会阻碍当前任务。

## 禁止事项
- 不要修改无关文件。
- 不要在未经说明的情况下重命名公开接口。
- 不要引入与项目现有栈冲突的方案。
- 不要输出或提交密钥、token、证书或其他敏感信息。

## 回复风格
- 先给结果，再给关键原因。
- 回答尽量简洁，用项目实际文件和命令说话。
- 讨论实现方案时，优先给出最符合当前仓库风格的方案。