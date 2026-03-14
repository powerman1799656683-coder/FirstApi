package com.firstapi.backend.common;

import java.util.List;

public class PageResponse<T> {

    private final List<T> items;
    private final long total;

    public PageResponse(List<T> items) {
        this.items = items;
        this.total = items.size();
    }

    public List<T> getItems() {
        return items;
    }

    public long getTotal() {
        return total;
    }
}
