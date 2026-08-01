package net.weavemc.mods.hudeditor;

import org.junit.Test;

import java.util.Collections;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

public final class HudSnapMathTest {
    private static final float DELTA = 0.001F;

    @Test
    public void snapsTopEdgesWhenElementsAreNearlyAligned() {
        HudSnapMath.Result result = HudSnapMath.snap(
                130.0F, 52.0F, 40.0F, 10.0F,
                Collections.singletonList(new HudSnapMath.Bounds(20.0F, 50.0F, 100.0F, 70.0F)),
                5.0F);

        assertEquals(50.0F, result.y, DELTA);
        assertNotNull(result.vertical);
        assertEquals(50.0F, result.vertical.guide, DELTA);
    }

    @Test
    public void snapsAdjacentEdgesWithoutChangingOtherAxis() {
        HudSnapMath.Result result = HudSnapMath.snap(
                97.0F, 100.0F, 30.0F, 20.0F,
                Collections.singletonList(new HudSnapMath.Bounds(20.0F, 20.0F, 100.0F, 40.0F)),
                5.0F);

        assertEquals(100.0F, result.x, DELTA);
        assertEquals(100.0F, result.y, DELTA);
        assertNotNull(result.horizontal);
        assertNull(result.vertical);
    }

    @Test
    public void leavesPositionFreeOutsideThreshold() {
        HudSnapMath.Result result = HudSnapMath.snap(
                130.0F, 80.0F, 30.0F, 20.0F,
                Collections.singletonList(new HudSnapMath.Bounds(20.0F, 20.0F, 100.0F, 40.0F)),
                5.0F);

        assertEquals(130.0F, result.x, DELTA);
        assertEquals(80.0F, result.y, DELTA);
        assertNull(result.horizontal);
        assertNull(result.vertical);
    }

    @Test
    public void locksTopEdgesWithinTheEditorSnapRange() {
        HudSnapMath.Result result = HudSnapMath.snap(
                104.0F, 59.0F, 60.0F, 50.0F,
                Collections.singletonList(new HudSnapMath.Bounds(20.0F, 50.0F, 100.0F, 70.0F)),
                10.0F);

        assertEquals(50.0F, result.y, DELTA);
        assertNotNull(result.vertical);
        assertEquals(50.0F, result.vertical.guide, DELTA);
    }
}
