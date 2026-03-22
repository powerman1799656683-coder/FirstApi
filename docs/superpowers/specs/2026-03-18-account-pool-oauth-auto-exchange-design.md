# Account Pool, Add-Account Config, And OAuth Code Auto-Exchange Design

## 1. Summary

This spec defines a practical, implementation-ready design for:

1. Account pool page capabilities and response fields
2. Add-account page configurable options and validation
3. "Paste code and auto-exchange" OAuth flow (admin pastes auth code, system exchanges and stores credential securely)

The design is based on the current codebase and keeps backward compatibility where possible.

---

## 2. Current Baseline (As-Is)

## 2.1 Existing account APIs

- `GET /api/admin/accounts` (keyword only)
- `GET /api/admin/accounts/{id}`
- `POST /api/admin/accounts`
- `PUT /api/admin/accounts/{id}`
- `DELETE /api/admin/accounts/{id}`
- `POST /api/admin/accounts/{id}/test`
- `POST /api/admin/accounts/oauth/start` (returns auth URL + state + session metadata)
- `POST /api/admin/accounts/oauth/exchange` (exchanges code and returns credentialRef)

All responses are wrapped by:

```json
{
  "success": true,
  "message": "ok",
  "data": {}
}
```

`PageResponse` currently returns only:

```json
{
  "items": [],
  "total": 0
}
```

## 2.2 Existing account data and scheduling behavior

The current `accounts` model already includes many useful fields:

- Basic: `id`, `name`, `platform`, `type`, `notes`, `baseUrl`, `models`
- Auth: `credential`, `accountType`, `authMethod`
- Scheduling: `tempDisabled`, `priorityValue`, `concurrency`
- Cost/ops: `billingRate`, `balance`, `weight`
- Expiry: `expiryTime`, `autoSuspendExpiry`
- Proxy: `proxyId`
- Runtime summary: `capacityUsed`, `capacityLimit`, `todayRequests`, `todayTokens`, `todayAccountCost`, `todayUserCost`, `groups`, `usageWindows`, `recentUsedAt`

Selector behavior in relay path is already meaningful for:

- Health/status filtering
- Temporary disable filtering
- Expiry filtering (if `autoSuspendExpiry=true`)
- Priority tiering (`priorityValue` lower = higher priority)
- Concurrency gating
- Round-robin within same priority
- Proxy resolution per account

## 2.3 Current gaps

1. OAuth support is incomplete: only URL generation exists, code exchange is missing.
2. Account list lacks server-side pagination/filter/sort contract.
3. Add-account page config options in target UI are not fully represented in backend contract.
4. Some config switches are display-only in target UI unless connected to runtime guard logic.
5. Group relation is currently derived by platform, not explicit account-group binding.

---

## 3. Goals

1. Define a stable API contract for account pool page data.
2. Define complete add-account configuration schema with validation rules.
3. Implement secure OAuth code auto-exchange for Anthropic Claude Code first.
4. Ensure each major config has a real runtime effect or explicit rollout phase.
5. Keep existing endpoints usable during migration.
6. Phase 1 add-account authorization path is OAuth-only in UI; `API Key` and `SetupToken` remain reserved for later rollout.

## 3.1 Non-goals (this iteration)

1. Full browser callback hosting inside admin backend (manual paste-code flow is enough for phase 1).
2. Multi-provider OAuth auto-exchange in one batch (phase 1 is Anthropic-first).
3. Rebuilding the entire relay protocol layer.

---

## 4. Target Functional Scope

## 4.1 Account pool page

Must support:

- Search, filter, sort, pagination on server side
- Row-level operations (edit, delete, test, enable/disable scheduling)
- Batch operations (schedule toggle, delete, test)
- Display columns aligned with operations value:
  - Name
  - Platform/type/auth tag
  - Capacity
  - Status
  - Scheduling state
  - Today statistics
  - Bound groups
  - Usage windows
  - Operations

## 4.2 Add-account page

Must support:

- Platform selection: `Anthropic`, `OpenAI`, `Gemini`, `Antigravity`
- Account type selection (platform-dependent)
- Auth method selection:
  - Phase 1 create flow: fixed to `OAuth`
  - `SetupToken` and `API Key` paths remain in backend contract for compatibility, but are not opened in create UI
- Base scheduling controls
- Advanced controls (with rollout flag if backend effect is phase 2)
- Proxy, expiry, billing rate, group binding

## 4.3 OAuth code auto-exchange

