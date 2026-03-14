package com.firstapi.backend.service;

import com.firstapi.backend.common.PageResponse;
import com.firstapi.backend.model.SubscriptionItem;
import com.firstapi.backend.repository.SubscriptionRepository;
import com.firstapi.backend.util.TimeSupport;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
public class SubscriptionService {

    private static final Pattern NUMBER_PATTERN = Pattern.compile("\\d+(?:\\.\\d+)?");

    private final SubscriptionRepository repository;

    public SubscriptionService(SubscriptionRepository repository) {
        this.repository = repository;
    }

    public PageResponse<SubscriptionItem> list(String keyword) {
        List<SubscriptionItem> items = repository.findAll();
        if (!isBlank(keyword)) {
            items = items.stream()
                .filter(i -> contains(i.getUser(), keyword)
                           || contains(i.getGroup(), keyword))
                .collect(Collectors.toList());
        }
        return new PageResponse<SubscriptionItem>(items);
    }

    public SubscriptionItem get(Long id) {
        SubscriptionItem item = repository.findById(id);
        if (item == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Subscription not found");
        }
        return item;
    }

    public SubscriptionItem create(SubscriptionItem.Request req) {
        SubscriptionItem item = new SubscriptionItem();
        item.setUser(emptyAsDefault(req.getUser(), "new-user@example.com"));
        item.setUid(req.getUid() != null ? req.getUid() : 0L);
        item.setGroup(emptyAsDefault(req.getGroup(), "Claude Max20"));
        item.setUsage(buildUsage(req.getUsage(), req.getQuota()));
        item.setProgress(req.getProgress() != null ? req.getProgress() : deriveProgress(item.getUsage()));
        item.setExpiry(emptyAsDefault(req.getExpiry(), TimeSupport.plusMonths(null, 1)));
        item.setStatus(emptyAsDefault(req.getStatus(), "正常"));
        return repository.save(item);
    }

    public SubscriptionItem update(Long id, SubscriptionItem.Request req) {
        SubscriptionItem existing = repository.findById(id);
        if (existing == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Subscription not found");
        }
        if (req.getUser() != null) existing.setUser(req.getUser());
        if (req.getUid() != null) existing.setUid(req.getUid());
        if (req.getGroup() != null) existing.setGroup(req.getGroup());
        if (req.getUsage() != null) {
            existing.setUsage(req.getUsage());
        } else if (!isBlank(req.getQuota())) {
            existing.setUsage(replaceQuota(existing.getUsage(), req.getQuota()));
        }
        if (req.getProgress() != null) {
            existing.setProgress(req.getProgress());
        } else if (req.getUsage() != null || !isBlank(req.getQuota())) {
            existing.setProgress(deriveProgress(existing.getUsage()));
        }
        if (req.getExpiry() != null) existing.setExpiry(req.getExpiry());
        if (req.getStatus() != null) existing.setStatus(req.getStatus());
        return repository.update(id, existing);
    }

    public void delete(Long id) {
        SubscriptionItem existing = repository.findById(id);
        if (existing == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Subscription not found");
        }
        repository.deleteById(id);
    }

    private String buildUsage(String usage, String quota) {
        if (!isBlank(usage)) {
            return usage;
        }
        if (!isBlank(quota)) {
            return "$0.00 / $" + quota;
        }
        return "$0.00 / $0.00";
    }

    private String replaceQuota(String usage, String quota) {
        String used = "$0.00";
        if (!isBlank(usage) && usage.contains("/")) {
            used = usage.split("/")[0].trim();
        }
        return used + " / $" + quota;
    }

    private Double deriveProgress(String usage) {
        if (isBlank(usage) || usage.contains("∞")) {
            return 0.0;
        }
        Matcher matcher = NUMBER_PATTERN.matcher(usage);
        if (!matcher.find()) {
            return 0.0;
        }
        double used = Double.parseDouble(matcher.group());
        if (!matcher.find()) {
            return 0.0;
        }
        double total = Double.parseDouble(matcher.group());
        if (total <= 0) {
            return 0.0;
        }
        return Math.min(100.0, Math.round((used / total) * 1000.0) / 10.0);
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
