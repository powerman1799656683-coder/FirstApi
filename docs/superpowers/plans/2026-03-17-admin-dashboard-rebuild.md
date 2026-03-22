# Admin Dashboard Rebuild Implementation Plan

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Rebuild the admin dashboard to match the provided screenshot using real global admin metrics, frozen billing snapshots, and date-range-driven aggregations.

**Architecture:** Extend `relay_records` so each request stores enough pricing and group-rate snapshot data to preserve historical money totals, then aggregate dashboard modules directly from relay records over a selected date window. Keep the frontend thin by returning one dashboard payload that already contains summary cards, model distribution rows, daily token trend data, and top-12 recent usage series.

**Tech Stack:** Spring Boot 2.7, Java 8, JdbcTemplate, JUnit 5, Vitest, React 19, Recharts

---

## Chunk 1: Billing Snapshot Foundation

### Task 1: Add failing backend tests for relay billing snapshots

**Files:**
- Test: `backend/src/test/java/com/firstapi/backend/service/RelayRecordServiceTest.java`
- Test: `backend/src/test/java/com/firstapi/backend/repository/RelayRecordRepositoryTest.java`

- [ ] **Step 1: Write the failing tests**

```java
@SpringBootTest
@ActiveProfiles("test")
class RelayRecordServiceTest {

    @Autowired
    private RelayRecordService service;

    @Autowired
    private GroupRepository groupRepository;

    @Autowired
    private UserRepository userRepository;

    @Test
    void recordsFrozenBillingSnapshots() {
        RelayResult result = new RelayResult();
        result.setPromptTokens(100_000);
        result.setCompletionTokens(20_000);
        result.setTotalTokens(120_000);
        result.setLatencyMs(450L);
        result.setStatusCode(200);
        result.setSuccess(true);

        service.record(apiKey(), route("gpt-4o"), result, "gpt-4o");

        RelayRecordItem saved = repository.findAll().get(0);
        assertThat(saved.getGroupRateSnapshot()).isEqualByComparingTo("0.5000");
        assertThat(saved.getStandardCost()).isEqualByComparingTo("0.600000");
        assertThat(saved.getActualCost()).isEqualByComparingTo("0.300000");
    }
}
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `mvn "-Dtest=RelayRecordServiceTest,RelayRecordRepositoryTest" test`
Expected: FAIL because snapshot fields and billing calculation support do not exist yet

- [ ] **Step 3: Implement the minimal schema and model changes**

```sql
alter table `relay_records`
    add column `group_name_snapshot` varchar(128) null,
    add column `group_rate_snapshot` decimal(8,4) null,
    add column `pricing_model_key` varchar(128) null,
    add column `input_price_per_million` decimal(18,6) null,
    add column `output_price_per_million` decimal(18,6) null,
    add column `standard_cost` decimal(18,6) null,
    add column `actual_cost` decimal(18,6) null;
```

- [ ] **Step 4: Run the tests to verify they pass**

Run: `mvn "-Dtest=RelayRecordServiceTest,RelayRecordRepositoryTest" test`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/resources/schema.sql backend/src/main/java/com/firstapi/backend/model/RelayRecordItem.java backend/src/main/java/com/firstapi/backend/repository/RelayRecordRepository.java backend/src/main/java/com/firstapi/backend/service/RelayRecordService.java backend/src/test/java/com/firstapi/backend/service/RelayRecordServiceTest.java backend/src/test/java/com/firstapi/backend/repository/RelayRecordRepositoryTest.java
git commit -m "feat: freeze relay billing snapshots"
```

### Task 2: Add model pricing lookup with request-time calculation

**Files:**
- Create: `backend/src/main/java/com/firstapi/backend/service/ModelPricingService.java`
- Test: `backend/src/test/java/com/firstapi/backend/service/ModelPricingServiceTest.java`
- Modify: `backend/src/main/java/com/firstapi/backend/service/RelayRecordService.java`

- [ ] **Step 1: Write the failing pricing tests**

