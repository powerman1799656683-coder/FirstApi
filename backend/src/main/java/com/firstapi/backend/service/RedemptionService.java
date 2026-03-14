package com.firstapi.backend.service;

import com.firstapi.backend.common.PageResponse;
import com.firstapi.backend.model.RedemptionItem;
import com.firstapi.backend.repository.RedemptionRepository;
import com.firstapi.backend.util.TimeSupport;
import com.firstapi.backend.util.ValidationSupport;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class RedemptionService {

    private final RedemptionRepository repository;

    public RedemptionService(RedemptionRepository repository) {
        this.repository = repository;
    }

    public PageResponse<RedemptionItem> list(String keyword) {
        List<RedemptionItem> items = repository.findAll();
        if (!isBlank(keyword)) {
            items = items.stream()
                    .filter(item -> contains(item.getName(), keyword) || contains(item.getCode(), keyword))
                    .collect(Collectors.toList());
        }
        return new PageResponse<RedemptionItem>(items);
    }

    public RedemptionItem get(Long id) {
        RedemptionItem item = repository.findById(id);
        if (item == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Redemption not found");
        }
        return item;
    }

    public List<RedemptionItem> create(RedemptionItem.Request request) {
        String name = ValidationSupport.requireNotBlank(request.getName(), "Redemption name is required");
        String value = ValidationSupport.requireNotBlank(request.getValue(), "Redemption value is required");
        int quantity = request.getQuantity() == null ? 1 : ValidationSupport.requirePositive(request.getQuantity(), "Quantity must be greater than 0");
        int validCount = request.getValidCount() == null ? 1 : ValidationSupport.requirePositive(request.getValidCount(), "Usage limit must be greater than 0");

        List<RedemptionItem> created = new ArrayList<RedemptionItem>();
        for (int i = 0; i < quantity; i++) {
            RedemptionItem item = new RedemptionItem();
            item.setName(name);
            item.setCode(generateCode());
            item.setType(emptyAsDefault(request.getType(), "余额充值"));
            item.setValue(value);
            item.setUsage("0 / " + validCount);
            item.setTime(emptyAsDefault(request.getTime(), TimeSupport.nowDateTime()));
            item.setStatus(emptyAsDefault(request.getStatus(), "未使用"));
            created.add(repository.save(item));
        }
        return created;
    }

    public RedemptionItem update(Long id, RedemptionItem.Request request) {
        RedemptionItem current = get(id);
        if (request.getName() != null) current.setName(ValidationSupport.requireNotBlank(request.getName(), "Redemption name is required"));
        if (request.getType() != null) current.setType(request.getType());
        if (request.getValue() != null) current.setValue(ValidationSupport.requireNotBlank(request.getValue(), "Redemption value is required"));
        if (request.getTime() != null) current.setTime(request.getTime());
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