package net.weavemc.mods.blockhitsound;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public final class BlockhitSoundsTest {
    @Test
    public void defaultsToAnvilPlaceAtDoublePitch() {
        SoundDefinition sound = BlockhitSounds.resolve(BlockhitSounds.DEFAULT_KEY);
        assertEquals("random.anvil_land", sound.getEventName());
        assertEquals(2.0F, sound.getDefaultPitch(), 0.0F);
        SoundDefinition custom = BlockhitSounds.resolve("random.orb");
        assertEquals("random.orb", custom.getEventName());
        assertEquals(1.0F, custom.getDefaultPitch(), 0.0F);
        assertEquals("item.fireCharge.use",
                BlockhitSounds.resolve("item.fireCharge.use").getEventName());
        assertEquals(sound, BlockhitSounds.resolve("not a sound"));
    }
}
