package net.weavemc.installer;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Properties;
import java.util.Set;

final class InstallService {
    interface Progress {
        void update(int percent, String status);

        void log(String message);
    }

    private final Path installRoot = configuredPath("weave.installRoot", ".weave");
    private final Path lunarRoot = configuredPath("weave.lunarRoot", ".lunarclient");
    private final LunarProfileService profiles = new LunarProfileService();

    private static Path configuredPath(String property, String fallbackName) {
        String configured = System.getProperty(property);
        return configured == null || configured.trim().isEmpty()
                ? java.nio.file.Paths.get(System.getProperty("user.home"), fallbackName)
                        .toAbsolutePath().normalize()
                : java.nio.file.Paths.get(configured).toAbsolutePath().normalize();
    }

    boolean isInstalled() {
        return Files.isRegularFile(installRoot.resolve("loader.jar"));
    }

    boolean isLunarRunning() throws IOException, InterruptedException {
        String script = "$p=@(Get-CimInstance Win32_Process -ErrorAction SilentlyContinue | ? {"
                + "$_.Name -eq 'Lunar Client.exe' -or "
                + "(($_.Name -eq 'java.exe' -or $_.Name -eq 'javaw.exe') -and "
                + "$_.CommandLine -match '(?i)lunarclient|\\.lunarclient')}); "
                + "if($p.Count -gt 0){exit 10}else{exit 0}";
        return runPowerShell(script) == 10;
    }

    void closeLunar() throws IOException, InterruptedException {
        String script = "$p=@(Get-CimInstance Win32_Process -ErrorAction SilentlyContinue | ? {"
                + "$_.Name -eq 'Lunar Client.exe' -or "
                + "(($_.Name -eq 'java.exe' -or $_.Name -eq 'javaw.exe') -and "
                + "$_.CommandLine -match '(?i)lunarclient|\\.lunarclient')}); "
                + "$p | % { Stop-Process -Id $_.ProcessId -Force -ErrorAction SilentlyContinue }; "
                + "Start-Sleep -Milliseconds 500; exit 0";
        if (runPowerShell(script) != 0) {
            throw new IOException("Could not close Lunar Client.");
        }
    }

    InstallSettings loadSettings() {
        InstallSettings settings = InstallSettings.defaults();
        try {
            Properties blockhit = readProperties(installRoot.resolve("config")
                    .resolve("blockhit-sound.properties"));
            String sound = blockhit.getProperty("sound", settings.blockhitSound).trim();
            settings.blockhitSound = "anvil_place".equalsIgnoreCase(sound)
                    ? InstallSettings.DEFAULT_SOUND : sound;
            settings.blockhitPitch = parsePitch(
                    blockhit.getProperty("pitch"), settings.blockhitPitch);

            Properties endstone = readProperties(installRoot.resolve("config")
                    .resolve("endstone-mod.properties"));
            settings.endstoneMode = validEndstoneMode(
                    endstone.getProperty("mode", settings.endstoneMode));
        } catch (IOException ignored) {
            return InstallSettings.defaults();
        }
        return settings;
    }

