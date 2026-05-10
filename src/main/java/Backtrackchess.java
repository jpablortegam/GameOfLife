import java.io.*;
import java.util.*;

/**
 * ╔══════════════════════════════════════════════════════════════╗
 * ║     Backtracking — Captura y Regreso (Ciclo Hamiltoniano)    ║
 * ╠══════════════════════════════════════════════════════════════╣
 * ║  Arquitectura V3: Bitboards, Playback Engine, File I/O, UI   ║
 * ╚══════════════════════════════════════════════════════════════╝
 */
public class Backtrackchess {

    static final int BOARD = 8;
    static final int MAX_ITER = 1_000_000;

    static final String PIECE_BLACK = "♞";
    static final String PIECE_WHITE = "♙";
    static final String PIECE_CAP = "✘";
    static final String PIECE_ORIG = "⌂";
    static final String ANSI_RESET = "\u001B[0m";
    static final String ANSI_CYAN = "\u001B[36m";
    static final String ANSI_GREEN = "\u001B[32m";
    static final String ANSI_RED = "\u001B[31m";
    static final String ANSI_YELLOW = "\u001B[33m";

    static final int[][] DIRS = {
        { -1, -1 },
        { -1, 0 },
        { -1, 1 },
        { 0, -1 },
        { 0, 1 },
        { 1, -1 },
        { 1, 0 },
        { 1, 1 },
    };

    // ─── Core del Dominio ──────────────────────────────────────────────────────
    static class Move {

        final int fromR, fromC, capR, capC, toR, toC;

        Move(int fr, int fc, int cr, int cc, int tr, int tc) {
            fromR = fr;
            fromC = fc;
            capR = cr;
            capC = cc;
            toR = tr;
            toC = tc;
        }
    }

    static class SolverResult {

        List<List<Move>> solutions = new ArrayList<>();
        List<Move> best = new ArrayList<>();
        int bestCount = 0;
        boolean hitLimit = false;
        int iters = 0;
    }

    static class Preset {

        final String name, desc;
        final int[] black;
        final int[][] whites;

        Preset(String name, String desc, int[] black, int[][] whites) {
            this.name = name;
            this.desc = desc;
            this.black = black;
            this.whites = whites;
        }
    }

    static final Preset[] PRESETS = {
        new Preset(
            "Zigzag-4",
            "4 blancas | Negro(0,0)",
            new int[] { 0, 0 },
            new int[][] { { 1, 0 }, { 1, 1 }, { 3, 0 }, { 3, 1 } }
        ),
        new Preset(
            "Rombo-4",
            "4 blancas | Negro(0,0)",
            new int[] { 0, 0 },
            new int[][] { { 0, 1 }, { 0, 3 }, { 1, 1 }, { 1, 3 } }
        ),
        new Preset(
            "Cuaderno",
            "6 blancas | Negro(0,3)",
            new int[] { 0, 3 },
            new int[][] {
                { 1, 2 },
                { 3, 1 },
                { 5, 2 },
                { 5, 4 },
                { 3, 5 },
                { 1, 4 },
            }
        ),
    };

    // ─── Motor de Resolución (Bitboards) ───────────────────────────────────────
    static class Solver {

        final int origR, origC;
        final int totalWhites;
        final SolverResult result = new SolverResult();
        final Move[] currentPath;
        int pathLen = 0;

        Solver(int[] black, int[][] whites) {
            this.origR = black[0];
            this.origC = black[1];
            this.totalWhites = whites.length;
            this.currentPath = new Move[whites.length + 1];
        }

        SolverResult run(int[][] whites) {
            long remainingMask = 0L;
            for (int[] w : whites) remainingMask |= (1L << (w[0] * 8 + w[1]));
            long visitedMask = (1L << (origR * 8 + origC));
            bt(origR, origC, visitedMask, remainingMask);
            return result;
        }

