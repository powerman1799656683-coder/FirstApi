# FirstAPI 前端 UI/UX 改进实施包（1/3）

> **主题**：基础设施与设计系统收口
> **目标**：先把全局样式变量、通用组件和按钮体系收拢，给后续页面逐步改造打底。
> **实施顺序**：必须先做第 1 部分，再进入第 2、3 部分。
> **导航**：[第 1 部分](./2026-03-21-frontend-ui-improvement-review.md) | [第 2 部分](./2026-03-21-frontend-ui-improvement-review-part-2.md) | [第 3 部分](./2026-03-21-frontend-ui-improvement-review-part-3.md)

---

## 这部分先解决什么

本部分对应原评审中的高优先级基础问题，核心是先解决“页面各写各的、后面没法统一改”的问题：

- P0.1 缺少统一组件库
- P0.2 大量硬编码颜色散落在组件中
- P0.3 按钮样式体系碎片化
- P2.1 缺少 `prefers-reduced-motion` 支持

如果这一步不先做，后面的搜索统一、无障碍、视觉打磨都会变成逐页返工。

---

## 这一包的交付结果

做完第 1 部分后，项目里应该具备下面这些基础能力：

- 有统一的颜色语义变量，而不是页面里到处写 `#10b981`、`rgba(...)`
- 有统一的按钮 class 体系，而不是 `btn-*`、`nav-item--button`、`mon-*` 并存
- 有统一的 `StatusBadge`、`EmptyState`、`LoadingSpinner`
- 至少 2 到 3 个高频页面已经切换到这些基础能力，证明这套设计可以落地
- 全局样式补上 `prefers-reduced-motion`

---

## 建议改动文件

**全局样式与入口**

- 修改：`frontend/src/index.css`
- 视情况修改：`frontend/src/App.css`

**公共组件**

- 新建：`frontend/src/components/StatusBadge.jsx`
- 新建：`frontend/src/components/EmptyState.jsx`
- 新建：`frontend/src/components/LoadingSpinner.jsx`
- 修改：`frontend/src/components/Layout.jsx`
- 修改：`frontend/src/components/Toast.jsx`
- 修改：`frontend/src/components/Modal.jsx`
- 修改：`frontend/src/components/Select.jsx`

**优先接入的页面**

- 修改：`frontend/src/pages/Accounts.jsx`
- 修改：`frontend/src/pages/Groups.jsx`
- 修改：`frontend/src/pages/Subscriptions.jsx`
- 视实现情况补充：`frontend/src/pages/Records.jsx`

**测试**

- 新建或补充：`frontend/src/test/Toast.test.jsx`
- 新建或补充：`frontend/src/test/Modal.test.jsx`
- 新建：`frontend/src/test/StatusBadge.test.jsx`
- 新建：`frontend/src/test/EmptyState.test.jsx`
- 新建：`frontend/src/test/LoadingSpinner.test.jsx`
- 视接入页面补充：`frontend/src/test/Accounts.test.jsx`
- 视接入页面补充：`frontend/src/test/Groups.test.jsx`

---

## 推荐实施顺序

### 1. 先统一主题变量，不先改页面业务

在 `frontend/src/index.css` 中先补齐语义化设计令牌：

- `--color-success`
- `--color-error`
- `--color-warning`
- `--color-info`
- `--surface-*`
- `--border-*`
- `--text-*`

同时把项目里已有的主按钮、次按钮、危险按钮的颜色改成引用这些变量，而不是继续写死颜色值。

这一步只做“变量收口”和“旧样式兼容”，不要一开始就大面积页面重构。

### 2. 把按钮体系先收成一套

在 `frontend/src/index.css` 中统一成下面这种结构：

```css
.btn
.btn-primary
.btn-secondary
.btn-danger
.btn-ghost
.btn-sm
.btn-md
.btn-lg
.btn-icon
.btn-icon-danger
```

现有的 `.nav-item--button`、`.mon-icon-btn`、`.mon-alert-rules-btn` 这类 class 不要一次性全删，可以先做兼容映射，再逐页替换。

### 3. 新建三个基础组件

优先补这三个公共组件：

- `StatusBadge.jsx`
- `EmptyState.jsx`
- `LoadingSpinner.jsx`

要求：

- 组件只负责表现，不耦合页面业务
- 颜色和尺寸都走 class 或 CSS 变量
- 不写页面专属文案
- 为后续第 2 部分的 loading、空状态统一做准备

### 4. 选 2 到 3 个页面做接入样板

建议优先接入：

1. `frontend/src/pages/Accounts.jsx`
2. `frontend/src/pages/Groups.jsx`
3. `frontend/src/pages/Subscriptions.jsx`

原因：

- 这几页已经暴露出状态徽章、空状态、按钮样式不统一的问题
- 接入后可以马上验证基础组件是否够用
- 这几页的改造结果会直接影响第 2 部分的交互统一

### 5. 最后再补全局减弱动效支持

在 `frontend/src/index.css` 增加：

```css
@media (prefers-reduced-motion: reduce) {
  *, *::before, *::after {
    animation-duration: 0.01ms !important;
    animation-iteration-count: 1 !important;
    transition-duration: 0.01ms !important;
    scroll-behavior: auto !important;
  }
}
```

这一步放在本包结尾做，风险最低，也方便集中验证全局样式影响。

---

## 这一包不要急着做什么

下面这些内容先不要混进第 1 部分：

- 搜索统一
- loading 行为统一
- 智能分页
- Modal 焦点锁定
- 表单字段级校验
- 排序图标、tooltip、移动端卡片视图
- Error Boundary

这些都放到第 2、3 部分，避免第一包范围失控。

---

## 验收标准

第 1 部分完成时，至少要满足下面几点：

- `frontend/src/index.css` 已提供统一语义化颜色变量
- 旧按钮体系开始向统一按钮体系收敛，且没有造成现有页面样式回退
- `StatusBadge`、`EmptyState`、`LoadingSpinner` 已创建并被真实页面使用
- `Accounts.jsx`、`Groups.jsx`、`Subscriptions.jsx` 中至少 2 个页面完成基础组件接入
- 全局已支持 `prefers-reduced-motion`
- 相关 Vitest 用例通过，至少覆盖新增公共组件的渲染与 class 绑定

---

## 完成后再进入下一包

第 1 部分完成后，再继续看：

- [第 2 部分：交互统一与无障碍修复](./2026-03-21-frontend-ui-improvement-review-part-2.md)

