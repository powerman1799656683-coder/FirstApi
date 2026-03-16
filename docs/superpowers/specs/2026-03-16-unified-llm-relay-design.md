# Unified LLM Relay Design

## Summary

Build a unified AI relay layer for the existing Spring Boot backend.

- External protocol: OpenAI-compatible
- Initial public endpoint: `POST /v1/chat/completions`
- External authentication: platform-issued API keys from `api_keys`
- Internal routing: choose upstream provider by `model`
- Initial upstream providers: OpenAI and Anthropic Claude

This keeps the client experience simple: one base URL, one platform API key, one common SDK style.

## Goals

- Expose a single OpenAI-compatible chat completion endpoint
- Allow clients to call GPT and Claude models through the same external API
- Reuse the current `accounts` table as the upstream credential pool
- Reuse the current `api_keys` table as the client-facing platform key store
- Record real relay requests for audit, usage, and troubleshooting
- Support both non-streaming and streaming chat responses

## Non-Goals

- No provider-specific public APIs in the first version
- No tool calling, function calling, image generation, embeddings, file upload, or multimodal support in the first version
- No advanced routing rules such as weighted load balancing, retry, or automatic failover in the first version
- No model alias table in the first version; provider selection is rule-based from the requested `model`

## Why This Shape

The common market pattern is:

- one domain
- one platform API key
- one OpenAI-compatible public interface

Internally, providers are still different. The relay absorbs those protocol differences so clients do not need provider-specific SDK logic.

The relay must not change response format based on the client API key. Authentication and response protocol are separate concerns. The public protocol is defined by the route, not by the key.

## Public API

### Endpoint

- `POST /v1/chat/completions`

### External Authentication

- Clients send `Authorization: Bearer sk-firstapi-...`
- The platform validates the key against `api_keys`
- Invalid or disabled keys return `401 Unauthorized`

### Request Contract

The relay accepts an OpenAI-style chat completion payload.

Supported fields in the first version:

- `model`
- `messages`
- `stream`
- `temperature`
- `max_tokens`
- `max_completion_tokens`
- `top_p`
- `presence_penalty`
- `frequency_penalty`
- `stop`
- `user`

Unsupported fields return `400 Bad Request`.

### Response Contract

- For OpenAI upstream models: pass through the OpenAI-compatible response
- For Claude upstream models: adapt the Claude response into OpenAI-compatible response shape
- Streaming responses use OpenAI-style SSE chunks

## Model Routing

Routing is based on the requested `model`.

Initial rules:

- `gpt-*`, `o1*`, `o3*`, `text-embedding-*` family names map to `openai`
- `claude-*` maps to `claude`
- unknown models return `400 Bad Request`

The selected provider then determines which upstream account is eligible.

## Data Model

### Reused Table: `api_keys`

Purpose:

- store platform-issued client API keys
- resolve the calling user
- verify status before a relay request is sent
- update `last_used` when a request is accepted for relay

No protocol decision is derived from this table.

### Reused Table: `accounts`

Purpose:

- store upstream provider credentials
- serve as the upstream account pool for relay requests

Required conventions:

- `platform` must be normalized to `openai` or `claude`
- `credential` stores the encrypted upstream API key
- `status_name` indicates whether the upstream account is eligible

Recommended schema addition:

- `base_url` so the relay can support official endpoints or compatible gateways without code changes

Optional future addition:

- `model_json` for explicit model allowlists per upstream account

### New Table: `relay_records`

Add a real request log table to replace the current demo-only records data.

Recommended columns:

- `id`
- `owner_id`
- `api_key_id`
- `provider_name`
- `account_id`
- `model_name`
- `request_id`
- `success`
- `status_code`
- `error_text`
- `latency_ms`
- `prompt_tokens`
- `completion_tokens`
- `total_tokens`
- `created_at`

This table supports usage reporting, failure diagnosis, and later quota logic.

## Backend Components

