package com.firstapi.backend.service;

import com.firstapi.backend.model.AccountItem;
import com.firstapi.backend.model.IpItem;
import com.firstapi.backend.model.RelayChatCompletionRequest;
import com.firstapi.backend.model.RelayResult;
import com.firstapi.backend.model.RelayRoute;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Service
public class OpenAiRelayAdapter {

    private final RelayAccountSelector relayAccountSelector;
    private final SensitiveDataService sensitiveDataService;
    private final UpstreamHttpClient upstreamHttpClient;
    private final ObjectMapper objectMapper;

    public OpenAiRelayAdapter(RelayAccountSelector relayAccountSelector,
                              SensitiveDataService sensitiveDataService,
                              UpstreamHttpClient upstreamHttpClient,
                              ObjectMapper objectMapper) {
        this.relayAccountSelector = relayAccountSelector;
        this.sensitiveDataService = sensitiveDataService;
        this.upstreamHttpClient = upstreamHttpClient;
        this.objectMapper = objectMapper;
    }

    public RelayResult relay(RelayChatCompletionRequest request, RelayRoute route, String accountType) {
        AccountItem account = relayAccountSelector.selectAccount(route.getProvider(), accountType);
        try {
            Map<String, String> headers = new LinkedHashMap<String, String>();
            headers.put(HttpHeaders.AUTHORIZATION, "Bearer " + sensitiveDataService.reveal(account.getCredential()));

            JsonNode payload = objectMapper.valueToTree(request);
            String baseUrl = relayAccountSelector.resolveBaseUrl(account, route.getProvider());
            IpItem proxy = relayAccountSelector.resolveProxy(account);
            RelayResult result = upstreamHttpClient.postJson(baseUrl + "/v1/chat/completions", headers, payload, proxy);
            result.setAccountId(account.getId());
            result.setProvider("openai");
            result.setAuthMethod(account.getAuthMethod());
            return result;
        } finally {
            relayAccountSelector.releaseAccount(account);
        }
    }

    /**
     * 流式 Relay: OpenAI Chat Completions。逐块转发 SSE 到客户端 outputStream。
     */
    public RelayResult relayStreaming(RelayChatCompletionRequest request, RelayRoute route,
                                      String accountType, java.io.OutputStream clientOutput) {
        AccountItem account = relayAccountSelector.selectAccount(route.getProvider(), accountType);
        try {
            Map<String, String> headers = new LinkedHashMap<String, String>();
            headers.put(HttpHeaders.AUTHORIZATION, "Bearer " + sensitiveDataService.reveal(account.getCredential()));

            JsonNode payload = objectMapper.valueToTree(request);
            String baseUrl = relayAccountSelector.resolveBaseUrl(account, route.getProvider());
            IpItem proxy = relayAccountSelector.resolveProxy(account);
            RelayResult result = upstreamHttpClient.postJsonStreaming(
                    baseUrl + "/v1/chat/completions", headers, payload, proxy,
                    chunk -> writeQuietly(clientOutput, chunk));
            result.setAccountId(account.getId());
            result.setProvider("openai");
            result.setAuthMethod(account.getAuthMethod());
            return result;
        } finally {
            relayAccountSelector.releaseAccount(account);
        }
    }

