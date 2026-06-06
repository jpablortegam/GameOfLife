package com.example.gameoflife;

import java.util.ArrayList;
import java.util.List;

public class SpatialHashGrid {
    private static final int INITIAL_CELL_CAPACITY = 8;

    private final double cellSize;
    private final int cols;
    private final int rows;
    private final int[][] cells;
    private final int[] counts;

    public SpatialHashGrid(double worldW, double worldH, double cellSize) {
        this.cellSize = cellSize;
        this.cols = (int) Math.ceil(worldW / cellSize) + 1;
        this.rows = (int) Math.ceil(worldH / cellSize) + 1;
        this.cells = new int[cols * rows][];
        this.counts = new int[cols * rows];
        for (int i = 0; i < cells.length; i++) {
            cells[i] = new int[INITIAL_CELL_CAPACITY];
        }
    }

    public void insert(int entity, double x, double y, double w, double h) {
        int minC = clampCol(x);
        int minR = clampRow(y);
        int maxC = clampCol(x + w);
        int maxR = clampRow(y + h);

        for (int r = minR; r <= maxR; r++) {
            for (int c = minC; c <= maxC; c++) {
                addToCell(cellKey(c, r), entity);
            }
        }
    }

    public void clear() {
        for (int i = 0; i < counts.length; i++) {
            counts[i] = 0;
        }
    }

    public int queryPoint(double px, double py, int[] out) {
        int key = cellKey(clampCol(px), clampRow(py));
        int n = Math.min(counts[key], out.length);
        System.arraycopy(cells[key], 0, out, 0, n);
        return n;
    }

    public List<Integer> queryRect(double x, double y, double w, double h) {
        int minC = clampCol(x);
        int minR = clampRow(y);
        int maxC = clampCol(x + w);
        int maxR = clampRow(y + h);
        ArrayList<Integer> result = new ArrayList<>();

        for (int r = minR; r <= maxR; r++) {
            for (int c = minC; c <= maxC; c++) {
                int key = cellKey(c, r);
                for (int i = 0; i < counts[key]; i++) {
                    int entity = cells[key][i];
                    if (!result.contains(entity)) {
                        result.add(entity);
                    }
                }
            }
        }
        return result;
    }

    private void addToCell(int key, int entity) {
        int count = counts[key];
        int[] cell = cells[key];
        if (count == cell.length) {
            int[] grown = new int[cell.length * 2];
            System.arraycopy(cell, 0, grown, 0, cell.length);
            cells[key] = grown;
            cell = grown;
        }
        cell[count] = entity;
        counts[key] = count + 1;
    }

    private int cellKey(int col, int row) {
        return row * cols + col;
    }

    private int clampCol(double x) {
        return Math.max(0, Math.min(cols - 1, (int) Math.floor(x / cellSize)));
    }

    private int clampRow(double y) {
        return Math.max(0, Math.min(rows - 1, (int) Math.floor(y / cellSize)));
    }
}