        void bt(int r, int c, long visited, long remaining) {
            if (result.hitLimit) return;
            if (++result.iters > MAX_ITER) {
                result.hitLimit = true;
                return;
            }

            int captured = totalWhites - Long.bitCount(remaining);
            if (captured > result.bestCount) {
                result.bestCount = captured;
                result.best = Arrays.asList(
                    Arrays.copyOf(currentPath, pathLen)
                );
            }

            for (int[] dir : DIRS) {
                int lr = r + 2 * dir[0],
                    lc = c + 2 * dir[1];
                int ar = r + dir[0],
                    ac = c + dir[1];
                if (
                    ar < 0 ||
                    ar >= BOARD ||
                    ac < 0 ||
                    ac >= BOARD ||
                    lr < 0 ||
                    lr >= BOARD ||
                    lc < 0 ||
                    lc >= BOARD
                ) continue;

                long adjMask = 1L << (ar * 8 + ac);
                long landMask = 1L << (lr * 8 + lc);
                if (
                    (remaining & adjMask) == 0 || (remaining & landMask) != 0
                ) continue;

                boolean isOrig = (lr == origR && lc == origC);
                if (isOrig && Long.bitCount(remaining) != 1) continue;
                if (!isOrig && (visited & landMask) != 0) continue;

                currentPath[pathLen++] = new Move(r, c, ar, ac, lr, lc);
                long newRem = remaining & ~adjMask;

                if (isOrig && newRem == 0) result.solutions.add(
                    Arrays.asList(Arrays.copyOf(currentPath, pathLen))
                );
                else bt(lr, lc, visited | landMask, newRem);

                pathLen--;
            }
        }
    }

    // ─── Creador de Mapas Interactivo ──────────────────────────────────────────
    static int[] parseCoord(String s) {
        if (s.length() < 2) return null;
        int c = s.charAt(0) - 'A';
        int r = s.charAt(1) - '0';
        if (c >= 0 && c < BOARD && r >= 0 && r < BOARD) return new int[] {
            r,
            c,
        };
        return null;
    }

    static Preset createMapInteractive(Scanner sc) {
        System.out.println(
            "\n" +
                ANSI_CYAN +
                "=== Creador de Mapas en Terminal ===" +
                ANSI_RESET
        );
        System.out.println(
            "Usa coordenadas combinando Columna (A-H) y Fila (0-7). Ejemplo: C2, A0, H7."
        );

        int[] black = null;
        while (black == null) {
            System.out.print(
                "Posición de la pieza Negra (" +
                    ANSI_GREEN +
                    PIECE_BLACK +
                    ANSI_RESET +
                    "): "
            );
            String in = sc.nextLine().trim().toUpperCase();
            black = parseCoord(in);
            if (black == null) System.out.println(
                ANSI_RED +
                    "  -> Coordenada inválida. Usa el formato A0 a H7." +
                    ANSI_RESET
            );
        }

        List<int[]> whites = new ArrayList<>();
        System.out.println(
            "\nIngresa posiciones de las Blancas (" +
                ANSI_YELLOW +
                PIECE_WHITE +
                ANSI_RESET +
                ")."
        );
        System.out.println(
            "Escribe 'FIN' o presiona " +
                ANSI_YELLOW +
                "[Enter]" +
                ANSI_RESET +
                " con la línea vacía para terminar."
        );

        while (true) {
            System.out.print("Blanca #" + (whites.size() + 1) + ": ");
            String in = sc.nextLine().trim().toUpperCase();
            if (in.isEmpty() || in.equals("FIN")) {
                if (whites.isEmpty()) {
                    System.out.println(
                        ANSI_RED +
                            "  -> ¡Necesitas colocar al menos 1 pieza blanca!" +
                            ANSI_RESET
                    );
                    continue;
                }
                break;
            }
            int[] w = parseCoord(in);
            if (w == null) {
                System.out.println(
                    ANSI_RED +
                        "  -> Coordenada inválida. Usa el formato A0 a H7." +
                        ANSI_RESET
                );
            } else if (w[0] == black[0] && w[1] == black[1]) {
                System.out.println(
                    ANSI_RED +
                        "  -> ¡La casilla " +
                        in +
                        " ya está ocupada por la Negra!" +
                        ANSI_RESET
                );
            } else {
                whites.add(w);
            }
        }

        System.out.print("\nNombre para este mapa: ");
        String name = sc.nextLine().trim();
        if (name.isEmpty()) name = "Mapa Personalizado";
        return new Preset(
            name,
            "Creado en terminal",
            black,
            whites.toArray(new int[0][])
        );
    }

