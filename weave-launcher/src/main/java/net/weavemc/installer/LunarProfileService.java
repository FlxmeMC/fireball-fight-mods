package net.weavemc.installer;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class LunarProfileService {
    private static final Pattern AGENT = Pattern.compile(
            "(?i)(?<!\\S)(?:\"-javaagent:[^\"]+\"|-javaagent:\"[^\"]+\"(?:=[^\\s]+)?"
                    + "|-javaagent:[^\\s\"]+)(?=\\s|$)");

    void configure(Path installRoot, Path lunarRoot, boolean remove) throws Exception {
        Path database = lunarRoot.resolve("db").resolve("profiles.db");
        if (!Files.isRegularFile(database)) {
            throw new IOException("Lunar profile database was not found: " + database);
        }
        Class.forName("org.sqlite.JDBC");
        String loader = installRoot.resolve("loader.jar").toAbsolutePath().normalize().toString();
        Map<String, String> updates = new LinkedHashMap<String, String>();
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + database)) {
            connection.createStatement().execute("PRAGMA busy_timeout=10000");
            try (PreparedStatement statement = connection.prepareStatement(
                    "SELECT id, COALESCE(jvm_arguments, '') FROM profiles "
                            + (remove ? "" : "WHERE type='lunar' AND game_version='1.8.9'"));
                 ResultSet result = statement.executeQuery()) {
                boolean found = false;
                while (result.next()) {
                    found = true;
                    String existing = result.getString(2);
                    String updated = removeAgent(existing, loader);
                    if (!remove) {
                        String token = loader.matches(".*\\s+.*")
                                ? "-javaagent:\"" + loader + "\"" : "-javaagent:" + loader;
                        updated = updated.isEmpty() ? token : updated + " " + token;
                    }
                    if (!updated.equals(existing)) {
                        updates.put(result.getString(1), updated);
                    }
                }
                if (!found && !remove) {
                    throw new IOException("A Lunar Minecraft 1.8.9 profile was not found.");
                }
            }
        }

        if (updates.isEmpty()) {
            return;
        }
        backup(database, installRoot);
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + database)) {
            connection.createStatement().execute("PRAGMA busy_timeout=10000");
            connection.setAutoCommit(false);
            try (PreparedStatement statement = connection.prepareStatement(
                    "UPDATE profiles SET jvm_arguments=? WHERE id=?")) {
                for (Map.Entry<String, String> update : updates.entrySet()) {
                    if (update.getValue().isEmpty()) {
                        statement.setNull(1, java.sql.Types.VARCHAR);
                    } else {
                        statement.setString(1, update.getValue());
                    }
                    statement.setString(2, update.getKey());
                    statement.executeUpdate();
                }
                connection.commit();
            } catch (SQLException exception) {
                connection.rollback();
                throw exception;
            }
        }
    }

    static String removeAgent(String arguments, String loader) {
        if (arguments == null || arguments.trim().isEmpty()) {
            return "";
        }
        Matcher matcher = AGENT.matcher(arguments);
        StringBuffer cleaned = new StringBuffer();
        while (matcher.find()) {
            String path = matcher.group().replace("\"", "").substring("-javaagent:".length());
            int options = path.indexOf('=');
            if (options >= 0) path = path.substring(0, options);
            path = path.replace('\\', '/').toLowerCase(Locale.ROOT);
            boolean managed = path.equals(loader.replace('\\', '/').toLowerCase(Locale.ROOT))
                    || path.endsWith("/.weave/loader.jar")
                    || path.matches(".*/\\.weave/weave-loader-agent-[0-9.]+\\.jar");
            matcher.appendReplacement(cleaned, managed ? "" : Matcher.quoteReplacement(matcher.group()));
        }
        matcher.appendTail(cleaned);
        return cleaned.toString().trim();
    }

    private static void backup(Path database, Path installRoot) throws IOException {
        String stamp = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss'Z'")
                .withZone(ZoneOffset.UTC).format(Instant.now());
        Path directory = installRoot.resolve("backups").resolve("lunar-profile-" + stamp);
        Files.createDirectories(directory);
        for (String suffix : new String[]{"", "-wal", "-shm"}) {
            Path source = database.resolveSibling(database.getFileName().toString() + suffix);
            if (Files.isRegularFile(source)) {
                Files.copy(source, directory.resolve(source.getFileName()),
                        StandardCopyOption.REPLACE_EXISTING);
            }
        }
    }
}
