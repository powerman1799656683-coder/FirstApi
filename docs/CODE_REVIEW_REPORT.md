# FirstAPI Code Review & Optimization Report

> **Date**: 2026-04-01
> **Stack**: Java 25 + Spring Boot 4.0.3 + MySQL | React 19 + Vite 8

---

## 1. Current Architecture Issues

### 1.1 Backend Core Issues

| # | Severity | Component | Issue |
|---|----------|-----------|-------|
| 1 | CRITICAL | UpstreamHttpClient | No connection pooling - every request new TCP/SSL handshake |
| 2 | CRITICAL | RelayRecordService | Billing deducted BEFORE record persisted - crash = money lost |
| 3 | CRITICAL | RelayService | Streaming response OutputStream not closed on error path |
| 4 | HIGH | RateLimitFilter | In-memory rate limiting - lost on restart, no multi-instance support |
| 5 | HIGH | DailyQuotaService | Group-level quota check ignores group_id parameter |
| 6 | HIGH | AccountService | findAll() + in-memory filter = full table scan on every list request |
| 7 | HIGH | Database Schema | No foreign keys, missing indexes on commonly filtered columns |
| 8 | MEDIUM | RelayService | 3 near-identical relay methods (~30% code duplication) |
| 9 | MEDIUM | CostCalculationService | volatile List not atomic - partial reads possible |
| 10 | MEDIUM | SensitiveDataService | SHA-256 directly as AES key, no PBKDF2/Argon2 |

### 1.2 Frontend Core Issues

| # | Severity | Component | Issue |
|---|----------|-----------|-------|
| 11 | HIGH | 11 pages | Error handling via alert() - blocking, poor UX |
| 12 | HIGH | Groups/Subscriptions | Missing AbortController cleanup - memory leaks |
| 13 | MEDIUM | All admin pages | Client-side pagination - won't scale past 1000 records |
| 14 | MEDIUM | App.jsx | ErrorBoundary exists but not used on any routes |
| 15 | MEDIUM | 5 pages | Artificial 300ms setTimeout delay on loading states |
| 16 | LOW | App.jsx | No code splitting - all 19 pages loaded at once |

---

## 2. Backend Optimization Suggestions

### 2.1 CRITICAL: Replace HttpURLConnection with Connection-Pooled HttpClient

**Current problem**: `UpstreamHttpClient.java` creates a new connection per request.
Every request pays DNS + TCP + TLS overhead (100-500ms), and under high concurrency
risks port exhaustion.

```java
// BEFORE: UpstreamHttpClient.java - new connection every time
HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
conn.setConnectTimeout(connectTimeout);
conn.setReadTimeout(readTimeout);

// AFTER: Use java.net.http.HttpClient with connection pool
@Service
public class UpstreamHttpClient {

    private final HttpClient httpClient;

    public UpstreamHttpClient(RelayProperties props) {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(props.getConnectTimeoutMs()))
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
    }

    public UpstreamResponse postJson(String url, String body,
                                      Map<String, String> headers,
                                      Duration readTimeout) {
        var builder = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(readTimeout)
                .POST(HttpRequest.BodyPublishers.ofString(body));
        headers.forEach(builder::header);

        HttpResponse<InputStream> resp = httpClient.send(
                builder.build(),
                HttpResponse.BodyHandlers.ofInputStream());

        return new UpstreamResponse(resp.statusCode(), resp.body(), resp.headers());
    }

    // Streaming variant
    public UpstreamResponse postJsonStreaming(String url, String body,
                                              Map<String, String> headers,
                                              Duration readTimeout) {
        var builder = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(readTimeout)
                .POST(HttpRequest.BodyPublishers.ofString(body));
        headers.forEach(builder::header);

        HttpResponse<InputStream> resp = httpClient.send(
                builder.build(),
                HttpResponse.BodyHandlers.ofInputStream());

        return new UpstreamResponse(resp.statusCode(), resp.body(), resp.headers());
    }
}
```

### 2.2 CRITICAL: Fix Billing Atomicity

**Current problem**: RelayRecordService deducts balance, then saves record.
If save() fails, money is lost with no audit trail.

