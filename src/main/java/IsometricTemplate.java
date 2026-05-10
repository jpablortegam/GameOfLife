import java.awt.*;
import java.awt.event.*;
import java.util.Arrays;
import javax.swing.*;

/**
 * ╔══════════════════════════════════════════════════════════╗
 * PLANTILLA ISOMÉTRICA ROTATORIA — Java puro
 * ╠══════════════════════════════════════════════════════════╣
 * MATEMÁTICAS:
 * Proyección isométrica con rotación en el eje Z.
 * Primero se rota la coordenada del grid (rx, ry)
 * y luego se proyecta a isométrico 2:1.
 *
 * VISIBILIDAD DE CARAS (Backface Culling):
 * Una cara lateral es visible si al proyectar sus
 * vértices superiores (v0 -> v1), el eje X decrece en
 * pantalla (v1.x < v0.x).
 *
 * CONTROLES:
 * [Q / E]       → Rota la cámara
 * [A]           → Auto-rotación
 * Clic Izq/Der  → Sube / Baja el tile
 * Arrastrar     → Desplaza la cámara
 * +/- o Scroll  → Zoom
 * R             → Resetea la escena
 * ╚══════════════════════════════════════════════════════════╝
 */
public class IsometricTemplate extends JFrame {

    // ── Dimensiones base del tile (a escala 1.0) ──────────────────────────────
    private static final double BASE_TILE_W = 64.0; // ancho total del rombo
    private static final double BASE_TILE_H = 32.0; // alto del rombo (mitad)
    private static final double BASE_TILE_Z = 20.0; // altura de cada nivel-Z

    // ── Tamaño de la cuadrícula ───────────────────────────────────────────────
    private static final int GRID_COLS = 10;
    private static final int GRID_ROWS = 10;
    private static final int MAX_HEIGHT = 6;

    // ── Estado de la escena ───────────────────────────────────────────────────
    private final int[][] heights = new int[GRID_COLS][GRID_ROWS];
    private int hoverX = -1,
        hoverY = -1;

    // ── Cámara y Rotación ─────────────────────────────────────────────────────
    private double camX, camY;
    private double zoom = 1.0;
    private Point dragStart;
    private double angle = 0.0;
    private double targetAngle = 0.0;
    private boolean autoRotate = false;

    // ── Banderas para evitar repetición de teclas ─────────────────────────────
    private boolean qPressed = false;
    private boolean ePressed = false;

    // ── Paleta de colores por altura ─────────────────────────────────────────
    // Se expandió a 5 colores para soportar las 4 caras al rotar: {Top, Norte, Este, Sur, Oeste}
    private static final Color[][] PALETTE = {
        // H = 0  — césped
        {
            new Color(0x5BA85A),
            new Color(0x3D7A3C),
            new Color(0x4A8A49),
            new Color(0x2E5C2D),
            new Color(0x376935),
        },
        // H = 1  — tierra
        {
            new Color(0xC8A96B),
            new Color(0x9A7A47),
            new Color(0xAD8C55),
            new Color(0x755A2E),
            new Color(0x876B3A),
        },
        // H = 2  — piedra
        {
            new Color(0x8C9DB5),
            new Color(0x5E7080),
            new Color(0x728A9A),
            new Color(0x445565),
            new Color(0x546070),
        },
        // H = 3  — madera
        {
            new Color(0xA0673A),
            new Color(0x7A4A25),
            new Color(0x8D5830),
            new Color(0x5B3018),
            new Color(0x6B3D20),
        },
        // H = 4  — nieve
        {
            new Color(0xE8F0F8),
            new Color(0xB0C4D8),
            new Color(0xC5D5E5),
            new Color(0x8A9EB8),
            new Color(0x9AADCA),
        },
        // H = 5  — oro
        {
            new Color(0xFFD700),
            new Color(0xC8A800),
            new Color(0xDDBB00),
            new Color(0x957900),
            new Color(0xAA8D00),
        },
        // H = 6  — cristal
        {
            new Color(0x7DF9FF),
            new Color(0x50C8D0),
            new Color(0x60D8E0),
            new Color(0x2E9099),
            new Color(0x3DA8B0),
        },
    };

