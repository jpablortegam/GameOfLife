package com.example.gameoflife.cache;

import java.util.LinkedHashMap;
import java.util.Map;
import javafx.scene.image.Image;

public final class ImageMemoryCache {

    private static final int MAX_ENTRIES = 200;

    private static final Map<String, Image> CACHE = new LinkedHashMap<>(
        256,
        0.75f,
        true
    ) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, Image> eldest) {
            return size() > MAX_ENTRIES;
        }
    };

    private ImageMemoryCache() {}

    public static synchronized Image get(String key) {
        return CACHE.get(key);
    }

    public static synchronized void put(String key, Image image) {
        CACHE.put(key, image);
    }

    public static synchronized boolean contains(String key) {
        return CACHE.containsKey(key);
    }

    public static synchronized void clear() {
        CACHE.clear();
    }

    public static synchronized int size() {
        return CACHE.size();
    }
}
