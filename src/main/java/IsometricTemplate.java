import java.awt.*;
import java.awt.event.*;
import java.util.*;
import javax.swing.*;

/**
 * Editor isométrico mejorado.
 * - Puntero/brush con preview.
 * - Corrección de geometría (pilares completos hasta la base Z=-1).
 * - Sombras proyectadas de isla flotante.
 * - Zoom centrado en cursor, Undo/redo.
 */
public class IsometricTemplate extends JFrame {

    // --- Grid ---------------------------------------------------------------
    private static final int COLS = 10;
    private static final int ROWS = 10;
    private static final int N = COLS * ROWS;
    private static final int MAX_H = 6;

    // --- Isometric projection constants ------------------------------------
    private static final double TW = 64.0; // tile width
    private static final double TH = 32.0; // tile height
    private static final double TZ = 22.0; // height per level

    // --- Visual palette by height bucket -----------------------------------
    // 0=top, 1=north, 2=east, 3=south, 4=west
    private static final Color[][] PAL = {
        {
            new Color(0x5BA85A),
            new Color(0x3D7A3C),
            new Color(0x4A8A49),
            new Color(0x2E5C2D),
            new Color(0x376935),
        },
        {
            new Color(0xC8A96B),
            new Color(0x9A7A47),
            new Color(0xAD8C55),
            new Color(0x755A2E),
            new Color(0x876B3A),
        },
        {
            new Color(0x8C9DB5),
            new Color(0x5E7080),
            new Color(0x728A9A),
            new Color(0x445565),
            new Color(0x546070),
        },
        {
            new Color(0xA0673A),
            new Color(0x7A4A25),
            new Color(0x8D5830),
            new Color(0x5B3018),
            new Color(0x6B3D20),
        },
        {
            new Color(0xE8F0F8),
            new Color(0xB0C4D8),
            new Color(0xC5D5E5),
            new Color(0x8A9EB8),
            new Color(0x9AADCA),
        },
        {
            new Color(0xFFD700),
            new Color(0xC8A800),
            new Color(0xDDBB00),
            new Color(0x957900),
            new Color(0xAA8D00),
        },
        {
            new Color(0x7DF9FF),
            new Color(0x50C8D0),
            new Color(0x60D8E0),
            new Color(0x2E9099),
            new Color(0x3DA8B0),
        },
    };

    private static final Color[][] DARK = new Color[PAL.length][5];
    private static final GradientPaint[][] GRAD_CACHE =
        new GradientPaint[PAL.length][4];

    static {
        for (int h = 0; h < PAL.length; h++) {
            for (int f = 0; f < 5; f++) {
                Color c = PAL[h][f];
                DARK[h][f] = new Color(
                    (int) (c.getRed() * 0.58),
                    (int) (c.getGreen() * 0.58),
                    (int) (c.getBlue() * 0.58)
                );
            }
            for (int f = 0; f < 4; f++) {
                GRAD_CACHE[h][f] = new GradientPaint(
                    0f,
                    0f,
                    PAL[h][f + 1],
                    0f,
                    100f,
                    DARK[h][f + 1]
                );
            }
        }
    }

    // --- Background / UI ----------------------------------------------------
    private static final Color BG_TOP_C = new Color(0x0E1C30);
    private static final Color BG_BOT_C = new Color(0x050A14);
    private static final Color SHADOW_C = new Color(0, 0, 12, 62);
    private static final Color GRID_LINE = new Color(0, 0, 0, 50);
    private static final Color HOVER_TOP = new Color(255, 255, 255, 70);
    private static final Color HOVER_SIDE = new Color(255, 255, 255, 35);
    private static final Color PREVIEW_C = new Color(255, 255, 255, 90);
    private static final Color PREVIEW_BORDER = new Color(90, 240, 255, 180);

    private static final Font FONT_BOLD = new Font(
        Font.MONOSPACED,
        Font.BOLD,
        11
    );
    private static final Font FONT_NORMAL = new Font(
        Font.MONOSPACED,
        Font.PLAIN,
        11
    );

    // --- Terrain state ------------------------------------------------------
    private final int[] heights = new int[N];
    private final double[] smooth = new double[N];

    private final float[] popAnim = new float[N];
    private final long[] popStart = new long[N];

    // --- Render cache -------------------------------------------------------
    // [tile][face 0..4][vertex 0..3][x/y]
    private final double[][][][] vcache = new double[N][5][4][2];
    private final boolean[][] sideVisible = new boolean[N][4];

    // --- Sort cache ---------------------------------------------------------
    private final int[] sortIdx = new int[N];
    private final double[] sortDepth = new double[N];

    // --- Hover / selection ---------------------------------------------------
    private int hoverX = -1,
        hoverY = -1;
    private int selectX = -1,
        selectY = -1;
    private long selectStart = 0L;
    private static final long SELECT_ANIM_DURATION = 420L;

