package com.firstapi.backend.service;

import com.firstapi.backend.common.CurrentSessionHolder;
import com.firstapi.backend.common.PageResponse;
import com.firstapi.backend.model.ApiKeyItem;
import com.firstapi.backend.model.AuthenticatedUser;
import com.firstapi.backend.repository.MyApiKeysRepository;
import com.firstapi.backend.util.TimeSupport;
import com.firstapi.backend.util.ValidationSupport;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class MyApiKeysService {

    private final MyApiKeysRepository repository;
    private final SensitiveDataService sensitiveDataService;

    public MyApiKeysService(MyApiKeysRepository repository, SensitiveDataService sensitiveDataService) {
        this.repository = repository;
        this.sensitiveDataService = sensitiveDataService;
    }

    public PageResponse<ApiKeyItem> list(String keyword) {
        AuthenticatedUser user = CurrentSessionHolder.require();
        List<ApiKeyItem> items = repository.findAllByOwnerId(user.getId());
        if (!isBlank(keyword)) {
            items = items.stream()
                    .filter(item -> contains(item.getName(), keyword))
                    .collect(Collectors.toList());
        }
        return new PageResponse<ApiKeyItem>(items.stream().map(item -> toResponse(item, false)).collect(Collectors.toList()));
    }

    public ApiKeyItem get(Long id) {
        return toResponse(requireOwned(id), false);
    }

    public ApiKeyItem create(ApiKeyItem.Request request) {
        AuthenticatedUser user = CurrentSessionHolder.require();
        ApiKeyItem item = new ApiKeyItem();
        item.setOwnerId(user.getId());
        item.setName(ValidationSupport.requireNotBlank(request.getName(), "API key name is required"));
        item.setKey(sensitiveDataService.protect(generateKey()));
        item.setCreated(TimeSupport.nowDateTime());
        item.setStatus(emptyAsDefault(request.getStatus(), "正常"));
        item.setLastUsed("-");
        repository.save(item);
        return toResponse(item, true);
    }

    public ApiKeyItem update(Long id, ApiKeyItem.Request request) {
        ApiKeyItem current = requireOwned(id);
        if (request.getName() != null) {
            current.setName(ValidationSupport.requireNotBlank(request.getName(), "API key name is required"));
        }
        if (request.getStatus() != null) {
            current.setStatus(request.getStatus());
        }
        repository.update(current);
        return toResponse(current, false);
    }

    public void delete(Long id) {
        AuthenticatedUser user = CurrentSessionHolder.require();
        requireOwned(id);
        repository.deleteByIdAndOwnerId(id, user.getId());
    }

    public ApiKeyItem rotateKey(Long id) {
        ApiKeyItem item = requireOwned(id);
        item.setKey(sensitiveDataService.protect(generateKey()));
        item.setLastUsed("-");
        repository.update(item);
        return toResponse(item, true);
    }

    private ApiKeyItem requireOwned(Long id) {
        AuthenticatedUser user = CurrentSessionHolder.require();
        ApiKeyItem item = repository.findByIdAndOwnerId(id, user.getId());
        if (item == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Api key not found");
        }
        return item;
    }

    private ApiKeyItem toResponse(ApiKeyItem source, boolean revealFullKey) {
        String plainTextKey = sensitiveDataService.reveal(source.getKey());
        ApiKeyItem response = new ApiKeyItem();
        response.setId(source.getId());
        response.setOwnerId(source.getOwnerId());
        response.setName(source.getName());
        response.setCreated(source.getCreated());
        response.setStatus(source.getStatus());
        response.setLastUsed(source.getLastUsed());
        response.setKeyPreview(maskKey(plainTextKey));
        if (revealFullKey) {
            response.setPlainTextKey(plainTextKey);
        }
        return response;
    }

    private String generateKey() {
        return "sk-yc-" + UUID.randomUUID().toString().replace("-", "").substring(0, 20).toLowerCase(Locale.ROOT);
    }

    private String maskKey(String key) {
        if (key == null || key.length() < 12) {
            return "********";
        }
        return key.substring(0, 8) + "..." + key.substring(key.length() - 4);
    }

    private boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }

    private boolean contains(String source, String keyword) {
        if (source == null || keyword == null) {
            return false;
        }
        return source.toLowerCase().contains(keyword.toLowerCase());
    }

    private String emptyAsDefault(String value, String defaultValue) {
        return isBlank(value) ? defaultValue : value;
    }
}
