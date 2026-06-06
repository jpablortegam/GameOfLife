package com.example.gameoflife;

import java.util.concurrent.ForkJoinPool;
import javafx.animation.AnimationTimer;
import javafx.application.Application;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.geometry.Rectangle2D;
import javafx.scene.Scene;
import javafx.scene.SnapshotParameters;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.image.Image;
import javafx.scene.input.KeyCode;
import javafx.scene.input.ScrollEvent;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.stage.Screen;
import javafx.stage.Stage;

public class CanvasMovieApp extends Application {

    private static final int MOVIE_COUNT = 10;
    private static final String[][] MOVIES = generateMovies(MOVIE_COUNT);

    private static final double CARD_W = 192;
    private static final double CARD_H = 288;
    private static final double SPACING = 18;
    private static final double RADIUS = 18;
    private static final double LERP_EPS = 0.001;

    private static final double SCALE_STIFFNESS = 300.0;
    private static final double SCALE_DAMPING = 35.0;
    private static final double OPACITY_STIFFNESS = 150.0;
    private static final double OPACITY_DAMPING = 25.0;
    private static final double Y_STIFFNESS = 200.0;
    private static final double Y_DAMPING = 29.0;

    private static final double MODAL_W = 800;
    private static final double MODAL_H = 450;
    private static final int PARALLEL_LAYOUT_THRESHOLD = 512;

    private static String[][] generateMovies(int n) {
        String[][] m = new String[n][4];
        String[] titles = {
            "The Dark Knight",
            "Blade Runner 2049",
            "Dune",
            "Inception",
            "Interstellar",
            "The Matrix",
            "Parasite",
            "Everything Everywhere All At Once",
            "Spirited Away",
            "Your Name",
            "Weathering With You",
            "Suzume",
            "Oppenheimer",
            "Barbie",
            "The Batman",
            "Joker",
            "Tenet",
            "Dunkirk",
            "1917",
            "Ford v Ferrari",
            "Mad Max Fury Road",
            "Fury",
            "Sicario",
            "Prisoners",
            "Arrival",
            "The Revenant",
            "Whiplash",
            "La La Land",
            "Get Out",
            "Us",
            "Nope",
            "The Whale",
            "Aftersun",
            "Past Lives",
            "The Holdovers",
            "Anatomy of a Fall",
            "Zone of Interest",
            "Poor Things",
            "The Boy and the Heron",
            "Godzilla Minus One",
            "Shrek",
            "Toy Story",
            "Finding Nemo",
            "The Incredibles",
            "Ratatouille",
            "WALL-E",
            "Up",
            "Inside Out",
            "Coco",
            "Soul",
            "Encanto",
            "Luca",
            "Turning Red",
            "Elemental",
            "Spider Verse",
            "Across the Spider Verse",
            "Puss in Boots",
            "The Bad Guys",
            "The Super Mario Bros Movie",
            "Wonka",
            "John Wick",
            "Extraction",
            "The Gray Man",
            "Bullet Train",
            "Nobody",
            "Atomic Blonde",
            "The Equalizer",
            "Man on Fire",
            "Taken",
            "The Bourne Identity",
            "Mission Impossible",
            "Top Gun Maverick",
            "The Covenant",
            "Green Room",
            "Blue Ruin",
            "Dragged Across Concrete",
            "Hell or High Water",
            "Wind River",
            "No Country for Old Men",
            "There Will Be Blood",
            "The Social Network",
            "The Fighter",
            "Silver Linings Playbook",
            "American Hustle",
            "The Wolf of Wall Street",
            "The Irishman",
            "Killers of the Flower Moon",
            "The Departed",
            "Goodfellas",
            "Casino",
            "Scarface",
            "The Godfather",
            "Pulp Fiction",
            "Reservoir Dogs",
            "Inglourious Basterds",
            "Django Unchained",
            "Once Upon a Time in Hollywood",
            "Jackie Brown",
            "The Hateful Eight",
            "Kill Bill",
            "Superbad",
            "Step Brothers",
            "Anchorman",
            "Bridesmaids",
            "The Hangover",
            "Old School",
            "Zoolander",
            "Dodgeball",
            "Tropic Thunder",
            "Borat",
            "Bruno",
            "The Dictator",
        };
        String[] genres = {
            "Accion",
            "Drama",
            "Ciencia Ficcion",
            "Thriller",
            "Comedia",
            "Animacion",
            "Aventura",
            "Crimen",
            "Fantasia",
            "Romance",
            "Documental",
            "Terror",
        };
        String[] years = {
            "2008",
            "2017",
            "2021",
            "2010",
            "2014",
            "1999",
            "2019",
            "2022",
            "2001",
            "2016",
            "2019",
            "2022",
            "2023",
            "2023",
            "2022",
            "2019",
            "2020",
            "2017",
            "2019",
            "2019",
            "2015",
            "2014",
            "2015",
            "2013",
        };
        for (int i = 0; i < n; i++) {
            String poster =
                "https://images.unsplash.com/photo-1536440136628-849c177e76a1?fm=jpg&q=85&w=400&rnd=" +
                i;
            String title = titles[i % titles.length];
            String genre = genres[(i * 7) % genres.length];
            String year = years[(i * 13) % years.length];
            String rating = String.format("%.1f", 5.0 + ((i * 3.7) % 5.0));
            m[i][0] = poster;
            m[i][1] = title;
            m[i][2] = genre + " \u2022 " + year;
            m[i][3] = rating;
        }
        return m;
    }

