package net.weavemc.mods.blockhitsound;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Properties;

/**
 * One-time import of the old ~/.weave/config/blockhit-sound.properties into the Lunar tile. The
 * file is renamed once the tile has been registered, so the import does not repeat.
 */
final class LegacyConfig {
    static final class Values {
        final String sound;
        final float volume;
        final float pitch;

        Values(String sound, float volume, float pitch) {
            this.sound = sound;
            this.volume = volume;
            this.pitch = pitch;
        }
    }

    private final Path file;

    LegacyConfig(Path file) {
        this.file = file;
    }

    static LegacyConfig atDefaultLocation() {
        return new LegacyConfig(Paths.get(System.getProperty("user.home"), ".weave", "config",
                "blockhit-sound.properties"));
    }

    /** The saved values, or null when there is no old config file. */
    Values read() {
        if (!Files.isRegularFile(file)) {
            return null;
        }
        Properties properties = new Properties();
        try (InputStream input = Files.newInputStream(file)) {
            properties.load(input);
        } catch (IOException error) {
            System.err.println("[Blockhit Sound] Failed to read old config: " + error.getMessage());
            return null;
        }
        return translate(properties);
    }

    /** Converts old values, including named presets, into a raw sound event, volume and pitch. */
    static Values translate(Properties properties) {
        String configuredSound = properties.getProperty("sound", BlockhitSounds.DEFAULT_KEY);
        // "anvil_fall" was the original default key; it meant the default preset at its own pitch.
        boolean legacyDefault = "anvil_fall".equalsIgnoreCase(configuredSound.trim());
        SoundDefinition sound = BlockhitSounds.resolve(
                legacyDefault ? BlockhitSounds.DEFAULT_KEY : configuredSound);
        float volume = positive(properties.getProperty("volume"), sound.getDefaultVolume());
        float pitch = legacyDefault
                ? sound.getDefaultPitch()
                : positive(properties.getProperty("pitch"), sound.getDefaultPitch());
        return new Values(sound.getEventName(), volume, pitch);
    }

    /** Renames the old file so the import does not run again. */
    void retire() {
        if (!Files.isRegularFile(file)) {
            return;
        }
        try {
            Files.move(file, file.resolveSibling(file.getFileName() + ".migrated"),
                    StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException error) {
            System.err.println("[Blockhit Sound] Failed to retire old config: " + error.getMessage());
        }
    }

    private static float positive(String value, float fallback) {
        if (value == null) {
            return fallback;
        }
        try {
            float parsed = Float.parseFloat(value.trim());
            return !Float.isNaN(parsed) && !Float.isInfinite(parsed) && parsed > 0.0F ? parsed : fallback;
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }
}
