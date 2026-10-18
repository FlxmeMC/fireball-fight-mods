package net.weavemc.installer;

import com.google.gson.Gson;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.sql.Connection;
import java.sql.DriverManager;
import java.util.Arrays;
import java.util.Collections;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import org.junit.After;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import static org.junit.Assert.*;

public final class InstallCleanupTest {
    @Rule public final TemporaryFolder temporary = new TemporaryFolder();
    private static final InstallService.Progress PROGRESS = new InstallService.Progress() {
        @Override public void update(int percent, String status) { }
        @Override public void log(String message) { }
    };

    @After public void reset() {
        System.clearProperty("weave.installRoot");
        System.clearProperty("weave.lunarRoot");
    }

    private Path setup() throws Exception {
        Path root = temporary.newFolder("weave").toPath();
        Path lunar = temporary.newFolder("lunar").toPath();
        System.setProperty("weave.installRoot", root.toString());
        System.setProperty("weave.lunarRoot", lunar.toString());
        return root;
    }

    @Test public void uninstallRemovesUnrecordedAndRenamedCopiesButPreservesUserFiles() throws Exception {
        Path root = setup();
        Path legacy = jar(root.resolve("mods/blockhit-sound-1.0.jar"), "blockhit-sound");
        Path renamed = jar(root.resolve("mods/1.8.9/old-sound.jar"), "blockhit-sound");
        Path retired = jar(root.resolve("mods/1.8.9/hud-editor.jar"), "hud-editor");
        Path unrelated = jar(root.resolve("mods/1.8.9/my-mod.jar"), "my-mod");
        Path disabled = jar(root.resolve("mods/disabled/sound.jar"), "blockhit-sound");
        Path otherVersion = jar(root.resolve("mods/1.12/sound.jar"), "blockhit-sound");
        Files.write(root.resolve("mods/1.8.9/old-sound.jar.sha256"), new byte[] {1});
        Files.createDirectories(root.resolve("config"));
        Path config = Files.write(root.resolve("config/sound.properties"), new byte[] {1});
        // A partial record must not prevent discovering the rest of the pack.
        Files.write(root.resolve("installed-release.json"),
                "{\"files\":[{\"path\":\"mods/1.8.9/hud-editor.jar\"}]}".getBytes(StandardCharsets.UTF_8));
        new InstallService().uninstall(PROGRESS);
        for (Path removed : Arrays.asList(legacy, renamed, retired,
                root.resolve("mods/1.8.9/old-sound.jar.sha256"), root.resolve("installed-release.json"))) {
            assertFalse(removed.toString(), Files.exists(removed));
        }
        for (Path preserved : Arrays.asList(unrelated, disabled, otherVersion, config)) {
            assertTrue(preserved.toString(), Files.exists(preserved));
        }
    }

    @Test public void detectsAndRemovesLeftoversWithoutALoaderOrValidRecord() throws Exception {
        Path root = setup();
        Path sound = jar(root.resolve("mods/sound-old.jar"), "blockhit-sound");
        InstallService service = new InstallService();
        assertTrue(service.isInstalled());
        Files.write(root.resolve("installed-release.json"), "broken".getBytes(StandardCharsets.UTF_8));
        service.uninstall(PROGRESS);
        assertFalse(Files.exists(sound));
        assertFalse(service.isInstalled());
    }

    @Test public void updateRemovesDuplicatesAndDeselectedModsWithoutAnOldRecord() throws Exception {
        Path root = setup();
        Path lunar = java.nio.file.Paths.get(System.getProperty("weave.lunarRoot"));
        Files.createDirectories(lunar.resolve("db"));
        Class.forName("org.sqlite.JDBC");
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + lunar.resolve("db/profiles.db"))) {
            connection.createStatement().execute("CREATE TABLE profiles (id TEXT, type TEXT, game_version TEXT, jvm_arguments TEXT)");
            connection.createStatement().execute("INSERT INTO profiles VALUES ('pvp','lunar','1.8.9',NULL)");
        }
        Path oldSound = jar(root.resolve("mods/blockhit-old.jar"), "blockhit-sound");
        Path deselected = jar(root.resolve("mods/1.8.9/timer-old.jar"), "timer");
        Path unrelated = jar(root.resolve("mods/unrelated.jar"), "other");
        Path snapshot = temporary.newFolder("snapshot").toPath();
        Path source = jar(snapshot.resolve("sound.jar"), "blockhit-sound");
        ReleaseManifest.FileInfo file = new ReleaseManifest.FileInfo();
        file.id = "blockhit-sound";
        file.component = "blockhit-sound";
        file.destination = "mods/1.8.9/blockhit-sound.jar";
        file.url = source.toUri().toString();
        file.size = Files.size(source);
        StringBuilder hash = new StringBuilder();
        for (byte value : MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(source))) {
            hash.append(String.format(java.util.Locale.ROOT, "%02x", value & 255));
        }
        file.sha256 = hash.toString();
        ReleaseManifest manifest = new ReleaseManifest();
        manifest.localDirectory = snapshot;
        manifest.mods = Collections.emptyList();
        manifest.files = Collections.singletonList(file);
        new InstallService().install(manifest, Collections.singleton("blockhit-sound"), PROGRESS);
        assertFalse(Files.exists(oldSound));
        assertFalse(Files.exists(deselected));
        assertTrue(Files.exists(unrelated));
        assertArrayEquals(Files.readAllBytes(source), Files.readAllBytes(root.resolve(file.destination)));
        InstallService.InstalledRecord record = new Gson().fromJson(new String(
                Files.readAllBytes(root.resolve("installed-release.json")), StandardCharsets.UTF_8),
                InstallService.InstalledRecord.class);
        assertEquals(1, record.files.size());
    }

    private static Path jar(Path path, String id) throws Exception {
        Files.createDirectories(path.getParent());
        try (JarOutputStream jar = new JarOutputStream(Files.newOutputStream(path))) {
            jar.putNextEntry(new JarEntry("weave.mod.json"));
            jar.write(("{\"modId\":\"" + id + "\"}").getBytes(StandardCharsets.UTF_8));
            jar.closeEntry();
        }
        return path;
    }
}
