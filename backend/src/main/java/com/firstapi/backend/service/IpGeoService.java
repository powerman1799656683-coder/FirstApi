package com.firstapi.backend.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * 通过 ip-api.com 免费接口查询 IP 归属地。
 * 返回格式示例："中国 北京 北京"，查询失败返回空字符串。
 */
@Service
public class IpGeoService {

    private static final Logger LOGGER = LoggerFactory.getLogger(IpGeoService.class);

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(3))
            .build();

    /**
     * 查询 IP 归属地，返回 "国家 省份 城市" 格式的字符串。
     * 对于内网 / 本地 IP 直接返回 "本地网络"。
     */
    public String lookup(String ip) {
        if (ip == null || ip.isEmpty()) return "";
        if (isLocal(ip)) return "本地网络";

        try {
            // ip-api.com 免费接口，lang=zh-CN 返回中文
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("http://ip-api.com/line/" + ip + "?fields=country,regionName,city&lang=zh-CN"))
                    .timeout(Duration.ofSeconds(3))
                    .GET()
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 200) {
                String body = response.body().trim();
                // 响应为三行：country\nregionName\ncity
                String[] lines = body.split("\n");
                if (lines.length >= 3) {
                    String country = lines[0].trim();
                    String region = lines[1].trim();
                    String city = lines[2].trim();
                    // 去重：如果省份和城市相同只保留一个
                    StringBuilder sb = new StringBuilder(country);
                    if (!region.isEmpty() && !region.equals(country)) sb.append(" ").append(region);
                    if (!city.isEmpty() && !city.equals(region)) sb.append(" ").append(city);
                    return sb.toString();
                }
            }
        } catch (Exception e) {
            LOGGER.warn("IP 归属地查询失败: ip={}, error={}", ip, e.getMessage());
        }
        return "";
    }

    private boolean isLocal(String ip) {
        return "127.0.0.1".equals(ip)
                || "0:0:0:0:0:0:0:1".equals(ip)
                || "::1".equals(ip)
                || ip.startsWith("192.168.")
                || ip.startsWith("10.")
                || ip.startsWith("172.16.")
                || ip.startsWith("172.17.")
                || ip.startsWith("172.18.")
                || ip.startsWith("172.19.")
                || ip.startsWith("172.2")
                || ip.startsWith("172.3");
    }
}
