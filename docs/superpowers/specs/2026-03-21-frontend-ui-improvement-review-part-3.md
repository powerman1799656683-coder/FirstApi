# FirstAPI 前端 UI/UX 改进实施包（3/3）

> **主题**：视觉打磨、性能清理与健壮性收尾
> **前置条件**：第 1、2 部分已经完成，基础能力和交互行为都已统一。
> **目标**：在不打乱现有交互的前提下，继续清理视觉不一致、性能隐患和稳定性问题。
> **导航**：[第 1 部分](./2026-03-21-frontend-ui-improvement-review.md) | [第 2 部分](./2026-03-21-frontend-ui-improvement-review-part-2.md) | [第 3 部分](./2026-03-21-frontend-ui-improvement-review-part-3.md)

---

## 这部分解决什么

本部分主要对应原评审里的收尾类问题：

- P3.1 字号 / 字重不统一
- P3.2 排序列缺少默认视觉标识
- P3.3 截断文本没有 Tooltip
- P3.4 表格移动端不可用
- P3.5 图表配色不统一
- P4.1 内联样式对象导致不必要重渲染
- P4.2 缺少 Error Boundary
- P4.3 `backdrop-filter` 缺少 webkit 前缀
- P4.4 Unicode 转义影响可读性
- 评估矩阵中剩余的页面级特例问题

这一包不是“锦上添花”而已，它决定系统最终是否足够稳、足够顺手、足够容易继续维护。

---

## 建议改动文件

**全局样式**

- 修改：`frontend/src/index.css`
- 修改：`frontend/src/App.css`

**公共组件**

- 修改：`frontend/src/components/Layout.jsx`
- 修改：`frontend/src/components/Modal.jsx`
- 修改：`frontend/src/components/Toast.jsx`
- 视需要新建：`frontend/src/components/ErrorBoundary.jsx`

**重点页面**

- 修改：`frontend/src/pages/Dashboard.jsx`
- 修改：`frontend/src/pages/Monitor.jsx`
- 修改：`frontend/src/pages/MonitorSystem.jsx`
- 修改：`frontend/src/pages/Accounts.jsx`
- 修改：`frontend/src/pages/Users.jsx`
- 修改：`frontend/src/pages/Settings.jsx`
- 修改：`frontend/src/pages/ModelPricing.jsx`
- 修改：`frontend/src/pages/MyApiKeys.jsx`
- 修改：`frontend/src/pages/Announcements.jsx`

**测试**

- 修改：`frontend/src/test/MonitorSystem.test.jsx`
- 修改：`frontend/src/test/MonitorMergedView.test.jsx`
- 修改：`frontend/src/test/ResponsiveLayout.test.jsx`
- 视需要新建：`frontend/src/test/ErrorBoundary.test.jsx`

---

## 推荐实施顺序

### 1. 先统一排版和视觉令牌

在 `frontend/src/index.css` 里定义清晰的排版层级：

- 页面标题
- 区块标题
- 正文
- caption
- 表格辅助文案

不要继续让 `Dashboard`、`Settings`、`Monitor`、`Modal` 各自定义标题大小。

同时把图表颜色也统一成同一套调色板变量，避免 `Records.jsx` 和 `MonitorSystem.jsx` 各走一套。

### 2. 再处理“用户看得到但现在不顺手”的细节

优先修这些细节：

- 排序列默认显示可排序标识
- 截断文本补 tooltip / `title`
- 表格操作按钮扩大触摸目标
- 窄屏下给关键表格提供更清晰的横向滚动提示

如果账号管理表格在移动端仍然明显难用，再补卡片视图替代方案，但不要过早把所有表格都改成双模式。

### 3. 然后做稳定性兜底

重点处理图表页和复杂页面：

- `frontend/src/pages/Dashboard.jsx`
- `frontend/src/pages/Monitor.jsx`
- `frontend/src/pages/MonitorSystem.jsx`

这里建议补 `ErrorBoundary`，避免单个图表异常把整页打白。

如果创建公共错误边界组件，建议放在：

- `frontend/src/components/ErrorBoundary.jsx`

### 4. 集中清理内联样式和兼容性问题

优先从内联样式最多的页面开始：

- `frontend/src/pages/Accounts.jsx`
- `frontend/src/pages/Users.jsx`
- `frontend/src/pages/Dashboard.jsx`

原则：

- 重复出现的 style 对象先抽 class
- 不为了“零内联样式”而硬拆一堆无意义 class
- 只清理重复度高、影响维护或造成重渲染的部分

同时补 `-webkit-backdrop-filter`，优先覆盖：

- 侧边栏
- 弹窗
- 毛玻璃卡片

### 5. 最后清理源码可读性问题

把类似 `'\u6682\u65e0\u771f\u5b9e\u6570\u636e'` 这种 Unicode 转义替换成直接中文，降低后续乱码排查成本。

这一步应该和前面功能性改造分开提交，避免混杂。

---

## 这一包的完成标准

- 标题、正文、辅助文字的字号和字重已经收口
- 图表配色统一，视觉风格不再割裂
- 排序、tooltip、触摸目标、窄屏表格体验明显改善
- 图表页具备错误边界保护
- 高频页面的重复内联样式明显减少
- `backdrop-filter` 在 Safari 兼容性上有兜底
- 源码里不再保留影响可读性的 Unicode 转义中文

---

## 页面级收尾清单

结合原评估矩阵，第三包完成时建议顺手把下面这些特例一起收掉：

- `frontend/src/pages/Users.jsx`：检查多个 Modal 是否可能叠加
- `frontend/src/pages/MyApiKeys.jsx`：彻底移除自定义按钮体系残留
- `frontend/src/pages/ModelPricing.jsx`：切回统一按钮和表格视觉
- `frontend/src/pages/MonitorSystem.jsx`：逐步消化 `mon-*` 独立样式
- `frontend/src/pages/Settings.jsx`：修复引用不存在的 CSS 变量

---

## 收尾建议

第 3 部分更适合按“小批次提交”推进：

- 一次提交排版与视觉令牌
- 一次提交图表和错误边界
- 一次提交内联样式清理
- 一次提交兼容性和源码可读性清理

这样回归风险更低，也更容易定位样式回退。
