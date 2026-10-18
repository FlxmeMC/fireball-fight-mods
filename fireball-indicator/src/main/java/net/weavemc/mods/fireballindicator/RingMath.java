package net.weavemc.mods.fireballindicator;

final class RingMath {
    private RingMath() {
    }

    /**
     * Angle of a horizontal offset relative to where the player faces, in degrees.
     * 0 is straight ahead (top of the ring), 90 is to the right, -90 to the left, 180 behind.
     */
    static double relativeAngle(float playerYaw, double dx, double dz) {
        double targetYaw = Math.toDegrees(Math.atan2(dz, dx)) - 90.0;
        return wrapDegrees(targetYaw - playerYaw);
    }

    static double wrapDegrees(double degrees) {
        double wrapped = degrees % 360.0;
        if (wrapped >= 180.0) {
            wrapped -= 360.0;
        }
        if (wrapped < -180.0) {
            wrapped += 360.0;
        }
        return wrapped;
    }

    static double lerp(double from, double to, float t) {
        return from + (to - from) * t;
    }

    /** 1 when the fireball is right next to the player, falling to 0 at {@code range} and beyond. */
    static double closeness(double distance, double range) {
        double near = 3.0;
        if (range <= near) {
            return 1.0;
        }
        double t = (distance - near) / (range - near);
        return 1.0 - Math.max(0.0, Math.min(1.0, t));
    }
}
