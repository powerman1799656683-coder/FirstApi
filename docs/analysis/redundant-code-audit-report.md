# 冗余代码审计报告

审计时间：2026-04-07  
审计范围：`frontend/src`、`backend/src`、根目录分析/运行产物、`docs`  
审计方式：只读检查；结合全文引用搜索、`git ls-files` / `git status` / `git check-ignore`、目录体积统计、`frontend` 的 `npm run lint` 结果。  
说明：本次未修改任何业务代码，仅新增本报告。

## 高置信度可清理

1. `frontend/src/assets/hero.png`、`frontend/src/assets/react.svg`、`frontend/src/assets/vite.svg`
理由：这 3 个资源都属于典型 Vite 脚手架残留；在 `frontend/src` 中按文件名全文搜索，没有发现除 `assets/` 自身之外的任何引用，可直接删除。

2. `frontend/src/hardcodedTranslation.js`、`frontend/src/locales/hardcoded-en.json`、`frontend/src/test/HardcodedI18n.test.jsx`
理由：这是一整条“硬编码中文转英文”的旁路实现，但没有接入正式运行链路。`frontend/src/i18n.js` 只加载 `en.json` 和 `zh.json`；`frontend/src/main.jsx` 仅引入 `./i18n`；当前 `frontend/src/App.jsx` 也没有安装 `installHardcodedTranslation()`。仓库内对 `installHardcodedTranslation` 的调用只出现在 `frontend/src/test/HardcodedI18n.test.jsx`，说明这套实现只剩测试自循环，未参与生产代码。

3. `frontend/src/index.css` 中的旧登录/注册样式块
理由：该文件内一整段旧样式选择器没有被当前页面使用，属于高置信度可清理的“文件内死代码”，但应做选择器级删除，不是删除整个文件。高置信度未使用选择器包括：`.nx-login-page`、`.nx-login-layout`、`.nx-copy-panel`、`.nx-chip`、`.nx-hero-title`、`.nx-feature-grid`、`.nx-card-panel`、`.nx-socials`、`.nx-social-btn`、`.nx-register-card`、`.nx-register-header`、`.nx-register-legacy`。当前 `frontend/src/pages/Login.jsx` 和 `frontend/src/pages/Register.jsx` 实际仍在使用的是 `auth-shell`、`auth-card`，以及保留中的 `.nx-grid-overlay`；上述旧类名全文搜索只命中 `index.css` 自身定义。

4. `frontend/README.md`
理由：内容仍是默认 `React + Vite` 模板说明，没有任何 FirstApi 项目信息，也不对应当前仓库的实际启动、构建、测试和部署方式。它不是业务代码，但属于过时产物；如果不打算重写，直接删除比继续保留模板 README 更干净。

5. `frontend/.scan/`
理由：这是大体量历史扫描归档目录，当前统计约 `133` 个文件、`103.67 MB`。其中包含 `authed_desktop_my-payment.png`、`authed_desktop_payment-orders.png`、`authed_desktop_promos.png`、`authed_desktop_redemptions.png` 等截图，而这些页面名称已经不在当前 `frontend/src/App.jsx` 的源码路由内。该目录已被 Git 跟踪，更像历史截图/巡检归档，不是源码运行必需项；若团队不需要保留可直接清理。

6. 本地构建产物与运行日志
路径：`frontend/dist/`、`frontend/.vite-dev.log`、`frontend/.runlogs/`、`backend/.runlogs/`、`backend/.mysql-test.out`、`backend/.mysql-test.err`、`backend/jar-8081.err.log`、`backend/jar-8081.out.log`、`backend/spring-boot.err.log`、`backend/spring-boot.out.log`、`backend/spring-boot-8081.err.log`、`backend/spring-boot-8081.out.log`、`backend/spring-boot-live.err.log`、`backend/spring-boot-live.out.log`、`backend/spring-boot-ui-8081.err.log`、`backend/spring-boot-ui-8081.out.log`、`backend/verify-auth-8083.err.log`、`backend/verify-auth-8083.out.log`
理由：这些目录/文件都明显是本地构建、调试或验证过程中生成的产物。`git check-ignore` 已确认其中大部分被 `.gitignore` 或 `frontend/.gitignore` 忽略，属于高置信度可删的本地清理项。需要注意的是，这一结论只针对上述本地产物，不包含 `backend/src/main/resources/static/`。

