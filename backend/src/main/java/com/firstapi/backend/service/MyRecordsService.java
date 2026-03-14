package com.firstapi.backend.service;

import com.firstapi.backend.model.MyRecordsData;
import com.firstapi.backend.model.MyRecordsData.RecordItem;
import com.firstapi.backend.model.MyRecordsData.StatCard;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class MyRecordsService {

    private final List<RecordItem> allRecords = Arrays.asList(
            new RecordItem(1L, "2026/03/13 18:30:15", "gpt-4o", "Chat Completion", "1,240", "$0.018", "成功"),
            new RecordItem(2L, "2026/03/13 18:25:02", "claude-3-5-sonnet", "Message", "3,800", "$0.057", "成功"),
            new RecordItem(3L, "2026/03/13 18:10:44", "gpt-4o-mini", "Chat Completion", "520", "$0.001", "成功"),
            new RecordItem(4L, "2026/03/13 17:45:12", "dall-e-3", "Image Generation", "1 image", "$0.040", "成功")
    );

    public MyRecordsData getRecords(String keyword) {
        MyRecordsData data = new MyRecordsData();
        data.stats = Arrays.asList(
                new StatCard("今日消费", "$0.116", "+12%", "zap", "#00f2ff"),
                new StatCard("总请求数", "1,452", "本月累计", "cpu", "#3b82f6"),
                new StatCard("平均响应", "845ms", "系统稳定", "clock", "#10b981")
        );
        data.records = filterRecords(keyword);
        return data;
    }

    private List<RecordItem> filterRecords(String keyword) {
        if (isBlank(keyword)) {
            return allRecords;
        }
        String lowerKeyword = keyword.toLowerCase();
        return allRecords.stream()
                .filter(r -> matchesKeyword(r, lowerKeyword))
                .collect(Collectors.toList());
    }

    private boolean matchesKeyword(RecordItem record, String lowerKeyword) {
        return record.model.toLowerCase().contains(lowerKeyword)
                || record.task.toLowerCase().contains(lowerKeyword);
    }

    private boolean isBlank(String str) {
        return str == null || str.trim().isEmpty();
    }
}
