package net.weavemc.installer;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class LauncherUpdaterTest {
    @Test
    public void detectsOnlyStrictlyNewerLauncherVersions() {
        ReleaseManifest manifest = new ReleaseManifest();
        manifest.installerVersion = "1.0.10";
        assertTrue(LauncherUpdater.isUpdateAvailable(manifest));

        manifest.installerVersion = Main.VERSION;
        assertFalse(LauncherUpdater.isUpdateAvailable(manifest));

        manifest.installerVersion = "0.9.9";
        assertFalse(LauncherUpdater.isUpdateAvailable(manifest));
    }
}
