package com.example.gameoflife;

import java.util.concurrent.ConcurrentHashMap;
import javafx.animation.AnimationTimer;
import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.SnapshotParameters;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.image.Image;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.paint.CycleMethod;
import javafx.scene.paint.LinearGradient;
import javafx.scene.paint.Stop;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.stage.Stage;

public class CanvasMovieApp extends Application {

    // --- Datos de prueba ---
    private static final String[][] MOVIES = {
        {
            "https://images.unsplash.com/photo-1536440136628-849c177e76a1?fm=jpg&q=85&w=400",
            "The Dark Knight",
            "Acción • 2008",
            "9.0",
        },
        {
            "https://images.unsplash.com/photo-1518676590629-3dcbd9c5a5c9?fm=jpg&q=85&w=400",
            "Blade Runner 2049",
            "Neo-Noir • 2017",
            "8.0",
        },
        {
            "https://images.unsplash.com/photo-1542204165-65bf26472b9b?fm=jpg&q=85&w=400",
            "Dune",
            "Épica • 2021",
            "8.4",
        },
        {
            "https://images.unsplash.com/photo-1509347528160-9329f6b3c38a?fm=jpg&q=85&w=400",
            "Inception",
            "Thriller • 2010",
            "8.8",
        },
    };

    // --- Layout ---
    private static final double APP_W = 1020;
    private static final double APP_H = 810;
    private static final double CARD_W = 192;
    private static final double CARD_H = 288;
    private static final double SPACING = 18;
    private static final double RADIUS = 18;

    private static final double LERP_EPS = 0.001;

    // OPTIMIZATION: Global Image Cache to reuse downloaded assets
    private static final ConcurrentHashMap<String, Image> IMAGE_CACHE =
        new ConcurrentHashMap<>();

    private Canvas canvas;
    private GraphicsContext gc;

    private MovieCardModel[] cards;
    private MovieCardModel hoveredCard = null; // OPTIMIZATION: Single reference for hover

    private long lastNanos = 0;
    private boolean isDirty = true;

    // --- Recursos permanentes ---
    private final LinearGradient overlayGradient = new LinearGradient(
        0,
        1,
        0,
        0,
        true,
        CycleMethod.NO_CYCLE,
        new Stop(0.00, Color.rgb(0, 0, 0, 0.95)),
        new Stop(0.40, Color.rgb(0, 0, 0, 0.40)),
        new Stop(1.00, Color.TRANSPARENT)
    );
    private final Font titleFont = Font.font("Segoe UI", FontWeight.BOLD, 14);
    private final Font subFont = Font.font("Segoe UI", FontWeight.NORMAL, 12);
    private final Font ratingFont = Font.font("Segoe UI", FontWeight.BOLD, 12);
    private final Color BG = Color.web("#0f0f0f");
    private final Color SKELETON = Color.web("#252525");
    private final Color SUB_TEXT = Color.rgb(255, 255, 255, 0.70);
    private final Color RATING_COL = Color.web("#E5A93C");

    // ─────────────────────────────────────────────────────────────────────
    @Override
    public void start(Stage stage) {
        canvas = new Canvas(APP_W, APP_H);
        gc = canvas.getGraphicsContext2D();

        StackPane root = new StackPane(canvas);
        root.setStyle("-fx-background-color: #0f0f0f;");

        buildCards();
        setupInput();

        new AnimationTimer() {
            @Override
            public void handle(long now) {
                double dt = (lastNanos == 0) ? 0.016 : (now - lastNanos) / 1e9;
                if (dt > 0.1) dt = 0.016;
                lastNanos = now;

                boolean animating = update(dt);
                // OPTIMIZATION: Adaptive Loop - skips frame completely if nothing moves
                if (animating || isDirty) {
                    isDirty = false;
                    render();
                }
            }
        }.start();

        stage.setTitle("CineStream – Ultra Performance");
        stage.setScene(new Scene(root, APP_W, APP_H));
        stage.show();
    }

    // ═════════════════════════════════════════════════════════════════
    //  PRE-RENDERIZADO — OPTIMIZATION: Merge Card Layers
    // ═════════════════════════════════════════════════════════════════

    /**
     * Replaces 4 distinct layer renders with a pre-rendered skeleton base.
     */
    private Image buildSkeletonTexture(
        String title,
        String subtitle,
        String rating
    ) {
        Canvas off = new Canvas(CARD_W, CARD_H);
        GraphicsContext g = off.getGraphicsContext2D();

        g.setFill(SKELETON);
        g.fillRect(0, 0, CARD_W, CARD_H);

        drawOverlayToContext(g, title, subtitle, rating);

        clearCorners(g); // Punch out corners cleanly using BG color

        SnapshotParameters sp = new SnapshotParameters();
        sp.setFill(Color.TRANSPARENT);
        return off.snapshot(sp, null);
    }