    // ── Colores UI ────────────────────────────────────────────────────────────
    private static final Color HOVER_TOP = new Color(255, 255, 255, 60);
    private static final Color HOVER_SIDE = new Color(255, 255, 255, 30);
    private static final Color GRID_LINE = new Color(0, 0, 0, 45);
    private static final Color BG_TOP = new Color(0x1A2535);
    private static final Color BG_BOTTOM = new Color(0x0D151F);

    // ─────────────────────────────────────────────────────────────────────────

    public IsometricTemplate() {
        super("Plantilla Isométrica Rotatoria — Java puro");
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setSize(900, 650);
        setLocationRelativeTo(null);

        initScene();

        IsometricCanvas canvas = new IsometricCanvas();
        add(canvas);
        setVisible(true);

        recentreCamera(canvas.getWidth(), canvas.getHeight());

        // Bucle de renderizado para animación fluida (~60 FPS)
        new Timer(16, e -> {
            if (autoRotate) targetAngle += 0.008;
            double diff = targetAngle - angle;
            diff = diff - Math.floor(diff / (2 * Math.PI) + 0.5) * 2 * Math.PI; // Normaliza a (-π, π]
            angle += diff * 0.12; // Lerp
            canvas.repaint();
        })
            .start();
    }

    private void initScene() {
        for (int x = 0; x < GRID_COLS; x++) {
            for (int y = 0; y < GRID_ROWS; y++) {
                double nx = (x - GRID_COLS / 2.0) / (GRID_COLS / 2.0);
                double ny = (y - GRID_ROWS / 2.0) / (GRID_ROWS / 2.0);
                double dist = Math.sqrt(nx * nx + ny * ny);
                int h = (int) Math.round(Math.max(0, (1 - dist) * 3.5));
                heights[x][y] = Math.min(h, MAX_HEIGHT);
            }
        }
    }

    private void recentreCamera(int w, int h) {
        camX = w / 2.0;
        camY = h / 4.0 + 40;
        angle = 0;
        targetAngle = 0;
        zoom = 1.0;
    }

    // =========================================================================
    //  MATEMÁTICAS 3D -> 2D
    // =========================================================================

    /** Transforma un punto 3D del grid a coordenadas 2D de pantalla. */
    private double[] project(double wx, double wy, double wz) {
        double tw = BASE_TILE_W * zoom;
        double th = BASE_TILE_H * zoom;
        double tz = BASE_TILE_Z * zoom;

        double cx = GRID_COLS / 2.0;
        double cy = GRID_ROWS / 2.0;

        double cos = Math.cos(angle);
        double sin = Math.sin(angle);

        // Trasladar al origen, rotar y volver
        double dx = wx - cx;
        double dy = wy - cy;
        double rx = dx * cos - dy * sin + cx;
        double ry = dx * sin + dy * cos + cy;

        return new double[] {
            camX + (rx - ry) * (tw / 2.0),
            camY + (rx + ry) * (th / 2.0) - wz * tz,
        };
    }

    /** Calcula la profundidad para ordenar los tiles (Painter's Algorithm). */
    private double tileDepth(int gx, int gy) {
        double cx = GRID_COLS / 2.0,
            cy = GRID_ROWS / 2.0;
        double cos = Math.cos(angle),
            sin = Math.sin(angle);
        double dx = (gx + 0.5) - cx,
            dy = (gy + 0.5) - cy;
        return (dx * cos - dy * sin) + (dx * sin + dy * cos);
    }

    // =========================================================================
    //  CANVAS
    // =========================================================================
    private class IsometricCanvas extends JPanel {

        private final Integer[] order = new Integer[GRID_COLS * GRID_ROWS];
        private final double[] depth = new double[GRID_COLS * GRID_ROWS];

