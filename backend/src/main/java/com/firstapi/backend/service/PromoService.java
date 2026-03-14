package com.firstapi.backend.service;

import com.firstapi.backend.common.PageResponse;
import com.firstapi.backend.model.PromoItem;
import com.firstapi.backend.repository.PromoRepository;
import com.firstapi.backend.util.ValidationSupport;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class PromoService {

    private final PromoRepository repository;

    public PromoService(PromoRepository repository) {
        this.repository = repository;
    }

    public PageResponse<PromoItem> list(String keyword) {
        List<PromoItem> items = repository.findAll();
        if (!isBlank(keyword)) {
            items = items.stream()
                    .filter(item -> contains(item.getCode(), keyword) || contains(item.getType(), keyword))
                    .collect(Collectors.toList());
        }
        return new PageResponse<PromoItem>(items);
    }

    public PromoItem get(Long id) {
        PromoItem item = repository.findById(id);
        if (item == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Promo not found");
        }
        return item;
    }

    public PromoItem create(PromoItem.Request request) {
        String code = ValidationSupport.trimToNull(request.getCode());
        if (code != null) {
            assertUniqueCode(code, null);
        }

        PromoItem item = new PromoItem();
        item.setCode(code == null ? generateCode() : code);
        item.setType(emptyAsDefault(request.getType(), "注册奖励"));
        item.setValue(ValidationSupport.requireNotBlank(request.getValue(), "Promo value is required"));
        item.setUsage(emptyAsDefault(request.getUsage(), "0 / 100"));
        item.setExpiry(emptyAsDefault(request.getExpiry(), "2026/12/31"));
        item.setStatus(emptyAsDefault(request.getStatus(), "进行中"));
        return repository.save(item);
    }

    public PromoItem update(Long id, PromoItem.Request request) {
        PromoItem current = get(id);
        if (request.getCode() != null) {
            String code = ValidationSupport.requireNotBlank(request.getCode(), "Promo code is required");
            assertUniqueCode(code, id);
            current.setCode(code);
        }
        if (request.getType() != null) current.setType(request.getType());
        if (request.getValue() != null) current.setValue(ValidationSupport.requireNotBlank(request.getValue(), "Promo value is required"));
        if (request.getUsage() != null) current.setUsage(request.getUsage());
        if (request.getExpiry() != null) current.setExpiry(request.getExpiry());
        if (request.getStatus() != null) current.setStatus(request.getStatus());
        return repository.update(id, current);
    }

    public void delete(Long id) {
        get(id);
        repository.deleteById(id);
    }

    private String generateCode() {
        return UUID.randomUUID().toString().substring(0, 8).toUpperCase(Locale.ROOT);
    }

    private void assertUniqueCode(String code, Long currentId) {
        boolean exists = repository.findAll().stream()
                .anyMatch(item -> !item.getId().equals(currentId) && code.equalsIgnoreCase(item.getCode()));
        if (exists) {
            throw new IllegalArgumentException("Promo code already exists");
        }
    }

    private boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }

    private boolean contains(String source, String keyword) {
        if (source == null || keyword == null) return false;
        return source.toLowerCase().contains(keyword.toLowerCase());
    }

    private String emptyAsDefault(String value, String defaultValue) {
        return isBlank(value) ? defaultValue : value;
    }
}