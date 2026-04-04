package com.firstapi.backend.model;

import com.firstapi.backend.common.SimpleStore;

public class AccountOAuthSession implements SimpleStore.Identifiable {
    private Long id;
    private String sessionId;
    private String stateValue;
    private String platform;
    private String accountType;
    private String authMethod;
    private String codeVerifier;
    private String statusName;
    private String encryptedCredential;
    private String credentialMask;
    private String providerSubject;
    private String errorText;
    private String expiresAt;
    private String exchangedAt;
    private String consumedAt;
    private Long createdBy;
    private String encryptedRefreshToken;

    public AccountOAuthSession() {}

    public AccountOAuthSession(Long id, String sessionId, String stateValue, String platform,
                               String accountType, String authMethod, String codeVerifier, String statusName,
                               String encryptedCredential, String credentialMask, String providerSubject,
                               String errorText, String expiresAt, String exchangedAt, String consumedAt,
                               Long createdBy) {
        this.id = id;
        this.sessionId = sessionId;
        this.stateValue = stateValue;
        this.platform = platform;
        this.accountType = accountType;
        this.authMethod = authMethod;
        this.codeVerifier = codeVerifier;
        this.statusName = statusName;
        this.encryptedCredential = encryptedCredential;
        this.credentialMask = credentialMask;
        this.providerSubject = providerSubject;
        this.errorText = errorText;
        this.expiresAt = expiresAt;
        this.exchangedAt = exchangedAt;
        this.consumedAt = consumedAt;
        this.createdBy = createdBy;
    }

    @Override public Long getId() { return id; }
    @Override public void setId(Long id) { this.id = id; }
    public String getSessionId() { return sessionId; }
    public void setSessionId(String sessionId) { this.sessionId = sessionId; }
    public String getStateValue() { return stateValue; }
    public void setStateValue(String stateValue) { this.stateValue = stateValue; }
    public String getPlatform() { return platform; }
    public void setPlatform(String platform) { this.platform = platform; }
    public String getAccountType() { return accountType; }
    public void setAccountType(String accountType) { this.accountType = accountType; }
    public String getAuthMethod() { return authMethod; }
    public void setAuthMethod(String authMethod) { this.authMethod = authMethod; }
    public String getCodeVerifier() { return codeVerifier; }
    public void setCodeVerifier(String codeVerifier) { this.codeVerifier = codeVerifier; }
    public String getStatusName() { return statusName; }
    public void setStatusName(String statusName) { this.statusName = statusName; }
    public String getEncryptedCredential() { return encryptedCredential; }
    public void setEncryptedCredential(String encryptedCredential) { this.encryptedCredential = encryptedCredential; }
    public String getCredentialMask() { return credentialMask; }
    public void setCredentialMask(String credentialMask) { this.credentialMask = credentialMask; }
    public String getProviderSubject() { return providerSubject; }
    public void setProviderSubject(String providerSubject) { this.providerSubject = providerSubject; }
    public String getErrorText() { return errorText; }
    public void setErrorText(String errorText) { this.errorText = errorText; }
    public String getExpiresAt() { return expiresAt; }
    public void setExpiresAt(String expiresAt) { this.expiresAt = expiresAt; }
    public String getExchangedAt() { return exchangedAt; }
    public void setExchangedAt(String exchangedAt) { this.exchangedAt = exchangedAt; }
    public String getConsumedAt() { return consumedAt; }
    public void setConsumedAt(String consumedAt) { this.consumedAt = consumedAt; }
    public Long getCreatedBy() { return createdBy; }
    public void setCreatedBy(Long createdBy) { this.createdBy = createdBy; }
    public String getEncryptedRefreshToken() { return encryptedRefreshToken; }
    public void setEncryptedRefreshToken(String encryptedRefreshToken) { this.encryptedRefreshToken = encryptedRefreshToken; }
}
