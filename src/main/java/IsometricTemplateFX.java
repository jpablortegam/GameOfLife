import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Deque;
import java.util.Random;
import javafx.animation.AnimationTimer;
import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.input.KeyCode;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.ScrollEvent;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.paint.CycleMethod;
import javafx.scene.paint.LinearGradient;
import javafx.scene.paint.Stop;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.stage.Stage;

public final class IsometricTemplateFX extends Application {

    private static final int COLS = 10;
    private static final int ROWS = 10;
    private static final int TILE_COUNT = COLS * ROWS;
    private static final int MAX_HEIGHT = 6;

    private static final double TILE_WIDTH = 64.0;
    private static final double TILE_HEIGHT = 32.0;
    private static final double TILE_Z = 22.0;

    private static final Color[][] PALETTE = {
        {
            Color.web("#5BA85A"),
            Color.web("#3D7A3C"),
            Color.web("#4A8A49"),
            Color.web("#2E5C2D"),
            Color.web("#376935"),
        },
        {
            Color.web("#C8A96B"),
            Color.web("#9A7A47"),
            Color.web("#AD8C55"),
            Color.web("#755A2E"),
            Color.web("#876B3A"),
        },
        {
            Color.web("#8C9DB5"),
            Color.web("#5E7080"),
            Color.web("#728A9A"),
            Color.web("#445565"),
            Color.web("#546070"),
        },
        {
            Color.web("#A0673A"),
            Color.web("#7A4A25"),
            Color.web("#8D5830"),
            Color.web("#5B3018"),
            Color.web("#6B3D20"),
        },
        {
            Color.web("#E8F0F8"),
            Color.web("#B0C4D8"),
            Color.web("#C5D5E5"),
            Color.web("#8A9EB8"),
            Color.web("#9AADCA"),
        },
        {
            Color.web("#FFD700"),
            Color.web("#C8A800"),
            Color.web("#DDBB00"),
            Color.web("#957900"),
            Color.web("#AA8D00"),
        },
        {
            Color.web("#7DF9FF"),
            Color.web("#50C8D0"),
            Color.web("#60D8E0"),
            Color.web("#2E9099"),
            Color.web("#3DA8B0"),
        },
    };

    private static final Color[][] DARK_PALETTE = new Color[PALETTE.length][5];
    private static final LinearGradient[][] CACHED_GRADIENTS =
        new LinearGradient[PALETTE.length][5];

    // Colores sólidos reemplazando las transparencias originales para mayor rendimiento
    private static final Color PULSE_COLOR = Color.web("#FFC864");
    private static final Color STROKE_NORMAL = Color.web("#141414");
    private static final Color HUD_BG = Color.web("#060E1C");
    private static final Color HUD_STROKE = Color.web("#3787FF");
    private static final Color BRUSH_STROKE = Color.web("#5AF0FF");

    static {
        for (int h = 0; h < PALETTE.length; h++) {
            for (int f = 0; f < 5; f++) {
                Color c = PALETTE[h][f];
                DARK_PALETTE[h][f] = Color.color(
                    c.getRed() * 0.58,
                    c.getGreen() * 0.58,
                    c.getBlue() * 0.58
                );
                CACHED_GRADIENTS[h][f] = new LinearGradient(
                    0,
                    0,
                    0,
                    1,
                    true,
                    CycleMethod.NO_CYCLE,
                    new Stop(0, PALETTE[h][f]),
                    new Stop(1, DARK_PALETTE[h][f])
                );
            }
        }
    }

    private static final Color BG_TOP = Color.web("#0E1C30");
    private static final Color BG_BOT = Color.web("#050A14");
    private static final LinearGradient BG_GRADIENT = new LinearGradient(
        0,
        0,
        0,
        1,
        true,
        CycleMethod.NO_CYCLE,
        new Stop(0, BG_TOP),
        new Stop(1, BG_BOT)
    );

    private static final Font HUD_FONT_BOLD = Font.font(
        "Consolas",
        FontWeight.BOLD,
        12
    );
    private static final Font HUD_FONT_NORMAL = Font.font(
        "Consolas",
        FontWeight.NORMAL,
        12
    );