Must support:

1. Generate auth URL + secure session state
2. Admin pastes `code`
3. Backend exchanges code via provider adapter
4. Backend stores credential encrypted
5. Account creation/update can consume exchanged credential without exposing plain secret in UI

---

## 5. Account Pool API Contract (To-Be)

## 5.1 List endpoint

### Endpoint

`GET /api/admin/accounts`

### Query params

- `keyword`
- `platform`
- `status`
- `authMethod`
- `scheduleEnabled` (`true|false`)
- `groupId`
- `page` (default `1`)
- `size` (default `20`, max `100`)
- `sortBy` (default `priorityValue`)
- `sortOrder` (`asc|desc`)

### Response shape

```json
{
  "items": [
    {
      "id": 12,
      "name": "claude_max20_a",
      "platform": "Anthropic",
      "type": "Claude Code",
      "accountType": "Claude Code",
      "authMethod": "OAuth",
      "credentialMask": "sk-a1b2****z9y8",
      "status": "normal",
      "effectiveStatus": "normal",
      "scheduleEnabled": true,
      "tempDisabled": false,
      "concurrency": 10,
      "capacityUsed": 3,
      "capacityLimit": 10,
      "priorityValue": 1,
      "weight": 1,
      "billingRate": 1.0,
      "todayRequests": 124,
      "todayTokens": 4521930,
      "todayAccountCost": "US$32.10",
      "todayUserCost": "US$38.52",
      "groups": ["claude_pro", "codex_premium"],
      "usageWindows": [
        { "key": "3h", "label": "3h", "used": 20, "limit": 120, "percentage": 17, "tone": "low" },
        { "key": "24h", "label": "24h", "used": 4521930, "limit": 20000000, "percentage": 23, "tone": "low" },
        { "key": "7d", "label": "7d", "used": 1, "limit": 20, "percentage": 5, "tone": "low" }
      ],
      "proxyId": 2,
      "expiryTime": "2026-12-31T23:59",
      "autoSuspendExpiry": true,
      "expired": false,
      "expiryLabel": "2026-12-31T23:59",
      "lastCheck": "2026/03/18 11:26:00",
      "errors": 0,
      "recentUsedAt": "2026/03/18 11:24:11",
      "recentUsedText": "2 minutes ago",
      "notes": "primary pool"
    }
  ],
  "total": 327,
  "page": 1,
  "size": 20
}
```

## 5.2 Field-by-field definition for account pool rows

| UI Column | API Field | Type | Meaning | Runtime significance |
|---|---|---|---|---|
| Name | `name` | string | Account display name | Routing visibility and ops targeting |
| Platform/type | `platform`, `accountType`, `authMethod` | string | Provider and authorization method tags | Provider routing and auth header shape |
| Capacity | `capacityUsed`, `capacityLimit`, `concurrency` | int | Current usage vs limit | Directly controls selector admission |
| Status | `status`, `effectiveStatus`, `errors`, `lastCheck` | string/int | Health and effective availability | Selector skips disabled/error/expired/temp-disabled |
| Scheduling | `scheduleEnabled`, `tempDisabled`, `priorityValue`, `weight` | bool/int | Dispatch switches and order | Priority and weighted dispatch |
| Today stats | `todayRequests`, `todayTokens`, `todayAccountCost`, `todayUserCost` | int/string | Daily ops and cost telemetry | Used by risk controls and operations |
| Groups | `groups` | string[] | Bound groups/tags | Group-aware routing and filtering |
| Usage windows | `usageWindows[]` | object[] | 3h/24h/7d utilization window | Trigger conditions for throttling guards |
| Credential | `credentialMask` | string | Masked credential only | No plain secret exposure |
| Expiry | `expiryTime`, `autoSuspendExpiry`, `expired`, `expiryLabel` | mixed | Expiry state | Selector expiry filtering |

## 5.3 Detail endpoint

### Endpoint

`GET /api/admin/accounts/{id}`

### Returns

All list fields plus advanced config fields:

- `interceptWarmupRequest`
- `window5hCostControlEnabled`
- `window5hCostLimitUsd`
- `sessionCountControlEnabled`
- `sessionCountLimit`
- `tlsFingerprintMode`
- `sessionIdMasqueradeEnabled`
- `sessionIdMasqueradeTtlMinutes`
- `groupIds` (explicit binding IDs)

---

## 6. Add-Account Page Design

## 6.1 Wizard steps

