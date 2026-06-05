package com.example.gameoflife;

import javafx.animation.AnimationTimer;
import javafx.application.Application;
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

    private static final double APP_W = 1020;
    private static final double APP_H = 810;
    private static final double CARD_W = 192;
    private static final double CARD_H = 288;
    private static final double SPACING = 18;
    private static final double RADIUS = 18;
    private static final double LERP_EPS = 0.001;
    private static final double FIXED_DT = 1.0 / 120.0;

    private static final double SCALE_STIFFNESS = 380.0;
    private static final double SCALE_DAMPING = 32.0;
    private static final double OPACITY_STIFFNESS = 180.0;
    private static final double OPACITY_DAMPING = 20.0;
    private static final double Y_STIFFNESS = 240.0;
    private static final double Y_DAMPING = 26.0;

    private final Spring scaleSpring;
    private final Spring opacitySpring;
    private final Spring ySpring;
    private final double textLerpFactor;
    private final double imageFadeRate;

    private int numCards;
    private double[] cX, cY;
    private double[] cScale, cTargetScale, cScaleVelocity;
    private double[] cOpacity, cTargetOpacity, cOpacityVelocity;
    private double[] cCurrentY, cTargetY, cYVelocity;
    private double[] cBaseY;
    private double[] cDelayTimer;
    private double[] cImageAlpha;
    private double[] cTextAlpha, cTargetTextAlpha, cTextDelayTimer;
    private double[] cHoverT;
    private boolean[] cIsImageLoaded;
    private Image[] cImage, cSkeletonTexture, cOverlayTexture, cModalTextTexture;

    private Canvas canvas;
    private GraphicsContext gc;
    private int hoveredIndex = -1;
    private long lastNanos = 0;
    private boolean isDirty = true;
    private double accumulator = 0;
    private double totalTime = 0;

    private int activeModalIndex = -1;
    private double modalProgress = 0.0;
    private double targetModalProgress = 0.0;
    private static final double MODAL_W = 800;
    private static final double MODAL_H = 450;
    private final AnimationSystem.Tween modalTween = new AnimationSystem.Tween(
        0.4,
        0.0,
        1.0
    );

    private final ImageCache imageCache = new ImageCache(150);

    private final GaussianBlur reusableBlur = new GaussianBlur();
    private final Color overlayBlack = Color.rgb(0, 0, 0, 1.0);
    private static final Color MODAL_BG = Color.web("#0c0c0c");
    private Image headerTextTexture;
    private final SnapshotParameters snapshotParams;

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

    private final LinearGradient modalFadeGradient = new LinearGradient(
        0,
        0,
        1,
        0,
        true,
        CycleMethod.NO_CYCLE,
        new Stop(0, Color.TRANSPARENT),
        new Stop(1, MODAL_BG)
    );

    private static final class Spring {

        final double xa, xb, xc, va, vb, vc;

        Spring(double k, double damping, double dt) {
            double dt2 = dt * dt;
            this.xa = 1.0 - k * dt2;
            this.xb = dt - damping * dt2;
            this.xc = k * dt2;
            this.va = -k * dt;
            this.vb = 1.0 - damping * dt;
            this.vc = k * dt;
        }
    }

    public CanvasMovieApp() {
        this.scaleSpring = new Spring(SCALE_STIFFNESS, SCALE_DAMPING, FIXED_DT);
        this.opacitySpring = new Spring(
            OPACITY_STIFFNESS,
            OPACITY_DAMPING,
            FIXED_DT
        );
        this.ySpring = new Spring(Y_STIFFNESS, Y_DAMPING, FIXED_DT);
        this.textLerpFactor = 1.0 - Math.exp(-8.0 * FIXED_DT);
        this.imageFadeRate = 3.5 * FIXED_DT;
        this.snapshotParams = new SnapshotParameters();
        this.snapshotParams.setFill(Color.TRANSPARENT);
    }

    @Override
    public void start(Stage stage) {
        canvas = new Canvas(APP_W, APP_H);
        gc = canvas.getGraphicsContext2D();

        StackPane root = new StackPane(canvas);
        root.setStyle("-fx-background-color: #0f0f0f;");

        initDataOrientedArrays();
        initCachedResources();
        buildCards();
        setupInput();

        new AnimationTimer() {
            @Override
            public void handle(long now) {
                double dt = (lastNanos == 0)
                    ? FIXED_DT
                    : (now - lastNanos) / 1e9;
                if (dt > 0.1) dt = FIXED_DT;
                lastNanos = now;

                accumulator += dt;
                if (accumulator > 0.1) accumulator = 0.1;

                boolean animating = false;
                while (accumulator >= FIXED_DT) {
                    animating |= update(FIXED_DT);
                    accumulator -= FIXED_DT;
                }

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
        cBaseY = new double[numCards];
        cDelayTimer = new double[numCards];
        cImageAlpha = new double[numCards];
        cTextAlpha = new double[numCards];
        cTargetTextAlpha = new double[numCards];
        cTextDelayTimer = new double[numCards];
        cHoverT = new double[numCards];
        cIsImageLoaded = new boolean[numCards];

        cImage = new Image[numCards];
        cSkeletonTexture = new Image[numCards];
        cOverlayTexture = new Image[numCards];
        cModalTextTexture = new Image[numCards];
    }

    private void initCachedResources() {
        headerTextTexture = buildHeaderTextTexture();
    }

    private void drawTexturePro(
        GraphicsContext g,
        Image img,
        double dx,
        double dy,
        double dw,
        double dh
    ) {
        if (img == null) return;
        double imgW = img.getWidth();
        double imgH = img.getHeight();
        if (imgW == 0 || imgH == 0) return;

        double imgRatio = imgW / imgH;
        double canvasRatio = dw / dh;
        double sx, sy, sw, sh;

        if (imgRatio > canvasRatio) {
            sh = imgH;
            sw = imgH * canvasRatio;
            sx = (imgW - sw) * 0.5;
            sy = 0;
        } else {
            sw = imgW;
            sh = imgW / canvasRatio;
            sx = 0;
            sy = (imgH - sh) * 0.5;
        }

        g.drawImage(img, sx, sy, sw, sh, dx, dy, dw, dh);
    }

    private void applyRoundRectClip(
        GraphicsContext g,
        double x,
        double y,
        double w,
        double h,
        double r
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

    private Image buildHeaderTextTexture() {
        Canvas off = new Canvas(500, 30);
        GraphicsContext g = off.getGraphicsContext2D();
        g.setFont(titleFont);
        g.setFill(SUB_TEXT);
        g.fillText("EN CARTELERA (Doble clic para expandir)", 0, 20);
        return off.snapshot(snapshotParams, null);
    }

    private Image buildModalTextTexture(String[] data) {
        Canvas off = new Canvas(400, 350);
        GraphicsContext g = off.getGraphicsContext2D();

        g.setFont(modalTitleFont);
        g.setFill(Color.WHITE);
        g.fillText(data[1], 0, 40);

        g.setFont(subFont);
        g.setFill(RATING_COL);
        g.fillText(
            data[2] + "  \u2022  Calificaci\u00f3n: \u2605 " + data[3],
            0,
            70
        );

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

        return off.snapshot(snapshotParams, null);
    }

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
        return off.snapshot(snapshotParams, null);
    }

    private Image buildOverlayTexture(
        String title,
        String subtitle,
        String rating
    ) {
        Canvas off = new Canvas(CARD_W, CARD_H);
        GraphicsContext g = off.getGraphicsContext2D();
        drawOverlayToContext(g, title, subtitle, rating);
        return off.snapshot(snapshotParams, null);
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
        g.fillText("\u2605 " + rating, tx, ty);
    }

    private void buildCards() {
        final double baseX = 40,
            baseY = 400;

        for (int i = 0; i < numCards; i++) {
            String[] d = MOVIES[i];

            cX[i] = baseX + i * (CARD_W + SPACING);
            cBaseY[i] = baseY;
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
            cTextAlpha[i] = 1.0;
            cTargetTextAlpha[i] = 1.0;
            cTextDelayTimer[i] = 0;
            cHoverT[i] = 0;

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
                cImage[i].progressProperty().addListener((obs, o, nv) -> {
                    if (nv.doubleValue() >= 1.0 || cImage[idx].isError()) {
                        cIsImageLoaded[idx] = nv.doubleValue() >= 1.0;
                        isDirty = true;
                    }
                });
            }
        }
    }

    private void setupInput() {
        canvas.setOnMouseMoved(e -> {
            if (activeModalIndex != -1) return;

            final double mx = e.getX(),
                my = e.getY();
            int newHover = -1;

            for (int i = 0; i < numCards; i++) {
                double w = CARD_W * cScale[i];
                double h = CARD_H * cScale[i];
                double cx = cX[i] + CARD_W * 0.5;
                double cy = cCurrentY[i] + CARD_H * 0.5;
                double rx = cx - w * 0.5;
                double ry = cy - h * 0.5;

                if (mx >= rx && mx <= rx + w && my >= ry && my <= ry + h) {
                    newHover = i;
                    break;
                }
            }

            if (newHover != hoveredIndex) {
                if (hoveredIndex != -1) cTargetScale[hoveredIndex] = 1.0;
                if (newHover != -1) cTargetScale[newHover] = 1.05;
                hoveredIndex = newHover;
                isDirty = true;
            }
        });

        canvas.setOnMouseClicked(e -> {
            if (
                e.getClickCount() == 2 &&
                hoveredIndex != -1 &&
                activeModalIndex == -1
            ) {
                activeModalIndex = hoveredIndex;
                targetModalProgress = 1.0;
                cTargetScale[hoveredIndex] = 1.0;
                cScale[hoveredIndex] = 1.0;
                cScaleVelocity[hoveredIndex] = 0.0;
                cTextAlpha[hoveredIndex] = 0;
                cTargetTextAlpha[hoveredIndex] = 0;
                hoveredIndex = -1;
                modalTween.reset(modalProgress, 1.0);
                modalTween.setDuration(0.4);
                isDirty = true;
            } else if (e.getClickCount() == 1 && activeModalIndex != -1) {
                targetModalProgress = 0.0;
                cTextAlpha[activeModalIndex] = 0;
                cTargetTextAlpha[activeModalIndex] = 0;
                cTextDelayTimer[activeModalIndex] = 0.4;
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
        totalTime += dt;

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

        for (int i = 0; i < numCards; i++) {
            // Hover envelope
            double hoverTarget = (i == hoveredIndex && activeModalIndex == -1)
                ? 1.0
                : 0.0;
            double tHover = 1.0 - Math.exp(-6.0 * dt);
            if (cHoverT[i] != hoverTarget) {
                cHoverT[i] += (hoverTarget - cHoverT[i]) * tHover;
                if (Math.abs(cHoverT[i] - hoverTarget) < 0.001) cHoverT[i] =
                    hoverTarget;
                any = true;
            }

            // Target scale from hover envelope + anticipation
            double anticipation =
                Math.sin(cHoverT[i] * Math.PI) * -0.03 * (1.0 - cHoverT[i]);
            double tScaleFromHover = 1.0 + 0.05 * cHoverT[i] + anticipation;
            cTargetScale[i] = tScaleFromHover;

            // Entry delay
            if (cDelayTimer[i] > 0) {
                cDelayTimer[i] -= dt;
                any = true;
                continue;
            }

            // Text delay timer
            if (cTextDelayTimer[i] > 0) {
                cTextDelayTimer[i] -= dt;
                if (cTextDelayTimer[i] <= 0) cTargetTextAlpha[i] = 1;
            }

            // Text alpha (pre-baked lerp factor)
            cTextAlpha[i] +=
                (cTargetTextAlpha[i] - cTextAlpha[i]) * textLerpFactor;
            if (Math.abs(cTextAlpha[i] - cTargetTextAlpha[i]) < LERP_EPS) {
                cTextAlpha[i] = cTargetTextAlpha[i];
            } else {
                any = true;
            }

            // Idle micro-float
            double yRest =
                Math.abs(cCurrentY[i] - cBaseY[i]) + Math.abs(cYVelocity[i]);
            double idleY = 0;
            if (yRest < 0.5) {
                idleY = Math.sin(totalTime * 0.9 + i * 0.7) * 1.5;
            }
            cTargetY[i] = cBaseY[i] + idleY;

            // At-rest scheduler
            boolean atRest =
                Math.abs(cScale[i] - cTargetScale[i]) < LERP_EPS &&
                Math.abs(cScaleVelocity[i]) < LERP_EPS &&
                Math.abs(cOpacity[i] - cTargetOpacity[i]) < LERP_EPS &&
                Math.abs(cOpacityVelocity[i]) < LERP_EPS &&
                Math.abs(cCurrentY[i] - cTargetY[i]) < LERP_EPS &&
                Math.abs(cYVelocity[i]) < LERP_EPS &&
                (!cIsImageLoaded[i] || cImageAlpha[i] >= 1.0) &&
                Math.abs(cTextAlpha[i] - cTargetTextAlpha[i]) < LERP_EPS;

            if (atRest) {
                cScale[i] = cTargetScale[i];
                cOpacity[i] = cTargetOpacity[i];
                cYVelocity[i] = 0;
                cCurrentY[i] = cTargetY[i];
                if (cIsImageLoaded[i]) cImageAlpha[i] = 1.0;
                continue;
            }

            // Scale spring (pre-baked)
            double newScale =
                scaleSpring.xa * cScale[i] +
                scaleSpring.xb * cScaleVelocity[i] +
                scaleSpring.xc * cTargetScale[i];
            double newScaleV =
                scaleSpring.va * cScale[i] +
                scaleSpring.vb * cScaleVelocity[i] +
                scaleSpring.vc * cTargetScale[i];
            cScale[i] = newScale;
            cScaleVelocity[i] = newScaleV;
            if (
                Math.abs(cScale[i] - cTargetScale[i]) < LERP_EPS &&
                Math.abs(cScaleVelocity[i]) < LERP_EPS
            ) {
                cScale[i] = cTargetScale[i];
                cScaleVelocity[i] = 0;
            } else {
                any = true;
            }

            // Opacity spring (pre-baked)
            double newOp =
                opacitySpring.xa * cOpacity[i] +
                opacitySpring.xb * cOpacityVelocity[i] +
                opacitySpring.xc * cTargetOpacity[i];
            double newOpV =
                opacitySpring.va * cOpacity[i] +
                opacitySpring.vb * cOpacityVelocity[i] +
                opacitySpring.vc * cTargetOpacity[i];
            cOpacity[i] = newOp;
            cOpacityVelocity[i] = newOpV;
            if (
                Math.abs(cOpacity[i] - cTargetOpacity[i]) < LERP_EPS &&
                Math.abs(cOpacityVelocity[i]) < LERP_EPS
            ) {
                cOpacity[i] = cTargetOpacity[i];
                cOpacityVelocity[i] = 0;
            } else {
                any = true;
            }

            // Y spring (pre-baked)
            double newY =
                ySpring.xa * cCurrentY[i] +
                ySpring.xb * cYVelocity[i] +
                ySpring.xc * cTargetY[i];
            double newYV =
                ySpring.va * cCurrentY[i] +
                ySpring.vb * cYVelocity[i] +
                ySpring.vc * cTargetY[i];
            cCurrentY[i] = newY;
            cYVelocity[i] = newYV;
            if (
                Math.abs(cCurrentY[i] - cTargetY[i]) < LERP_EPS &&
                Math.abs(cYVelocity[i]) < LERP_EPS
            ) {
                cCurrentY[i] = cTargetY[i];
                cYVelocity[i] = 0;
            } else {
                any = true;
            }

            // Image fade-in (linear ramp, no double smoothstep)
            if (cIsImageLoaded[i] && cImageAlpha[i] < 1.0) {
                cImageAlpha[i] = Math.min(1.0, cImageAlpha[i] + imageFadeRate);
                any = true;
            }
        }

        return any;
    }

    private void render() {
        gc.setFill(BG);
        gc.fillRect(0, 0, APP_W, APP_H);

        gc.drawImage(headerTextTexture, 40, 350);

        for (int i = 0; i < numCards; i++) {
            if (
                i != hoveredIndex && i != activeModalIndex && cOpacity[i] > 0.01
            ) {
                drawCard(i);
            }
        }

        if (
            hoveredIndex != -1 &&
            hoveredIndex != activeModalIndex &&
            cOpacity[hoveredIndex] > 0.01
        ) {
            drawCard(hoveredIndex);
        }

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

        if (rx + w < 0 || rx > APP_W || ry + h < 0 || ry > APP_H) return;

        gc.save();
        gc.setGlobalAlpha(cOpacity[i]);
        double currentRadius = RADIUS * cScale[i];

        applyRoundRectClip(gc, rx, ry, w, h, currentRadius);

        if (cIsImageLoaded[i] && cImageAlpha[i] >= 1.0) {
            drawTexturePro(gc, cImage[i], rx, ry, w, h);
            gc.setGlobalAlpha(cOpacity[i] * cTextAlpha[i]);
            drawTexturePro(gc, cOverlayTexture[i], rx, ry, w, h);
        } else if (cIsImageLoaded[i] && cImageAlpha[i] > 0.0) {
            drawTexturePro(gc, cSkeletonTexture[i], rx, ry, w, h);
            gc.setGlobalAlpha(cOpacity[i] * cImageAlpha[i]);
            drawTexturePro(gc, cImage[i], rx, ry, w, h);
            gc.setGlobalAlpha(cOpacity[i] * cTextAlpha[i]);
            drawTexturePro(gc, cOverlayTexture[i], rx, ry, w, h);
        } else {
            drawTexturePro(gc, cSkeletonTexture[i], rx, ry, w, h);
        }

        gc.restore();

        // Antialiased border
        if (cOpacity[i] > 0.01) {
            gc.save();
            gc.setGlobalAlpha(cOpacity[i]);
            gc.setStroke(BG);
            gc.setLineWidth(1.5);
            gc.strokeRoundRect(
                rx,
                ry,
                w,
                h,
                currentRadius * 2,
                currentRadius * 2
            );
            gc.restore();
        }
    }

    private void renderModalCapsule(int i) {
        double ease =
            modalProgress * modalProgress * (3.0 - 2.0 * modalProgress);

        // Dark overlay
        gc.setGlobalAlpha(ease * 0.95);
        gc.setFill(overlayBlack);
        gc.fillRect(0, 0, APP_W, APP_H);
        gc.setGlobalAlpha(1.0);

        final double startX = cX[i];
        final double startY = cCurrentY[i];
        final double targetX = (APP_W - MODAL_W) * 0.5;
        final double targetY = (APP_H - MODAL_H) * 0.5;
        final double currX = startX + (targetX - startX) * ease;
        final double currY = startY + (targetY - startY) * ease;
        final double currW = CARD_W + (MODAL_W - CARD_W) * ease;
        final double currH = CARD_H + (MODAL_H - CARD_H) * ease;
        final double currRadius = RADIUS + (24.0 - RADIUS) * ease;
        final double posterW = CARD_W + (MODAL_W * 0.4 - CARD_W) * ease;
        final double posterH = currH;

        gc.save();

        double blurAmount = Math.sin(ease * Math.PI) * 15.0;
        if (blurAmount > 0.5) {
            reusableBlur.setRadius(blurAmount);
            gc.setEffect(reusableBlur);
        }

        applyRoundRectClip(gc, currX, currY, currW, currH, currRadius);

        gc.setFill(MODAL_BG);
        gc.fillRect(currX, currY, currW, currH);

        Image posterImage = (cIsImageLoaded[i] && cImage[i] != null)
            ? cImage[i]
            : cSkeletonTexture[i];
        drawTexturePro(gc, posterImage, currX, currY, posterW, posterH);

        // Overlay fade-out (synced with card text alpha for seamless transition)
        double overlayAlpha = 1.0 - ease;
        if (overlayAlpha > 0.001) {
            gc.setGlobalAlpha(cOpacity[i] * overlayAlpha * cTextAlpha[i]);
            drawTexturePro(
                gc,
                cOverlayTexture[i],
                currX,
                currY,
                posterW,
                posterH
            );
        }

        // Modal fade gradient
        if (ease > 0.001) {
            gc.setGlobalAlpha(ease);
            gc.setFill(modalFadeGradient);
            gc.fillRect(currX + posterW - 50, currY, 51, posterH);
        }

        // Text with wipe reveal
        double textAlpha = smoothstep(0.40, 1.0, ease);
        if (textAlpha > 0.001) {
            double textOffsetY = (1.0 - smoothstep(0.40, 0.55, ease)) * -20.0;
            double textX = currX + posterW + 40;
            double textY = currY + 40 + textOffsetY;

            gc.setGlobalAlpha(textAlpha);
            gc.save();
            gc.beginPath();
            gc.rect(
                textX - 4,
                textY - 4,
                410,
                360 * smoothstep(0.40, 0.95, ease)
            );
            gc.clip();
            gc.drawImage(cModalTextTexture[i], textX, textY);
            gc.restore();
        }

        gc.restore();

        // Border
        gc.save();
        gc.setGlobalAlpha(1.0);
        gc.setLineWidth(1.5);
        gc.setStroke(SKELETON);
        gc.strokeRoundRect(
            currX,
            currY,
            currW,
            currH,
            currRadius * 2,
            currRadius * 2
        );
        gc.restore();
    }

    public static void main(String[] args) {
        System.setProperty("prism.forceGPU", "true");
        launch(args);
    }
}
