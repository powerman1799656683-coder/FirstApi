# FirstAPI 前端 UI/UX 改进实施包（2/3）

> **主题**：交互统一与无障碍修复
> **前置条件**：第 1 部分已完成，项目已经具备统一的基础组件和样式变量。
> **目标**：把列表页、表单、弹窗的交互方式统一下来，同时补齐最关键的可访问性问题。
> **导航**：[第 1 部分](./2026-03-21-frontend-ui-improvement-review.md) | [第 2 部分](./2026-03-21-frontend-ui-improvement-review-part-2.md) | [第 3 部分](./2026-03-21-frontend-ui-improvement-review-part-3.md)

---

## 这部分解决什么

本部分对应原评审里最影响使用体验的问题：

- P1.1 搜索触发方式不统一
- P1.2 Loading 反馈不统一
- P1.3 分页没有省略号截断
- P1.4 表单验证缺少字段级反馈
- P1.5 筛选器状态不可见
- P2.2 Modal 不锁定焦点
- P2.3 表单标签关联缺失
- P2.4 Toast 缺少 ARIA 语义
- P2.5 键盘导航缺陷

这一包做完后，用户会明显感觉“页面终于像同一个系统了”。

---

## 建议改动文件

**公共组件**

- 修改：`frontend/src/components/Modal.jsx`
- 修改：`frontend/src/components/Toast.jsx`
- 修改：`frontend/src/components/Select.jsx`
- 视需要新建：`frontend/src/components/Pagination.jsx`
- 视需要新建：`frontend/src/components/FormField.jsx`

**列表页**

- 修改：`frontend/src/pages/Groups.jsx`
- 修改：`frontend/src/pages/Subscriptions.jsx`
- 修改：`frontend/src/pages/Records.jsx`
- 修改：`frontend/src/pages/MyRecords.jsx`
- 修改：`frontend/src/pages/Announcements.jsx`
- 修改：`frontend/src/pages/Accounts.jsx`
- 视情况修改：`frontend/src/pages/ModelPricing.jsx`

**表单相关页面**

- 修改：`frontend/src/pages/Users.jsx`
- 修改：`frontend/src/pages/Settings.jsx`
- 修改：`frontend/src/pages/Profile.jsx`
- 视情况修改：`frontend/src/pages/Login.jsx`
- 视情况修改：`frontend/src/pages/Register.jsx`

**测试**

- 修改：`frontend/src/test/Modal.test.jsx`
- 修改：`frontend/src/test/Toast.test.jsx`
- 修改：`frontend/src/test/selectControlStyles.test.js`
- 视需要新建：`frontend/src/test/Pagination.test.jsx`
- 修改：`frontend/src/test/Accounts.test.jsx`
- 修改：`frontend/src/test/Groups.test.jsx`
- 修改：`frontend/src/test/Users.test.jsx`

---

## 推荐实施顺序

### 1. 先统一列表页搜索行为

把下面这些页面统一成“300ms 防抖实时搜索”：

- `frontend/src/pages/Groups.jsx`
- `frontend/src/pages/Subscriptions.jsx`
- `frontend/src/pages/Records.jsx`
- `frontend/src/pages/MyRecords.jsx`
- `frontend/src/pages/Announcements.jsx`
- `frontend/src/pages/ModelPricing.jsx`

原则：

- 不再混用“有的实时搜，有的回车搜”
- 搜索触发后默认回到第一页
- 搜索输入框样式要避免重叠，符合仓库前端规范

### 2. 再统一 loading、空状态、分页

基于第 1 部分新增的 `LoadingSpinner` 与 `EmptyState`，给所有列表页补齐统一体验：

- 刷新按钮 loading 态
- 表格区域 loading 态
- 空状态展示
- 智能省略号分页

如果分页逻辑已经在多个页面重复，优先抽成 `frontend/src/components/Pagination.jsx`，不要继续每页单独实现。

### 3. 给筛选器做“状态可见化”

优先处理 `frontend/src/pages/Accounts.jsx`：

- 激活中的筛选项高亮
- 显示“已筛选”状态
- 提供“清除全部筛选”
- 结果总数与筛选状态联动显示

这一步会让复杂列表页的可理解性提升很多。

### 4. 统一弹窗与表单的无障碍行为

集中处理 `frontend/src/components/Modal.jsx`、`frontend/src/components/Toast.jsx`、`frontend/src/components/Select.jsx`：

- Modal 打开后锁定焦点
- Modal 关闭后焦点回到触发元素
- Modal 使用 `role="dialog"` 和 `aria-modal="true"`
- Toast 增加 `role="alert"` / `aria-live`
- Select 和操作按钮补齐可见 focus 态

这里要特别遵守仓库规范：

- 弹出窗口点击空白区域时不应自动关闭

也就是说，修 Modal 的时候不能顺手改成“点击遮罩关闭”。

### 5. 最后补字段级校验反馈和 label 关联

对本包里碰到的表单统一补齐：

- `label` 与输入框通过 `htmlFor` / `id` 绑定
- 错误状态进入字段级反馈
- 输入框错误态有边框变化
- 错误信息在字段附近展示，而不是只堆在顶部

---

## 这一包的完成标准

- 所有核心列表页搜索行为一致
- 所有核心列表页都有统一 loading / empty / pagination 表现
- `Accounts.jsx` 的筛选状态一眼可见
- `Modal.jsx`、`Toast.jsx`、`Select.jsx` 的可访问性行为通过键盘操作验证
- 表单标签关联与字段级错误反馈已经落到真实页面
- 没有出现“点击弹窗空白关闭弹窗”的回归

---

## 建议验收方式

### 手工验证

- 键盘只用 `Tab` / `Shift+Tab` 能完整操作弹窗
- Toast 出现时不会打断主流程，但有可读语义
- 搜索输入后 300ms 左右触发查询，不需要按回车
- 大于 50 页的分页不会横向炸开

### 自动化验证

- Vitest 补齐弹窗、Toast、分页、字段错误态测试
- 现有列表页测试在搜索和空状态逻辑变化后同步更新

---

## 完成后再进入下一包

第 2 部分完成后，再继续看：

- [第 3 部分：视觉打磨、性能清理与健壮性](./2026-03-21-frontend-ui-improvement-review-part-3.md)

