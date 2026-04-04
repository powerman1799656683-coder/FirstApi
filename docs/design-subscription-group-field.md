# 订阅管理新增分组字段设计文档

**版本**: v2.0
**日期**: 2026-03-31
**状态**: 待评审

---

## 一、现状与问题

### 1.1 当前扣费逻辑（RelayService → RelayRecordService）

```
用户请求进来
  → resolveGroup(apiKey)          从 api_keys.group_id 找到 groups 表的分组
  → checkBilling(apiKey, group)   先检查每日配额，再检查订阅额度/余额
  → 转发到上游
  → RelayRecordService.record()   扣费
       ├─ 有活跃订阅且额度剩余 → 扣订阅额度（subscriptionService.deductQuota）
       └─ 否则              → 扣用户余额（userService.deductByAuthUserId）
```

### 1.2 关键现状

**分组（groups）是请求入口的概念**：
- `api_keys.group_id` 绑定分组，决定路由到哪个平台（OpenAI/Claude）
- 分组的 `platform` 字段决定上游，`account_type` 决定账号池

**订阅（subscriptions）是额度配置的概念**：
- `subscriptions.group_name` 是纯文本，不关联 `groups` 表
- 订阅只控制"能用多少钱"，不控制"用哪个分组"
- 目前订阅制只用于 codex（OpenAI），Claude 直扣余额

### 1.3 核心问题

**当前设计隐含了一个未明确的规则**：
> 订阅用户调用 codex → 扣订阅
> 订阅用户调用 claude → 也会先检查订阅额度，若有剩余会扣订阅（当前 bug）

`subscriptionService.hasQuotaRemaining()` 不区分 provider，只要有活跃订阅就会走扣订阅逻辑，但订阅实际上只应覆盖 codex（OpenAI） 的用量。

### 1.4 需要解决的问题

1. **订阅要绑定分组**：某个订阅只能用于特定分组（如 codex 分组），调用 claude 分组时不应消耗该订阅
2. **前端下拉写死**：新建订阅时"订阅等级"硬编码为三个值，应改为动态加载分组列表
3. **可扩展**：以后支持"同一用户持有多个不同分组的订阅"，互相独立

---

## 二、设计方案

### 2.1 核心思路：订阅绑定 group_id

**最小改动，最大收益的切入点在 `subscriptions` 表新增 `group_id`**。

- 每条订阅记录绑定一个分组（`groups.id`）
- 扣费时根据本次请求使用的分组（来自 `api_keys.group_id`），精确匹配该分组下的订阅
- 没有匹配订阅 → 直接扣余额

这样天然解决了"claude 不扣订阅"的问题，因为 claude 分组和 codex 分组是两个不同的 `group_id`。

### 2.2 方案对比

| 方案 | 改动量 | 扩展性 | 说明 |
|------|--------|--------|------|
| A：subscriptions 加 group_id | **小** | **好** | 扣费逻辑改一处，天然多分组隔离 ✅ |
| B：subscriptions 加 platform 字段 | 小 | 差 | 与 group 概念重叠，以后难扩展 |
| C：在 checkBilling 里硬编码 provider | 极小 | 极差 | 补丁式，违背设计意图 |

**选方案 A。**

---

## 三、数据库变更

### 3.1 subscriptions 表新增 group_id

```sql
-- 新增 group_id，允许 null 兼容存量数据
ALTER TABLE `subscriptions`
    ADD COLUMN `group_id` bigint NULL
    COMMENT '绑定的分组 ID（groups.id），null 表示不限分组（存量兼容）'
    AFTER `uid_value`;
```

存量数据处理：`group_id` 为 null 时，行为与现在一致（不限分组，有额度就扣）。

### 3.2 无需改 groups 表

groups 表本身不需要变动，它只是被引用。

---

## 四、后端改造

### 4.1 改动点汇总（共 4 处）

```
1. SubscriptionItem          + group_id 字段
2. SubscriptionRepository    + group_id 列读写、新增按 uid+groupId 查询
3. SubscriptionService       + 查询/扣费方法接收 groupId 参数
4. RelayRecordService        + 扣费时传入 group.getId()
```

RelayService 的 `checkBilling` 也需要同步修改，把 `group.getId()` 传进去。

---

### 4.2 SubscriptionItem

新增字段：

