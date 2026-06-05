package com.example.gameoflife;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Function;
import javafx.scene.image.Image;

public class ImageCache {

    private final Map<String, Image> cache;

    public ImageCache() {
        this(150);
    }

    public ImageCache(int maxSize) {
        this.cache = Collections.synchronizedMap(
            new LinkedHashMap<String, Image>(maxSize + 1, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(
                    Map.Entry<String, Image> eldest
                ) {
                    return size() > maxSize;
                }
            }
        );
    }

    public Image get(String url) {
        return cache.get(url);
    }

    public Image computeIfAbsent(
        String url,
        Function<String, Image> loader
    ) {
        return cache.computeIfAbsent(url, loader);
    }

    public void clear() {
        cache.clear();
    }

    public int size() {
        return cache.size();
    }
}