    private ECS ecs;
    private AssetManager assetManager;
    private TextureAtlas atlas;
    private AnimationEngine animEngine;
    private SpatialHashGrid spatialGrid;
    private DirtyRegionTracker dirtyTracker;
    private RenderQueue renderQueue;
    private LayerCache layerCache;
    private RenderGraph renderGraph;
    private Profiler profiler;

    private Canvas canvas;
    private GraphicsContext gc;
    private int hoveredIndex = -1;
    private long lastNanos = 0;
    private boolean isDirty = true;

    private int activeModalIndex = -1;
    private double modalProgress = 0.0;
    private double targetModalProgress = 0.0;
    private final AnimationSystem.Tween modalTween = new AnimationSystem.Tween(
        0.4,
        0.0,
        1.0
    );

    private double appW = 1020;
    private double appH = 810;
    private double maxScroll = 0;
    private int gridCols = 4;

    private double scrollY = 0;
    private double targetScrollY = 0;
    private double scrollVelocity = 0;
    private int firstVisibleIndex = 0;
    private int lastVisibleIndex = 0;

    private double lastMouseX = 0;
    private double lastMouseY = 0;

    private final Image[] cardSnapshotTex = new Image[MOVIE_COUNT];
    private final boolean[] cardSnapshotDirty = new boolean[MOVIE_COUNT];
    private final int[] pickingBuffer = new int[MOVIE_COUNT];
    private final ForkJoinPool jobPool = new ForkJoinPool(
        Math.max(1, Runtime.getRuntime().availableProcessors() - 1)
    );
    private double cachedBackgroundW = -1;
    private double cachedBackgroundH = -1;
    private int renderGraceCounter = 0;
    private final SnapshotParameters snapshotParams = new SnapshotParameters();

