package com.example.gameoflife;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.image.Image;
import javafx.scene.paint.Color;
import javafx.scene.paint.Paint;
import javafx.scene.text.Font;

public class RenderQueue {

    public enum BlendMode { NORMAL, MULTIPLY, SCREEN }

    public static abstract class RenderCommand {
        public int zOrder = 0;
        public int layer = 0;
        public abstract void execute(GraphicsContext gc);
        void resetBase() {
            zOrder = 0;
            layer = 0;
        }
    }

    public static class DrawImageCommand extends RenderCommand {
        Image image;
        double sx, sy, sw, sh;
        double dx, dy, dw, dh;
        double opacity = 1.0;
        boolean cover = false;

        public DrawImageCommand() {
        }

        public DrawImageCommand(Image image, double dx, double dy, double dw, double dh) {
            reset(image, dx, dy, dw, dh);
        }

        DrawImageCommand reset(Image image, double dx, double dy, double dw, double dh) {
            resetBase();
            this.image = image;
            if (image != null) {
                this.sx = 0; this.sy = 0;
                this.sw = image.getWidth(); this.sh = image.getHeight();
            } else {
                this.sx = 0; this.sy = 0;
                this.sw = 0; this.sh = 0;
            }
            this.dx = dx; this.dy = dy; this.dw = dw; this.dh = dh;
            this.opacity = 1.0;
            this.cover = false;
            return this;
        }

        public DrawImageCommand cover(boolean c) { this.cover = c; return this; }
        public DrawImageCommand opacity(double o) { this.opacity = o; return this; }
        public DrawImageCommand z(int z) { this.zOrder = z; return this; }
        public DrawImageCommand layer(int l) { this.layer = l; return this; }

        @Override
        public void execute(GraphicsContext gc) {
            if (image == null) return;
            double iw = image.getWidth(), ih = image.getHeight();
            if (iw == 0 || ih == 0) return;

            double gx = dx, gy = dy, gw = dw, gh = dh;

            if (opacity < 1.0) {
                gc.setGlobalAlpha(opacity);
            }

            if (cover) {
                double imgRatio = iw / ih;
                double canvasRatio = dw / dh;
                double sx2, sy2, sw2, sh2;
                if (imgRatio > canvasRatio) {
                    sh2 = ih;
                    sw2 = ih * canvasRatio;
                    sx2 = (iw - sw2) / 2.0;
                    sy2 = 0;
                } else {
                    sw2 = iw;
                    sh2 = iw / canvasRatio;
                    sx2 = 0;
                    sy2 = (ih - sh2) / 2.0;
                }
                gc.drawImage(image, sx2, sy2, sw2, sh2, gx, gy, gw, gh);
            } else {
                gc.drawImage(image, sx, sy, sw, sh, gx, gy, gw, gh);
            }

            if (opacity < 1.0) {
                gc.setGlobalAlpha(1.0);
            }
        }
    }

    public static class FillRectCommand extends RenderCommand {
        double x, y, w, h;
        Paint fill;
        double radius = 0;
        double opacity = 1.0;

        public FillRectCommand() {
        }

        public FillRectCommand(double x, double y, double w, double h, Paint fill) {
            reset(x, y, w, h, fill);
        }

        FillRectCommand reset(double x, double y, double w, double h, Paint fill) {
            resetBase();
            this.x = x; this.y = y; this.w = w; this.h = h; this.fill = fill;
            this.radius = 0;
            this.opacity = 1.0;
            return this;
        }

        public FillRectCommand radius(double r) { this.radius = r; return this; }
        public FillRectCommand opacity(double o) { this.opacity = o; return this; }
        public FillRectCommand z(int z) { this.zOrder = z; return this; }
        public FillRectCommand layer(int l) { this.layer = l; return this; }

        @Override
        public void execute(GraphicsContext gc) {
            if (opacity < 1.0) gc.setGlobalAlpha(opacity);
            gc.setFill(fill);
            if (radius > 0) {
                gc.fillRoundRect(x, y, w, h, radius * 2, radius * 2);
            } else {
                gc.fillRect(x, y, w, h);
            }
            if (opacity < 1.0) gc.setGlobalAlpha(1.0);
        }
    }

    public static class DrawTextCommand extends RenderCommand {
        String text;
        double x, y;
        Paint fill;
        Font font;
        double opacity = 1.0;

