package net.weavemc.installer;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class LauncherUpdaterTest {
    @Test
    public void detectsOnlyStrictlyNewerLauncherVersions() {
        ReleaseManifest manifest = new ReleaseManifest();
        String[] current = Main.VERSION.split("\\.");
        manifest.installerVersion = current[0] + "." + current[1] + "." + (Integer.parseInt(current[2]) + 1);
        assertTrue(LauncherUpdater.isUpdateAvailable(manifest));

        manifest.installerVersion = Main.VERSION;
        assertFalse(LauncherUpdater.isUpdateAvailable(manifest));

        manifest.installerVersion = "0.9.9";
        assertFalse(LauncherUpdater.isUpdateAvailable(manifest));
    }
}
