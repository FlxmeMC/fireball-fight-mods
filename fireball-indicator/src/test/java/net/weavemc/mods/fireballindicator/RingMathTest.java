package net.weavemc.mods.fireballindicator;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public final class RingMathTest {
    private static final double EPSILON = 1.0E-6;

    @Test
    public void fireballAheadIsAtTopOfRing() {
        // Yaw 0 faces +Z (south).
        assertEquals(0.0, RingMath.relativeAngle(0.0F, 0.0, 10.0), EPSILON);
    }

    @Test
    public void fireballToTheRightAndBehind() {
        // Facing south, west (-X) is on the player's right.
        assertEquals(90.0, RingMath.relativeAngle(0.0F, -10.0, 0.0), EPSILON);
        assertEquals(-90.0, RingMath.relativeAngle(0.0F, 10.0, 0.0), EPSILON);
        assertEquals(-180.0, RingMath.relativeAngle(0.0F, 0.0, -10.0), EPSILON);
    }

    @Test
    public void turningMovesTheHighlightAroundTheRing() {
        // Fireball stays to the south; once we face west (yaw 90) it is on our left.
        assertEquals(-90.0, RingMath.relativeAngle(90.0F, 0.0, 10.0), EPSILON);
        // Unwrapped yaw values from spinning in place still land in range.
        assertEquals(0.0, RingMath.relativeAngle(720.0F, 0.0, 10.0), EPSILON);
    }

    @Test
    public void closenessScalesWithDistance() {
        assertEquals(1.0, RingMath.closeness(2.0, 40.0), EPSILON);
        assertEquals(0.0, RingMath.closeness(40.0, 40.0), EPSILON);
        assertEquals(0.5, RingMath.closeness(21.5, 40.0), EPSILON);
    }
}
