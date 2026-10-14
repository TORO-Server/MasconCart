package prj.salmon.masconcart;

/** Constant acceleration and braking, using the existing display speed scale. */
final class MasconMotionProfile {
    static final double MAX_SPEED = 1.5;
    static final double DISPLAY_KMH_PER_SPEED = 80.0;
    private static final double ACCELERATION_PER_SECOND = 0.2; // 0.02 per two server ticks
    private static final double P1_ACCELERATION_PER_SECOND = 0.05; // 0.005 per two server ticks

    double update(double speed, int notch, double seconds) {
        if (!Double.isFinite(seconds) || seconds <= 0.0) {
            throw new IllegalArgumentException("seconds must be finite and positive");
        }
        notch = Math.max(-8, Math.min(notch, 7));
        if (notch > 0) {
            double target = MAX_SPEED / 7.0 * notch;
            double acceleration = notch == 1 ? P1_ACCELERATION_PER_SECOND : ACCELERATION_PER_SECOND;
            if (speed < target) speed = Math.min(speed + acceleration * seconds, target);
            else speed *= Math.pow(0.995, seconds / 0.1);
        } else if (notch == -8) {
            speed = 0.0;
        } else if (notch < 0) {
            speed -= 0.07 * Math.abs(notch) * seconds;
        } else {
            speed *= Math.pow(0.995, seconds / 0.1);
        }
        return Math.max(0.0, Math.min(speed, MAX_SPEED));
    }
}
