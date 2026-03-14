package com.firstapi.backend.common;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

public class SimpleStore<T extends SimpleStore.Identifiable> {

    private final Map<Long, T> store = new ConcurrentHashMap<Long, T>();
    private final AtomicLong idGenerator = new AtomicLong(1L);

    public void seed(Collection<T> items) {
        for (T item : items) {
            if (item.getId() == null) {
                item.setId(idGenerator.getAndIncrement());
            } else {
                idGenerator.set(Math.max(idGenerator.get(), item.getId() + 1));
            }
            store.put(item.getId(), item);
        }
    }

    public List<T> list() {
        List<T> items = new ArrayList<T>(store.values());
        items.sort(new Comparator<T>() {
            @Override
            public int compare(T left, T right) {
                return right.getId().compareTo(left.getId());
            }
        });
        return items;
    }

    public T get(Long id) {
        return store.get(id);
    }

    public T create(T item) {
        item.setId(idGenerator.getAndIncrement());
        store.put(item.getId(), item);
        return item;
    }

    public T update(Long id, T item) {
        item.setId(id);
        store.put(id, item);
        return item;
    }

    public void delete(Long id) {
        store.remove(id);
    }

    public interface Identifiable {
        Long getId();

        void setId(Long id);
    }
}
