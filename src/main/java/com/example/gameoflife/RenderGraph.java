package com.example.gameoflife;

import java.util.ArrayList;
import java.util.List;

public class RenderGraph {

    public enum PassType {
        INPUT(0),
        ANIMATION(1),
        LAYOUT(2),
        VISIBILITY(3),
        RENDER_QUEUE_BUILD(4),
        DRAW(5);

        final int order;
        PassType(int order) { this.order = order; }
    }

    public interface Pass {
        void execute(double dt);
    }

    private static class PassEntry {
        final PassType type;
        final Pass pass;
        PassEntry(PassType type, Pass pass) {
            this.type = type;
            this.pass = pass;
        }
    }

    private final List<PassEntry> passes = new ArrayList<>();
    private final Profiler profiler;

    public RenderGraph(Profiler profiler) {
        this.profiler = profiler;
    }

    public void addPass(PassType type, Pass pass) {
        passes.add(new PassEntry(type, pass));
        passes.sort((a, b) -> Integer.compare(a.type.order, b.type.order));
    }

    public void execute(double dt) {
        for (PassEntry pe : passes) {
            long start = System.nanoTime();
            pe.pass.execute(dt);
            profiler.recordPass(pe.type.name(), System.nanoTime() - start);
        }
    }
}
