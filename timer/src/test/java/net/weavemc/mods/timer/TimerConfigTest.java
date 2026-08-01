package net.weavemc.mods.timer;

import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.Rule;

import java.nio.file.Path;

import static org.junit.Assert.assertEquals;

public final class TimerConfigTest {
    @Rule
    public final TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void savesHudPositionAndClampedScale() throws Exception {
        Path file = temporaryFolder.newFolder("config").toPath().resolve("timer.properties");
        TimerConfig config = TimerConfig.load(file);
        config.setPosition(42.5F, 18.25F);
        config.setScale(99.0F);
        config.save();

        TimerConfig loaded = TimerConfig.load(file);
        assertEquals(42.5F, loaded.getX(), 0.0F);
        assertEquals(18.25F, loaded.getY(), 0.0F);
        assertEquals(TimerConfig.MAX_SCALE, loaded.getScale(), 0.0F);
    }
}
