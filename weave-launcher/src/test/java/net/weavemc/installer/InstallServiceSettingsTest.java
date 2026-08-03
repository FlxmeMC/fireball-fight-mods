package net.weavemc.installer;

import org.junit.After;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Properties;

import static org.junit.Assert.assertEquals;

public final class InstallServiceSettingsTest {
    @Rule
    public final TemporaryFolder temporaryFolder = new TemporaryFolder();

    @After
    public void clearProperties() {
        System.clearProperty("weave.installRoot");
        System.clearProperty("weave.lunarRoot");
    }

    @Test
    public void writesSelectionsAndLoadsThemAgain() throws Exception {
        Path install = temporaryFolder.newFolder("weave").toPath();
        Path lunar = temporaryFolder.newFolder("lunar").toPath();
        System.setProperty("weave.installRoot", install.toString());
        System.setProperty("weave.lunarRoot", lunar.toString());
        InstallService service = new InstallService();
        InstallSettings settings = new InstallSettings(
                "random.orb", 1.35D, InstallSettings.ENDSTONE_ALWAYS);

        service.saveSettings(new HashSet<String>(Arrays.asList(
                "blockhit-sound", "endstone-mod")), settings);

        InstallSettings reloaded = new InstallService().loadSettings();
        assertEquals("random.orb", reloaded.blockhitSound);
        assertEquals(1.35D, reloaded.blockhitPitch, 0.0D);
        assertEquals(InstallSettings.ENDSTONE_ALWAYS, reloaded.endstoneMode);

        Properties blockhit = load(install.resolve("config/blockhit-sound.properties"));
        Properties endstone = load(install.resolve("config/endstone-mod.properties"));
        assertEquals("random.orb", blockhit.getProperty("sound"));
        assertEquals("1.35", blockhit.getProperty("pitch"));
        assertEquals(InstallSettings.ENDSTONE_ALWAYS, endstone.getProperty("mode"));
    }

    private static Properties load(Path file) throws Exception {
        Properties properties = new Properties();
        try (InputStream input = Files.newInputStream(file)) {
            properties.load(input);
        }
        return properties;
    }
}
