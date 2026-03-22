package com.firstapi.backend.common;

import java.util.List;

public class PageResponse<T> {

    private final List<T> items;
    private final long total;
    private final int page;
    private final int size;

    public PageResponse(List<T> items) {
        this.items = items;
        this.total = items.size();
        this.page = 1;
        this.size = items.size();
    }

    public PageResponse(List<T> items, long total, int page, int size) {
        this.items = items;
        this.total = total;
        this.page = page;
        this.size = size;
    }

    public List<T> getItems() {
        return items;
    }

    public long getTotal() {
        return total;
    }

    public int getPage() {
        return page;
    }

    public int getSize() {
        return size;
    }
}
