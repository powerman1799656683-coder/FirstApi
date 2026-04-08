# 前端冗余与无用代码分析

## 范围说明

- 本文档基于当前仓库源码做只读分析，不包含任何代码修改。
- 分析目标是识别前端中的遗留页面、重复实现、疑似无用组件，以及低风险清理点。
- 本次结论优先依据当前路由挂载关系、仓库内引用关系和页面实际职责来判断。

## 结论摘要

- 前端最明确的遗留链路是 `LoginLegacy.jsx`、`RegisterLegacy.jsx` 和它们专属的 `SteampunkDecorations.jsx`。
- 这套 Legacy 链路仍然被 `App.jsx` 注册到路由中，但在当前仓库内没有发现任何页面内跳转入口。
- 当前前端并未表现出大量“完全未引用”的共享组件；更突出的其实是“重复实现”而不是“孤立死文件”。
- 前端测试中仍保留了对旧订阅接口 `/user/subscription` 的模拟，这说明前端层面还残留着旧接口认知。

## 高置信度遗留页面链路

### 1. Legacy 登录/注册页面

**证据**

- `frontend/src/App.jsx:47-51` 仍然注册了以下路由：
  - `/login-legacy`
  - `/register-legacy`
- 仓库内全文搜索 `/login-legacy` 和 `/register-legacy` 时，仅命中 `App.jsx` 中的这两条路由注册，没有发现任何页面内导航或按钮跳转。
- `frontend/src/pages/LoginLegacy.jsx` 和 `frontend/src/pages/RegisterLegacy.jsx` 都是完整页面实现，而不是简单壳组件。

**判断**

- 这两页不是“绝对死代码”，因为手动输入 URL 仍可访问。
- 但它们已经非常接近“隐藏遗留入口”状态：项目内部没有自然流量入口，只有路由保留。

**风险判断**

- 如果没有历史书签、外部文档或运营用途，这组页面属于前端最适合优先确认是否删除的对象。
- 如果仍需保留备用主题或演示入口，应明确标记其用途，否则后续维护者很容易误判。

### 2. `SteampunkDecorations.jsx` 属于 Legacy 页面专用组件

**证据**

- `frontend/src/components/SteampunkDecorations.jsx` 提供 `Gear` 和 `Rivet` 两个装饰组件。
- 仓库内搜索 `SteampunkDecorations` 的结果只命中：
  - `frontend/src/pages/LoginLegacy.jsx`
  - `frontend/src/pages/RegisterLegacy.jsx`

**判断**

- 该组件没有被当前主登录页或主注册页使用。
- 它与 Legacy 登录/注册页形成一个非常清晰的绑定链路。

**风险判断**

- 如果 Legacy 路线确认废弃，`SteampunkDecorations.jsx` 可以跟随一起删除，属于低风险清理点。

## 明显重复实现

### 1. `Login.jsx` 与 `LoginLegacy.jsx`

**共同点**

- 两者都实现了登录表单。
- 两者都调用 `useAuth().login(...)`。
- 登录成功后都根据角色跳转到后台首页或 `/my-api-keys`。

**差异点**

- `Login.jsx` 是当前主视觉版本，包含 `LanguageSwitcher`、记住用户名、密码显隐、国际化文案等能力。
- `LoginLegacy.jsx` 是另一套完整 UI 皮肤，实现更老、更轻量，但仍承载同类业务职责。

**判断**

- 这不是“同页复用不同样式”，而是两份并行的登录页面实现。
- 业务职责高度重叠，维护成本高于保留价值的可能性很大。

### 2. `Register.jsx` 与 `RegisterLegacy.jsx`

**共同点**

- 两者都负责注册流程。
- 两者都调用 `useAuth().register(...)`。
- 提交成功后都跳转到 `/my-api-keys`。

**差异点**

- 主注册页 `Register.jsx` 已经对接当前页面语言和 UI 规范。
- Legacy 注册页 `RegisterLegacy.jsx` 保留另一套完整表单结构，甚至含有与当前主注册页不完全一致的字段组织方式。

**判断**

- 这里的重复不只是视觉重复，还带有潜在的行为分叉风险。
- 如果两套页面长期并存，字段校验、交互文案和接口契约更容易逐步漂移。

## 前端中的“旧接口认知”残留

### `MyUsageSubscriptionSplit.test.jsx` 仍在模拟旧接口

**证据**

- `frontend/src/pages/MySubscription.jsx:366` 当前正式页面请求的是 `/user/quota/summary`。
- 但 `frontend/src/test/MyUsageSubscriptionSplit.test.jsx:19` 仍然对 `/user/subscription` 做 mock 返回。

**判断**

- 这说明前端正式页面已经切到新订阅数据链路，但测试层仍保留旧接口背景。
- 这类代码不一定需要立即删除，但它会让后来阅读测试的人误以为旧接口仍是正式依赖。

## 低风险清理点

- 如果确认不再需要 Legacy 登录/注册入口，可整链清理：
  - `frontend/src/pages/LoginLegacy.jsx`
  - `frontend/src/pages/RegisterLegacy.jsx`
  - `frontend/src/components/SteampunkDecorations.jsx`
  - `frontend/src/App.jsx` 中对应 legacy 路由
- 如果短期内不删，建议至少补充用途说明，避免这组文件继续被误认为正式主链路。
- 与其相比，`LanguageSwitcher.jsx`、`EmptyState.jsx`、`Modal.jsx` 这类共享组件当前都有明确引用，不应误判为无用代码。

## 暂不归类为无用代码的部分

- `frontend/src/components/LanguageSwitcher.jsx`
- `frontend/src/components/EmptyState.jsx`
- `frontend/src/components/Modal.jsx`

这些组件都在页面或测试中有明确使用，不属于本次“无用代码”范围。

## 建议的后续确认顺序

1. 先确认 `/login-legacy`、`/register-legacy` 是否仍有业务用途或历史入口。
2. 再确认旧订阅接口 `/user/subscription` 是否还需要保留对应测试语义。
3. 若确认无外部依赖，再做一次“Legacy 页面整链删除”会比零散删文件更安全。
