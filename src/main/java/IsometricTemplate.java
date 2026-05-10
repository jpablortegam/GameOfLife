import java.awt.*;
import java.awt.event.*;
import java.util.Random;
import javax.swing.*;

/**
 * ╔═══════════════════════════════════════════════════════════════════╗
 *               PLANTILLA ISOMÉTRICA v2 — Java puro
 * ╠═══════════════════════════════════════════════════════════════════╣
 * MEJORAS vs v1:
 *
 *  1. ANIMACIÓN SUAVE       — Las alturas hacen lerp (factor 0.18/frame)
 *     al subir/bajar. Efecto de "flotación" sin costo extra.
 *
 *  2. CIELO ESTRELLADO      — 180 estrellas con efecto twinkle basado
 *     en sin(t + fase_individual). Coordenadas normalizadas [0,1].
 *
 *  3. GRADIENTE LATERAL     — Cada cara lateral usa GradientPaint vertical:
 *     color base en lo alto → 42 % más oscuro en la base. Percepción
 *     de volumen inmediata.
 *
 *  4. SOMBRAS DE CONTACTO   — Pass previo al renderizado de tiles: se
 *     dibuja el footprint del tile en z=0 con un color oscuro semitrans-
 *     parente y un pequeño offset isométrico. Simula AO de contacto.
 *
 *  5. HOVER OUTLINE GLOW    — La cara superior del tile bajo el cursor
 *     muestra un contorno blanco de 2.2 px además del blend de color.
 *
 *  6. MODO PINTURA          — Mantener pulsado Clic-Izq/Der mientras
 *     arrastras modifica tiles continuamente sin mover la cámara.
 *
 *  7. OPTIMIZACIONES DE GC/CPU:
 *     · cos/sin del ángulo: calculados UNA VEZ por frame (fcos, fsin).
 *     · Ordenamiento:  int[] + insertion sort, sin boxing de Integer.
 *     · project():     escribe en double[] pasado por parámetro, no new[].
 *     · fillPolygon:   reutiliza int[4] pre-asignados, no new[] por frame.
 *     · Hover:         usa caché de vértices del último frame pintado.
 *                      No reproyecta en cada mouseMoved.
 *
 *  8. HUD MEJORADO          — Muestra altura del tile hover; fuente y
 *     colores diferenciados por relevancia.
 *
 * CONTROLES
 *  [Q / E]        rotar cámara           [A] auto-rotate
 *  [R]            resetear escena        [+/-/Scroll] zoom
 *  Clic Izq       sube tile              Clic Der bajar tile
 *  Arrastrar*     mover cámara
 *  Ctrl+Arrastrar pintura continua       [ESC] reset total
 * ╚═══════════════════════════════════════════════════════════════════╝
 */
public class IsometricTemplate extends JFrame {

    // ── Dimensiones base (a zoom = 1.0) ──────────────────────────────────────
    private static final double TW = 64.0; // ancho del rombo isométrico
    private static final double TH = 32.0; // alto del rombo
    private static final double TZ = 22.0; // altura por nivel-Z

    // ── Grid ──────────────────────────────────────────────────────────────────
    private static final int COLS = 10;
    private static final int ROWS = 10;
    private static final int MAX_H = 6;
    private static final int N = COLS * ROWS; // 100 tiles

    // ── Alturas objetivo (enteras) y actuales (lerp, dobles) ─────────────────
    private final int[][] heights = new int[COLS][ROWS];
    private final double[][] smooth = new double[COLS][ROWS];

    // ── Hover ─────────────────────────────────────────────────────────────────
    private int hoverX = -1,
        hoverY = -1;

    // ── Cámara ────────────────────────────────────────────────────────────────
    private double camX,
        camY,
        zoom = 1.0;
    private Point dragStart;
    private int dragButton = MouseEvent.BUTTON1;
    private boolean paintMode = false; // Ctrl + arrastrar

    // ── Rotación ──────────────────────────────────────────────────────────────
    private double angle = 0.0,
        targetAngle = 0.0;
    private boolean autoRotate = false;
    private boolean qPressed = false,
        ePressed = false;

