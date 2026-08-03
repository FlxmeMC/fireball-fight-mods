package net.weavemc.installer;

import com.google.gson.Gson;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

final class ManifestClient {
    static final String MANIFEST_URL =
            "https://fbfight-updates.flxme.cc/installer/manifest.json";
    static final String SIGNATURE_URL = MANIFEST_URL + ".sig";

    // Replaced by publish-online-installer.ps1 from the offline release key.
    private static final String PUBLIC_KEY_BASE64 =
            "MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAEJlZO55XlPtE2Xeep98/x2os+oAxVJp8h1TCyPKhtjunOKARADgJ630kmIgLJdXs84dJae2FAl8CR6hvvjVrkMw==";

    ReleaseManifest fetch() throws Exception {
        byte[] manifestBytes = download(MANIFEST_URL, 15_000, 30_000);
        byte[] signatureBytes = Base64.getDecoder().decode(
                new String(download(SIGNATURE_URL, 15_000, 30_000),
                        StandardCharsets.US_ASCII).trim());
        verify(manifestBytes, signatureBytes);
        ReleaseManifest manifest = new Gson().fromJson(
                new String(manifestBytes, StandardCharsets.UTF_8), ReleaseManifest.class);
        if (manifest == null || manifest.schemaVersion != 1
                || manifest.releaseVersion == null || manifest.installerVersion == null
                || manifest.installer == null || manifest.installer.url == null
                || manifest.installer.sha256 == null
                || manifest.files == null || manifest.mods == null) {
            throw new IOException("The update server returned an unsupported manifest.");
        }
        return manifest;
    }

    private static void verify(byte[] content, byte[] signatureBytes) throws Exception {
        if (PUBLIC_KEY_BASE64.startsWith("__")) {
            throw new SecurityException("Installer manifest public key was not embedded.");
        }
        PublicKey key = KeyFactory.getInstance("EC").generatePublic(
                new X509EncodedKeySpec(Base64.getDecoder().decode(PUBLIC_KEY_BASE64)));
        Signature verifier = Signature.getInstance("SHA256withECDSA");
        verifier.initVerify(key);
        verifier.update(content);
        if (!verifier.verify(signatureBytes)) {
            throw new SecurityException("The update manifest signature is invalid.");
        }
    }

    static byte[] download(String url, int connectTimeout, int readTimeout) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
        connection.setConnectTimeout(connectTimeout);
        connection.setReadTimeout(readTimeout);
        connection.setInstanceFollowRedirects(true);
        connection.setRequestProperty("User-Agent", "Fireball-Fight-Mods/" + Main.VERSION);
        int status = connection.getResponseCode();
        if (status < 200 || status >= 300) {
            connection.disconnect();
            throw new IOException("Download failed with HTTP " + status + ": " + url);
        }
        try (InputStream input = connection.getInputStream();
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[16 * 1024];
            int read;
            while ((read = input.read(buffer)) >= 0) {
                output.write(buffer, 0, read);
            }
            return output.toByteArray();
        } finally {
            connection.disconnect();
        }
    }
}
