package com.firstapi.backend.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

public class MyRedemptionData {
    public String title;
    public String description;
    public List<HistoryItem> history;

    public static class HistoryItem {
        public String code;
        public String type;
        public String value;
        public String time;
        public String status;

        public HistoryItem() {}

        public HistoryItem(String code, String type, String value, String time, String status) {
            this.code = code;
            this.type = type;
            this.value = value;
            this.time = time;
            this.status = status;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class RedeemRequest {
        public String code;
    }
}
