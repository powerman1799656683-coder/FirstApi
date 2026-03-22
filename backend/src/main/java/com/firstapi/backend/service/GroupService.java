package com.firstapi.backend.service;

import com.firstapi.backend.common.PageResponse;
import com.firstapi.backend.model.AccountItem;
import com.firstapi.backend.model.GroupItem;
import com.firstapi.backend.repository.AccountRepository;
import com.firstapi.backend.repository.GroupRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class GroupService {

    private final GroupRepository repository;
    private final AccountRepository accountRepository;

    public GroupService(GroupRepository repository,
                        AccountRepository accountRepository) {
        this.repository = repository;
        this.accountRepository = accountRepository;
    }

    public PageResponse<GroupItem> list(String keyword, String platform, String status, String groupType) {
        List<GroupItem> items = repository.findAll();
        if (!isBlank(keyword)) {
            items = items.stream()
                    .filter(i -> contains(i.getName(), keyword)
                            || contains(i.getBillingType(), keyword)
                            || contains(i.getPlatform(), keyword)
                            || contains(i.getAccountType(), keyword))
                    .collect(Collectors.toList());
        }
        if (!isBlank(platform)) {
            items = items.stream()
                    .filter(i -> platform.equals(i.getPlatform()))
                    .collect(Collectors.toList());
        }
        if (!isBlank(status)) {
            items = items.stream()
                    .filter(i -> status.equals(i.getStatus()))
                    .collect(Collectors.toList());
        }
        if (!isBlank(groupType)) {
            items = items.stream()
                    .filter(i -> groupType.equals(i.getGroupType()))
                    .collect(Collectors.toList());
        }
        enrichLevelMetadata(items);
        return new PageResponse<GroupItem>(items);
    }

    public GroupItem get(Long id) {
        GroupItem item = repository.findById(id);
        if (item == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "分组不存在");
        }
        enrichLevelMetadata(Collections.singletonList(item));
        return item;
    }

    public GroupItem create(GroupItem.Request req) {
        GroupItem item = new GroupItem();
        item.setName(requireNotBlank(req.getName(), "等级名称不能为空"));
        item.setDescription(req.getDescription());
        String platform = requireNotBlank(req.getPlatform(), "平台不能为空");
        item.setPlatform(platform);
        item.setAccountType(requireValidAccountType(platform, req.getAccountType()));
        item.setBillingType(emptyAsDefault(req.getBillingType(), "标准（余额）"));
        item.setBillingAmount(req.getBillingAmount());
        item.setRate(requireValidRate(req.getRate()));
        item.setGroupType(emptyAsDefault(req.getGroupType(), "公开"));
        item.setAccountCount("0个账号");
        item.setStatus(emptyAsDefault(req.getStatus(), "正常"));
        item.setClaudeCodeLimit(req.getClaudeCodeLimit() != null && req.getClaudeCodeLimit());
        item.setFallbackGroup(serializeFallbackGroups(req.getFallbackGroupIds(), req.getFallbackGroup()));
        item.setModelRouting(req.getModelRouting() != null && req.getModelRouting());
        GroupItem saved = repository.save(item);
        enrichLevelMetadata(Collections.singletonList(saved));
        return saved;
    }

    public GroupItem update(Long id, GroupItem.Request req) {
        GroupItem existing = repository.findById(id);
        if (existing == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "分组不存在");
        }
        if (req.getName() != null) existing.setName(req.getName());
        if (req.getDescription() != null) existing.setDescription(req.getDescription());
        // platform is immutable after creation.
        if (isBlank(existing.getAccountType())) {
            existing.setAccountType(PlatformAccountTypeCatalog.defaultAccountType(existing.getPlatform()));
        }
        if (req.getAccountType() != null) {
            existing.setAccountType(requireValidAccountType(existing.getPlatform(), req.getAccountType()));
        }
        if (req.getBillingType() != null) existing.setBillingType(req.getBillingType());
        if (req.getBillingAmount() != null) existing.setBillingAmount(req.getBillingAmount());
        if (req.getRate() != null) existing.setRate(requireValidRate(req.getRate()));
        if (req.getGroupType() != null) existing.setGroupType(req.getGroupType());
        if (req.getStatus() != null) existing.setStatus(req.getStatus());
        if (req.getClaudeCodeLimit() != null) existing.setClaudeCodeLimit(req.getClaudeCodeLimit());
        if (req.getFallbackGroupIds() != null || req.getFallbackGroup() != null) {
            existing.setFallbackGroup(serializeFallbackGroups(req.getFallbackGroupIds(), req.getFallbackGroup()));
        }
        if (req.getModelRouting() != null) existing.setModelRouting(req.getModelRouting());
        GroupItem updated = repository.update(id, existing);
        enrichLevelMetadata(Collections.singletonList(updated));
        return updated;
    }

    public void delete(Long id) {
        GroupItem existing = repository.findById(id);
        if (existing == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "分组不存在");
        }
        repository.deleteById(id);
    }

    private boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }

    private boolean contains(String value, String keyword) {
        if (value == null || keyword == null) return false;
        return value.toLowerCase(Locale.ROOT).contains(keyword.toLowerCase(Locale.ROOT));
    }

    private String emptyAsDefault(String value, String defaultValue) {
        return isBlank(value) ? defaultValue : value;
    }

    private String requireValidRate(String rate) {
        if (isBlank(rate)) {
            return "1";
        }
        try {
            java.math.BigDecimal value = new java.math.BigDecimal(rate.trim());
            if (value.compareTo(java.math.BigDecimal.ZERO) < 0) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "分组倍率不能为负数");
            }
            return rate.trim();
        } catch (NumberFormatException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "分组倍率必须为有效数字");
        }
    }

    private String requireNotBlank(String value, String message) {
        if (isBlank(value)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
        }
        return value.trim();
    }

    private void enrichLevelMetadata(List<GroupItem> items) {
        if (items == null || items.isEmpty()) {
            return;
        }

        List<GroupItem> allGroups = repository.findAll();
        Map<String, Long> idByName = new HashMap<>();
        for (GroupItem item : allGroups) {
            if (item == null || item.getId() == null || isBlank(item.getName())) {
                continue;
            }
            idByName.put(item.getName().trim().toLowerCase(Locale.ROOT), item.getId());
        }

        Map<String, Integer> countsByAccountType = new HashMap<>();
        List<AccountItem> allAccounts = accountRepository.findAll();
        for (AccountItem account : allAccounts) {
            if (account == null) {
                continue;
            }
            String key = buildAccountTypeKey(account.getPlatform(), account.getAccountType());
            if (isBlank(key)) {
                continue;
            }
            countsByAccountType.merge(key, 1, Integer::sum);
        }

        for (GroupItem item : items) {
            if (item == null) {
                continue;
            }
            if (isBlank(item.getAccountType())) {
                item.setAccountType(PlatformAccountTypeCatalog.defaultAccountType(item.getPlatform()));
            }
            item.setFallbackGroupIds(parseFallbackGroups(item.getFallbackGroup(), idByName));
            String key = buildAccountTypeKey(item.getPlatform(), item.getAccountType());
            int accountTotal = isBlank(key) ? 0 : countsByAccountType.getOrDefault(key, 0);
            item.setAccountTotal(accountTotal);
            item.setAccountCount(accountTotal + "个账号");
        }
    }

    private String serializeFallbackGroups(List<Long> fallbackGroupIds, String fallbackGroup) {
        if (fallbackGroupIds != null) {
            List<String> values = fallbackGroupIds.stream()
                    .filter(id -> id != null && id > 0)
                    .map(String::valueOf)
                    .collect(Collectors.toList());
            if (!values.isEmpty()) {
                return String.join(",", values);
            }
            return null;
        }
        if (isBlank(fallbackGroup)) {
            return null;
        }
        return fallbackGroup.trim();
    }

    private List<Long> parseFallbackGroups(String fallbackGroup, Map<String, Long> idByName) {
        if (isBlank(fallbackGroup)) {
            return new ArrayList<>();
        }
        String[] tokens = fallbackGroup.split("[,，]");
        LinkedHashSet<Long> result = new LinkedHashSet<>();
        for (String token : tokens) {
            if (token == null) {
                continue;
            }
            String trimmed = token.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            try {
                long id = Long.parseLong(trimmed);
                if (id > 0) {
                    result.add(id);
                    continue;
                }
            } catch (NumberFormatException ignored) {
                // Try name mapping below.
            }
            Long byName = idByName.get(trimmed.toLowerCase(Locale.ROOT));
            if (byName != null) {
                result.add(byName);
            }
        }
        return new ArrayList<>(result);
    }

    private String requireValidAccountType(String platform, String accountType) {
        if (isBlank(accountType)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "accountType 不能为空");
        }
        String resolved = PlatformAccountTypeCatalog.resolveAllowedAccountType(platform, accountType);
        if (resolved == null) {
            List<String> allowed = PlatformAccountTypeCatalog.allowedAccountTypes(platform);
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "accountType 非法，平台 " + emptyAsDefault(platform, "-") + " 仅支持: " + String.join(", ", allowed)
            );
        }
        return resolved;
    }

    private String buildAccountTypeKey(String platform, String accountType) {
        if (isBlank(platform) || isBlank(accountType)) {
            return null;
        }
        return platform.trim().toLowerCase(Locale.ROOT) + "::" + accountType.trim().toLowerCase(Locale.ROOT);
    }
}