    @Override
    public void start(Stage stage) {
        Rectangle2D bounds = Screen.getPrimary().getVisualBounds();
        appW = bounds.getWidth();
        appH = bounds.getHeight();
        gridCols = Math.max(1, (int) ((appW - 80) / (CARD_W + SPACING)));
        int initRows = (MOVIE_COUNT + gridCols - 1) / gridCols;
        maxScroll = Math.max(0, initRows * (CARD_H + SPACING) - appH + 100);
        lastMouseX = appW * 0.5;
        lastMouseY = appH * 0.5;

        canvas = new Canvas(appW, appH);
        gc = canvas.getGraphicsContext2D();

        ecs = new ECS();
        assetManager = new AssetManager(200);
        animEngine = new AnimationEngine();
        spatialGrid = new SpatialHashGrid(appW, appH, CARD_W + SPACING);
        dirtyTracker = new DirtyRegionTracker();
        renderQueue = new RenderQueue();
        layerCache = new LayerCache();
        profiler = new Profiler();
        atlas = new TextureAtlas();

        snapshotParams.setFill(Color.TRANSPARENT);

        renderGraph = new RenderGraph(profiler);
        renderGraph.addPass(RenderGraph.PassType.INPUT, this::inputPass);
        renderGraph.addPass(RenderGraph.PassType.ANIMATION, this::animationPass);
        renderGraph.addPass(RenderGraph.PassType.LAYOUT, this::layoutPass);
        renderGraph.addPass(RenderGraph.PassType.VISIBILITY, this::visibilityPass);
        renderGraph.addPass(RenderGraph.PassType.RENDER_QUEUE_BUILD, this::renderQueueBuildPass);
        renderGraph.addPass(RenderGraph.PassType.DRAW, this::drawPass);

        initECS();
        setupInput();

        StackPane root = new StackPane(canvas);
        Scene scene = new Scene(root, appW, appH);
        root.setStyle("-fx-background-color: #0f0f0f;");

        canvas.widthProperty().bind(root.widthProperty());
        canvas.heightProperty().bind(root.heightProperty());

        root.widthProperty().addListener((obs, o, n) -> {
            appW = n.doubleValue();
            gridCols = Math.max(1, (int) ((appW - 80) / (CARD_W + SPACING)));
            int r = (MOVIE_COUNT + gridCols - 1) / gridCols;
            maxScroll = Math.max(0, r * (CARD_H + SPACING) - appH + 100);
            spatialGrid = new SpatialHashGrid(appW, appH, CARD_W + SPACING);
            layerCache.invalidateAll();
            dirtyTracker.markFull();
            isDirty = true;
        });
        root.heightProperty().addListener((obs, o, n) -> {
            appH = n.doubleValue();
            int r = (MOVIE_COUNT + gridCols - 1) / gridCols;
            maxScroll = Math.max(0, r * (CARD_H + SPACING) - appH + 100);
            spatialGrid = new SpatialHashGrid(appW, appH, CARD_W + SPACING);
            layerCache.invalidateAll();
            dirtyTracker.markFull();
            isDirty = true;
        });

        new AnimationTimer() {
            @Override
            public void handle(long now) {
                double dt = (lastNanos == 0) ? 0.016 : (now - lastNanos) / 1e9;
                if (dt > 0.1) dt = 0.016;
                lastNanos = now;

                profiler.beginFrame();
                renderGraph.execute(dt);
            }
        }.start();

        stage.setTitle("CineStream \u2013 Ultra Performance Engine");
        stage.setScene(scene);
        stage.setMaximized(true);
        stage.show();
    }

    @Override
    public void stop() {
        jobPool.shutdown();
    }

    private void initECS() {
        double gridW = gridCols * (CARD_W + SPACING);
        double startX = (appW - gridW) * 0.5 + SPACING * 0.5;
        double startY = 60;

        for (int i = 0; i < MOVIE_COUNT; i++) {
            int e = ecs.create();
            String[] d = MOVIES[i];
            int col = i % gridCols;
            int row = i / gridCols;

            ecs.setX(e, startX + col * (CARD_W + SPACING));
            ecs.setY(e, startY + row * (CARD_H + SPACING));
            ecs.setW(e, CARD_W);
            ecs.setH(e, CARD_H);
            ecs.setTargetScale(e, 1.0);
            ecs.setScale(e, 1.0);
            ecs.setScaleVel(e, 0.0);
            ecs.setTargetOpacity(e, 1.0);
            ecs.setOpacity(e, 0.0);
            ecs.setOpacityVel(e, 0.0);
            ecs.setTargetY(e, startY + row * (CARD_H + SPACING));
            ecs.setCurrentY(e, startY + row * (CARD_H + SPACING) + 40);
            ecs.setYVel(e, 0.0);
            ecs.setDelayTimer(e, i * 0.1);

            ecs.setSkeletonTex(e, buildSkeletonTexture(d[1], d[2], d[3]));
            ecs.setOverlayTex(e, buildOverlayTexture(d[1], d[2], d[3]));
            ecs.setModalTextTex(e, buildModalTextTexture(d));

            Image img = assetManager.computeImageIfAbsent(d[0], url ->
                new Image(url, CARD_W, CARD_H, false, true, true)
            );

            if (img.getProgress() >= 1.0 && !img.isError()) {
                ecs.setImageLoaded(e, true);
                ecs.setImageAlpha(e, 1.0);
            } else {
                final int idx = e;
                ChangeListener<Number> listener = new ChangeListener<Number>() {
                    @Override
                    public void changed(
                        ObservableValue<? extends Number> obs,
                        Number o,
                        Number nv
                    ) {
                        if (nv.doubleValue() >= 1.0 || img.isError()) {
                            ecs.setImageLoaded(idx, nv.doubleValue() >= 1.0);
                            cardSnapshotDirty[idx] = true;
                            isDirty = true;
                            img.progressProperty().removeListener(this);
                        }
                    }
                };
                img.progressProperty().addListener(listener);
            }
            ecs.setImage(e, img);
            cardSnapshotDirty[e] = true;
        }

        buildAtlas();
        updateSpatialGrid();
    }

