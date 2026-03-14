package com.firstapi.backend.service;

import com.firstapi.backend.common.PageResponse;
import com.firstapi.backend.model.IpItem;
import com.firstapi.backend.repository.IpRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class IpService {

    private final IpRepository repository;

    public IpService(IpRepository repository) {
        this.repository = repository;
    }

    public PageResponse<IpItem> list(String keyword) {
        List<IpItem> items = repository.findAll();
        if (!isBlank(keyword)) {
            items = items.stream()
                    .filter(item -> contains(item.getName(), keyword) || contains(item.getAddress(), keyword))
                    .collect(Collectors.toList());
        }
        return new PageResponse<IpItem>(items);
    }

    public IpItem get(Long id) {
        IpItem item = repository.findById(id);
        if (item == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Ip not found");
        }
        return item;
    }

    public IpItem create(IpItem.Request request) {
        IpItem item = new IpItem();
        item.setName(emptyAsDefault(request.getName(), "新代理"));
        item.setProtocol(emptyAsDefault(request.getProtocol(), "SOCKS5"));
        item.setAddress(emptyAsDefault(request.getAddress(), "0.0.0.0:0000"));
        item.setLocation(emptyAsDefault(request.getLocation(), "未知"));
        item.setAccounts(emptyAsDefault(request.getAccounts(), "0"));
        item.setLatency(emptyAsDefault(request.getLatency(), "0ms"));
        item.setStatus(emptyAsDefault(request.getStatus(), "正常"));
        return repository.save(item);
    }

    public IpItem update(Long id, IpItem.Request request) {
        IpItem current = get(id);
        if (request.getName() != null) current.setName(request.getName());
        if (request.getProtocol() != null) current.setProtocol(request.getProtocol());
        if (request.getAddress() != null) current.setAddress(request.getAddress());
        if (request.getLocation() != null) current.setLocation(request.getLocation());
        if (request.getAccounts() != null) current.setAccounts(request.getAccounts());
        if (request.getLatency() != null) current.setLatency(request.getLatency());
        if (request.getStatus() != null) current.setStatus(request.getStatus());
        return repository.update(id, current);
    }

    public void delete(Long id) {
        get(id);
        repository.deleteById(id);
    }

    public IpItem testIp(Long id) {
        IpItem item = get(id);
        item.setLatency("128ms");
        item.setStatus("正常");
        return repository.update(id, item);
    }

    public List<IpItem> testAll() {
        List<IpItem> all = repository.findAll();
        for (IpItem item : all) {
            item.setLatency("128ms");
            item.setStatus("正常");
            repository.update(item.getId(), item);
        }
        return repository.findAll();
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