    // ── cos/sin cacheados para el frame actual (actualizados en paintComponent)
    private double fcos = 1.0,
        fsin = 0.0;

    // ── Caché de vértices proyectados (cara superior) ─────────────────────────
    // [tile_idx][vértice 0-3][x=0, y=1]  → relleno en drawTile, leído en hover
    private final double[][][] vcache = new double[N][4][2];

    // ── Ordenamiento sin boxing ───────────────────────────────────────────────
    private final int[] sortIdx = new int[N];
    private final double[] sortDepth = new double[N];

    // ── Buffers para hover (evita new double[4] en mouseMoved) ───────────────
    private final double[] hbx = new double[4],
        hby = new double[4];

    // ── Estrellas ─────────────────────────────────────────────────────────────
    private static final int NSTARS = 180;
    private final float[] sX = new float[NSTARS]; // posición normalizada [0,1]
    private final float[] sY = new float[NSTARS];
    private final float[] sA = new float[NSTARS]; // alpha base
    private final float[] sR = new float[NSTARS]; // radio en píxeles
    private float starT = 0f;

    // ── Paleta de colores por altura ─────────────────────────────────────────
    //   Índices: 0=Top, 1=Norte, 2=Este, 3=Sur, 4=Oeste
    private static final Color[][] PAL = {
        // H=0  Césped
        {
            new Color(0x5BA85A),
            new Color(0x3D7A3C),
            new Color(0x4A8A49),
            new Color(0x2E5C2D),
            new Color(0x376935),
        },
        // H=1  Tierra
        {
            new Color(0xC8A96B),
            new Color(0x9A7A47),
            new Color(0xAD8C55),
            new Color(0x755A2E),
            new Color(0x876B3A),
        },
        // H=2  Piedra
        {
            new Color(0x8C9DB5),
            new Color(0x5E7080),
            new Color(0x728A9A),
            new Color(0x445565),
            new Color(0x546070),
        },
        // H=3  Madera
        {
            new Color(0xA0673A),
            new Color(0x7A4A25),
            new Color(0x8D5830),
            new Color(0x5B3018),
            new Color(0x6B3D20),
        },
        // H=4  Nieve
        {
            new Color(0xE8F0F8),
            new Color(0xB0C4D8),
            new Color(0xC5D5E5),
            new Color(0x8A9EB8),
            new Color(0x9AADCA),
        },
        // H=5  Oro
        {
            new Color(0xFFD700),
            new Color(0xC8A800),
            new Color(0xDDBB00),
            new Color(0x957900),
            new Color(0xAA8D00),
        },
        // H=6  Cristal
        {
            new Color(0x7DF9FF),
            new Color(0x50C8D0),
            new Color(0x60D8E0),
            new Color(0x2E9099),
            new Color(0x3DA8B0),
        },
    };

    // ── Constantes visuales ───────────────────────────────────────────────────
    private static final Color HOVER_TOP = new Color(255, 255, 255, 70);
    private static final Color HOVER_SIDE = new Color(255, 255, 255, 35);
    private static final Color GRID_LINE = new Color(0, 0, 0, 50);
    private static final Color SHADOW_C = new Color(0, 0, 12, 62);
    private static final Color BG_TOP_C = new Color(0x0E1C30);
    private static final Color BG_BOT_C = new Color(0x050A14);

    // ─────────────────────────────────────────────────────────────────────────

    public IsometricTemplate() {
        super("Plantilla Isométrica v2 — Smooth + Stars + Shadows + Gradient");
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setSize(960, 700);
        setLocationRelativeTo(null);

        initScene();
        initStars();

        IsoCanvas cv = new IsoCanvas();
        add(cv);
        setVisible(true);
        recentre(cv.getWidth(), cv.getHeight());

        // ── Bucle principal ~60 FPS ────────────────────────────────────────
        new Timer(16, e -> {
            if (autoRotate) targetAngle += 0.008;

            // Lerp de ángulo (arco más corto)
            double diff = targetAngle - angle;
            diff -= Math.floor(diff / (2 * Math.PI) + 0.5) * 2 * Math.PI;
            angle += diff * 0.12;

            // Lerp de alturas (animación suave al subir/bajar)
            for (int x = 0; x < COLS; x++) for (
                int y = 0;
                y < ROWS;
                y++
            ) smooth[x][y] += (heights[x][y] - smooth[x][y]) * 0.18;

            starT += 0.038f; // reloj para twinkle
            cv.repaint();
        })
            .start();
    }