        IsometricCanvas() {
            setBackground(BG_TOP);
            for (int i = 0; i < order.length; i++) order[i] = i;

            MouseAdapter mouse = new MouseAdapter() {
                @Override
                public void mouseMoved(MouseEvent e) {
                    updateHover(e.getX(), e.getY());
                }

                @Override
                public void mousePressed(MouseEvent e) {
                    dragStart = e.getPoint();
                    if (e.getButton() == MouseEvent.BUTTON1) modifyTile(+1);
                    else if (e.getButton() == MouseEvent.BUTTON3) modifyTile(
                        -1
                    );
                }

                @Override
                public void mouseDragged(MouseEvent e) {
                    if (dragStart != null) {
                        camX += e.getX() - dragStart.x;
                        camY += e.getY() - dragStart.y;
                        dragStart = e.getPoint();
                        updateHover(e.getX(), e.getY());
                    }
                }

                @Override
                public void mouseWheelMoved(MouseWheelEvent e) {
                    double f = e.getWheelRotation() < 0 ? 1.1 : 1.0 / 1.1;
                    zoom = Math.max(0.3, Math.min(3.0, zoom * f));
                    updateHover(e.getX(), e.getY());
                }

                @Override
                public void mouseExited(MouseEvent e) {
                    hoverX = hoverY = -1;
                }
            };
            addMouseMotionListener(mouse);
            addMouseListener(mouse);
            addMouseWheelListener(mouse);

            addKeyListener(
                new KeyAdapter() {
                    @Override
                    public void keyPressed(KeyEvent e) {
                        int k = e.getKeyCode();
                        if (k == KeyEvent.VK_R) recentreCamera(
                            getWidth(),
                            getHeight()
                        );

                        // Solo rotamos si la tecla no estaba ya presionada
                        if (k == KeyEvent.VK_Q && !qPressed) {
                            targetAngle -= Math.PI / 4;
                            qPressed = true;
                        }
                        if (k == KeyEvent.VK_E && !ePressed) {
                            targetAngle += Math.PI / 4;
                            ePressed = true;
                        }

                        if (k == KeyEvent.VK_A) autoRotate = !autoRotate;
                        if (
                            k == KeyEvent.VK_PLUS || k == KeyEvent.VK_EQUALS
                        ) zoom = Math.min(zoom * 1.1, 3.0);
                        if (k == KeyEvent.VK_MINUS) zoom = Math.max(
                            zoom / 1.1,
                            0.3
                        );
                    }

                    @Override
                    public void keyReleased(KeyEvent e) {
                        int k = e.getKeyCode();
                        if (k == KeyEvent.VK_Q) qPressed = false;
                        if (k == KeyEvent.VK_E) ePressed = false;
                    }
                }
            );
            setFocusable(true);
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g;

            g2.setRenderingHint(
                RenderingHints.KEY_ANTIALIASING,
                RenderingHints.VALUE_ANTIALIAS_ON
            );
            g2.setRenderingHint(
                RenderingHints.KEY_STROKE_CONTROL,
                RenderingHints.VALUE_STROKE_PURE
            );

            drawBackground(g2);

            // Ordenamiento por profundidad
            for (int i = 0; i < order.length; i++) {
                depth[i] = tileDepth(i / GRID_ROWS, i % GRID_ROWS);
            }
            Arrays.sort(order, (a, b) -> Double.compare(depth[a], depth[b]));

            // Renderizado de atrás hacia adelante
            for (int idx : order) {
                drawTile(g2, idx / GRID_ROWS, idx % GRID_ROWS);
            }

            drawHUD(g2);
        }

        private void drawBackground(Graphics2D g2) {
            GradientPaint gp = new GradientPaint(
                0,
                0,
                BG_TOP,
                0,
                getHeight(),
                BG_BOTTOM
            );
            g2.setPaint(gp);
            g2.fillRect(0, 0, getWidth(), getHeight());
        }

