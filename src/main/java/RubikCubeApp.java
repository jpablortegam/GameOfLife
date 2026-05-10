import java.awt.*;
import java.awt.event.*;
import java.awt.geom.Path2D;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.List;
import java.util.Random;
import javax.swing.*;

/**
 * ╔════════════════════════════════════════════════════════════════════╗
 * MOTOR 3D MEJORADO — CUBO RUBIK v2
 * ╠════════════════════════════════════════════════════════════════════╣
 * Mejoras respecto a la versión anterior:
 *
 *  1. ILUMINACIÓN PHONG  — Ambiente + Difusa + Especular (Blinn-Phong).
 *     La intensidad de cada cara se calcula a partir de su normal 3D y
 *     una fuente de luz fija en espacio-cámara (0.577, 0.577, 0.577).
 *
 *  2. STICKERS REALISTAS — Cada cara se dibuja en dos pasadas:
 *     a) Cuadrilátero negro como borde/plástico del cubie.
 *     b) Quad interior escalado al 76 % (la calcomanía) con el color
 *        ya modulado por la iluminación.
 *
 *  3. EASING CÚBICO      — La animación usa ease-in-out cubic
 *     (t<0.5 → 4t³  /  t≥0.5 → 1-(-2t+2)³/2) en vez de lineal.
 *     Resultado: arranque y frenado suaves, sin salto brusco al final.
 *
 *  4. HISTORIAL + UNDO   — Cada movimiento queda en un Deque.
 *     [Z] deshace el último turno aplicando el inverso sin animación extra.
 *
 *  5. MEZCLAR (SCRAMBLE) — [S] aplica 25 giros aleatorios instantáneos
 *     y arranca el cronómetro.
 *
 *  6. HUD MEJORADO       — Muestra contador de movimientos y cronómetro
 *     SS:cs (segundos : centésimas de segundo).
 *
 *  7. MENOR PRESIÓN GC   — buildPath() reutiliza un único Path2D por
 *     frame; clamp() inline evita boxing; se pre-reserva la lista con
 *     capacidad conocida (100 caras visibles máximo).
 *
 * ╠════════════════════════════════════════════════════════════════════╣
 * Controles
 *  Arrastrar       → rotar cámara                 SHIFT+letra → inverso
 *  U/D/L/R/F/B     → girar capa                   S → mezclar
 *  Z               → deshacer último movimiento   ESC → reiniciar
 * ╚════════════════════════════════════════════════════════════════════╝
 */
public class RubikCubeApp extends JFrame {

    public RubikCubeApp() {
        super("Motor 3D v2 — Cubo Rubik (Phong + Stickers)");
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setSize(1060, 790);
        setLocationRelativeTo(null);
        RubikCanvas canvas = new RubikCanvas();
        add(canvas);
        setVisible(true);
        // Bucle principal ~60 FPS
        new Timer(16, e -> canvas.tick()).start();
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(RubikCubeApp::new);
    }

    // =========================================================================
    //  CANVAS PRINCIPAL & MOTOR 3D
    // =========================================================================
    class RubikCanvas extends JPanel {

        // ── Paleta estándar occidental ─────────────────────────────────────────
        private final Color C_R = new Color(0xD50000); // Derecho  +X  Rojo
        private final Color C_L = new Color(0xFF6D00); // Izquierdo -X  Naranja
        private final Color C_U = new Color(0xFFFFFF); // Arriba   +Y  Blanco
        private final Color C_D = new Color(0xFFD600); // Abajo    -Y  Amarillo
        private final Color C_F = new Color(0x00C853); // Frente   +Z  Verde
        private final Color C_B = new Color(0x2962FF); // Atrás    -Z  Azul
        private final Color C_IN = new Color(0x151515); // Interior (oculto)

        private final Cubie[] cubies = new Cubie[27];

        // ── Cámara ────────────────────────────────────────────────────────────
        private double camRotX = 0.50;
        private double camRotY = -0.60;
        private Point lastMouse;

        // ── Animación de turno ────────────────────────────────────────────────
        private boolean isAnimating = false;
        private int animAxis, animLayer, animDir;
        private double animProgress = 0.0;
        private static final double ANIM_SPEED = 0.072; // ≈ 13 frames por giro

        // ── Historial para Undo ───────────────────────────────────────────────
        private final Deque<int[]> history = new ArrayDeque<>();

        // ── Estadísticas ──────────────────────────────────────────────────────
        private int moveCount = 0;
        private long startTimeMs = -1; // -1 = cronómetro parado

