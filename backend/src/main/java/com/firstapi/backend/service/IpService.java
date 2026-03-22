package com.firstapi.backend.service;

import com.firstapi.backend.common.PageResponse;
import com.firstapi.backend.config.RelayProperties;
import com.firstapi.backend.model.IpItem;
import com.firstapi.backend.model.RelayResult;
import com.firstapi.backend.repository.IpRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.Collections;
import java.util.stream.Collectors;

@Service
public class IpService {

    private static final Set<String> VALID_PROTOCOLS = new HashSet<String>(Arrays.asList("SOCKS5", "HTTP", "HTTPS"));

    private final IpRepository repository;
    private final UpstreamHttpClient upstreamHttpClient;
    private final RelayProperties relayProperties;

    public IpService(IpRepository repository,
                     UpstreamHttpClient upstreamHttpClient,
                     RelayProperties relayProperties) {
        this.repository = repository;
        this.upstreamHttpClient = upstreamHttpClient;
        this.relayProperties = relayProperties;
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
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "IP not found");
        }
        return item;
    }

    public IpItem create(IpItem.Request request) {
        String protocol = emptyAsDefault(request.getProtocol(), "SOCKS5");
        if (!VALID_PROTOCOLS.contains(protocol.toUpperCase(Locale.ROOT))) {
            throw new IllegalArgumentException("Unsupported protocol. Allowed: SOCKS5, HTTP, HTTPS");
        }
        String address = emptyAsDefault(request.getAddress(), "0.0.0.0:0000");
        validateAddress(address);

        IpItem item = new IpItem();
        item.setName(emptyAsDefault(request.getName(), "\u65b0\u4ee3\u7406"));
        item.setProtocol(protocol);
        item.setAddress(address);
        item.setLocation(emptyAsDefault(request.getLocation(), "\u672a\u77e5"));
        item.setAccounts(emptyAsDefault(request.getAccounts(), "0"));
        item.setLatency(emptyAsDefault(request.getLatency(), "0ms"));
        item.setStatus(emptyAsDefault(request.getStatus(), "\u6b63\u5e38"));
        return repository.save(item);
    }

    public IpItem update(Long id, IpItem.Request request) {
        IpItem current = get(id);
        if (request.getName() != null) {
            current.setName(request.getName());
        }
        if (request.getProtocol() != null) {
            String protocol = request.getProtocol().toUpperCase(Locale.ROOT);
            if (!VALID_PROTOCOLS.contains(protocol)) {
                throw new IllegalArgumentException("Unsupported protocol. Allowed: SOCKS5, HTTP, HTTPS");
            }
            current.setProtocol(request.getProtocol());
        }
        if (request.getAddress() != null) {
            validateAddress(request.getAddress());
            current.setAddress(request.getAddress());
        }
        if (request.getLocation() != null) {
            current.setLocation(request.getLocation());
        }
        if (request.getAccounts() != null) {
            current.setAccounts(request.getAccounts());
        }
        if (request.getLatency() != null) {
            current.setLatency(request.getLatency());
        }
        if (request.getStatus() != null) {
            current.setStatus(request.getStatus());
        }
        return repository.update(id, current);
    }

    public void delete(Long id) {
        get(id);
        repository.deleteById(id);
    }

    public IpItem testIp(Long id) {
        IpItem item = get(id);
        try {
            RelayResult result = upstreamHttpClient.get(resolveProbeUrl(), Collections.emptyMap(), item);
            long latency = result.getLatencyMs();
            item.setLatency(latency >= 0 ? latency + "ms" : "-");
            item.setStatus(result.getStatusCode() >= 200 && result.getStatusCode() < 500 ? "normal" : "error");
        } catch (Exception ignored) {
            item.setLatency("-");
            item.setStatus("error");
        }
        return repository.update(id, item);
    }

    public List<IpItem> testAll() {
        List<IpItem> all = repository.findAll();
        return all.stream()
                .map(item -> testIp(item.getId()))
                .collect(Collectors.toList());
    }

    private String resolveProbeUrl() {
        String base = relayProperties.getOpenaiBaseUrl();
        if (base == null || base.trim().isEmpty()) {
            base = "https://api.openai.com";
        }
        String normalized = base.trim();
        while (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized + "/v1/models";
    }

    private void validateAddress(String address) {
        if (address.contains("<") || address.contains(">") || address.contains("\"")) {
            throw new IllegalArgumentException("Address contains illegal characters");
        }
        if (!address.matches("^[a-zA-Z0-9.:_\\-/%@+=]+$")) {
            throw new IllegalArgumentException("Invalid proxy address format");
        }
    }

    private boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }

    private boolean contains(String source, String keyword) {
        if (source == null || keyword == null) {
            return false;
        }
        return source.toLowerCase(Locale.ROOT).contains(keyword.toLowerCase(Locale.ROOT));
    }

    private String emptyAsDefault(String value, String defaultValue) {
        return isBlank(value) ? defaultValue : value;
    }
}
