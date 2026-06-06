package com.example.gameoflife;

import java.util.concurrent.TimeUnit;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;

public class Profiler {
    private static final int PROFILER_LAYER = 9;
    private static final String[] PASS_NAMES = {
        "INPUT",
        "ANIMATION",
        "LAYOUT",
        "VISIBILITY",
        "RENDER_QUEUE_BUILD",
        "DRAW",
    };

    private long lastTime = System.nanoTime();
    private double fps = 0;
    private double frameTimeMs = 0;
    private double fpsAccum = 0;
    private int fpsCount = 0;
    private long lastFpsTime = System.nanoTime();

    private int drawCalls = 0;
    private int visibleCards = 0;
    private int textureCount = 0;
    private int entityCount = 0;
    private boolean visible = false;
    private final double[] passTimesMs = new double[PASS_NAMES.length];

    private final Font overlayFont = Font.font("Monospace", 12);
    private final Color overlayBg = Color.rgb(0, 0, 0, 0.6);
    private final Color overlayFg = Color.LIMEGREEN;

    public void toggle() { visible = !visible; }
    public boolean isVisible() { return visible; }

    public void beginFrame() {
        long now = System.nanoTime();
        frameTimeMs = (now - lastTime) / 1_000_000.0;
        lastTime = now;

        fpsAccum += frameTimeMs;
        fpsCount++;
        if (fpsAccum >= 1000.0 || (now - lastFpsTime) > TimeUnit.SECONDS.toNanos(1)) {
            fps = fpsCount * 1000.0 / fpsAccum;
            fpsCount = 0;
            fpsAccum = 0;
            lastFpsTime = now;
        }
    }

    public void setDrawCalls(int n) { drawCalls = n; }
    public void setVisibleCards(int n) { visibleCards = n; }
    public void setTextureCount(int n) { textureCount = n; }
    public void setEntityCount(int n) { entityCount = n; }

    public void recordPass(String name, long elapsedNanos) {
        for (int i = 0; i < PASS_NAMES.length; i++) {
            if (PASS_NAMES[i].equals(name)) {
                passTimesMs[i] = elapsedNanos / 1_000_000.0;
                return;
            }
        }
    }

    public void render(RenderQueue queue, double appW, double appH) {
        if (!visible) return;
        double px = 10, py = 10, lw = 260, lh = 230;
        queue.fill(px, py, lw, lh, overlayBg)
            .radius(8).z(9999).layer(PROFILER_LAYER);
        double ly = py + 16, lx = px + 12, ls = 16;
        queue.text(
            String.format("FPS:           %.0f", fps), lx, ly, overlayFont, overlayFg)
            .z(9999).layer(PROFILER_LAYER); ly += ls;
        queue.text(
            String.format("Frame Time:    %.1f ms", frameTimeMs), lx, ly, overlayFont, overlayFg)
            .z(9999).layer(PROFILER_LAYER); ly += ls;
        queue.text(
            String.format("Draw Calls:    %d", drawCalls), lx, ly, overlayFont, overlayFg)
            .z(9999).layer(PROFILER_LAYER); ly += ls;
        queue.text(
            String.format("Visible Cards: %d", visibleCards), lx, ly, overlayFont, overlayFg)
            .z(9999).layer(PROFILER_LAYER); ly += ls;
        queue.text(
            String.format("Textures:      %d", textureCount), lx, ly, overlayFont, overlayFg)
            .z(9999).layer(PROFILER_LAYER); ly += ls;
        queue.text(
            String.format("Entities:      %d", entityCount), lx, ly, overlayFont, overlayFg)
            .z(9999).layer(PROFILER_LAYER); ly += ls;

        for (int i = 0; i < PASS_NAMES.length; i++) {
            queue.text(
                String.format("%-18s %.2f ms", label(PASS_NAMES[i]) + ":", passTimesMs[i]),
                lx,
                ly,
                overlayFont,
                overlayFg
            ).z(9999).layer(PROFILER_LAYER);
            ly += ls;
        }
    }

    private String label(String passName) {
        return switch (passName) {
            case "RENDER_QUEUE_BUILD" -> "Queue Build";
            default -> {
                String lower = passName.toLowerCase().replace('_', ' ');
                yield Character.toUpperCase(lower.charAt(0)) + lower.substring(1);
            }
        };
    }
}
