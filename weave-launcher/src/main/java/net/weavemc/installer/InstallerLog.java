package net.weavemc.installer;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Small append-only log under the Weave install root. The Logs window and its
 * "Copy all logs" button pick it up, so users can share the real failure reason.
 * Logging never throws.
 */
final class InstallerLog {
    private static final long MAX_BYTES = 512L * 1024L;
    private static final Object LOCK = new Object();

    private InstallerLog() {
    }

    static Path file() {
        String configured = System.getProperty("weave.installRoot");
        Path root = configured == null || configured.trim().isEmpty()
                ? Paths.get(System.getProperty("user.home"), ".weave")
                : Paths.get(configured);
        return root.toAbsolutePath().normalize().resolve("logs").resolve("fireball-fight-mods.log");
    }

    static void info(String message) {
        write("INFO", message, null);
    }

    static void error(String message, Throwable error) {
        write("ERROR", message, error);
    }

    private static void write(String level, String message, Throwable error) {
        StringBuilder line = new StringBuilder();
        line.append(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.ROOT).format(new Date()))
                .append(' ').append(level).append(' ').append(message)
                .append(System.lineSeparator());
        if (error != null) {
            StringWriter trace = new StringWriter();
            error.printStackTrace(new PrintWriter(trace));
            line.append(trace.toString());
        }
        synchronized (LOCK) {
            try {
                Path file = file();
                Files.createDirectories(file.getParent());
                if (Files.isRegularFile(file) && Files.size(file) > MAX_BYTES) {
                    Files.write(file, new byte[0]);
                }
                Files.write(file, line.toString().getBytes(StandardCharsets.UTF_8),
                        StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            } catch (IOException | RuntimeException ignored) {
                // Logging must never break the installer.
            }
        }
    }
}
