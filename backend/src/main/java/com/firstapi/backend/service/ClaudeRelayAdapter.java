package com.firstapi.backend.service;

import com.firstapi.backend.config.RelayProperties;
import com.firstapi.backend.model.AccountItem;
import com.firstapi.backend.model.IpItem;
import com.firstapi.backend.model.RelayChatCompletionRequest;
import com.firstapi.backend.model.RelayException;
import com.firstapi.backend.model.RelayResult;
import com.firstapi.backend.model.RelayRoute;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class ClaudeRelayAdapter {

    private final ObjectMapper objectMapper;
    private final RelayProperties relayProperties;
    private final RelayAccountSelector relayAccountSelector;
    private final SensitiveDataService sensitiveDataService;
    private final UpstreamHttpClient upstreamHttpClient;

    public ClaudeRelayAdapter(ObjectMapper objectMapper, RelayProperties relayProperties) {
        this(objectMapper, relayProperties, null, null, null);
    }

    @Autowired
    public ClaudeRelayAdapter(ObjectMapper objectMapper,
                              RelayProperties relayProperties,
                              RelayAccountSelector relayAccountSelector,
                              SensitiveDataService sensitiveDataService,
                              UpstreamHttpClient upstreamHttpClient) {
        this.objectMapper = objectMapper;
        this.relayProperties = relayProperties;
        this.relayAccountSelector = relayAccountSelector;
        this.sensitiveDataService = sensitiveDataService;
        this.upstreamHttpClient = upstreamHttpClient;
    }

    public ObjectNode toClaudeRequest(RelayChatCompletionRequest request) {
        ObjectNode root = objectMapper.createObjectNode();
        root.put("model", request.getModel());
        root.put("max_tokens", request.resolveMaxTokens());
        if (Boolean.TRUE.equals(request.getStream())) {
            root.put("stream", true);
        }
        if (request.getTemperature() != null) {
            root.put("temperature", request.getTemperature());
        }
        if (request.getTopP() != null) {
            root.put("top_p", request.getTopP());
        }
        ArrayNode stopSequences = toStopSequences(request.getStop());
        if (stopSequences != null && stopSequences.size() > 0) {
            root.set("stop_sequences", stopSequences);
        }
        ArrayNode messages = root.putArray("messages");
        if (request.getMessages() != null) {
            for (RelayChatCompletionRequest.Message message : request.getMessages()) {
                ObjectNode item = messages.addObject();
                item.put("role", message.getRole());
                item.put("content", message.getContent());
            }
        }
        return root;
    }

    public ObjectNode toOpenAiResponse(JsonNode claudeResponse, String model) {
        ObjectNode root = objectMapper.createObjectNode();
        root.put("id", claudeResponse.path("id").asText("chatcmpl-claude"));
        root.put("object", "chat.completion");
        root.put("model", model);

        ArrayNode choices = root.putArray("choices");
        ObjectNode choice = choices.addObject();
        choice.put("index", 0);
        ObjectNode message = choice.putObject("message");
        message.put("role", "assistant");
        message.put("content", extractTextContent(claudeResponse));
        choice.put("finish_reason", mapFinishReason(claudeResponse.path("stop_reason").asText(null)));

        ObjectNode usage = root.putObject("usage");
        int promptTokens = claudeResponse.path("usage").path("input_tokens").asInt(0);
        int completionTokens = claudeResponse.path("usage").path("output_tokens").asInt(0);
        usage.put("prompt_tokens", promptTokens);
        usage.put("completion_tokens", completionTokens);
        usage.put("total_tokens", promptTokens + completionTokens);
        return root;
    }

    public RelayResult relay(RelayChatCompletionRequest request, RelayRoute route, String accountType) {
        AccountItem account = relayAccountSelector.selectAccount(route.getProvider(), accountType);
        try {
            Map<String, String> headers = buildClaudeHeaders(account, null);

            String baseUrl = relayAccountSelector.resolveBaseUrl(account, route.getProvider());
            IpItem proxy = relayAccountSelector.resolveProxy(account);
            JsonNode claudeBody = injectOAuthSystemPrompt(toClaudeRequest(request), account);
            RelayResult upstream = upstreamHttpClient.postJson(
                    baseUrl + "/v1/messages",
                    headers,
                    claudeBody,
                    proxy
            );
            upstream.setAccountId(account.getId());
            upstream.setProvider("claude");

            if (!upstream.isSuccess()) {
                return upstream;
            }

            if (Boolean.TRUE.equals(request.getStream())) {
                ClaudeStreamConversion conversion = toOpenAiStreamResponse(upstream.getBody(), request.getModel(), upstream.getRequestId());
                upstream.setBody(conversion.getBody());
                upstream.setContentType("text/event-stream");
                if (conversion.getRequestId() != null) {
                    upstream.setRequestId(conversion.getRequestId());
                }
                upstream.setPromptTokens(conversion.getPromptTokens());
                upstream.setCompletionTokens(conversion.getCompletionTokens());
                upstream.setTotalTokens(conversion.getTotalTokens());
                upstream.setUsageJson(conversion.getUsageJson());
                return upstream;
            }

            try {
                JsonNode claudeResponse = objectMapper.readTree(upstream.getBody());
                ObjectNode openAiResponse = toOpenAiResponse(claudeResponse, request.getModel());
                upstream.setBody(objectMapper.writeValueAsString(openAiResponse));
                upstream.setContentType("application/json");
                upstream.setPromptTokens(openAiResponse.path("usage").path("prompt_tokens").asInt());
                upstream.setCompletionTokens(openAiResponse.path("usage").path("completion_tokens").asInt());
                upstream.setTotalTokens(openAiResponse.path("usage").path("total_tokens").asInt());
                // 保存 Claude 原始 usage 快照（覆盖 populateUsage 的值）
                JsonNode claudeUsage = claudeResponse.path("usage");
                if (!claudeUsage.isMissingNode()) {
                    upstream.setUsageJson(claudeUsage.toString());
                }
                return upstream;
            } catch (Exception ex) {
                throw new RelayException(HttpStatus.BAD_GATEWAY, "Invalid Claude response", "upstream_error");
            }
        } finally {
            relayAccountSelector.releaseAccount(account);
        }
    }

    private static final String OAUTH_SYSTEM_PROMPT = "You are Claude Code, Anthropic's official CLI for Claude.";

    public RelayResult relayMessages(JsonNode requestBody, RelayRoute route, String accountType, String anthropicVersion) {
        AccountItem account = relayAccountSelector.selectAccount(route.getProvider(), accountType);
        try {
            Map<String, String> headers = buildClaudeHeaders(account, anthropicVersion);
            JsonNode body = injectOAuthSystemPrompt(requestBody, account);
            String baseUrl = relayAccountSelector.resolveBaseUrl(account, route.getProvider());
            IpItem proxy = relayAccountSelector.resolveProxy(account);
            RelayResult upstream = upstreamHttpClient.postJson(
                    baseUrl + "/v1/messages",
                    headers,
                    body,
                    proxy
            );
            upstream.setAccountId(account.getId());
            upstream.setProvider("claude");
            return upstream;
        } finally {
            relayAccountSelector.releaseAccount(account);
        }
    }

    private Map<String, String> buildClaudeHeaders(AccountItem account, String anthropicVersion) {
        Map<String, String> headers = new LinkedHashMap<String, String>();
        String credential = sensitiveDataService.reveal(account.getCredential());
        if ("OAuth".equalsIgnoreCase(account.getAuthMethod())) {
            // OAuth accounts always use Bearer auth + beta header
            headers.put("Authorization", "Bearer " + credential);
            headers.put("anthropic-beta", relayProperties.getOauthBetaHeader());
        } else {
            headers.put("x-api-key", credential);
        }
        headers.put("anthropic-version", resolveAnthropicVersion(anthropicVersion));
        return headers;
    }

    /**
     * For OAuth accounts, inject the required system prompt if not already present.
     * This is needed for OAuth tokens to access Sonnet/Opus models.
     */
    private JsonNode injectOAuthSystemPrompt(JsonNode requestBody, AccountItem account) {
        if (!"OAuth".equalsIgnoreCase(account.getAuthMethod())) {
            return requestBody;
        }
        if (requestBody instanceof ObjectNode) {
            ObjectNode body = (ObjectNode) requestBody;
            // Inject system prompt if not present
            if (!body.has("system") || body.path("system").asText("").isEmpty()) {
                body.put("system", OAUTH_SYSTEM_PROMPT);
            }
            // Strip fields not supported by the API for OAuth tokens
            body.remove("context_management");
        }
        return requestBody;
    }

    private String resolveAnthropicVersion(String anthropicVersion) {
        if (anthropicVersion == null || anthropicVersion.trim().isEmpty()) {
            return relayProperties.getAnthropicVersion();
        }
        return anthropicVersion.trim();
    }

    private String extractTextContent(JsonNode claudeResponse) {
        JsonNode content = claudeResponse.path("content");
        if (!content.isArray() || content.size() == 0) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        for (JsonNode item : content) {
            if ("text".equals(item.path("type").asText()) && item.has("text")) {
                if (builder.length() > 0) {
                    builder.append('\n');
                }
                builder.append(item.get("text").asText());
            }
        }
        return builder.toString();
    }

    private String mapFinishReason(String stopReason) {
        if (stopReason == null || stopReason.trim().isEmpty()) {
            return "stop";
        }
        if ("max_tokens".equals(stopReason)) {
            return "length";
        }
        if ("tool_use".equals(stopReason)) {
            return "tool_calls";
        }
        if ("refusal".equals(stopReason)) {
            return "content_filter";
        }
        return "stop";
    }

    private ArrayNode toStopSequences(Object stop) {
        if (stop == null) {
            return null;
        }
        ArrayNode values = objectMapper.createArrayNode();
        if (stop instanceof String) {
            String value = ((String) stop).trim();
            if (!value.isEmpty()) {
                values.add(value);
            }
            return values;
        }
        if (stop instanceof Iterable<?>) {
            for (Object item : (Iterable<?>) stop) {
                if (item instanceof String) {
                    String value = ((String) item).trim();
                    if (!value.isEmpty()) {
                        values.add(value);
                    }
                }
            }
            return values;
        }
        return values;
    }

    private ClaudeStreamConversion toOpenAiStreamResponse(String claudeStreamBody, String model, String upstreamRequestId) {
        String chunkId = normalizeChunkId(upstreamRequestId);
        StringBuilder body = new StringBuilder();
        String finishReason = null;
        Integer promptTokens = null;
        Integer completionTokens = null;
        String usageJson = null;
        boolean emittedContent = false;
        boolean emittedTerminalChunk = false;
        String currentEvent = null;

        String[] lines = claudeStreamBody == null ? new String[0] : claudeStreamBody.split("\\r?\\n");
        try {
            for (String line : lines) {
                if (line.startsWith("event:")) {
                    currentEvent = line.substring("event:".length()).trim();
                    continue;
                }
                if (!line.startsWith("data:")) {
                    continue;
                }

                String payloadText = line.substring("data:".length()).trim();
                if (payloadText.isEmpty() || "[DONE]".equals(payloadText)) {
                    continue;
                }

                JsonNode payload = objectMapper.readTree(payloadText);
                String eventType = textOrFallback(payload.path("type").asText(null), currentEvent);
                if ("message_start".equals(eventType)) {
                    chunkId = normalizeChunkId(payload.path("message").path("id").asText(chunkId));
                    continue;
                }
                if ("content_block_start".equals(eventType)) {
                    String text = payload.path("content_block").path("text").asText(null);
                    if (text != null && !text.isEmpty()) {
                        appendStreamChunk(body, chunkId, model, !emittedContent, text, null);
                        emittedContent = true;
                    }
                    continue;
                }
                if ("content_block_delta".equals(eventType)) {
                    String text = payload.path("delta").path("text").asText(null);
                    if (text != null && !text.isEmpty()) {
                        appendStreamChunk(body, chunkId, model, !emittedContent, text, null);
                        emittedContent = true;
                    }
                    continue;
                }
                if ("message_delta".equals(eventType)) {
                    finishReason = mapFinishReason(payload.path("delta").path("stop_reason").asText(null));
                    JsonNode usageNode = payload.path("usage");
                    if (!usageNode.isMissingNode()) {
                        if (usageNode.has("input_tokens")) {
                            promptTokens = Integer.valueOf(usageNode.path("input_tokens").asInt());
                        }
                        if (usageNode.has("output_tokens")) {
                            completionTokens = Integer.valueOf(usageNode.path("output_tokens").asInt());
                        }
                        usageJson = usageNode.toString();
                    }
                    continue;
                }
                if ("message_stop".equals(eventType)) {
                    appendStreamChunk(body, chunkId, model, false, null, textOrFallback(finishReason, "stop"));
                    emittedTerminalChunk = true;
                }
            }
        } catch (Exception ex) {
            throw new RelayException(HttpStatus.BAD_GATEWAY, "Invalid Claude response", "upstream_error");
        }

        if (!emittedTerminalChunk) {
            appendStreamChunk(body, chunkId, model, false, null, textOrFallback(finishReason, "stop"));
        }
        body.append("data: [DONE]\n\n");

        ClaudeStreamConversion conversion = new ClaudeStreamConversion();
        conversion.setBody(body.toString());
        conversion.setRequestId(chunkId);
        conversion.setPromptTokens(promptTokens);
        conversion.setCompletionTokens(completionTokens);
        conversion.setUsageJson(usageJson);
        if (promptTokens != null || completionTokens != null) {
            conversion.setTotalTokens(Integer.valueOf((promptTokens == null ? 0 : promptTokens.intValue())
                    + (completionTokens == null ? 0 : completionTokens.intValue())));
        }
        return conversion;
    }

    private void appendStreamChunk(StringBuilder body,
                                   String chunkId,
                                   String model,
                                   boolean includeRole,
                                   String content,
                                   String finishReason) {
        boolean hasContent = content != null && !content.isEmpty();
        if (!includeRole && !hasContent && finishReason == null) {
            return;
        }

        ObjectNode root = objectMapper.createObjectNode();
        root.put("id", normalizeChunkId(chunkId));
        root.put("object", "chat.completion.chunk");
        root.put("created", System.currentTimeMillis() / 1000L);
        root.put("model", model);

        ArrayNode choices = root.putArray("choices");
        ObjectNode choice = choices.addObject();
        choice.put("index", 0);
        ObjectNode delta = choice.putObject("delta");
        if (includeRole) {
            delta.put("role", "assistant");
        }
        if (hasContent) {
            delta.put("content", content);
        }
        if (finishReason == null) {
            choice.putNull("finish_reason");
        } else {
            choice.put("finish_reason", finishReason);
        }

        try {
            body.append("data: ")
                    .append(objectMapper.writeValueAsString(root))
                    .append("\n\n");
        } catch (Exception ex) {
            throw new RelayException(HttpStatus.BAD_GATEWAY, "Invalid Claude response", "upstream_error");
        }
    }

    private String normalizeChunkId(String value) {
        if (value == null || value.trim().isEmpty()) {
            return "chatcmpl-claude-stream";
        }
        return value.trim();
    }

    private String textOrFallback(String value, String fallback) {
        return value == null || value.trim().isEmpty() ? fallback : value;
    }

    private static class ClaudeStreamConversion {
        private String body;
        private String requestId;
        private Integer promptTokens;
        private Integer completionTokens;
        private Integer totalTokens;
        private String usageJson;

        public String getBody() {
            return body;
        }

        public void setBody(String body) {
            this.body = body;
        }

        public String getRequestId() {
            return requestId;
        }

        public void setRequestId(String requestId) {
            this.requestId = requestId;
        }

        public Integer getPromptTokens() {
            return promptTokens;
        }

        public void setPromptTokens(Integer promptTokens) {
            this.promptTokens = promptTokens;
        }

        public Integer getCompletionTokens() {
            return completionTokens;
        }

        public void setCompletionTokens(Integer completionTokens) {
            this.completionTokens = completionTokens;
        }

        public Integer getTotalTokens() {
            return totalTokens;
        }

        public void setTotalTokens(Integer totalTokens) {
            this.totalTokens = totalTokens;
        }

        public String getUsageJson() {
            return usageJson;
        }

        public void setUsageJson(String usageJson) {
            this.usageJson = usageJson;
        }
    }
}