    // --- Camera -------------------------------------------------------------
    private double camX = 0;
    private double camY = 0;
    private double zoom = 1.0;
    private double angle = 0.0;
    private double targetAngle = 0.0;
    private boolean autoRotate = false;

    // --- Interaction --------------------------------------------------------
    private Point dragStart;
    private int dragButton = MouseEvent.BUTTON1;
    private boolean ctrlPaint = false;
    private boolean editingGesture = false;
    private int lastPaintIndex = -1;
    private int brushRadius = 0;

    // --- Undo / redo --------------------------------------------------------
    private static final int HISTORY_LIMIT = 40;
    private final Deque<int[]> undoStack = new ArrayDeque<>();
    private final Deque<int[]> redoStack = new ArrayDeque<>();

    // --- Stars --------------------------------------------------------------
    private static final int NSTARS = 180;
    private final float[] sX = new float[NSTARS];
    private final float[] sY = new float[NSTARS];
    private final float[] sA = new float[NSTARS];
    private final float[] sR = new float[NSTARS];
    private final float[] sPhase = new float[NSTARS];
    private float starT = 0f;

    // --- Reusable geometry buffers -----------------------------------------
    private final double[] tmpA = new double[2];
    private final double[] tmpB = new double[2];
    private final double[] tmpC = new double[2];
    private final double[] tmpD = new double[2];
    private final double[] hitX = new double[4];
    private final double[] hitY = new double[4];
    private final int[] pxi = new int[4];
    private final int[] pyi = new int[4];

    private final Stroke strokeThin = new BasicStroke(1f);
    private final Stroke strokeHover = new BasicStroke(
        2.2f,
        BasicStroke.CAP_ROUND,
        BasicStroke.JOIN_ROUND
    );
    private final Stroke strokePreview = new BasicStroke(
        1.6f,
        BasicStroke.CAP_ROUND,
        BasicStroke.JOIN_ROUND
    );

    // --- Panel --------------------------------------------------------------
    private final IsoCanvas canvas = new IsoCanvas();

    public IsometricTemplate() {
        super("Isométrico mejorado — Corrección de vacíos y pilares sólidos");
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setSize(1080, 760);
        setLocationRelativeTo(null);
        setLayout(new BorderLayout());
        add(canvas, BorderLayout.CENTER);

        initScene();
        initStars();

        setVisible(true);
        SwingUtilities.invokeLater(() -> canvas.requestFocusInWindow());

        new javax.swing.Timer(16, e -> {
            if (autoRotate) targetAngle += 0.008;

            double diff = targetAngle - angle;
            diff -= Math.floor(diff / (2 * Math.PI) + 0.5) * 2 * Math.PI;
            angle += diff * 0.12;

            for (int i = 0; i < N; i++) {
                double target = heights[i];
                double d = target - smooth[i];
                double speed = (target == 0 && smooth[i] < 1.0) ? 0.35 : 0.18;
                smooth[i] += d * speed;

                if (popAnim[i] > 0.001f) {
                    long elapsed = System.currentTimeMillis() - popStart[i];
                    float t = Math.min(1f, elapsed / 400f);
                    popAnim[i] = 1f - t;
                }
            }

            starT += 0.038f;
            canvas.repaint();
        })
            .start();
    }

