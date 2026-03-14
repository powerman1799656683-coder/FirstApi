package com.firstapi.backend.service;

import com.firstapi.backend.common.PageResponse;
import com.firstapi.backend.model.AccountItem;
import com.firstapi.backend.repository.AccountRepository;
import com.firstapi.backend.util.TimeSupport;
import com.firstapi.backend.util.ValidationSupport;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class AccountService {

    private final AccountRepository repository;
    private final SensitiveDataService sensitiveDataService;

    public AccountService(AccountRepository repository, SensitiveDataService sensitiveDataService) {
        this.repository = repository;
        this.sensitiveDataService = sensitiveDataService;
    }

    public PageResponse<AccountItem> list(String keyword) {
        List<AccountItem> items = repository.findAll();
        if (!isBlank(keyword)) {
            items = items.stream()
                    .filter(i -> contains(i.getName(), keyword)
                            || contains(i.getPlatform(), keyword)
                            || contains(String.valueOf(i.getId()), keyword))
                    .collect(Collectors.toList());
        }
        return new PageResponse<AccountItem>(items);
    }

    public AccountItem get(Long id) {
        AccountItem item = repository.findById(id);
        if (item == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Account not found");
        }
        return item;
    }

    public AccountItem create(AccountItem.Request req) {
        AccountItem item = new AccountItem();
        item.setName(ValidationSupport.requireNotBlank(req.getName(), "Account name is required"));
        item.setPlatform(ValidationSupport.requireNotBlank(req.getPlatform(), "Platform is required"));
        item.setType(emptyAsDefault(req.getType(), "API Key"));
        item.setUsage(emptyAsDefault(req.getUsage(), "$0.00"));
        item.setStatus(emptyAsDefault(req.getStatus(), "正常"));
        item.setErrors(req.getErrors() != null ? req.getErrors() : 0);
        item.setLastCheck(emptyAsDefault(req.getLastCheck(), TimeSupport.nowDateTime()));
        item.setCredential(sensitiveDataService.protect(ValidationSupport.requireNotBlank(req.getCredential(), "Credential is required")));
        return repository.save(item);
    }

    public AccountItem update(Long id, AccountItem.Request req) {
        AccountItem existing = repository.findById(id);
        if (existing == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Account not found");
        }
        if (req.getName() != null) {
            existing.setName(ValidationSupport.requireNotBlank(req.getName(), "Account name is required"));
        }
        if (req.getPlatform() != null) {
            existing.setPlatform(ValidationSupport.requireNotBlank(req.getPlatform(), "Platform is required"));
        }
        if (req.getType() != null) {
            existing.setType(req.getType());
        }
        if (req.getUsage() != null) {
            existing.setUsage(req.getUsage());
        }
        if (req.getStatus() != null) {
            existing.setStatus(req.getStatus());
        }
        if (req.getErrors() != null) {
            existing.setErrors(req.getErrors());
        }
        if (req.getLastCheck() != null) {
            existing.setLastCheck(req.getLastCheck());
        }
        if (!ValidationSupport.isBlank(req.getCredential())) {
            existing.setCredential(sensitiveDataService.protect(req.getCredential().trim()));
        }
        return repository.update(id, existing);
    }

    public void delete(Long id) {
        AccountItem existing = repository.findById(id);
        if (existing == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Account not found");
        }
        repository.deleteById(id);
    }

    public AccountItem test(Long id) {
        AccountItem existing = repository.findById(id);
        if (existing == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Account not found");
        }
        existing.setStatus("正常");
        existing.setLastCheck(TimeSupport.nowDateTime());
        existing.setErrors(0);
        return repository.update(id, existing);
    }

    private boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }

    private boolean contains(String value, String keyword) {
        if (value == null || keyword == null) {
            return false;
        }
        return value.toLowerCase().contains(keyword.toLowerCase());
    }

    private String emptyAsDefault(String value, String defaultValue) {
        return isBlank(value) ? defaultValue : value;
    }
}