```java
@SpringBootTest
class ModelPricingServiceTest {

    @Autowired
    private ModelPricingService service;

    @Test
    void resolvesOpenAiModelPricing() {
        PricingSnapshot snapshot = service.resolve("gpt-4o");
        assertThat(snapshot.getInputPricePerMillion()).isPositive();
        assertThat(snapshot.getOutputPricePerMillion()).isPositive();
    }
}
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `mvn "-Dtest=ModelPricingServiceTest,RelayRecordServiceTest" test`
Expected: FAIL because pricing lookup support does not exist yet

- [ ] **Step 3: Implement the minimal pricing lookup**

```java
public PricingSnapshot resolve(String modelName) {
    if (modelName.startsWith("gpt-4o")) {
        return new PricingSnapshot("gpt-4o", new BigDecimal("5.000000"), new BigDecimal("15.000000"));
    }
    return PricingSnapshot.zero(modelName);
}
```

- [ ] **Step 4: Run the tests to verify they pass**

Run: `mvn "-Dtest=ModelPricingServiceTest,RelayRecordServiceTest" test`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/firstapi/backend/service/ModelPricingService.java backend/src/test/java/com/firstapi/backend/service/ModelPricingServiceTest.java backend/src/main/java/com/firstapi/backend/service/RelayRecordService.java
git commit -m "feat: add model pricing snapshots"
```

## Chunk 2: Dashboard Backend Aggregation

### Task 3: Add failing dashboard aggregation tests

**Files:**
- Test: `backend/src/test/java/com/firstapi/backend/service/DashboardServiceTest.java`
- Modify: `backend/src/main/java/com/firstapi/backend/model/DashboardData.java`
- Modify: `backend/src/main/java/com/firstapi/backend/service/DashboardService.java`
- Modify: `backend/src/main/java/com/firstapi/backend/controller/DashboardController.java`

- [ ] **Step 1: Write the failing dashboard service tests**

```java
@SpringBootTest
@ActiveProfiles("test")
class DashboardServiceTest {

    @Autowired
    private DashboardService service;

    @Test
    void aggregatesLast7DaysDashboard() {
        DashboardData data = service.getDashboard("last7days", null, null);

        assertThat(data.summaryCards).hasSize(8);
        assertThat(data.tokenTrend).hasSize(7);
        assertThat(data.modelDistribution.rows).isNotEmpty();
    }
}
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `mvn "-Dtest=DashboardServiceTest" test`
Expected: FAIL because the new dashboard data model and query signature are missing

- [ ] **Step 3: Implement the minimal dashboard aggregation**

```java
public DashboardData getDashboard(String rangePreset, String startDate, String endDate) {
    DateWindow window = rangeResolver.resolve(rangePreset, startDate, endDate);
    List<RelayRecordItem> records = repository.findBetween(window.getStart(), window.getEnd());
    return aggregate(records, window);
}
```

- [ ] **Step 4: Run the tests to verify they pass**

Run: `mvn "-Dtest=DashboardServiceTest" test`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/firstapi/backend/model/DashboardData.java backend/src/main/java/com/firstapi/backend/service/DashboardService.java backend/src/main/java/com/firstapi/backend/controller/DashboardController.java backend/src/test/java/com/firstapi/backend/service/DashboardServiceTest.java
git commit -m "feat: add real dashboard aggregations"
```

### Task 4: Add failing controller tests for preset and custom date filters

**Files:**
- Test: `backend/src/test/java/com/firstapi/backend/controller/DashboardControllerTest.java`
- Modify: `backend/src/main/java/com/firstapi/backend/controller/DashboardController.java`
- Modify: `backend/src/main/java/com/firstapi/backend/service/DashboardService.java`

- [ ] **Step 1: Write the failing controller tests**

```java
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class DashboardControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void rejectsCustomRangeWithoutDates() throws Exception {
        mockMvc.perform(get("/api/admin/dashboard").param("rangePreset", "custom"))
            .andExpect(status().isBadRequest());
    }
}
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `mvn "-Dtest=DashboardControllerTest" test`
Expected: FAIL because validation is not implemented yet

- [ ] **Step 3: Implement the minimal request validation**

```java
if ("custom".equals(rangePreset) && (isBlank(startDate) || isBlank(endDate))) {
    throw new IllegalArgumentException("Custom date range requires startDate and endDate");
}
```

- [ ] **Step 4: Run the tests to verify they pass**

Run: `mvn "-Dtest=DashboardControllerTest" test`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add backend/src/test/java/com/firstapi/backend/controller/DashboardControllerTest.java backend/src/main/java/com/firstapi/backend/controller/DashboardController.java backend/src/main/java/com/firstapi/backend/service/DashboardService.java
git commit -m "test: validate dashboard date filters"
```

## Chunk 3: Dashboard Frontend Rebuild