    private final int[] heights = new int[TILE_COUNT];
    private final double[] smoothHeights = new double[TILE_COUNT];
    private final double[] popAnim = new double[TILE_COUNT];
    private final long[] popStart = new long[TILE_COUNT];

    private final int[] sortedTileIndices = new int[TILE_COUNT];
    private final double[] tileDepths = new double[TILE_COUNT];

    private final double[] screenX = new double[TILE_COUNT * 4];
    private final double[] screenY = new double[TILE_COUNT * 4];
    private final double[] sideScreenX = new double[TILE_COUNT * 4 * 4];
    private final double[] sideScreenY = new double[TILE_COUNT * 4 * 4];
    private final double[] sideHeight = new double[TILE_COUNT];

    private int hoverX = -1,
        hoverY = -1;
    private int selectX = -1,
        selectY = -1;
    private long selectStart = 0L;
    private long lastFrameTime = 0L;

    private double lastMouseX, lastMouseY;
    private int dragButton = -1;
    private int lastPaintIndex = -1;
    private int brushRadius = 0;

    private boolean keyQ, keyE, keyW, keyA, keyS, keyD, keyUp, keyDown, keyLeft, keyRight;

    private static final int HISTORY_LIMIT = 40;
    private final Deque<int[]> undoStack = new ArrayDeque<>();
    private final Deque<int[]> redoStack = new ArrayDeque<>();

    private final Camera camera = new Camera();

    private Canvas canvas;
    private GraphicsContext gc;

    private final double[] tx = new double[4];
    private final double[] ty = new double[4];
    private final double[] px = new double[8];
    private final double[] py = new double[8];

    private static final int NSTARS = 180;
    private final float[] sX = new float[NSTARS];
    private final float[] sY = new float[NSTARS];
    private final float[] sA = new float[NSTARS];
    private final float[] sR = new float[NSTARS];
    private final float[] sPhase = new float[NSTARS];
    private float starT = 0f;

    public static void main(String[] args) {
        launch(args);
    }

    @Override
    public void start(Stage stage) {
        canvas = new Canvas(1080, 760);
        gc = canvas.getGraphicsContext2D();
        canvas.setFocusTraversable(true);

        StackPane root = new StackPane(canvas);
        Scene scene = new Scene(root);

        initScene();
        initStars();
        wireInput(scene);

        AnimationTimer timer = new AnimationTimer() {
            @Override
            public void handle(long now) {
                // BUG CORREGIDO: now viene en nanosegundos, no en milisegundos.
                double dt =
                    lastFrameTime == 0
                        ? 0.016
                        : (now - lastFrameTime) / 1_000_000_000.0;
                lastFrameTime = now;
                update(dt);
                render();
            }
        };
        timer.start();

        stage.setTitle("Isometric FX — Optimized Edition (Solid & Bug-Free)");
        stage.setScene(scene);
        stage.show();

        canvas.widthProperty().bind(root.widthProperty());
        canvas.heightProperty().bind(root.heightProperty());
    }

    private void initScene() {
        Random random = new Random(1337);
        for (int x = 0; x < COLS; x++) {
            for (int y = 0; y < ROWS; y++) {
                int index = tileIndex(x, y);
                double nx = (x - (COLS - 1) / 2.0) / (COLS / 2.0);
                double ny = (y - (ROWS - 1) / 2.0) / (ROWS / 2.0);
                double distance = Math.sqrt(nx * nx + ny * ny);
                double noise = random.nextDouble() - 0.5;
                int h = (int) Math.round(
                    Math.max(0, (1.0 - distance) * 4 + noise)
                );
                heights[index] = clamp(h, 0, MAX_HEIGHT);
                smoothHeights[index] = heights[index];
                popAnim[index] = 0.0;
            }
        }
        camera.reset();
    }