        // ── Iluminación Phong (espacio-cámara fijo) ───────────────────────────
        //    Fuente a 45° diagonal superior-derecha-frente → normalizada (1,1,1)/√3
        private static final double L_X = 0.57735;
        private static final double L_Y = 0.57735;
        private static final double L_Z = 0.57735;
        private static final double AMBIENT = 0.32; // mínimo de luz ambiente
        private static final double SPEC_EXP = 56.0; // brillo especular
        private static final double SPEC_K = 0.42; // fuerza especular

        // ── Sticker ───────────────────────────────────────────────────────────
        private static final double INSET = 0.76; // 76 % del quad = sticker

        // ── Path2D reutilizable (reduce GC) ──────────────────────────────────
        private final Path2D.Double reusePath = new Path2D.Double();

        // ── Estilos pre-creados ───────────────────────────────────────────────
        private final Stroke strokeThick = new BasicStroke(
            3.8f,
            BasicStroke.CAP_ROUND,
            BasicStroke.JOIN_ROUND
        );
        private final Stroke strokeThin = new BasicStroke(
            1.1f,
            BasicStroke.CAP_ROUND,
            BasicStroke.JOIN_ROUND
        );
        private final Color borderColor = new Color(0, 0, 0, 215);
        private final Color stickerEdge = new Color(0, 0, 0, 65);
        private final Color sheen = new Color(255, 255, 255, 16);

        // ── Lista de caras renderizables (pre-reservada) ──────────────────────
        private final List<RenderFace> renderList = new ArrayList<>(108);

        // ─────────────────────────────────────────────────────────────────────

        public RubikCanvas() {
            setBackground(new Color(0x0A0F1A));
            initCube();
            wireInput();
        }

        // ── Inicialización ────────────────────────────────────────────────────
        private void initCube() {
            int i = 0;
            for (int x = -1; x <= 1; x++) for (int y = -1; y <= 1; y++) for (
                int z = -1;
                z <= 1;
                z++
            ) cubies[i++] = new Cubie(
                x,
                y,
                z,
                x == 1 ? C_R : C_IN,
                x == -1 ? C_L : C_IN,
                y == 1 ? C_U : C_IN,
                y == -1 ? C_D : C_IN,
                z == 1 ? C_F : C_IN,
                z == -1 ? C_B : C_IN
            );
            history.clear();
            moveCount = 0;
            startTimeMs = -1;
            repaint();
        }

        // ── Ratón & Teclado ───────────────────────────────────────────────────
        private void wireInput() {
            MouseAdapter ma = new MouseAdapter() {
                @Override
                public void mousePressed(MouseEvent e) {
                    lastMouse = e.getPoint();
                }

                @Override
                public void mouseDragged(MouseEvent e) {
                    camRotY -= (e.getX() - lastMouse.x) * 0.0080;
                    camRotX += (e.getY() - lastMouse.y) * 0.0080;
                    lastMouse = e.getPoint();
                    repaint();
                }
            };
            addMouseListener(ma);
            addMouseMotionListener(ma);

            addKeyListener(
                new KeyAdapter() {
                    @Override
                    public void keyPressed(KeyEvent e) {
                        switch (e.getKeyCode()) {
                            case KeyEvent.VK_ESCAPE -> {
                                camRotX = 0.50;
                                camRotY = -0.60;
                                initCube();
                                return;
                            }
                            case KeyEvent.VK_S -> {
                                if (!isAnimating) scramble();
                                return;
                            }
                            case KeyEvent.VK_Z -> {
                                if (!isAnimating) undo();
                                return;
                            }
                        }
                        if (isAnimating) return;
                        boolean sh = e.isShiftDown();
                        int d = sh ? -1 : 1;
                        switch (e.getKeyCode()) {
                            case KeyEvent.VK_U -> doMove(1, 1, d);
                            case KeyEvent.VK_D -> doMove(1, -1, -d);
                            case KeyEvent.VK_R -> doMove(0, 1, d);
                            case KeyEvent.VK_L -> doMove(0, -1, -d);
                            case KeyEvent.VK_F -> doMove(2, 1, d);
                            case KeyEvent.VK_B -> doMove(2, -1, -d);
                        }
                    }
                }
            );
            setFocusable(true);
        }

        // ── Movimiento registrado (con historial y cronómetro) ────────────────
        private void doMove(int axis, int layer, int dir) {
            if (startTimeMs < 0) startTimeMs = System.currentTimeMillis();
            history.push(new int[] { axis, layer, dir });
            moveCount++;
            startAnim(axis, layer, dir);
        }