    // ── Inicialización ────────────────────────────────────────────────────────
    private void initScene() {
        for (int x = 0; x < COLS; x++) for (int y = 0; y < ROWS; y++) {
            double nx = (x - COLS / 2.0) / (COLS / 2.0);
            double ny = (y - ROWS / 2.0) / (ROWS / 2.0);
            double d = Math.sqrt(nx * nx + ny * ny);
            int h = (int) Math.round(Math.max(0, (1 - d) * 3.5));
            heights[x][y] = Math.min(h, MAX_H);
            smooth[x][y] = heights[x][y];
        }
    }

    private void initStars() {
        Random r = new Random(1234L);
        for (int i = 0; i < NSTARS; i++) {
            sX[i] = r.nextFloat();
            sY[i] = r.nextFloat() * 0.55f; // solo en la mitad superior
            sA[i] = 0.25f + r.nextFloat() * 0.75f;
            sR[i] = 0.4f + r.nextFloat() * 1.6f;
        }
    }

    private void recentre(int w, int h) {
        camX = w / 2.0;
        camY = h / 4.0 + 40;
        zoom = 1.0;
        angle = targetAngle = 0;
    }

    // =========================================================================
    //  MATEMÁTICAS 3D → 2D
    // =========================================================================

    /**
     * Proyecta (wx, wy, wz) en coordenadas de pantalla y escribe en out[0..1].
     * Usa fcos/fsin cacheados — SIN new[].
     */
    private void proj(double wx, double wy, double wz, double[] out) {
        double tw = TW * zoom,
            th = TH * zoom,
            tz = TZ * zoom;
        double cx = COLS / 2.0,
            cy = ROWS / 2.0;
        double dx = wx - cx,
            dy = wy - cy;
        double rx = dx * fcos - dy * fsin + cx;
        double ry = dx * fsin + dy * fcos + cy;
        out[0] = camX + (rx - ry) * (tw * 0.5);
        out[1] = camY + (rx + ry) * (th * 0.5) - wz * tz;
    }

    /** Profundidad del centro del tile (para Painter's Algorithm). */
    private double tileDepth(int gx, int gy) {
        double cx = COLS / 2.0,
            cy = ROWS / 2.0;
        double dx = (gx + 0.5) - cx,
            dy = (gy + 0.5) - cy;
        return (dx * fcos - dy * fsin) + (dx * fsin + dy * fcos);
    }

    // =========================================================================
    //  CANVAS
    // =========================================================================
    private class IsoCanvas extends JPanel {

        // Buffers reutilizables: 4 vértices de la cara superior
        private final double[] vA = new double[2],
            vB = new double[2],
            vC = new double[2],
            vD = new double[2];

        // Arrays int[4] para fillPolygon/drawPolygon — SIN new[] por frame
        private final int[] pxi = new int[4],
            pyi = new int[4];

        // Strokes pre-instanciados
        private final Stroke sThin = new BasicStroke(1f);
        private final Stroke sHover = new BasicStroke(
            2.2f,
            BasicStroke.CAP_ROUND,
            BasicStroke.JOIN_ROUND
        );

        // ── Constructor ───────────────────────────────────────────────────────
        IsoCanvas() {
            setBackground(BG_TOP_C);
            wireInput();
        }

