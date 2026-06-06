package com.example.gameoflife;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.function.DoubleConsumer;

public class AnimationEngine {

    public enum Easing {
        SMOOTHSTEP,
        EASE_OUT_QUINT,
        EASE_OUT_EXPO,
        EASE_OUT_SINE,
        LINEAR
    }

    public static abstract class AnimationTrack {
        protected double elapsed = 0;
        protected double duration;
        protected Easing easing = Easing.SMOOTHSTEP;
        protected boolean finished;

        public AnimationTrack(double duration) {
            this.duration = duration;
        }

        public AnimationTrack easing(Easing e) { this.easing = e; return this; }
        public AnimationTrack duration(double d) { this.duration = d; return this; }

        public boolean isFinished() { return finished; }

        public void update(double dt) {
            if (finished) return;
            elapsed += dt;
            double t = Math.min(1.0, elapsed / duration);
            double eased = applyEasing(t);
            apply(eased);
            if (t >= 1.0) {
                apply(1.0);
                finished = true;
                onFinish();
            }
        }

        protected abstract void apply(double t);
        protected void onFinish() {}

        private double applyEasing(double t) {
            switch (easing) {
                case EASE_OUT_QUINT:
                    t = Math.max(0, Math.min(1, t));
                    return 1.0 - Math.pow(1.0 - t, 5);
                case EASE_OUT_EXPO:
                    t = Math.max(0, Math.min(1, t));
                    return t >= 1.0 ? 1.0 : 1.0 - Math.pow(2.0, -10.0 * t);
                case EASE_OUT_SINE:
                    return Math.sin((t * Math.PI) / 2.0);
                case LINEAR:
                    return t;
                default:
                    t = Math.max(0, Math.min(1, t));
                    return t * t * (3.0 - 2.0 * t);
            }
        }
    }

    private static class FloatTrack extends AnimationTrack {
        private final DoubleConsumer setter;
        private final double from, to;

        FloatTrack(double duration, double from, double to, DoubleConsumer setter) {
            super(duration);
            this.from = from;
            this.to = to;
            this.setter = setter;
        }

        @Override
        protected void apply(double t) {
            setter.accept(from + (to - from) * t);
        }
    }

    private static class DelayedTrack extends AnimationTrack {
        private final AnimationTrack inner;
        private double delay;

        DelayedTrack(double delay, AnimationTrack inner) {
            super(inner.duration);
            this.delay = delay;
            this.inner = inner;
        }

        @Override
        public void update(double dt) {
            if (finished) return;
            if (delay > 0) {
                delay -= dt;
                if (delay > 0) return;
                dt = -delay;
                delay = 0;
            }
            inner.update(dt);
            finished = inner.isFinished();
        }

        @Override
        protected void apply(double t) {}
    }

    private static class ParallelTrack extends AnimationTrack {
        private final List<AnimationTrack> tracks;

        ParallelTrack(AnimationTrack... tracks) {
            super(0);
            this.tracks = new ArrayList<>();
            for (AnimationTrack t : tracks) this.tracks.add(t);
            double maxDur = 0;
            for (AnimationTrack t : tracks) maxDur = Math.max(maxDur, t.duration);
            this.duration = maxDur;
        }

        @Override
        public void update(double dt) {
            if (finished) return;
            boolean allDone = true;
            for (AnimationTrack t : tracks) {
                t.update(dt);
                if (!t.isFinished()) allDone = false;
            }
            finished = allDone;
        }

        @Override
        protected void apply(double t) {}
    }

    private static class SequenceTrack extends AnimationTrack {
        private final List<AnimationTrack> tracks;
        private int index = 0;

        SequenceTrack(AnimationTrack... tracks) {
            super(0);
            this.tracks = new ArrayList<>();
            for (AnimationTrack t : tracks) this.tracks.add(t);
            double totalDur = 0;
            for (AnimationTrack t : tracks) totalDur += t.duration;
            this.duration = totalDur;
        }

        @Override
        public void update(double dt) {
            if (finished) return;
            while (index < tracks.size()) {
                AnimationTrack cur = tracks.get(index);
                cur.update(dt);
                if (!cur.isFinished()) break;
                index++;
            }
            if (index >= tracks.size()) finished = true;
        }

        @Override
        protected void apply(double t) {}
    }

    private final List<AnimationTrack> active = new ArrayList<>();
    private final List<Runnable> onCompleteHandlers = new ArrayList<>();

    public AnimationTrack animateFloat(double from, double to, double duration, DoubleConsumer setter) {
        FloatTrack t = new FloatTrack(duration, from, to, setter);
        active.add(t);
        return t;
    }

    public AnimationTrack animateFloat(double from, double to, double duration, double delay, DoubleConsumer setter) {
        FloatTrack inner = new FloatTrack(duration, from, to, setter);
        DelayedTrack t = new DelayedTrack(delay, inner);
        active.add(t);
        return t;
    }

    public AnimationTrack parallel(AnimationTrack... tracks) {
        ParallelTrack t = new ParallelTrack(tracks);
        active.add(t);
        return t;
    }

    public AnimationTrack sequence(AnimationTrack... tracks) {
        SequenceTrack t = new SequenceTrack(tracks);
        active.add(t);
        return t;
    }

    public void update(double dt) {
        Iterator<AnimationTrack> it = active.iterator();
        while (it.hasNext()) {
            AnimationTrack t = it.next();
            t.update(dt);
            if (t.isFinished()) {
                it.remove();
            }
        }
    }

    public int activeCount() { return active.size(); }
    public boolean isAnimating() { return !active.isEmpty(); }
    public void clear() { active.clear(); }
}