7. `backend/src/main/java/com/firstapi/backend/repository/JsonListRepository.java`、`backend/src/main/java/com/firstapi/backend/store/JsonStorePersistence.java`
理由：这是后端一套基于 `json_store` 表的 JSON 存储抽象，但在当前主链代码中没有实际使用。全文搜索 `extends JsonListRepository`、`JsonListRepository<`、`JsonStorePersistence` 时，只命中这两个定义自身，没有任何 repository/service 继承或注入它们。对应表 `json_store` 也不在主 `backend/src/main/resources/schema.sql` 中，只出现在测试 schema：`backend/src/test/resources/schema.sql`、`backend/src/test/resources/schema-test.sql`。这说明它们高度疑似已废弃基座。

## 中低置信度需确认

1. `backend/src/main/resources/schema.sql`、`backend/src/test/resources/schema.sql`、`backend/src/test/resources/schema-test.sql` 中的 `redemptions`、`promos`、`my_redemption` 表
理由：在 `backend/src/main/java` 与 `frontend/src` 范围内全文搜索 `redemptions`、`promos`、`my_redemption`，没有发现当前运行代码对这些表名的直接引用，说明它们很像历史业务残留。但数据库表可能承载过往生产数据、迁移脚本或手工查询用途，不能仅凭源码无引用就直接删表，需先确认线上/测试库是否仍保存有效数据。

2. `docs/analysis/frontend-redundant-unused-analysis.md`、`docs/analysis/backend-redundant-unused-analysis.md`、`docs/analysis/redundant-code-audit-request.md`
理由：这 3 个文件当前是未跟踪文件；内容中出现了当前仓库已不成立的判断或已过时的对象，例如旧的 `/login-legacy`、`/register-legacy`、`MySubscriptionService`、`my_subscription`、`/api/user/subscription` 等。它们更像前一轮审计草稿或中间记录。若团队需要保留审计历史，可归档到明确目录；若不需要，建议删除，避免误导后续维护者。

3. `task_plan.md`、`findings.md`、`progress.md`、`test-report.md`
理由：这几份根目录文档更像会话过程文件、临时审计笔记或历史验证记录，而不是面向仓库读者的正式文档。`task_plan.md`、`findings.md`、`progress.md` 当前还是已跟踪且有本地改动的文件；`test-report.md` 也是已跟踪文件。它们是否可删，取决于团队是否把这些文件当作长期审计历史保留。如果保留，至少建议迁移到 `docs/` 下的明确子目录。

4. `backend/deploy-vps.ps1`、`backend/verify-auth.ps1`
理由：这两个脚本已被 Git 跟踪，但仓库主说明并未把它们列为标准入口。`AGENTS.md` 只明确提到 `backend/run-integration.ps1` 和 `backend/run-ui-e2e.ps1`；`backend/DEPLOY.md` 只明确提到 `deploy-prod.ps1`。这说明 `deploy-vps.ps1`、`verify-auth.ps1` 可能是一次性脚本、个人运维脚本或历史验证脚本，但是否还能删除需要先确认是否仍有人工使用场景。

5. `backend/src/main/java/com/firstapi/backend/service/RecordsService.java`、`backend/src/main/java/com/firstapi/backend/service/MyRecordsService.java`
理由：这两份服务不是死代码，但存在明显重复实现。它们都各自维护了 `formatCost`、`formatTokens`、`formatLatency`、`formatNumber`，并重复实现了将 `RelayRecordItem` 映射为页面展示对象的逻辑。若后续要做瘦身或维护性整理，这是一组高价值的去重目标；但由于两边输出字段略有差异，不建议在未回归验证前直接合并或删除任一方。

