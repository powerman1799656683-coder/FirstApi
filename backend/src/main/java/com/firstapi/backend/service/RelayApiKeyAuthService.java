package com.firstapi.backend.service;

import com.firstapi.backend.model.ApiKeyItem;
import com.firstapi.backend.model.RelayException;
import com.firstapi.backend.repository.MyApiKeysRepository;
import com.firstapi.backend.util.TimeSupport;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class RelayApiKeyAuthService {

    private static final String STATUS_ACTIVE = "\u6b63\u5e38";

    private final MyApiKeysRepository repository;

    public RelayApiKeyAuthService(MyApiKeysRepository repository) {
        this.repository = repository;
    }

    public ApiKeyItem authenticate(String authorizationHeader) {
        return authenticateToken(extractBearerToken(authorizationHeader));
    }

    public ApiKeyItem authenticateFlexible(String authorizationHeader, String xApiKeyHeader) {
        return authenticateToken(extractFlexibleToken(authorizationHeader, xApiKeyHeader));
    }

    private ApiKeyItem authenticateToken(String token) {
        ApiKeyItem item = repository.findByPlainTextKey(token);
        if (item == null || !STATUS_ACTIVE.equals(normalizeStatus(item.getStatus()))) {
            throw new RelayException(HttpStatus.UNAUTHORIZED, "Invalid API key", "invalid_api_key");
        }
        String lastUsed = TimeSupport.nowDateTime();
        repository.touchLastUsed(item.getId(), item.getOwnerId(), lastUsed);
        item.setLastUsed(lastUsed);
        return item;
    }

    private String extractFlexibleToken(String authorizationHeader, String xApiKeyHeader) {
        if (xApiKeyHeader != null && !xApiKeyHeader.trim().isEmpty()) {
            return xApiKeyHeader.trim();
        }
        return extractBearerToken(authorizationHeader);
    }

    private String extractBearerToken(String authorizationHeader) {
        if (authorizationHeader == null) {
            throw new RelayException(HttpStatus.UNAUTHORIZED, "Missing API key", "invalid_api_key");
        }
        String prefix = "Bearer ";
        if (!authorizationHeader.startsWith(prefix) || authorizationHeader.length() <= prefix.length()) {
            throw new RelayException(HttpStatus.UNAUTHORIZED, "Invalid API key", "invalid_api_key");
        }
        return authorizationHeader.substring(prefix.length()).trim();
    }

    private String normalizeStatus(String status) {
        return status == null ? "" : status.trim();
    }
}
