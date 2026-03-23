# CLAUDE.md

## Build & Run Commands

### Backend (Spring Boot 4.0.3 + Java 25)
```bash
cd D:/FirstApi/backend
mvn package -DskipTests          # Build JAR
FIRSTAPI_ADMIN_PASSWORD=”AdminPass123!” FIRSTAPI_USER_ENABLED=true FIRSTAPI_USER_PASSWORD=”UserPass123!” java -jar target/backend-0.0.1-SNAPSHOT.jar  # Run on port 8080
```
The backend requires a running MySQL on 127.0.0.1:3306 (user `root`, password `root`, database `firstapi`). Schema is auto-created via `schema.sql` on startup (`spring.sql.init.mode=always`).

### Frontend (React 19 + Vite 8)
```bash
cd D:/FirstApi/frontend
npm run dev      # Dev server on port 5173 (proxies /api to 8080)
npm run build    # Production build to dist/
npm run lint     # ESLint
```

### Deploy frontend to backend
```bash
rm -rf D:/FirstApi/backend/src/main/resources/static/*
cp -r D:/FirstApi/frontend/dist/* D:/FirstApi/backend/src/main/resources/static/
```

## Tech Stack
- **Backend**: Java 25 + Spring Boot 4.0.3 + Spring Framework 7 + Jackson 3 + MySQL
- **Frontend**: React 19 + Vite 8 + React Router DOM 7
- **Namespace**: Uses `jakarta.*` (NOT javax) for servlet/annotation imports
- **Jackson**: Uses `tools.jackson.*` for databind/core, `com.fasterxml.jackson.annotation.*` for annotations

## Security
- **Rate Limiting**: `RateLimitFilter` enforces per-IP limits — 10/min for auth endpoints, 120/min for general API
- **Encryption**: `SensitiveDataService` uses AES-256-GCM for stored credentials (key from `FIRSTAPI_DATA_SECRET`)
- **Authentication**: Cookie-based sessions with PBKDF2-hashed passwords (min 10 chars)
- **Session**: Existing sessions invalidated on new login (prevents session fixation)

## 默认账号说明
Admin 管理员账号：admin / AdminPass123!，不可修改 。
Member 普通用户账号：member / UserPass123!，不可修改。
需要使用账户进行测试时直接使用上面的账号进行测试就行。

## 启动与重启要求
除非明确要求重新部署或重启，否则不要主动重启项目，包括不要重启后端、前端或重新执行部署流程，只有在明确说明 “重新部署”、”重新启动前端”、”重新启动后端” 时才能执行。

新增/修改代码过程中出现乱码，应立即修复。

系统无需默认值。

前端规范：查询框样式应避免重叠；弹出窗口点击空白区域时不应自动关闭。

## 前端语言规范
开发的代码中，所有用户可见的文本（按钮、标签、提示、表头、弹窗标题等）默认使用中文，除非明确要求使用英文。

## 搜索框样式规范（禁止修改）
所有页面的搜索框使用 `.select-control` 容器（`display: inline-flex`），内部放置 `<Search>` 图标 + `<input>`。
- 图标使用 `flex-shrink: 0` 保持固定尺寸
- 输入框使用 `flex: 1`（不是 `width: 100%`）填充剩余空间
- **禁止**将输入框改为 `width: 100%`，禁止对图标使用 `position: absolute`，这些写法会导致图标和输入框重叠
- 相关 CSS 在 `frontend/src/index.css` 的 `.select-control > svg` 和 `.select-control input` 规则中，已有注释说明，请勿修改

必须用中文回答。

- 管理员：
admin / AdminPass123!
 
- 普通用户：
member / UserPass123! 