        private void startAnim(int axis, int layer, int dir) {
            animAxis = axis;
            animLayer = layer;
            animDir = dir;
            animProgress = 0.0;
            isAnimating = true;
        }

        // ── Deshacer ──────────────────────────────────────────────────────────
        private void undo() {
            if (history.isEmpty()) return;
            int[] last = history.pop();
            moveCount = Math.max(0, moveCount - 1);
            startAnim(last[0], last[1], -last[2]); // inverso, sin re-registrar
        }

        // ── Mezclar ───────────────────────────────────────────────────────────
        private void scramble() {
            // {eje, capa}
            int[][] slots = {
                { 0, 1 },
                { 0, -1 },
                { 1, 1 },
                { 1, -1 },
                { 2, 1 },
                { 2, -1 },
            };
            Random rnd = new Random();
            for (int i = 0; i < 25; i++) {
                int[] s = slots[rnd.nextInt(6)];
                int dir = rnd.nextBoolean() ? 1 : -1;
                for (Cubie c : cubies)
                    if (c.getPos(s[0]) == s[1]) c.rotateLogical(s[0], dir);
            }
            history.clear();
            moveCount = 0;
            startTimeMs = System.currentTimeMillis();
            repaint();
        }

        // ── Bucle de animación ────────────────────────────────────────────────
        public void tick() {
            if (!isAnimating) return;
            animProgress += ANIM_SPEED;
            if (animProgress >= 1.0) {
                animProgress = 1.0;
                isAnimating = false;
                commitTurn();
            }
            repaint();
        }

        /** Aplica la permutación lógica al terminar la animación. */
        private void commitTurn() {
            for (Cubie c : cubies)
                if (c.getPos(animAxis) == animLayer) c.rotateLogical(
                    animAxis,
                    animDir
                );
        }

        /**
         * Ease-in-out cúbico.
         * t < 0.5  →  4·t³
         * t ≥ 0.5  →  1 − (−2t+2)³ / 2
         */
        private static double ease(double t) {
            return t < 0.5
                ? 4 * t * t * t
                : 1.0 - Math.pow(-2 * t + 2, 3) * 0.5;
        }

        // ── Renderizado ───────────────────────────────────────────────────────
        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g;

            // Calidad máxima
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
                RenderingHints.KEY_COLOR_RENDERING,
                RenderingHints.VALUE_COLOR_RENDER_QUALITY
            );

            final int W = getWidth(),
                H = getHeight();

            // Fondo degradado radial oscuro (plasma espacial)
            g2.setPaint(
                new RadialGradientPaint(
                    W * 0.42f,
                    H * 0.38f,
                    W * 0.75f,
                    new float[] { 0f, 0.6f, 1f },
                    new Color[] {
                        new Color(0x1A2840),
                        new Color(0x0D1620),
                        new Color(0x050810),
                    }
                )
            );
            g2.fillRect(0, 0, W, H);

            // ── Recopilación de caras visibles ─────────────────────────────────
            renderList.clear();
            final double animAngle =
                ease(animProgress) * (Math.PI / 2.0) * animDir;