    /**
     * 流式 Relay: OpenAI Responses API。逐块转发 SSE 到客户端 outputStream。
     */
    public RelayResult relayResponsesStreaming(JsonNode requestBody, RelayRoute route,
                                                String accountType, java.io.OutputStream clientOutput) {
        AccountItem account = relayAccountSelector.selectAccount(route.getProvider(), accountType);
        try {
            String credential = sensitiveDataService.reveal(account.getCredential());
            Map<String, String> headers = new LinkedHashMap<>();
            headers.put(HttpHeaders.AUTHORIZATION, "Bearer " + credential);

            String targetUrl;
            IpItem proxy = relayAccountSelector.resolveProxy(account);
            JsonNode payload;
            if ("OAuth".equalsIgnoreCase(account.getAuthMethod())) {
                targetUrl = "https://chatgpt.com/backend-api/codex/responses";
                headers.put("User-Agent", "codex_cli_rs/0.80.0 (Windows 15.7.2; x86_64) Terminal");
                headers.put("Origin", "https://chatgpt.com");
                headers.put("Referer", "https://chatgpt.com/");
                headers.put("Oai-Language", "en-US");
                headers.put("Oai-Device-Id", UUID.randomUUID().toString());
                payload = normalizeCodexRequest(requestBody);
            } else {
                String baseUrl = relayAccountSelector.resolveBaseUrl(account, route.getProvider());
                targetUrl = baseUrl + "/v1/responses";
                payload = requestBody;
            }

            RelayResult result = upstreamHttpClient.postJsonStreaming(
                    targetUrl, headers, payload, proxy,
                    chunk -> writeQuietly(clientOutput, chunk));
            result.setAccountId(account.getId());
            result.setProvider("openai");
            result.setAuthMethod(account.getAuthMethod());
            return result;
        } finally {
            relayAccountSelector.releaseAccount(account);
        }
    }

    private static void writeQuietly(java.io.OutputStream out, byte[] data) {
        try {
            out.write(data);
            out.flush();
        } catch (java.io.IOException ignored) {
            // 客户端断开连接，忽略
        }
    }

    /**
     * Relay OpenAI Responses API requests.
     * OAuth accounts (ChatGPT Plus/Pro): forward directly to chatgpt.com/backend-api/codex/responses.
     * API key accounts: forward to configured base_url/v1/responses.
     */
    public RelayResult relayResponses(JsonNode requestBody, RelayRoute route, String accountType) {
        AccountItem account = relayAccountSelector.selectAccount(route.getProvider(), accountType);
        try {
            String credential = sensitiveDataService.reveal(account.getCredential());
            Map<String, String> headers = new LinkedHashMap<>();
            headers.put(HttpHeaders.AUTHORIZATION, "Bearer " + credential);

            String targetUrl;
            IpItem proxy = relayAccountSelector.resolveProxy(account);

            JsonNode payload;
            if ("OAuth".equalsIgnoreCase(account.getAuthMethod())) {
                // ChatGPT Plus/Pro OAuth → chatgpt.com codex endpoint
                targetUrl = "https://chatgpt.com/backend-api/codex/responses";
                headers.put("User-Agent", "codex_cli_rs/0.80.0 (Windows 15.7.2; x86_64) Terminal");
                headers.put("Origin", "https://chatgpt.com");
                headers.put("Referer", "https://chatgpt.com/");
                headers.put("Oai-Language", "en-US");
                headers.put("Oai-Device-Id", UUID.randomUUID().toString());
                // chatgpt.com/backend-api/codex/responses requires: instructions, store=false, stream=true, input as array
                payload = normalizeCodexRequest(requestBody);
            } else {
                // API key → forward to configured base URL (OpenAI Responses API)
                String baseUrl = relayAccountSelector.resolveBaseUrl(account, route.getProvider());
                targetUrl = baseUrl + "/v1/responses";
                payload = requestBody;
            }

            RelayResult result = upstreamHttpClient.postJson(targetUrl, headers, payload, proxy);
            normalizeResponsesBody(result);
            result.setAccountId(account.getId());
            result.setProvider("openai");
            result.setAuthMethod(account.getAuthMethod());
            return result;
        } finally {
            relayAccountSelector.releaseAccount(account);
        }
    }

