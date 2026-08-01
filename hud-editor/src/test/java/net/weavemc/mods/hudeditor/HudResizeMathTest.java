package net.weavemc.mods.hudeditor;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public final class HudResizeMathTest {
    private static final float EPSILON = 0.0001F;

    @Test
    public void rightEdgeScalesAroundLeftEdgeAndVerticalCenter() {
        HudResizeMath.Result result = HudResizeMath.resize(
                ResizeEdge.RIGHT, 10.0F, 20.0F, 100.0F, 40.0F,
                1.0F, 210.0F, 40.0F, 0.5F, 5.0F);

        assertEquals(2.0F, result.scale, EPSILON);
        assertEquals(10.0F, result.x, EPSILON);
        assertEquals(0.0F, result.y, EPSILON);
    }

    @Test
    public void leftEdgeKeepsOppositeEdgeFixed() {
        HudResizeMath.Result result = HudResizeMath.resize(
                ResizeEdge.LEFT, 10.0F, 20.0F, 100.0F, 40.0F,
                1.0F, -90.0F, 40.0F, 0.5F, 5.0F);

        assertEquals(2.0F, result.scale, EPSILON);
        assertEquals(-90.0F, result.x, EPSILON);
        assertEquals(110.0F, result.x + 200.0F, EPSILON);
    }

    @Test
    public void cornerUsesBothAxesAndKeepsOppositeCornerFixed() {
        HudResizeMath.Result result = HudResizeMath.resize(
                ResizeEdge.BOTTOM_RIGHT, 10.0F, 20.0F, 100.0F, 50.0F,
                1.0F, 210.0F, 120.0F, 0.5F, 5.0F);

        assertEquals(2.0F, result.scale, EPSILON);
        assertEquals(10.0F, result.x, EPSILON);
        assertEquals(20.0F, result.y, EPSILON);
    }

    @Test
    public void scaleLimitsStillPreserveResizeAnchor() {
        HudResizeMath.Result result = HudResizeMath.resize(
                ResizeEdge.TOP, 10.0F, 20.0F, 100.0F, 40.0F,
                1.0F, 60.0F, -500.0F, 0.5F, 2.0F);

        assertEquals(2.0F, result.scale, EPSILON);
        assertEquals(60.0F, result.y + 80.0F, EPSILON);
    }

    @Test
    public void hitTestPrioritizesWindowsStyleCornerHandles() {
        assertEquals(ResizeEdge.TOP_LEFT,
                ResizeEdge.hitTest(10.0F, 20.0F, 110.0F, 60.0F, 12, 22, 5.0F));
        assertEquals(ResizeEdge.RIGHT,
                ResizeEdge.hitTest(10.0F, 20.0F, 110.0F, 60.0F, 109, 40, 5.0F));
        assertEquals(ResizeEdge.NONE,
                ResizeEdge.hitTest(10.0F, 20.0F, 110.0F, 60.0F, 60, 40, 5.0F));
    }

    @Test
    public void smallElementsKeepTheirCenterAvailableForDragging() {
        float grip = ResizeEdge.adaptiveGrip(10.0F, 20.0F, 90.0F, 30.0F, 5.0F);

        assertEquals(2.5F, grip, EPSILON);
        assertEquals(ResizeEdge.NONE,
                ResizeEdge.hitTest(10.0F, 20.0F, 90.0F, 30.0F, 50, 25, grip));
        assertEquals(ResizeEdge.TOP,
                ResizeEdge.hitTest(10.0F, 20.0F, 90.0F, 30.0F, 50, 21, grip));
    }
}