```java
// BEFORE: Deduct first, log second (DANGEROUS)
costCalculationService.deductBalance(userId, cost);
relayRecordRepository.save(record);  // If this fails, money gone

// AFTER: Atomic billing with @Transactional
@Transactional(isolation = Isolation.READ_COMMITTED)
public void recordAndBill(RelayRecord record, BigDecimal cost) {
    // 1. Persist audit trail FIRST
    relayRecordRepository.save(record);
    // 2. Deduct balance SECOND (rollback if step 1 failed)
    costCalculationService.deductBalance(record.getOwnerId(), cost);
}
```

### 2.3 HIGH: Fix Streaming Resource Cleanup

```java
// BEFORE: OutputStream leak on error
public void relayChatCompletionStreaming(HttpServletRequest req,
                                         HttpServletResponse resp) {
    OutputStream out = resp.getOutputStream();
    // ... if exception occurs here, out never flushed/closed
    upstreamClient.streamTo(out);
}

// AFTER: try-finally guarantees cleanup
public void relayChatCompletionStreaming(HttpServletRequest req,
                                         HttpServletResponse resp) {
    resp.setContentType("text/event-stream");
    resp.setCharacterEncoding("UTF-8");
    OutputStream out = resp.getOutputStream();
    try {
        upstreamClient.streamTo(out);
    } finally {
        try { out.flush(); } catch (IOException ignored) {}
        try { out.close(); } catch (IOException ignored) {}
    }
}
```

### 2.4 HIGH: Extract Common Relay Pattern (Reduce Duplication)

```java
// BEFORE: 3 near-identical methods in RelayService.java
// relayResponsesApi(), relayChatCompletion(), relayClaudeMessages()
// each ~80 lines with ~30% duplication

// AFTER: Template method pattern
public RelayResult executeRelay(HttpServletRequest req,
                                 RelayContext ctx) {
    // 1. Auth
    ApiKeyAuth auth = relayApiKeyAuthService.authenticate(req);

    // 2. Route
    RelayRoute route = relayModelRouter.resolve(ctx.getModel(), auth);

    // 3. Select account (with retry)
    int maxRetries = 3;
    for (int attempt = 0; attempt < maxRetries; attempt++) {
        AccountItem account = relayAccountSelector.select(route);
        if (account == null) break;

        try {
            // 4. Adapt request per provider
            RelayAdapter adapter = adapterRegistry.get(account.getPlatform());
            UpstreamRequest upstream = adapter.buildRequest(ctx, account);

            // 5. Execute
            UpstreamResponse response = upstreamClient.execute(upstream);

            // 6. Record
            relayRecordService.record(auth, account, ctx, response);

            return adapter.buildResult(response);
        } catch (RelayRetryableException e) {
            syncQuotaRuntimeState(account, e);
            continue;  // Try next account
        }
    }
    throw new RelayException(503, "No available accounts");
}
```

### 2.5 MEDIUM: Thread-Safe Pricing Cache

```java
// BEFORE: volatile List - partial reads possible
private volatile List<ModelPricingItem> cachedPricing;

// AFTER: AtomicReference for safe publication
private final AtomicReference<List<ModelPricingItem>> cachedPricing =
        new AtomicReference<>(List.of());

@Scheduled(fixedDelay = 60_000)  // Reduced from 5min to 1min
public void refreshPricingCache() {
    List<ModelPricingItem> fresh = modelPricingRepository.findAll();
    cachedPricing.set(List.copyOf(fresh));  // Immutable snapshot
}

public List<ModelPricingItem> getPricing() {
    return cachedPricing.get();  // Always consistent snapshot
}
```

---

## 3. Frontend Optimization Suggestions

### 3.1 HIGH: Replace All alert() With Toast Notifications

```jsx
// BEFORE: 11 instances across pages - blocking UX
.catch(err => alert(err.message || 'Load failed'))

// AFTER: Non-blocking toast
.catch(err => setToast({ message: err.message || 'Load failed', type: 'error' }))
```

Files to fix: Users.jsx, Groups.jsx, Subscriptions.jsx, Records.jsx,
MyRecords.jsx, Accounts.jsx, Profile.jsx

