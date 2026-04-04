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

## 远程服务器连接（SSH）

- **Host**: 185.200.64.83
- **Port**: 2222
- **Username**: root
- **Password**: Je8cEeRI

### 连接方式（必须使用 paramiko）

Windows 环境下无法使用 `sshpass`，Claude Code 的 Bash 也不是真正的 TTY，所以 **必须使用 Python paramiko** 进行密码认证的 SSH 连接：

```python
import paramiko, sys, io
sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding='utf-8')

ssh = paramiko.SSHClient()
ssh.set_missing_host_key_policy(paramiko.AutoAddPolicy())
ssh.connect('185.200.64.83', port=2222, username='root', password='Je8cEeRI', timeout=10)

stdin, stdout, stderr = ssh.exec_command('YOUR_COMMAND_HERE')
out = stdout.read().decode('utf-8', errors='replace')
err = stderr.read().decode('utf-8', errors='replace')
if out.strip():
    print(out)
if err.strip():
    print("STDERR:", err)

ssh.close()
```

### 常用命令示例
- 查看服务状态：`systemctl status firstapi`
- 查看最新日志：`journalctl -u firstapi -n 100 --no-pager`
- 查看 Java 进程：`ps aux | grep java`

### 注意事项
- 使用 `/ssh` skill 可快速连接，例如：`/ssh journalctl -u firstapi -n 50`
- 输出含中文时需设置 `encoding='utf-8'`
- 不要将密码提交到公开仓库

## 回复风格
- 先给结果，再给关键原因。
- 回答尽量简洁，用项目实际文件和命令说话。
- 讨论实现方案时，优先给出最符合当前仓库风格的方案。