    // ─── I/O de Archivos ───────────────────────────────────────────────────────
    static Preset loadMapFromFile(String filename) {
        List<int[]> whitesList = new ArrayList<>();
        int[] black = null;
        try (Scanner fileSc = new Scanner(new File(filename))) {
            int r = 0;
            while (fileSc.hasNextLine() && r < BOARD) {
                String line = fileSc
                    .nextLine()
                    .replaceAll("\\s+", "")
                    .toUpperCase();
                for (int c = 0; c < line.length() && c < BOARD; c++) {
                    char ch = line.charAt(c);
                    if (ch == 'X') whitesList.add(new int[] { r, c });
                    else if (ch == 'O') black = new int[] { r, c };
                }
                r++;
            }
        } catch (Exception e) {
            System.out.println(
                ANSI_RED + "Error leyendo: " + e.getMessage() + ANSI_RESET
            );
            return null;
        }
        if (black == null) {
            System.out.println(
                ANSI_RED +
                    "Error: Falta la pieza negra 'O' en el mapa." +
                    ANSI_RESET
            );
            return null;
        }
        return new Preset(
            filename,
            "Cargado desde TXT",
            black,
            whitesList.toArray(new int[0][])
        );
    }

    static void exportSolution(List<Move> sequence, String filename) {
        try (PrintWriter pw = new PrintWriter(new File(filename))) {
            pw.println("=== Solucion Backtrack Chess ===");
            for (int i = 0; i < sequence.size(); i++) {
                Move m = sequence.get(i);
                pw.printf(
                    "Paso %02d: %c%d -> Captura %c%d -> Aterriza %c%d%n",
                    i + 1,
                    'A' + m.fromC,
                    m.fromR,
                    'A' + m.capC,
                    m.capR,
                    'A' + m.toC,
                    m.toR
                );
            }
            System.out.println(
                ANSI_GREEN + "¡Ruta exportada a " + filename + "!" + ANSI_RESET
            );
        } catch (Exception e) {
            System.out.println(
                ANSI_RED + "Error al exportar: " + e.getMessage() + ANSI_RESET
            );
        }
    }

    static void runBenchmark() {
        System.out.println(
            "\nIniciando Benchmark (100 iteraciones por mapa)..."
        );
        for (Preset p : PRESETS) {
            long totalTime = 0;
            for (int i = 0; i < 100; i++) {
                long t0 = System.currentTimeMillis();
                new Solver(p.black, p.whites).run(p.whites);
                totalTime += (System.currentTimeMillis() - t0);
            }
            System.out.printf(
                "  %s -> Promedio: %.2f ms%n",
                p.name,
                (totalTime / 100.0)
            );
        }
    }

    // ─── Playback Engine ───────────────────────────────────────────────────────
    static class PlaybackEngine {

        final int[] startBlack;
        final int[][] startWhites;
        final List<Move> sequence;

        PlaybackEngine(int[] b, int[][] w, List<Move> seq) {
            startBlack = b;
            startWhites = w;
            sequence = seq;
        }

        void play(Scanner sc) {
            int step = 0;
            boolean auto = false;
            System.out.print("\033[2J\033[H");
            System.out.flush();
            while (step <= sequence.size()) {
                System.out.print("\033[H\033[J");
                renderBoard(step);
                renderControls(step, auto);
                System.out.flush();
                if (auto) {
                    if (step == sequence.size()) break;
                    try {
                        Thread.sleep(800);
                    } catch (Exception e) {}
                    step++;
                    continue;
                }
                if (!sc.hasNextLine()) break;
                String in = sc.nextLine().trim().toLowerCase();
                if (in.equals("q")) break;
                else if (in.equals("p") && step > 0) step--;
                else if (in.equals("a")) auto = true;
                else if (in.equals("n") || in.isEmpty()) {
                    if (step < sequence.size()) step++;
                }
            }
            System.out.println("\nReproducción finalizada.");
        }