        public DrawTextCommand() {
        }

        public DrawTextCommand(String text, double x, double y, Font font, Paint fill) {
            reset(text, x, y, font, fill);
        }

        DrawTextCommand reset(String text, double x, double y, Font font, Paint fill) {
            resetBase();
            this.text = text; this.x = x; this.y = y; this.font = font; this.fill = fill;
            this.opacity = 1.0;
            return this;
        }

        public DrawTextCommand opacity(double o) { this.opacity = o; return this; }
        public DrawTextCommand z(int z) { this.zOrder = z; return this; }
        public DrawTextCommand layer(int l) { this.layer = l; return this; }

        @Override
        public void execute(GraphicsContext gc) {
            if (opacity < 1.0) gc.setGlobalAlpha(opacity);
            gc.setFont(font);
            gc.setFill(fill);
            gc.fillText(text, x, y);
            if (opacity < 1.0) gc.setGlobalAlpha(1.0);
        }
    }

    public static class RoundRectClipCommand extends RenderCommand {
        double x, y, w, h, r;

        public RoundRectClipCommand(double x, double y, double w, double h, double r) {
            this.x = x; this.y = y; this.w = w; this.h = h; this.r = r;
        }

        public RoundRectClipCommand z(int z) { this.zOrder = z; return this; }
        public RoundRectClipCommand layer(int l) { this.layer = l; return this; }

        @Override
        public void execute(GraphicsContext gc) {
            double rr = Math.min(r, Math.min(w, h) * 0.5);
            gc.beginPath();
            gc.moveTo(x + rr, y);
            gc.lineTo(x + w - rr, y);
            gc.quadraticCurveTo(x + w, y, x + w, y + rr);
            gc.lineTo(x + w, y + h - rr);
            gc.quadraticCurveTo(x + w, y + h, x + w - rr, y + h);
            gc.lineTo(x + rr, y + h);
            gc.quadraticCurveTo(x, y + h, x, y + h - rr);
            gc.lineTo(x, y + rr);
            gc.quadraticCurveTo(x, y, x + rr, y);
            gc.closePath();
            gc.clip();
        }
    }

    public static class StrokeRoundRectCommand extends RenderCommand {
        double x, y, w, h, r;
        Paint stroke;
        double lineWidth = 1.5;
        double opacity = 1.0;

        public StrokeRoundRectCommand() {
        }

        public StrokeRoundRectCommand(double x, double y, double w, double h, double r, Paint stroke) {
            reset(x, y, w, h, r, stroke);
        }

        StrokeRoundRectCommand reset(double x, double y, double w, double h, double r, Paint stroke) {
            resetBase();
            this.x = x; this.y = y; this.w = w; this.h = h; this.r = r; this.stroke = stroke;
            this.lineWidth = 1.5;
            this.opacity = 1.0;
            return this;
        }

        public StrokeRoundRectCommand lineWidth(double lw) { this.lineWidth = lw; return this; }
        public StrokeRoundRectCommand opacity(double o) { this.opacity = o; return this; }
        public StrokeRoundRectCommand z(int z) { this.zOrder = z; return this; }
        public StrokeRoundRectCommand layer(int l) { this.layer = l; return this; }

        @Override
        public void execute(GraphicsContext gc) {
            if (opacity < 1.0) gc.setGlobalAlpha(opacity);
            gc.setLineWidth(lineWidth);
            gc.setStroke(stroke);
            gc.strokeRoundRect(x, y, w, h, r * 2, r * 2);
            if (opacity < 1.0) gc.setGlobalAlpha(1.0);
        }
    }

    public static class SetEffectCommand extends RenderCommand {
        javafx.scene.effect.Effect effect;

        public SetEffectCommand(javafx.scene.effect.Effect effect) { this.effect = effect; }

        public SetEffectCommand z(int z) { this.zOrder = z; return this; }
        public SetEffectCommand layer(int l) { this.layer = l; return this; }

        @Override
        public void execute(GraphicsContext gc) {
            gc.setEffect(effect);
        }
    }

    public static class SaveCommand extends RenderCommand {
        public SaveCommand z(int z) { this.zOrder = z; return this; }
        public SaveCommand layer(int l) { this.layer = l; return this; }

        @Override
        public void execute(GraphicsContext gc) { gc.save(); }
    }

