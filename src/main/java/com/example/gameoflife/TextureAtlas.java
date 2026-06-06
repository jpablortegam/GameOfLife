package com.example.gameoflife;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javafx.scene.SnapshotParameters;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.image.Image;
import javafx.scene.paint.Color;

public class TextureAtlas {
    private static final int ATLAS_W = 2048;
    private static final int ATLAS_H = 2048;
    private static final int PADDING = 2;

    public static class AtlasRect {
        public final int x, y, w, h;
        public AtlasRect(int x, int y, int w, int h) {
            this.x = x; this.y = y; this.w = w; this.h = h;
        }
    }

    private final Map<String, AtlasRect> entries = new HashMap<>();
    private final List<Image> sources = new ArrayList<>();
    private final List<String> sourceKeys = new ArrayList<>();
    private Image atlasImage;
    private boolean built;

    public void add(String key, Image image) {
        sources.add(image);
        sourceKeys.add(key);
        built = false;
    }

    public void build() {
        if (built) return;
        Canvas canvas = new Canvas(ATLAS_W, ATLAS_H);
        GraphicsContext g = canvas.getGraphicsContext2D();
        g.setFill(Color.TRANSPARENT);
        g.clearRect(0, 0, ATLAS_W, ATLAS_H);

        int cursorX = PADDING;
        int cursorY = PADDING;
        int rowH = 0;

        for (int i = 0; i < sources.size(); i++) {
            Image img = sources.get(i);
            int iw = (int) Math.ceil(img.getWidth());
            int ih = (int) Math.ceil(img.getHeight());

            if (cursorX + iw + PADDING > ATLAS_W) {
                cursorX = PADDING;
                cursorY += rowH + PADDING;
                rowH = 0;
            }

            if (cursorY + ih + PADDING > ATLAS_H) {
                break;
            }

            g.drawImage(img, cursorX, cursorY, iw, ih);
            entries.put(sourceKeys.get(i), new AtlasRect(cursorX, cursorY, iw, ih));

            cursorX += iw + PADDING;
            rowH = Math.max(rowH, ih);
        }

        SnapshotParameters sp = new SnapshotParameters();
        sp.setFill(Color.TRANSPARENT);
        atlasImage = canvas.snapshot(sp, null);
        built = true;
    }

    public Image getAtlasImage() {
        if (!built) build();
        return atlasImage;
    }

    public AtlasRect getRect(String key) {
        return entries.get(key);
    }

    public int size() {
        return entries.size();
    }
}
