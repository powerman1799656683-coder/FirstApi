package com.firstapi.backend.service;

import com.firstapi.backend.model.RecordsData;
import com.firstapi.backend.model.RecordsData.BarPoint;
import com.firstapi.backend.model.RecordsData.PieSlice;
import com.firstapi.backend.model.RecordsData.RecordItem;
import com.firstapi.backend.model.RecordsData.StatCard;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

@Service
public class RecordsService {

    public RecordsData getRecords(String keyword) {
        RecordsData data = new RecordsData();
        data.stats = Arrays.asList(
                new StatCard("今日总消费", "$142.18", "+15.2%", "zap", "#ef4444"),
                new StatCard("总请求次数", "72,655", "+8.4%", "activity", "#10b981"),
                new StatCard("活跃 API 密钥", "38", "+2", "database", "#3b82f6"),
                new StatCard("平均响应耗时", "1.24s", "-5.1%", "clock", "#00f2ff")
        );
        data.modelPie = Arrays.asList(
                new PieSlice("Claude-3.5", 45, "#00f2ff"),
                new PieSlice("GPT-4o", 35, "#3b82f6"),
                new PieSlice("Gemini-1.5", 15, "#10b981"),
                new PieSlice("Llama-3", 5, "#8b5cf6")
        );
        data.bar = Arrays.asList(
                new BarPoint("03-08", 120),
                new BarPoint("03-09", 180),
                new BarPoint("03-10", 420),
                new BarPoint("03-11", 380),
                new BarPoint("03-12", 550),
                new BarPoint("03-13", 490)
        );
        List<RecordItem> all = Arrays.asList(
                new RecordItem(1L, "19:45:10", "fzq@yc.com", "sk-yc-a1b2...", "gpt-4o", "1,240", "$0.018", "成功"),
                new RecordItem(2L, "19:44:02", "zhou@yc.com", "sk-yc-c3d4...", "claude-3-5", "3,800", "$0.057", "成功"),
                new RecordItem(3L, "19:43:44", "liu@yc.com", "sk-yc-e5f6...", "gpt-4o-mini", "520", "$0.001", "成功"),
                new RecordItem(4L, "19:42:12", "guest_01", "sk-yc-g7h8...", "dall-e-3", "1 image", "$0.040", "成功")
        );
        data.records = filter(all, keyword);
        return data;
    }

    private List<RecordItem> filter(List<RecordItem> all, String keyword) {
        if (keyword == null || keyword.trim().isEmpty()) {
            return all;
        }
        List<RecordItem> result = new ArrayList<RecordItem>();
        for (RecordItem item : all) {
            if (contains(item.user, keyword) || contains(item.key, keyword) || contains(item.model, keyword)) {
                result.add(item);
            }
        }
        return result;
    }

    private boolean contains(String text, String keyword) {
        return text != null && text.toLowerCase(Locale.ROOT).contains(keyword.toLowerCase(Locale.ROOT));
    }
}
