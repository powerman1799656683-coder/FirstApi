# Unified LLM Relay Implementation Plan

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a unified OpenAI-compatible relay endpoint at `POST /v1/chat/completions` that authenticates platform API keys, routes by model to OpenAI or Claude, and records real relay usage.

**Architecture:** Add a dedicated relay controller outside the `/api/**` admin/user surface, keep the public protocol OpenAI-compatible, and isolate relay-specific auth, routing, upstream HTTP, and error formatting into focused backend services. Reuse `api_keys` for platform bearer tokens and `accounts` for encrypted upstream credentials, then persist relay outcomes into a new `relay_records` table.

**Tech Stack:** Spring Boot 2.7, Java 8, JdbcTemplate, Jackson, MockMvc, H2, MockWebServer

---

## Scope Decisions

- Public API stays OpenAI-compatible even when routing to Claude.
- First public endpoint is only `POST /v1/chat/completions`.
- `AuthFilter` does not need to gate relay traffic because it only enforces session auth under `/api/**`.
- Relay errors must not use the existing `ApiResponse<T>` wrapper; use a relay-specific error handler scoped to the relay controller.
- Start account selection with deterministic first healthy match. Add load balancing later.
- Add `base_url` to `accounts` now, with official provider URLs as defaults when the column is blank.
- Update `api_keys.last_used` when the relay request is authenticated and accepted for upstream dispatch.
- Keep frontend/admin UI changes out of the first implementation unless backend testing proves they are required.

## File Map

### Create

- `backend/src/main/java/com/firstapi/backend/controller/RelayController.java`
- `backend/src/main/java/com/firstapi/backend/controller/RelayExceptionHandler.java`
- `backend/src/main/java/com/firstapi/backend/model/RelayChatCompletionRequest.java`
- `backend/src/main/java/com/firstapi/backend/model/RelayErrorResponse.java`
- `backend/src/main/java/com/firstapi/backend/model/RelayException.java`
- `backend/src/main/java/com/firstapi/backend/model/RelayRecordItem.java`
- `backend/src/main/java/com/firstapi/backend/model/RelayRoute.java`
- `backend/src/main/java/com/firstapi/backend/model/RelayResult.java`
- `backend/src/main/java/com/firstapi/backend/repository/RelayRecordRepository.java`
- `backend/src/main/java/com/firstapi/backend/service/RelayApiKeyAuthService.java`
- `backend/src/main/java/com/firstapi/backend/service/RelayModelRouter.java`
- `backend/src/main/java/com/firstapi/backend/service/RelayRecordService.java`
- `backend/src/main/java/com/firstapi/backend/service/RelayService.java`
- `backend/src/main/java/com/firstapi/backend/service/OpenAiRelayAdapter.java`
- `backend/src/main/java/com/firstapi/backend/service/ClaudeRelayAdapter.java`
- `backend/src/main/java/com/firstapi/backend/service/UpstreamHttpClient.java`
- `backend/src/main/java/com/firstapi/backend/config/RelayProperties.java`
- `backend/src/test/java/com/firstapi/backend/repository/MyApiKeysRepositoryTest.java`
- `backend/src/test/java/com/firstapi/backend/repository/RelayRecordRepositoryTest.java`
- `backend/src/test/java/com/firstapi/backend/service/RelayApiKeyAuthServiceTest.java`
- `backend/src/test/java/com/firstapi/backend/service/RelayModelRouterTest.java`
- `backend/src/test/java/com/firstapi/backend/service/ClaudeRelayAdapterTest.java`
- `backend/src/test/java/com/firstapi/backend/controller/RelayControllerTest.java`
- `backend/src/test/resources/application-test.yml`

### Modify

- `backend/pom.xml`
- `backend/src/main/resources/application.yml`
- `backend/src/main/resources/schema.sql`
- `backend/src/main/java/com/firstapi/backend/model/AccountItem.java`
- `backend/src/main/java/com/firstapi/backend/repository/AccountRepository.java`
- `backend/src/main/java/com/firstapi/backend/repository/MyApiKeysRepository.java`
- `backend/src/main/java/com/firstapi/backend/service/AccountService.java`

