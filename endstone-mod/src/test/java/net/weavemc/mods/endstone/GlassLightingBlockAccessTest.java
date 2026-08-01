package net.weavemc.mods.endstone;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public final class GlassLightingBlockAccessTest {
    @Test
    public void raisesOnlyNearbyGlassLightingWithoutSamplingAnotherPosition() {
        assertEquals(15, GlassLightingBlockAccess.lightValueFor(true, 0));
        assertEquals(15, GlassLightingBlockAccess.lightValueFor(true, 8));
        assertEquals(15, GlassLightingBlockAccess.lightValueFor(false, 15));
        assertEquals(4, GlassLightingBlockAccess.lightValueFor(false, 4));
    }
}
