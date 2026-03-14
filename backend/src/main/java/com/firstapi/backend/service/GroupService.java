package com.firstapi.backend.service;

import com.firstapi.backend.common.PageResponse;
import com.firstapi.backend.model.GroupItem;
import com.firstapi.backend.repository.GroupRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class GroupService {

    private final GroupRepository repository;

    public GroupService(GroupRepository repository) {
        this.repository = repository;
    }

    public PageResponse<GroupItem> list(String keyword) {
        List<GroupItem> items = repository.findAll();
        if (!isBlank(keyword)) {
            items = items.stream()
                .filter(i -> contains(i.getName(), keyword)
                           || contains(i.getBillingType(), keyword))
                .collect(Collectors.toList());
        }
        return new PageResponse<GroupItem>(items);
    }

    public GroupItem get(Long id) {
        GroupItem item = repository.findById(id);
        if (item == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Group not found");
        }
        return item;
    }

    public GroupItem create(GroupItem.Request req) {
        GroupItem item = new GroupItem();
        item.setName(emptyAsDefault(req.getName(), ""));
        item.setBillingType(emptyAsDefault(req.getBillingType(), "按量计费"));
        item.setUserCount(emptyAsDefault(req.getUserCount(), "0"));
        item.setStatus(emptyAsDefault(req.getStatus(), "激活"));
        item.setPriority(emptyAsDefault(req.getPriority(), "10"));
        item.setRate(emptyAsDefault(req.getRate(), "1.0x"));
        return repository.save(item);
    }

    public GroupItem update(Long id, GroupItem.Request req) {
        GroupItem existing = repository.findById(id);
        if (existing == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Group not found");
        }
        if (req.getName() != null) existing.setName(req.getName());
        if (req.getBillingType() != null) existing.setBillingType(req.getBillingType());
        if (req.getUserCount() != null) existing.setUserCount(req.getUserCount());
        if (req.getStatus() != null) existing.setStatus(req.getStatus());
        if (req.getPriority() != null) existing.setPriority(req.getPriority());
        if (req.getRate() != null) existing.setRate(req.getRate());
        return repository.update(id, existing);
    }

    public void delete(Long id) {
        GroupItem existing = repository.findById(id);
        if (existing == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Group not found");
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