    /**
     * Bakes the poster, gradient, text, and rounded clipping into a SINGLE flat image.
     */
    private Image buildFinalTexture(
        Image poster,
        String title,
        String subtitle,
        String rating
    ) {
        Canvas off = new Canvas(CARD_W, CARD_H);
        GraphicsContext g = off.getGraphicsContext2D();

        g.drawImage(poster, 0, 0, CARD_W, CARD_H);
        drawOverlayToContext(g, title, subtitle, rating);

        clearCorners(g); // Punch out corners cleanly using BG color

        SnapshotParameters sp = new SnapshotParameters();
        sp.setFill(Color.TRANSPARENT);
        return off.snapshot(sp, null);
    }

    /**
     * Instead of using unsupported BlendModes or clip() (which causes dark halos when
     * snapshotting against transparent backgrounds), we over-draw the 4 outer corner pieces
     * with the main background color (BG).
     * Since the entire app rests on this exact color, these corners seamlessly vanish into
     * the background canvas, producing flawless rounded edges.
     */
    private void clearCorners(GraphicsContext g) {
        g.setFill(BG);
        g.beginPath();

        // Top-Left corner cutout
        g.moveTo(0, 0);
        g.lineTo(RADIUS, 0);
        g.arcTo(0, 0, 0, RADIUS, RADIUS);
        g.closePath();

        // Top-Right corner cutout
        g.moveTo(CARD_W, 0);
        g.lineTo(CARD_W, RADIUS);
        g.arcTo(CARD_W, 0, CARD_W - RADIUS, 0, RADIUS);
        g.closePath();

        // Bottom-Right corner cutout
        g.moveTo(CARD_W, CARD_H);
        g.lineTo(CARD_W - RADIUS, CARD_H);
        g.arcTo(CARD_W, CARD_H, CARD_W, CARD_H - RADIUS, RADIUS);
        g.closePath();

        // Bottom-Left corner cutout
        g.moveTo(0, CARD_H);
        g.lineTo(0, CARD_H - RADIUS);
        g.arcTo(0, CARD_H, RADIUS, CARD_H, RADIUS);
        g.closePath();

        g.fill();
    }

    private void drawOverlayToContext(
        GraphicsContext g,
        String title,
        String subtitle,
        String rating
    ) {
        g.setFill(overlayGradient);
        g.fillRect(0, 0, CARD_W, CARD_H);

        double tx = 12,
            ty = CARD_H - 45;
        g.setFont(titleFont);
        g.setFill(Color.WHITE);
        g.fillText(title, tx, ty);
        ty += 16;
        g.setFont(subFont);
        g.setFill(SUB_TEXT);
        g.fillText(subtitle, tx, ty);
        ty += 16;
        g.setFont(ratingFont);
        g.setFill(RATING_COL);
        g.fillText("★ " + rating, tx, ty);
    }

    // ═════════════════════════════════════════════════════════════════

    private void buildCards() {
        final double baseX = 40,
            baseY = 400;
        cards = new MovieCardModel[MOVIES.length];

        for (int i = 0; i < MOVIES.length; i++) {
            String[] d = MOVIES[i];
            MovieCardModel c = new MovieCardModel();

            c.x = baseX + i * (CARD_W + SPACING);
            c.y = baseY;
            c.right = c.x + CARD_W;
            c.bottom = baseY + CARD_H;

            c.targetOpacity = 1.0;
            c.targetY = baseY;
            c.currentY = baseY + 40;
            c.delayTimer = i * 0.1;

            c.skeletonTexture = buildSkeletonTexture(d[1], d[2], d[3]);

            // OPTIMIZATION: Use ConcurrentHashMap to fetch/cache images globally
            c.image = IMAGE_CACHE.computeIfAbsent(d[0], url ->
                new Image(url, CARD_W, CARD_H, false, true, true)
            );

            if (c.image.getProgress() >= 1.0 && !c.image.isError()) {
                c.finalTexture = buildFinalTexture(c.image, d[1], d[2], d[3]);
                c.isImageLoaded = true;
                c.imageAlpha = 1.0; // Skip fade-in if cached
            } else {
                c.image.progressProperty().addListener((obs, o, nv) -> {
                    if (nv.doubleValue() >= 1.0 && !c.image.isError()) {
                        // Bake the final composite ONCE upon load completion
                        c.finalTexture = buildFinalTexture(
                            c.image,
                            d[1],
                            d[2],
                            d[3]
                        );
                        c.isImageLoaded = true;
                        isDirty = true;
                    }
                });
            }

            cards[i] = c;
        }
    }

