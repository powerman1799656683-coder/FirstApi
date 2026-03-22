package com.firstapi.backend.service;

import com.firstapi.backend.common.PageResponse;
import com.firstapi.backend.model.AnnouncementItem;
import com.firstapi.backend.model.AuthenticatedUser;
import com.firstapi.backend.repository.AnnouncementRepository;
import com.firstapi.backend.util.TimeSupport;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

@Service
public class AnnouncementService {

    private final AnnouncementRepository repository;

    public AnnouncementService(AnnouncementRepository repository) {
        this.repository = repository;
    }

    public PageResponse<AnnouncementItem> list(String keyword) {
        List<AnnouncementItem> items = repository.findAll();
        if (!isBlank(keyword)) {
            items = items.stream()
                .filter(i -> contains(i.getTitle(), keyword)
                           || contains(i.getContent(), keyword))
                .collect(Collectors.toList());
        }
        return new PageResponse<AnnouncementItem>(items);
    }

    public PageResponse<AnnouncementItem> listForUser(AuthenticatedUser user) {
        return listForUser(user, null);
    }

    public PageResponse<AnnouncementItem> listForUser(AuthenticatedUser user, String keyword) {
        List<AnnouncementItem> items = repository.findAll().stream()
                .filter(item -> isPublishedStatus(item.getStatus()))
                .collect(Collectors.toList());

        if (!isBlank(keyword)) {
            items = items.stream()
                    .filter(i -> contains(i.getTitle(), keyword)
                            || contains(i.getContent(), keyword))
                    .collect(Collectors.toList());
        }

        return new PageResponse<AnnouncementItem>(items);
    }

    public AnnouncementItem get(Long id) {
        AnnouncementItem item = repository.findById(id);
        if (item == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "公告不存在");
        }
        return item;
    }

    public AnnouncementItem create(AnnouncementItem.Request req) {
        AnnouncementItem item = new AnnouncementItem();
        item.setTitle(emptyAsDefault(req.getTitle(), "新公告"));
        item.setContent(emptyAsDefault(req.getContent(), ""));
        item.setType(emptyAsDefault(req.getType(), "维护"));
        item.setStatus(emptyAsDefault(req.getStatus(), "发布中"));
        item.setTarget(emptyAsDefault(req.getTarget(), "所有用户"));
        item.setTime(emptyAsDefault(req.getTime(), TimeSupport.nowDateTime()));
        return repository.save(item);
    }

    public AnnouncementItem update(Long id, AnnouncementItem.Request req) {
        AnnouncementItem existing = repository.findById(id);
        if (existing == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "公告不存在");
        }
        if (req.getTitle() != null) existing.setTitle(req.getTitle());
        if (req.getContent() != null) existing.setContent(req.getContent());
        if (req.getType() != null) existing.setType(req.getType());
        if (req.getStatus() != null) existing.setStatus(req.getStatus());
        if (req.getTarget() != null) existing.setTarget(req.getTarget());
        if (req.getTime() != null) existing.setTime(req.getTime());
        return repository.update(id, existing);
    }

    public void delete(Long id) {
        AnnouncementItem existing = repository.findById(id);
        if (existing == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "公告不存在");
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

    private boolean isPublishedStatus(String status) {
        if (isBlank(status)) {
            return true;
        }

        String normalized = status.toLowerCase(Locale.ROOT);
        if (normalized.contains("draft") || status.contains("草稿")) {
            return false;
        }

        return normalized.contains("publish")
                || normalized.contains("active")
                || status.contains("发布");
    }
}
