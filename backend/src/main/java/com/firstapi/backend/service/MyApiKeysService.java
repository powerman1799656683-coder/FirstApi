package com.firstapi.backend.service;

import com.firstapi.backend.common.CurrentSessionHolder;
import com.firstapi.backend.common.PageResponse;
import com.firstapi.backend.model.ApiKeyItem;
import com.firstapi.backend.model.AuthenticatedUser;
import com.firstapi.backend.model.GroupItem;
import com.firstapi.backend.repository.GroupRepository;
import com.firstapi.backend.repository.MyApiKeysRepository;
import com.firstapi.backend.repository.RelayRecordRepository;
import com.firstapi.backend.util.TimeSupport;
import com.firstapi.backend.util.ValidationSupport;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class MyApiKeysService {

    private final MyApiKeysRepository repository;
    private final GroupRepository groupRepository;
    private final SensitiveDataService sensitiveDataService;
    private final RelayRecordRepository relayRecordRepository;

    public MyApiKeysService(MyApiKeysRepository repository, GroupRepository groupRepository, SensitiveDataService sensitiveDataService, RelayRecordRepository relayRecordRepository) {
        this.repository = repository;
        this.groupRepository = groupRepository;
        this.sensitiveDataService = sensitiveDataService;
        this.relayRecordRepository = relayRecordRepository;
    }

    public PageResponse<ApiKeyItem> listByUserId(Long userId) {
        List<ApiKeyItem> items = repository.findAllByOwnerId(userId);
        Map<Long, String> groupNameMap = loadGroupNameMap();
        Map<Long, RelayRecordRepository.ApiKeyStat> statsMap = loadApiKeyStats(userId);
        return new PageResponse<>(items.stream().map(item -> toResponse(item, true, groupNameMap, statsMap)).collect(Collectors.toList()));
    }

    public PageResponse<ApiKeyItem> list(String keyword, java.time.LocalDateTime start, java.time.LocalDateTime end) {
        AuthenticatedUser user = CurrentSessionHolder.require();
        List<ApiKeyItem> items = repository.findAllByOwnerId(user.getId());
        if (!isBlank(keyword)) {
            items = items.stream()
                    .filter(item -> contains(item.getName(), keyword))
                    .collect(Collectors.toList());
        }
        Map<Long, String> groupNameMap = loadGroupNameMap();
        Map<Long, RelayRecordRepository.ApiKeyStat> statsMap = loadApiKeyStats(user.getId(), start, end);
        return new PageResponse<>(items.stream().map(item -> toResponse(item, false, groupNameMap, statsMap)).collect(Collectors.toList()));
    }

    public ApiKeyItem get(Long id) {
        return toResponse(requireOwned(id), false, loadGroupNameMap(), null);
    }

    public ApiKeyItem revealKey(Long id) {
        return toResponse(requireOwned(id), true, loadGroupNameMap(), null);
    }

    private static final int MAX_KEYS_PER_USER = 10;

    public ApiKeyItem create(ApiKeyItem.Request request) {
        AuthenticatedUser user = CurrentSessionHolder.require();
        long currentCount = repository.countByOwnerId(user.getId());
        if (currentCount >= MAX_KEYS_PER_USER) {
            throw new IllegalArgumentException("每个用户最多创建 " + MAX_KEYS_PER_USER + " 个 API 密钥");
        }
        if (request.getGroupId() == null) {
            throw new IllegalArgumentException("请选择分组");
        }
        GroupItem group = groupRepository.findById(request.getGroupId());
        if (group == null) {
            throw new IllegalArgumentException("所选分组不存在");
        }
        ApiKeyItem item = new ApiKeyItem();
        item.setOwnerId(user.getId());
        item.setGroupId(request.getGroupId());
        item.setName(ValidationSupport.requireNotBlank(request.getName(), "API 密钥名称不能为空"));
        item.setKey(sensitiveDataService.protect(generateKey()));
        item.setCreated(TimeSupport.nowDateTime());
        item.setStatus(emptyAsDefault(request.getStatus(), "正常"));
        item.setLastUsed("-");
        repository.save(item);
        return toResponse(item, true, loadGroupNameMap(), null);
    }

    public ApiKeyItem update(Long id, ApiKeyItem.Request request) {
        ApiKeyItem current = requireOwned(id);
        if (request.getName() != null) {
            current.setName(ValidationSupport.requireNotBlank(request.getName(), "API 密钥名称不能为空"));
        }
        if (request.getStatus() != null) {
            current.setStatus(request.getStatus());
        }
        if (request.getGroupId() != null) {
            GroupItem group = groupRepository.findById(request.getGroupId());
            if (group == null) {
                throw new IllegalArgumentException("所选分组不存在");
            }
            current.setGroupId(request.getGroupId());
        }
        repository.update(current);
        return toResponse(current, false, loadGroupNameMap(), null);
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
        return toResponse(item, true, loadGroupNameMap(), null);
    }

    private ApiKeyItem requireOwned(Long id) {
        AuthenticatedUser user = CurrentSessionHolder.require();
        ApiKeyItem item = repository.findByIdAndOwnerId(id, user.getId());
        if (item == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "API 密钥不存在");
        }
        return item;
    }

    private Map<Long, String> loadGroupNameMap() {
        Map<Long, String> map = new HashMap<>();
        for (GroupItem g : groupRepository.findAll()) {
            if (g.getId() != null && g.getName() != null) {
                map.put(g.getId(), g.getName());
            }
        }
        return map;
    }

    private Map<Long, RelayRecordRepository.ApiKeyStat> loadApiKeyStats(Long ownerId) {
        return loadApiKeyStats(ownerId, null, null);
    }

    private Map<Long, RelayRecordRepository.ApiKeyStat> loadApiKeyStats(Long ownerId, java.time.LocalDateTime start, java.time.LocalDateTime end) {
        Map<Long, RelayRecordRepository.ApiKeyStat> map = new HashMap<>();
        for (RelayRecordRepository.ApiKeyStat stat : relayRecordRepository.groupByApiKeyIdForOwner(ownerId, start, end)) {
            map.put(stat.apiKeyId(), stat);
        }
        return map;
    }

    private ApiKeyItem toResponse(ApiKeyItem source, boolean revealFullKey, Map<Long, String> groupNameMap, Map<Long, RelayRecordRepository.ApiKeyStat> statsMap) {
        String plainTextKey;
        try {
            plainTextKey = sensitiveDataService.reveal(source.getKey());
        } catch (Exception e) {
            plainTextKey = null;
        }
        ApiKeyItem response = new ApiKeyItem();
        response.setId(source.getId());
        response.setOwnerId(source.getOwnerId());
        response.setGroupId(source.getGroupId());
        if (source.getGroupId() != null && groupNameMap != null) {
            response.setGroupName(groupNameMap.get(source.getGroupId()));
        }
        response.setName(source.getName());
        response.setCreated(source.getCreated());
        response.setStatus(normalizeStatus(source.getStatus()));
        response.setLastUsed(source.getLastUsed());
        response.setKeyPreview(maskKey(plainTextKey));
        if (revealFullKey && plainTextKey != null) {
            response.setPlainTextKey(plainTextKey);
        }
        if (statsMap != null && source.getId() != null) {
            RelayRecordRepository.ApiKeyStat stat = statsMap.get(source.getId());
            if (stat != null) {
                response.setRequestCount(stat.requestCount());
                response.setTotalCost(stat.totalCost());
            }
        }
        return response;
    }

    private String maskKey(String key) {
        if (key == null) {
            return "sk-****";
        }
        if (key.length() <= 10) {
            return key.substring(0, Math.min(4, key.length())) + "****";
        }
        return key.substring(0, 10) + "..." + key.substring(key.length() - 4);
    }

    private String generateKey() {
        return "sk-firstapi-" + UUID.randomUUID().toString().replace("-", "").substring(0, 20).toLowerCase(Locale.ROOT);
    }

    private String normalizeStatus(String status) {
        if (status != null && status.indexOf('\uFFFD') >= 0) {
            return "正常";
        }
        return status;
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
