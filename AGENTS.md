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