package net.weavemc.installer;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

/** Only this pack's binaries in directories actually searched by the 1.8.9 loader. */
final class ManagedMods {
    private static final Set<String> IDS = new HashSet<String>(Arrays.asList(
            "blockhit-sound", "endstone-mod", "hud-editor", "spawnprot-mod",
            "spawn-protection-tracker", "timer", "server-context", "fireball-hitboxes",
            "fireball-indicator", "lunar-mod-api", "weave-disclosure"));

    private ManagedMods() { }

    static Set<String> destinations(Path root) throws IOException {
        Set<String> result = new LinkedHashSet<String>();
        for (String relative : new String[] {"mods", "mods/1.8.9"}) {
            Path directory = root.resolve(relative);
            if (!Files.isDirectory(directory, LinkOption.NOFOLLOW_LINKS)
                    || !directory.toRealPath().startsWith(root.toRealPath())) {
                continue;
            }
            try (DirectoryStream<Path> files = Files.newDirectoryStream(directory, "*.jar")) {
                for (Path file : files) {
                    if (Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS) && belongsToPack(file)) {
                        result.add(relative + "/" + file.getFileName());
                    }
                }
            }
        }
        return result;
    }

    private static boolean belongsToPack(Path file) {
        String name = file.getFileName().toString();
        // Also recognize damaged canonical files, so an interrupted install can be removed.
        if (IDS.contains(name.substring(0, name.length() - 4))) {
            return true;
        }
        // Older/manual installs can use versioned or renamed jars. Never guess from a prefix.
        try (JarFile jar = new JarFile(file.toFile())) {
            JarEntry entry = jar.getJarEntry("weave.mod.json");
            if (entry == null) return false;
            try (Reader reader = new InputStreamReader(jar.getInputStream(entry), StandardCharsets.UTF_8)) {
                JsonObject config = JsonParser.parseReader(reader).getAsJsonObject();
                return config.has("modId") && IDS.contains(config.get("modId").getAsString());
            }
        } catch (IOException | RuntimeException ignored) {
            return false;
        }
    }
}