    void install(ReleaseManifest manifest, Set<String> selectedMods,
                 InstallSettings settings, Progress progress)
            throws Exception {
        Set<String> components = resolveComponents(manifest, selectedMods);
        List<ReleaseManifest.FileInfo> selectedFiles = new ArrayList<ReleaseManifest.FileInfo>();
        for (ReleaseManifest.FileInfo file : manifest.files) {
            if ("core".equals(file.component) || components.contains(file.component)) {
                selectedFiles.add(file);
            }
        }
        if (selectedFiles.isEmpty()) {
            throw new IOException("The release manifest contains no installable files.");
        }

        Files.createDirectories(installRoot);
        List<InstalledFile> installed = new ArrayList<InstalledFile>();
        int index = 0;
        for (ReleaseManifest.FileInfo file : selectedFiles) {
            index++;
            int percent = 5 + (int) ((index - 1) * 75.0 / selectedFiles.size());
            progress.update(percent, "Downloading " + file.id + "...");
            progress.log("Downloading " + file.id);
            byte[] bytes = ManifestClient.download(file.url, 15_000, 120_000);
            String actualHash = sha256(bytes);
            if (!actualHash.equalsIgnoreCase(file.sha256)) {
                throw new SecurityException("SHA-256 mismatch for " + file.id);
            }
            if (file.size > 0 && bytes.length != file.size) {
                throw new SecurityException("Size mismatch for " + file.id);
            }
            Path destination = safeDestination(file.destination);
            Files.createDirectories(destination.getParent());
            Path temporary = destination.resolveSibling("." + destination.getFileName()
                    + ".installing-" + System.nanoTime());
            try {
                Files.write(temporary, bytes, StandardOpenOption.CREATE_NEW);
                moveAtomically(temporary, destination);
            } finally {
                Files.deleteIfExists(temporary);
            }
            installed.add(new InstalledFile(file.destination, file.sha256, file.component));
        }

        removeDeselected(manifest, selectedFiles);
        saveSettings(selectedMods, settings);
        progress.update(85, "Configuring Lunar 1.8.9...");
        profiles.configure(installRoot, lunarRoot, false);

        InstalledRecord record = new InstalledRecord();
        record.releaseVersion = manifest.releaseVersion;
        record.installerVersion = manifest.installerVersion;
        record.lunarDataRoot = lunarRoot.toString();
        record.selectedMods = new ArrayList<String>(selectedMods);
        Collections.sort(record.selectedMods);
        record.files = installed;
        byte[] recordBytes = new GsonBuilder().setPrettyPrinting().create()
                .toJson(record).getBytes(StandardCharsets.UTF_8);
        Files.write(installRoot.resolve("installed-release.json"), recordBytes,
                StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        progress.update(100, "Installation complete");
        progress.log("Installed Weave " + manifest.releaseVersion + " successfully.");
    }

    void saveSettings(Set<String> selectedMods, InstallSettings settings)
            throws IOException {
        if (selectedMods.contains("blockhit-sound")) {
            String sound = settings.blockhitSound == null
                    ? "" : settings.blockhitSound.trim();
            if (!sound.matches("[a-zA-Z0-9_.:-]+")) {
                throw new IOException("Choose a valid Minecraft sound event.");
            }
            double pitch = parsePitch(Double.toString(settings.blockhitPitch),
                    InstallSettings.DEFAULT_PITCH);
            Path file = installRoot.resolve("config").resolve("blockhit-sound.properties");
            Properties properties = readProperties(file);
            properties.setProperty("sound", sound);
            properties.setProperty("pitch", String.format(Locale.ROOT, "%.2f", pitch));
            writeProperties(file, properties, "Blockhit Sound Config");
        }
        if (selectedMods.contains("endstone-mod")) {
            Path file = installRoot.resolve("config").resolve("endstone-mod.properties");
            Properties properties = readProperties(file);
            properties.setProperty("mode", validEndstoneMode(settings.endstoneMode));
            writeProperties(file, properties, "Endstone Mod Config");
        }
    }

    private static Properties readProperties(Path file) throws IOException {
        Properties properties = new Properties();
        if (Files.isRegularFile(file)) {
            try (InputStream input = Files.newInputStream(file)) {
                properties.load(input);
            }
        }
        return properties;
    }

    private static void writeProperties(Path file, Properties properties, String heading)
            throws IOException {
        Files.createDirectories(file.getParent());
        Path temporary = file.resolveSibling("." + file.getFileName()
                + ".installing-" + System.nanoTime());
        try {
            try (OutputStream output = Files.newOutputStream(temporary,
                    StandardOpenOption.CREATE_NEW)) {
                properties.store(output, heading);
            }
            moveAtomically(temporary, file);
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    private static double parsePitch(String value, double fallback) {
        if (value == null) {
            return fallback;
        }
        try {
            double parsed = Double.parseDouble(value);
            return Double.isFinite(parsed) && parsed >= 0.5D && parsed <= 2.0D
                    ? parsed : fallback;
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private static String validEndstoneMode(String mode) {
        if (InstallSettings.ENDSTONE_RADIUS_3.equals(mode)
                || InstallSettings.ENDSTONE_ALWAYS.equals(mode)) {
            return mode;
        }
        return InstallSettings.ENDSTONE_RADIUS_5;
    }

    void uninstall(Progress progress) throws Exception {
        progress.update(10, "Removing Lunar JVM argument...");
        if (Files.isRegularFile(lunarRoot.resolve("db").resolve("profiles.db"))) {
            profiles.configure(installRoot, lunarRoot, true);
        }
        List<String> destinations = readInstalledDestinations();
        if (destinations.isEmpty()) {
            Collections.addAll(destinations,
                    "loader.jar", "mods/1.8.9/blockhit-sound.jar",
                    "mods/1.8.9/endstone-mod.jar", "mods/1.8.9/hud-editor.jar",
                    "mods/1.8.9/spawnprot-mod.jar", "mods/1.8.9/timer.jar",
                    "mods/1.8.9/weave-disclosure.jar");
        }
        int index = 0;
        for (String destination : destinations) {
            index++;
            progress.update(20 + (int) (70.0 * index / destinations.size()),
                    "Removing managed files...");
            Files.deleteIfExists(safeDestination(destination));
            Files.deleteIfExists(safeDestination(destination + ".sha256"));
        }
        Files.deleteIfExists(installRoot.resolve("installed-release.json"));
        progress.update(100, "Uninstall complete");
        progress.log("Weave was removed. Settings and backups were preserved.");
    }

    private Set<String> resolveComponents(ReleaseManifest manifest, Set<String> selected) {
        Set<String> result = new HashSet<String>(selected);
        boolean changed;
        do {
            changed = false;
            for (ReleaseManifest.ModInfo mod : manifest.mods) {
                if (result.contains(mod.id) && mod.dependencies != null) {
                    for (String dependency : mod.dependencies) {
                        changed |= result.add(dependency);
                    }
                }
            }
        } while (changed);
        return result;
    }

    private void removeDeselected(ReleaseManifest manifest,
                                  List<ReleaseManifest.FileInfo> selectedFiles) throws IOException {
        Set<String> selected = new HashSet<String>();
        for (ReleaseManifest.FileInfo file : selectedFiles) {
            selected.add(file.destination.replace('\\', '/'));
        }
        for (ReleaseManifest.FileInfo file : manifest.files) {
            if (file.destination != null
                    && !selected.contains(file.destination.replace('\\', '/'))) {
                Files.deleteIfExists(safeDestination(file.destination));
            }
        }
    }

    private List<String> readInstalledDestinations() {
        Path recordPath = installRoot.resolve("installed-release.json");
        if (!Files.isRegularFile(recordPath)) {
            return new ArrayList<String>();
        }
        try (BufferedReader reader = Files.newBufferedReader(recordPath, StandardCharsets.UTF_8)) {
            InstalledRecord record = new Gson().fromJson(reader, InstalledRecord.class);
            List<String> result = new ArrayList<String>();
            if (record != null && record.files != null) {
                for (InstalledFile file : record.files) {
                    if (file.path != null) {
                        result.add(file.path);
                    }
                }
            }
            return result;
        } catch (RuntimeException | IOException ignored) {
            return new ArrayList<String>();
        }
    }

    private Path safeDestination(String relative) throws IOException {
        if (relative == null || relative.trim().isEmpty()) {
            throw new IOException("Manifest destination is empty.");
        }
        Path destination = installRoot.resolve(relative.replace('/', java.io.File.separatorChar))
                .normalize().toAbsolutePath();
        if (!destination.startsWith(installRoot)) {
            throw new IOException("Unsafe manifest destination: " + relative);
        }
        return destination;
    }

    private static String sha256(byte[] bytes) throws Exception {
        byte[] digest = MessageDigest.getInstance("SHA-256").digest(bytes);
        StringBuilder text = new StringBuilder(64);
        for (byte value : digest) {
            text.append(String.format(Locale.ROOT, "%02x", value & 255));
        }
        return text.toString();
    }

    private static void moveAtomically(Path source, Path destination) throws IOException {
        try {
            Files.move(source, destination, StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException ignored) {
            Files.move(source, destination, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static int runPowerShell(String script) throws IOException, InterruptedException {
        Process process = new ProcessBuilder("powershell.exe", "-NoLogo", "-NoProfile",
                "-NonInteractive", "-ExecutionPolicy", "Bypass", "-Command", script)
                .redirectErrorStream(true).start();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
            while (reader.readLine() != null) {
                // Drain output so PowerShell cannot block on a full pipe.
            }
        }
        return process.waitFor();
    }

    static final class InstalledRecord {
        String releaseVersion;
        String installerVersion;
        String lunarDataRoot;
        List<String> selectedMods;
        List<InstalledFile> files;
    }

    static final class InstalledFile {
        String path;
        String sha256;
        String component;

        InstalledFile(String path, String sha256, String component) {
            this.path = path;
            this.sha256 = sha256;
            this.component = component;
        }
    }
}
