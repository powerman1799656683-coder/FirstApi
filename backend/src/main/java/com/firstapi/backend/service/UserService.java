package com.firstapi.backend.service;

import com.firstapi.backend.common.PageResponse;
import com.firstapi.backend.model.UserItem;
import com.firstapi.backend.repository.UserRepository;
import com.firstapi.backend.util.TimeSupport;
import com.firstapi.backend.util.ValidationSupport;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class UserService {

    private final UserRepository repository;

    public UserService(UserRepository repository) {
        this.repository = repository;
    }

    public PageResponse<UserItem> list(String keyword) {
        List<UserItem> items = repository.findAll();
        if (!isBlank(keyword)) {
            items = items.stream()
                .filter(i -> contains(i.getEmail(), keyword)
                           || contains(i.getUsername(), keyword)
                           || contains(String.valueOf(i.getId()), keyword))
                .collect(Collectors.toList());
        }
        return new PageResponse<UserItem>(items);
    }

    public UserItem get(Long id) {
        UserItem item = repository.findById(id);
        if (item == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found");
        }
        return item;
    }

    public UserItem create(UserItem.Request req) {
        UserItem item = new UserItem();
        item.setEmail(ValidationSupport.requireNotBlank(req.getEmail(), "Email is required"));
        item.setUsername(ValidationSupport.requireNotBlank(req.getUsername(), "Username is required"));
        item.setBalance(emptyAsDefault(req.getBalance(), "$0.00"));
        item.setGroup(emptyAsDefault(req.getGroup(), "Default"));
        item.setRole(emptyAsDefault(req.getRole(), "用户"));
        item.setStatus(emptyAsDefault(req.getStatus(), "正常"));
        item.setTime(emptyAsDefault(req.getTime(), TimeSupport.today()));
        return repository.save(item);
    }

    public UserItem update(Long id, UserItem.Request req) {
        UserItem existing = repository.findById(id);
        if (existing == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found");
        }
        if (req.getEmail() != null) existing.setEmail(ValidationSupport.requireNotBlank(req.getEmail(), "Email is required"));
        if (req.getUsername() != null) existing.setUsername(ValidationSupport.requireNotBlank(req.getUsername(), "Username is required"));
        if (req.getBalance() != null) existing.setBalance(req.getBalance());
        if (req.getGroup() != null) existing.setGroup(req.getGroup());
        if (req.getRole() != null) existing.setRole(req.getRole());
        if (req.getStatus() != null) existing.setStatus(req.getStatus());
        if (req.getTime() != null) existing.setTime(req.getTime());
        return repository.update(id, existing);
    }

    public void delete(Long id) {
        UserItem existing = repository.findById(id);
        if (existing == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found");
        }
        repository.deleteById(id);
    }

    private boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }

    private boolean contains(String value, String keyword) {
        if (value == null || keyword == null) return false;
        return value.toLowerCase().contains(keyword.toLowerCase());
    }

    private String emptyAsDefault(String value, String defaultValue) {
        return isBlank(value) ? defaultValue : value;
    }
}