### Leave Alone Unless Forced

- `backend/src/main/java/com/firstapi/backend/config/AuthFilter.java`
- `backend/src/main/java/com/firstapi/backend/controller/ApiExceptionHandler.java`
- `frontend/**`

## Chunk 1: Persistence And Relay Foundations

### Task 1: Add test infrastructure and failing persistence tests

**Files:**
- Modify: `backend/pom.xml`
- Create: `backend/src/test/resources/application-test.yml`
- Create: `backend/src/test/java/com/firstapi/backend/repository/MyApiKeysRepositoryTest.java`
- Create: `backend/src/test/java/com/firstapi/backend/repository/RelayRecordRepositoryTest.java`

- [ ] **Step 1: Write the failing tests**

```java
@SpringBootTest
@ActiveProfiles("test")
class MyApiKeysRepositoryTest {

    @Autowired
    private MyApiKeysRepository repository;

    @Autowired
    private SensitiveDataService sensitiveDataService;

    @Test
    void findsActiveKeyByPlainTextValue() {
        ApiKeyItem item = new ApiKeyItem();
        item.setOwnerId(7L);
        item.setName("relay");
        item.setKey(sensitiveDataService.protect("sk-firstapi-test"));
        item.setCreated("2026/03/16 12:00:00");
        item.setStatus("正常");
        item.setLastUsed("-");
        repository.save(item);

        ApiKeyItem resolved = repository.findByPlainTextKey("sk-firstapi-test");

        assertThat(resolved).isNotNull();
        assertThat(resolved.getOwnerId()).isEqualTo(7L);
    }
}
```

```java
@SpringBootTest
@ActiveProfiles("test")
class RelayRecordRepositoryTest {

    @Autowired
    private RelayRecordRepository repository;

    @Test
    void savesUsageRecord() {
        RelayRecordItem item = new RelayRecordItem();
        item.setOwnerId(7L);
        item.setApiKeyId(3L);
        item.setProvider("openai");
        item.setAccountId(2L);
        item.setModel("gpt-4o-mini");
        item.setRequestId("req_123");
        item.setSuccess(true);
        item.setStatusCode(200);
        item.setLatencyMs(512L);
        item.setTotalTokens(42);

        RelayRecordItem saved = repository.save(item);

        assertThat(saved.getId()).isNotNull();
        assertThat(repository.findAll()).hasSize(1);
    }
}
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `mvn "-Dtest=MyApiKeysRepositoryTest,RelayRecordRepositoryTest" test`  
Expected: compilation failure for missing `RelayRecordRepository` and missing `findByPlainTextKey`

- [ ] **Step 3: Add the minimal test support**

```yaml
spring:
  datasource:
    url: jdbc:h2:mem:firstapi;MODE=MySQL;DB_CLOSE_DELAY=-1
    driver-class-name: org.h2.Driver
    username: sa
    password:
  sql:
    init:
      mode: always
```

```xml
<dependency>
    <groupId>com.squareup.okhttp3</groupId>
    <artifactId>mockwebserver</artifactId>
    <version>4.12.0</version>
    <scope>test</scope>
