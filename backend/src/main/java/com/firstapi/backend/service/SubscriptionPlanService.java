package com.firstapi.backend.service;

import com.firstapi.backend.common.PageResponse;
import com.firstapi.backend.model.SubscriptionPlanItem;
import com.firstapi.backend.repository.SubscriptionPlanRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class SubscriptionPlanService {

    private final SubscriptionPlanRepository repository;

    public SubscriptionPlanService(SubscriptionPlanRepository repository) {
        this.repository = repository;
    }

    public PageResponse<SubscriptionPlanItem> list(String keyword) {
        List<SubscriptionPlanItem> items = repository.findAll();
        if (keyword != null && !keyword.isBlank()) {
            String kw = keyword.toLowerCase();
            items = items.stream()
                    .filter(i -> (i.getName() != null && i.getName().toLowerCase().contains(kw))
                               || (i.getStatus() != null && i.getStatus().toLowerCase().contains(kw)))
                    .collect(Collectors.toList());
        }
        return new PageResponse<>(items);
    }

    public SubscriptionPlanItem get(Long id) {
        SubscriptionPlanItem item = repository.findById(id);
        if (item == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "订阅等级不存在");
        }
        return item;
    }

    public SubscriptionPlanItem create(SubscriptionPlanItem.Request req) {
        if (req.getName() == null || req.getName().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "等级名称不能为空");
        }
        if (req.getMonthlyQuota() == null || req.getMonthlyQuota().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "每月配额不能为空");
        }
        SubscriptionPlanItem item = new SubscriptionPlanItem();
        item.setName(req.getName().trim());
        item.setMonthlyQuota(req.getMonthlyQuota().trim());
        item.setDailyLimit(req.getDailyLimit() == null || req.getDailyLimit().isBlank() ? null : req.getDailyLimit().trim());
        item.setStatus(req.getStatus() == null || req.getStatus().isBlank() ? "正常" : req.getStatus().trim());
        return repository.save(item);
    }

    public SubscriptionPlanItem update(Long id, SubscriptionPlanItem.Request req) {
        SubscriptionPlanItem existing = repository.findById(id);
        if (existing == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "订阅等级不存在");
        }
        if (req.getName() != null) existing.setName(req.getName().trim());
        if (req.getMonthlyQuota() != null) existing.setMonthlyQuota(req.getMonthlyQuota().trim());
        if (req.getDailyLimit() != null) existing.setDailyLimit(req.getDailyLimit().isBlank() ? null : req.getDailyLimit().trim());
        if (req.getStatus() != null) existing.setStatus(req.getStatus().trim());
        return repository.update(id, existing);
    }

    public void delete(Long id) {
        SubscriptionPlanItem existing = repository.findById(id);
        if (existing == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "订阅等级不存在");
        }
        repository.deleteById(id);
    }

    /**
     * 返回所有状态为"正常"的等级列表（供订阅管理下拉使用）。
     */
    public List<SubscriptionPlanItem> listActive() {
        return repository.findActiveList();
    }
}