```java
private Long groupId;
```

`Request` 内部类同样新增 `groupId`。

---

### 4.3 SubscriptionRepository

**ALL_COLS** 增加 `group_id`：

```java
private static final String ALL_COLS =
    "`id`, `user_name`, `uid_value`, `group_id`, `group_name`, " +
    "`usage_text`, `progress_value`, `expiry_label`, `status_name`, `daily_limit`";
```

**ROW_MAPPER** 增加读取（注意 null 安全）：

```java
Long groupId = rs.getObject("group_id") != null ? rs.getLong("group_id") : null;
```

**新增查询方法**：

```java
/**
 * 按 uid + groupId 查找活跃订阅。
 * groupId 为 null 时退化为按 uid 查（兼容存量）。
 */
public SubscriptionItem findActiveByUidAndGroup(Long uid, Long groupId) {
    if (groupId == null) {
        return findActiveByUid(uid);   // 存量兼容路径
    }
    // 优先精确匹配 group_id，找不到时再找 group_id IS NULL 的兜底订阅
    List<SubscriptionItem> items = jdbcTemplate.query(
        "select " + ALL_COLS + " from `subscriptions` " +
        "where `uid_value` = ? and `status_name` = '正常' " +
        "and (`group_id` = ? or `group_id` is null) " +
        "order by `group_id` desc, `id` desc limit 1",   // group_id 精确匹配优先
        ROW_MAPPER, uid, groupId
    );
    return items.isEmpty() ? null : items.get(0);
}
```

> **兜底逻辑说明**：`group_id IS NULL` 的存量订阅视为"不限分组"，任何分组都可以消耗它。新创建的订阅必须绑定 `group_id`。

---

### 4.4 SubscriptionService

**修改 `hasQuotaRemaining` 和 `deductQuota`，增加 `groupId` 参数**：

```java
// 旧签名（保留，用于兼容其他调用方）
public boolean hasQuotaRemaining(Long uid) {
    return hasQuotaRemaining(uid, null);
}

// 新签名
public boolean hasQuotaRemaining(Long uid, Long groupId) {
    SubscriptionItem sub = repository.findActiveByUidAndGroup(uid, groupId);
    if (sub == null) return false;
    BigDecimal[] parsed = parseUsage(sub.getUsage());
    return parsed != null && parsed[0].compareTo(parsed[1]) < 0;
}

// 旧签名（兼容）
public synchronized void deductQuota(Long uid, BigDecimal cost) {
    deductQuota(uid, null, cost);
}

// 新签名
public synchronized void deductQuota(Long uid, Long groupId, BigDecimal cost) {
    SubscriptionItem sub = repository.findActiveByUidAndGroup(uid, groupId);
    // ... 后续逻辑不变
}

// 供 checkBilling 使用
public SubscriptionItem getActiveSubscription(Long uid, Long groupId) {
    return repository.findActiveByUidAndGroup(uid, groupId);
}
```

**create/update 时同步 group_name**：

传入 `groupId` 时，自动从 `GroupRepository` 查出 `group.name`，写入 `group_name`（保留文本字段作为展示用快照）：

```java
// create 时
if (req.getGroupId() != null) {
    item.setGroupId(req.getGroupId());
    GroupItem g = groupRepository.findById(req.getGroupId());
    item.setGroup(g != null ? g.getName() : req.getGroup());
} else {
    item.setGroup(emptyAsDefault(req.getGroup(), "普通会员"));
}
```

---

### 4.5 RelayService.checkBilling（修改）

```java
private void checkBilling(ApiKeyItem apiKey, GroupItem group) {
    Long ownerId = apiKey.getOwnerId();
    Long groupId = group.getId();   // ← 新增，传入当前分组 ID

    // 检查每日配额（改用 groupId 精确匹配）
    SubscriptionItem activeSub = subscriptionService.getActiveSubscription(ownerId, groupId);
    if (activeSub != null && activeSub.getDailyLimit() != null && ...) {
        // ... 每日配额检查逻辑不变
    }

    // 检查订阅额度（传 groupId）
    if (subscriptionService.hasQuotaRemaining(ownerId, groupId)) {
        return;
    }
    // 检查余额
    if (userService.checkBalanceByAuthUserId(ownerId)) {
        return;
    }
    throw new RelayException(...);
}
```

