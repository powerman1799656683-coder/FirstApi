package com.firstapi.backend.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "app.relay")
public class RelayProperties {
    private String openaiBaseUrl = "https://api.openai.com";
    private String claudeBaseUrl = "https://api.anthropic.com";
    private String anthropicVersion = "2023-06-01";
    private int connectTimeoutMs = 30000;
    private int readTimeoutMs = 300000;
    /** 流式请求专用读取超时，默认 300 秒（大模型长回复可能需要更长时间） */
    private int streamReadTimeoutMs = 300000;
    private boolean probeEnabled = false;
    private int probeReadTimeoutMs = 10000;
    private int probeIntervalMs = 10000;
    private String probeOpenaiModel = "gpt-4o-mini";
    private String probeClaudeModel = "claude-haiku-4-5-20251001";
    private String oauthBetaHeader = "oauth-2025-04-20";

    public String getOauthBetaHeader() {
        return oauthBetaHeader;
    }

    public void setOauthBetaHeader(String oauthBetaHeader) {
        this.oauthBetaHeader = oauthBetaHeader;
    }

    public String getOpenaiBaseUrl() {
        return openaiBaseUrl;
    }

    public void setOpenaiBaseUrl(String openaiBaseUrl) {
        this.openaiBaseUrl = openaiBaseUrl;
    }

    public String getClaudeBaseUrl() {
        return claudeBaseUrl;
    }

    public void setClaudeBaseUrl(String claudeBaseUrl) {
        this.claudeBaseUrl = claudeBaseUrl;
    }

    public String getAnthropicVersion() {
        return anthropicVersion;
    }

    public void setAnthropicVersion(String anthropicVersion) {
        this.anthropicVersion = anthropicVersion;
    }

    public int getConnectTimeoutMs() {
        return connectTimeoutMs;
    }

    public void setConnectTimeoutMs(int connectTimeoutMs) {
        this.connectTimeoutMs = connectTimeoutMs;
    }

    public int getReadTimeoutMs() {
        return readTimeoutMs;
    }

    public void setReadTimeoutMs(int readTimeoutMs) {
        this.readTimeoutMs = readTimeoutMs;
    }

    public int getStreamReadTimeoutMs() {
        return streamReadTimeoutMs;
    }

    public void setStreamReadTimeoutMs(int streamReadTimeoutMs) {
        this.streamReadTimeoutMs = streamReadTimeoutMs;
    }

    public boolean isProbeEnabled() {
        return probeEnabled;
    }

    public void setProbeEnabled(boolean probeEnabled) {
        this.probeEnabled = probeEnabled;
    }

    public int getProbeReadTimeoutMs() {
        return probeReadTimeoutMs;
    }

    public void setProbeReadTimeoutMs(int probeReadTimeoutMs) {
        this.probeReadTimeoutMs = probeReadTimeoutMs;
    }

    public int getProbeIntervalMs() {
        return probeIntervalMs;
    }

    public void setProbeIntervalMs(int probeIntervalMs) {
        this.probeIntervalMs = probeIntervalMs;
    }

    public String getProbeOpenaiModel() {
        return probeOpenaiModel;
    }

    public void setProbeOpenaiModel(String probeOpenaiModel) {
        this.probeOpenaiModel = probeOpenaiModel;
    }

    public String getProbeClaudeModel() {
        return probeClaudeModel;
    }

    public void setProbeClaudeModel(String probeClaudeModel) {
        this.probeClaudeModel = probeClaudeModel;
    }
}