    private void initStars() {
        Random r = new Random(1234L);
        for (int i = 0; i < NSTARS; i++) {
            sX[i] = r.nextFloat();
            sY[i] = r.nextFloat() * 0.55f;
            sA[i] = 0.25f + r.nextFloat() * 0.75f;
            sR[i] = 0.4f + r.nextFloat() * 1.6f;
            sPhase[i] = i * 0.6180339f;
        }
    }

    private void wireInput(Scene scene) {
        canvas.setOnMouseMoved(this::handleMouseHover);

        canvas.setOnMousePressed(e -> {
            canvas.requestFocus();
            lastMouseX = e.getX();
            lastMouseY = e.getY();
            dragButton =
                e.getButton() == MouseButton.PRIMARY
                    ? 1
                    : (e.getButton() == MouseButton.SECONDARY ? 2 : 3);
            lastPaintIndex = -1;

            if (e.getClickCount() == 2 && hoverX >= 0) {
                selectX = hoverX;
                selectY = hoverY;
                selectStart = System.currentTimeMillis();
            } else if (dragButton == 1 || dragButton == 2) {
                handleContinuousPaint(e);
            }
        });

        canvas.setOnMouseDragged(e -> {
            if (dragButton == 1 || dragButton == 2) {
                handleContinuousPaint(e);
            } else if (dragButton == 3) {
                camera.x += e.getX() - lastMouseX;
                camera.y += e.getY() - lastMouseY;
                handleMouseHover(e);
            }
            lastMouseX = e.getX();
            lastMouseY = e.getY();
        });

        canvas.setOnMouseReleased(e -> {
            dragButton = -1;
            lastPaintIndex = -1;
        });

        canvas.setOnScroll((ScrollEvent e) -> {
            if (e.getDeltaY() == 0) return;
            // Escala exponencial fluida que se adapta suavemente a ratones y trackpads
            double factor = Math.exp(e.getDeltaY() * 0.002);
            // Limitamos los picos para evitar saltos locos en el scroll
            factor = clamp(factor, 0.8, 1.25);
            zoomAt(factor, e.getX(), e.getY());
        });

        canvas.setOnKeyPressed(e -> {
            KeyCode key = e.getCode();
            boolean ctrl = e.isControlDown();

            if (ctrl && key == KeyCode.Z) {
                undo();
                return;
            }
            if (ctrl && key == KeyCode.Y) {
                redo();
                return;
            }

            switch (key) {
                case Q -> keyQ = true;
                case E -> keyE = true;
                case W -> keyW = true;
                case A -> keyA = true;
                case S -> keyS = true;
                case D -> keyD = true;
                case UP -> keyUp = true;
                case DOWN -> keyDown = true;
                case LEFT -> keyLeft = true;
                case RIGHT -> keyRight = true;
                case SPACE -> camera.autoRotate = !camera.autoRotate;
                case R, ESCAPE -> {
                    undoStack.clear();
                    redoStack.clear();
                    initScene();
                }
                case OPEN_BRACKET -> brushRadius = Math.max(0, brushRadius - 1);
                case CLOSE_BRACKET -> brushRadius = Math.min(
                    4,
                    brushRadius + 1
                );
                case G -> randomizeTerrain();
                case F -> flattenTerrain();
                case T -> smoothTerrain();
                case DELETE, BACK_SPACE -> {
                    if (hoverX >= 0) modifyTerrain(
                        hoverX,
                        hoverY,
                        -heights[tileIndex(hoverX, hoverY)]
                    );
                }
                case ADD, PLUS, EQUALS -> zoomAt(
                    1.1,
                    canvas.getWidth() / 2,
                    canvas.getHeight() / 2
                );
                case SUBTRACT, MINUS -> zoomAt(
                    1.0 / 1.1,
                    canvas.getWidth() / 2,
                    canvas.getHeight() / 2
                );
                default -> {
                }
            }
        });

        canvas.setOnKeyReleased(e -> {
            switch (e.getCode()) {
                case Q -> keyQ = false;
                case E -> keyE = false;
                case W -> keyW = false;
                case A -> keyA = false;
                case S -> keyS = false;
                case D -> keyD = false;
                case UP -> keyUp = false;
                case DOWN -> keyDown = false;
                case LEFT -> keyLeft = false;
                case RIGHT -> keyRight = false;
                default -> {
                }
            }
        });
    }

