package com.firstapi.backend.model;

public class RelayResult {
    private int statusCode;
    private String contentType;
    private String body;
    private String requestId;
    private boolean success;
    private long latencyMs;
    private Long accountId;
    private Integer promptTokens;
    private Integer completionTokens;
    private Integer totalTokens;
    private String provider;
    private String usageJson;
    private java.util.Map<String, String> responseHeaders = new java.util.LinkedHashMap<>();

    public int getStatusCode() {
        return statusCode;
    }

    public void setStatusCode(int statusCode) {
        this.statusCode = statusCode;
    }

    public String getContentType() {
        return contentType;
    }

    public void setContentType(String contentType) {
        this.contentType = contentType;
    }

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

    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }

    public long getLatencyMs() {
        return latencyMs;
    }

    public void setLatencyMs(long latencyMs) {
        this.latencyMs = latencyMs;
    }

    public Long getAccountId() {
        return accountId;
    }

    public void setAccountId(Long accountId) {
        this.accountId = accountId;
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

    public String getProvider() {
        return provider;
    }

    public void setProvider(String provider) {
        this.provider = provider;
    }

    public String getUsageJson() {
        return usageJson;
    }

    public void setUsageJson(String usageJson) {
        this.usageJson = usageJson;
    }

    public java.util.Map<String, String> getResponseHeaders() {
        return responseHeaders;
    }

    public void setResponseHeaders(java.util.Map<String, String> responseHeaders) {
        this.responseHeaders = responseHeaders != null ? responseHeaders : new java.util.LinkedHashMap<>();
    }

    public String getResponseHeader(String name) {
        if (responseHeaders == null || name == null) return null;
        String value = responseHeaders.get(name);
        if (value != null) return value;
        // case-insensitive fallback
        for (java.util.Map.Entry<String, String> entry : responseHeaders.entrySet()) {
            if (name.equalsIgnoreCase(entry.getKey())) return entry.getValue();
        }
        return null;
    }
}
