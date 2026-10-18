package net.weavemc.installer;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
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
import java.util.LinkedHashSet;
import java.util.Locale;
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
        if (Files.isRegularFile(installRoot.resolve("loader.jar"))
                || Files.isRegularFile(installRoot.resolve("installed-release.json"))) return true;
        try {
            return !ManagedMods.destinations(installRoot).isEmpty();
        } catch (IOException exception) {
            return false;
        }
    }

    boolean isLunarRunning() throws IOException, InterruptedException {
        String script = "$ErrorActionPreference='Stop'; $p=@(Get-CimInstance Win32_Process | ? {"
                + "$_.Name -eq 'Lunar Client.exe' -or "
                + "(($_.Name -eq 'java.exe' -or $_.Name -eq 'javaw.exe') -and "
                + "$_.CommandLine -match '(?i)lunarclient|\\.lunarclient')}); "
                + "if($p.Count -gt 0){exit 10}else{exit 0}";
        int exit = runPowerShell(script);
        if (exit != 0 && exit != 10) throw new IOException("Could not check whether Lunar Client is running.");
        return exit == 10;
    }

    void closeLunar() throws IOException, InterruptedException {
        String script = "$ErrorActionPreference='Stop'; $p=@(Get-CimInstance Win32_Process | ? {"
                + "$_.Name -eq 'Lunar Client.exe' -or "
                + "(($_.Name -eq 'java.exe' -or $_.Name -eq 'javaw.exe') -and "
                + "$_.CommandLine -match '(?i)lunarclient|\\.lunarclient')}); "
                + "$p | % { Stop-Process -Id $_.ProcessId -Force -ErrorAction SilentlyContinue }; "
                + "Start-Sleep -Milliseconds 500; exit 0";
        if (runPowerShell(script) != 0) {
            throw new IOException("Could not close Lunar Client.");
        }
        for (int attempt = 0; attempt < 10; attempt++) {
            if (!isLunarRunning()) return;
            Thread.sleep(500);
        }
        throw new IOException("Lunar Client is still running. Close the launcher and game before continuing.");
    }

    void install(ReleaseManifest manifest, Set<String> selectedMods, Progress progress)
            throws Exception {
        List<String> previousDestinations = readInstalledDestinations();
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
            String action = manifest.localDirectory == null ? "Downloading " : "Installing local ";
            progress.update(percent, action + file.id + "...");
            progress.log(action + file.id);
            byte[] bytes = manifest.localDirectory == null
                    ? ManifestClient.download(file.url, 15_000, 120_000)
                    : LocalRelease.readArtifact(manifest, file);
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
        removeObsolete(manifest, previousDestinations);
        Set<String> retained = new HashSet<String>();
        for (ReleaseManifest.FileInfo file : selectedFiles) {
            retained.add(normalizeDestination(file.destination));
        }
        for (String previous : ManagedMods.destinations(installRoot)) {
            if (!retained.contains(previous)) deleteManagedFile(previous);
        }
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

    void uninstall(Progress progress) throws Exception {
        progress.update(10, "Removing Lunar JVM argument...");
        if (Files.isRegularFile(lunarRoot.resolve("db").resolve("profiles.db"))) {
            profiles.configure(installRoot, lunarRoot, true);
        }
        Set<String> destinations = new LinkedHashSet<String>(readInstalledDestinations());
        destinations.add("loader.jar");
        destinations.addAll(ManagedMods.destinations(installRoot));
        int index = 0;
        for (String destination : destinations) {
            index++;
            progress.update(20 + (int) (70.0 * index / destinations.size()),
                    "Removing managed files...");
            // Install records from older installers may also list settings or helper files.
            if ("loader.jar".equals(destination) || isRemovableModFile(destination)) {
                deleteManagedFile(destination);
            }
        }
        Files.deleteIfExists(installRoot.resolve("installed-release.json"));
        progress.update(100, "Uninstall complete");
        progress.log("Weave was removed. Settings and backups were preserved.");
    }

    private void deleteManagedFile(String destination) throws IOException {
        Files.deleteIfExists(safeDestination(destination));
        Files.deleteIfExists(safeDestination(destination + ".sha256"));
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
                deleteManagedFile(file.destination);
            }
        }
    }

    /**
     * Removes mod jars an earlier release installed that the new release no longer ships at all,
     * such as a retired library, so they stop loading. Only jars (and their checksums) under
     * mods/ are touched; config and mod data are never removed.
     */
    private void removeObsolete(ReleaseManifest manifest, List<String> previousDestinations)
            throws IOException {
        Set<String> shipped = new HashSet<String>();
        for (ReleaseManifest.FileInfo file : manifest.files) {
            if (file.destination != null) {
                shipped.add(normalizeDestination(file.destination));
            }
        }
        for (String previous : previousDestinations) {
            String normalized = normalizeDestination(previous);
            if (shipped.contains(normalized) || !isRemovableModFile(normalized)) {
                continue;
            }
            Path destination;
            try {
                destination = safeDestination(normalized);
            } catch (IOException unsafe) {
                continue;
            }
            Files.deleteIfExists(destination);
            if (normalized.toLowerCase(Locale.ROOT).endsWith(".jar")) {
                Files.deleteIfExists(safeDestination(normalized + ".sha256"));
            }
        }
    }

    static boolean isRemovableModFile(String destination) {
        String normalized = normalizeDestination(destination);
        if (!normalized.startsWith("mods/") || normalized.contains("..")) {
            return false;
        }
        String lower = normalized.toLowerCase(Locale.ROOT);
        return lower.endsWith(".jar") || lower.endsWith(".jar.sha256");
    }

    private static String normalizeDestination(String destination) {
        String normalized = destination.trim().replace('\\', '/');
        while (normalized.startsWith("./")) {
            normalized = normalized.substring(2);
        }
        return normalized;
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