---

### 4.6 RelayRecordService.record（修改）

```java
// 扣费时传入 groupId
if (subscriptionService.hasQuotaRemaining(apiKey.getOwnerId(), group.getId())) {
    subscriptionService.deductQuota(apiKey.getOwnerId(), group.getId(), item.getCost());
} else {
    userService.deductByAuthUserId(apiKey.getOwnerId(), item.getCost());
}
```

---

## 五、前端改造

### 5.1 Subscriptions.jsx — 订阅等级下拉动态化

**现状**（硬编码）：

```jsx
<option value="普通会员">普通会员</option>
<option value="Pro会员">Pro会员</option>
<option value="Max会员">Max会员</option>
```

**改造后**（从 groups 接口动态加载）：

```jsx
// loadGroups() 已存在，groups state 已有
// 下拉改为：
{groups.map(g => (
    <option key={g.id} value={g.id}>{g.name}</option>
))}

// formData 改为存 groupId（数字）
// handleCreate 初始默认值去掉硬编码
// handleEdit 初始值改为 subscription.groupId
```

提交时 `formData` 携带 `groupId`，后端 create/update 接收 `groupId` 并自动同步 `group_name`。

---

## 六、完整扣费流程（改造后）

```
用户请求
  → resolveGroup(apiKey)
      拿到 group（包含 group.getId()，即本次使用的分组）

  → checkBilling(apiKey, group)
      activeSub = subscriptionService.getActiveSubscription(uid, group.getId())
      ├─ 精确匹配：找 group_id = group.getId() 的活跃订阅
      └─ 兜底：找 group_id IS NULL 的活跃订阅（存量兼容）

      若有 activeSub && 设了 dailyLimit → 检查每日配额
      若有订阅额度剩余 → 通过
      否则检查余额 → 通过 or 拒绝

  → 上游转发

  → RelayRecordService.record(apiKey, route, result, model, group)
      计算 cost
      subscriptionService.hasQuotaRemaining(uid, group.getId())
      ├─ true  → deductQuota(uid, group.getId(), cost)   // 扣对应分组的订阅
      └─ false → deductByAuthUserId(uid, cost)           // 扣余额
      dailyQuotaService.addCost(uid, cost)
      relayRecordRepository.save(item)
```

**以 codex + claude 场景为例**：

| 场景 | group_id | 订阅匹配结果 | 扣费方式 |
|------|----------|------------|--------|
| 调用 codex，绑了 codex 订阅 | codex 分组 ID | 命中 group_id = codex | 扣订阅 |
| 调用 claude，无 claude 订阅 | claude 分组 ID | 未命中 | 扣余额 |
| 调用 claude，买了 claude 订阅 | claude 分组 ID | 命中 group_id = claude | 扣订阅（未来可扩展）|
| 存量订阅（group_id IS NULL） | 任意 | 兜底命中 | 扣订阅（向后兼容）|

---

## 七、兼容性

| 场景 | 处理 |
|------|------|
| 存量订阅 `group_id` 为 null | 兜底逻辑，行为与改造前完全一致 |
| 前端未传 groupId | 后端 create 时 groupId 为 null，写入 null，走兜底 |
| `groups` 中的分组被删除 | `group_id` 失效，兜底逻辑接管（或查不到订阅则扣余额） |

---

## 八、任务拆解

| # | 任务 | 层次 | 备注 |
|---|------|------|------|
| 1 | `schema.sql` 新增 `subscriptions.group_id` 列（条件 DDL） | DB | P0 |
| 2 | `SubscriptionItem` + `Request` 新增 `groupId` 字段 | 后端 | P0 |
| 3 | `SubscriptionRepository` 新增 `findActiveByUidAndGroup` 方法，ALL_COLS / ROW_MAPPER / toColumnValues 更新 | 后端 | P0 |
| 4 | `SubscriptionService` 新增带 `groupId` 的重载方法，create/update 同步 group_name | 后端 | P0 |
| 5 | `RelayService.checkBilling` 传入 `group.getId()` | 后端 | P0 |
| 6 | `RelayRecordService.record` 扣费时传入 `group.getId()` | 后端 | P0 |
| 7 | `Subscriptions.jsx` 订阅等级下拉改为动态分组列表，formData 改用 groupId | 前端 | P0 |