        // ── Input ─────────────────────────────────────────────────────────────
        private void wireInput() {
            MouseAdapter ma = new MouseAdapter() {
                @Override
                public void mouseMoved(MouseEvent e) {
                    hoverFromCache(e.getX(), e.getY());
                }

                @Override
                public void mousePressed(MouseEvent e) {
                    dragStart = e.getPoint();
                    dragButton = e.getButton();
                    paintMode =
                        (e.getModifiersEx() & MouseEvent.CTRL_DOWN_MASK) != 0;
                    // Clic sin Ctrl → modificar tile inmediatamente
                    if (!paintMode) modifyTile(
                        hoverX,
                        hoverY,
                        dragButton == MouseEvent.BUTTON1 ? 1 : -1
                    );
                }

                @Override
                public void mouseDragged(MouseEvent e) {
                    if (dragStart == null) return;
                    if (paintMode) {
                        // Ctrl+arrastrar: pintar continuamente sin mover cámara
                        hoverFromCache(e.getX(), e.getY());
                        modifyTile(
                            hoverX,
                            hoverY,
                            dragButton == MouseEvent.BUTTON1 ? 1 : -1
                        );
                    } else {
                        // Arrastrar normal: mover cámara
                        camX += e.getX() - dragStart.x;
                        camY += e.getY() - dragStart.y;
                        dragStart = e.getPoint();
                        hoverFromCache(e.getX(), e.getY());
                    }
                }

                @Override
                public void mouseWheelMoved(MouseWheelEvent e) {
                    double f = e.getWheelRotation() < 0 ? 1.1 : 1.0 / 1.1;
                    zoom = Math.max(0.3, Math.min(3.0, zoom * f));
                }

                @Override
                public void mouseExited(MouseEvent e) {
                    hoverX = hoverY = -1;
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
                        if (k == KeyEvent.VK_ESCAPE) {
                            recentre(getWidth(), getHeight());
                            initScene();
                        }
                        if (k == KeyEvent.VK_R) recentre(
                            getWidth(),
                            getHeight()
                        );
                        if (k == KeyEvent.VK_A) autoRotate = !autoRotate;
                        if (k == KeyEvent.VK_Q && !qPressed) {
                            targetAngle -= Math.PI / 4;
                            qPressed = true;
                        }
                        if (k == KeyEvent.VK_E && !ePressed) {
                            targetAngle += Math.PI / 4;
                            ePressed = true;
                        }
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
                        if (e.getKeyCode() == KeyEvent.VK_Q) qPressed = false;
                        if (e.getKeyCode() == KeyEvent.VK_E) ePressed = false;
                    }
                }
            );
            setFocusable(true);
        }

        // ── Render principal ──────────────────────────────────────────────────
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
            g2.setRenderingHint(
                RenderingHints.KEY_RENDERING,
                RenderingHints.VALUE_RENDER_QUALITY
            );
            g2.setStroke(sThin);

            // ─── 1. Cachear cos/sin del frame ─────────────────────────────────
            fcos = Math.cos(angle);
            fsin = Math.sin(angle);

            int W = getWidth(),
                H = getHeight();

            // ─── 2. Fondo + estrellas ──────────────────────────────────────────
            drawBackground(g2, W, H);

            // ─── 3. Ordenamiento sin boxing (insertion sort, n=100) ────────────
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

            // ─── 4. Pass de sombras de contacto ───────────────────────────────
            drawShadowPass(g2);

            // ─── 5. Renderizar tiles (atrás → adelante) ───────────────────────
            for (int k = 0; k < N; k++) drawTile(g2, sortIdx[k]);

