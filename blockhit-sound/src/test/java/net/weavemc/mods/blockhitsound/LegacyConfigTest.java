package net.weavemc.mods.blockhitsound;

import org.junit.Test;

import java.util.Properties;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public final class LegacyConfigTest {
    private static final float EPSILON = 0.0F;

    @Test
    public void presetKeyBecomesItsRawEventAndPitch() {
        LegacyConfig.Values values = LegacyConfig.translate(properties("anvil_place", null, null));
        assertEquals("random.anvil_land", values.sound);
        assertEquals(1.0F, values.volume, EPSILON);
        assertEquals(2.0F, values.pitch, EPSILON);
    }

    @Test
    public void rawEventKeepsSavedVolumeAndPitch() {
        LegacyConfig.Values values = LegacyConfig.translate(properties("random.orb", "0.5", "1.25"));
        assertEquals("random.orb", values.sound);
        assertEquals(0.5F, values.volume, EPSILON);
        assertEquals(1.25F, values.pitch, EPSILON);
    }

    @Test
    public void legacyDefaultIgnoresSavedPitch() {
        LegacyConfig.Values values = LegacyConfig.translate(properties("anvil_fall", "0.8", "0.7"));
        assertEquals("random.anvil_land", values.sound);
        assertEquals(0.8F, values.volume, EPSILON);
        assertEquals(2.0F, values.pitch, EPSILON);
    }

    @Test
    public void invalidValuesFallBackToDefaults() {
        LegacyConfig.Values values = LegacyConfig.translate(properties("not a sound", "-1", "abc"));
        assertEquals("random.anvil_land", values.sound);
        assertEquals(1.0F, values.volume, EPSILON);
        assertEquals(2.0F, values.pitch, EPSILON);
    }

    @Test
    public void catalogContainsDefaultSound() {
        assertTrue(BlockhitSettings.loadCatalog().contains(BlockhitSettings.DEFAULT_SOUND));
        assertTrue(BlockhitSettings.loadCatalog().size() > 200);
    }

    private static Properties properties(String sound, String volume, String pitch) {
        Properties properties = new Properties();
        if (sound != null) {
            properties.setProperty("sound", sound);
        }
        if (volume != null) {
            properties.setProperty("volume", volume);
        }
        if (pitch != null) {
            properties.setProperty("pitch", pitch);
        }
        return properties;
    }
}