    private void handleMouseHover(MouseEvent e) {
        hoverX = hoverY = -1;
        double mx = e.getX();
        double my = e.getY();

        for (int i = TILE_COUNT - 1; i >= 0; i--) {
            int index = sortedTileIndices[i];
            int baseIdx = index * 4;

            if (pointInQuad(mx, my, screenX, baseIdx, screenY, baseIdx)) {
                hoverX = index / ROWS;
                hoverY = index % ROWS;
                return;
            }

            double sh = sideHeight[index];
            for (int f = 0; f < 4; f++) {
                int sideBase = (index * 4 + f) * 4;
                double x0 = sideScreenX[sideBase];
                double y0 = sideScreenY[sideBase];
                double x1 = sideScreenX[sideBase + 1];
                double y1 = sideScreenY[sideBase + 1];

                if (x1 >= x0) continue;

                px[0] = x0;
                py[0] = y0;
                px[1] = x1;
                py[1] = y1;
                px[2] = x1;
                py[2] = y1 + sh;
                px[3] = x0;
                py[3] = y0 + sh;

                if (pointInQuad(mx, my, px, 0, py, 0)) {
                    hoverX = index / ROWS;
                    hoverY = index % ROWS;
                    return;
                }
            }
        }
    }

    private void handleContinuousPaint(MouseEvent e) {
        handleMouseHover(e);
        if (hoverX < 0) return;

        int delta = (dragButton == 2) ? -1 : 1;
        int index = tileIndex(hoverX, hoverY);

        if (index != lastPaintIndex) {
            modifyTerrain(hoverX, hoverY, delta);
            lastPaintIndex = index;
        }
    }

    private void update(double dt) {
        dt = Math.min(dt, 0.1);

        if (keyQ) camera.targetAngle -= 0.05 * dt * 60;
        if (keyE) camera.targetAngle += 0.05 * dt * 60;

        double panSpeed = 16.0 / camera.zoom;
        double moveAmt = panSpeed * dt * 60;
        if (keyLeft || keyA) camera.x += moveAmt;
        if (keyRight || keyD) camera.x -= moveAmt;
        if (keyUp || keyW) camera.y += moveAmt;
        if (keyDown || keyS) camera.y -= moveAmt;

        if (camera.autoRotate) {
            camera.targetAngle += 0.008 * dt * 60;
        }
        camera.update();

        long now = System.currentTimeMillis();
        for (int i = 0; i < TILE_COUNT; i++) {
            double delta = heights[i] - smoothHeights[i];
            double speed = (heights[i] == 0 && smoothHeights[i] < 1.0)
                ? 0.35
                : 0.18;
            smoothHeights[i] += delta * speed * dt * 60;

            if (popAnim[i] > 0.001) {
                long elapsed = now - popStart[i];
                double t = Math.min(1.0, elapsed / 400.0);
                popAnim[i] = 1.0 - t;
            }

            int gx = i / ROWS;
            int gy = i % ROWS;
            tileDepths[i] = computeTileDepth(gx, gy);
            sortedTileIndices[i] = i;
        }

        starT += 0.038f * dt * 60;

        for (int i = 1; i < TILE_COUNT; i++) {
            int index = sortedTileIndices[i];
            double depth = tileDepths[i];
            int j = i;
            while (j > 0 && tileDepths[j - 1] > depth) {
                sortedTileIndices[j] = sortedTileIndices[j - 1];
                tileDepths[j] = tileDepths[j - 1];
                j--;
            }
            sortedTileIndices[j] = index;
            tileDepths[j] = depth;
        }

        updateScreenCoords();
    }