1. **Step 1: Identity and auth mode**
   - Name, notes
   - Platform
   - Account type
   - Auth method (Phase 1: fixed `OAuth` for create flow)
2. **Step 2: Authorization data**
   - OAuth path: generate URL, open browser, paste callback `code`, auto-exchange
   - SetupToken/API Key path: reserved (not open in Phase 1 create UI)
3. **Step 3: Scheduling and controls**
   - Temp disable, concurrency, priority, weight
   - Proxy
   - Billing rate
   - Expiry + auto-suspend
   - Advanced controls
   - Group bindings
4. **Step 4: Review and save**
   - Display normalized config summary
   - Save and optional immediate connectivity test

## 6.2 Add-account request contract

### Endpoint

`POST /api/admin/accounts`

### Request body (extended)

```json
{
  "name": "claude_max20_a",
  "platform": "Anthropic",
  "accountType": "Claude Code",
  "authMethod": "OAuth",
  "credentialRef": "oauth_sess_8f1c...",
  "baseUrl": "",
  "models": "claude-3-7-sonnet,claude-3-5-haiku",
  "notes": "created by OAuth auto-exchange",
  "tempDisabled": false,
  "interceptWarmupRequest": true,
  "window5hCostControlEnabled": true,
  "window5hCostLimitUsd": 30.0,
  "sessionCountControlEnabled": true,
  "sessionCountLimit": 80,
  "tlsFingerprintMode": "CLAUDE_CODE",
  "sessionIdMasqueradeEnabled": true,
  "sessionIdMasqueradeTtlMinutes": 15,
  "proxyId": 2,
  "concurrency": 10,
  "priorityValue": 1,
  "weight": 1,
  "billingRate": 1.0,
  "expiryTime": "2026-12-31T23:59",
  "autoSuspendExpiry": true,
  "groupIds": [3, 9]
}
```

`credential` remains supported for backward compatibility when `credentialRef` is absent.

For Phase 1 create flow:
- `authMethod` must be `OAuth`
- `credentialRef` is required
- plain `credential` is rejected for OAuth create/update payloads

## 6.3 Config matrix and validation

| Config | Type | Default | Validation | Runtime effect |
|---|---|---|---|---|
| `name` | string | none | required, 3-64 chars | Human ops and targeting |
| `platform` | enum | `OpenAI` | required | Provider route and header mode |
| `accountType` | enum | provider default | required | Capability display and policy |
| `authMethod` | enum | by account type | required | Determines credential handling path |
| `credential` | string | null | required for non-OAuth paths | Upstream auth secret |
| `credentialRef` | string | null | required for OAuth auto-exchange path | Server-side secure credential handoff |
| `tempDisabled` | bool | false | none | Hard off for scheduler |
| `concurrency` | int | 10 | 1..500 | Admission limit in selector |
| `priorityValue` | int | 1 | 1..1000 | Scheduling priority tier |
| `weight` | int | 1 | 1..100 | Weighted selection in same priority |
| `billingRate` | decimal | 1.0 | >= 0 | Cost calculation multiplier |
| `proxyId` | long | null | must exist and healthy | Upstream network route |
| `expiryTime` | string | null | `yyyy-MM-ddTHH:mm` | Expiry-based suspension |
| `autoSuspendExpiry` | bool | true | none | Expiry enforcement switch |
| `groupIds` | long[] | [] | each id exists | Group-aware filtering/routing |
| `interceptWarmupRequest` | bool | false | none | Skip known prewarm requests with mock response |
| `window5hCostControlEnabled` | bool | false | none | Sliding window cost limiter |
| `window5hCostLimitUsd` | decimal | null | > 0 when enabled | Threshold for limiter |
| `sessionCountControlEnabled` | bool | false | none | Session cardinality limiter |
| `sessionCountLimit` | int | null | > 0 when enabled | Threshold for limiter |
| `tlsFingerprintMode` | enum | `NONE` | enum | Upstream TLS profile hint (via proxy or client profile) |
| `sessionIdMasqueradeEnabled` | bool | false | none | Sticky session ID mapping |
| `sessionIdMasqueradeTtlMinutes` | int | 15 | 1..240 | Sticky session TTL |

---

## 7. OAuth Code Auto-Exchange Design

## 7.1 User flow (Anthropic first)