            for (Cubie cubie : cubies) {
                final boolean inLayer =
                    isAnimating && cubie.getPos(animAxis) == animLayer;

                for (Face face : cubie.faces) {
                    if (face.color == C_IN) continue;

                    // Transformar los 4 vértices
                    final Point3D[] t = new Point3D[4];
                    double zSum = 0;
                    for (int i = 0; i < 4; i++) {
                        Point3D p = face.vertices[i].copy();
                        p.x += cubie.x;
                        p.y += cubie.y;
                        p.z += cubie.z;
                        if (inLayer) p.rotateAxis(animAxis, animAngle);
                        p.rotateX(camRotX);
                        p.rotateY(camRotY);
                        t[i] = p;
                        zSum += p.z;
                    }

                    // Proyección perspectiva (distancia focal = 680)
                    final double[] px = new double[4],
                        py = new double[4];
                    for (int i = 0; i < 4; i++) {
                        final double zD = 9.0 - t[i].z;
                        final double sc = 680.0 / zD;
                        px[i] = W * 0.5 + t[i].x * sc;
                        py[i] = H * 0.5 - t[i].y * sc;
                    }

                    // Backface culling — Área signed 2D (Shoelace)
                    final double area =
                        (px[1] - px[0]) * (py[2] - py[0]) -
                        (py[1] - py[0]) * (px[2] - px[0]);
                    if (area >= 0) continue; // cara mirando hacia atrás → descartar

                    // ── Iluminación Phong (Blinn-Phong) ─────────────────────────
                    // Normal de la cara: producto vectorial u × v en espacio-cámara
                    final double ux = t[1].x - t[0].x,
                        uy = t[1].y - t[0].y,
                        uz = t[1].z - t[0].z;
                    final double vx = t[2].x - t[0].x,
                        vy = t[2].y - t[0].y,
                        vz = t[2].z - t[0].z;
                    double nx = uy * vz - uz * vy;
                    double ny = uz * vx - ux * vz;
                    double nz = ux * vy - uy * vx;
                    final double nl = Math.sqrt(nx * nx + ny * ny + nz * nz);
                    if (nl > 1e-9) {
                        nx /= nl;
                        ny /= nl;
                        nz /= nl;
                    }

                    // Componente difusa (Lambert)
                    final double diffuse = Math.max(
                        0.0,
                        nx * L_X + ny * L_Y + nz * L_Z
                    );

                    // Componente especular (Blinn-Phong — semivector H = norm(L + V))
                    // V = (0,0,1) en espacio-cámara
                    final double hLen = Math.sqrt(
                        L_X * L_X + L_Y * L_Y + (L_Z + 1.0) * (L_Z + 1.0)
                    );
                    final double specDot = Math.max(
                        0.0,
                        nx * (L_X / hLen) +
                            ny * (L_Y / hLen) +
                            nz * ((L_Z + 1.0) / hLen)
                    );
                    final double spec = Math.pow(specDot, SPEC_EXP) * SPEC_K;

                    // Intensidad total
                    final double intens = AMBIENT + (1.0 - AMBIENT) * diffuse;

                    // Color final con iluminación
                    final Color fc = face.color;
                    final int r = clamp(
                        (int) (fc.getRed() * intens + 255.0 * spec)
                    );
                    final int gv = clamp(
                        (int) (fc.getGreen() * intens + 255.0 * spec)
                    );
                    final int b = clamp(
                        (int) (fc.getBlue() * intens + 255.0 * spec)
                    );

                    // ── Sticker inset ────────────────────────────────────────────
                    // Centro del quad proyectado
                    final double cx = (px[0] + px[1] + px[2] + px[3]) * 0.25;
                    final double cy = (py[0] + py[1] + py[2] + py[3]) * 0.25;
                    final double[] sx = new double[4],
                        sy = new double[4];
                    for (int i = 0; i < 4; i++) {
                        sx[i] = cx + (px[i] - cx) * INSET;
                        sy[i] = cy + (py[i] - cy) * INSET;
                    }

                    renderList.add(
                        new RenderFace(
                            px,
                            py,
                            sx,
                            sy,
                            zSum * 0.25,
                            new Color(r, gv, b)
                        )
                    );
                }
            }

            // ── Painter's Algorithm ────────────────────────────────────────────
            Collections.sort(renderList); // de más lejos (z menor) a más cerca

            // ── Dibujado ──────────────────────────────────────────────────────
            for (RenderFace rf : renderList) {
                // 1) Cara negra exterior (plástico del cubie)
                fillPath(g2, rf.x, rf.y, new Color(10, 10, 10));
                g2.setStroke(strokeThick);
                g2.setColor(borderColor);
                strokePath(g2, rf.x, rf.y);

                // 2) Sticker de color (con iluminación Phong)
                fillPath(g2, rf.sx, rf.sy, rf.color);

                // Borde sutil del sticker
                g2.setStroke(strokeThin);
                g2.setColor(stickerEdge);
                strokePath(g2, rf.sx, rf.sy);

                // Reflejo especular tenue (highlight de superficie plástica)
                fillPath(g2, rf.sx, rf.sy, sheen);
            }