    private void buildAtlas() {
        for (int i = 0; i < MOVIE_COUNT; i++) {
            if (ecs.getImage(i) != null) {
                atlas.add("poster_" + i, ecs.getImage(i));
            }
            atlas.add("skeleton_" + i, ecs.getSkeletonTex(i));
            atlas.add("overlay_" + i, ecs.getOverlayTex(i));
            atlas.add("modaltext_" + i, ecs.getModalTextTex(i));
        }
        atlas.build();
    }

    private void setupInput() {
        canvas.setOnMouseMoved(e -> {
            lastMouseX = e.getX();
            lastMouseY = e.getY();
            if (activeModalIndex != -1) return;
            checkHoverAt(lastMouseX, lastMouseY);
        });

        canvas.setOnMouseClicked(e -> {
            lastMouseX = e.getX();
            lastMouseY = e.getY();
            if (e.getClickCount() == 2 && hoveredIndex != -1 && activeModalIndex == -1) {
                activeModalIndex = hoveredIndex;
                targetModalProgress = 1.0;
                ecs.setTargetScale(hoveredIndex, 1.0);
                ecs.setScale(hoveredIndex, 1.0);
                ecs.setScaleVel(hoveredIndex, 0.0);
                hoveredIndex = -1;
                modalTween.reset(modalProgress, 1.0);
                modalTween.setDuration(0.4);
                isDirty = true;
                dirtyTracker.markFull();
            } else if (e.getClickCount() == 1 && activeModalIndex != -1) {
                targetModalProgress = 0.0;
                modalTween.reset(modalProgress, 0.0);
                modalTween.setDuration(0.35);
                isDirty = true;
                dirtyTracker.markFull();
            }
        });

        canvas.setOnMouseExited(e -> {
            int h = hoveredIndex;
            if (h != -1) {
                ecs.setTargetScale(h, 1.0);
                final int fh = h;
                animEngine.animateFloat(ecs.getScale(h), 1.0, 0.25, v ->
                    ecs.setScale(fh, v)
                );
                hoveredIndex = -1;
                isDirty = true;
                dirtyTracker.markFull();
            }
        });

        canvas.setOnScroll((ScrollEvent e) -> {
            targetScrollY = Math.max(0,
                Math.min(targetScrollY - e.getDeltaY() * 0.5, maxScroll));
            isDirty = true;
            dirtyTracker.markFull();
        });

        canvas.setOnKeyPressed(e -> {
            if (e.getCode() == KeyCode.P) {
                profiler.toggle();
                isDirty = true;
            }
        });
        canvas.setFocusTraversable(true);
    }

    private void updateSpatialGrid() {
        spatialGrid.clear();
        for (int i = 0; i < MOVIE_COUNT; i++) {
            if (!ecs.exists(i)) continue;
            double s = ecs.getScale(i);
            double w = CARD_W * s,
                h = CARD_H * s;
            double cx = ecs.getX(i) + CARD_W * 0.5;
            double cy = ecs.getCurrentY(i) + CARD_H * 0.5;
            spatialGrid.insert(i, cx - w * 0.5, cy - h * 0.5, w, h);
        }
    }