1. Admin selects `Anthropic + Claude Code + OAuth`
2. UI calls `POST /api/admin/accounts/oauth/start`
3. Backend returns `authorizationUrl`, `state`, `sessionId`, `expiresAt`
4. Admin opens URL, authorizes in browser, copies returned `code`
5. Admin pastes `code` in UI and clicks `Auto Exchange`
6. UI calls `POST /api/admin/accounts/oauth/exchange`
7. Backend exchanges code at provider token endpoint via adapter, stores encrypted credential in OAuth session record
8. Backend returns `credentialRef` (session-bound reference), no plain secret returned
9. Final account save uses `credentialRef`

## 7.2 API contract

### Start OAuth

`POST /api/admin/accounts/oauth/start`

Request:

```json
{
  "platform": "Anthropic",
  "accountType": "Claude Code",
  "authMethod": "OAuth",
  "purpose": "create"
}
```

Response:

```json
{
  "sessionId": "oauth_sess_18b7...",
  "state": "st_7fcb...",
  "authorizationUrl": "https://console.anthropic.com/oauth/authorize?...",
  "expiresAt": "2026-03-18T12:20:00Z"
}
```

### Exchange code

`POST /api/admin/accounts/oauth/exchange`

Request:

```json
{
  "sessionId": "oauth_sess_18b7...",
  "state": "st_7fcb...",
  "code": "auth_code_from_browser"
}
```

Response:

```json
{
  "credentialRef": "oauth_sess_18b7...",
  "credentialMask": "sk-ant-****-a8Pq",
  "authMethod": "OAuth",
  "providerAccount": {
    "provider": "Anthropic",
    "subject": "org_abc123"
  },
  "expiresAt": null
}
```

## 7.3 OAuth session lifecycle

States:

- `PENDING` (URL generated)
- `EXCHANGED` (credential acquired and encrypted)
- `CONSUMED` (account created/updated using this credentialRef)
- `FAILED` (exchange failed)
- `EXPIRED` (ttl reached)

Session TTL recommendation: 10 minutes.

## 7.4 Security controls

1. Enforce admin auth on all OAuth endpoints.
2. `state` must match and be single-use.
3. Rate limit exchange attempts per session and admin user.
4. Never log plain `code`, token, setup token, or API key.
5. Encrypt all exchanged credentials with existing `SensitiveDataService`.
6. Expire and hard-delete stale OAuth sessions on cleanup job.

## 7.5 Provider adapter abstraction

Define:

```java
interface AccountOAuthProvider {
    OAuthStartResult start(OAuthStartRequest req);
    OAuthExchangeResult exchange(OAuthExchangeRequest req);
}
```

Phase 1 implementation: `AnthropicOAuthProvider`.

This keeps extension path for OpenAI/Gemini without changing controller contract.

---

## 8. Database Changes

## 8.1 `accounts` table additions

Add columns:

- `intercept_warmup_request` `tinyint(1)` default `0`
- `window5h_cost_control_enabled` `tinyint(1)` default `0`
- `window5h_cost_limit_usd` `decimal(12,2)` null
- `session_count_control_enabled` `tinyint(1)` default `0`
- `session_count_limit` `int` null
- `tls_fingerprint_mode` `varchar(32)` default `'NONE'`
- `session_id_masquerade_enabled` `tinyint(1)` default `0`
- `session_id_masquerade_ttl_minutes` `int` default `15`

## 8.2 `account_group_bindings`

```sql
create table if not exists account_group_bindings (
  account_id bigint not null,
  group_id bigint not null,
  created_at timestamp not null default current_timestamp,
  primary key (account_id, group_id),
  key idx_agb_group (group_id)
);
```

## 8.3 `account_oauth_sessions`

```sql
create table if not exists account_oauth_sessions (
  id bigint not null auto_increment,
  session_id varchar(64) not null,
  state_value varchar(128) not null,
  platform varchar(32) not null,
  account_type varchar(64) not null,
  auth_method varchar(32) not null,
  status_name varchar(32) not null,
  encrypted_credential text null,
  credential_mask varchar(64) null,
  provider_subject varchar(128) null,
  error_text text null,
  expires_at datetime not null,
  exchanged_at datetime null,
  consumed_at datetime null,
  created_by bigint not null,
  updated_at timestamp not null default current_timestamp on update current_timestamp,
  primary key (id),
  unique key uk_oauth_session_id (session_id),
  unique key uk_oauth_state (state_value)
);
```

---

## 9. Runtime Enforcement Matrix (Configuration -> Real Effect)