        void renderBoard(int step) {
            long wMask = 0L;
            for (int[] w : startWhites) wMask |= (1L << (w[0] * 8 + w[1]));
            int curR = startBlack[0],
                curC = startBlack[1];
            int lastCapR = -1,
                lastCapC = -1;
            Map<Integer, Integer> trails = new HashMap<>();
            trails.put(curR * 8 + curC, 0);

            for (int i = 0; i < step; i++) {
                Move m = sequence.get(i);
                wMask &= ~(1L << (m.capR * 8 + m.capC));
                curR = m.toR;
                curC = m.toC;
                trails.put(curR * 8 + curC, i + 1);
                if (i == step - 1) {
                    lastCapR = m.capR;
                    lastCapC = m.capC;
                }
            }

            String info = String.format(
                "Paso: %02d / %02d  |  Capturadas: %02d",
                step,
                sequence.size(),
                step
            );
            int padding = (31 - info.length()) / 2;
            String inner =
                " ".repeat(Math.max(0, padding)) +
                info +
                " ".repeat(Math.max(0, 31 - info.length() - padding));

            System.out.println(
                ANSI_CYAN + "   ╔═══════════════════════════════╗" + ANSI_RESET
            );
            System.out.println(
                ANSI_CYAN +
                    "   ║" +
                    ANSI_RESET +
                    inner +
                    ANSI_CYAN +
                    "║" +
                    ANSI_RESET
            );
            System.out.println(
                ANSI_CYAN + "   ╚═══════════════════════════════╝" + ANSI_RESET
            );

            System.out.print("   ");
            for (int c = 0; c < BOARD; c++) System.out.printf(
                "  %c  ",
                'A' + c
            );
            System.out.println("\n  ╔" + "═══╦".repeat(BOARD - 1) + "═══╗");

            for (int r = 0; r < BOARD; r++) {
                System.out.print(r + " ║");
                for (int c = 0; c < BOARD; c++) {
                    int pos = r * 8 + c;
                    boolean isBlk = (curR == r && curC == c);
                    boolean isWht = (wMask & (1L << pos)) != 0;
                    boolean isCap = (lastCapR == r && lastCapC == c);
                    boolean isOrig =
                        (startBlack[0] == r && startBlack[1] == c) &&
                        step > 0 &&
                        !isBlk;
                    Integer tNum = trails.get(pos);

                    String cell = "   ";
                    if (isBlk) cell =
                        ANSI_GREEN + " " + PIECE_BLACK + " " + ANSI_RESET;
                    else if (isCap) cell =
                        ANSI_RED + " " + PIECE_CAP + " " + ANSI_RESET;
                    else if (isWht) cell =
                        ANSI_YELLOW + " " + PIECE_WHITE + " " + ANSI_RESET;
                    else if (isOrig) cell =
                        ANSI_CYAN + " " + PIECE_ORIG + " " + ANSI_RESET;
                    else if (tNum != null && tNum > 0) cell = String.format(
                        "\u001B[90m%2d \u001B[0m",
                        tNum
                    );

                    System.out.print(cell + "║");
                }
                System.out.println();
                if (r < BOARD - 1) System.out.println(
                    "  ╠" + "═══╬".repeat(BOARD - 1) + "═══╣"
                );
            }
            System.out.println("  ╚" + "═══╩".repeat(BOARD - 1) + "═══╝");
        }

