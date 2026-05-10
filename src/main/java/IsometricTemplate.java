import java.awt.*;
import java.awt.event.*;
import java.awt.geom.*;
import java.awt.image.BufferedImage;
import java.util.Arrays;
import javax.swing.*;

/**
 * ╔══════════════════════════════════════════════════════════╗
 *  PLANTILLA ISOMÉTRICA — Java puro (java.awt / javax.swing)
 * ╠══════════════════════════════════════════════════════════╣
 *  MATEMÁTICAS:
 *    Proyección isométrica clásica 2:1
 *    Punto 3D (gx, gy, gz) → pantalla (sx, sy):
 *      sx = origin.x + (gx - gy) * (TILE_W / 2)
 *      sy = origin.y + (gx + gy) * (TILE_H / 2) - gz * TILE_Z
 *
 *    Detección del tile bajo el cursor (unprojection):
 *      dx = mouseX - origin.x
 *      dy = mouseY - origin.y
 *      gx = floor((dy / TILE_H + dx / TILE_W))
 *      gy = floor((dy / TILE_H - dx / TILE_W))
 *
 *  CONTROLES:
 *    Clic izquierdo  → sube el tile
 *    Clic derecho    → baja el tile
 *    Arrastrar       → desplaza la cámara
 *    R               → resetea la escena
 *    +/-             → zoom
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
    private static final int MAX_HEIGHT = 6; // niveles máximos de apilamiento

    // ── Estado de la escena ───────────────────────────────────────────────────
    private final int[][] heights = new int[GRID_COLS][GRID_ROWS];
    private int hoverX = -1,
        hoverY = -1; // tile bajo el cursor

    // ── Cámara ────────────────────────────────────────────────────────────────
    private double camX, camY; // traslación de cámara
    private double zoom = 1.0;
    private Point dragStart; // para el pan

    // ── Paleta de colores por altura ─────────────────────────────────────────
    // Cada altura tiene: {top, left, right}
    private static final Color[][] PALETTE = {
        // H = 0  — césped
        { new Color(0x5BA85A), new Color(0x3D7A3C), new Color(0x2E5C2D) },
        // H = 1  — tierra
        { new Color(0xC8A96B), new Color(0x9A7A47), new Color(0x755A2E) },
        // H = 2  — piedra
        { new Color(0x8C9DB5), new Color(0x5E7080), new Color(0x445565) },
        // H = 3  — madera
        { new Color(0xA0673A), new Color(0x7A4A25), new Color(0x5B3018) },
        // H = 4  — nieve
        { new Color(0xE8F0F8), new Color(0xB0C4D8), new Color(0x8A9EB8) },
        // H = 5  — oro
        { new Color(0xFFD700), new Color(0xC8A800), new Color(0x957900) },
        // H = 6  — cristal
        { new Color(0x7DF9FF), new Color(0x50C8D0), new Color(0x2E9099) },
    };

    // ── Color de hover (overlay semitransparente) ─────────────────────────────
    private static final Color HOVER_TOP = new Color(255, 255, 255, 60);
    private static final Color HOVER_SIDE = new Color(255, 255, 255, 30);
    private static final Color GRID_LINE = new Color(0, 0, 0, 45);
    private static final Color BG_TOP = new Color(0x1A2535);
    private static final Color BG_BOTTOM = new Color(0x0D151F);

    // ─────────────────────────────────────────────────────────────────────────

    public IsometricTemplate() {
        super("Plantilla Isométrica — Java puro");
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setSize(900, 650);
        setLocationRelativeTo(null);

        // Escena inicial con algo de relieve
        initScene();

        IsometricCanvas canvas = new IsometricCanvas();
        add(canvas);

        setVisible(true);
        // Centra la cuadrícula en la ventana al inicio
        recentreCamera(canvas.getWidth(), canvas.getHeight());
    }

    /** Genera una escena inicial con variaciones de altura. */
    private void initScene() {
        for (int x = 0; x < GRID_COLS; x++) {
            for (int y = 0; y < GRID_ROWS; y++) {
                // Función de ejemplo: colina suave + borde en 0
                double nx = (x - GRID_COLS / 2.0) / (GRID_COLS / 2.0);
                double ny = (y - GRID_ROWS / 2.0) / (GRID_ROWS / 2.0);
                double dist = Math.sqrt(nx * nx + ny * ny);
                int h = (int) Math.round(Math.max(0, (1 - dist) * 3.5));
                heights[x][y] = Math.min(h, MAX_HEIGHT);
            }
        }
    }

    /** Centra la cámara sobre la cuadrícula. */
    private void recentreCamera(int w, int h) {
        // Centro del grid en coordenadas pantalla (sin cámara)
        double cx =
            (GRID_COLS - GRID_ROWS) * 0.5 * ((BASE_TILE_W * zoom) / 2.0) +
            (GRID_COLS + GRID_ROWS) * 0.5 * 0;
        camX = w / 2.0;
        camY = h / 4.0 + 40;
    }

    // =========================================================================
    //  CANVAS
    // =========================================================================
    private class IsometricCanvas extends JPanel {

        IsometricCanvas() {
            setBackground(BG_TOP);

            MouseAdapter mouse = new MouseAdapter() {
                @Override
                public void mouseMoved(MouseEvent e) {
                    updateHover(e.getX(), e.getY());
                    repaint();
                }

                @Override
                public void mousePressed(MouseEvent e) {
                    dragStart = e.getPoint();
                    if (e.getButton() == MouseEvent.BUTTON1) modifyTile(+1);
                    else if (e.getButton() == MouseEvent.BUTTON3) modifyTile(
                        -1
                    );
                    repaint();
                }

                @Override
                public void mouseDragged(MouseEvent e) {
                    if (dragStart != null) {
                        camX += e.getX() - dragStart.x;
                        camY += e.getY() - dragStart.y;
                        dragStart = e.getPoint();
                        updateHover(e.getX(), e.getY());
                        repaint();
                    }
                }

                @Override
                public void mouseExited(MouseEvent e) {
                    hoverX = hoverY = -1;
                    repaint();
                }
            };
            addMouseMotionListener(mouse);
            addMouseListener(mouse);

            addKeyListener(
                new KeyAdapter() {
                    @Override
                    public void keyPressed(KeyEvent e) {
                        if (e.getKeyChar() == 'r' || e.getKeyChar() == 'R') {
                            initScene();
                            recentreCamera(getWidth(), getHeight());
                        }
                        if (e.getKeyChar() == '+' || e.getKeyChar() == '=') {
                            zoom = Math.min(zoom * 1.1, 3.0);
                        }
                        if (e.getKeyChar() == '-') {
                            zoom = Math.max(zoom / 1.1, 0.3);
                        }
                        repaint();
                    }
                }
            );
            setFocusable(true);
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g;

            // Antialiasing
            g2.setRenderingHint(
                RenderingHints.KEY_ANTIALIASING,
                RenderingHints.VALUE_ANTIALIAS_ON
            );
            g2.setRenderingHint(
                RenderingHints.KEY_STROKE_CONTROL,
                RenderingHints.VALUE_STROKE_PURE
            );

            // Fondo degradado
            drawBackground(g2);

            // ── Render isométrico ─────────────────────────────────────────────
            // Orden correcto de pintado: painter's algorithm
            // x + y creciente → más cercano al espectador
            for (int sum = 0; sum <= (GRID_COLS - 1) + (GRID_ROWS - 1); sum++) {
                for (int x = 0; x < GRID_COLS; x++) {
                    int y = sum - x;
                    if (y < 0 || y >= GRID_ROWS) continue;
                    drawTile(g2, x, y, heights[x][y]);
                }
            }

            // HUD / overlay
            drawHUD(g2);
        }

        private void drawBackground(Graphics2D g2) {
            int w = getWidth(),
                h = getHeight();
            GradientPaint gp = new GradientPaint(0, 0, BG_TOP, 0, h, BG_BOTTOM);
            g2.setPaint(gp);
            g2.fillRect(0, 0, w, h);
        }

        /**
         * Dibuja un tile isométrico en (gx, gy) con altura gz.
         * Cada tile tiene tres caras: top, left, right.
         */
        private void drawTile(Graphics2D g2, int gx, int gy, int gz) {
            double tw = BASE_TILE_W * zoom;
            double th = BASE_TILE_H * zoom;
            double tz = BASE_TILE_Z * zoom;

            // ── Proyección isométrica ─────────────────────────────────────────
            // Vértice superior del rombo (sin altura Z)
            double sx = camX + (gx - gy) * (tw / 2.0);
            double sy = camY + (gx + gy) * (th / 2.0) - gz * tz;

            // ── 5 puntos clave del tile ───────────────────────────────────────
            //        top(T)
            //       /       \
            //     lM         rM   ← mitades laterales
            //       \       /
            //        bot(B)
            //
            // Con cara Z (lateral), proyectados hacia abajo tz unidades:
            //
            //    T ─── rM         rM ─── T
            //    |      |         |      |
            //   lMz ─ rMz        rMz ─ Tz     (no se usa Tz aquí)

            double tx = sx,
                tY = sy; // top
            double lmx = sx - tw / 2.0,
                lmY = sy + th / 2.0; // left-mid
            double rmx = sx + tw / 2.0,
                rmY = sy + th / 2.0; // right-mid
            double bx = sx,
                bY = sy + th; // bottom

            // Caras laterales van desde la fila del rombo hacia abajo tz
            double lmxz = lmx,
                lmYz = lmY + tz;
            double rmxz = rmx,
                rmYz = rmY + tz;
            double bxz = bx,
                bYz = bY + tz;

            Color[] pal = PALETTE[Math.min(gz, PALETTE.length - 1)];
            boolean hover = (gx == hoverX && gy == hoverY);

            // ── Cara IZQUIERDA ────────────────────────────────────────────────
            if (gz > 0) {
                Polygon left = poly(lmx, lmY, bx, bY, bxz, bYz, lmxz, lmYz);
                g2.setColor(blend(pal[1], HOVER_SIDE, hover ? 1 : 0));
                g2.fill(left);
                g2.setColor(GRID_LINE);
                g2.draw(left);
            }

            // ── Cara DERECHA ──────────────────────────────────────────────────
            if (gz > 0) {
                Polygon right = poly(rmx, rmY, bx, bY, bxz, bYz, rmxz, rmYz);
                g2.setColor(blend(pal[2], HOVER_SIDE, hover ? 1 : 0));
                g2.fill(right);
                g2.setColor(GRID_LINE);
                g2.draw(right);
            }

            // ── Cara SUPERIOR (top) ───────────────────────────────────────────
            Polygon top = poly(tx, tY, rmx, rmY, bx, bY, lmx, lmY);
            g2.setColor(hover ? blend(pal[0], HOVER_TOP, 1) : pal[0]);
            g2.fill(top);
            g2.setColor(GRID_LINE);
            g2.draw(top);

            // ── Coordenadas (debug opcional) ──────────────────────────────────
            if (zoom >= 1.4 && hover) {
                g2.setColor(Color.WHITE);
                g2.setFont(
                    new Font(Font.MONOSPACED, Font.BOLD, (int) (8 * zoom))
                );
                String label = "(" + gx + "," + gy + ")";
                FontMetrics fm = g2.getFontMetrics();
                int lw = fm.stringWidth(label);
                g2.drawString(
                    label,
                    (int) (sx - lw / 2.0),
                    (int) (sy + th / 2.0 + fm.getAscent() / 2.0)
                );
            }
        }

        private void drawHUD(Graphics2D g2) {
            g2.setRenderingHint(
                RenderingHints.KEY_TEXT_ANTIALIASING,
                RenderingHints.VALUE_TEXT_ANTIALIAS_ON
            );

            // Panel semi-transparente
            g2.setColor(new Color(10, 18, 30, 200));
            g2.fillRoundRect(12, 12, 230, 130, 14, 14);
            g2.setColor(new Color(100, 180, 255, 80));
            g2.setStroke(new BasicStroke(1f));
            g2.drawRoundRect(12, 12, 230, 130, 14, 14);

            g2.setFont(new Font(Font.MONOSPACED, Font.BOLD, 11));
            g2.setColor(new Color(160, 220, 255));

            String[] lines = {
                " PLANTILLA ISOMÉTRICA",
                " ─────────────────────────",
                " [Clic ↑] sube tile",
                " [Clic ↓] baja tile",
                " [Arrastrar] mueve cámara",
                " [+/-] zoom  [R] reset",
                String.format(
                    " zoom: %.2f × hover: %s",
                    zoom,
                    hoverX >= 0 ? "(" + hoverX + "," + hoverY + ")" : "—"
                ),
            };

            for (int i = 0; i < lines.length; i++) {
                if (i == 0) g2.setColor(new Color(200, 240, 255));
                else if (i == 1) g2.setColor(new Color(60, 100, 140));
                else g2.setColor(new Color(160, 220, 200));
                g2.drawString(lines[i], 18, 30 + i * 16);
            }
        }
    }

    // =========================================================================
    //  HELPERS
    // =========================================================================

    /**
     * Crea un Polygon a partir de pares (x,y) como doubles.
     * Acepta exactamente 4 puntos (8 valores).
     */
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

    /**
     * Mezcla dos colores según t ∈ [0,1].
     * Cuando t=0 devuelve base; t=1 añade overlay encima (alpha-blend).
     */
    private static Color blend(Color base, Color overlay, int doIt) {
        if (doIt == 0) return base;
        float a = overlay.getAlpha() / 255f;
        int r = (int) (base.getRed() * (1 - a) + overlay.getRed() * a);
        int g = (int) (base.getGreen() * (1 - a) + overlay.getGreen() * a);
        int b = (int) (base.getBlue() * (1 - a) + overlay.getBlue() * a);
        return new Color(Math.min(255, r), Math.min(255, g), Math.min(255, b));
    }

    /**
     * Convierte coordenadas de pantalla → coordenadas de cuadrícula.
     *
     *  Matemáticas (inversión de la proyección):
     *    dx = mouseX - camX
     *    dy = mouseY - camY
     *    gx = floor( dy/th + dx/tw )
     *    gy = floor( dy/th - dx/tw )
     *
     *  Nota: ignoramos Z para la detección (usamos la superficie del tile
     *  en su altura real, con un ajuste sencillo por heights).
     */
    private void updateHover(int mx, int my) {
        double tw = BASE_TILE_W * zoom;
        double th = BASE_TILE_H * zoom;
        double tz = BASE_TILE_Z * zoom;

        int bestX = -1,
            bestY = -1;

        // Iteramos sobre tiles y detectamos cuál está bajo el cursor
        // en orden correcto (igual que el render) para respetar el Z correcto.
        for (int sum = (GRID_COLS - 1) + (GRID_ROWS - 1); sum >= 0; sum--) {
            for (int x = GRID_COLS - 1; x >= 0; x--) {
                int y = sum - x;
                if (y < 0 || y >= GRID_ROWS) continue;
                int gz = heights[x][y];

                double sx = camX + (x - y) * (tw / 2.0);
                double sy = camY + (x + y) * (th / 2.0) - gz * tz;

                // Top face del tile (rombo)
                double[] topX = { sx, sx + tw / 2, sx, sx - tw / 2 };
                double[] topY = { sy, sy + th / 2, sy + th, sy + th / 2 };

                if (pointInPolygon(mx, my, topX, topY)) {
                    bestX = x;
                    bestY = y;
                    break;
                }
                // Caras laterales si gz > 0
                if (gz > 0) {
                    double lmY = sy + th / 2.0;
                    double bY = sy + th;
                    // Cara izquierda: lm → bot → bot+z → lm+z
                    double[] lx = { sx - tw / 2, sx, sx, sx - tw / 2 };
                    double[] ly = { lmY, bY, bY + tz, lmY + tz };
                    if (pointInPolygon(mx, my, lx, ly)) {
                        bestX = x;
                        bestY = y;
                        break;
                    }
                    // Cara derecha
                    double[] rx = { sx + tw / 2, sx, sx, sx + tw / 2 };
                    double[] ry = { lmY, bY, bY + tz, lmY + tz };
                    if (pointInPolygon(mx, my, rx, ry)) {
                        bestX = x;
                        bestY = y;
                        break;
                    }
                }
            }
            if (bestX >= 0) break;
        }

        hoverX = bestX;
        hoverY = bestY;
    }

    /**
     * Ray-casting: ¿está el punto (px, py) dentro del polígono?
     * Algoritmo clásico de paridad de cruces.
     */
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

    /** Sube o baja el tile en hover. */
    private void modifyTile(int delta) {
        if (hoverX < 0 || hoverY < 0) return;
        heights[hoverX][hoverY] = Math.max(
            0,
            Math.min(MAX_HEIGHT, heights[hoverX][hoverY] + delta)
        );
    }

    // =========================================================================
    //  MAIN
    // =========================================================================
    public static void main(String[] args) {
        SwingUtilities.invokeLater(IsometricTemplate::new);
    }
}