            // ─── 6. HUD ───────────────────────────────────────────────────────
            drawHUD(g2);
        }

        // ── Fondo + estrellas con twinkle ─────────────────────────────────────
        private void drawBackground(Graphics2D g2, int W, int H) {
            g2.setPaint(new GradientPaint(0, 0, BG_TOP_C, 0, H, BG_BOT_C));
            g2.fillRect(0, 0, W, H);

            for (int i = 0; i < NSTARS; i++) {
                // Twinkle: sin con fase individual por estrella (número áureo)
                float tw = 0.5f + 0.5f * (float) Math.sin(starT + i * 0.6180f);
                float a = Math.min(1f, sA[i] * (0.35f + 0.65f * tw));
                g2.setColor(new Color(1f, 1f, 1f, a));
                float x = sX[i] * W,
                    y = sY[i] * H,
                    r = sR[i];
                g2.fillOval(
                    (int) (x - r),
                    (int) (y - r),
                    (int) (2 * r + 1),
                    (int) (2 * r + 1)
                );
            }
            g2.setStroke(sThin);
        }

        // ── Sombras de contacto bajo tiles elevados ───────────────────────────
        private void drawShadowPass(Graphics2D g2) {
            g2.setColor(SHADOW_C);
            for (int x = 0; x < COLS; x++) {
                for (int y = 0; y < ROWS; y++) {
                    double h = smooth[x][y];
                    if (h < 0.05) continue;
                    // Footprint del tile proyectado en z = 0
                    proj(x, y, 0, vA);
                    proj(x + 1, y, 0, vB);
                    proj(x + 1, y + 1, 0, vC);
                    proj(x, y + 1, 0, vD);
                    // Offset isométrico proporcional a la altura → separa la sombra
                    double ox = (h * 2.2 * zoom) / TW;
                    double oy = (h * 3.2 * zoom) / TH;
                    fillQ(
                        g2,
                        vA[0] + ox,
                        vA[1] + oy,
                        vB[0] + ox,
                        vB[1] + oy,
                        vC[0] + ox,
                        vC[1] + oy,
                        vD[0] + ox,
                        vD[1] + oy
                    );
                }
            }
        }

        // ── Dibuja un tile completo y rellena vcache ──────────────────────────
        private void drawTile(Graphics2D g2, int idx) {
            int gx = idx / ROWS,
                gy = idx % ROWS;
            double gh = smooth[gx][gy];
            int ih = heights[gx][gy];
            boolean hover = (gx == hoverX && gy == hoverY);
            Color[] pal = PAL[Math.min(ih, PAL.length - 1)];
            double tz = TZ * zoom;

            // Proyectar cara superior (sin new[])
            proj(gx, gy, gh, vA);
            proj(gx + 1, gy, gh, vB);
            proj(gx + 1, gy + 1, gh, vC);
            proj(gx, gy + 1, gh, vD);

            // Guardar en caché para hover
            double[][] vc = vcache[idx];
            vc[0][0] = vA[0];
            vc[0][1] = vA[1];
            vc[1][0] = vB[0];
            vc[1][1] = vB[1];
            vc[2][0] = vC[0];
            vc[2][1] = vC[1];
            vc[3][0] = vD[0];
            vc[3][1] = vD[1];

            double[][] vs = { vA, vB, vC, vD };

            // ── Caras laterales con gradiente vertical ────────────────────────
            if (gh > 0.01) {
                for (int f = 0; f < 4; f++) {
                    double[] v0 = vs[f],
                        v1 = vs[(f + 1) % 4];
                    // Backface culling: visible si el arco va de derecha a izquierda
                    if (v1[0] >= v0[0]) continue;

                    Color topC = hover
                        ? blend(pal[f + 1], HOVER_SIDE)
                        : pal[f + 1];
                    Color botC = darker(topC, 0.58); // base 42 % más oscura

                    double topY = Math.min(v0[1], v1[1]);
                    double botY = Math.max(v0[1], v1[1]) + tz;
                    // GradientPaint vertical: arriba claro, abajo oscuro
                    g2.setPaint(
                        new GradientPaint(
                            0f,
                            (float) topY,
                            topC,
                            0f,
                            (float) botY,
                            botC
                        )
                    );
                    fillQ(
                        g2,
                        v0[0],
                        v0[1],
                        v1[0],
                        v1[1],
                        v1[0],
                        v1[1] + tz,
                        v0[0],
                        v0[1] + tz
                    );

                    g2.setColor(GRID_LINE);
                    strokeQ(
                        g2,
                        v0[0],
                        v0[1],
                        v1[0],
                        v1[1],
                        v1[0],
                        v1[1] + tz,
                        v0[0],
                        v0[1] + tz
                    );
                }
            }

            // ── Cara superior ─────────────────────────────────────────────────
            Color topC = hover ? blend(pal[0], HOVER_TOP) : pal[0];
            g2.setColor(topC);
            fillQ(g2, vA[0], vA[1], vB[0], vB[1], vC[0], vC[1], vD[0], vD[1]);

            // Outline brillante en hover
            if (hover) {
                g2.setStroke(sHover);
                g2.setColor(new Color(255, 255, 255, 130));
                strokeQ(
                    g2,
                    vA[0],
                    vA[1],
                    vB[0],
                    vB[1],
                    vC[0],
                    vC[1],
                    vD[0],
                    vD[1]
                );
                g2.setStroke(sThin);
            }

            g2.setColor(GRID_LINE);
            strokeQ(g2, vA[0], vA[1], vB[0], vB[1], vC[0], vC[1], vD[0], vD[1]);

            // Etiqueta coord+altura en hover con zoom alto
            if (zoom >= 1.5 && hover) {
                g2.setColor(new Color(255, 255, 255, 220));
                g2.setFont(
                    new Font(Font.MONOSPACED, Font.BOLD, (int) (9 * zoom))
                );
                String lbl = gx + "," + gy + " h" + ih;
                FontMetrics fm = g2.getFontMetrics();
                double cx = (vA[0] + vB[0] + vC[0] + vD[0]) * 0.25;
                double cy = (vA[1] + vB[1] + vC[1] + vD[1]) * 0.25;
                g2.drawString(
                    lbl,
                    (int) (cx - fm.stringWidth(lbl) * 0.5),
                    (int) (cy + fm.getAscent() * 0.5)
                );
            }
        }

        // ── HUD ───────────────────────────────────────────────────────────────
        private void drawHUD(Graphics2D g2) {
            g2.setRenderingHint(
                RenderingHints.KEY_TEXT_ANTIALIASING,
                RenderingHints.VALUE_TEXT_ANTIALIAS_ON
            );

            int pw = 295,
                ph = 215;
            g2.setColor(new Color(6, 14, 28, 220));
            g2.fillRoundRect(12, 12, pw, ph, 14, 14);

            g2.setStroke(new BasicStroke(1.3f));
            g2.setColor(new Color(55, 135, 255, 70));
            g2.drawRoundRect(12, 12, pw, ph, 14, 14);

            // Acento de color en la parte superior del panel
            g2.setColor(new Color(70, 200, 150, 110));
            g2.fillRoundRect(12, 12, pw, 4, 4, 4);
            g2.setStroke(sThin);

            double deg = ((Math.toDegrees(angle) % 360) + 360) % 360;
            String hStr =
                hoverX >= 0
                    ? String.format(
                          "(%d,%d)   altura = %d",
                          hoverX,
                          hoverY,
                          heights[hoverX][hoverY]
                      )
                    : "—";
            String mode = paintMode ? "PINTURA (Ctrl)" : "CÁMARA";

            Font bf = new Font(Font.MONOSPACED, Font.BOLD, 11);
            Font nf = new Font(Font.MONOSPACED, Font.PLAIN, 11);

            Object[][] lines = {
                // {Color, texto, Font}
                { new Color(120, 255, 185), " ISOMÉTRICA v2 — Mejoras", bf },
                {
                    new Color(35, 82, 160),
                    " ─────────────────────────────────",
                    nf,
                },
                { new Color(140, 210, 175), " [Q / E]       rotar cámara", nf },
                {
                    autoRotate
                        ? new Color(70, 255, 145)
                        : new Color(130, 200, 165),
                    " [A]           auto-rotar: " +
                    (autoRotate ? "ON  ◉" : "OFF ○"),
                    nf,
                },
                {
                    new Color(140, 210, 175),
                    " [Clic ↑/↓]    sube / baja tile",
                    nf,
                },
                {
                    new Color(140, 210, 175),
                    " [Ctrl+Drag]   pintura continua",
                    nf,
                },
                { new Color(140, 210, 175), " [Arrastrar]   mover cámara", nf },
                {
                    new Color(140, 210, 175),
                    " [+/-/Scroll]  zoom   [R] reset",
                    nf,
                },
                {
                    new Color(35, 82, 160),
                    " ─────────────────────────────────",
                    nf,
                },
                {
                    new Color(255, 210, 55),
                    String.format(" zoom: %.2f×   ángulo: %05.1f°", zoom, deg),
                    bf,
                },
                { new Color(100, 228, 255), " hover: " + hStr, bf },
                { new Color(180, 180, 200), " modo arrastre: " + mode, nf },
            };

            for (int i = 0; i < lines.length; i++) {
                g2.setFont((Font) lines[i][2]);
                g2.setColor((Color) lines[i][0]);
                g2.drawString((String) lines[i][1], 20, 32 + i * 16);
            }
        }

        // ── Quad fill/stroke reutilizando int[4] (sin new[]) ─────────────────

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
            pxi[0] = (int) x0;
            pxi[1] = (int) x1;
            pxi[2] = (int) x2;
            pxi[3] = (int) x3;
            pyi[0] = (int) y0;
            pyi[1] = (int) y1;
            pyi[2] = (int) y2;
            pyi[3] = (int) y3;
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
            pxi[0] = (int) x0;
            pxi[1] = (int) x1;
            pxi[2] = (int) x2;
            pxi[3] = (int) x3;
            pyi[0] = (int) y0;
            pyi[1] = (int) y1;
            pyi[2] = (int) y2;
            pyi[3] = (int) y3;
            g2.drawPolygon(pxi, pyi, 4);
        }
    }

    // =========================================================================
    //  HOVER DESDE CACHÉ DE VÉRTICES
    // =========================================================================
    /**
     * Determina qué tile está bajo el cursor usando vcache[] del último frame.
     * No reproyecta — O(N) comparaciones de punto-en-polígono.
     * Itera de adelante hacia atrás (mayor sortDepth primero).
     */
    private void hoverFromCache(int mx, int my) {
        hoverX = hoverY = -1;
        for (int k = N - 1; k >= 0; k--) {
            int idx = sortIdx[k];
            double[][] vc = vcache[idx];
            hbx[0] = vc[0][0];
            hbx[1] = vc[1][0];
            hbx[2] = vc[2][0];
            hbx[3] = vc[3][0];
            hby[0] = vc[0][1];
            hby[1] = vc[1][1];
            hby[2] = vc[2][1];
            hby[3] = vc[3][1];
            if (pip(mx, my, hbx, hby)) {
                hoverX = idx / ROWS;
                hoverY = idx % ROWS;
                return;
            }
        }
    }

    // =========================================================================
    //  MODIFICAR TILE
    // =========================================================================
    private void modifyTile(int gx, int gy, int delta) {
        if (gx < 0 || gx >= COLS || gy < 0 || gy >= ROWS) return;
        heights[gx][gy] = Math.max(0, Math.min(MAX_H, heights[gx][gy] + delta));
    }

    // =========================================================================
    //  HELPERS MATEMÁTICOS Y DE COLOR
    // =========================================================================

    /** Point-in-polygon por ray casting. */
    private static boolean pip(double px, double py, double[] vx, double[] vy) {
        int n = vx.length;
        boolean inside = false;
        for (int i = 0, j = n - 1; i < n; j = i++) {
            if (
                ((vy[i] > py) != (vy[j] > py)) &&
                (px <
                    ((vx[j] - vx[i]) * (py - vy[i])) / (vy[j] - vy[i]) + vx[i])
            ) inside = !inside;
        }
        return inside;
    }

    /** Alpha-blend de overlay sobre base usando el canal alpha de overlay. */
    private static Color blend(Color base, Color ov) {
        float a = ov.getAlpha() / 255f;
        return new Color(
            Math.min(255, (int) (base.getRed() * (1 - a) + ov.getRed() * a)),
            Math.min(
                255,
                (int) (base.getGreen() * (1 - a) + ov.getGreen() * a)
            ),
            Math.min(255, (int) (base.getBlue() * (1 - a) + ov.getBlue() * a))
        );
    }

    /** Oscurece un color por un factor multiplicativo (0 = negro, 1 = igual). */
    private static Color darker(Color c, double f) {
        return new Color(
            (int) (c.getRed() * f),
            (int) (c.getGreen() * f),
            (int) (c.getBlue() * f)
        );
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(IsometricTemplate::new);
    }
}