| Config | Enforcement point | Behavior |
|---|---|---|
| `tempDisabled` | `RelayAccountSelector.findEligible` | Account excluded from dispatch |
| `concurrency` | `RelayAccountSelector.tryAcquire` | In-flight limit per account |
| `priorityValue` | `RelayAccountSelector.selectAccount` | Priority-tier dispatch order |
| `weight` | `RelayAccountSelector.pickFromTier` | Weighted selection within same priority |
| `proxyId` | `RelayAccountSelector.resolveProxy` | Route request through bound proxy |
| `expiryTime` + `autoSuspendExpiry` | Selector expiry check | Account excluded after expiry |
| `groupIds` | Selector filter by caller group | Restrict candidate set to bound groups |
| `window5hCostControl*` | New `AccountCostGuard` pre-selector | Temporarily throttle account when 5h spend exceeds threshold |
| `sessionCountControl*` | New `SessionCountGuard` pre-selector | Reject/throttle when active session cardinality exceeds threshold |
| `interceptWarmupRequest` | New `RelayRequestPrecheckService` | Return mock response for recognized prewarm patterns |
| `tlsFingerprintMode` | `UpstreamHttpClient`/proxy adapter | Apply TLS profile hint when transport supports it |
| `sessionIdMasquerade*` | Request rewrite layer | Stable masked session id mapping in configured TTL |

If transport does not support TLS profile emulation, `tlsFingerprintMode` is stored and displayed but marked `degraded` in runtime diagnostics.

---

## 10. Implementation Plan (Must-Do)

## Phase 1 (MVP, mandatory)

1. Extend account query contract:
   - server-side filter/sort/pagination
   - list/detail field completion
2. Add OAuth session model + repo + service:
   - `oauth/start`
   - `oauth/exchange`
3. Extend create/update account contract with `credentialRef`
4. Add DB migrations for OAuth sessions and core control fields
5. Add add-account UI OAuth code auto-exchange flow

## Phase 2 (mandatory for practical controls)

1. Implement explicit account-group bindings and list filtering
2. Implement `weight`-aware selector behavior
3. Implement `window5h` cost guard
4. Implement `sessionCount` guard
5. Implement warmup intercept path

## Phase 3 (advanced transport controls)

1. TLS fingerprint mode runtime adapter
2. Session ID masquerade strategy and TTL cache
3. Diagnostics endpoint for advanced control status

---

## 11. Testing Strategy

## 11.1 Backend tests

1. OAuth start/exchange happy path
2. Invalid/expired/reused state rejection
3. CredentialRef consume-once behavior
4. Account create/update validation matrix by auth method
5. Selector behavior:
   - priority
   - concurrency
   - weight
   - temp disable
   - expiry
6. Guard behavior:
   - 5h cost throttling
   - session count throttling
7. Group binding filter correctness

## 11.2 Frontend tests

1. Add-account wizard step transitions
2. OAuth URL generation and code exchange flow
3. Validation errors by auth method
4. Account pool filter/sort/pagination rendering
5. Batch operations and row actions

---

## 12. Acceptance Criteria (Business Meaning)

1. Admin can create Anthropic OAuth account with "paste code -> auto exchange -> save" in under 90 seconds median.
2. No plain credential appears in API responses, logs, or browser storage.
3. Scheduling toggles (`tempDisabled`, `priority`, `concurrency`, `expiry`) immediately change dispatch behavior.
4. Weight affects account selection distribution within same priority tier.
5. 5h cost guard and session count guard can prevent overuse and produce clear status reasons.
6. Account pool page fields are sufficient for operators to diagnose why an account is or is not being scheduled.

---

## 13. Rollout And Backward Compatibility

1. Keep old create/update payload fields valid.
2. `credential` remains accepted until all clients migrate to `credentialRef`.
3. Introduce feature flags:
   - `firstapi.feature.account.oauthAutoExchange`
   - `firstapi.feature.account.advancedGuards`
   - `firstapi.feature.account.tlsProfile`
4. Rollout order:
   - Staging full validation
   - Canary in production for admin-only usage
   - Full rollout after 7-day error budget check

---

## 14. Open Decisions To Confirm Before Implementation

1. Anthropic OAuth token endpoint/client configuration source in each environment.
2. Whether group-aware routing is strict by default or opt-in per API key/group.
3. Whether advanced controls (`tlsFingerprintMode`, `sessionIdMasquerade`) launch in phase 2 or phase 3 in this cycle.
