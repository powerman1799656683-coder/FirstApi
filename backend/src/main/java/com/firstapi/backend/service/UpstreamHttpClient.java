package com.firstapi.backend.service;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import com.firstapi.backend.config.RelayProperties;
import com.firstapi.backend.model.IpItem;
import com.firstapi.backend.model.RelayException;
import com.firstapi.backend.model.RelayResult;
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
            throw new RelayException(HttpStatus.BAD_GATEWAY, "Upstream request failed", "upstream_error");
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    public RelayResult postJson(String url, Map<String, String> headers, JsonNode body, IpItem proxyItem) {
        return postJson(url, headers, body, proxyItem, relayProperties.getReadTimeoutMs());
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
            throw new RelayException(HttpStatus.BAD_GATEWAY, "Upstream request failed", "upstream_error");
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
        try {
            JsonNode root = objectMapper.readTree(responseBody);
            JsonNode usage = root.get("usage");
            if (usage == null || usage.isNull()) {
                return;
            }
            // OpenAI format: prompt_tokens / completion_tokens / total_tokens
            if (usage.has("prompt_tokens")) {
                result.setPromptTokens(usage.get("prompt_tokens").asInt());
            }
            if (usage.has("completion_tokens")) {
                result.setCompletionTokens(usage.get("completion_tokens").asInt());
            }
            if (usage.has("total_tokens")) {
                result.setTotalTokens(usage.get("total_tokens").asInt());
            }
            result.setUsageJson(usage.toString());
        } catch (Exception ignored) {
            // Leave token fields empty if the upstream body is not JSON.
        }
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
