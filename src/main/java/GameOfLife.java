import java.io.PrintStream;
import java.util.Scanner;

public class GameOfLife {

    static final int ROWS = 10;
    static final int COLS = 20;

    // ── Toroidal wrapping with conditionals ──────────────────────────────────

    static int wrapRow(int row) {
        if (row < 0) return ROWS - 1;
        if (row >= ROWS) return 0;
        return row;
    }

    static int wrapCol(int col) {
        if (col < 0) return COLS - 1;
        if (col >= COLS) return 0;
        return col;
    }

    // ── Count live neighbors of cell (row, col) ───────────────────────────────

    static int countNeighbors(int[][] grid, int row, int col) {
        int count = 0;
        for (int dr = -1; dr <= 1; dr++) {
            for (int dc = -1; dc <= 1; dc++) {
                if (dr == 0 && dc == 0) continue;
                count += grid[wrapRow(row + dr)][wrapCol(col + dc)];
            }
        }
        return count;
    }

    // ── Compute next generation ───────────────────────────────────────────────

    static int[][] nextGeneration(int[][] grid) {
        int[][] next = new int[ROWS][COLS];
        for (int r = 0; r < ROWS; r++) {
            for (int c = 0; c < COLS; c++) {
                int neighbors = countNeighbors(grid, r, c);
                boolean alive = grid[r][c] == 1;

                if (alive && (neighbors == 2 || neighbors == 3)) next[r][c] = 1;
                else if (!alive && neighbors == 3) next[r][c] = 1;
                else next[r][c] = 0;
            }
        }
        return next;
    }

    // ── Render ────────────────────────────────────────────────────────────────

    static void print(int[][] grid, int gen) {
        // Move cursor to top-left after first frame so it animates in place
        if (gen > 0) System.out.print("\033[" + (ROWS + 3) + "A");

        System.out.println("  Generacion: " + gen + "  (Ctrl+C para salir)");
        System.out.println("  +" + "-".repeat(COLS * 2) + "+");
        for (int r = 0; r < ROWS; r++) {
            System.out.print("  |");
            for (int c = 0; c < COLS; c++) {
                System.out.print(grid[r][c] == 1 ? "O " : ". ");
            }
            System.out.println("|");
        }
        System.out.println("  +" + "-".repeat(COLS * 2) + "+");
    }

    // ── Seed patterns ─────────────────────────────────────────────────────────

    static int[][] glider() {
        int[][] g = new int[ROWS][COLS];
        // Patron glider clasico (viaja en diagonal)
        g[0][1] = 1;
        g[1][2] = 1;
        g[2][0] = 1;
        g[2][1] = 1;
        g[2][2] = 1;
        return g;
    }

    static int[][] blinker() {
        int[][] g = new int[ROWS][COLS];
        // Oscilador de periodo 2
        int mid = ROWS / 2;
        g[mid][COLS / 2 - 1] = 1;
        g[mid][COLS / 2] = 1;
        g[mid][COLS / 2 + 1] = 1;
        return g;
    }

    static int[][] random() {
        int[][] g = new int[ROWS][COLS];
        for (int r = 0; r < ROWS; r++) for (int c = 0; c < COLS; c++) g[r][c] =
            Math.random() < 0.3 ? 1 : 0;
        return g;
    }

    // ── Main ──────────────────────────────────────────────────────────────────

    public static void main(String[] args) throws Exception {
        System.setOut(new PrintStream(System.out, true, "UTF-8"));

        Scanner sc = new Scanner(System.in);

        System.out.println("\n  === JUEGO DE LA VIDA (Tablero Toroidal) ===");
        System.out.println("  Seleccione un patron inicial:");
        System.out.println("  1. Glider");
        System.out.println("  2. Blinker");
        System.out.println("  3. Aleatorio");
        System.out.print("  Opcion: ");

        int opcion = sc.hasNextInt() ? sc.nextInt() : 3;
        int[][] grid = switch (opcion) {
            case 1 -> glider();
            case 2 -> blinker();
            default -> random();
        };

        System.out.println();
        for (int gen = 0; ; gen++) {
            print(grid, gen);
            Thread.sleep(200);
            grid = nextGeneration(grid);
        }
    }
}