    private void checkHoverAt(double mx, double my) {
        int candidateCount = spatialGrid.queryPoint(mx, my, pickingBuffer);
        int newHover = -1;

        for (int i = 0; i < candidateCount; i++) {
            int idx = pickingBuffer[i];
            if (!ecs.exists(idx)) continue;
            double s = ecs.getScale(idx);
            double cw = CARD_W * s,
                ch = CARD_H * s;
            double cx = ecs.getX(idx) + CARD_W * 0.5;
            double cy = ecs.getCurrentY(idx) + CARD_H * 0.5;
            double rx = cx - cw * 0.5,
                ry = cy - ch * 0.5;
            if (mx >= rx && mx <= rx + cw && my >= ry && my <= ry + ch) {
                newHover = idx;
                break;
            }
        }

        if (newHover != hoveredIndex) {
            int oldHover = hoveredIndex;
            if (oldHover != -1) {
                ecs.setTargetScale(oldHover, 1.0);
                final int fOld = oldHover;
                animEngine.animateFloat(ecs.getScale(oldHover), 1.0, 0.25,
                    v -> ecs.setScale(fOld, v));
            }
            if (newHover != -1) {
                ecs.setTargetScale(newHover, 1.05);
                final int fNew = newHover;
                animEngine.animateFloat(ecs.getScale(newHover), 1.05, 0.25,
                    v -> ecs.setScale(fNew, v));
            }
            hoveredIndex = newHover;
            isDirty = true;
            dirtyTracker.markFull();
        }
    }

    private void inputPass(double dt) {
    }

    private void animationPass(double dt) {
        boolean any = false;

        if (!modalTween.isFinished()) {
            modalTween.update(dt);
            modalProgress = modalTween.getValue();
            any = true;
        } else {
            modalProgress = targetModalProgress;
            if (targetModalProgress == 0.0 && activeModalIndex != -1) {
                activeModalIndex = -1;
                checkHoverAt(lastMouseX, lastMouseY);
                any = true;
            }
        }

        for (int i = 0; i < MOVIE_COUNT; i++) {
            if (!ecs.exists(i)) continue;

            if (ecs.getDelayTimer(i) > 0) {
                ecs.setDelayTimer(i, ecs.getDelayTimer(i) - dt);
                any = true;
                continue;
            }

            double forceS =
                SCALE_STIFFNESS * (ecs.getTargetScale(i) - ecs.getScale(i)) -
                SCALE_DAMPING * ecs.getScaleVel(i);
            ecs.setScaleVel(i, ecs.getScaleVel(i) + forceS * dt);
            ecs.setScale(i, ecs.getScale(i) + ecs.getScaleVel(i) * dt);
            if (
                Math.abs(ecs.getScale(i) - ecs.getTargetScale(i)) < LERP_EPS &&
                Math.abs(ecs.getScaleVel(i)) < LERP_EPS
            ) {
                ecs.setScale(i, ecs.getTargetScale(i));
                ecs.setScaleVel(i, 0);
            } else {
                any = true;
                if (ecs.getScale(i) != ecs.getTargetScale(i)) {
                    dirtyTracker.add(
                        ecs.getX(i) - 10,
                        ecs.getCurrentY(i) - 10,
                        CARD_W + 20,
                        CARD_H + 20
                    );
                }
            }

            double forceO =
                OPACITY_STIFFNESS *
                    (ecs.getTargetOpacity(i) - ecs.getOpacity(i)) -
                OPACITY_DAMPING * ecs.getOpacityVel(i);
            ecs.setOpacityVel(i, ecs.getOpacityVel(i) + forceO * dt);
            ecs.setOpacity(i, ecs.getOpacity(i) + ecs.getOpacityVel(i) * dt);
            if (
                Math.abs(ecs.getOpacity(i) - ecs.getTargetOpacity(i)) <
                    LERP_EPS &&
                Math.abs(ecs.getOpacityVel(i)) < LERP_EPS
            ) {
                ecs.setOpacity(i, ecs.getTargetOpacity(i));
                ecs.setOpacityVel(i, 0);
            } else {
                any = true;
            }

            double forceY =
                Y_STIFFNESS * (ecs.getTargetY(i) - ecs.getCurrentY(i)) -
                Y_DAMPING * ecs.getYVel(i);
            ecs.setYVel(i, ecs.getYVel(i) + forceY * dt);
            ecs.setCurrentY(i, ecs.getCurrentY(i) + ecs.getYVel(i) * dt);
            if (
                Math.abs(ecs.getCurrentY(i) - ecs.getTargetY(i)) < LERP_EPS &&
                Math.abs(ecs.getYVel(i)) < LERP_EPS
            ) {
                ecs.setCurrentY(i, ecs.getTargetY(i));
                ecs.setYVel(i, 0);
            } else {
                any = true;
            }

            if (ecs.isImageLoaded(i) && ecs.getImageAlpha(i) < 1.0) {
                ecs.setImageAlpha(
                    i,
                    Math.min(1.0, ecs.getImageAlpha(i) + 3.5 * dt)
                );
                any = true;
            }
        }

        animEngine.update(dt);

        double scrollDiff = targetScrollY - scrollY;
        if (Math.abs(scrollDiff) > 0.5) {
            scrollVelocity += scrollDiff * 8.0 * dt;
            scrollVelocity *= 0.92;
            scrollY += scrollVelocity * dt;
            any = true;
            dirtyTracker.markFull();
        } else {
            scrollY = targetScrollY;
            scrollVelocity = 0;
        }

        if (any) {
            isDirty = true;
            renderGraceCounter = 3;
        }
    }