    private void updateScreenCoords() {
        double tw = TILE_WIDTH * camera.zoom;
        double th = TILE_HEIGHT * camera.zoom;
        double tz = TILE_Z * camera.zoom;
        double cx = COLS / 2.0;
        double cy = ROWS / 2.0;

        for (int i = 0; i < TILE_COUNT; i++) {
            int gx = i / ROWS;
            int gy = i % ROWS;
            double gh = smoothHeights[i];
            double sh = (gh + 1.0) * tz;

            int baseIdx = i * 4;
            projectVertex(
                gx,
                gy,
                gh,
                cx,
                cy,
                tw,
                th,
                tz,
                screenX,
                screenY,
                baseIdx
            );
            projectVertex(
                gx + 1,
                gy,
                gh,
                cx,
                cy,
                tw,
                th,
                tz,
                screenX,
                screenY,
                baseIdx + 1
            );
            projectVertex(
                gx + 1,
                gy + 1,
                gh,
                cx,
                cy,
                tw,
                th,
                tz,
                screenX,
                screenY,
                baseIdx + 2
            );
            projectVertex(
                gx,
                gy + 1,
                gh,
                cx,
                cy,
                tw,
                th,
                tz,
                screenX,
                screenY,
                baseIdx + 3
            );

            sideHeight[i] = sh;

            for (int f = 0; f < 4; f++) {
                double x0 = screenX[baseIdx + f];
                double y0 = screenY[baseIdx + f];
                double x1 = screenX[baseIdx + ((f + 1) % 4)];
                double y1 = screenY[baseIdx + ((f + 1) % 4)];

                int sideBase = (i * 4 + f) * 4;
                sideScreenX[sideBase] = x0;
                sideScreenY[sideBase] = y0;
                sideScreenX[sideBase + 1] = x1;
                sideScreenY[sideBase + 1] = y1;
                sideScreenX[sideBase + 2] = x1;
                sideScreenY[sideBase + 2] = y1 + sh;
                sideScreenX[sideBase + 3] = x0;
                sideScreenY[sideBase + 3] = y0 + sh;
            }
        }
    }

    private void projectVertex(
        double wx,
        double wy,
        double wz,
        double cx,
        double cy,
        double tw,
        double th,
        double tz,
        double[] outX,
        double[] outY,
        int idx
    ) {
        double dx = wx - cx;
        double dy = wy - cy;
        double rx = dx * camera.cos - dy * camera.sin + cx;
        double ry = dx * camera.sin + dy * camera.cos + cy;
        outX[idx] = camera.x + (rx - ry) * (tw * 0.5);
        outY[idx] = camera.y + (rx + ry) * (th * 0.5) - wz * tz;
    }

    private void render() {
        double w = canvas.getWidth();
        double h = canvas.getHeight();

        gc.setFill(BG_GRADIENT);
        gc.fillRect(0, 0, w, h);

        renderStars(w, h);

        for (int i = 0; i < TILE_COUNT; i++) {
            renderTile(sortedTileIndices[i]);
        }

        renderBrushPreview();
        renderHUD();
    }

    private void renderStars(double w, double h) {
        for (int i = 0; i < NSTARS; i++) {
            float tw = 0.5f + 0.5f * (float) Math.sin(starT + sPhase[i]);
            float a = Math.min(1f, sA[i] * (0.35f + 0.65f * tw));
            // Calculamos el color sólido mezclado con el fondo en lugar de usar alpha
            Color starColor = BG_TOP.interpolate(Color.WHITE, a);
            gc.setFill(starColor);
            double x = sX[i] * w;
            double y = sY[i] * h;
            double r = sR[i];
            // Usar fillRect en lugar de fillOval mejora ligeramente el rendimiento
            gc.fillRect(x - r, y - r, r * 2, r * 2);
        }
    }

