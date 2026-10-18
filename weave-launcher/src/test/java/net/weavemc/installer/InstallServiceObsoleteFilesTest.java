package net.weavemc.installer;

import org.junit.After;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class InstallServiceObsoleteFilesTest {
    @Rule
    public final TemporaryFolder temporaryFolder = new TemporaryFolder();

    @After
    public void clearProperties() {
        System.clearProperty("weave.installRoot");
        System.clearProperty("weave.lunarRoot");
    }

    @Test
    public void removesRetiredModJarsButKeepsShippedFilesAndUserData() throws Exception {
        Path install = temporaryFolder.newFolder("weave").toPath();
        System.setProperty("weave.installRoot", install.toString());
        System.setProperty("weave.lunarRoot", temporaryFolder.newFolder("lunar").toString());
        Path retired = touch(install.resolve("mods/1.8.9/hud-editor.jar"));
        Path retiredChecksum = touch(install.resolve("mods/1.8.9/hud-editor.jar.sha256"));
        Path shipped = touch(install.resolve("mods/1.8.9/timer.jar"));
        Path config = touch(install.resolve("config/timer.properties"));
        Path modData = touch(install.resolve("mod-data/1.8.9/lunar-mod-api/settings.json"));
        Path outside = touch(temporaryFolder.getRoot().toPath().resolve("outside.jar"));

        ReleaseManifest manifest = new ReleaseManifest();
        ReleaseManifest.FileInfo timer = new ReleaseManifest.FileInfo();
        timer.destination = "mods/1.8.9/timer.jar";
        manifest.files = Collections.singletonList(timer);
        List<String> previous = Arrays.asList(
                "mods/1.8.9/hud-editor.jar", "mods\\1.8.9\\timer.jar", "config/timer.properties",
                "mod-data/1.8.9/lunar-mod-api/settings.json", "mods/../../outside.jar");

        Method removeObsolete = InstallService.class.getDeclaredMethod(
                "removeObsolete", ReleaseManifest.class, List.class);
        removeObsolete.setAccessible(true);
        removeObsolete.invoke(new InstallService(), manifest, previous);

        assertFalse(Files.exists(retired));
        assertFalse(Files.exists(retiredChecksum));
        assertTrue(Files.exists(shipped));
        assertTrue(Files.exists(config));
        assertTrue(Files.exists(modData));
        assertTrue(Files.exists(outside));
    }

    @Test
    public void onlyModJarsAreEverRemovable() {
        assertTrue(InstallService.isRemovableModFile("mods/1.8.9/hud-editor.jar"));
        assertTrue(InstallService.isRemovableModFile("mods\\1.8.9\\hud-editor.jar.sha256"));
        assertFalse(InstallService.isRemovableModFile("loader.jar"));
        assertFalse(InstallService.isRemovableModFile("config/blockhit-sound.properties"));
        assertFalse(InstallService.isRemovableModFile("mods/1.8.9/notes.txt"));
        assertFalse(InstallService.isRemovableModFile("mods/../loader.jar"));
    }

    private static Path touch(Path file) throws Exception {
        Files.createDirectories(file.getParent());
        Files.write(file, new byte[] {1});
        return file;
    }
}