    private void layoutPass(double dt) {
        double gridW = gridCols * (CARD_W + SPACING);
        double startX = (appW - gridW) * 0.5 + SPACING * 0.5;
        if (MOVIE_COUNT >= PARALLEL_LAYOUT_THRESHOLD) {
            jobPool.submit(() ->
                java.util.stream.IntStream.range(0, MOVIE_COUNT).parallel()
                    .forEach(i -> layoutEntity(i, startX))
            ).join();
        } else {
            for (int i = 0; i < MOVIE_COUNT; i++) {
                layoutEntity(i, startX);
            }
        }
        updateSpatialGrid();
    }

    private void layoutEntity(int i, double startX) {
        if (!ecs.exists(i)) return;
        int col = i % gridCols;
        int row = i / gridCols;
        ecs.setX(i, startX + col * (CARD_W + SPACING));
        ecs.setTargetY(i, 60 + row * (CARD_H + SPACING) - scrollY);
    }

    private void visibilityPass(double dt) {
        double itemH = CARD_H + SPACING;
        int firstRow = Math.max(0, (int) ((scrollY - CARD_H) / itemH));
        int lastRow = Math.min((MOVIE_COUNT + gridCols - 1) / gridCols - 1,
            (int) Math.ceil((scrollY + appH) / itemH) + 1);
        firstVisibleIndex = Math.max(0, firstRow * gridCols);
        lastVisibleIndex = Math.min(MOVIE_COUNT - 1, (lastRow + 1) * gridCols - 1);
    }

    private void renderQueueBuildPass(double dt) {
        if (!shouldRenderFrame()) return;

        renderQueue.clear();
        profiler.setEntityCount(MOVIE_COUNT);

        Image background = getBackgroundLayer();
        renderQueue.image(background, 0, 0, appW, appH).z(-10).layer(0);

        int visibleCount = 0;
        for (int i = firstVisibleIndex; i <= lastVisibleIndex && i < MOVIE_COUNT; i++) {
            if (!ecs.exists(i)) continue;
            if (i == hoveredIndex || i == activeModalIndex) continue;
            if (ecs.getOpacity(i) < 0.01) continue;
            buildCardCommands(i);
            visibleCount++;
        }
        if (hoveredIndex != -1 && hoveredIndex != activeModalIndex
            && ecs.getOpacity(hoveredIndex) > 0.01) {
            buildCardCommands(hoveredIndex);
            visibleCount++;
        }
        profiler.setVisibleCards(visibleCount);

        if (activeModalIndex != -1 && modalProgress > 0.001) {
            buildModalCommands(activeModalIndex);
        }

        if (profiler.isVisible()) {
            profiler.render(renderQueue, appW, appH);
        }
    }

    private void buildCardCommands(int i) {
        double s = ecs.getScale(i);
        double w = CARD_W * s,
            h = CARD_H * s;
        double cx = ecs.getX(i) + CARD_W * 0.5;
        double cy = ecs.getCurrentY(i) + CARD_H * 0.5;
        double rx = cx - w * 0.5,
            ry = cy - h * 0.5;

        if (rx > appW || rx + w < 0 || ry > appH || ry + h < 0) return;

        double opacity = ecs.getOpacity(i);
        Image snapshot = getCardSnapshot(i);
        int cardZ = (i == hoveredIndex) ? 5000 : i * 2;

        renderQueue.image(snapshot, rx, ry, w, h)
            .opacity(opacity)
            .z(cardZ)
            .layer(1);
    }