    private void setupInput() {
        canvas.setOnMouseMoved((MouseEvent e) -> {
            final double mx = e.getX(),
                my = e.getY();
            MovieCardModel newHover = null;

            // Fast O(N) basic hit test ignoring dynamic scale for stability
            for (MovieCardModel c : cards) {
                if (mx >= c.x && mx <= c.right && my >= c.y && my <= c.bottom) {
                    newHover = c;
                    break;
                }
            }

            if (newHover != hoveredCard) {
                if (hoveredCard != null) hoveredCard.targetScale = 1.0;
                if (newHover != null) newHover.targetScale = 1.05;

                hoveredCard = newHover;
                isDirty = true;
            }
        });

        canvas.setOnMouseExited(e -> {
            if (hoveredCard != null) {
                hoveredCard.targetScale = 1.0;
                hoveredCard = null;
                isDirty = true;
            }
        });
    }

    // ═════════════════════════════════════════════════════════════════
    //  GAME LOOP
    // ═════════════════════════════════════════════════════════════════

    private boolean update(double dt) {
        boolean any = false;

        // OPTIMIZATION: Exponential Interpolation - Frame-rate independent & perfectly stable
        final double tScale = 1.0 - Math.exp(-16.0 * dt);
        final double tOpacity = 1.0 - Math.exp(-8.0 * dt);
        final double tY = 1.0 - Math.exp(-10.0 * dt);

        for (MovieCardModel c : cards) {
            if (c.delayTimer > 0) {
                c.delayTimer -= dt;
                any = true;
                continue;
            }

            final double dS = c.targetScale - c.currentScale;
            final double dO = c.targetOpacity - c.currentOpacity;
            final double dY = c.targetY - c.currentY;

            if (Math.abs(dS) > LERP_EPS) {
                c.currentScale += dS * tScale;
                any = true;
            } else c.currentScale = c.targetScale;

            if (Math.abs(dO) > LERP_EPS) {
                c.currentOpacity += dO * tOpacity;
                any = true;
            } else c.currentOpacity = c.targetOpacity;

            if (Math.abs(dY) > LERP_EPS) {
                c.currentY += dY * tY;
                any = true;
            } else c.currentY = c.targetY;

            if (c.isImageLoaded && c.imageAlpha < 1.0) {
                c.imageAlpha = Math.min(1.0, c.imageAlpha + 3.5 * dt);
                any = true;
            }
        }
        return any;
    }

    private void render() {
        gc.setFill(BG);
        gc.fillRect(0, 0, APP_W, APP_H);
        gc.setFill(SUB_TEXT);
        gc.setFont(titleFont);
        gc.fillText("EN CARTELERA", 40, 370);

        // OPTIMIZATION: Hover Tracking - Skip double loop iteration over entire array
        for (MovieCardModel c : cards) {
            if (c != hoveredCard && c.currentOpacity > 0.01) drawCard(c);
        }
        if (hoveredCard != null && hoveredCard.currentOpacity > 0.01) {
            drawCard(hoveredCard);
        }
    }

    private void drawCard(MovieCardModel c) {
        // OPTIMIZATION: Remove save()/restore() overhead by pre-calculating manual transforms
        final double w = CARD_W * c.currentScale;
        final double h = CARD_H * c.currentScale;

        final double cx = c.x + CARD_W * 0.5;
        final double cy = c.currentY + CARD_H * 0.5;

        final double rx = cx - w * 0.5;
        final double ry = cy - h * 0.5;

        // OPTIMIZATION: Viewport Culling - Don't issue GPU calls for off-screen items
        if (rx > APP_W || rx + w < 0 || ry > APP_H || ry + h < 0) return;

        gc.setGlobalAlpha(c.currentOpacity);

        // OPTIMIZATION: Render down to 1 draw call per card using composed textures
        if (c.isImageLoaded && c.imageAlpha >= 1.0) {
            // Fast path: Image loaded and fully faded in (99% of lifetime) = 1 drawImage
            gc.drawImage(c.finalTexture, rx, ry, w, h);
        } else if (c.isImageLoaded && c.imageAlpha > 0.0) {
            // Crossfade path: Image is fading in = 2 drawImages
            gc.drawImage(c.skeletonTexture, rx, ry, w, h);
            gc.setGlobalAlpha(c.currentOpacity * c.imageAlpha);
            gc.drawImage(c.finalTexture, rx, ry, w, h);
        } else {
            // Loading path: No image yet = 1 drawImage
            gc.drawImage(c.skeletonTexture, rx, ry, w, h);
        }

        // Cleanup state for the next card without invoking expensive gc.restore()
        gc.setGlobalAlpha(1.0);
    }

    // ═════════════════════════════════════════════════════════════════
    //  MODELO
    // ═════════════════════════════════════════════════════════════════
    private static final class MovieCardModel {

        double x, y, right, bottom;

        double targetScale = 1.0,
            currentScale = 1.0;
        double targetOpacity = 0.0,
            currentOpacity = 0.0;
        double targetY = 0,
            currentY = 0;
        double delayTimer = 0;

        Image image;
        Image skeletonTexture; // Baked loading state
        Image finalTexture; // Baked complete state

        boolean isImageLoaded = false;
        double imageAlpha = 0;
    }

    public static void main(String[] args) {
        launch(args);
    }
}