</dependency>
```

- [ ] **Step 4: Run the tests again**

Run: `mvn "-Dtest=MyApiKeysRepositoryTest,RelayRecordRepositoryTest" test`  
Expected: tests still fail, but now only because repository and schema code is not implemented

- [ ] **Step 5: Commit**

```bash
git add backend/pom.xml backend/src/test/resources/application-test.yml backend/src/test/java/com/firstapi/backend/repository/MyApiKeysRepositoryTest.java backend/src/test/java/com/firstapi/backend/repository/RelayRecordRepositoryTest.java
git commit -m "test: add relay repository test scaffolding"
```

### Task 2: Implement schema, model, and repository support for relay persistence

**Files:**
- Modify: `backend/src/main/resources/schema.sql`
- Modify: `backend/src/main/java/com/firstapi/backend/model/AccountItem.java`
- Modify: `backend/src/main/java/com/firstapi/backend/repository/AccountRepository.java`
- Modify: `backend/src/main/java/com/firstapi/backend/repository/MyApiKeysRepository.java`
- Modify: `backend/src/main/java/com/firstapi/backend/service/AccountService.java`
- Create: `backend/src/main/java/com/firstapi/backend/model/RelayRecordItem.java`
- Create: `backend/src/main/java/com/firstapi/backend/repository/RelayRecordRepository.java`

- [ ] **Step 1: Implement the failing repository contract**

```java
public ApiKeyItem findByPlainTextKey(String plainTextKey) {
    List<ApiKeyItem> items = jdbcTemplate.query(
        "select `id`, `owner_id`, `name`, `api_key`, `created_label`, `status_name`, `last_used` from `api_keys` order by `id` desc",
        rowMapper
    );
    for (ApiKeyItem item : items) {
        if (plainTextKey.equals(sensitiveDataService.reveal(item.getKey()))) {
            return item;
        }
    }
    return null;
}
```

```sql
alter table `accounts` add column `base_url` varchar(512) null;

create table if not exists `relay_records` (
    `id` bigint not null auto_increment,
    `owner_id` bigint not null,
    `api_key_id` bigint not null,
    `provider_name` varchar(32) not null,
    `account_id` bigint not null,
    `model_name` varchar(255) not null,
    `request_id` varchar(128) null,
    `success` tinyint(1) not null,
    `status_code` int not null,
    `error_text` text null,
    `latency_ms` bigint not null,
    `prompt_tokens` int null,
    `completion_tokens` int null,
    `total_tokens` int null,
    `created_at` timestamp not null default current_timestamp,
    primary key (`id`)
) engine=InnoDB default charset=utf8mb4;
```

- [ ] **Step 2: Run the persistence tests**

Run: `mvn "-Dtest=MyApiKeysRepositoryTest,RelayRecordRepositoryTest" test`  
Expected: PASS

- [ ] **Step 3: Add `last_used` update support**

```java
public void touchLastUsed(Long id, Long ownerId, String lastUsed) {
    jdbcTemplate.update(
        "update `api_keys` set `last_used` = ? where `id` = ? and `owner_id` = ?",
        lastUsed,
        id,
        ownerId
    );
}
```

- [ ] **Step 4: Re-run the persistence tests**

Run: `mvn "-Dtest=MyApiKeysRepositoryTest,RelayRecordRepositoryTest" test`  
Expected: PASS with no schema or repository regressions

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/resources/schema.sql backend/src/main/java/com/firstapi/backend/model/AccountItem.java backend/src/main/java/com/firstapi/backend/repository/AccountRepository.java backend/src/main/java/com/firstapi/backend/repository/MyApiKeysRepository.java backend/src/main/java/com/firstapi/backend/service/AccountService.java backend/src/main/java/com/firstapi/backend/model/RelayRecordItem.java backend/src/main/java/com/firstapi/backend/repository/RelayRecordRepository.java
git commit -m "feat: add relay persistence primitives"
```

### Task 3: Add failing auth and model routing tests

**Files:**
- Create: `backend/src/main/java/com/firstapi/backend/model/RelayRoute.java`
- Create: `backend/src/main/java/com/firstapi/backend/service/RelayApiKeyAuthService.java`
- Create: `backend/src/main/java/com/firstapi/backend/service/RelayModelRouter.java`
- Create: `backend/src/test/java/com/firstapi/backend/service/RelayApiKeyAuthServiceTest.java`
- Create: `backend/src/test/java/com/firstapi/backend/service/RelayModelRouterTest.java`

- [ ] **Step 1: Write the failing service tests**