6. `frontend/src/pages/MyApiKeys.jsx`、`frontend/src/pages/Users.jsx`、`frontend/src/pages/Accounts.jsx`
理由：这三个页面里都存在复制到剪贴板的相似逻辑，且处理风格不一致。例如 `MyApiKeys.jsx`、`Users.jsx`、`Accounts.jsx` 都直接调用 `navigator.clipboard.writeText(...)`。这属于重复实现而不是未使用代码，建议后续确认是否抽成共享 helper；当前不建议直接删。

7. `frontend/src/pages/Users.jsx`、`frontend/src/pages/Accounts.jsx`、`frontend/src/pages/ModelPricing.jsx`、`frontend/src/pages/MySubscription.jsx`、`frontend/src/test/AuthContext.test.jsx`
理由：`frontend` 的 `npm run lint` 已明确报出一批未使用变量，属于“可删片段”而不是“可删文件”。已确认的例子包括：`Users.jsx` 中的 `total`、`startRow`、`endRow`；`Accounts.jsx` 中的 `_`、`startRow`、`endRow`、`isError`；`ModelPricing.jsx` 中未使用的 `e`；`MySubscription.jsx` 中未使用的 `Icon`；`AuthContext.test.jsx` 中未使用的 `handlers`。这些片段可以作为后续小步清理项，但因为同文件还承载活跃业务代码，应先按最小改动处理。

## 风险说明

1. 不要把 `backend/src/main/resources/static/` 当成“可直接手动清理”的目录
理由：仓库规范已经明确，编译后的前端资源最终由 Spring Boot 从 `backend/src/main/resources/static/` 提供。实际核对显示，`frontend/dist/assets/` 与 `backend/src/main/resources/static/assets/` 当前文件名和 SHA256 完全一致，它们是同一批构建产物的拷贝。可以清理 `frontend/dist/` 这样的本地产物，但不要在没有重新构建和回填的前提下直接手动清空 `backend/src/main/resources/static/`。

2. 当前构建产物与当前源码存在“不同步”风险
理由：当前 `frontend/src/App.jsx` 源码里已经不再注册 `/login-legacy`、`/register-legacy`，但 `frontend/dist/assets/index-C87j__wd.js` 与 `backend/src/main/resources/static/assets/index-C87j__wd.js` 仍然包含这两条 legacy 路由。说明现有编译产物落后于源码，不能把 `dist/static` 中看到的内容直接当作源码现状依据。所有“是否可删”的判断应优先以 `frontend/src` / `backend/src` 为准。

3. 后端 schema、测试和仓库实现之间存在结构漂移，不能简单按“无引用”处理
理由：`backend/src/test/java/com/firstapi/backend/SchemaSqlTest.java` 仍然要求主 schema 包含 `payment_orders`、`account_group_bindings`、`payment_config`、`json_store`；但当前 `backend/src/main/resources/schema.sql` 中搜不到这些建表语句。与此同时，`backend/src/main/java/com/firstapi/backend/repository/AccountGroupBindingRepository.java` 仍在直接访问 `account_group_bindings`。这属于 schema/test/代码漂移风险，不是单纯的冗余删除问题，应先补齐数据结构来源，再谈清理。

4. 不要把设计文档中的旧接口/旧路由描述直接当成可删依据
理由：`docs/superpowers/specs/2026-03-18-console-ia-no-payment-design.md`、`docs/superpowers/specs/2026-03-18-console-ia-no-payment-design-zh-CN.md`、`docs/superpowers/specs/2026-03-18-page-level-detailed-design-no-payment-zh-CN.md`、`docs/superpowers/specs/pages-no-payment/03-login-legacy.md`、`docs/superpowers/specs/pages-no-payment/04-register-legacy.md`、`docs/superpowers/specs/pages-no-payment/19-my-subscription.md` 仍然记载了 `/login-legacy`、`/register-legacy`、`/api/user/subscription`。但当前真实源码中，`frontend/src/pages/MySubscription.jsx` 已经改为请求 `/user/quota/summary`。因此文档更适合作为“历史背景”或“迁移痕迹”参考，不能单独作为删代码的最终依据。
