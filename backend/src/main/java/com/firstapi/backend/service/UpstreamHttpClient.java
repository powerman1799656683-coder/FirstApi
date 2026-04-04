package com.firstapi.backend.service;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import com.firstapi.backend.config.RelayProperties;
import com.firstapi.backend.model.IpItem;
import com.firstapi.backend.model.RelayException;
import com.firstapi.backend.model.RelayResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.HttpURLConnection;
import java.net.Proxy;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

@Service
public class UpstreamHttpClient {

    private static final Logger LOGGER = LoggerFactory.getLogger(UpstreamHttpClient.class);

    private final ObjectMapper objectMapper;
    private final RelayProperties relayProperties;

    public UpstreamHttpClient(ObjectMapper objectMapper, RelayProperties relayProperties) {
        this.objectMapper = objectMapper;
        this.relayProperties = relayProperties;
    }

    public RelayResult postJson(String url, Map<String, String> headers, JsonNode body) {
        return postJson(url, headers, body, null);
    }

    public RelayResult get(String url, Map<String, String> headers, IpItem proxyItem) {
        HttpURLConnection connection = null;
        long start = System.currentTimeMillis();
        try {
            ProxyRoute proxyRoute = resolveProxyRoute(proxyItem);
            connection = openConnection(url, proxyRoute);
            connection.setConnectTimeout(relayProperties.getConnectTimeoutMs());
            connection.setReadTimeout(relayProperties.getReadTimeoutMs());
            connection.setRequestMethod("GET");
            connection.setDoInput(true);
            if (proxyRoute != null && proxyRoute.proxyAuthorization() != null) {
                connection.setRequestProperty("Proxy-Authorization", proxyRoute.proxyAuthorization());
            }
            for (Map.Entry<String, String> entry : headers.entrySet()) {
                connection.setRequestProperty(entry.getKey(), entry.getValue());
            }

            int statusCode = connection.getResponseCode();
            String responseBody = readFully(statusCode >= 400 ? connection.getErrorStream() : connection.getInputStream());

            RelayResult result = new RelayResult();
            result.setStatusCode(statusCode);
            result.setContentType(connection.getContentType());
            result.setBody(responseBody);
            result.setSuccess(statusCode >= 200 && statusCode < 300);
            result.setLatencyMs(System.currentTimeMillis() - start);
            result.setRequestId(firstHeaderOrNull(connection, "x-request-id", "request-id"));
            result.setResponseHeaders(captureCooldownHeaders(connection));
            populateUsage(result, responseBody);
            return result;
        } catch (IOException ex) {
            long elapsed = System.currentTimeMillis() - start;
            LOGGER.error("Upstream request failed: url={}, elapsed={}ms, error={}", url, elapsed, ex.getMessage(), ex);
            throw new RelayException(HttpStatus.BAD_GATEWAY, "Upstream request failed: " + ex.getMessage(), "upstream_error");
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    public RelayResult postJson(String url, Map<String, String> headers, JsonNode body, IpItem proxyItem) {
        return postJson(url, headers, body, proxyItem, relayProperties.getReadTimeoutMs());
    }

    /**
     * 流式 POST 请求：逐块读取上游 SSE 响应并通过 chunkConsumer 实时转发给客户端。
     * 同时在内存中收集 usage 数据，流结束后填充到返回的 RelayResult。
     * body 字段在成功时为 null（已转发），错误时包含错误响应体。
     *
     * 使用流式专用超时（streamReadTimeoutMs，默认 300s），并在客户端断开时
     * 标记 clientDisconnected，及时终止上游读取。
     */
    /** 流式请求连接阶段最大重试次数 */
    private static final int STREAMING_CONNECT_MAX_RETRIES = 1;
    /** 重试间隔（毫秒） */
    private static final long STREAMING_CONNECT_RETRY_DELAY_MS = 1000;

    public RelayResult postJsonStreaming(String url, Map<String, String> headers, JsonNode body,
                                         IpItem proxyItem, java.util.function.Consumer<byte[]> chunkConsumer) {
        HttpURLConnection connection = null;
        long start = System.currentTimeMillis();
        long totalBytesForwarded = 0;
        int chunksForwarded = 0;
        boolean clientDisconnected = false;
        try {
            ProxyRoute proxyRoute = resolveProxyRoute(proxyItem);
            byte[] payload = objectMapper.writeValueAsBytes(body);

            // 连接阶段重试：在还没向客户端发送任何数据之前，连接/发送失败可以安全重试
            int connectAttempt = 0;
            int statusCode;
            while (true) {
                try {
                    connection = openConnection(url, proxyRoute);
                    connection.setConnectTimeout(relayProperties.getConnectTimeoutMs());
                    connection.setReadTimeout(relayProperties.getStreamReadTimeoutMs());
                    connection.setRequestMethod("POST");
                    connection.setDoOutput(true);
                    connection.setRequestProperty("Content-Type", "application/json");
                    if (proxyRoute != null && proxyRoute.proxyAuthorization() != null) {
                        connection.setRequestProperty("Proxy-Authorization", proxyRoute.proxyAuthorization());
                    }
                    for (Map.Entry<String, String> entry : headers.entrySet()) {
                        connection.setRequestProperty(entry.getKey(), entry.getValue());
                    }

                    OutputStream out = connection.getOutputStream();
                    out.write(payload);
                    out.flush();
                    out.close();

                    statusCode = connection.getResponseCode();
                    break; // 连接成功，跳出重试循环
                } catch (IOException connectEx) {
                    if (connection != null) {
                        connection.disconnect();
                        connection = null;
                    }
                    connectAttempt++;
                    if (connectAttempt > STREAMING_CONNECT_MAX_RETRIES) {
                        throw connectEx; // 重试耗尽，抛出原始异常
                    }
                    LOGGER.warn("Streaming connect attempt {} failed, retrying in {}ms: url={}, error={}",
                            connectAttempt, STREAMING_CONNECT_RETRY_DELAY_MS, url, connectEx.getMessage());
                    try {
                        Thread.sleep(STREAMING_CONNECT_RETRY_DELAY_MS);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw connectEx;
                    }
                }
            }
            RelayResult result = new RelayResult();
            result.setStatusCode(statusCode);
            result.setContentType(connection.getContentType());
            result.setSuccess(statusCode >= 200 && statusCode < 300);
            result.setRequestId(firstHeaderOrNull(connection, "x-request-id", "request-id"));
            result.setResponseHeaders(captureCooldownHeaders(connection));

            if (statusCode >= 400) {
                String errorBody = readFully(connection.getErrorStream());
                result.setBody(errorBody);
                result.setLatencyMs(System.currentTimeMillis() - start);
                populateUsage(result, errorBody);
                LOGGER.warn("Upstream streaming returned error: url={}, status={}, elapsed={}ms, body={}",
                        url, statusCode, result.getLatencyMs(),
                        errorBody != null && errorBody.length() > 500 ? errorBody.substring(0, 500) + "..." : errorBody);
                return result;
            }

            // 成功响应：逐块读取并转发，同时收集 usage 行
            InputStream inputStream = connection.getInputStream();
            StringBuilder usageCollector = new StringBuilder();
            StringBuilder lineBuffer = new StringBuilder();
            byte[] buffer = new byte[4096];
            int read;
            while ((read = inputStream.read(buffer)) != -1) {
                totalBytesForwarded += read;
                chunksForwarded++;
                // 转发给客户端，检测客户端是否已断开
                try {
                    chunkConsumer.accept(java.util.Arrays.copyOf(buffer, read));
                } catch (Exception writeEx) {
                    // 客户端已断开，停止转发但继续读取 usage 数据
                    if (!clientDisconnected) {
                        clientDisconnected = true;
                        LOGGER.info("Client disconnected during streaming: url={}, bytesForwarded={}, chunks={}, elapsed={}ms",
                                url, totalBytesForwarded, chunksForwarded, System.currentTimeMillis() - start);
                    }
                }
                collectUsageLines(usageCollector, lineBuffer, buffer, read);
            }
            inputStream.close();

            result.setLatencyMs(System.currentTimeMillis() - start);
            parseCollectedUsage(result, usageCollector.toString());
            result.setBody(null);

            LOGGER.debug("Upstream streaming completed: url={}, bytesForwarded={}, chunks={}, elapsed={}ms, clientDisconnected={}",
                    url, totalBytesForwarded, chunksForwarded, result.getLatencyMs(), clientDisconnected);
            return result;
        } catch (java.net.SocketTimeoutException ex) {
            long elapsed = System.currentTimeMillis() - start;
            LOGGER.error("Upstream streaming read timeout: url={}, bytesForwarded={}, chunks={}, elapsed={}ms, timeoutMs={}",
                    url, totalBytesForwarded, chunksForwarded, elapsed, relayProperties.getStreamReadTimeoutMs());
            throw new RelayException(HttpStatus.BAD_GATEWAY,
                    "Upstream streaming timeout after " + (elapsed / 1000) + "s (forwarded " + totalBytesForwarded + " bytes)",
                    "upstream_timeout");
        } catch (IOException ex) {
            long elapsed = System.currentTimeMillis() - start;
            String errorClass = ex.getClass().getSimpleName();
            LOGGER.error("Upstream streaming failed: url={}, errorType={}, bytesForwarded={}, chunks={}, elapsed={}ms, error={}",
                    url, errorClass, totalBytesForwarded, chunksForwarded, elapsed, ex.getMessage(), ex);
            throw new RelayException(HttpStatus.BAD_GATEWAY,
                    "Upstream streaming failed (" + errorClass + "): " + ex.getMessage(),
                    "upstream_error");
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    /**
     * 从 SSE 流中收集可能包含 usage/limit 信息的行。
     * 使用 lineBuffer 正确处理跨 buffer 断行，避免遗漏被拆分的 usage 行。
     */
    private void collectUsageLines(StringBuilder collector, StringBuilder lineBuffer,
                                    byte[] buffer, int length) {
        lineBuffer.append(new String(buffer, 0, length, StandardCharsets.UTF_8));
        int newlineIdx;
        while ((newlineIdx = lineBuffer.indexOf("\n")) >= 0) {
            String line = lineBuffer.substring(0, newlineIdx).replace("\r", "");
            lineBuffer.delete(0, newlineIdx + 1);
            if (line.startsWith("data:") && (line.contains("usage") || line.contains("limit")
                    || line.contains("response.completed"))) {
                collector.append(line).append('\n');
            }
        }
    }

    /**
     * 从收集的 usage 行中解析 token 信息。
     */
    private void parseCollectedUsage(RelayResult result, String collectedLines) {
        if (collectedLines == null || collectedLines.isEmpty()) {
            return;
        }
        extractUsageFromSse(result, collectedLines);
    }

    public RelayResult postJson(String url, Map<String, String> headers, JsonNode body, IpItem proxyItem, int readTimeoutMs) {
        HttpURLConnection connection = null;
        long start = System.currentTimeMillis();
        try {
            ProxyRoute proxyRoute = resolveProxyRoute(proxyItem);
            connection = openConnection(url, proxyRoute);
            connection.setConnectTimeout(relayProperties.getConnectTimeoutMs());
            connection.setReadTimeout(readTimeoutMs);
            connection.setRequestMethod("POST");
            connection.setDoOutput(true);
            connection.setRequestProperty("Content-Type", "application/json");
            if (proxyRoute != null && proxyRoute.proxyAuthorization() != null) {
                connection.setRequestProperty("Proxy-Authorization", proxyRoute.proxyAuthorization());
            }
            for (Map.Entry<String, String> entry : headers.entrySet()) {
                connection.setRequestProperty(entry.getKey(), entry.getValue());
            }

            byte[] payload = objectMapper.writeValueAsBytes(body);
            OutputStream outputStream = connection.getOutputStream();
            outputStream.write(payload);
            outputStream.flush();
            outputStream.close();

            int statusCode = connection.getResponseCode();
            String responseBody = readFully(statusCode >= 400 ? connection.getErrorStream() : connection.getInputStream());

            RelayResult result = new RelayResult();
            result.setStatusCode(statusCode);
            result.setContentType(connection.getContentType());
            result.setBody(responseBody);
            result.setSuccess(statusCode >= 200 && statusCode < 300);
            result.setLatencyMs(System.currentTimeMillis() - start);
            result.setRequestId(firstHeaderOrNull(connection, "x-request-id", "request-id"));
            result.setResponseHeaders(captureCooldownHeaders(connection));
            populateUsage(result, responseBody);
            return result;
        } catch (IOException ex) {
            long elapsed = System.currentTimeMillis() - start;
            LOGGER.error("Upstream request failed: url={}, elapsed={}ms, error={}", url, elapsed, ex.getMessage(), ex);
            throw new RelayException(HttpStatus.BAD_GATEWAY, "Upstream request failed: " + ex.getMessage(), "upstream_error");
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private HttpURLConnection openConnection(String url, ProxyRoute proxyRoute) throws IOException {
        if (proxyRoute == null) {
            return (HttpURLConnection) URI.create(url).toURL().openConnection();
        }
        return (HttpURLConnection) URI.create(url).toURL().openConnection(proxyRoute.proxy());
    }

    ProxyRoute resolveProxyRoute(IpItem proxyItem) {
        if (proxyItem == null) {
            return null;
        }
        String protocol = normalize(proxyItem.getProtocol());
        Proxy.Type type;
        if ("SOCKS5".equals(protocol)) {
            type = Proxy.Type.SOCKS;
        } else if ("HTTP".equals(protocol) || "HTTPS".equals(protocol)) {
            type = Proxy.Type.HTTP;
        } else {
            throw new RelayException(HttpStatus.BAD_REQUEST, "Unsupported proxy protocol", "invalid_proxy");
        }
        ProxyAddress endpoint = parseProxyAddress(proxyItem.getAddress());
        if (type == Proxy.Type.SOCKS && endpoint.userInfo() != null) {
            throw new RelayException(HttpStatus.BAD_REQUEST, "SOCKS5 proxy auth is not supported", "invalid_proxy");
        }
        String proxyAuthorization = null;
        if (type == Proxy.Type.HTTP && endpoint.userInfo() != null) {
            proxyAuthorization = "Basic " + Base64.getEncoder()
                    .encodeToString(endpoint.userInfo().getBytes(StandardCharsets.UTF_8));
        }
        InetSocketAddress address = new InetSocketAddress(endpoint.host(), endpoint.port());
        return new ProxyRoute(new Proxy(type, address), proxyAuthorization);
    }

    ProxyAddress parseProxyAddress(String rawAddress) {
        String normalized = rawAddress == null ? "" : rawAddress.trim();
        if (normalized.isEmpty()) {
            throw new RelayException(HttpStatus.BAD_REQUEST, "Invalid proxy address", "invalid_proxy");
        }
        String uriText = normalized.contains("://") ? normalized : ("http://" + normalized);
        URI uri;
        try {
            uri = URI.create(uriText);
        } catch (Exception ex) {
            throw new RelayException(HttpStatus.BAD_REQUEST, "Invalid proxy address", "invalid_proxy");
        }
        String host = uri.getHost();
        int port = uri.getPort();
        if (host == null || host.trim().isEmpty() || port <= 0 || port > 65535) {
            throw new RelayException(HttpStatus.BAD_REQUEST, "Invalid proxy address", "invalid_proxy");
        }
        rejectPrivateHost(host);
        String userInfo = uri.getUserInfo();
        if (userInfo != null) {
            userInfo = userInfo.trim();
            if (userInfo.isEmpty()) {
                userInfo = null;
            }
        }
        return new ProxyAddress(host, port, userInfo);
    }

    /**
     * SSRF 防护——拒绝指向内网 / 回环 / 元数据服务的代理地址。
     */
    private void rejectPrivateHost(String host) {
        String lower = host.toLowerCase(Locale.ROOT);
        // 允许 127.0.0.1 用于本机 Mihomo 代理，拒绝其他私网/回环地址
        if ("127.0.0.1".equals(lower)) {
            return;
        }
        if ("localhost".equals(lower) || lower.endsWith(".local")) {
            throw new RelayException(HttpStatus.BAD_REQUEST, "Proxy to localhost is not allowed", "ssrf_blocked");
        }
        try {
            java.net.InetAddress addr = java.net.InetAddress.getByName(host);
            if (addr.isLoopbackAddress() || addr.isLinkLocalAddress()
                    || addr.isSiteLocalAddress() || addr.isAnyLocalAddress()) {
                throw new RelayException(HttpStatus.BAD_REQUEST, "Proxy to private network is not allowed", "ssrf_blocked");
            }
        } catch (java.net.UnknownHostException ignored) {
            // DNS 解析失败时让后续连接自然报错
        }
    }

    private String normalize(String value) {
        if (value == null) {
            return "";
        }
        return value.trim().toUpperCase(Locale.ROOT);
    }

    private void populateUsage(RelayResult result, String responseBody) {
        if (responseBody == null || responseBody.trim().isEmpty()) {
            return;
        }
        // 先尝试作为单个 JSON 解析（非流式响应）
        if (!responseBody.trim().startsWith("event:") && !responseBody.trim().startsWith("data:")) {
            try {
                JsonNode root = objectMapper.readTree(responseBody);
                extractUsageFromNode(result, root.get("usage"));
                return;
            } catch (Exception ignored) {
                // 不是有效 JSON，尝试 SSE 解析
            }
        }
        // SSE 流式响应：逐行扫描提取 usage
        extractUsageFromSse(result, responseBody);
    }

    /**
     * 从 SSE 事件流中提取 usage。
     * 支持 OpenAI Responses API（response.completed 事件含 usage）、
     * OpenAI Chat Completions（最后一个 chunk 含 usage）、
     * Claude Messages（message_start + message_delta 含 usage）。
     */
    void extractUsageFromSse(RelayResult result, String sseBody) {
        Integer promptTokens = null;
        Integer completionTokens = null;
        String usageJson = null;

        String[] lines = sseBody.split("\\r?\\n");
        for (String line : lines) {
            if (!line.startsWith("data:")) continue;
            String payload = line.substring("data:".length()).trim();
            if (payload.isEmpty() || "[DONE]".equals(payload)) continue;
            try {
                JsonNode node = objectMapper.readTree(payload);

                // --- OpenAI Responses API: response.completed 事件包含完整 response 对象 ---
                JsonNode responseUsage = node.path("response").path("usage");
                if (!responseUsage.isMissingNode()) {
                    Integer pt = readTokenField(responseUsage, "input_tokens", "prompt_tokens");
                    Integer ct = readTokenField(responseUsage, "output_tokens", "completion_tokens");
                    if (pt != null) promptTokens = pt;
                    if (ct != null) completionTokens = ct;
                    usageJson = responseUsage.toString();
                    continue;
                }

                // --- OpenAI Chat Completions: 最后一个 chunk 的顶层 usage ---
                JsonNode topUsage = node.get("usage");
                if (topUsage != null && !topUsage.isNull() && !topUsage.isMissingNode()) {
                    Integer pt = readTokenField(topUsage, "prompt_tokens", "input_tokens");
                    Integer ct = readTokenField(topUsage, "completion_tokens", "output_tokens");
                    if (pt != null) promptTokens = pt;
                    if (ct != null) completionTokens = ct;
                    usageJson = topUsage.toString();
                    continue;
                }

                // --- Claude: message_start.message.usage ---
                JsonNode msgUsage = node.path("message").path("usage");
                if (!msgUsage.isMissingNode()) {
                    Integer pt = readTokenField(msgUsage, "input_tokens", "prompt_tokens");
                    Integer ct = readTokenField(msgUsage, "output_tokens", "completion_tokens");
                    if (pt != null) promptTokens = pt;
                    if (ct != null) completionTokens = ct;
                    usageJson = msgUsage.toString();
                    continue;
                }

                // --- Claude: message_delta 顶层 usage ---
                String eventType = node.path("type").asText(null);
                if ("message_delta".equals(eventType)) {
                    JsonNode deltaUsage = node.get("usage");
                    if (deltaUsage != null && !deltaUsage.isNull()) {
                        Integer pt = readTokenField(deltaUsage, "input_tokens", "prompt_tokens");
                        Integer ct = readTokenField(deltaUsage, "output_tokens", "completion_tokens");
                        if (pt != null) promptTokens = pt;
                        if (ct != null) completionTokens = ct;
                        usageJson = deltaUsage.toString();
                    }
                }
            } catch (Exception ignored) {
                // 跳过无法解析的行
            }
        }

        if (promptTokens != null) result.setPromptTokens(promptTokens);
        if (completionTokens != null) result.setCompletionTokens(completionTokens);
        if (promptTokens != null && completionTokens != null) {
            result.setTotalTokens(promptTokens + completionTokens);
        }
        if (usageJson != null) result.setUsageJson(usageJson);
    }

    private void extractUsageFromNode(RelayResult result, JsonNode usage) {
        if (usage == null || usage.isNull()) return;
        Integer pt = readTokenField(usage, "prompt_tokens", "input_tokens");
        Integer ct = readTokenField(usage, "completion_tokens", "output_tokens");
        if (pt != null) result.setPromptTokens(pt);
        if (ct != null) result.setCompletionTokens(ct);
        if (usage.has("total_tokens")) {
            result.setTotalTokens(usage.get("total_tokens").asInt());
        } else if (pt != null && ct != null) {
            result.setTotalTokens(pt + ct);
        }
        result.setUsageJson(usage.toString());
    }

    /**
     * 读取 token 字段，优先使用 primaryField，备选 fallbackField。
     */
    private Integer readTokenField(JsonNode usage, String primaryField, String fallbackField) {
        if (usage.has(primaryField)) return usage.get(primaryField).asInt();
        if (usage.has(fallbackField)) return usage.get(fallbackField).asInt();
        return null;
    }

    private static final String[] COOLDOWN_HEADERS = {
            "retry-after",
            "x-ratelimit-limit-requests",
            "x-ratelimit-remaining-requests",
            "x-ratelimit-reset-requests",
            "x-ratelimit-limit-tokens",
            "x-ratelimit-remaining-tokens",
            "x-ratelimit-reset-tokens",
            "anthropic-ratelimit-requests-limit",
            "anthropic-ratelimit-requests-remaining",
            "anthropic-ratelimit-requests-reset",
            "anthropic-ratelimit-tokens-limit",
            "anthropic-ratelimit-tokens-remaining",
            "anthropic-ratelimit-tokens-reset",
            "anthropic-ratelimit-input-tokens-limit",
            "anthropic-ratelimit-input-tokens-remaining",
            "anthropic-ratelimit-input-tokens-reset",
            "anthropic-ratelimit-output-tokens-limit",
            "anthropic-ratelimit-output-tokens-remaining",
            "anthropic-ratelimit-output-tokens-reset"
    };

    private Map<String, String> captureCooldownHeaders(HttpURLConnection connection) {
        Map<String, String> captured = new LinkedHashMap<>();
        for (String name : COOLDOWN_HEADERS) {
            String value = connection.getHeaderField(name);
            if (value != null) {
                captured.put(name.toLowerCase(Locale.ROOT), value);
            }
        }
        return captured;
    }

    private String firstHeaderOrNull(HttpURLConnection connection, String... names) {
        for (String name : names) {
            String value = connection.getHeaderField(name);
            if (value != null) {
                return value;
            }
            value = connection.getHeaderField(name.toUpperCase());
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    private String readFully(InputStream inputStream) throws IOException {
        if (inputStream == null) {
            return "";
        }
        try {
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            byte[] buffer = new byte[4096];
            int read;
            while ((read = inputStream.read(buffer)) != -1) {
                outputStream.write(buffer, 0, read);
            }
            return new String(outputStream.toByteArray(), StandardCharsets.UTF_8);
        } finally {
            inputStream.close();
        }
    }

    record ProxyAddress(String host, int port, String userInfo) {}
    record ProxyRoute(Proxy proxy, String proxyAuthorization) {}
}
