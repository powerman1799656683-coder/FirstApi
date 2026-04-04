# OpenAI Tools Chat Completions Design

## Summary

Add OpenAI `tools` compatibility to the existing `POST /v1/chat/completions` relay path for OpenAI models only.

- Preserve existing OpenAI non-streaming relay behavior
- Preserve existing OpenAI streaming relay behavior
- Accept and forward OpenAI tool-calling request fields
- Preserve tool-calling response payloads from upstream without rewriting them
- Keep Claude relay behavior unchanged

## Goals

- Support OpenAI chat completion requests that include `tools`
- Support OpenAI chat completion requests that include `tool_choice`
- Support OpenAI chat completion requests that include `parallel_tool_calls`
- Preserve assistant `tool_calls` messages and tool-result messages across follow-up turns
- Support both non-streaming and streaming OpenAI tool-calling responses
- Reject OpenAI tool-calling requests for Claude-routed models instead of silently dropping fields

## Non-Goals

- No Claude tool-calling protocol mapping in this change
- No broad support for every OpenAI chat completion field in this change
- No Responses API changes in this change
- No frontend or admin console changes in this change

## Why This Shape

The current relay already supports OpenAI and Claude chat completions in buffered and streaming modes. The blocking issue is narrower: OpenAI tool-calling fields are treated as unsupported request fields before the request ever reaches the upstream provider.

The smallest safe fix is:

- add explicit OpenAI tool fields at the top level
- preserve tool-related message fields on follow-up turns
- keep the OpenAI upstream body as a pass-through
- explicitly reject tool payloads on the Claude-compatible chat-completions path

This keeps the OpenAI path compatible with agent clients such as Harness without introducing partial Claude tool compatibility.

## Public API Behavior

### OpenAI models via `/v1/chat/completions`

Supported new top-level fields:

- `tools`
- `tool_choice`
- `parallel_tool_calls`

Supported message-level passthrough fields:

- assistant message `tool_calls`
- tool message `tool_call_id`
- other message-level fields needed to preserve follow-up OpenAI tool-call turns

Behavior:

- Non-streaming OpenAI requests forward these fields to upstream `/v1/chat/completions`
- Streaming OpenAI requests forward these fields to upstream `/v1/chat/completions`
- Non-streaming OpenAI responses are returned as-is, including `message.tool_calls`
- Streaming OpenAI SSE chunks are returned as-is, including `delta.tool_calls`

### Claude models via `/v1/chat/completions`

Behavior:

- Existing non-tool chat-completions compatibility remains unchanged
- If the request includes OpenAI tool-calling fields, return `400 Bad Request`
- Error message should be explicit that OpenAI tool calling is only supported for OpenAI-routed models in this change

## Backend Changes

### `RelayChatCompletionRequest`

Add explicit top-level fields for:

- `tools`
- `tool_choice`
- `parallel_tool_calls`

Extend `Message` to preserve unknown message-level fields instead of silently dropping them during OpenAI relay serialization.

### `RelayService`

Keep existing basic request validation for:

- `model`
- `messages`
- unsupported unknown top-level fields

Add provider-aware validation after model routing:

- allow OpenAI tool payloads for `openai`
- reject OpenAI tool payloads for `claude`

### `OpenAiRelayAdapter`

No protocol conversion should be added. The adapter should continue forwarding the serialized request body to OpenAI upstream.

Because the relay will now preserve tool-related request fields, upstream OpenAI responses can pass through unchanged.

## Testing

Add coverage for:

- OpenAI non-streaming request with `tools` and `tool_choice`
- OpenAI non-streaming response containing `message.tool_calls`
- OpenAI streaming response containing `delta.tool_calls`
- Follow-up OpenAI request that contains assistant `tool_calls` and tool `tool_call_id`
- Claude-routed request with OpenAI tool fields returning `400`
