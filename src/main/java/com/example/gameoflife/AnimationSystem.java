package com.example.gameoflife;

public class AnimationSystem {

    // ─── Easing Functions ───────────────────────────────────────────────

    public static double smoothstep(double t) {
        t = Math.max(0.0, Math.min(1.0, t));
        return t * t * (3.0 - 2.0 * t);
    }

    public static double easeOutQuint(double t) {
        t = Math.max(0.0, Math.min(1.0, t));
        return 1.0 - Math.pow(1.0 - t, 5);
    }

    public static double easeOutExpo(double t) {
        t = Math.max(0.0, Math.min(1.0, t));
        return t >= 1.0 ? 1.0 : 1.0 - Math.pow(2.0, -10.0 * t);
    }

    public static double easeOutSine(double t) {
        return Math.sin((t * Math.PI) / 2.0);
    }

    // ─── Spring-Damper ─────────────────────────────────────────────────

    public static class SpringValue {
        private double position;
        private double velocity;
        private double target;
        private double stiffness = 180.0;
        private double damping = 18.0;

        public SpringValue(double initial) {
            this.position = initial;
            this.target = initial;
            this.velocity = 0;
        }

        public void setTarget(double target) { this.target = target; }
        public double getTarget() { return target; }
        public double getPosition() { return position; }
        public double getVelocity() { return velocity; }

        public void setStiffness(double s) { this.stiffness = s; }
        public void setDamping(double d) { this.damping = d; }

        public boolean isAtRest() {
            return Math.abs(position - target) < 0.001
                && Math.abs(velocity) < 0.001;
        }

        public void snapToTarget() {
            position = target;
            velocity = 0;
        }

        public void update(double dt) {
            double force = stiffness * (target - position) - damping * velocity;
            velocity += force * dt;
            position += velocity * dt;
        }
    }

    // ─── Time-based Tween ──────────────────────────────────────────────

    public enum Easing {
        SMOOTHSTEP,
        EASE_OUT_QUINT,
        EASE_OUT_EXPO,
        EASE_OUT_SINE,
        LINEAR
    }

    public static class Tween {
        private double elapsed = 0;
        private double duration = 0.4;
        private double from = 0;
        private double to = 1;
        private Easing easing = Easing.SMOOTHSTEP;

        public Tween(double duration, double from, double to) {
            this.duration = duration;
            this.from = from;
            this.to = to;
        }

        public Tween() {}

        public void reset() { elapsed = 0; }
        public void reset(double from, double to) {
            this.from = from;
            this.to = to;
            elapsed = 0;
        }
        public void setFrom(double from) { this.from = from; }
        public void setTo(double to) { this.to = to; }

        public void setEasing(Easing e) { this.easing = e; }
        public Easing getEasing() { return easing; }

        public double getDuration() { return duration; }
        public void setDuration(double d) { this.duration = d; }

        public double getProgress() {
            return Math.min(1.0, elapsed / duration);
        }

        public double getValue() {
            double t = getProgress();
            double eased;
            switch (easing) {
                case EASE_OUT_QUINT:  eased = easeOutQuint(t);  break;
                case EASE_OUT_EXPO:   eased = easeOutExpo(t);   break;
                case EASE_OUT_SINE:   eased = easeOutSine(t);   break;
                case LINEAR:          eased = t;                break;
                default:              eased = smoothstep(t);    break;
            }
            return from + (to - from) * eased;
        }

        public boolean isFinished() { return elapsed >= duration; }

        public void update(double dt) {
            elapsed = Math.min(elapsed + dt, duration);
        }
    }
}