        private void drawTile(Graphics2D g2, int gx, int gy) {
            int gz = heights[gx][gy];
            boolean hover = (gx == hoverX && gy == hoverY);
            Color[] pal = PALETTE[Math.min(gz, PALETTE.length - 1)];
            double tz = BASE_TILE_Z * zoom;

            // 4 vértices de la cara superior (N, E, S, W)
            double[] pA = project(gx, gy, gz);
            double[] pB = project(gx + 1, gy, gz);
            double[] pC = project(gx + 1, gy + 1, gz);
            double[] pD = project(gx, gy + 1, gz);
            double[][] verts = { pA, pB, pC, pD };

            // ── Caras laterales ───────────────────────────────────────────────
            if (gz > 0) {
                for (int f = 0; f < 4; f++) {
                    double[] v0 = verts[f];
                    double[] v1 = verts[(f + 1) % 4];

                    // Visible si el vector va de derecha a izquierda en pantalla
                    if (v1[0] < v0[0]) {
                        Color fc = hover
                            ? blend(pal[f + 1], HOVER_SIDE, 1)
                            : pal[f + 1];
                        g2.setColor(fc);

                        Polygon side = poly(
                            v0[0],
                            v0[1],
                            v1[0],
                            v1[1],
                            v1[0],
                            v1[1] + tz,
                            v0[0],
                            v0[1] + tz
                        );
                        g2.fill(side);
                        g2.setColor(GRID_LINE);
                        g2.draw(side);
                    }
                }
            }

            // ── Cara superior ─────────────────────────────────────────────────
            Polygon top = poly(
                pA[0],
                pA[1],
                pB[0],
                pB[1],
                pC[0],
                pC[1],
                pD[0],
                pD[1]
            );
            g2.setColor(hover ? blend(pal[0], HOVER_TOP, 1) : pal[0]);
            g2.fill(top);
            g2.setColor(GRID_LINE);
            g2.draw(top);

            // Debug (opcional) en zoom alto
            if (zoom >= 1.4 && hover) {
                g2.setColor(Color.WHITE);
                g2.setFont(
                    new Font(Font.MONOSPACED, Font.BOLD, (int) (8 * zoom))
                );
                String label = "(" + gx + "," + gy + ")";
                FontMetrics fm = g2.getFontMetrics();
                double cx = (pA[0] + pB[0] + pC[0] + pD[0]) / 4.0;
                double cy = (pA[1] + pB[1] + pC[1] + pD[1]) / 4.0;
                g2.drawString(
                    label,
                    (int) (cx - fm.stringWidth(label) / 2.0),
                    (int) (cy + fm.getAscent() / 2.0)
                );
            }
        }

        private void drawHUD(Graphics2D g2) {
            g2.setRenderingHint(
                RenderingHints.KEY_TEXT_ANTIALIASING,
                RenderingHints.VALUE_TEXT_ANTIALIAS_ON
            );

            g2.setColor(new Color(10, 18, 30, 200));
            g2.fillRoundRect(12, 12, 260, 165, 14, 14);
            g2.setColor(new Color(100, 180, 255, 80));
            g2.setStroke(new BasicStroke(1f));
            g2.drawRoundRect(12, 12, 260, 165, 14, 14);

            g2.setFont(new Font(Font.MONOSPACED, Font.BOLD, 11));

            double deg = ((Math.toDegrees(angle) % 360) + 360) % 360;
            String[] lines = {
                " PLANTILLA ISOMÉTRICA ROTATORIA",
                " ────────────────────────────────",
                " [Q / E]     rota cámara",
                " [A]         auto-rotar: " + (autoRotate ? "ON  ◉" : "OFF ○"),
                " [Clic ↑/↓]  sube / baja tile",
                " [Arrastrar] mueve cámara",
                " [+/-] zoom  [R] reset",
                "",
                String.format(
                    " zoom: %.2f ×  hover: %s",
                    zoom,
                    hoverX >= 0 ? "(" + hoverX + "," + hoverY + ")" : "—"
                ),
                String.format(" ángulo: %05.1f°", deg),
            };

            for (int i = 0; i < lines.length; i++) {
                if (i == 0) g2.setColor(new Color(200, 240, 255));
                else if (i == 1) g2.setColor(new Color(60, 100, 140));
                else if (i == 3) g2.setColor(
                    autoRotate
                        ? new Color(80, 255, 150)
                        : new Color(160, 220, 200)
                );
                else if (i >= 8) g2.setColor(new Color(255, 210, 80));
                else g2.setColor(new Color(160, 220, 200));
                g2.drawString(lines[i], 18, 30 + i * 16);
            }
        }
    }

