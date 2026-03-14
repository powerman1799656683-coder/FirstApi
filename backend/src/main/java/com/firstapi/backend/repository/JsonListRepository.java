package com.firstapi.backend.repository;

import com.firstapi.backend.common.SimpleStore;
import com.firstapi.backend.store.JsonStorePersistence;

import javax.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public abstract class JsonListRepository<T extends SimpleStore.Identifiable> {

    private final JsonStorePersistence persistence;
    private final String storeKey;
    private final Class<T> itemType;

    protected JsonListRepository(JsonStorePersistence persistence, String storeKey, Class<T> itemType) {
        this.persistence = persistence;
        this.storeKey = storeKey;
        this.itemType = itemType;
    }

    @PostConstruct
    public void init() {
        persistence.seedListIfMissing(storeKey, defaultItems());
    }

    public synchronized List<T> findAll() {
        List<T> items = new ArrayList<T>(loadItems());
        items.sort(new Comparator<T>() {
            @Override
            public int compare(T left, T right) {
                Long leftId = left.getId() == null ? 0L : left.getId();
                Long rightId = right.getId() == null ? 0L : right.getId();
                return rightId.compareTo(leftId);
            }
        });
        return items;
    }

    public synchronized T findById(Long id) {
        for (T item : loadItems()) {
            if (id != null && id.equals(item.getId())) {
                return item;
            }
        }
        return null;
    }

    public synchronized T save(T item) {
        List<T> items = new ArrayList<T>(loadItems());
        item.setId(nextId(items));
        items.add(item);
        persist(items);
        return item;
    }

    public synchronized T update(Long id, T item) {
        List<T> items = new ArrayList<T>(loadItems());
        item.setId(id);
        boolean replaced = false;
        for (int i = 0; i < items.size(); i++) {
            if (id != null && id.equals(items.get(i).getId())) {
                items.set(i, item);
                replaced = true;
                break;
            }
        }
        if (!replaced) {
            items.add(item);
        }
        persist(items);
        return item;
    }

    public synchronized void deleteById(Long id) {
        List<T> items = new ArrayList<T>(loadItems());
        items.removeIf(item -> id != null && id.equals(item.getId()));
        persist(items);
    }

    protected abstract List<T> defaultItems();

    private List<T> loadItems() {
        return new ArrayList<T>(persistence.readList(storeKey, itemType));
    }

    private void persist(List<T> items) {
        persistence.writeList(storeKey, items);
    }

    private Long nextId(List<T> items) {
        long maxId = 0L;
        for (T item : items) {
            if (item.getId() != null && item.getId() > maxId) {
                maxId = item.getId();
            }
        }
        return maxId + 1L;
    }
}
