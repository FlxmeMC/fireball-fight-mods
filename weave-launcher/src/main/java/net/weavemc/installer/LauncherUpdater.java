package net.weavemc.installer;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.Locale;

final class LauncherUpdater {
    private LauncherUpdater() {
    }

    static boolean isUpdateAvailable(ReleaseManifest manifest) {
        if (manifest.localDirectory != null) return false;
        try {
            return VersionNumbers.compare(manifest.installerVersion, Main.VERSION) > 0;
        } catch (IllegalArgumentException ignored) {
            return false;
        }
    }

    static Path downloadAndVerify(ReleaseManifest manifest) throws Exception {
        URI uri = URI.create(manifest.installer.url);
        if (!"https".equalsIgnoreCase(uri.getScheme())) {
            throw new SecurityException("The update download must use HTTPS.");
        }
        String expected = manifest.installer.sha256.trim().toLowerCase(Locale.ROOT);
        if (!expected.matches("[0-9a-f]{64}")) {
            throw new SecurityException("The update checksum is invalid.");
        }

        Path target = Files.createTempFile(
                "Fireball-Fight-Mods-update-" + safeVersion(manifest.installerVersion) + "-", ".exe");
        try {
            String actual = downloadToFile(uri, target);
            if (!MessageDigest.isEqual(expected.getBytes(java.nio.charset.StandardCharsets.US_ASCII),
                    actual.getBytes(java.nio.charset.StandardCharsets.US_ASCII))) {
                throw new SecurityException("The downloaded update failed its security check.");
            }
            return target;
        } catch (Exception exception) {
            Files.deleteIfExists(target);
            throw exception;
        }
    }

    static void launch(Path installer) throws IOException {
        new ProcessBuilder(installer.toAbsolutePath().toString()).start();
    }

    private static String downloadToFile(URI uri, Path target) throws Exception {
        String url = uri.toString();
        HttpURLConnection connection = HttpConnections.connect(url, 15_000, 180_000);
        int status = connection.getResponseCode();
        if (status < 200 || status >= 300) {
            ReleaseServerException failure = HttpConnections.statusFailure(connection, url);
            connection.disconnect();
            throw failure;
        }
        if (!"https".equalsIgnoreCase(connection.getURL().getProtocol())) {
            connection.disconnect();
            throw new SecurityException("The update server redirected to an insecure connection.");
        }

        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (InputStream input = connection.getInputStream();
             OutputStream output = Files.newOutputStream(target)) {
            byte[] buffer = new byte[64 * 1024];
            int read;
            while ((read = input.read(buffer)) >= 0) {
                if (read > 0) {
                    output.write(buffer, 0, read);
                    digest.update(buffer, 0, read);
                }
            }
        } catch (IOException exception) {
            InstallerLog.error("Update download failed for " + url, exception);
            throw HttpConnections.describe(url, exception);
        } finally {
            connection.disconnect();
        }
        return toHex(digest.digest());
    }

    private static String safeVersion(String version) {
        return version.replaceAll("[^0-9A-Za-z._-]", "-");
    }

    private static String toHex(byte[] bytes) {
        StringBuilder output = new StringBuilder(bytes.length * 2);
        for (byte value : bytes) {
            output.append(String.format(Locale.ROOT, "%02x", value & 0xff));
        }
        return output.toString();
    }
}
