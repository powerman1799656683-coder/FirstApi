# Console IA and Page Design (No Payment)

## Summary

This design defines the next-stage information architecture for `FirstApi` with one hard constraint:

- all payment capability is removed from product scope
- no payment page, route, API, callback, config, or design artifact remains active

The design keeps the current core business model and improves page utility and navigation consistency without a full rewrite.

## Scope and Constraints

### In Scope

- admin and user console information architecture
- page-level keep/refactor/remove decisions
- route and role-access normalization
- phased implementation order for low-risk delivery

### Out of Scope

- any payment feature, including hidden or disabled mode
- billing gateway integration, order management, callback handling
- promo/redemption feature reintroduction

### Hard Constraints

- `Users`, `Groups`, `Subscriptions` remain primary business pages
- all payment-related code stays removed/closed
- no dead link or commented route for removed pages

## IA Targets

### Admin Navigation

1. `Overview`
- `/dashboard`
- platform KPI, health summary, usage/cost trend

2. `Monitoring`
- `/monitor/system`
- `/monitor/accounts`
- system runtime, account pool health, token/usage anomalies

3. `Business`
- `/users`
- `/groups`
- `/subscriptions`

4. `Resources`
- `/accounts`
- `/ips`

5. `Content and Audit`
- `/announcements`
- `/records`

6. `System`
- `/settings`

### User Navigation

1. `API Usage`
- `/my-api-keys`
- `/my-records`

2. `Account`
- `/my-subscription`
- `/profile`

## Page Decisions

### Keep (as primary pages)

- `Dashboard`
- `MonitorSystem`
- `MonitorAccounts`
- `Users`
- `Groups`
- `Subscriptions`
- `Accounts`
- `IPs`
- `Announcements`
- `Records`
- `Settings`
- `MyApiKeys`
- `MyRecords`
- `MySubscription`
- `Profile`

### Refactor (structure retained, behavior improved)

1. `Dashboard`
- replace placeholder card logic with backend-driven KPI blocks
- prioritize operational metrics over demo visuals

2. `MonitorSystem` and `MonitorAccounts`
- enforce real backend-backed data shape
- remove fallback-only behavior for primary paths

3. `Settings`
- keep focused on system/auth/upstream settings only
- remove duplicated management behavior owned by other pages

4. `Users`
- keep manual balance adjust operation if needed for ops
- remove references to payment order history and payment workflow

### Remove or keep offline

- `MyPayment` page: removed
- `PaymentOrders` page: removed
- payment controllers/services/models/repositories: removed
- payment callback public-path behavior in auth filter: removed
- payment design spec: removed

## Route and Permission Baseline

### Public

- `/login`
- `/register`
- `/login-legacy` (migration only, remove later)
- `/register-legacy` (migration only, remove later)

### Authenticated User

- `/my-api-keys`
- `/my-records`
- `/my-subscription`
- `/profile`

### Admin Only

- `/dashboard`
- `/monitor/system`
- `/monitor/accounts`
- `/users`
- `/groups`
- `/subscriptions`
- `/accounts`
- `/announcements`
- `/ips`
- `/records`
- `/settings`

### Explicitly Not Routed

- `/my-payment`
- `/payment-orders`
- `/settings/payment`
- `/api/payment/*`
- `/api/user/payment/*`
- `/api/admin/payments*`

## API and Data Alignment

1. Monitoring pages must consume only supported backend endpoints.
2. Dashboard aggregations should align with relay/account/usage data already persisted.
3. Subscription renewal remains non-payment workflow.
4. User balance operations (if retained) remain admin-driven operational adjustments, not payment-driven accounting.

## Rollout Plan

### Phase 1: Navigation and Route Consistency

- ensure no removed-page entry exists in menu or routes
- ensure role guards are consistent with IA structure

### Phase 2: Page Utility Improvements

- dashboard metrics and chart model cleanup
- monitor page endpoint/data contract cleanup

### Phase 3: Settings and Ownership Cleanup

- remove duplicate responsibilities from settings
- keep single ownership per domain page

### Phase 4: UX and Stability Hardening

- loading/empty/error states normalized
- remove stale fallback text and dead action paths

## Verification Checklist

1. Search check returns no payment route/API references in source pages and backend controllers.
2. Unauthorized request to old payment callback path returns auth failure.
3. Frontend build passes.
4. Core admin and user routes render correctly.
5. No menu dead links exist.

## Risks and Mitigations

1. Risk: hidden payment references in comments/tests create confusion.
- Mitigation: enforce source scan in CI for payment keywords on active code paths.

2. Risk: monitor pages still rely on fallback shape.
- Mitigation: contract test per endpoint and strict UI schema guards.

3. Risk: overlapping responsibilities between settings and domain pages.
- Mitigation: define clear ownership map and reject duplicate actions in PR review.

## Acceptance Criteria

1. Product surface has zero payment entry points.
2. Admin IA and user IA are stable and internally consistent.
3. Existing key pages remain available and useful.
4. No payment-specific spec or implementation remains active in the repository.