### Task 5: Add failing frontend dashboard rendering tests

**Files:**
- Test: `frontend/src/test/Dashboard.test.jsx`
- Modify: `frontend/src/pages/Dashboard.jsx`

- [ ] **Step 1: Write the failing dashboard page tests**

```jsx
it('renders eight summary cards from the dashboard payload', async () => {
  api.get.mockResolvedValue(mockDashboardPayload)
  render(<Dashboard />)
  expect(await screen.findByText('今日 Token')).toBeInTheDocument()
  expect(screen.getByText('模型分布')).toBeInTheDocument()
  expect(screen.getByText('Token 使用趋势')).toBeInTheDocument()
  expect(screen.getByText('最近使用(Top 12)')).toBeInTheDocument()
})
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `npm test -- Dashboard.test.jsx`
Expected: FAIL because the current dashboard page does not render the screenshot structure

- [ ] **Step 3: Implement the minimal page structure**

```jsx
<section className="dashboard-summary-grid">{cards.map(renderCard)}</section>
<section className="dashboard-filter-bar">{renderFilters()}</section>
<section className="dashboard-main-grid">{renderModelDistribution()}{renderTokenTrend()}</section>
<section className="dashboard-recent-usage">{renderRecentUsage()}</section>
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `npm test -- Dashboard.test.jsx`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add frontend/src/test/Dashboard.test.jsx frontend/src/pages/Dashboard.jsx
git commit -m "feat: rebuild dashboard page structure"
```

### Task 6: Add failing frontend filter interaction tests

**Files:**
- Test: `frontend/src/test/Dashboard.test.jsx`
- Modify: `frontend/src/pages/Dashboard.jsx`
- Modify: `frontend/src/index.css`

- [ ] **Step 1: Extend the failing test for preset and custom apply**

```jsx
it('requests dashboard data again when the date range is applied', async () => {
  api.get.mockResolvedValue(mockDashboardPayload)
  render(<Dashboard />)
  await screen.findByText('时间范围')
  await user.click(screen.getByRole('button', { name: /近 7 天/i }))
  await user.click(screen.getByText('近 30 天'))
  expect(api.get).toHaveBeenLastCalledWith('/admin/dashboard?rangePreset=last30days')
})
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `npm test -- Dashboard.test.jsx`
Expected: FAIL because the current page has no preset/custom date control behavior

- [ ] **Step 3: Implement the minimal interaction and styling**

```jsx
const loadDashboard = (params) => api.get(`/admin/dashboard${toQuery(params)}`)
```

```css
.dashboard-range-popover {
  position: absolute;
  inset-inline-start: 0;
  top: calc(100% + 12px);
}
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `npm test -- Dashboard.test.jsx`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add frontend/src/test/Dashboard.test.jsx frontend/src/pages/Dashboard.jsx frontend/src/index.css
git commit -m "feat: add dashboard date range controls"
```

## Chunk 4: Verification

### Task 7: Run focused and full verification

**Files:**
- Modify: `frontend/src/pages/Dashboard.jsx`
- Modify: `backend/src/main/java/com/firstapi/backend/service/DashboardService.java`

- [ ] **Step 1: Run focused backend dashboard tests**

Run: `mvn "-Dtest=RelayRecordServiceTest,ModelPricingServiceTest,DashboardServiceTest,DashboardControllerTest" test`
Expected: PASS

- [ ] **Step 2: Run focused frontend dashboard tests**

Run: `npm test -- Dashboard.test.jsx`
Expected: PASS

- [ ] **Step 3: Run backend and frontend build verification**

Run: `mvn test`
Expected: PASS

Run: `npm run build`
Expected: PASS

- [ ] **Step 4: Fix any verification regressions and re-run**

Run: repeat the failing command until it passes cleanly
Expected: PASS with no dashboard-specific regressions

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/firstapi/backend/service/DashboardService.java frontend/src/pages/Dashboard.jsx frontend/src/index.css
git commit -m "test: verify dashboard rebuild"
```

## Manual Notes For The Implementer

- Do not overwrite unrelated user changes in the dirty worktree.
- Prefer extending existing relay persistence rather than introducing a second billing table.
- Keep money calculations in `BigDecimal` from the first implementation.
- If an old record has no billing snapshot data, render token metrics normally and treat money values as absent in dashboard aggregates.
- Use the screenshot structure as the source of truth for the frontend layout, not the current dashboard page.
