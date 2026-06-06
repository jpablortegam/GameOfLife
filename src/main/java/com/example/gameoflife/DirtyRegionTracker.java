package com.example.gameoflife;

import java.util.ArrayList;
import java.util.List;

public class DirtyRegionTracker {
    public static class Rect {
        public double x, y, w, h;
        public Rect(double x, double y, double w, double h) {
            this.x = x; this.y = y; this.w = w; this.h = h;
        }
        public boolean isEmpty() { return w <= 0 || h <= 0; }
    }

    private final List<Rect> regions = new ArrayList<>();
    private boolean fullDirty = true;

    public void markFull() {
        fullDirty = true;
        regions.clear();
    }

    public void add(double x, double y, double w, double h) {
        if (fullDirty) return;
        if (w <= 0 || h <= 0) return;
        regions.add(new Rect(x, y, w, h));
        if (regions.size() > 32) {
            mergeAll();
        }
    }

    private void mergeAll() {
        if (regions.isEmpty()) return;
        double minX = Double.MAX_VALUE, minY = Double.MAX_VALUE;
        double maxX = -Double.MAX_VALUE, maxY = -Double.MAX_VALUE;
        for (Rect r : regions) {
            minX = Math.min(minX, r.x);
            minY = Math.min(minY, r.y);
            maxX = Math.max(maxX, r.x + r.w);
            maxY = Math.max(maxY, r.y + r.h);
        }
        regions.clear();
        regions.add(new Rect(minX, minY, maxX - minX, maxY - minY));
    }

    public boolean isFullDirty() { return fullDirty; }

    public List<Rect> getRegions() {
        if (fullDirty) return List.of();
        mergeAll();
        return regions;
    }

    public void clear() {
        regions.clear();
        fullDirty = false;
    }
}