    // ---------------------------------------------------------------------
    // Scene setup
    // ---------------------------------------------------------------------
    private void initScene() {
        Random r = new Random(1337L);
        for (int x = 0; x < COLS; x++) {
            for (int y = 0; y < ROWS; y++) {
                int idx = idx(x, y);
                double nx = (x - (COLS - 1) / 2.0) / (COLS / 2.0);
                double ny = (y - (ROWS - 1) / 2.0) / (ROWS / 2.0);
                double d = Math.sqrt(nx * nx + ny * ny);
                double noise = (r.nextDouble() - 0.5) * 1.0;
                int h = (int) Math.round(Math.max(0, (1.0 - d) * 4.0 + noise));
                heights[idx] = clamp(h, 0, MAX_H);
                smooth[idx] = heights[idx];
                popAnim[idx] = 0f;
            }
        }
        recentre();
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

    private void recentre() {
        camX = 540;
        camY = 220;
        zoom = 1.0;
        angle = targetAngle = 0.0;
    }

    // ---------------------------------------------------------------------
    // Projection helpers
    // ---------------------------------------------------------------------
    private void proj(double wx, double wy, double wz, double[] out) {
        double tw = TW * zoom;
        double th = TH * zoom;
        double tz = TZ * zoom;

        double cx = COLS / 2.0;
        double cy = ROWS / 2.0;
        double dx = wx - cx;
        double dy = wy - cy;

        double c = Math.cos(angle);
        double s = Math.sin(angle);

        double rx = dx * c - dy * s + cx;
        double ry = dx * s + dy * c + cy;

        out[0] = camX + (rx - ry) * (tw * 0.5);
        out[1] = camY + (rx + ry) * (th * 0.5) - wz * tz;
    }

    private double tileDepth(int gx, int gy) {
        double cx = COLS / 2.0;
        double cy = ROWS / 2.0;
        double dx = (gx + 0.5) - cx;
        double dy = (gy + 0.5) - cy;

        double c = Math.cos(angle);
        double s = Math.sin(angle);
        double rx = dx * c - dy * s;
        double ry = dx * s + dy * c;
        return rx + ry;
    }

    // ---------------------------------------------------------------------
    // Tile / history helpers
    // ---------------------------------------------------------------------
    private static int idx(int x, int y) {
        return x * ROWS + y;
    }

    private static int clamp(int v, int lo, int hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    private static double clamp(double v, double lo, double hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    private int getPaletteIndex(int h) {
        return Math.min(Math.max(h, 0), PAL.length - 1);
    }

    private void pushUndoSnapshot() {
        undoStack.push(Arrays.copyOf(heights, heights.length));
        while (undoStack.size() > HISTORY_LIMIT) {
            undoStack.removeLast();
        }
        redoStack.clear();
    }

    private void applySnapshotToSmooth() {
        for (int i = 0; i < N; i++) smooth[i] = heights[i];
    }

    private void undo() {
        if (undoStack.isEmpty()) return;
        redoStack.push(Arrays.copyOf(heights, heights.length));
        int[] snap = undoStack.pop();
        System.arraycopy(snap, 0, heights, 0, N);
        applySnapshotToSmooth();
    }

    private void redo() {
        if (redoStack.isEmpty()) return;
        undoStack.push(Arrays.copyOf(heights, heights.length));
        int[] snap = redoStack.pop();
        System.arraycopy(snap, 0, heights, 0, N);
        applySnapshotToSmooth();
    }

    private void resetTerrain() {
        pushUndoSnapshot();
        initScene();
        applySnapshotToSmooth();
    }

    private void flattenTerrain() {
        pushUndoSnapshot();
        Arrays.fill(heights, 0);
        applySnapshotToSmooth();
    }

    private void randomizeTerrain() {
        pushUndoSnapshot();
        Random r = new Random(System.nanoTime());
        for (int i = 0; i < N; i++) {
            heights[i] = r.nextInt(MAX_H + 1);
        }
        applySnapshotToSmooth();
    }

    private void smoothTerrainOnce() {
        pushUndoSnapshot();
        int[] tmp = Arrays.copyOf(heights, heights.length);
        for (int x = 0; x < COLS; x++) {
            for (int y = 0; y < ROWS; y++) {
                int sum = 0;
                int cnt = 0;
                for (int dx = -1; dx <= 1; dx++) {
                    for (int dy = -1; dy <= 1; dy++) {
                        int nx = x + dx,
                            ny = y + dy;
                        if (
                            nx < 0 || nx >= COLS || ny < 0 || ny >= ROWS
                        ) continue;
                        sum += heights[idx(nx, ny)];
                        cnt++;
                    }
                }
                tmp[idx(x, y)] = clamp(
                    (int) Math.round(sum / (double) cnt),
                    0,
                    MAX_H
                );
            }
        }
        System.arraycopy(tmp, 0, heights, 0, N);
        applySnapshotToSmooth();
    }

    private void modifyTile(int gx, int gy, int delta) {
        modifyTile(gx, gy, delta, brushRadius);
    }

    private void modifyTile(int gx, int gy, int delta, int radius) {
        if (gx < 0 || gx >= COLS || gy < 0 || gy >= ROWS) return;
        pushUndoSnapshot();

        boolean changed = false;
        for (
            int x = Math.max(0, gx - radius);
            x <= Math.min(COLS - 1, gx + radius);
            x++
        ) {
            for (
                int y = Math.max(0, gy - radius);
                y <= Math.min(ROWS - 1, gy + radius);
                y++
            ) {
                double dist = Math.hypot(x - gx, y - gy);
                if (dist > radius + 0.001) continue;
                int i = idx(x, y);
                int old = heights[i];
                heights[i] = clamp(heights[i] + delta, 0, MAX_H);
                if (old != heights[i]) {
                    changed = true;
                    popAnim[i] = 1f;
                    popStart[i] = System.currentTimeMillis();
                }
            }
        }
        if (changed) {
            selectX = gx;
            selectY = gy;
            selectStart = System.currentTimeMillis();
            editingGesture = true;
        }
        applySnapshotToSmooth();
    }

    private void selectTile(int gx, int gy) {
        if (gx < 0 || gx >= COLS || gy < 0 || gy >= ROWS) return;
        selectX = gx;
        selectY = gy;
        selectStart = System.currentTimeMillis();
    }

    private void zoomAt(double factor, int mx, int my) {
        double oldZoom = zoom;
        double newZoom = clamp(zoom * factor, 0.35, 3.0);
        if (Math.abs(newZoom - oldZoom) < 1e-9) return;

        double ratio = newZoom / oldZoom;
        camX = mx - (mx - camX) * ratio;
        camY = my - (my - camY) * ratio;
        zoom = newZoom;
    }

    // ---------------------------------------------------------------------
    // Hover / hit testing
    // ---------------------------------------------------------------------
    private void hoverFromCache(int mx, int my) {
        hoverX = hoverY = -1;

        for (int k = N - 1; k >= 0; k--) {
            int id = sortIdx[k];
            double[][][] vc = vcache[id];
            int gx = id / ROWS;
            int gy = id % ROWS;

            // top face
            for (int i = 0; i < 4; i++) {
                hitX[i] = vc[0][i][0];
                hitY[i] = vc[0][i][1];
            }
            if (pip(mx, my, hitX, hitY)) {
                hoverX = gx;
                hoverY = gy;
                return;
            }

            // visible sides
            for (int f = 0; f < 4; f++) {
                if (!sideVisible[id][f]) continue;
                for (int i = 0; i < 4; i++) {
                    hitX[i] = vc[f + 1][i][0];
                    hitY[i] = vc[f + 1][i][1];
                }
                if (pip(mx, my, hitX, hitY)) {
                    hoverX = gx;
                    hoverY = gy;
                    return;
                }
            }
        }
    }

    // ---------------------------------------------------------------------
    // Geometry / math helpers
    // ---------------------------------------------------------------------
    private static boolean pip(double px, double py, double[] vx, double[] vy) {
        boolean inside = false;
        for (int i = 0, j = vx.length - 1; i < vx.length; j = i++) {
            if (
                ((vy[i] > py) != (vy[j] > py)) &&
                (px <
                    ((vx[j] - vx[i]) * (py - vy[i])) / (vy[j] - vy[i]) + vx[i])
            ) {
                inside = !inside;
            }
        }
        return inside;
    }

    private static Color blend(Color base, Color ov) {
        float a = ov.getAlpha() / 255f;
        return new Color(
            clampChannel(base.getRed() * (1 - a) + ov.getRed() * a),
            clampChannel(base.getGreen() * (1 - a) + ov.getGreen() * a),
            clampChannel(base.getBlue() * (1 - a) + ov.getBlue() * a)
        );
    }

    private static int clampChannel(double v) {
        return Math.max(0, Math.min(255, (int) Math.round(v)));
    }

    private static Color rgba(int r, int g, int b, int a) {
        return new Color(r, g, b, a);
    }

    // ---------------------------------------------------------------------
    // Canvas
    // ---------------------------------------------------------------------
    private class IsoCanvas extends JPanel {

        IsoCanvas() {
            setBackground(BG_TOP_C);
            setFocusable(true);
            wireInput();
        }

        private void wireInput() {
            MouseAdapter ma = new MouseAdapter() {
                @Override
                public void mouseMoved(MouseEvent e) {
                    hoverFromCache(e.getX(), e.getY());
                    repaint();
                }

                @Override
                public void mousePressed(MouseEvent e) {
                    requestFocusInWindow();
                    dragStart = e.getPoint();
                    dragButton = e.getButton();
                    ctrlPaint =
                        (e.getModifiersEx() & InputEvent.CTRL_DOWN_MASK) != 0;
                    editingGesture = false;
                    lastPaintIndex = -1;

                    if (ctrlPaint) {
                        beginPaint(e);
                    } else if (
                        dragButton == MouseEvent.BUTTON1 ||
                        dragButton == MouseEvent.BUTTON3
                    ) {
                        beginPaint(e);
                    }
                }

                @Override
                public void mouseDragged(MouseEvent e) {
                    if (dragStart == null) return;

                    if (ctrlPaint) {
                        beginPaint(e);
                    } else {
                        camX += e.getX() - dragStart.x;
                        camY += e.getY() - dragStart.y;
                        dragStart = e.getPoint();
                        hoverFromCache(e.getX(), e.getY());
                    }
                }

                @Override
                public void mouseReleased(MouseEvent e) {
                    dragStart = null;
                    ctrlPaint = false;
                    lastPaintIndex = -1;
                    editingGesture = false;
                }

                @Override
                public void mouseClicked(MouseEvent e) {
                    if (e.getClickCount() == 2 && hoverX >= 0) {
                        selectTile(hoverX, hoverY);
                    }
                }

                @Override
                public void mouseWheelMoved(MouseWheelEvent e) {
                    double factor =
                        e.getPreciseWheelRotation() < 0 ? 1.1 : 1.0 / 1.1;
                    zoomAt(factor, e.getX(), e.getY());
                }

                @Override
                public void mouseExited(MouseEvent e) {
                    hoverX = hoverY = -1;
                }

                private void beginPaint(MouseEvent e) {
                    hoverFromCache(e.getX(), e.getY());
                    if (hoverX < 0) return;

                    int delta = (dragButton == MouseEvent.BUTTON3) ? -1 : 1;
                    int i = idx(hoverX, hoverY);
                    if (i != lastPaintIndex) {
                        modifyTile(hoverX, hoverY, delta);
                        lastPaintIndex = i;
                    }
                }
            };

            addMouseListener(ma);
            addMouseMotionListener(ma);
            addMouseWheelListener(ma);

            addKeyListener(
                new KeyAdapter() {
                    @Override
                    public void keyPressed(KeyEvent e) {
                        int k = e.getKeyCode();
                        boolean ctrl =
                            (e.getModifiersEx() & InputEvent.CTRL_DOWN_MASK) !=
                            0;

                        if (ctrl && k == KeyEvent.VK_Z) {
                            undo();
                            return;
                        }
                        if (ctrl && k == KeyEvent.VK_Y) {
                            redo();
                            return;
                        }

                        switch (k) {
                            case KeyEvent.VK_ESCAPE -> {
                                undoStack.clear();
                                redoStack.clear();
                                initScene();
                                canvas.repaint();
                            }
                            case KeyEvent.VK_R -> recentre();
                            case KeyEvent.VK_A -> autoRotate = !autoRotate;
                            case KeyEvent.VK_Q -> targetAngle -= Math.PI / 4;
                            case KeyEvent.VK_E -> targetAngle += Math.PI / 4;
                            case KeyEvent.VK_LEFT -> camX += 24;
                            case KeyEvent.VK_RIGHT -> camX -= 24;
                            case KeyEvent.VK_UP -> camY += 24;
                            case KeyEvent.VK_DOWN -> camY -= 24;
                            case KeyEvent.VK_OPEN_BRACKET -> brushRadius =
                                Math.max(0, brushRadius - 1);
                            case KeyEvent.VK_CLOSE_BRACKET -> brushRadius =
                                Math.min(4, brushRadius + 1);
                            case KeyEvent.VK_G -> randomizeTerrain();
                            case KeyEvent.VK_F -> flattenTerrain();
                            case KeyEvent.VK_T -> smoothTerrainOnce();
                            case KeyEvent.VK_DELETE -> {
                                if (hoverX >= 0) modifyTile(
                                    hoverX,
                                    hoverY,
                                    -heights[idx(hoverX, hoverY)]
                                );
                            }
                            case KeyEvent.VK_ADD, KeyEvent.VK_EQUALS -> zoomAt(
                                1.1,
                                getWidth() / 2,
                                getHeight() / 2
                            );
                            case
                                KeyEvent.VK_SUBTRACT,
                                KeyEvent.VK_MINUS -> zoomAt(
                                1.0 / 1.1,
                                getWidth() / 2,
                                getHeight() / 2
                            );
                        }
                        repaint();
                    }
                }
            );
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g.create();
            try {
                g2.setRenderingHint(
                    RenderingHints.KEY_ANTIALIASING,
                    RenderingHints.VALUE_ANTIALIAS_ON
                );
                g2.setRenderingHint(
                    RenderingHints.KEY_STROKE_CONTROL,
                    RenderingHints.VALUE_STROKE_PURE
                );
                g2.setRenderingHint(
                    RenderingHints.KEY_RENDERING,
                    RenderingHints.VALUE_RENDER_QUALITY
                );
                g2.setRenderingHint(
                    RenderingHints.KEY_TEXT_ANTIALIASING,
                    RenderingHints.VALUE_TEXT_ANTIALIAS_ON
                );

                int W = getWidth();
                int H = getHeight();

                // Background
                g2.setPaint(new GradientPaint(0, 0, BG_TOP_C, 0, H, BG_BOT_C));
                g2.fillRect(0, 0, W, H);
                drawStars(g2, W, H);

                // Order tiles
                for (int i = 0; i < N; i++) {
                    sortIdx[i] = i;
                    sortDepth[i] = tileDepth(i / ROWS, i % ROWS);
                }
                for (int i = 1; i < N; i++) {
                    int si = sortIdx[i];
                    double sd = sortDepth[i];
                    int j = i;
                    while (j > 0 && sortDepth[j - 1] > sd) {
                        sortIdx[j] = sortIdx[j - 1];
                        sortDepth[j] = sortDepth[j - 1];
                        j--;
                    }
                    sortIdx[j] = si;
                    sortDepth[j] = sd;
                }

                // Sombras de contacto (ahora proyectadas a la altura base -1)
                drawShadows(g2);

                // Tiles
                for (int k = 0; k < N; k++) {
                    drawTile(g2, sortIdx[k]);
                }

                // Brush preview on top
                drawBrushPreview(g2);

                // HUD
                drawHUD(g2);
            } finally {
                g2.dispose();
            }
        }

        private void drawStars(Graphics2D g2, int W, int H) {
            for (int i = 0; i < NSTARS; i++) {
                float tw = 0.5f + 0.5f * (float) Math.sin(starT + sPhase[i]);
                float a = Math.min(1f, sA[i] * (0.35f + 0.65f * tw));
                g2.setColor(new Color(1f, 1f, 1f, a));
                float x = sX[i] * W;
                float y = sY[i] * H;
                float r = sR[i];
                g2.fillOval(
                    (int) (x - r),
                    (int) (y - r),
                    (int) (2 * r + 1),
                    (int) (2 * r + 1)
                );
            }
        }

        private void drawShadows(Graphics2D g2) {
            g2.setColor(SHADOW_C);
            for (int x = 0; x < COLS; x++) {
                for (int y = 0; y < ROWS; y++) {
                    int i = idx(x, y);
                    double h = smooth[i] + 1.0; // Todo proyecta sombra, incluyendo la capa base

                    // Sombras proyectadas sobre el fondo base en Z = -1.0
                    proj(x, y, -1.0, tmpA);
                    proj(x + 1, y, -1.0, tmpB);
                    proj(x + 1, y + 1, -1.0, tmpC);
                    proj(x, y + 1, -1.0, tmpD);

                    // Offsets funcionales (antes se dividían por TW arruinando el efecto)
                    double ox = (h * 4.0 * zoom);
                    double oy = (h * 6.0 * zoom);
                    fillQ(
                        g2,
                        tmpA[0] + ox,
                        tmpA[1] + oy,
                        tmpB[0] + ox,
                        tmpB[1] + oy,
                        tmpC[0] + ox,
                        tmpC[1] + oy,
                        tmpD[0] + ox,
                        tmpD[1] + oy
                    );
                }
            }
        }

        private void drawTile(Graphics2D g2, int id) {
            int gx = id / ROWS;
            int gy = id % ROWS;
            double gh = smooth[id];
            int ih = getPaletteIndex(heights[id]);
            double tz = TZ * zoom;

            boolean hover = (gx == hoverX && gy == hoverY);
            boolean selected = (gx == selectX && gy == selectY);

            float pulseAlpha = 0f;
            if (selected) {
                long elapsed = System.currentTimeMillis() - selectStart;
                float t = Math.min(1f, elapsed / (float) SELECT_ANIM_DURATION);
                pulseAlpha = (float) (Math.sin(t * Math.PI * 3) *
                    (1 - t) *
                    0.42);
            }

            double[][][] vc = vcache[id];

            proj(gx, gy, gh, tmpA);
            proj(gx + 1, gy, gh, tmpB);
            proj(gx + 1, gy + 1, gh, tmpC);
            proj(gx, gy + 1, gh, tmpD);

            storeFace(vc[0], tmpA, tmpB, tmpC, tmpD);

            // Caras laterales visibles calculadas dinámicamente
            double[][] vs = { tmpA, tmpB, tmpC, tmpD };
            for (int f = 0; f < 4; f++) sideVisible[id][f] = false;

            // BUG SOLUCIONADO: Las paredes laterales ahora bajan hasta la capa -1,
            // llenando todos los "huecos" y creando un modelo sólido (capa 0 ahora tiene grosor).
            double sideH = (gh + 1.0) * tz;

            for (int f = 0; f < 4; f++) {
                double[] v0 = vs[f];
                double[] v1 = vs[(f + 1) % 4];
                if (v1[0] >= v0[0]) continue; // hidden side
                sideVisible[id][f] = true;

                double topY = Math.min(v0[1], v1[1]);
                double botY = Math.max(v0[1], v1[1]) + sideH;

                Color baseSide = PAL[ih][f + 1];
                Color sideTop = baseSide;
                if (hover) sideTop = blend(sideTop, HOVER_SIDE);
                if (selected && pulseAlpha > 0f) {
                    sideTop = blend(
                        sideTop,
                        rgba(255, 200, 100, (int) (pulseAlpha * 255))
                    );
                }
                if (popAnim[id] > 0.01f) {
                    sideTop = blend(
                        sideTop,
                        rgba(255, 255, 200, (int) (popAnim[id] * 170))
                    );
                }

                g2.setPaint(
                    new GradientPaint(
                        0f,
                        (float) topY,
                        sideTop,
                        0f,
                        (float) botY,
                        DARK[ih][f + 1]
                    )
                );
                fillQ(
                    g2,
                    v0[0],
                    v0[1],
                    v1[0],
                    v1[1],
                    v1[0],
                    v1[1] + sideH,
                    v0[0],
                    v0[1] + sideH
                );

                storeFace(
                    vc[f + 1],
                    v0[0],
                    v0[1],
                    v1[0],
                    v1[1],
                    v1[0],
                    v1[1] + sideH,
                    v0[0],
                    v0[1] + sideH
                );

                g2.setColor(GRID_LINE);
                strokeQ(
                    g2,
                    v0[0],
                    v0[1],
                    v1[0],
                    v1[1],
                    v1[0],
                    v1[1] + sideH,
                    v0[0],
                    v0[1] + sideH
                );
            }

            // Top Face
            Color topC = PAL[ih][0];
            if (hover) topC = blend(topC, HOVER_TOP);
            if (selected && pulseAlpha > 0f) {
                topC = blend(
                    topC,
                    rgba(255, 200, 100, (int) (pulseAlpha * 255))
                );
            }
            if (popAnim[id] > 0.01f) {
                topC = blend(
                    topC,
                    rgba(255, 255, 200, (int) (popAnim[id] * 170))
                );
            }
            g2.setColor(topC);
            fillQ(
                g2,
                tmpA[0],
                tmpA[1],
                tmpB[0],
                tmpB[1],
                tmpC[0],
                tmpC[1],
                tmpD[0],
                tmpD[1]
            );

            if (hover || selected) {
                g2.setStroke(strokeHover);
                int alpha = hover
                    ? 130
                    : Math.max(0, Math.min(255, (int) (pulseAlpha * 190)));
                g2.setColor(new Color(255, 255, 255, alpha));
                strokeQ(
                    g2,
                    tmpA[0],
                    tmpA[1],
                    tmpB[0],
                    tmpB[1],
                    tmpC[0],
                    tmpC[1],
                    tmpD[0],
                    tmpD[1]
                );
                g2.setStroke(strokeThin);
            }

            g2.setColor(GRID_LINE);
            strokeQ(
                g2,
                tmpA[0],
                tmpA[1],
                tmpB[0],
                tmpB[1],
                tmpC[0],
                tmpC[1],
                tmpD[0],
                tmpD[1]
            );

            if (zoom >= 1.5 && (hover || selected)) {
                String lbl = gx + "," + gy + " h" + heights[id];
                g2.setFont(
                    new Font(Font.MONOSPACED, Font.BOLD, (int) (9 * zoom))
                );
                FontMetrics fm = g2.getFontMetrics();
                double cx = (tmpA[0] + tmpB[0] + tmpC[0] + tmpD[0]) * 0.25;
                double cy = (tmpA[1] + tmpB[1] + tmpC[1] + tmpD[1]) * 0.25;
                g2.setColor(new Color(255, 255, 255, selected ? 255 : 220));
                g2.drawString(
                    lbl,
                    (int) (cx - fm.stringWidth(lbl) * 0.5),
                    (int) (cy + fm.getAscent() * 0.5)
                );
            }
        }

        private void drawBrushPreview(Graphics2D g2) {
            if (hoverX < 0 || hoverY < 0) return;

            int minX = Math.max(0, hoverX - brushRadius);
            int maxX = Math.min(COLS - 1, hoverX + brushRadius);
            int minY = Math.max(0, hoverY - brushRadius);
            int maxY = Math.min(ROWS - 1, hoverY + brushRadius);

            g2.setStroke(strokePreview);
            for (int x = minX; x <= maxX; x++) {
                for (int y = minY; y <= maxY; y++) {
                    if (
                        Math.hypot(x - hoverX, y - hoverY) > brushRadius + 0.001
                    ) continue;
                    int id = idx(x, y);
                    double[][][] vc = vcache[id];
                    if (
                        vc[0][0][0] == 0 &&
                        vc[0][0][1] == 0 &&
                        vc[0][1][0] == 0 &&
                        vc[0][1][1] == 0 &&
                        vc[0][2][0] == 0 &&
                        vc[0][2][1] == 0 &&
                        vc[0][3][0] == 0 &&
                        vc[0][3][1] == 0
                    ) {
                        continue;
                    }
                    g2.setColor(new Color(255, 255, 255, 40));
                    strokeQ(
                        g2,
                        vc[0][0][0],
                        vc[0][0][1],
                        vc[0][1][0],
                        vc[0][1][1],
                        vc[0][2][0],
                        vc[0][2][1],
                        vc[0][3][0],
                        vc[0][3][1]
                    );
                    if (x == hoverX && y == hoverY) {
                        g2.setColor(PREVIEW_BORDER);
                        strokeQ(
                            g2,
                            vc[0][0][0],
                            vc[0][0][1],
                            vc[0][1][0],
                            vc[0][1][1],
                            vc[0][2][0],
                            vc[0][2][1],
                            vc[0][3][0],
                            vc[0][3][1]
                        );
                    }
                }
            }
        }

        private void drawHUD(Graphics2D g2) {
            int pw = 345;
            int ph = 236;

            g2.setColor(new Color(6, 14, 28, 220));
            g2.fillRoundRect(12, 12, pw, ph, 14, 14);

            g2.setStroke(new BasicStroke(1.3f));
            g2.setColor(new Color(55, 135, 255, 70));
            g2.drawRoundRect(12, 12, pw, ph, 14, 14);

            g2.setColor(new Color(70, 200, 150, 110));
            g2.fillRoundRect(12, 12, pw, 4, 4, 4);
            g2.setStroke(strokeThin);

            double deg = ((Math.toDegrees(angle) % 360) + 360) % 360;

            String hoverText =
                hoverX >= 0
                    ? String.format(
                          "(%d,%d)  h=%d",
                          hoverX,
                          hoverY,
                          heights[idx(hoverX, hoverY)]
                      )
                    : "—";
            String selectedText =
                selectX >= 0
                    ? String.format(
                          "(%d,%d)  h=%d",
                          selectX,
                          selectY,
                          heights[idx(selectX, selectY)]
                      )
                    : "—";
            String modeText = ctrlPaint ? "PINTURA" : "CÁMARA";

            Object[][] lines = {
                {
                    new Color(120, 255, 185),
                    " ISOMÉTRICA — editor mejorado",
                    FONT_BOLD,
                },
                {
                    new Color(35, 82, 160),
                    " ─────────────────────────────────────",
                    FONT_NORMAL,
                },
                {
                    new Color(140, 210, 175),
                    " [Q / E]     rotar cámara",
                    FONT_NORMAL,
                },
                {
                    autoRotate
                        ? new Color(70, 255, 145)
                        : new Color(130, 200, 165),
                    " [A]         auto-rotar: " + (autoRotate ? "ON" : "OFF"),
                    FONT_NORMAL,
                },
                {
                    new Color(140, 210, 175),
                    " [Arrastrar]  mover cámara",
                    FONT_NORMAL,
                },
                {
                    new Color(140, 210, 175),
                    " [Ctrl+Drag]  pintar continuo",
                    FONT_NORMAL,
                },
                {
                    new Color(140, 210, 175),
                    " [Clic Izq]   subir   [Der] bajar",
                    FONT_NORMAL,
                },
                {
                    new Color(140, 210, 175),
                    " [Wheel]      zoom al cursor",
                    FONT_NORMAL,
                },
                {
                    new Color(140, 210, 175),
                    " [[ / ]]     brush radius",
                    FONT_NORMAL,
                },
                {
                    new Color(140, 210, 175),
                    " [G] random  [F] flat  [T] smooth",
                    FONT_NORMAL,
                },
                {
                    new Color(140, 210, 175),
                    " [Ctrl+Z/Y] undo / redo",
                    FONT_NORMAL,
                },
                {
                    new Color(140, 210, 175),
                    " [ESC] reset escena",
                    FONT_NORMAL,
                },
                {
                    new Color(35, 82, 160),
                    " ─────────────────────────────────────",
                    FONT_NORMAL,
                },
                {
                    new Color(255, 210, 55),
                    String.format(
                        " zoom: %.2f×   ángulo: %05.1f°   brush: %d",
                        zoom,
                        deg,
                        brushRadius
                    ),
                    FONT_BOLD,
                },
                { new Color(100, 228, 255), " hover: " + hoverText, FONT_BOLD },
                {
                    selectX >= 0
                        ? new Color(255, 180, 80)
                        : new Color(160, 160, 160),
                    " seleccionado: " + selectedText,
                    FONT_BOLD,
                },
                { new Color(180, 180, 200), " modo: " + modeText, FONT_NORMAL },
            };

            int y = 32;
            for (Object[] line : lines) {
                g2.setFont((Font) line[2]);
                g2.setColor((Color) line[0]);
                g2.drawString((String) line[1], 20, y);
                y += 16;
            }
        }

        // Geometry I/O
        private void fillQ(
            Graphics2D g2,
            double x0,
            double y0,
            double x1,
            double y1,
            double x2,
            double y2,
            double x3,
            double y3
        ) {
            pxi[0] = (int) Math.round(x0);
            pxi[1] = (int) Math.round(x1);
            pxi[2] = (int) Math.round(x2);
            pxi[3] = (int) Math.round(x3);
            pyi[0] = (int) Math.round(y0);
            pyi[1] = (int) Math.round(y1);
            pyi[2] = (int) Math.round(y2);
            pyi[3] = (int) Math.round(y3);
            g2.fillPolygon(pxi, pyi, 4);
        }

        private void strokeQ(
            Graphics2D g2,
            double x0,
            double y0,
            double x1,
            double y1,
            double x2,
            double y2,
            double x3,
            double y3
        ) {
            pxi[0] = (int) Math.round(x0);
            pxi[1] = (int) Math.round(x1);
            pxi[2] = (int) Math.round(x2);
            pxi[3] = (int) Math.round(x3);
            pyi[0] = (int) Math.round(y0);
            pyi[1] = (int) Math.round(y1);
            pyi[2] = (int) Math.round(y2);
            pyi[3] = (int) Math.round(y3);
            g2.drawPolygon(pxi, pyi, 4);
        }

        private void storeFace(
            double[][] face,
            double x0,
            double y0,
            double x1,
            double y1,
            double x2,
            double y2,
            double x3,
            double y3
        ) {
            face[0][0] = x0;
            face[0][1] = y0;
            face[1][0] = x1;
            face[1][1] = y1;
            face[2][0] = x2;
            face[2][1] = y2;
            face[3][0] = x3;
            face[3][1] = y3;
        }

        private void storeFace(
            double[][] face,
            double[] a,
            double[] b,
            double[] c,
            double[] d
        ) {
            storeFace(face, a[0], a[1], b[0], b[1], c[0], c[1], d[0], d[1]);
        }
    }

    // ---------------------------------------------------------------------
    // Entry point
    // ---------------------------------------------------------------------
    public static void main(String[] args) {
        SwingUtilities.invokeLater(IsometricTemplate::new);
    }
}
