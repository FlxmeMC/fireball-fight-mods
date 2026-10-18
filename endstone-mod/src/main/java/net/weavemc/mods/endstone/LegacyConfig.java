package net.weavemc.mods.endstone;

import com.weave.lunarmods.LunarMods;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Properties;

/**
 * The properties file settings lived in before they moved to Lunar's mod menu. Its values are
 * imported once; after a session where the tile reached Lunar's menu the file is renamed so the
 * import does not run again.
 */
final class LegacyConfig {
    private final File file;
    private final Properties properties = new Properties();

    private LegacyConfig(File file) {
        this.file = file;
    }

    static LegacyConfig read(File file) {
        LegacyConfig config = new LegacyConfig(file);
        if (file.isFile()) {
            try (FileInputStream input = new FileInputStream(file)) {
                config.properties.load(input);
            } catch (IOException exception) {
                System.err.println("[Endstone Mod] Could not read old config " + file + ": " + exception.getMessage());
            }
        }
        return config;
    }

    boolean exists() {
        return file.isFile();
    }

    String get(String key) {
        return properties.getProperty(key);
    }

    /** Renames the file once its values are safely in Lunar's settings. */
    void retireIfImported() {
        if (!exists() || !LunarMods.isInLunarMenu()) {
            return;
        }
        File migrated = new File(file.getPath() + ".migrated");
        if (migrated.exists() && !migrated.delete()) {
            return;
        }
        if (!file.renameTo(migrated)) {
            System.err.println("[Endstone Mod] Could not rename old config " + file);
        }
    }
}
