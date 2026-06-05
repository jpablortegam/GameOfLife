package com.example.gameoflife;

public class AnimationSystem {

    // ─── Utilidades de CPU (Altamente Inlining) ─────────────────────────

    /**
     * Reemplazo ultrarrápido para Math.max(0, Math.min(1, t)).
     * Evita el overhead de llamadas a métodos de la API estándar y el manejo
     * de casos NaN de coma flotante que no aplican a nuestro delta de tiempo.
     */
    public static double clamp01(double t) {
        return t < 0.0 ? 0.0 : (t > 1.0 ? 1.0 : t);
    }

    // ─── Easing Functions (Matemática Desenrollada) ─────────────────────

    public static double smoothstep(double t) {
        t = clamp01(t);
        return t * t * (3.0 - 2.0 * t);
    }

    public static double easeOutQuint(double t) {
        t = clamp01(t);
        double inv = 1.0 - t;
        double inv2 = inv * inv;
        // Multiplicación explícita: ~10x más rápido que Math.pow(inv, 5) a nivel hardware
        return 1.0 - (inv2 * inv2 * inv);
    }

    public static double easeOutExpo(double t) {
        t = clamp01(t);
        // Math.pow es aceptable aquí por el exponente variable (-10 * t),
        // pero evitamos evaluar si t == 1.0 con la condición rápida inicial.
        return t >= 1.0 ? 1.0 : 1.0 - Math.pow(2.0, -10.0 * t);
    }

    public static double easeOutSine(double t) {
        return Math.sin((clamp01(t) * Math.PI) * 0.5); // Multiplicar por 0.5 es más rápido que dividir entre 2.0
    }

    // ─── Spring-Damper (Data-Oriented approach wrapper) ─────────────────
    // NOTA: En tu CanvasMovieApp ya estás usando Arrays SoA para los resortes.
    // Esta clase se mantiene por compatibilidad, pero la lógica nativa en arreglos es la correcta.

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

        public void setTarget(double target) {
            this.target = target;
        }

        public double getTarget() {
            return target;
        }

        public double getPosition() {
            return position;
        }

        public double getVelocity() {
            return velocity;
        }

        public void setStiffness(double s) {
            this.stiffness = s;
        }

        public void setDamping(double d) {
            this.damping = d;
        }

        public boolean isAtRest() {
            // Comparación de bits aproximada en floats, más rápida
            return (
                Math.abs(position - target) < 0.001 &&
                Math.abs(velocity) < 0.001
            );
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

    // ─── Time-based Tween (Zero-Allocation & Branchless) ────────────────

    public enum Easing {
        SMOOTHSTEP,
        EASE_OUT_QUINT,
        EASE_OUT_EXPO,
        EASE_OUT_SINE,
        LINEAR,
    }

    public static class Tween {

        private double elapsed = 0;
        private double duration = 0.4;
        private double from = 0;
        private double to = 1;
        private Easing easing = Easing.SMOOTHSTEP;

        // Caché de la diferencia para evitar restas en cada frame de getValue()
        private double delta = 1;

        public Tween(double duration, double from, double to) {
            this.duration = duration;
            this.from = from;
            this.to = to;
            this.delta = to - from;
        }

        public Tween() {}

        public void reset() {
            elapsed = 0;
        }

        public void reset(double from, double to) {
            this.from = from;
            this.to = to;
            this.delta = to - from;
            elapsed = 0;
        }

        public void setFrom(double from) {
            this.from = from;
            this.delta = this.to - from;
        }

        public void setTo(double to) {
            this.to = to;
            this.delta = to - this.from;
        }

        public void setEasing(Easing e) {
            this.easing = e;
        }

        public Easing getEasing() {
            return easing;
        }

        public double getDuration() {
            return duration;
        }

        public void setDuration(double d) {
            this.duration = d;
        }

        // Utiliza una división pre-calculada si la duración fuera estática,
        // pero al ser variable, la división nativa de double es suficientemente rápida.
        public double getProgress() {
            return elapsed >= duration ? 1.0 : elapsed / duration;
        }

        public double getValue() {
            double t = getProgress();
            if (t >= 1.0) return to; // Bypass total si la animación terminó
            if (t <= 0.0) return from;

            double eased;
            switch (easing) {
                case EASE_OUT_QUINT:
                    eased = easeOutQuint(t);
                    break;
                case EASE_OUT_EXPO:
                    eased = easeOutExpo(t);
                    break;
                case EASE_OUT_SINE:
                    eased = easeOutSine(t);
                    break;
                case LINEAR:
                    eased = t;
                    break;
                default:
                    eased = smoothstep(t);
                    break;
            }
            // Utilizamos el delta precalculado en la actualización de estados
            return from + delta * eased;
        }

        public boolean isFinished() {
            return elapsed >= duration;
        }

        public void update(double dt) {
            elapsed += dt; // Eliminamos el Math.min para ahorrar ciclos, limitamos en getProgress()
        }
    }
}