            drawHUD(g2, W, H);
        }

        /** Rellena un quad con el Path2D reutilizable. */
        private void fillPath(
            Graphics2D g2,
            double[] px,
            double[] py,
            Color c
        ) {
            reusePath.reset();
            reusePath.moveTo(px[0], py[0]);
            reusePath.lineTo(px[1], py[1]);
            reusePath.lineTo(px[2], py[2]);
            reusePath.lineTo(px[3], py[3]);
            reusePath.closePath();
            g2.setColor(c);
            g2.fill(reusePath);
        }

        /** Traza el contorno del Path2D reutilizable (sin rellenar). */
        private void strokePath(Graphics2D g2, double[] px, double[] py) {
            reusePath.reset();
            reusePath.moveTo(px[0], py[0]);
            reusePath.lineTo(px[1], py[1]);
            reusePath.lineTo(px[2], py[2]);
            reusePath.lineTo(px[3], py[3]);
            reusePath.closePath();
            g2.draw(reusePath);
        }

        private static int clamp(int v) {
            return v < 0 ? 0 : v > 255 ? 255 : v;
        }

        // ── HUD ───────────────────────────────────────────────────────────────
        private void drawHUD(Graphics2D g2, int W, int H) {
            final long elapsed =
                startTimeMs > 0 ? System.currentTimeMillis() - startTimeMs : 0;
            final String timeStr = String.format(
                "%02d:%02d",
                elapsed / 1000,
                (elapsed % 1000) / 10
            );

            // Panel translúcido con borde azulado
            final int PW = 300,
                PH = 268;
            g2.setColor(new Color(6, 12, 24, 230));
            g2.fillRoundRect(18, 18, PW, PH, 16, 16);
            g2.setStroke(new BasicStroke(1.4f));
            g2.setColor(new Color(50, 130, 255, 85));
            g2.drawRoundRect(18, 18, PW, PH, 16, 16);

            // Línea de acento superior
            g2.setColor(new Color(70, 200, 140, 140));
            g2.fillRoundRect(18, 18, PW, 4, 4, 4);

            final Font bold = new Font(Font.MONOSPACED, Font.BOLD, 12);
            final Font norm = new Font(Font.MONOSPACED, Font.PLAIN, 11);
            g2.setFont(bold);

            final Object[][] lines = {
                // {Color, texto, fuente}
                {
                    new Color(70, 255, 165),
                    " RUBIK 3D — PHONG + STICKERS",
                    bold,
                },
                {
                    new Color(45, 90, 200),
                    " ─────────────────────────────",
                    norm,
                },
                {
                    new Color(180, 210, 240),
                    " [Arrastrar]  Rotar cámara",
                    norm,
                },
                {
                    new Color(45, 90, 200),
                    " ─────────────────────────────",
                    norm,
                },
                { new Color(180, 210, 240), " [U] Arriba    [D] Abajo", norm },
                {
                    new Color(180, 210, 240),
                    " [R] Derecha   [L] Izquierda",
                    norm,
                },
                { new Color(180, 210, 240), " [F] Frente    [B] Atrás", norm },
                {
                    new Color(45, 90, 200),
                    " ─────────────────────────────",
                    norm,
                },
                {
                    new Color(255, 200, 60),
                    " [S] Mezclar   [Z] Deshacer",
                    bold,
                },
                { new Color(255, 90, 90), " [ESC] Reiniciar cubo", bold },
                {
                    new Color(45, 90, 200),
                    " ─────────────────────────────",
                    norm,
                },
                {
                    new Color(80, 230, 255),
                    " Movimientos : " + moveCount,
                    bold,
                },
                { new Color(80, 230, 255), " Tiempo      : " + timeStr, bold },
                {
                    new Color(140, 150, 170),
                    " * SHIFT = sentido inverso",
                    norm,
                },
            };

            for (int i = 0; i < lines.length; i++) {
                g2.setFont((Font) lines[i][2]);
                g2.setColor((Color) lines[i][0]);
                g2.drawString((String) lines[i][1], 34, 46 + i * 17);
            }
        }
    }

    // =========================================================================
    //  CLASES DE SOPORTE 3D
    // =========================================================================

    /** Vector / punto en espacio 3D con rotaciones in-place. */
    class Point3D {

        double x, y, z;

        Point3D(double x, double y, double z) {
            this.x = x;
            this.y = y;
            this.z = z;
        }

        Point3D copy() {
            return new Point3D(x, y, z);
        }

        void rotateX(double a) {
            final double c = Math.cos(a),
                s = Math.sin(a),
                ny = y * c - z * s,
                nz = y * s + z * c;
            y = ny;
            z = nz;
        }

        void rotateY(double a) {
            final double c = Math.cos(a),
                s = Math.sin(a),
                nx = x * c + z * s,
                nz = -x * s + z * c;
            x = nx;
            z = nz;
        }

        void rotateZ(double a) {
            final double c = Math.cos(a),
                s = Math.sin(a),
                nx = x * c - y * s,
                ny = x * s + y * c;
            x = nx;
            y = ny;
        }

        void rotateAxis(int axis, double a) {
            if (axis == 0) rotateX(a);
            else if (axis == 1) rotateY(a);
            else rotateZ(a);
        }
    }

    /** Cara de un cubie: 4 vértices + color. */
    class Face {

        final Point3D[] vertices;
        Color color;

        Face(Point3D p1, Point3D p2, Point3D p3, Point3D p4, Color c) {
            vertices = new Point3D[] { p1, p2, p3, p4 };
            color = c;
        }
    }

    /**
     * Cara lista para dibujar en pantalla.
     * Almacena coordenadas 2D del quad exterior y del sticker interior.
     */
    class RenderFace implements Comparable<RenderFace> {

        final double[] x, y; // quad exterior (borde negro)
        final double[] sx, sy; // quad interior (sticker)
        final double z; // profundidad media para Painter's Algorithm
        final Color color; // color modulado por iluminación

        RenderFace(
            double[] x,
            double[] y,
            double[] sx,
            double[] sy,
            double z,
            Color c
        ) {
            this.x = x;
            this.y = y;
            this.sx = sx;
            this.sy = sy;
            this.z = z;
            this.color = c;
        }

        @Override
        public int compareTo(RenderFace o) {
            return Double.compare(this.z, o.z);
        }
    }

    /**
     * Pieza pequeña del cubo (cubie).
     * Mantiene posición lógica (x,y,z) ∈ {-1,0,1}³ y 6 caras con sus colores.
     */
    class Cubie {

        int x, y, z;
        final Face[] faces = new Face[6]; // 0:R 1:L 2:U 3:D 4:F 5:B

        Cubie(
            int x,
            int y,
            int z,
            Color cr,
            Color cl,
            Color cu,
            Color cd,
            Color cf,
            Color cb
        ) {
            this.x = x;
            this.y = y;
            this.z = z;
            final double s = 0.47; // semilado del cubie (gap de 0.03 entre cubies)

            final Point3D p0 = new Point3D(s, s, s),
                p1 = new Point3D(-s, s, s),
                p2 = new Point3D(-s, -s, s),
                p3 = new Point3D(s, -s, s),
                p4 = new Point3D(s, s, -s),
                p5 = new Point3D(-s, s, -s),
                p6 = new Point3D(-s, -s, -s),
                p7 = new Point3D(s, -s, -s);

            faces[0] = new Face(p0, p3, p7, p4, cr); // Derecha  +X
            faces[1] = new Face(p1, p5, p6, p2, cl); // Izquierda -X
            faces[2] = new Face(p0, p4, p5, p1, cu); // Arriba   +Y
            faces[3] = new Face(p3, p2, p6, p7, cd); // Abajo    -Y
            faces[4] = new Face(p0, p1, p2, p3, cf); // Frente   +Z
            faces[5] = new Face(p4, p7, p6, p5, cb); // Atrás    -Z
        }

        int getPos(int axis) {
            return axis == 0 ? x : axis == 1 ? y : z;
        }

        /**
         * Aplica la rotación lógica: mueve la posición del cubie y permuta los
         * colores de sus caras para reflejar el giro real del Rubik.
         *
         * @param axis  0=X, 1=Y, 2=Z
         * @param dir   +1 o -1 (sentido de giro, visto desde el eje positivo)
         */
        void rotateLogical(int axis, int dir) {
            final int ox = x,
                oy = y,
                oz = z;
            // Rotar coordenada de posición en el plano perpendicular al eje
            if (axis == 0) {
                y = -oz * dir;
                z = oy * dir;
            } else if (axis == 1) {
                x = oz * dir;
                z = -ox * dir;
            } else {
                x = -oy * dir;
                y = ox * dir;
            }

            // Permutación de colores entre caras adyacentes
            if (axis == 0) swapFaces(
                dir == 1 ? new int[] { 2, 5, 3, 4 } : new int[] { 2, 4, 3, 5 }
            );
            else if (axis == 1) swapFaces(
                dir == 1 ? new int[] { 0, 4, 1, 5 } : new int[] { 0, 5, 1, 4 }
            );
            else swapFaces(
                dir == 1 ? new int[] { 0, 3, 1, 2 } : new int[] { 0, 2, 1, 3 }
            );
        }

        /** Ciclo de permutación de 4 caras (rotación de colores en anillo). */
        private void swapFaces(int[] idx) {
            final Color tmp = faces[idx[0]].color;
            faces[idx[0]].color = faces[idx[1]].color;
            faces[idx[1]].color = faces[idx[2]].color;
            faces[idx[2]].color = faces[idx[3]].color;
            faces[idx[3]].color = tmp;
        }
    }
}
