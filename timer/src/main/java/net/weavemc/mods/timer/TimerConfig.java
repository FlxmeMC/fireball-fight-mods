package net.weavemc.mods.timer;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Properties;

final class TimerConfig {
    static final float DEFAULT_X = 10.0F;
    static final float DEFAULT_Y = 10.0F;
    static final float DEFAULT_SCALE = 2.0F;
    static final float MIN_SCALE = 0.5F;
    static final float MAX_SCALE = 5.0F;

    private final Path file;
    private float x = DEFAULT_X;
    private float y = DEFAULT_Y;
    private float scale = DEFAULT_SCALE;

    private TimerConfig(Path file) {
        this.file = file;
    }

    static TimerConfig loadDefault() {
        return load(Paths.get(System.getProperty("user.home"), ".weave", "config", "timer.properties"));
    }

    static TimerConfig load(Path file) {
        TimerConfig config = new TimerConfig(file);
        if (!Files.isRegularFile(file)) {
            return config;
        }
        Properties properties = new Properties();
        try (InputStream input = Files.newInputStream(file)) {
            properties.load(input);
            config.x = nonNegative(properties.getProperty("x"), DEFAULT_X);
            config.y = nonNegative(properties.getProperty("y"), DEFAULT_Y);
            config.scale = clamp(parseFinite(properties.getProperty("scale"), DEFAULT_SCALE));
        } catch (IOException | IllegalArgumentException error) {
            System.err.println("[Timer] Ignoring invalid config: " + error.getMessage());
        }
        return config;
    }

    synchronized void save() {
        Properties properties = new Properties();
        properties.setProperty("x", Float.toString(x));
        properties.setProperty("y", Float.toString(y));
        properties.setProperty("scale", Float.toString(scale));
        Path parent = file.getParent();
        Path temporary = file.resolveSibling(file.getFileName() + ".tmp");
        try {
            if (parent != null) {
                Files.createDirectories(parent);
            }
            try (OutputStream output = Files.newOutputStream(temporary)) {
                properties.store(output, "Timer HUD");
            }
            try {
                Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING,
                        StandardCopyOption.ATOMIC_MOVE);
            } catch (IOException atomicMoveUnsupported) {
                Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException error) {
            System.err.println("[Timer] Failed to save config: " + error.getMessage());
            try {
                Files.deleteIfExists(temporary);
            } catch (IOException ignored) {
                // Best-effort cleanup.
            }
        }
    }

    synchronized float getX() {
        return x;
    }

    synchronized float getY() {
        return y;
    }

    synchronized float getScale() {
        return scale;
    }

    synchronized void setPosition(float newX, float newY) {
        if (Float.isFinite(newX) && Float.isFinite(newY)) {
            x = Math.max(0.0F, newX);
            y = Math.max(0.0F, newY);
        }
    }

    synchronized void setScale(float newScale) {
        if (Float.isFinite(newScale)) {
            scale = clamp(newScale);
        }
    }

    synchronized void reset() {
        x = DEFAULT_X;
        y = DEFAULT_Y;
        scale = DEFAULT_SCALE;
    }

    private static float nonNegative(String value, float fallback) {
        return Math.max(0.0F, parseFinite(value, fallback));
    }

    private static float parseFinite(String value, float fallback) {
        if (value == null) {
            return fallback;
        }
        float parsed = Float.parseFloat(value);
        if (!Float.isFinite(parsed)) {
            throw new IllegalArgumentException("non-finite number");
        }
        return parsed;
    }

    private static float clamp(float value) {
        return Math.max(MIN_SCALE, Math.min(MAX_SCALE, value));
    }
}