    /**
     * 对指定账号发送一次最小化真实探针请求（与正常中转完全一致的 headers + payload），
     * 供账号测试使用，不走 selectAccount/releaseAccount。
     * - OAuth 账号：走 chatgpt.com/backend-api/codex/responses（与真实 Responses API 中转一致）
     * - API Key 账号：走 /v1/chat/completions
     */
    public RelayResult probeAccount(AccountItem account, IpItem proxy) {
        String credential = sensitiveDataService.reveal(account.getCredential());
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put(HttpHeaders.AUTHORIZATION, "Bearer " + credential);

        String targetUrl;
        JsonNode payload;

        if ("OAuth".equalsIgnoreCase(account.getAuthMethod())) {
            // OAuth 账号：与 relayResponses/relayResponsesStreaming 完全一致的头部和 endpoint
            targetUrl = "https://chatgpt.com/backend-api/codex/responses";
            headers.put("User-Agent", "codex_cli_rs/0.80.0 (Windows 15.7.2; x86_64) Terminal");
            headers.put("Origin", "https://chatgpt.com");
            headers.put("Referer", "https://chatgpt.com/");
            headers.put("Oai-Language", "en-US");
            headers.put("Oai-Device-Id", UUID.randomUUID().toString());
            // 构造最小化 codex/responses 格式请求
            // chatgpt.com codex 端点只接受 gpt-5.x/codex 系列模型，使用实际中转中最常用的 gpt-5.4
            tools.jackson.databind.node.ObjectNode reqBody = objectMapper.createObjectNode();
            reqBody.put("model", "gpt-5.4");
            reqBody.put("instructions", "You are a helpful assistant.");
            reqBody.put("store", false);
            reqBody.put("stream", true);
            reqBody.putArray("input").addObject().put("role", "user").put("content", "hi");
            payload = reqBody;
        } else {
            // API Key 账号：标准 chat/completions
            String baseUrl = relayAccountSelector.resolveBaseUrl(account, "openai");
            targetUrl = baseUrl + "/v1/chat/completions";
            tools.jackson.databind.node.ObjectNode reqBody = objectMapper.createObjectNode();
            reqBody.put("model", "gpt-4o-mini");
            reqBody.put("max_tokens", 1);
            reqBody.putArray("messages").addObject().put("role", "user").put("content", "hi");
            payload = reqBody;
        }

        RelayResult result = upstreamHttpClient.postJson(targetUrl, headers, payload, proxy);
        result.setAccountId(account.getId());
        result.setProvider("openai");
        result.setAuthMethod(account.getAuthMethod());
        return result;
    }

    private void normalizeResponsesBody(RelayResult result) {
        if (result == null || result.getBody() == null) {
            return;
        }
        String body = result.getBody().trim();
        if (!body.startsWith("event:") && !body.startsWith("data:")) {
            return;
        }
        String normalized = extractCompletedResponse(body);
        if (normalized != null) {
            result.setBody(normalized);
            result.setContentType("application/json");
        }
    }

    private String extractCompletedResponse(String sseBody) {
        String[] lines = sseBody.split("\\r?\\n");
        for (String line : lines) {
            if (!line.startsWith("data:")) {
                continue;
            }
            String payload = line.substring("data:".length()).trim();
            if (payload.isEmpty() || "[DONE]".equals(payload)) {
                continue;
            }
            try {
                JsonNode event = objectMapper.readTree(payload);
                JsonNode response = event.path("response");
                if ("response.completed".equals(event.path("type").asText())
                        && !response.isMissingNode()
                        && !response.isNull()) {
                    return response.toString();
                }
            } catch (Exception ignored) {
                // 忽略无法解析的中间事件，继续查找 response.completed
            }
        }
        return null;
    }

    /**
     * Normalize request body for chatgpt.com/backend-api/codex/responses.
     * Required fields: instructions (string), store=false, stream=true, input as array.
     */
    private JsonNode normalizeCodexRequest(JsonNode original) {
        tools.jackson.databind.node.ObjectNode node = objectMapper.createObjectNode();
        // Copy all original fields
        original.properties().forEach(entry -> node.set(entry.getKey(), entry.getValue()));

        // instructions is required by chatgpt.com
        if (!node.has("instructions") || node.path("instructions").isNull()) {
            node.put("instructions", "You are a helpful assistant.");
        }

        // store must be false
        node.put("store", false);

        // stream must be true
        node.put("stream", true);

        // input must be an array, convert string to array if needed
        JsonNode input = node.path("input");
        if (input.isTextual()) {
            tools.jackson.databind.node.ArrayNode arr = objectMapper.createArrayNode();
            tools.jackson.databind.node.ObjectNode msg = objectMapper.createObjectNode();
            msg.put("role", "user");
            msg.put("content", input.asText());
            arr.add(msg);
            node.set("input", arr);
        } else if (input.isMissingNode() || input.isNull()) {
            node.set("input", objectMapper.createArrayNode());
        }

        return node;
    }
}