```java
@SpringBootTest
@ActiveProfiles("test")
class RelayApiKeyAuthServiceTest {

    @Autowired
    private RelayApiKeyAuthService service;

    @Autowired
    private MyApiKeysRepository repository;

    @Autowired
    private SensitiveDataService sensitiveDataService;

    @Test
    void rejectsDisabledKey() {
        ApiKeyItem item = new ApiKeyItem();
        item.setOwnerId(9L);
        item.setName("disabled");
        item.setKey(sensitiveDataService.protect("sk-firstapi-disabled"));
        item.setCreated("2026/03/16 12:10:00");
        item.setStatus("禁用");
        item.setLastUsed("-");
        repository.save(item);

        assertThatThrownBy(() -> service.authenticate("Bearer sk-firstapi-disabled"))
            .hasMessageContaining("API key");
    }
}
```

```java
@SpringBootTest
@ActiveProfiles("test")
class RelayModelRouterTest {

    @Autowired
    private RelayModelRouter router;

    @Test
    void routesClaudeModelsToClaudeProvider() {
        RelayRoute route = router.route("claude-3-5-sonnet");

        assertThat(route.getProvider()).isEqualTo("claude");
    }
}
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `mvn "-Dtest=RelayApiKeyAuthServiceTest,RelayModelRouterTest" test`  
Expected: compilation failure for missing relay auth and router classes

- [ ] **Step 3: Implement the minimal auth and routing services**

```java
public ApiKeyItem authenticate(String authorizationHeader) {
    String token = extractBearerToken(authorizationHeader);
    ApiKeyItem item = repository.findByPlainTextKey(token);
    if (item == null || !"正常".equals(normalizeStatus(item.getStatus()))) {
        throw new RelayException(HttpStatus.UNAUTHORIZED, "Invalid API key", "invalid_api_key");
    }
    repository.touchLastUsed(item.getId(), item.getOwnerId(), TimeSupport.nowDateTime());
    return item;
}
```

```java
public RelayRoute route(String model) {
    if (model != null && model.startsWith("claude-")) {
        return new RelayRoute("claude");
    }
    if (model != null && (model.startsWith("gpt-") || model.startsWith("o1") || model.startsWith("o3"))) {
        return new RelayRoute("openai");
    }
    throw new RelayException(HttpStatus.BAD_REQUEST, "Unsupported model", "unsupported_model");
}
```

- [ ] **Step 4: Run the service tests**

Run: `mvn "-Dtest=RelayApiKeyAuthServiceTest,RelayModelRouterTest" test`  
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/firstapi/backend/model/RelayRoute.java backend/src/main/java/com/firstapi/backend/service/RelayApiKeyAuthService.java backend/src/main/java/com/firstapi/backend/service/RelayModelRouter.java backend/src/test/java/com/firstapi/backend/service/RelayApiKeyAuthServiceTest.java backend/src/test/java/com/firstapi/backend/service/RelayModelRouterTest.java
git commit -m "feat: add relay auth and model routing"
```

## Chunk 2: OpenAI-Compatible Relay Endpoint

### Task 4: Add failing non-stream relay controller tests

**Files:**
- Create: `backend/src/main/java/com/firstapi/backend/model/RelayChatCompletionRequest.java`
- Create: `backend/src/main/java/com/firstapi/backend/model/RelayErrorResponse.java`
- Create: `backend/src/main/java/com/firstapi/backend/model/RelayException.java`
- Create: `backend/src/main/java/com/firstapi/backend/model/RelayResult.java`
- Create: `backend/src/main/java/com/firstapi/backend/controller/RelayController.java`
- Create: `backend/src/main/java/com/firstapi/backend/controller/RelayExceptionHandler.java`
- Create: `backend/src/main/java/com/firstapi/backend/service/RelayService.java`
- Create: `backend/src/main/java/com/firstapi/backend/service/OpenAiRelayAdapter.java`
- Create: `backend/src/main/java/com/firstapi/backend/service/UpstreamHttpClient.java`
- Create: `backend/src/main/java/com/firstapi/backend/service/RelayRecordService.java`
- Create: `backend/src/main/java/com/firstapi/backend/config/RelayProperties.java`
- Modify: `backend/src/main/resources/application.yml`
- Create: `backend/src/test/java/com/firstapi/backend/controller/RelayControllerTest.java`

- [ ] **Step 1: Write the failing controller tests**