        void renderControls(int step, boolean auto) {
            System.out.println(
                "\n" + ANSI_YELLOW + "  Controles:" + ANSI_RESET
            );
            if (auto) {
                System.out.println("  [ Modo Automático en ejecución... ]");
            } else {
                System.out.println(
                    "  [n]+Enter: Sig | [p]+Enter: Prev | [a]+Enter: Auto Play | [q]+Enter: Salir"
                );
                System.out.print("  Acción: ");
            }
        }
    }

    // ─── Entry Point ───────────────────────────────────────────────────────────
    public static void main(String[] args) {
        try {
            System.setOut(new PrintStream(System.out, true, "UTF-8"));
        } catch (UnsupportedEncodingException e) {
            /* Ignorar */
        }

        Scanner sc = new Scanner(System.in);
        while (true) {
            System.out.println("\n" + "=".repeat(50));
            System.out.println("  Motor Backtracking - Ciclo Hamiltoniano V3");
            System.out.println("=".repeat(50));
            System.out.println("── Mapas y Herramientas ──");
            for (int i = 0; i < PRESETS.length; i++) System.out.printf(
                "  [%d] %s%n",
                i + 1,
                PRESETS[i].name
            );
            System.out.println(
                "  [T] Crear mapa interactivo " +
                    ANSI_GREEN +
                    "(NUEVO)" +
                    ANSI_RESET
            );
            System.out.println("  [C] Cargar mapa desde archivo .txt");
            System.out.println("  [B] Benchmark (Prueba de rendimiento)");
            System.out.println("  [0] Salir");
            System.out.print("\nSelección: ");

            if (!sc.hasNextLine()) break;
            String input = sc.nextLine().trim().toUpperCase();
            if (input.equals("0")) break;

            if (input.equals("B")) {
                runBenchmark();
                continue;
            }

            Preset p = null;
            if (input.equals("T")) {
                p = createMapInteractive(sc);
            } else if (input.equals("C")) {
                System.out.print("Ruta del archivo (ej. mapa.txt): ");
                if (!sc.hasNextLine()) break;
                p = loadMapFromFile(sc.nextLine().trim());
                if (p == null) continue;
            } else {
                try {
                    int opt = Integer.parseInt(input);
                    if (opt >= 1 && opt <= PRESETS.length) p = PRESETS[opt - 1];
                } catch (Exception e) {
                    /* Ignorar */
                }
            }

            if (p == null) {
                System.out.println("Opción inválida.");
                continue;
            }

            System.out.printf("%nResolviendo '%s'...%n", p.name);
            long t0 = System.currentTimeMillis();
            Solver solver = new Solver(p.black, p.whites);
            SolverResult res = solver.run(p.whites);
            long elapsed = System.currentTimeMillis() - t0;
            System.out.printf(
                "Terminado en %d ms | %,d iteraciones exploradas%n",
                elapsed,
                res.iters
            );

            if (!res.solutions.isEmpty()) {
                System.out.printf(
                    "¡Éxito! %d solución(es) encontrada(s).%n",
                    res.solutions.size()
                );
                System.out.print("¿Exportar solución a TXT? (s/n): ");
                if (
                    sc.hasNextLine() &&
                    sc.nextLine().trim().equalsIgnoreCase("s")
                ) {
                    System.out.print("Nombre del archivo: ");
                    exportSolution(res.solutions.get(0), sc.nextLine().trim());
                }
                System.out.print(
                    "Presiona Enter para iniciar el Playback Engine..."
                );
                if (sc.hasNextLine()) sc.nextLine();
                new PlaybackEngine(
                    p.black,
                    p.whites,
                    res.solutions.get(0)
                ).play(sc);
            } else {
                System.out.println(
                    ANSI_RED + "No se encontró solución exacta." + ANSI_RESET
                );
                if (res.bestCount > 0) {
                    System.out.print("¿Ver mejor ruta parcial? (s/n): ");
                    if (
                        sc.hasNextLine() &&
                        sc.nextLine().trim().equalsIgnoreCase("s")
                    ) new PlaybackEngine(p.black, p.whites, res.best).play(sc);
                }
            }
        }
        sc.close();
    }
}