    private void buildModalCommands(int i) {
        double ease =
            modalProgress * modalProgress * (3.0 - 2.0 * modalProgress);

        renderQueue.fill(
                0,
                0,
                appW,
                appH,
                Color.rgb(0, 0, 0, ease * 0.85)
            )
                .z(9000)
                .layer(3);

        double startW = CARD_W,
            startH = CARD_H;
        double startX = ecs.getX(i),
            startY = ecs.getCurrentY(i);
        double targetX = (appW - MODAL_W) * 0.5;
        double targetY = (appH - MODAL_H) * 0.5;
        double currX = startX + (targetX - startX) * ease;
        double currY = startY + (targetY - startY) * ease;
        double currW = startW + (MODAL_W - startW) * ease;
        double currH = startH + (MODAL_H - startH) * ease;
        double currRadius = RADIUS + (24.0 - RADIUS) * ease;
        double posterW = startW + (MODAL_W * 0.4 - startW) * ease;

        Color bgColor = Color.web("#252525").interpolate(
            Color.web("#1A1A1A"),
            ease
        );
        renderQueue.fill(currX, currY, currW, currH, bgColor)
                .z(9002)
                .layer(3);

        Image posterImg = (ecs.isImageLoaded(i) && ecs.getImage(i) != null)
            ? ecs.getImage(i)
            : ecs.getSkeletonTex(i);

        renderQueue.image(
                posterImg,
                currX,
                currY,
                posterW,
                currH
            )
                .cover(true)
                .z(9003)
                .layer(3);

        double overlayAlpha = 1.0 - ease;
        if (overlayAlpha > 0.001) {
            renderQueue.image(
                    ecs.getOverlayTex(i),
                    currX,
                    currY,
                    posterW,
                    currH
                )
                    .opacity(ecs.getOpacity(i) * overlayAlpha)
                    .z(9004)
                    .layer(3);
        }

        if (ease > 0.001) {
            renderQueue.fill(
                    currX + posterW - 50,
                    currY,
                    51,
                    currH,
                    assetManager.getGradient("modalFade")
                )
                    .opacity(ease)
                    .z(9005)
                    .layer(3);
        }

        renderQueue.strokeRound(
                currX,
                currY,
                currW,
                currH,
                currRadius,
                bgColor
            )
                .lineWidth(1.5)
                .z(9006)
                .layer(3);

        double textAlpha = smoothstep(0.20, 0.80, ease);
        if (textAlpha > 0.001) {
            double textOffsetY = (1.0 - textAlpha) * 20.0;
            renderQueue.image(
                    ecs.getModalTextTex(i),
                    currX + posterW + 40,
                    currY + 40 + textOffsetY,
                    ecs.getModalTextTex(i).getWidth(),
                    ecs.getModalTextTex(i).getHeight()
                )
                    .opacity(textAlpha)
                    .z(9007)
                    .layer(3);
        }
    }

    private void drawPass(double dt) {
        if (!shouldRenderFrame()) return;
        isDirty = false;

        renderQueue.execute(gc);
        profiler.setDrawCalls(renderQueue.getDrawCalls());
        profiler.setTextureCount(atlas.size());
        renderQueue.clear();
        dirtyTracker.clear();
    }

    private boolean shouldRenderFrame() {
        if (renderGraceCounter > 0) {
            renderGraceCounter--;
            return true;
        }
        return isDirty || profiler.isVisible();
    }

    private Image getCardSnapshot(int i) {
        if (cardSnapshotTex[i] == null || cardSnapshotDirty[i]) {
            cardSnapshotTex[i] = buildCardSnapshot(i);
            cardSnapshotDirty[i] = false;
        }
        return cardSnapshotTex[i];
    }

    private Image getBackgroundLayer() {
        Image cached = layerCache.get(LayerCache.Layer.BACKGROUND);
        if (cached != null && cachedBackgroundW == appW && cachedBackgroundH == appH) {
            return cached;
        }

        Canvas off = new Canvas(appW, appH);
        GraphicsContext g = off.getGraphicsContext2D();
        g.setFill(Color.web("#0f0f0f"));
        g.fillRect(0, 0, appW, appH);
        g.setFill(Color.rgb(255, 255, 255, 0.70));
        g.setFont(assetManager.getFont("title"));
        g.fillText("EN CARTELERA (Doble clic para expandir)", 40, 35);
        Image image = off.snapshot(snapshotParams, null);
        layerCache.cache(LayerCache.Layer.BACKGROUND, image);
        cachedBackgroundW = appW;
        cachedBackgroundH = appH;
        return image;
    }

