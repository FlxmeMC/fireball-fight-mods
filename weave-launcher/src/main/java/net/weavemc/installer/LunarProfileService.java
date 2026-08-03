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
import java.util.regex.Pattern;

final class LunarProfileService {
    private static final Pattern DEFAULT_AGENT = Pattern.compile(
            "(?i)(?<!\\S)(?:\"-javaagent:[^\"]*[/\\\\]\\.weave[/\\\\]loader\\.jar\""
                    + "|-javaagent:\"[^\"]*[/\\\\]\\.weave[/\\\\]loader\\.jar\""
                    + "|-javaagent:[^\\s\"]*[/\\\\]\\.weave[/\\\\]loader\\.jar)(?=\\s|$)");

    void configure(Path installRoot, Path lunarRoot, boolean remove) throws Exception {
        Path database = lunarRoot.resolve("db").resolve("profiles.db");
        if (!Files.isRegularFile(database)) {
            throw new IOException("Lunar profile database was not found: " + database);
        }
        Class.forName("org.sqlite.JDBC");
        String profileId;
        String existing;
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + database)) {
            connection.createStatement().execute("PRAGMA busy_timeout=10000");
            try (PreparedStatement statement = connection.prepareStatement(
                    "SELECT id, COALESCE(jvm_arguments, '') FROM profiles "
                            + "WHERE type='lunar' AND game_version='1.8.9' "
                            + "ORDER BY CASE WHEN path='1.8' THEN 0 ELSE 1 END LIMIT 1");
                 ResultSet result = statement.executeQuery()) {
                if (!result.next()) {
                    throw new IOException("Lunar's standard Minecraft 1.8.9 profile was not found.");
                }
                profileId = result.getString(1);
                existing = result.getString(2);
            }
        }

        String loader = installRoot.resolve("loader.jar").toAbsolutePath().normalize().toString();
        String updated = removeAgent(existing, loader);
        if (!remove) {
            String token = loader.matches(".*\\s+.*")
                    ? "-javaagent:\"" + loader + "\"" : "-javaagent:" + loader;
            updated = updated.isEmpty() ? token : updated + " " + token;
        }
        if (updated.equals(existing)) {
            return;
        }
        backup(database, installRoot);
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + database)) {
            connection.setAutoCommit(false);
            try (PreparedStatement statement = connection.prepareStatement(
                    "UPDATE profiles SET jvm_arguments=? WHERE id=?")) {
                if (updated.isEmpty()) {
                    statement.setNull(1, java.sql.Types.VARCHAR);
                } else {
                    statement.setString(1, updated);
                }
                statement.setString(2, profileId);
                statement.executeUpdate();
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
        String exact = "(?i)(?<!\\S)(?:\"-javaagent:" + Pattern.quote(loader) + "\""
                + "|-javaagent:\"" + Pattern.quote(loader) + "\""
                + "|-javaagent:" + Pattern.quote(loader) + ")(?=\\s|$)";
        String cleaned = arguments.replaceAll(exact, " ");
        cleaned = DEFAULT_AGENT.matcher(cleaned).replaceAll(" ");
        return cleaned.trim().replaceAll("\\s+", " ");
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
