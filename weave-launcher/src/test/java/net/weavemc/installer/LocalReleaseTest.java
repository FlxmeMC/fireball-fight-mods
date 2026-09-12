package net.weavemc.installer;

import com.google.gson.Gson;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.Collections;
import org.junit.After;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import static org.junit.Assert.*;

public final class LocalReleaseTest {
    @Rule public final TemporaryFolder temporary = new TemporaryFolder();
    private final String previousLocalManifest = System.getProperty("weave.localManifest");
    @After public void restoreMode() {
        if (previousLocalManifest == null) System.clearProperty("weave.localManifest");
        else System.setProperty("weave.localManifest", previousLocalManifest);
    }

    private Path fixture(boolean outside, boolean remote) throws Exception {
        Path root = temporary.newFolder().toPath();
        Path artifact = outside ? temporary.newFile().toPath() : root.resolve("mod.jar");
        byte[] bytes = "local build fixture".getBytes(StandardCharsets.UTF_8);
        Files.write(artifact, bytes);
        ReleaseManifest manifest = new ReleaseManifest();
        manifest.schemaVersion = 1;
        manifest.releaseVersion = "1.0.8-local-test";
        manifest.installerVersion = "999.0.0";
        ReleaseManifest.ModInfo mod = new ReleaseManifest.ModInfo();
        mod.id = "fireball-hitboxes";
        mod.name = "Fireball Hitboxes";
        mod.version = "1.0.0";
        mod.dependencies = Collections.singletonList("server-context");
        manifest.mods = Collections.singletonList(mod);
        ReleaseManifest.FileInfo file = new ReleaseManifest.FileInfo();
        file.id = "fireball-hitboxes";
        file.component = "fireball-hitboxes";
        file.destination = "mods/1.8.9/fireball-hitboxes.jar";
        file.url = remote ? "https://example.invalid/mod.jar" : artifact.toUri().toString();
        file.size = bytes.length;
        StringBuilder hash = new StringBuilder();
        for (byte value : MessageDigest.getInstance("SHA-256").digest(bytes)) {
            hash.append(String.format(java.util.Locale.ROOT, "%02x", value & 255));
        }
        file.sha256 = hash.toString();
        manifest.files = Collections.singletonList(file);
        Path path = root.resolve("manifest.json");
        Files.write(path, new Gson().toJson(manifest).getBytes(StandardCharsets.UTF_8));
        return path;
    }

    @Test public void explicitLocalLaunchLoadsLocalCatalogAndSuppressesOnlineUpdates() throws Exception {
        System.setProperty("weave.localManifest", fixture(false, false).toString());
        ReleaseManifest manifest = new ManifestClient().fetch();
        assertEquals("fireball-hitboxes", manifest.mods.get(0).id);
        assertNotNull(manifest.localDirectory);
        assertEquals("local build fixture", new String(LocalRelease.readArtifact(manifest, manifest.files.get(0)), StandardCharsets.UTF_8));
        assertFalse(LauncherUpdater.isUpdateAvailable(manifest));
    }
    @Test public void changedArtifactIsRejectedBeforeInstallation() throws Exception {
        Path path = fixture(false, false);
        ReleaseManifest manifest = LocalRelease.load(path);
        Files.write(path.getParent().resolve("mod.jar"), new byte[] {1, 2, 3});
        try { LocalRelease.readArtifact(manifest, manifest.files.get(0)); fail("Expected changed artifact rejection"); }
        catch (IOException expected) { assertTrue(expected.getMessage().contains("changed or is corrupt")); }
    }
    @Test public void rejectsSourcesOutsideSnapshotAndRemoteSources() throws Exception {
        for (Path path : new Path[] {fixture(true, false), fixture(false, true)}) {
            try { LocalRelease.load(path); fail("Expected source rejection"); }
            catch (IOException expected) { assertNotNull(expected.getMessage()); }
        }
    }
    @Test public void brokenLocalManifestDoesNotFallBackToPublishedRelease() throws Exception {
        System.setProperty("weave.localManifest", temporary.getRoot().toPath().resolve("missing.json").toString());
        try { new ManifestClient().fetch(); fail("Expected local file error"); }
        catch (java.nio.file.NoSuchFileException expected) { assertTrue(ManifestClient.isLocalMode()); }
    }
    @Test public void jsonCannotGrantLocalFileAccess() throws Exception {
        ReleaseManifest online = new Gson().fromJson("{\"localDirectory\":\"C:/\"}", ReleaseManifest.class);
        assertNull(online.localDirectory);
        try { LocalRelease.readArtifact(online, new ReleaseManifest.FileInfo()); fail("Expected explicit-launch requirement"); }
        catch (IOException expected) { assertTrue(expected.getMessage().contains("explicit local launch")); }
    }
}
