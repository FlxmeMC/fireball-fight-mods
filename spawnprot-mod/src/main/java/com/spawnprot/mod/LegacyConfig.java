package com.spawnprot.mod;

import com.weave.lunarmods.LunarMods;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.Properties;

/**
 * The settings file used before SpawnProt moved into Lunar's mod menu. Its values are imported
 * once, and the file is renamed after a session in which Lunar took them over.
 */
final class LegacyConfig {
    private final File file;
    private final Properties properties = new Properties();

    private LegacyConfig(File file) {
        this.file = file;
        if (!file.isFile()) {
            return;
        }
        try (FileInputStream input = new FileInputStream(file)) {
            properties.load(input);
        } catch (IOException exception) {
            System.err.println("[SpawnProt] Could not read old config: " + exception.getMessage());
        }
    }

    static LegacyConfig spawnProt() {
        File weaveHome = new File(System.getProperty("user.home"), ".weave");
        return new LegacyConfig(new File(weaveHome, "config/spawnprot/config.properties"));
    }

    Long longValue(String key) {
        String value = properties.getProperty(key);
        if (value == null) {
            return null;
        }
        try {
            return Long.parseLong(value.trim());
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    /** Renames the old file once Lunar holds the imported values; outside Lunar it is kept for next time. */
    void retireIfImported() {
        if (!file.isFile() || !LunarMods.isInLunarMenu()) {
            return;
        }
        try {
            Files.move(file.toPath(), new File(file.getPath() + ".migrated").toPath(),
                    StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException exception) {
            System.err.println("[SpawnProt] Could not retire old config: " + exception.getMessage());
        }
    }
}