### `RelayController`

Responsibilities:

- expose `POST /v1/chat/completions`
- parse the OpenAI-compatible request
- delegate to relay orchestration service

### `RelayApiKeyAuthService`

Responsibilities:

- extract bearer token
- look up the platform API key
- verify key status
- resolve the owning user

This is separate from the existing admin/user cookie session system.

### `ModelRouter`

Responsibilities:

- map requested `model` to provider
- reject unknown model names
- choose an eligible upstream account for that provider

First version account selection can be simple first-match or round-robin across healthy accounts.

### `OpenAiAdapter`

Responsibilities:

- forward OpenAI-compatible requests to OpenAI upstream
- set upstream `Authorization: Bearer ...`
- stream or buffer response based on request mode

### `ClaudeAdapter`

Responsibilities:

- translate OpenAI-style chat request into Anthropic `messages` request
- set `x-api-key` and `anthropic-version`
- translate Anthropic response back into OpenAI-compatible response shape
- translate Anthropic streaming events into OpenAI-style SSE chunks

### `UpstreamHttpClient`

Responsibilities:

- send outbound HTTP requests
- support buffered and streaming modes
- preserve upstream status codes and error bodies where possible
- enforce timeout and connection settings

### `RelayRecordService`

Responsibilities:

- persist relay request outcome
- capture latency, tokens, provider, account, and errors

The record write can be synchronous in the first version and made asynchronous later if needed.

## Request Flow

1. Client sends `POST /v1/chat/completions` with platform bearer token.
2. Relay authenticates the platform API key.
3. Relay reads `model` and resolves provider.
4. Relay selects an eligible upstream account for that provider.
5. Relay sends the request upstream through the provider adapter.
6. Relay returns the upstream result in OpenAI-compatible form.
7. Relay writes a `relay_records` entry with success, status, timing, and usage.

## Streaming Behavior

### Public Behavior

- Client sends `stream: true`
- Relay responds as an SSE stream

### OpenAI Upstream

- Relay forwards stream chunks with minimal transformation

### Claude Upstream

- Relay parses Anthropic SSE events
- Relay emits OpenAI-style delta chunks
- Relay emits an OpenAI-style terminal chunk and `[DONE]`

This preserves a single client streaming contract.

## Error Handling

Return policy:

- invalid or missing platform API key: `401`
- unsupported model or unsupported request fields: `400`
- no eligible upstream account: `503`
- upstream provider error: preserve upstream status code when practical
- unexpected internal failure: `500`

Logging policy:

- save upstream error payload summary into `relay_records.error_text`
- keep full sensitive upstream credentials out of logs

## Security

- Platform API keys and upstream provider keys are different assets and must stay separate
- Upstream keys must never be exposed to clients
- Upstream keys must remain encrypted at rest using the existing sensitive data support
- Real upstream keys must not be committed to git or shared in chat

## Testing Strategy

First validation targets:

1. Invalid platform key is blocked before any upstream request
2. OpenAI non-streaming chat request relays successfully
3. OpenAI streaming chat request relays successfully
4. Claude request routing and transformation is covered by focused tests and can be live-verified once a valid Claude key is available

Manual verification should include:

- successful GPT request through the unified endpoint
- streaming GPT request through the unified endpoint
- model-based routing rejection for an unsupported model
- disabled platform key rejection

## Implementation Sequence

1. Add relay data structures and schema changes
2. Implement platform API key lookup for bearer auth
3. Add unified relay controller and orchestration service
4. Implement OpenAI adapter end-to-end first
5. Add relay request logging
6. Implement Claude adapter and OpenAI-compatible response conversion
7. Add `GET /v1/models` later as a follow-up endpoint

## Open Questions

- Whether account selection should start as simple first-match or round-robin
- Whether `last_used` should update before upstream dispatch or only after request completion
- Whether `base_url` should be added immediately or supplied via config for the first OpenAI-only milestone
