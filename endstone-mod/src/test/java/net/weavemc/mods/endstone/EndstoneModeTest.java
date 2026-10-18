package net.weavemc.mods.endstone;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public final class EndstoneModeTest {
    @Test
    public void parsesModesFromTheOldPropertiesFile() {
        assertEquals(EndstoneMode.RADIUS_3, EndstoneMode.fromFeatureMode("radius_3"));
        assertEquals(EndstoneMode.RADIUS_5, EndstoneMode.fromFeatureMode(" radius_5 "));
        assertEquals(EndstoneMode.ALWAYS, EndstoneMode.fromFeatureMode("always"));
    }

    @Test
    public void unknownOrMissingModesAreNotImported() {
        assertNull(EndstoneMode.fromFeatureMode(null));
        assertNull(EndstoneMode.fromFeatureMode("radius_7"));
    }

    @Test
    public void labelsMatchTheMenu() {
        assertEquals("3 blocks", EndstoneMode.RADIUS_3.toString());
        assertEquals("5 blocks", EndstoneMode.RADIUS_5.toString());
        assertEquals("Always", EndstoneMode.ALWAYS.toString());
    }

    @Test
    public void eachModeDrivesTheMatchingFeatureMode() {
        for (EndstoneMode mode : EndstoneMode.values()) {
            EndstoneFeature.setGlassMode(mode.featureMode);
            assertEquals(mode.featureMode, EndstoneFeature.getGlassMode());
        }
        EndstoneFeature.setGlassMode(EndstoneFeature.MODE_RADIUS_5);
    }
}