    private void renderTile(int id) {
        int gx = id / ROWS;
        int gy = id % ROWS;
        int paletteIndex = paletteIndex(heights[id]);

        boolean isHovered = (gx == hoverX && gy == hoverY);
        boolean isSelected = (gx == selectX && gy == selectY);

        double pulseAlpha = 0.0;
        if (isSelected) {
            long elapsed = System.currentTimeMillis() - selectStart;
            double t = Math.min(1.0, elapsed / 420.0);
            double rawPulse =
                Math.abs(Math.sin(t * Math.PI * 3.0)) * (1.0 - t) * 0.42;
            pulseAlpha = clamp(rawPulse, 0.0, 1.0);
        }
        double popA = clamp(popAnim[id], 0.0, 1.0);
        int baseIdx = id * 4;

        // Renderizado de las caras laterales
        for (int f = 0; f < 4; f++) {
            double x0 = screenX[baseIdx + f];
            double x1 = screenX[baseIdx + ((f + 1) % 4)];
            if (x1 >= x0) continue;

            int sideBase = (id * 4 + f) * 4;
            for (int v = 0; v < 4; v++) {
                px[v] = sideScreenX[sideBase + v];
                py[v] = sideScreenY[sideBase + v];
            }

            gc.setFill(CACHED_GRADIENTS[paletteIndex][f + 1]);
            gc.fillPolygon(px, py, 4);
            gc.setStroke(STROKE_NORMAL);
            gc.setLineWidth(1.0);
            gc.strokePolygon(px, py, 4);
        }

        // Renderizado de la cara superior sin Alpha, solo mezcla de color
        for (int v = 0; v < 4; v++) {
            tx[v] = screenX[baseIdx + v];
            ty[v] = screenY[baseIdx + v];
        }

        Color topColor = PALETTE[paletteIndex][0];

        if (popA > 0) {
            topColor = topColor.interpolate(Color.WHITE, popA * 0.66);
        } else if (isSelected && pulseAlpha > 0) {
            topColor = topColor.interpolate(PULSE_COLOR, pulseAlpha);
        } else if (isHovered) {
            topColor = topColor.interpolate(Color.WHITE, 0.28);
        }

        gc.setFill(topColor);
        gc.fillPolygon(tx, ty, 4);

        if (isHovered || isSelected) {
            gc.setLineWidth(isSelected ? 2.2 : 2.0);
            gc.setStroke(Color.WHITE); // Borde sólido brillante
        } else {
            gc.setLineWidth(1.0);
            gc.setStroke(STROKE_NORMAL);
        }
        gc.strokePolygon(tx, ty, 4);
    }

