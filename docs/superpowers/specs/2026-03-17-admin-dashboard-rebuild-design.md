# Admin Dashboard Rebuild Design

## Summary

Rebuild the admin dashboard so it matches the provided screenshots in structure and interaction while using real backend data instead of demo data.

The rebuilt dashboard will:

- use a global admin view
- support screenshot-style date range presets and custom date ranges
- keep chart granularity fixed to `day`
- show real token, request, user, model, latency, and cost metrics
- use frozen per-request billing snapshots so historical money totals stay stable

## Goals

- Replace the current placeholder dashboard data model and UI
- Match the screenshot layout closely: summary cards, date control bar, model distribution, token trend, and recent usage top 12
- Query real data from `relay_records`
- Support date presets: `today`, `yesterday`, `last7days`, `last14days`, `last30days`, `thisMonth`, `lastMonth`, `custom`
- Show costs using historical request-time pricing snapshots

## Non-Goals

- No hourly or weekly dashboard granularity in this iteration
- No dashboard export feature
- No live auto-refresh or websocket updates
- No backfill job for old records beyond safe defaults for missing billing fields

## Why This Shape

The current dashboard mixes placeholder cards, empty charts, and an unrelated alerts table. The screenshot target is a system overview page centered on usage and billing telemetry. The backend already stores real relay usage, so the right long-term shape is:

- record request-time billing snapshots once
- aggregate dashboard data on query
- keep frontend rendering thin and deterministic

This avoids historical cost drift when group multipliers or pricing rules change later.

## Data Model

### Source Table: `relay_records`

Keep the real token usage fields:

- `prompt_tokens`
- `completion_tokens`
- `total_tokens`
- `latency_ms`
- `model_name`
- `owner_id`
- `created_at`

Add frozen billing snapshot fields:

- `group_name_snapshot`
- `group_rate_snapshot`
- `pricing_model_key`
- `input_price_per_million`
- `output_price_per_million`
- `standard_cost`
- `actual_cost`

Recommended SQL types:

- multiplier and prices: `DECIMAL`
- money: `DECIMAL(18,6)`

### Billing Formula

`standard_cost`:

```text
(prompt_tokens / 1_000_000) * input_price_per_million
+ (completion_tokens / 1_000_000) * output_price_per_million
```

`actual_cost`:

```text
standard_cost * group_rate_snapshot
```

### Pricing Inputs

- OpenAI pricing comes from official OpenAI pricing pages
- Anthropic pricing comes from official Anthropic pricing pages
- model-to-pricing mapping is resolved when the request is recorded

Unknown models should not block relay recording. They should fall back to safe zero-cost snapshots and a warning log until a mapping rule is added.

## Backend API

### Endpoint

- `GET /api/admin/dashboard`

### Query Parameters

- `rangePreset`
- `startDate`
- `endDate`

`granularity` stays fixed to `day` and does not need to be accepted as a request parameter in this iteration.

### Response Shape

```json
{
  "filters": {
    "preset": "last7days",
    "startDate": "2026-03-11",
    "endDate": "2026-03-17",
    "granularity": "day",
    "availablePresets": [
      "today",
      "yesterday",
      "last7days",
      "last14days",
      "last30days",
      "thisMonth",
      "lastMonth",
      "custom"
    ]
  },
  "summaryCards": [],
  "modelDistribution": {
    "chart": [],
    "rows": []
  },
  "tokenTrend": [],
  "recentUsageTop12": {
    "users": [],
    "series": []
  }
}
```

## Dashboard Modules

### Summary Cards

Rebuild the top summary section as 8 cards:

1. `API 密钥`
2. `账号`
3. `今日请求`
4. `用户`
5. `今日 Token`
6. `总 Token`
7. `性能指标`
8. `平均响应`

Card rules:

- token cards show real token values
- token cards also show `actual_cost / standard_cost`
- performance card shows RPM and TPM
- average response card shows average latency and active user count

### Date Control Bar

Support the screenshot interaction:

- preset dropdown
- custom start date
- custom end date
- apply button
- fixed `按天` granularity display on the right

### Model Distribution

Use one aggregated model dataset for both:

- donut chart on the left
- table on the right

Table columns:

- `模型`
- `请求`
- `Token`
- `实际`
- `标准`

Distribution share is based on total tokens within the selected time window.

### Token Trend

Daily points with:

- `inputTokens`
- `outputTokens`
- `cacheTokens`
- `requests`
- `actualCost`
- `standardCost`

Current schema has no cache token fields, so `cacheTokens` should return zero in this iteration while the frontend keeps the series slot for screenshot parity.

### Recent Usage Top 12

Select the top 12 users by total token consumption within the selected window, then expand each selected user into a daily series for the chart.

## Aggregation Rules

- Global admin scope only
- Date filtering applies to all dashboard modules
- Empty windows return zeroed cards and empty arrays, not errors
- `今日请求` and `今日 Token` use the end date of the selected window
- `总 Token` uses the whole selected window
- `平均响应` uses average `latency_ms` across the whole selected window

## Error Handling

- Invalid preset: `400`
- Missing `startDate` or `endDate` for `custom`: `400`
- `startDate > endDate`: `400`
- Excessive custom range: reject with `400` once above the chosen max window

Dashboard aggregation should be resilient to partially missing billing snapshots:

- token metrics still render
- money cells render `--` when a required billing field is absent

## Frontend

Replace the existing dashboard page with a dedicated layout that mirrors the screenshot:

- 8 summary cards
- control bar
- two-column middle section
- full-width recent usage chart

The page should keep the existing project visual language and reuse current card, control, and chart styling where that helps, but the module structure should match the screenshot rather than the existing placeholder implementation.

## Testing Strategy

### Backend

- range preset parsing
- custom date validation
- frozen billing snapshot calculation
- summary card aggregation
- model distribution aggregation
- daily token trend aggregation
- top 12 user selection and daily expansion
- empty dataset behavior

### Frontend

- initial dashboard fetch
- preset selection and apply flow
- custom date apply flow
- rendering of 8 cards
- rendering of model table and charts
- empty states without crashes

## Implementation Sequence

1. Extend relay record persistence for billing snapshots
2. Freeze pricing and group-rate snapshots when relay records are written
3. Replace the dashboard backend response model and aggregation service
4. Add backend tests for range parsing and aggregation
5. Add frontend tests for dashboard rendering and controls
6. Rebuild the dashboard page to match the screenshot
7. Run backend and frontend verification
