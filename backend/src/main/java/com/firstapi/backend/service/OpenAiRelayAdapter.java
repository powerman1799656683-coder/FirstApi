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
            return result;
        } finally {
            relayAccountSelector.releaseAccount(account);
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
            result.setAccountId(account.getId());
            result.setProvider("openai");
            return result;
        } finally {
            relayAccountSelector.releaseAccount(account);
        }
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