    private Image buildCardSnapshot(int i) {
        Canvas off = new Canvas(CARD_W, CARD_H);
        GraphicsContext g = off.getGraphicsContext2D();
        g.setFill(Color.TRANSPARENT);
        g.clearRect(0, 0, CARD_W, CARD_H);
        g.setFill(Color.web("#252525"));
        g.fillRoundRect(0, 0, CARD_W, CARD_H, RADIUS * 2, RADIUS * 2);
        g.save();
        applyRoundRectClip(g, 0, 0, CARD_W, CARD_H, RADIUS);

        if (ecs.isImageLoaded(i) && ecs.getImage(i) != null) {
            drawImageCover(g, ecs.getImage(i), 0, 0, CARD_W, CARD_H);
            g.drawImage(ecs.getOverlayTex(i), 0, 0, CARD_W, CARD_H);
        } else {
            g.drawImage(ecs.getSkeletonTex(i), 0, 0, CARD_W, CARD_H);
        }

        g.restore();
        return off.snapshot(snapshotParams, null);
    }

    private void drawImageCover(
        GraphicsContext g,
        Image img,
        double x,
        double y,
        double w,
        double h
    ) {
        if (img == null) return;
        double iw = img.getWidth(),
            ih = img.getHeight();
        if (iw == 0 || ih == 0) return;
        double imgRatio = iw / ih,
            canvasRatio = w / h;
        double sx, sy, sw, sh;
        if (imgRatio > canvasRatio) {
            sh = ih;
            sw = ih * canvasRatio;
            sx = (iw - sw) / 2.0;
            sy = 0;
        } else {
            sw = iw;
            sh = iw / canvasRatio;
            sx = 0;
            sy = (ih - sh) / 2.0;
        }
        g.drawImage(img, sx, sy, sw, sh, x, y, w, h);
    }

    private void applyRoundRectClip(
        GraphicsContext g,
        double x,
        double y,
        double w,
        double h,
        double r
    ) {
        r = Math.min(r, Math.min(w, h) * 0.5);
        g.beginPath();
        g.moveTo(x + r, y);
        g.lineTo(x + w - r, y);
        g.quadraticCurveTo(x + w, y, x + w, y + r);
        g.lineTo(x + w, y + h - r);
        g.quadraticCurveTo(x + w, y + h, x + w - r, y + h);
        g.lineTo(x + r, y + h);
        g.quadraticCurveTo(x, y + h, x, y + h - r);
        g.lineTo(x, y + r);
        g.quadraticCurveTo(x, y, x + r, y);
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
        g.setFont(assetManager.getFont("modalTitle"));
        g.setFill(Color.WHITE);
        g.fillText(data[1], 0, 40);
        g.setFont(assetManager.getFont("sub"));
        g.setFill(Color.web("#E5A93C"));
        g.fillText(
            data[2] + "  \u2022  Calificaci\u00f3n: \u2605 " + data[3],
            0,
            70
        );
        g.setFill(Color.rgb(255, 255, 255, 0.70));
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
        g.setFont(assetManager.getFont("title"));
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
        g.setFill(Color.web("#252525"));
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
        g.setFill(assetManager.getGradient("overlay"));
        g.fillRect(0, 0, CARD_W, CARD_H);
        double tx = 12,
            ty = CARD_H - 45;
        g.setFont(assetManager.getFont("title"));
        g.setFill(Color.WHITE);
        g.fillText(title, tx, ty);
        ty += 16;
        g.setFont(assetManager.getFont("sub"));
        g.setFill(Color.rgb(255, 255, 255, 0.70));
        g.fillText(subtitle, tx, ty);
        ty += 16;
        g.setFont(assetManager.getFont("rating"));
        g.setFill(Color.web("#E5A93C"));
        g.fillText("\u2605 " + rating, tx, ty);
    }

    public static void main(String[] args) {
        System.setProperty("prism.forceGPU", "true");
        launch(args);
    }
}