### 3.2 HIGH: Add Error Boundaries to Routes

```jsx
// BEFORE: App.jsx - no error isolation
<Route path="users" element={<Users />} />

// AFTER: Wrap each route with ErrorBoundary
import ErrorBoundary from './components/ErrorBoundary';

function PageWrapper({ children }) {
    return <ErrorBoundary>{children}</ErrorBoundary>;
}

<Route path="users" element={<PageWrapper><Users /></PageWrapper>} />
```

### 3.3 HIGH: Fix Memory Leaks - Add AbortController

```jsx
// BEFORE: Groups.jsx - no request cancellation
const loadData = () => {
    setIsLoading(true);
    api.get('/admin/groups')
        .then(res => setGroups(res.items))
        .catch(err => alert(err.message))
        .finally(() => setTimeout(() => setIsLoading(false), 300));
};

// AFTER: Proper cleanup
const abortRef = useRef(null);

const loadData = useCallback(() => {
    abortRef.current?.abort();
    abortRef.current = new AbortController();

    setIsLoading(true);
    api.get('/admin/groups', { signal: abortRef.current.signal })
        .then(res => setGroups(res.items))
        .catch(err => {
            if (err.name !== 'AbortError') {
                setToast({ message: err.message, type: 'error' });
            }
        })
        .finally(() => setIsLoading(false));  // No artificial delay
}, []);

useEffect(() => {
    loadData();
    return () => abortRef.current?.abort();
}, []);
```

### 3.4 MEDIUM: Extract Reusable useFormModal Hook

```jsx
// Duplicated in Users.jsx, Groups.jsx, Subscriptions.jsx, Accounts.jsx

// AFTER: src/hooks/useFormModal.js
export function useFormModal(initialData = {}) {
    const [isOpen, setIsOpen] = useState(false);
    const [editingItem, setEditingItem] = useState(null);
    const [formData, setFormData] = useState(initialData);
    const [formError, setFormError] = useState('');

    const open = useCallback((item = null) => {
        setEditingItem(item);
        setFormData(item ? { ...item } : { ...initialData });
        setFormError('');
        setIsOpen(true);
    }, [initialData]);

    const close = useCallback(() => {
        setIsOpen(false);
        setEditingItem(null);
        setFormData({ ...initialData });
        setFormError('');
    }, [initialData]);

    return { isOpen, editingItem, formData, formError,
             setFormData, setFormError, open, close };
}
```

### 3.5 MEDIUM: Code Splitting with React.lazy

```jsx
// BEFORE: App.jsx - all 19 pages loaded upfront
import Users from './pages/Users';
import Groups from './pages/Groups';
// ... 17 more static imports

// AFTER: Lazy loading per route
import { lazy, Suspense } from 'react';
import LoadingSpinner from './components/LoadingSpinner';

const Users = lazy(() => import('./pages/Users'));
const Groups = lazy(() => import('./pages/Groups'));
const Accounts = lazy(() => import('./pages/Accounts'));
// ... etc

function LazyRoute({ component: Component }) {
    return (
        <Suspense fallback={<LoadingSpinner />}>
            <ErrorBoundary>
                <Component />
            </ErrorBoundary>
        </Suspense>
    );
}

// In routes:
<Route path="users" element={<LazyRoute component={Users} />} />
```

### 3.6 LOW: Extract Reusable SortableTable Component

```jsx
// src/components/SortableTable.jsx
function SortableTable({ columns, data, sortConfig, onSort, renderRow }) {
    const SortIcon = ({ column }) => {
        if (sortConfig.key !== column) return null;
        return sortConfig.direction === 'asc'
            ? <ChevronDown size={12} style={{ transform: 'rotate(180deg)' }} />
            : <ChevronDown size={12} />;
    };

    return (
        <table className="data-table">
            <thead>
                <tr>
                    {columns.map(col => (
                        <th key={col.key}
                            onClick={col.sortable ? () => onSort(col.key) : undefined}
                            style={col.sortable ? { cursor: 'pointer' } : {}}>
                            {col.label} {col.sortable && <SortIcon column={col.key} />}
                        </th>
                    ))}
                </tr>
            </thead>
            <tbody>
                {data.length === 0
                    ? <EmptyState colSpan={columns.length} />
                    : data.map(renderRow)}
            </tbody>
        </table>
    );
}
```