```java
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class RelayControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void relaysOpenAiChatCompletions() throws Exception {
        mockMvc.perform(post("/v1/chat/completions")
                .header(HttpHeaders.AUTHORIZATION, "Bearer sk-firstapi-live")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"model\":\"gpt-4o-mini\",\"messages\":[{\"role\":\"user\",\"content\":\"hi\"}]}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.choices[0].message.content").value("hello"));
    }

    @Test
    void returnsOpenAiStyleErrorForBadKey() throws Exception {
        mockMvc.perform(post("/v1/chat/completions")
                .header(HttpHeaders.AUTHORIZATION, "Bearer sk-firstapi-bad")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"model\":\"gpt-4o-mini\",\"messages\":[{\"role\":\"user\",\"content\":\"hi\"}]}"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.error.code").value("invalid_api_key"));
    }
}
```

- [ ] **Step 2: Run the controller tests to verify they fail**

Run: `mvn "-Dtest=RelayControllerTest" test`  
Expected: compilation failure for missing relay controller, relay service, and relay error model

- [ ] **Step 3: Implement the minimal non-stream relay path**

```java
@RestController
public class RelayController {

    @PostMapping("/v1/chat/completions")
    public ResponseEntity<?> chatCompletions(
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
            @RequestBody RelayChatCompletionRequest request) {
        RelayResult result = relayService.relayChatCompletion(authorization, request);
        return ResponseEntity.status(result.getStatusCode())
            .contentType(MediaType.APPLICATION_JSON)
            .body(result.getJsonBody());
    }
}
```

```java
public RelayResult relayChatCompletion(String authorization, RelayChatCompletionRequest request) {
    ApiKeyItem apiKey = relayApiKeyAuthService.authenticate(authorization);
    RelayRoute route = modelRouter.route(request.getModel());
    if ("openai".equals(route.getProvider())) {
        RelayResult result = openAiRelayAdapter.relay(request, route);
        relayRecordService.record(apiKey, route, result, request.getModel());
        return result;
    }
    throw new RelayException(HttpStatus.BAD_REQUEST, "Unsupported model", "unsupported_model");
}
```

- [ ] **Step 4: Run the controller tests**