    private void renderBrushPreview() {
        if (hoverX < 0) return;

        int minX = Math.max(0, hoverX - brushRadius);
        int maxX = Math.min(COLS - 1, hoverX + brushRadius);
        int minY = Math.max(0, hoverY - brushRadius);
        int maxY = Math.min(ROWS - 1, hoverY + brushRadius);

        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                if (
                    Math.hypot(x - hoverX, y - hoverY) > brushRadius + 0.001
                ) continue;
                int id = tileIndex(x, y);
                int baseIdx = id * 4;

                for (int v = 0; v < 4; v++) {
                    tx[v] = screenX[baseIdx + v];
                    ty[v] = screenY[baseIdx + v];
                }

                // Dibujar solo los marcos para no tapar los colores sin usar Alpha
                gc.setStroke(BRUSH_STROKE);
                gc.setLineWidth((x == hoverX && y == hoverY) ? 2.5 : 1.2);
                gc.strokePolygon(tx, ty, 4);
            }
        }
    }

    private void renderHUD() {
        double pw = 345,
            ph = 236;

        // HUD totalmente opaco para respetar la regla de no transparencias
        gc.setFill(HUD_BG);
        gc.fillRoundRect(12, 12, pw, ph, 14, 14);

        gc.setStroke(HUD_STROKE);
        gc.setLineWidth(1.3);
        gc.strokeRoundRect(12, 12, pw, ph, 14, 14);

        gc.setFill(Color.web("#46C896"));
        gc.fillRoundRect(12, 12, pw, 4, 4, 4);

        double angleDeg = ((Math.toDegrees(camera.angle) % 360) + 360) % 360;

        String hoverText = (hoverX >= 0)
            ? String.format(
                  "(%d,%d)  h=%d",
                  hoverX,
                  hoverY,
                  heights[tileIndex(hoverX, hoverY)]
              )
            : "—";
        String selectText = (selectX >= 0)
            ? String.format(
                  "(%d,%d)  h=%d",
                  selectX,
                  selectY,
                  heights[tileIndex(selectX, selectY)]
              )
            : "—";

        Object[][] lines = {
            { Color.web("#78FFB9"), " ISOMÉTRICA — optimizada", HUD_FONT_BOLD },
            {
                Color.web("#2352A0"),
                " ─────────────────────────────────────",
                HUD_FONT_NORMAL,
            },
            {
                Color.web("#8CD2AF"),
                " [Q / E]    rotar cámara",
                HUD_FONT_NORMAL,
            },
            {
                camera.autoRotate ? Color.web("#46FF91") : Color.web("#82C8A5"),
                " [Space]    auto-rotar: " + (camera.autoRotate ? "ON" : "OFF"),
                HUD_FONT_NORMAL,
            },
            {
                Color.web("#8CD2AF"),
                " [WASD / Mid-Drag] mover cámara",
                HUD_FONT_NORMAL,
            },
            {
                Color.web("#8CD2AF"),
                " [Clic/Drag] pintar: Izq(+) Der(-)",
                HUD_FONT_NORMAL,
            },
            {
                Color.web("#8CD2AF"),
                " [Wheel]     zoom al cursor",
                HUD_FONT_NORMAL,
            },
            {
                Color.web("#8CD2AF"),
                " [[ / ]]     brush radius",
                HUD_FONT_NORMAL,
            },
            {
                Color.web("#8CD2AF"),
                " [G] random  [F] flat  [T] smooth",
                HUD_FONT_NORMAL,
            },
            {
                Color.web("#8CD2AF"),
                " [Ctrl+Z/Y]  undo / redo",
                HUD_FONT_NORMAL,
            },
            { Color.web("#8CD2AF"), " [ESC] reset escena", HUD_FONT_NORMAL },
            {
                Color.web("#2352A0"),
                " ─────────────────────────────────────",
                HUD_FONT_NORMAL,
            },
            {
                Color.web("#FFD237"),
                String.format(
                    " zoom: %.2f×   ángulo: %05.1f°   brush: %d",
                    camera.zoom,
                    angleDeg,
                    brushRadius
                ),
                HUD_FONT_BOLD,
            },
            { Color.web("#64E4FF"), " hover: " + hoverText, HUD_FONT_BOLD },
            {
                selectX >= 0 ? Color.web("#FFB450") : Color.web("#A0A0A0"),
                " seleccionado: " + selectText,
                HUD_FONT_BOLD,
            },
        };

        double y = 32;
        for (Object[] line : lines) {
            gc.setFill((Color) line[0]);
            gc.setFont((Font) line[2]);
            gc.fillText((String) line[1], 20, y);
            y += 16;
        }
    }

    private void modifyTerrain(int cx, int cy, int delta) {
        pushUndo();
        boolean changed = false;
        long now = System.currentTimeMillis();

        for (
            int x = Math.max(0, cx - brushRadius);
            x <= Math.min(COLS - 1, cx + brushRadius);
            x++
        ) {
            for (
                int y = Math.max(0, cy - brushRadius);
                y <= Math.min(ROWS - 1, cy + brushRadius);
                y++
            ) {
                if (Math.hypot(x - cx, y - cy) > brushRadius + 0.001) continue;
                int idx = tileIndex(x, y);
                int old = heights[idx];
                heights[idx] = clamp(heights[idx] + delta, 0, MAX_HEIGHT);

                if (old != heights[idx]) {
                    changed = true;
                    popAnim[idx] = 1.0;
                    popStart[idx] = now;
                }
            }
        }
        if (changed) {
            selectX = cx;
            selectY = cy;
            selectStart = now;
        }
    }

    private void flattenTerrain() {
        pushUndo();
        Arrays.fill(heights, 0);
    }

    private void randomizeTerrain() {
        pushUndo();
        Random r = new Random();
        for (int i = 0; i < TILE_COUNT; i++) heights[i] = r.nextInt(
            MAX_HEIGHT + 1
        );
    }

    private void smoothTerrain() {
        pushUndo();
        int[] temp = Arrays.copyOf(heights, heights.length);
        for (int x = 0; x < COLS; x++) {
            for (int y = 0; y < ROWS; y++) {
                int sum = 0,
                    count = 0;
                for (int dx = -1; dx <= 1; dx++) {
                    for (int dy = -1; dy <= 1; dy++) {
                        int nx = x + dx,
                            ny = y + dy;
                        if (nx >= 0 && ny >= 0 && nx < COLS && ny < ROWS) {
                            sum += heights[tileIndex(nx, ny)];
                            count++;
                        }
                    }
                }
                temp[tileIndex(x, y)] = clamp(
                    (int) Math.round(sum / (double) count),
                    0,
                    MAX_HEIGHT
                );
            }
        }
        System.arraycopy(temp, 0, heights, 0, TILE_COUNT);
    }

    private void pushUndo() {
        undoStack.push(Arrays.copyOf(heights, heights.length));
        if (undoStack.size() > HISTORY_LIMIT) undoStack.removeLast();
        redoStack.clear();
    }

    private void undo() {
        if (undoStack.isEmpty()) return;
        redoStack.push(Arrays.copyOf(heights, heights.length));
        System.arraycopy(undoStack.pop(), 0, heights, 0, TILE_COUNT);
        for (int i = 0; i < TILE_COUNT; i++) smoothHeights[i] = heights[i];
    }

    private void redo() {
        if (redoStack.isEmpty()) return;
        undoStack.push(Arrays.copyOf(heights, heights.length));
        System.arraycopy(redoStack.pop(), 0, heights, 0, TILE_COUNT);
        for (int i = 0; i < TILE_COUNT; i++) smoothHeights[i] = heights[i];
    }

    private double computeTileDepth(int gx, int gy) {
        double dx = (gx + 0.5) - (COLS / 2.0);
        double dy = (gy + 0.5) - (ROWS / 2.0);
        return (
            dx * camera.cos -
            dy * camera.sin +
            dx * camera.sin +
            dy * camera.cos
        );
    }

    private void zoomAt(double factor, double mx, double my) {
        double oldZoom = camera.zoom;
        camera.zoom = clamp(camera.zoom * factor, 0.35, 3.0);
        double ratio = camera.zoom / oldZoom;
        camera.x = mx - (mx - camera.x) * ratio;
        camera.y = my - (my - camera.y) * ratio;
    }

    private boolean pointInQuad(
        double px,
        double py,
        double[] vx,
        int vxBase,
        double[] vy,
        int vyBase
    ) {
        boolean inside = false;
        for (int i = 0, j = 3; i < 4; j = i++) {
            if (
                ((vy[vyBase + i] > py) != (vy[vyBase + j] > py)) &&
                (px <
                    ((vx[vxBase + j] - vx[vxBase + i]) *
                        (py - vy[vyBase + i])) /
                    (vy[vyBase + j] - vy[vyBase + i]) +
                    vx[vxBase + i])
            ) {
                inside = !inside;
            }
        }
        return inside;
    }

    private int tileIndex(int x, int y) {
        return x * ROWS + y;
    }

    private int paletteIndex(int h) {
        return clamp(h, 0, PALETTE.length - 1);
    }

    private int clamp(int val, int min, int max) {
        return Math.max(min, Math.min(max, val));
    }

    private double clamp(double val, double min, double max) {
        return Math.max(min, Math.min(max, val));
    }

    private static final class Camera {

        double x = 540,
            y = 220;
        double zoom = 1.0;
        double angle = 0.0,
            targetAngle = 0.0;
        double cos = 1.0,
            sin = 0.0;
        boolean autoRotate = false;

        void update() {
            double diff = targetAngle - angle;
            diff -= Math.floor(diff / (2 * Math.PI) + 0.5) * 2 * Math.PI;
            angle += diff * 0.12;
            cos = Math.cos(angle);
            sin = Math.sin(angle);
        }

        void reset() {
            x = 540;
            y = 220;
            zoom = 1.0;
            angle = targetAngle = 0;
            cos = 1.0;
            sin = 0.0;
        }
    }
}