---

## 4. Database & Performance Optimization

### 4.1 CRITICAL: Add Missing Indexes

```sql
-- relay_records: Most queried table, needs composite indexes
CREATE INDEX idx_relay_records_owner_time
    ON relay_records(owner_id, created_at DESC);
CREATE INDEX idx_relay_records_account_time
    ON relay_records(account_id, created_at DESC);
CREATE INDEX idx_relay_records_api_key
    ON relay_records(api_key_id);

-- accounts: Filtered by platform/status on every relay request
CREATE INDEX idx_accounts_platform_status
    ON accounts(platform, status_name, temp_disabled, quota_exhausted);

-- api_keys: Looked up on every relay request
CREATE INDEX idx_api_keys_owner
    ON api_keys(owner_id);

-- subscriptions: Filtered frequently
CREATE INDEX idx_subscriptions_uid
    ON subscriptions(uid_value);
CREATE INDEX idx_subscriptions_status
    ON subscriptions(status_name);

-- daily_quota_usage: Queried per request for quota check
CREATE INDEX idx_daily_quota_owner_date
    ON daily_quota_usage(owner_id, usage_date);
```

### 4.2 HIGH: Add Foreign Key Constraints

```sql
ALTER TABLE api_keys
    ADD CONSTRAINT fk_api_keys_owner
    FOREIGN KEY (owner_id) REFERENCES auth_users(id) ON DELETE CASCADE;

ALTER TABLE subscriptions
    ADD CONSTRAINT fk_subscriptions_group
    FOREIGN KEY (group_id) REFERENCES `groups`(id) ON DELETE SET NULL;

ALTER TABLE account_group_bindings
    ADD CONSTRAINT fk_agb_account
    FOREIGN KEY (account_id) REFERENCES accounts(id) ON DELETE CASCADE,
    ADD CONSTRAINT fk_agb_group
    FOREIGN KEY (group_id) REFERENCES `groups`(id) ON DELETE CASCADE;
```

### 4.3 HIGH: Async Relay Record Logging

Relay record persistence is on the critical request path.
Move to async to reduce response latency.

```java
@Service
public class RelayRecordService {

    private final ExecutorService recordExecutor =
            Executors.newFixedThreadPool(4, Thread.ofVirtual().factory());

    public void recordAsync(RelayRecord record) {
        recordExecutor.submit(() -> {
            try {
                relayRecordRepository.save(record);
            } catch (Exception e) {
                log.error("Failed to save relay record: {}", record.getId(), e);
                // Fallback: write to local file for recovery
            }
        });
    }
}
```

### 4.4 MEDIUM: Server-Side Pagination for AccountService

```java
// BEFORE: Load all accounts into memory, filter in Java
public AccountListResponse listAccounts(String keyword, String platform, ...) {
    List<AccountItem> all = repository.findAll();  // ALL records!
    // ... filter in memory
}

// AFTER: Database-level filtering and pagination
public AccountListResponse listAccounts(AccountQuery query) {
    String sql = """
        SELECT a.*, COUNT(*) OVER() AS total_count
        FROM accounts a
        WHERE 1=1
        """;
    var params = new MapSqlParameterSource();

    if (query.keyword() != null) {
        sql += " AND (a.name LIKE :kw OR a.notes LIKE :kw)";
        params.addValue("kw", "%" + query.keyword() + "%");
    }
    if (query.platform() != null) {
        sql += " AND a.platform = :platform";
        params.addValue("platform", query.platform());
    }

    sql += " ORDER BY " + query.sortColumn() + " " + query.sortDirection();
    sql += " LIMIT :limit OFFSET :offset";
    params.addValue("limit", query.pageSize());
    params.addValue("offset", query.page() * query.pageSize());

    // Single query: filtered, sorted, paginated, with total count
    return jdbcTemplate.query(sql, params, resultSetExtractor);
}
```

---

## 5. Security Optimization

