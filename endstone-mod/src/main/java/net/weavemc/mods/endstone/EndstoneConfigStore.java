package net.weavemc.mods.endstone;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Properties;

final class EndstoneConfigStore {
    private static final boolean DEFAULT_ENABLED = false;
    private final Path configFile;

    EndstoneConfigStore(Path configFile) {
        this.configFile = configFile;
    }

    static EndstoneConfigStore inUserHome() {
        Path path = new java.io.File(System.getProperty("user.home"), ".weave/config")
                .toPath()
                .resolve("endstone-mod.properties");
        return new EndstoneConfigStore(path);
    }

    boolean loadEnabled() throws IOException {
        Properties properties = loadProperties();
        String value = properties.getProperty("enabled");
        if ("true".equalsIgnoreCase(value)) {
            return true;
        }
        if ("false".equalsIgnoreCase(value)) {
            return false;
        }
        return DEFAULT_ENABLED;
    }

    String loadMode() throws IOException {
        String mode = loadProperties().getProperty("mode", EndstoneFeature.MODE_RADIUS_5);
        if (EndstoneFeature.MODE_RADIUS_3.equals(mode)
                || EndstoneFeature.MODE_RADIUS_5.equals(mode)
                || EndstoneFeature.MODE_ALWAYS.equals(mode)) {
            return mode;
        }
        return EndstoneFeature.MODE_RADIUS_5;
    }

    void saveEnabled(boolean enabled) throws IOException {
        Path directory = configFile.getParent();
        if (directory == null) {
            throw new IOException("Config file must have a parent directory: " + configFile);
        }
        Files.createDirectories(directory);
        Properties properties = loadProperties();
        properties.setProperty("enabled", Boolean.toString(enabled));

        Path temporary = Files.createTempFile(directory, configFile.getFileName().toString(), ".tmp");
        try {
            try (OutputStream output = Files.newOutputStream(temporary)) {
                properties.store(output, "Endstone Mod Config");
            }
            try {
                Files.move(temporary, configFile,
                        StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException ignored) {
                Files.move(temporary, configFile, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    private Properties loadProperties() throws IOException {
        Properties properties = new Properties();
        if (Files.isRegularFile(configFile)) {
            try (InputStream input = Files.newInputStream(configFile)) {
                properties.load(input);
            }
        }
        return properties;
    }
}
