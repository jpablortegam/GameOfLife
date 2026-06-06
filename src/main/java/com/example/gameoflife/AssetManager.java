package com.example.gameoflife;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Function;
import javafx.scene.image.Image;
import javafx.scene.paint.Color;
import javafx.scene.paint.CycleMethod;
import javafx.scene.paint.LinearGradient;
import javafx.scene.paint.Stop;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;

public class AssetManager {
    private final Map<String, Image> imageCache;
    private final Map<String, Font> fontCache;
    private final Map<String, LinearGradient> gradientCache;

    public AssetManager(int maxImages) {
        this.imageCache = Collections.synchronizedMap(
            new LinkedHashMap<String, Image>(maxImages + 1, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, Image> eldest) {
                    return size() > maxImages;
                }
            }
        );
        this.fontCache = new LinkedHashMap<>();
        this.gradientCache = new LinkedHashMap<>();
        initDefaults();
    }

    private void initDefaults() {
        fontCache.put("title", Font.font("Segoe UI", FontWeight.BOLD, 14));
        fontCache.put("sub", Font.font("Segoe UI", FontWeight.NORMAL, 12));
        fontCache.put("rating", Font.font("Segoe UI", FontWeight.BOLD, 12));
        fontCache.put("modalTitle", Font.font("Segoe UI", FontWeight.BOLD, 36));

        gradientCache.put("overlay", new LinearGradient(
            0, 1, 0, 0, true, CycleMethod.NO_CYCLE,
            new Stop(0.00, Color.rgb(0, 0, 0, 0.95)),
            new Stop(0.40, Color.rgb(0, 0, 0, 0.40)),
            new Stop(1.00, Color.TRANSPARENT)
        ));
        gradientCache.put("modalFade", new LinearGradient(
            0, 0, 1, 0, true, CycleMethod.NO_CYCLE,
            new Stop(0, Color.TRANSPARENT),
            new Stop(1, Color.web("#1A1A1A"))
        ));
    }

    public Image getImage(String url) {
        return imageCache.get(url);
    }

    public Image computeImageIfAbsent(String url, Function<String, Image> loader) {
        return imageCache.computeIfAbsent(url, loader);
    }

    public Font getFont(String name) {
        return fontCache.get(name);
    }

    public void registerFont(String name, Font font) {
        fontCache.put(name, font);
    }

    public LinearGradient getGradient(String name) {
        return gradientCache.get(name);
    }

    public void registerGradient(String name, LinearGradient gradient) {
        gradientCache.put(name, gradient);
    }

    public void clear() {
        imageCache.clear();
    }

    public int imageCount() {
        return imageCache.size();
    }
}
