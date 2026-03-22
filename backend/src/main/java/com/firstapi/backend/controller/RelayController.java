package com.firstapi.backend.controller;

import com.firstapi.backend.model.RelayChatCompletionRequest;
import com.firstapi.backend.model.RelayResult;
import com.firstapi.backend.service.RelayService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.JsonNode;

@RestController
public class RelayController {

    private final RelayService relayService;

    public RelayController(RelayService relayService) {
        this.relayService = relayService;
    }

    @RequestMapping(value = "/v1/models", method = {RequestMethod.GET, RequestMethod.POST})
    public ResponseEntity<String> models() {
        String body = "{\"object\":\"list\",\"data\":["
                // OpenAI GPT models
                + "{\"id\":\"gpt-4o\",\"object\":\"model\",\"created\":1715367049,\"owned_by\":\"openai\"},"
                + "{\"id\":\"gpt-4o-mini\",\"object\":\"model\",\"created\":1715367049,\"owned_by\":\"openai\"},"
                + "{\"id\":\"gpt-4.1\",\"object\":\"model\",\"created\":1715367049,\"owned_by\":\"openai\"},"
                + "{\"id\":\"gpt-4.1-mini\",\"object\":\"model\",\"created\":1715367049,\"owned_by\":\"openai\"},"
                + "{\"id\":\"gpt-4.1-nano\",\"object\":\"model\",\"created\":1715367049,\"owned_by\":\"openai\"},"
                + "{\"id\":\"gpt-5.4\",\"object\":\"model\",\"created\":1715367049,\"owned_by\":\"openai\"},"
                + "{\"id\":\"gpt-5.4-mini\",\"object\":\"model\",\"created\":1715367049,\"owned_by\":\"openai\"},"
                + "{\"id\":\"gpt-5.4-nano\",\"object\":\"model\",\"created\":1715367049,\"owned_by\":\"openai\"},"
                // OpenAI reasoning models
                + "{\"id\":\"o3\",\"object\":\"model\",\"created\":1715367049,\"owned_by\":\"openai\"},"
                + "{\"id\":\"o3-mini\",\"object\":\"model\",\"created\":1715367049,\"owned_by\":\"openai\"},"
                + "{\"id\":\"o4-mini\",\"object\":\"model\",\"created\":1715367049,\"owned_by\":\"openai\"},"
                // OpenAI codex models
                + "{\"id\":\"codex-mini\",\"object\":\"model\",\"created\":1715367049,\"owned_by\":\"openai\"},"
                + "{\"id\":\"gpt-5.3-codex\",\"object\":\"model\",\"created\":1715367049,\"owned_by\":\"openai\"},"
                // Anthropic Claude models
                + "{\"id\":\"claude-opus-4-6\",\"object\":\"model\",\"created\":1715367049,\"owned_by\":\"anthropic\"},"
                + "{\"id\":\"claude-sonnet-4-6\",\"object\":\"model\",\"created\":1715367049,\"owned_by\":\"anthropic\"},"
                + "{\"id\":\"claude-opus-4-5\",\"object\":\"model\",\"created\":1715367049,\"owned_by\":\"anthropic\"},"
                + "{\"id\":\"claude-sonnet-4-5\",\"object\":\"model\",\"created\":1715367049,\"owned_by\":\"anthropic\"},"
                + "{\"id\":\"claude-haiku-4-5\",\"object\":\"model\",\"created\":1715367049,\"owned_by\":\"anthropic\"},"
                + "{\"id\":\"claude-3-5-haiku-20241022\",\"object\":\"model\",\"created\":1715367049,\"owned_by\":\"anthropic\"}"
                + "]}";
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(body);
    }

    @PostMapping("/v1/chat/completions")
    public ResponseEntity<String> chatCompletions(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
            @RequestBody RelayChatCompletionRequest request) {
        RelayResult result = relayService.relayChatCompletion(authorization, request);
        MediaType mediaType = result.getContentType() == null
                ? MediaType.APPLICATION_JSON
                : MediaType.parseMediaType(result.getContentType());
        return ResponseEntity.status(result.getStatusCode())
                .contentType(mediaType)
                .body(result.getBody());
    }

    @PostMapping("/v1/messages")
    public ResponseEntity<String> messages(
            @RequestHeader(value = "x-api-key", required = false) String xApiKey,
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
            @RequestHeader(value = "anthropic-version", required = false) String anthropicVersion,
            @RequestBody JsonNode body) {
        RelayResult result = relayService.relayClaudeMessages(xApiKey, authorization, anthropicVersion, body);
        MediaType mediaType = result.getContentType() == null
                ? MediaType.APPLICATION_JSON
                : MediaType.parseMediaType(result.getContentType());
        return ResponseEntity.status(result.getStatusCode())
                .contentType(mediaType)
                .body(result.getBody());
    }

    /**
     * OpenAI Responses API endpoint.
     * OAuth accounts: proxied to chatgpt.com/backend-api/codex/responses (no format conversion).
     * API key accounts: proxied to base_url/v1/responses.
     */
    @PostMapping("/v1/responses")
    public ResponseEntity<String> responses(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
            @RequestBody JsonNode body) {
        RelayResult result = relayService.relayResponsesApi(authorization, body);
        MediaType mediaType = result.getContentType() == null
                ? MediaType.APPLICATION_JSON
                : MediaType.parseMediaType(result.getContentType());
        return ResponseEntity.status(result.getStatusCode())
                .contentType(mediaType)
                .body(result.getBody());
    }
}