Run: `mvn "-Dtest=RelayControllerTest" test`  
Expected: PASS for bad-key handling and OpenAI non-stream relay

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/firstapi/backend/model/RelayChatCompletionRequest.java backend/src/main/java/com/firstapi/backend/model/RelayErrorResponse.java backend/src/main/java/com/firstapi/backend/model/RelayException.java backend/src/main/java/com/firstapi/backend/model/RelayResult.java backend/src/main/java/com/firstapi/backend/controller/RelayController.java backend/src/main/java/com/firstapi/backend/controller/RelayExceptionHandler.java backend/src/main/java/com/firstapi/backend/service/RelayService.java backend/src/main/java/com/firstapi/backend/service/OpenAiRelayAdapter.java backend/src/main/java/com/firstapi/backend/service/UpstreamHttpClient.java backend/src/main/java/com/firstapi/backend/service/RelayRecordService.java backend/src/main/java/com/firstapi/backend/config/RelayProperties.java backend/src/main/resources/application.yml backend/src/test/java/com/firstapi/backend/controller/RelayControllerTest.java
git commit -m "feat: add openai-compatible relay endpoint"
```

### Task 5: Add failing streaming tests and implement OpenAI stream passthrough

**Files:**
- Modify: `backend/src/main/java/com/firstapi/backend/controller/RelayController.java`
- Modify: `backend/src/main/java/com/firstapi/backend/service/RelayService.java`
- Modify: `backend/src/main/java/com/firstapi/backend/service/OpenAiRelayAdapter.java`
- Modify: `backend/src/main/java/com/firstapi/backend/service/UpstreamHttpClient.java`
- Modify: `backend/src/test/java/com/firstapi/backend/controller/RelayControllerTest.java`

- [ ] **Step 1: Extend the controller test with a failing stream case**

```java
@Test
void relaysOpenAiStreamChunks() throws Exception {
    MvcResult result = mockMvc.perform(post("/v1/chat/completions")
            .header(HttpHeaders.AUTHORIZATION, "Bearer sk-firstapi-live")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"model\":\"gpt-4o-mini\",\"stream\":true,\"messages\":[{\"role\":\"user\",\"content\":\"hi\"}]}"))
        .andExpect(status().isOk())
        .andExpect(header().string(HttpHeaders.CONTENT_TYPE, startsWith(MediaType.TEXT_EVENT_STREAM_VALUE)))
        .andReturn();

    String body = result.getResponse().getContentAsString();
    assertThat(body).contains("data: {\"id\":\"chatcmpl-stream\"");
    assertThat(body).contains("data: [DONE]");
}
```

- [ ] **Step 2: Run the controller test to verify the streaming case fails**

Run: `mvn "-Dtest=RelayControllerTest" test`  
Expected: FAIL because the endpoint still buffers or returns JSON instead of SSE

- [ ] **Step 3: Implement minimal streaming passthrough**

```java
if (Boolean.TRUE.equals(request.getStream())) {
    return ResponseEntity.ok()
        .contentType(MediaType.TEXT_EVENT_STREAM)
        .body((StreamingResponseBody) outputStream ->
            relayService.streamChatCompletion(authorization, request, outputStream));
}
```

```java
public void streamOpenAi(ObjectNode upstreamRequest, RelayRoute route, OutputStream outputStream) {
    upstreamHttpClient.stream(route.getBaseUrl() + "/v1/chat/completions", headers, upstreamRequest, inputStream -> {
        StreamUtils.copy(inputStream, outputStream);
        outputStream.flush();
    });
}
```

- [ ] **Step 4: Run the controller test**

Run: `mvn "-Dtest=RelayControllerTest" test`  
Expected: PASS with both non-stream and stream coverage

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/firstapi/backend/controller/RelayController.java backend/src/main/java/com/firstapi/backend/service/RelayService.java backend/src/main/java/com/firstapi/backend/service/OpenAiRelayAdapter.java backend/src/main/java/com/firstapi/backend/service/UpstreamHttpClient.java backend/src/test/java/com/firstapi/backend/controller/RelayControllerTest.java
git commit -m "feat: support streaming relay passthrough"
```

## Chunk 3: Claude Adaptation And End-To-End Verification

### Task 6: Add failing Claude adapter tests

**Files:**
- Create: `backend/src/main/java/com/firstapi/backend/service/ClaudeRelayAdapter.java`
- Modify: `backend/src/main/java/com/firstapi/backend/service/RelayService.java`
- Modify: `backend/src/main/java/com/firstapi/backend/service/RelayModelRouter.java`
- Create: `backend/src/test/java/com/firstapi/backend/service/ClaudeRelayAdapterTest.java`
- Modify: `backend/src/test/java/com/firstapi/backend/controller/RelayControllerTest.java`

- [ ] **Step 1: Write the failing Claude transformation tests**

```java
@SpringBootTest
@ActiveProfiles("test")
class ClaudeRelayAdapterTest {

    @Autowired
    private ClaudeRelayAdapter adapter;

    @Test
    void convertsOpenAiMessagesIntoClaudePayload() {
        RelayChatCompletionRequest request = RelayChatCompletionRequest.builder()
            .model("claude-3-5-sonnet")
            .addMessage("user", "hello")
            .maxTokens(128)
            .build();

        ObjectNode payload = adapter.toClaudeRequest(request);

        assertThat(payload.get("model").asText()).isEqualTo("claude-3-5-sonnet");
        assertThat(payload.get("messages").get(0).get("role").asText()).isEqualTo("user");
    }
}
```

```java
@Test
void relaysClaudeModelThroughOpenAiCompatibleEndpoint() throws Exception {
    mockMvc.perform(post("/v1/chat/completions")
            .header(HttpHeaders.AUTHORIZATION, "Bearer sk-firstapi-live")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"model\":\"claude-3-5-sonnet\",\"messages\":[{\"role\":\"user\",\"content\":\"hi\"}]}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.choices[0].message.content").value("hello from claude"));
}
```

