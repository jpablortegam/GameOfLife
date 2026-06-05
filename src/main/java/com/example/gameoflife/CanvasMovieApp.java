package com.example.gameoflife;

import javafx.animation.AnimationTimer;
import javafx.application.Application;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.scene.Scene;
import javafx.scene.SnapshotParameters;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.effect.GaussianBlur;
import javafx.scene.image.Image;
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
            "Acci\u00f3n \u2022 2008",
            "9.0",
        },
        {
            "https://images.unsplash.com/photo-1518676590629-3dcbd9c5a5c9?fm=jpg&q=85&w=400",
            "Blade Runner 2049",
            "Neo-Noir \u2022 2017",
            "8.0",
        },
        {
            "https://images.unsplash.com/photo-1542204165-65bf26472b9b?fm=jpg&q=85&w=400",
            "Dune",
            "\u00c9pica \u2022 2021",
            "8.4",
        },
        {
            "https://images.unsplash.com/photo-1509347528160-9329f6b3c38a?fm=jpg&q=85&w=400",
            "Inception",
            "Thriller \u2022 2010",
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

    // --- Spring parameters ---
    private static final double SCALE_STIFFNESS = 300.0;
    private static final double SCALE_DAMPING = 28.0;
    private static final double OPACITY_STIFFNESS = 150.0;
    private static final double OPACITY_DAMPING = 18.0;
    private static final double Y_STIFFNESS = 200.0;
    private static final double Y_DAMPING = 22.0;

    // --- Arrays SoA ---
    private int numCards;
    private double[] cX, cY;
    private double[] cScale, cTargetScale, cScaleVelocity;
    private double[] cOpacity, cTargetOpacity, cOpacityVelocity;
    private double[] cCurrentY, cTargetY, cYVelocity;
    private double[] cDelayTimer;
    private double[] cImageAlpha;
    private boolean[] cIsImageLoaded;
    private Image[] cImage, cSkeletonTexture, cOverlayTexture, cModalTextTexture;

    // --- Estado UI ---
    private Canvas canvas;
    private GraphicsContext gc;
    private int hoveredIndex = -1;
    private long lastNanos = 0;
    private boolean isDirty = true;

    // --- Modal ---
    private int activeModalIndex = -1;
    private double modalProgress = 0.0;
    private double targetModalProgress = 0.0;
    private static final double MODAL_W = 800;
    private static final double MODAL_H = 450;
    private final AnimationSystem.Tween modalTween =
        new AnimationSystem.Tween(0.4, 0.0, 1.0);

    // --- Image Cache ---
    private final ImageCache imageCache = new ImageCache(150);

    private final Font titleFont = Font.font("Segoe UI", FontWeight.BOLD, 14);
    private final Font subFont = Font.font("Segoe UI", FontWeight.NORMAL, 12);
    private final Font ratingFont = Font.font("Segoe UI", FontWeight.BOLD, 12);
    private final Font modalTitleFont = Font.font(
        "Segoe UI",
        FontWeight.BOLD,
        36
    );
    private final Color BG = Color.web("#0f0f0f");
    private final Color SKELETON = Color.web("#252525");
    private final Color SUB_TEXT = Color.rgb(255, 255, 255, 0.70);
    private final Color RATING_COL = Color.web("#E5A93C");

    private final LinearGradient overlayGradient = new LinearGradient(
        0, 1, 0, 0, true, CycleMethod.NO_CYCLE,
        new Stop(0.00, Color.rgb(0, 0, 0, 0.95)),
        new Stop(0.40, Color.rgb(0, 0, 0, 0.40)),
        new Stop(1.00, Color.TRANSPARENT)
    );

    private final LinearGradient modalFadeGradient = new LinearGradient(
        0, 0, 1, 0, true, CycleMethod.NO_CYCLE,
        new Stop(0, Color.TRANSPARENT),
        new Stop(1, Color.web("#1A1A1A"))
    );

    // ─────────────────────────────────────────────────────────────────────
    @Override
    public void start(Stage stage) {
        canvas = new Canvas(APP_W, APP_H);
        gc = canvas.getGraphicsContext2D();

        StackPane root = new StackPane(canvas);
        root.setStyle("-fx-background-color: #0f0f0f;");

        initDataOrientedArrays();
        buildCards();
        setupInput();

        new AnimationTimer() {
            @Override
            public void handle(long now) {
                double dt = (lastNanos == 0) ? 0.016 : (now - lastNanos) / 1e9;
                if (dt > 0.1) dt = 0.016;
                lastNanos = now;

                boolean animating = update(dt);
                if (animating || isDirty) {
                    isDirty = false;
                    render();
                }
            }
        }.start();

        stage.setTitle("CineStream \u2013 Ultra Performance");
        stage.setScene(new Scene(root, APP_W, APP_H));
        stage.show();
    }

    private void initDataOrientedArrays() {
        numCards = MOVIES.length;
        cX = new double[numCards];
        cY = new double[numCards];
        cScale = new double[numCards];
        cTargetScale = new double[numCards];
        cScaleVelocity = new double[numCards];
        cOpacity = new double[numCards];
        cTargetOpacity = new double[numCards];
        cOpacityVelocity = new double[numCards];
        cCurrentY = new double[numCards];
        cTargetY = new double[numCards];
        cYVelocity = new double[numCards];
        cDelayTimer = new double[numCards];
        cImageAlpha = new double[numCards];
        cIsImageLoaded = new boolean[numCards];

        cImage = new Image[numCards];
        cSkeletonTexture = new Image[numCards];
        cOverlayTexture = new Image[numCards];
        cModalTextTexture = new Image[numCards];
    }

    // ═════════════════════════════════════════════════════════════════
    //  UTILIDADES DE RENDERIZADO
    // ═════════════════════════════════════════════════════════════════

    private void drawImageCover(
        GraphicsContext g,
        Image img,
        double x, double y, double w, double h
    ) {
        if (img == null) return;

        double imgW = img.getWidth();
        double imgH = img.getHeight();
        if (imgW == 0 || imgH == 0) return;

        double imgRatio = imgW / imgH;
        double canvasRatio = w / h;
        double sx, sy, sw, sh;

        if (imgRatio > canvasRatio) {
            sh = imgH;
            sw = imgH * canvasRatio;
            sx = (imgW - sw) / 2.0;
            sy = 0;
        } else {
            sw = imgW;
            sh = imgW / canvasRatio;
            sx = 0;
            sy = (imgH - sh) / 2.0;
        }
        g.drawImage(img, sx, sy, sw, sh, x, y, w, h);
    }

    private void applyRoundRectClip(
        GraphicsContext g,
        double x, double y, double w, double h, double r
    ) {
        g.beginPath();
        g.moveTo(x + r, y);
        g.lineTo(x + w - r, y);
        g.arcTo(x + w, y, x + w, y + r, r);
        g.lineTo(x + w, y + h - r);
        g.arcTo(x + w, y + h, x + w - r, y + h, r);
        g.lineTo(x + r, y + h);
        g.arcTo(x, y + h, x, y + h - r, r);
        g.lineTo(x, y + r);
        g.arcTo(x, y, x + r, y, r);
        g.closePath();
        g.clip();
    }

    private double smoothstep(double edge0, double edge1, double x) {
        double t = Math.max(0.0, Math.min(1.0, (x - edge0) / (edge1 - edge0)));
        return t * t * (3.0 - 2.0 * t);
    }

    private Image buildModalTextTexture(String[] data) {
        Canvas off = new Canvas(400, 350);
        GraphicsContext g = off.getGraphicsContext2D();

        g.setFont(modalTitleFont);
        g.setFill(Color.WHITE);
        g.fillText(data[1], 0, 40);

        g.setFont(subFont);
        g.setFill(RATING_COL);
        g.fillText(data[2] + "  \u2022  Calificaci\u00f3n: \u2605 " + data[3], 0, 70);

        g.setFill(SUB_TEXT);
        String[] descLines = {
            "Lorem ipsum dolor sit amet, consectetur adipiscing elit.",
            "Sed do eiusmod tempor incididunt ut labore et dolore",
            "magna aliqua. Ut enim ad minim veniam, quis nostrud",
            "exercitation ullamco laboris nisi ut aliquip ex ea.",
        };
        double ty = 120;
        for (String line : descLines) {
            g.fillText(line, 0, ty);
            ty += 20;
        }

        g.setFill(Color.WHITE);
        g.setFont(titleFont);
        g.fillText("\u2716 Haz clic en cualquier parte para cerrar", 0, 300);

        SnapshotParameters sp = new SnapshotParameters();
        sp.setFill(Color.TRANSPARENT);
        return off.snapshot(sp, null);
    }

    private Image buildSkeletonTexture(
        String title, String subtitle, String rating
    ) {
        Canvas off = new Canvas(CARD_W, CARD_H);
        GraphicsContext g = off.getGraphicsContext2D();

        g.setFill(SKELETON);
        g.fillRect(0, 0, CARD_W, CARD_H);
        drawOverlayToContext(g, title, subtitle, rating);

        SnapshotParameters sp = new SnapshotParameters();
        sp.setFill(Color.TRANSPARENT);
        return off.snapshot(sp, null);
    }

    private Image buildOverlayTexture(
        String title, String subtitle, String rating
    ) {
        Canvas off = new Canvas(CARD_W, CARD_H);
        GraphicsContext g = off.getGraphicsContext2D();

        drawOverlayToContext(g, title, subtitle, rating);

        SnapshotParameters sp = new SnapshotParameters();
        sp.setFill(Color.TRANSPARENT);
        return off.snapshot(sp, null);
    }

    private void drawOverlayToContext(
        GraphicsContext g,
        String title, String subtitle, String rating
    ) {
        g.setFill(overlayGradient);
        g.fillRect(0, 0, CARD_W, CARD_H);
        double tx = 12, ty = CARD_H - 45;
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
        g.fillText("\u2605 " + rating, tx, ty);
    }

    private void buildCards() {
        final double baseX = 40, baseY = 400;

        for (int i = 0; i < numCards; i++) {
            String[] d = MOVIES[i];

            cX[i] = baseX + i * (CARD_W + SPACING);
            cY[i] = baseY;
            cTargetScale[i] = 1.0;
            cScale[i] = 1.0;
            cScaleVelocity[i] = 0.0;
            cTargetOpacity[i] = 1.0;
            cOpacity[i] = 0.0;
            cOpacityVelocity[i] = 0.0;
            cTargetY[i] = baseY;
            cCurrentY[i] = baseY + 40;
            cYVelocity[i] = 0.0;
            cDelayTimer[i] = i * 0.1;

            cSkeletonTexture[i] = buildSkeletonTexture(d[1], d[2], d[3]);
            cOverlayTexture[i] = buildOverlayTexture(d[1], d[2], d[3]);
            cModalTextTexture[i] = buildModalTextTexture(d);

            cImage[i] = imageCache.computeIfAbsent(d[0], url ->
                new Image(url, CARD_W, CARD_H, false, true, true)
            );

            final int idx = i;
            if (cImage[i].getProgress() >= 1.0 && !cImage[i].isError()) {
                cIsImageLoaded[i] = true;
                cImageAlpha[i] = 1.0;
            } else {
                final ChangeListener<Number> progressListener =
                    new ChangeListener<Number>() {
                        @Override
                        public void changed(
                            ObservableValue<? extends Number> obs,
                            Number o, Number nv
                        ) {
                            if (nv.doubleValue() >= 1.0
                                || cImage[idx].isError()
                            ) {
                                cIsImageLoaded[idx] =
                                    nv.doubleValue() >= 1.0;
                                isDirty = true;
                                cImage[idx]
                                    .progressProperty()
                                    .removeListener(this);
                            }
                        }
                    };
                cImage[i].progressProperty().addListener(progressListener);
            }
        }
    }

    private void setupInput() {
        canvas.setOnMouseMoved(e -> {
            if (activeModalIndex != -1) return;

            final double mx = e.getX(), my = e.getY();
            int newHover = -1;

            for (int i = 0; i < numCards; i++) {
                double w = CARD_W * cScale[i];
                double h = CARD_H * cScale[i];
                double cx = cX[i] + CARD_W * 0.5;
                double cy = cCurrentY[i] + CARD_H * 0.5;
                double rx = cx - w * 0.5;
                double ry = cy - h * 0.5;

                if (mx >= rx && mx <= rx + w
                    && my >= ry && my <= ry + h) {
                    newHover = i;
                    break;
                }
            }

            if (newHover != hoveredIndex) {
                if (hoveredIndex != -1)
                    cTargetScale[hoveredIndex] = 1.0;
                if (newHover != -1)
                    cTargetScale[newHover] = 1.05;
                hoveredIndex = newHover;
                isDirty = true;
            }
        });

        canvas.setOnMouseClicked(e -> {
            if (e.getClickCount() == 2
                && hoveredIndex != -1
                && activeModalIndex == -1
            ) {
                activeModalIndex = hoveredIndex;
                targetModalProgress = 1.0;
                cTargetScale[hoveredIndex] = 1.0;
                cScale[hoveredIndex] = 1.0;
                cScaleVelocity[hoveredIndex] = 0.0;
                hoveredIndex = -1;
                modalTween.reset(modalProgress, 1.0);
                modalTween.setDuration(0.4);
                isDirty = true;
            } else if (e.getClickCount() == 1
                && activeModalIndex != -1
            ) {
                targetModalProgress = 0.0;
                modalTween.reset(modalProgress, 0.0);
                modalTween.setDuration(0.35);
                isDirty = true;
            }
        });

        canvas.setOnMouseExited(e -> {
            if (hoveredIndex != -1) {
                cTargetScale[hoveredIndex] = 1.0;
                hoveredIndex = -1;
                isDirty = true;
            }
        });
    }

    private boolean update(double dt) {
        boolean any = false;

        // --- Time-based modal animation ---
        if (!modalTween.isFinished()) {
            modalTween.update(dt);
            modalProgress = modalTween.getValue();
            any = true;
        } else {
            modalProgress = targetModalProgress;
            if (targetModalProgress == 0.0 && activeModalIndex != -1) {
                activeModalIndex = -1;
                any = true;
            }
        }

        // --- Spring-based card animation ---
        for (int i = 0; i < numCards; i++) {
            if (cDelayTimer[i] > 0) {
                cDelayTimer[i] -= dt;
                any = true;
                continue;
            }

            // Scale spring
            double forceS = SCALE_STIFFNESS
                * (cTargetScale[i] - cScale[i])
                - SCALE_DAMPING * cScaleVelocity[i];
            cScaleVelocity[i] += forceS * dt;
            cScale[i] += cScaleVelocity[i] * dt;
            if (Math.abs(cScale[i] - cTargetScale[i]) < LERP_EPS
                && Math.abs(cScaleVelocity[i]) < LERP_EPS
            ) {
                cScale[i] = cTargetScale[i];
                cScaleVelocity[i] = 0;
            } else {
                any = true;
            }

            // Opacity spring
            double forceO = OPACITY_STIFFNESS
                * (cTargetOpacity[i] - cOpacity[i])
                - OPACITY_DAMPING * cOpacityVelocity[i];
            cOpacityVelocity[i] += forceO * dt;
            cOpacity[i] += cOpacityVelocity[i] * dt;
            if (Math.abs(cOpacity[i] - cTargetOpacity[i]) < LERP_EPS
                && Math.abs(cOpacityVelocity[i]) < LERP_EPS
            ) {
                cOpacity[i] = cTargetOpacity[i];
                cOpacityVelocity[i] = 0;
            } else {
                any = true;
            }

            // Y position spring
            double forceY = Y_STIFFNESS
                * (cTargetY[i] - cCurrentY[i])
                - Y_DAMPING * cYVelocity[i];
            cYVelocity[i] += forceY * dt;
            cCurrentY[i] += cYVelocity[i] * dt;
            if (Math.abs(cCurrentY[i] - cTargetY[i]) < LERP_EPS
                && Math.abs(cYVelocity[i]) < LERP_EPS
            ) {
                cCurrentY[i] = cTargetY[i];
                cYVelocity[i] = 0;
            } else {
                any = true;
            }

            // Image fade-in
            if (cIsImageLoaded[i] && cImageAlpha[i] < 1.0) {
                cImageAlpha[i] = Math.min(1.0, cImageAlpha[i] + 3.5 * dt);
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
        gc.fillText("EN CARTELERA (Doble clic para expandir)", 40, 370);

        // Draw non-hovered, non-modal cards
        for (int i = 0; i < numCards; i++) {
            if (i != hoveredIndex
                && i != activeModalIndex
                && cOpacity[i] > 0.01
            ) {
                drawCard(i);
            }
        }

        // Draw hovered card on top
        if (hoveredIndex != -1
            && hoveredIndex != activeModalIndex
            && cOpacity[hoveredIndex] > 0.01
        ) {
            drawCard(hoveredIndex);
        }

        // Draw modal capsule on top of everything
        if (activeModalIndex != -1 && modalProgress > 0.001) {
            renderModalCapsule(activeModalIndex);
        }
    }

    private void drawCard(int i) {
        final double w = CARD_W * cScale[i];
        final double h = CARD_H * cScale[i];
        final double cx = cX[i] + CARD_W * 0.5;
        final double cy = cCurrentY[i] + CARD_H * 0.5;
        final double rx = cx - w * 0.5;
        final double ry = cy - h * 0.5;

        if (rx > APP_W || rx + w < 0
            || ry > APP_H || ry + h < 0) return;

        gc.save();
        gc.setGlobalAlpha(cOpacity[i]);

        double currentRadius = RADIUS * cScale[i];

        // ── Corners fix: antialiased roundrect background ──
        // Provides clean rounded edges; the clip on top blends
        // seamlessly, eliminating alias artifacts.
        gc.setFill(SKELETON);
        gc.fillRoundRect(rx, ry, w, h,
            currentRadius * 2, currentRadius * 2);

        applyRoundRectClip(gc, rx, ry, w, h, currentRadius);

        if (cIsImageLoaded[i] && cImageAlpha[i] >= 1.0) {
            drawImageCover(gc, cImage[i], rx, ry, w, h);
            gc.drawImage(cOverlayTexture[i], rx, ry, w, h);
        } else if (cIsImageLoaded[i] && cImageAlpha[i] > 0.0) {
            drawImageCover(gc, cSkeletonTexture[i], rx, ry, w, h);
            gc.setGlobalAlpha(cOpacity[i] * cImageAlpha[i]);
            drawImageCover(gc, cImage[i], rx, ry, w, h);
            gc.drawImage(cOverlayTexture[i], rx, ry, w, h);
        } else {
            drawImageCover(gc, cSkeletonTexture[i], rx, ry, w, h);
        }

        gc.restore();
    }

    private void renderModalCapsule(int i) {
        double ease = modalProgress * modalProgress * (3.0 - 2.0 * modalProgress);

        // Dark page overlay
        gc.setFill(Color.rgb(0, 0, 0, ease * 0.85));
        gc.fillRect(0, 0, APP_W, APP_H);

        // Interpolate modal rect
        final double startW = CARD_W;
        final double startH = CARD_H;
        final double startX = cX[i];
        final double startY = cCurrentY[i];
        final double targetX = (APP_W - MODAL_W) * 0.5;
        final double targetY = (APP_H - MODAL_H) * 0.5;
        final double currX = startX + (targetX - startX) * ease;
        final double currY = startY + (targetY - startY) * ease;
        final double currW = startW + (MODAL_W - startW) * ease;
        final double currH = startH + (MODAL_H - startH) * ease;
        final double currRadius = RADIUS + (24.0 - RADIUS) * ease;

        // Poster interpolates from full card to left 40% of modal
        final double posterW = startW + (MODAL_W * 0.4 - startW) * ease;
        // Use height that fills clip region
        final double posterH = currH;

        gc.save();

        // Motion blur during mid-transition
        double blurAmount = Math.sin(ease * Math.PI) * 15.0;
        if (blurAmount > 0.5) {
            gc.setEffect(new GaussianBlur(blurAmount));
        }

        // ── Clip to rounded rect ──
        applyRoundRectClip(gc, currX, currY, currW, currH, currRadius);

        // ── Background: interpolate from SKELETON to modal dark bg ──
        // This ensures no visual jump when transitioning to/from drawCard.
        Color bgColor = SKELETON.interpolate(
            Color.web("#1A1A1A"), ease);
        gc.setFill(bgColor);
        gc.fillRect(currX, currY, currW, currH);

        // ── Poster image ──
        Image posterImage = (cIsImageLoaded[i] && cImage[i] != null)
            ? cImage[i] : cSkeletonTexture[i];
        drawImageCover(gc, posterImage, currX, currY, posterW, posterH);

        // ── Card overlay fades OUT as modal opens (solves image jump) ──
        // At ease=0: full overlay visibility matches drawCard
        // At ease=1: overlay fully hidden (modal text takes over)
        double overlayAlpha = 1.0 - ease;
        if (overlayAlpha > 0.001) {
            gc.setGlobalAlpha(cOpacity[i] * overlayAlpha);
            gc.drawImage(cOverlayTexture[i], currX, currY, posterW, posterH);
        }

        // ── Modal fade gradient fades IN on right side of poster ──
        if (ease > 0.001) {
            gc.setGlobalAlpha(ease);
            gc.setFill(modalFadeGradient);
            gc.fillRect(
                currX + posterW - 50, currY, 51, posterH);
        }

        // ── Antialiased edge stroke (matches background, subtle) ──
        gc.setGlobalAlpha(1.0);
        gc.setLineWidth(1.5);
        gc.setStroke(bgColor);
        gc.strokeRoundRect(currX, currY, currW, currH,
            currRadius * 2, currRadius * 2);

        gc.restore(); // clears blur, clip, and saves

        // ── Text: wider smoothstep range for gradual in/out ──
        double textAlpha = smoothstep(0.20, 0.80, ease);
        if (textAlpha > 0.001) {
            gc.setGlobalAlpha(textAlpha);
            double textOffsetY = (1.0 - textAlpha) * 20.0;
            gc.drawImage(
                cModalTextTexture[i],
                currX + posterW + 40,
                currY + 40 + textOffsetY
            );
            gc.setGlobalAlpha(1.0);
        }
    }

    public static void main(String[] args) {
        System.setProperty("prism.forceGPU", "true");
        launch(args);
    }
}
