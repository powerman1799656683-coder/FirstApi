package com.firstapi.backend.controller;

import com.firstapi.backend.model.RelayChatCompletionRequest;
import com.firstapi.backend.model.RelayException;
import com.firstapi.backend.model.RelayResult;
import com.firstapi.backend.service.RelayService;
import jakarta.servlet.http.HttpServletResponse;
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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

import org.springframework.web.context.request.async.AsyncRequestNotUsableException;

@RestController
public class RelayController {

    private static final Logger LOGGER = LoggerFactory.getLogger(RelayController.class);

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
    public void chatCompletions(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
            @RequestBody RelayChatCompletionRequest request,
            HttpServletResponse servletResponse) throws IOException {

        if (Boolean.TRUE.equals(request.getStream())) {
            initSseResponse(servletResponse);
            OutputStream out = servletResponse.getOutputStream();
            try {
                RelayResult result = relayService.relayChatCompletionStreaming(authorization, request, out);
                // Claude 模型回退到缓冲模式时需要写出 body
                if (result.getBody() != null) {
                    out.write(result.getBody().getBytes(StandardCharsets.UTF_8));
                }
            } catch (AsyncRequestNotUsableException ex) {
                LOGGER.debug("Client disconnected during chat completions streaming: {}", ex.getMessage());
                return;
            } catch (RelayException ex) {
                LOGGER.warn("Streaming chat completions relay error: status={}, code={}, msg={}",
                        ex.getStatus().value(), ex.getCode(), ex.getMessage());
                writeSseError(out, ex.getStatus().value(), ex.getMessage());
            } catch (Exception ex) {
                LOGGER.error("Streaming chat completions unexpected error: {}", ex.getMessage(), ex);
                writeSseError(out, 500, ex.getMessage());
            }
            flushQuietly(out);
            return;
        }

        // 非流式
        RelayResult result = relayService.relayChatCompletion(authorization, request);
        writeResult(servletResponse, result);
    }

    @PostMapping("/v1/messages")
    public void messages(
            @RequestHeader(value = "x-api-key", required = false) String xApiKey,
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
            @RequestHeader(value = "anthropic-version", required = false) String anthropicVersion,
            @RequestBody JsonNode body,
            HttpServletResponse servletResponse) throws IOException {

        boolean isStream = body.path("stream").asBoolean(false);
        if (isStream) {
            initSseResponse(servletResponse);
            OutputStream out = servletResponse.getOutputStream();
            try {
                RelayResult result = relayService.relayClaudeMessagesStreaming(
                        xApiKey, authorization, anthropicVersion, body, out);
                if (result.getBody() != null) {
                    out.write(result.getBody().getBytes(StandardCharsets.UTF_8));
                }
            } catch (AsyncRequestNotUsableException ex) {
                LOGGER.debug("Client disconnected during Claude messages streaming: {}", ex.getMessage());
                return;
            } catch (RelayException ex) {
                LOGGER.warn("Streaming Claude messages relay error: status={}, code={}, msg={}",
                        ex.getStatus().value(), ex.getCode(), ex.getMessage());
                writeSseError(out, ex.getStatus().value(), ex.getMessage());
            } catch (Exception ex) {
                LOGGER.error("Streaming Claude messages unexpected error: {}", ex.getMessage(), ex);
                writeSseError(out, 500, ex.getMessage());
            }
            flushQuietly(out);
            return;
        }

        RelayResult result = relayService.relayClaudeMessages(xApiKey, authorization, anthropicVersion, body);
        writeResult(servletResponse, result);
    }

    /**
     * OpenAI Responses API endpoint.
     */
    @PostMapping({"/v1/responses", "/responses"})
    public void responses(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
            @RequestBody JsonNode body,
            HttpServletResponse servletResponse) throws IOException {

        boolean isStream = body.path("stream").asBoolean(false);
        if (isStream) {
            initSseResponse(servletResponse);
            OutputStream out = servletResponse.getOutputStream();
            try {
                RelayResult result = relayService.relayResponsesApiStreaming(authorization, body, out);
                // 上游返回错误时，body 中有错误信息，需要写出
                if (result.getBody() != null) {
                    out.write(result.getBody().getBytes(StandardCharsets.UTF_8));
                }
            } catch (AsyncRequestNotUsableException ex) {
                return;
            } catch (RelayException ex) {
                LOGGER.error("Streaming responses relay error: {}", ex.getMessage());
                writeSseError(out, ex.getStatus().value(), ex.getMessage());
            } catch (Exception ex) {
                LOGGER.error("Streaming responses unexpected error", ex);
                writeSseError(out, 500, ex.getMessage());
            }
            flushQuietly(out);
            return;
        }

        RelayResult result = relayService.relayResponsesApi(authorization, body);
        writeResult(servletResponse, result);
    }

    private static void initSseResponse(HttpServletResponse response) {
        response.setStatus(200);
        response.setContentType("text/event-stream");
        response.setCharacterEncoding("UTF-8");
        response.setHeader("Cache-Control", "no-cache");
        response.setHeader("X-Accel-Buffering", "no");
    }

    private static void writeResult(HttpServletResponse response, RelayResult result) throws IOException {
        response.setStatus(result.getStatusCode());
        String contentType = result.getContentType();
        response.setContentType(contentType != null ? contentType : "application/json");
        if (result.getBody() != null) {
            response.getOutputStream().write(result.getBody().getBytes(StandardCharsets.UTF_8));
            response.getOutputStream().flush();
        }
    }

    private static void flushQuietly(OutputStream out) {
        try {
            out.flush();
        } catch (IOException ignored) {
            // 客户端已断开
        }
    }

    private static void writeSseError(OutputStream out, int status, String message) {
        try {
            String errorJson = "{\"error\":{\"message\":\""
                    + (message == null ? "Internal error" : message.replace("\"", "\\\""))
                    + "\",\"type\":\"server_error\",\"code\":" + status + "}}";
            out.write(("data: " + errorJson + "\n\ndata: [DONE]\n\n")
                    .getBytes(StandardCharsets.UTF_8));
            out.flush();
        } catch (IOException ignored) {
        }
    }
}