    // =========================================================================
    //  INTERACCIÓN Y DETECCIÓN (HOVER)
    // =========================================================================
    private void updateHover(int mx, int my) {
        double tz = BASE_TILE_Z * zoom;
        int n = GRID_COLS * GRID_ROWS;
        Integer[] ord = new Integer[n];
        double[] dep = new double[n];

        for (int i = 0; i < n; i++) {
            ord[i] = i;
            dep[i] = tileDepth(i / GRID_ROWS, i % GRID_ROWS);
        }
        // Ordenamos de adelante hacia atrás para interceptar clics correctamente
        Arrays.sort(ord, (a, b) -> Double.compare(dep[b], dep[a]));

        hoverX = hoverY = -1;

        for (int k : ord) {
            int gx = k / GRID_ROWS;
            int gy = k % GRID_ROWS;
            int gz = heights[gx][gy];

            double[] pA = project(gx, gy, gz);
            double[] pB = project(gx + 1, gy, gz);
            double[] pC = project(gx + 1, gy + 1, gz);
            double[] pD = project(gx, gy + 1, gz);

            // Colisión con la cara superior
            if (
                pointInPolygon(
                    mx,
                    my,
                    new double[] { pA[0], pB[0], pC[0], pD[0] },
                    new double[] { pA[1], pB[1], pC[1], pD[1] }
                )
            ) {
                hoverX = gx;
                hoverY = gy;
                return;
            }

            // Colisión con las caras laterales (solo las frontales visibles)
            if (gz > 0) {
                double[][] verts = { pA, pB, pC, pD };
                for (int f = 0; f < 4; f++) {
                    double[] v0 = verts[f],
                        v1 = verts[(f + 1) % 4];
                    if (v1[0] < v0[0]) {
                        if (
                            pointInPolygon(
                                mx,
                                my,
                                new double[] { v0[0], v1[0], v1[0], v0[0] },
                                new double[] {
                                    v0[1],
                                    v1[1],
                                    v1[1] + tz,
                                    v0[1] + tz,
                                }
                            )
                        ) {
                            hoverX = gx;
                            hoverY = gy;
                            return;
                        }
                    }
                }
            }
        }
    }

    private void modifyTile(int delta) {
        if (hoverX < 0 || hoverY < 0) return;
        heights[hoverX][hoverY] = Math.max(
            0,
            Math.min(MAX_HEIGHT, heights[hoverX][hoverY] + delta)
        );
    }

    // =========================================================================
    //  HELPERS MATEMÁTICOS Y GRÁFICOS
    // =========================================================================
    private static Polygon poly(
        double x0,
        double y0,
        double x1,
        double y1,
        double x2,
        double y2,
        double x3,
        double y3
    ) {
        return new Polygon(
            new int[] { (int) x0, (int) x1, (int) x2, (int) x3 },
            new int[] { (int) y0, (int) y1, (int) y2, (int) y3 },
            4
        );
    }

    private static boolean pointInPolygon(
        double px,
        double py,
        double[] vx,
        double[] vy
    ) {
        int n = vx.length;
        boolean inside = false;
        for (int i = 0, j = n - 1; i < n; j = i++) {
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

    private static Color blend(Color base, Color overlay, int doIt) {
        if (doIt == 0) return base;
        float a = overlay.getAlpha() / 255f;
        int r = (int) (base.getRed() * (1 - a) + overlay.getRed() * a);
        int g = (int) (base.getGreen() * (1 - a) + overlay.getGreen() * a);
        int b = (int) (base.getBlue() * (1 - a) + overlay.getBlue() * a);
        return new Color(Math.min(255, r), Math.min(255, g), Math.min(255, b));
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(IsometricTemplate::new);
    }
}