### 5.1 HIGH: Strengthen Key Derivation

```java
// BEFORE: SHA-256 directly as AES key (fast = bad for secrets)
byte[] keyBytes = MessageDigest.getInstance("SHA-256")
        .digest(secret.getBytes());

// AFTER: PBKDF2 with proper iterations
private SecretKey deriveKey(String secret) {
    SecretKeyFactory factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
    KeySpec spec = new PBEKeySpec(
            secret.toCharArray(),
            FIXED_SALT,     // Unique per deployment
            310_000,        // OWASP recommended iterations
            256);
    byte[] keyBytes = factory.generateSecret(spec).getEncoded();
    return new SecretKeySpec(keyBytes, "AES");
}
```

### 5.2 HIGH: Fix Rate Limit IP Spoofing

```java
// BEFORE: Trusts X-Forwarded-For directly
private String getClientIp(HttpServletRequest req) {
    String xff = req.getHeader("X-Forwarded-For");
    if (xff != null) return xff.split(",")[0].trim();  // Spoofable!
    return req.getRemoteAddr();
}

// AFTER: Only trust known proxy headers, validate chain
private String getClientIp(HttpServletRequest req) {
    // If behind known reverse proxy (Nginx/CF), trust rightmost
    String xff = req.getHeader("X-Forwarded-For");
    if (xff != null) {
        String[] parts = xff.split(",");
        // Last entry added by YOUR nginx = real client IP
        return parts[parts.length - 1].trim();
    }
    return req.getRemoteAddr();
}
```

### 5.3 MEDIUM: Add CSRF Protection

```java
// Add to AuthFilter or as new filter
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 2)
public class CsrfFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest req,
                                     HttpServletResponse resp,
                                     FilterChain chain) {
        if (isStateChanging(req.getMethod())) {
            String origin = req.getHeader("Origin");
            String referer = req.getHeader("Referer");
            String host = req.getHeader("Host");

            if (origin != null && !origin.contains(host)) {
                resp.setStatus(403);
                return;
            }
        }
        chain.doFilter(req, resp);
    }

    private boolean isStateChanging(String method) {
        return "POST".equals(method) || "PUT".equals(method)
            || "DELETE".equals(method) || "PATCH".equals(method);
    }
}
```

### 5.4 MEDIUM: Prevent Credential Logging

```java
// Ensure OAuth tokens never appear in logs
// Add to relay logging:
private String sanitizeForLog(String body) {
    if (body == null) return null;
    return body.replaceAll(
        "(access_token|refresh_token|api_key|authorization)[\":]\\s*[\"']?[\\w\\-./=+]{8,}",
        "$1: [REDACTED]");
}
```

---

## 6. Priority Execution Order

### Phase 1 - Critical (This Week)

| # | Task | Risk | Effort |
|---|------|------|--------|
| 1 | Fix billing atomicity (add @Transactional) | Financial loss | 1h |
| 2 | Fix streaming resource cleanup (try-finally) | Service crash | 1h |
| 3 | Add database indexes | Performance | 30min |
| 4 | Replace alert() with Toast (frontend) | UX | 2h |

### Phase 2 - High (Next Sprint)

| # | Task | Risk | Effort |
|---|------|------|--------|
| 5 | Replace HttpURLConnection with HttpClient | Performance | 4h |
| 6 | Fix DailyQuotaService group_id bug | Billing bypass | 1h |
| 7 | Add AbortController cleanup (frontend) | Memory leak | 2h |
| 8 | Add ErrorBoundary to routes | Stability | 1h |
| 9 | Add foreign key constraints | Data integrity | 1h |

### Phase 3 - Medium (Planned)

| # | Task | Risk | Effort |
|---|------|------|--------|
| 10 | Server-side pagination for admin pages | Scalability | 8h |
| 11 | Extract common relay pattern | Maintainability | 4h |
| 12 | AtomicReference for pricing cache | Thread safety | 1h |
| 13 | PBKDF2 key derivation | Security | 2h |
| 14 | CSRF protection | Security | 2h |
| 15 | React.lazy code splitting | Bundle size | 2h |
| 16 | Extract useFormModal hook | Code quality | 2h |
