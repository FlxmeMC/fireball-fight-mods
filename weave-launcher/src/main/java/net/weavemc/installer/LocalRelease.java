package net.weavemc.installer;

import com.google.gson.Gson;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.util.HashSet;
import java.util.Set;

/** Explicit local launch only. Online manifests cannot opt themselves into local file access. */
final class LocalRelease {
    private LocalRelease() { }

    static ReleaseManifest load(Path manifestPath) throws IOException {
        Path path = manifestPath.toRealPath();
        ReleaseManifest manifest = new Gson().fromJson(
                new String(Files.readAllBytes(path), StandardCharsets.UTF_8), ReleaseManifest.class);
        if (manifest == null || manifest.schemaVersion != 1 || manifest.releaseVersion == null
                || manifest.installerVersion == null || manifest.mods == null
                || manifest.files == null || manifest.files.isEmpty()) {
            throw new IOException("The local build manifest is incomplete. Run launch-local-app.ps1 again.");
        }
        manifest.localDirectory = path.getParent();
        Set<String> destinations = new HashSet<String>();
        for (ReleaseManifest.FileInfo file : manifest.files) {
            if (file.id == null || file.component == null || file.destination == null
                    || file.sha256 == null || !file.sha256.matches("[a-fA-F0-9]{64}")
                    || file.size < 1 || !destinations.add(file.destination)) {
                throw new IOException("Invalid local artifact metadata.");
            }
            // Verify every snapshot before offering it for installation, then verify again when read.
            readArtifact(manifest, file);
        }
        return manifest;
    }

    static byte[] readArtifact(ReleaseManifest manifest, ReleaseManifest.FileInfo file) throws IOException {
        if (manifest.localDirectory == null) throw new IOException("Local artifacts require an explicit local launch.");
        Path source;
        try {
            URI uri = URI.create(file.url);
            if (!"file".equalsIgnoreCase(uri.getScheme())) throw new IllegalArgumentException("Not a local file");
            source = Paths.get(uri).toRealPath();
        } catch (RuntimeException exception) {
            throw new IOException("Invalid local artifact location for " + file.id, exception);
        }
        if (!source.startsWith(manifest.localDirectory) || !Files.isRegularFile(source)) {
            throw new IOException("Local artifact is outside its build snapshot: " + file.id);
        }
        byte[] bytes = Files.readAllBytes(source);
        if (bytes.length != file.size || !sha256(bytes).equalsIgnoreCase(file.sha256)) {
            throw new IOException("Local artifact changed or is corrupt: " + file.id + ". Rebuild the local app.");
        }
        return bytes;
    }

    private static String sha256(byte[] bytes) throws IOException {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(bytes);
            StringBuilder text = new StringBuilder();
            for (byte value : hash) text.append(String.format(java.util.Locale.ROOT, "%02x", value & 255));
            return text.toString();
        } catch (java.security.NoSuchAlgorithmException exception) {
            throw new IOException("SHA-256 unavailable", exception);
        }
    }
}