    public static class RestoreCommand extends RenderCommand {
        public RestoreCommand z(int z) { this.zOrder = z; return this; }
        public RestoreCommand layer(int l) { this.layer = l; return this; }

        @Override
        public void execute(GraphicsContext gc) { gc.restore(); }
    }

    public static class ClearRectCommand extends RenderCommand {
        double x, y, w, h;
        Paint fill;

        public ClearRectCommand() {
        }

        public ClearRectCommand(double x, double y, double w, double h, Paint fill) {
            reset(x, y, w, h, fill);
        }

        ClearRectCommand reset(double x, double y, double w, double h, Paint fill) {
            resetBase();
            this.x = x; this.y = y; this.w = w; this.h = h; this.fill = fill;
            return this;
        }

        public ClearRectCommand z(int z) { this.zOrder = z; return this; }
        public ClearRectCommand layer(int l) { this.layer = l; return this; }

        @Override
        public void execute(GraphicsContext gc) {
            gc.setFill(fill);
            gc.fillRect(x, y, w, h);
        }
    }

    private final List<RenderCommand> commands = new ArrayList<>();
    private final List<DrawImageCommand> imagePool = new ArrayList<>();
    private final List<FillRectCommand> fillPool = new ArrayList<>();
    private final List<DrawTextCommand> textPool = new ArrayList<>();
    private final List<StrokeRoundRectCommand> strokePool = new ArrayList<>();
    private final List<ClearRectCommand> clearPool = new ArrayList<>();
    private int drawCalls = 0;

    public void add(RenderCommand cmd) {
        commands.add(cmd);
    }

    public DrawImageCommand image(Image image, double dx, double dy, double dw, double dh) {
        DrawImageCommand cmd = imagePool.isEmpty()
            ? new DrawImageCommand()
            : imagePool.remove(imagePool.size() - 1);
        add(cmd.reset(image, dx, dy, dw, dh));
        return cmd;
    }

    public FillRectCommand fill(double x, double y, double w, double h, Paint fill) {
        FillRectCommand cmd = fillPool.isEmpty()
            ? new FillRectCommand()
            : fillPool.remove(fillPool.size() - 1);
        add(cmd.reset(x, y, w, h, fill));
        return cmd;
    }

    public DrawTextCommand text(String text, double x, double y, Font font, Paint fill) {
        DrawTextCommand cmd = textPool.isEmpty()
            ? new DrawTextCommand()
            : textPool.remove(textPool.size() - 1);
        add(cmd.reset(text, x, y, font, fill));
        return cmd;
    }

    public StrokeRoundRectCommand strokeRound(double x, double y, double w, double h, double r, Paint stroke) {
        StrokeRoundRectCommand cmd = strokePool.isEmpty()
            ? new StrokeRoundRectCommand()
            : strokePool.remove(strokePool.size() - 1);
        add(cmd.reset(x, y, w, h, r, stroke));
        return cmd;
    }

    public ClearRectCommand clearRect(double x, double y, double w, double h, Paint fill) {
        ClearRectCommand cmd = clearPool.isEmpty()
            ? new ClearRectCommand()
            : clearPool.remove(clearPool.size() - 1);
        add(cmd.reset(x, y, w, h, fill));
        return cmd;
    }

    public void clear() {
        releaseCommands();
        commands.clear();
        drawCalls = 0;
    }

    public int size() { return commands.size(); }

    public int getDrawCalls() { return drawCalls; }

    public void execute(GraphicsContext gc) {
        drawCalls = 0;
        commands.sort(Comparator
            .comparingInt((RenderCommand c) -> c.layer)
            .thenComparingInt(c -> c.zOrder));
        for (RenderCommand cmd : commands) {
            cmd.execute(gc);
            drawCalls++;
        }
    }

    private void releaseCommands() {
        for (RenderCommand cmd : commands) {
            if (cmd instanceof DrawImageCommand c) {
                c.image = null;
                imagePool.add(c);
            } else if (cmd instanceof FillRectCommand c) {
                c.fill = null;
                fillPool.add(c);
            } else if (cmd instanceof DrawTextCommand c) {
                c.text = null;
                c.font = null;
                c.fill = null;
                textPool.add(c);
            } else if (cmd instanceof StrokeRoundRectCommand c) {
                c.stroke = null;
                strokePool.add(c);
            } else if (cmd instanceof ClearRectCommand c) {
                c.fill = null;
                clearPool.add(c);
            }
        }
    }
}
