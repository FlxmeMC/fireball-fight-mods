package net.weavemc.mods.blockhitsound;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Properties;

final class BlockhitConfig {
    private final Path file;
    private final SoundDefinition sound;
    private final float volume;
    private final float pitch;

    private BlockhitConfig(Path file, SoundDefinition sound, float volume, float pitch) {
        this.file = file;
        this.sound = sound;
        this.volume = volume;
        this.pitch = pitch;
    }

    static BlockhitConfig loadDefault() {
        return load(Paths.get(System.getProperty("user.home"), ".weave", "config",
                "blockhit-sound.properties"));
    }

    static BlockhitConfig load(Path file) {
        Properties properties = new Properties();
        if (Files.isRegularFile(file)) {
            try (InputStream input = Files.newInputStream(file)) {
                properties.load(input);
            } catch (IOException error) {
                System.err.println("[Blockhit Sound] Failed to load config: " + error.getMessage());
            }
        }
        String configuredSound = properties.getProperty("sound", BlockhitSounds.DEFAULT_KEY);
        boolean legacyDefault = "anvil_fall".equalsIgnoreCase(configuredSound);
        SoundDefinition sound = BlockhitSounds.resolve(configuredSound);
        float volume = positive(properties.getProperty("volume"), sound.getDefaultVolume());
        float pitch = legacyDefault
                ? sound.getDefaultPitch()
                : positive(properties.getProperty("pitch"), sound.getDefaultPitch());
        return new BlockhitConfig(file, sound, volume, pitch);
    }

    void save() {
        Properties properties = new Properties();
        properties.setProperty("sound", sound.getKey());
        properties.setProperty("volume", Float.toString(volume));
        properties.setProperty("pitch", Float.toString(pitch));
        Path parent = file.getParent();
        Path temporary = file.resolveSibling(file.getFileName() + ".tmp");
        try {
            if (parent != null) {
                Files.createDirectories(parent);
            }
            try (OutputStream output = Files.newOutputStream(temporary)) {
                properties.store(output, "Blockhit Sound Config");
            }
            try {
                Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING,
                        StandardCopyOption.ATOMIC_MOVE);
            } catch (IOException atomicMoveUnsupported) {
                Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException error) {
            System.err.println("[Blockhit Sound] Failed to save config: " + error.getMessage());
        }
    }

    SoundDefinition getSound() {
        return sound;
    }

    float getVolume() {
        return volume;
    }

    float getPitch() {
        return pitch;
    }

    private static float positive(String value, float fallback) {
        if (value == null) {
            return fallback;
        }
        try {
            float parsed = Float.parseFloat(value);
            return Float.isFinite(parsed) && parsed > 0.0F ? parsed : fallback;
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }
}