- [ ] **Step 2: Run the Claude-focused tests to verify they fail**

Run: `mvn "-Dtest=ClaudeRelayAdapterTest,RelayControllerTest" test`  
Expected: compilation failure for missing Claude adapter or failing assertions on provider routing

- [ ] **Step 3: Implement minimal Claude adaptation**

```java
public ObjectNode toClaudeRequest(RelayChatCompletionRequest request) {
    ObjectNode root = objectMapper.createObjectNode();
    root.put("model", request.getModel());
    root.put("max_tokens", request.resolveMaxTokens());
    root.set("messages", mapMessages(request.getMessages()));
    return root;
}
```

```java
public ObjectNode toOpenAiResponse(JsonNode claudeResponse, String model) {
    ObjectNode root = objectMapper.createObjectNode();
    root.put("object", "chat.completion");
    root.put("model", model);
    root.set("choices", buildChoicesArray(claudeResponse));
    root.set("usage", buildUsageObject(claudeResponse));
    return root;
}
```

- [ ] **Step 4: Run the Claude-focused tests**

Run: `mvn "-Dtest=ClaudeRelayAdapterTest,RelayControllerTest" test`  
Expected: PASS for request translation, provider routing, and OpenAI-compatible Claude response

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/firstapi/backend/service/ClaudeRelayAdapter.java backend/src/main/java/com/firstapi/backend/service/RelayService.java backend/src/main/java/com/firstapi/backend/service/RelayModelRouter.java backend/src/test/java/com/firstapi/backend/service/ClaudeRelayAdapterTest.java backend/src/test/java/com/firstapi/backend/controller/RelayControllerTest.java
git commit -m "feat: add claude provider adaptation"
```

### Task 7: Run the backend suite and perform manual smoke verification

**Files:**
- Modify: `backend/DEPLOY.md`

- [ ] **Step 1: Add the relay smoke test instructions to docs**

```md
## Relay Smoke Test

1. Insert or create a platform API key in `api_keys`
2. Insert an encrypted OpenAI account in `accounts`
3. Run: `curl http://127.0.0.1:8080/v1/chat/completions -H "Authorization: Bearer sk-firstapi-local" -H "Content-Type: application/json" -d "{\"model\":\"gpt-4o-mini\",\"messages\":[{\"role\":\"user\",\"content\":\"hi\"}]}" `
```

- [ ] **Step 2: Run the full backend suite**

Run: `mvn test`  
Expected: PASS

- [ ] **Step 3: Run a local non-stream smoke test against the real OpenAI key**

Run:

```powershell
curl.exe http://127.0.0.1:8080/v1/chat/completions `
  -H "Authorization: Bearer sk-firstapi-local" `
  -H "Content-Type: application/json" `
  -d "{\"model\":\"gpt-4o-mini\",\"messages\":[{\"role\":\"user\",\"content\":\"Reply with ok\"}]}"
```

Expected: `200 OK` with an OpenAI-style `choices[0].message.content`

- [ ] **Step 4: Run a local stream smoke test**

Run:

```powershell
curl.exe -N http://127.0.0.1:8080/v1/chat/completions `
  -H "Authorization: Bearer sk-firstapi-local" `
  -H "Content-Type: application/json" `
  -d "{\"model\":\"gpt-4o-mini\",\"stream\":true,\"messages\":[{\"role\":\"user\",\"content\":\"Count to three\"}]}"
```

Expected: multiple `data:` lines followed by `data: [DONE]`

- [ ] **Step 5: Commit**

```bash
git add backend/DEPLOY.md
git commit -m "docs: add relay smoke test instructions"
```

## Manual Notes For The Implementer

- Use a fresh upstream OpenAI key during live verification. Do not reuse the one that was exposed in chat.
- Seed or create the platform key separately from the upstream account key; they are different credentials.
- If `MockWebServer` proves awkward for SSE assertions, keep it for buffered JSON tests and use a minimal local `HttpServer` fixture for the stream case.
- Keep relay code focused on text chat only. Reject unsupported fields explicitly instead of partially ignoring them.
