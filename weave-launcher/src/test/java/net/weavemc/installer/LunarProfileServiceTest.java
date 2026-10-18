package net.weavemc.installer;

import org.junit.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public final class LunarProfileServiceTest {
    @Test
    public void removesQuotedAndUnquotedWeaveAgentsButPreservesOtherArguments() {
        String loader = "C:\\Users\\PC\\.weave\\loader.jar";
        assertEquals("-Xmx4G", LunarProfileService.removeAgent(
                "-Xmx4G \"-javaagent:" + loader + "\"", loader));
        assertEquals("-Xmx4G", LunarProfileService.removeAgent(
                "-javaagent:" + loader + " -Xmx4G", loader));
        assertEquals("-Dexample=true", LunarProfileService.removeAgent(
                "-Dexample=true -javaagent:C:\\Users\\Other\\.weave\\loader.jar", loader));
        assertEquals("-javaagent:C:/other/agent.jar -Dlabel=\"two  spaces\"", LunarProfileService.removeAgent(
                "-javaagent:C:/Users/PC/.weave/Weave-Loader-Agent-1.3.3.jar "
                        + "-javaagent:C:/other/agent.jar -Dlabel=\"two  spaces\"", loader));
        assertEquals("", LunarProfileService.removeAgent("-javaagent:C:/Users/PC/.weave/loader.jar=debug", loader));
    }

    @Test
    public void configuresAndRemovesAgentInARealSqliteProfile() throws Exception {
        Path root = Files.createTempDirectory("weave-profile-test");
        try {
            Path install = root.resolve(".weave");
            Path lunar = root.resolve(".lunarclient");
            Files.createDirectories(install);
            Files.createDirectories(lunar.resolve("db"));
            Files.createFile(install.resolve("loader.jar"));
            Path database = lunar.resolve("db").resolve("profiles.db");
            Class.forName("org.sqlite.JDBC");
            try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + database)) {
                connection.createStatement().execute("CREATE TABLE profiles ("
                        + "id TEXT PRIMARY KEY, path TEXT, type TEXT, game_version TEXT, jvm_arguments TEXT)");
                connection.createStatement().execute("INSERT INTO profiles VALUES ("
                        + "'test','1.8','lunar','1.8.9','-Xmx4G')");
            }
            LunarProfileService service = new LunarProfileService();
            service.configure(install, lunar, false);
            String configured = readArguments(database);
            assertEquals("-Xmx4G -javaagent:" + install.resolve("loader.jar"), configured);
            service.configure(install, lunar, true);
            assertEquals("-Xmx4G", readArguments(database));
        } finally {
            deleteTree(root);
        }
    }

    private static String readArguments(Path database) throws Exception {
        return readArguments(database, "test");
    }

    private static String readArguments(Path database, String id) throws Exception {
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + database);
             ResultSet result = connection.createStatement().executeQuery(
                     "SELECT jvm_arguments FROM profiles WHERE id='" + id + "'")) {
            result.next();
            return result.getString(1);
        }
    }

    @Test
    public void handlesCustomProfilesAndUninstallsEvenWithoutAStandardProfile() throws Exception {
        Path root = Files.createTempDirectory("weave profiles ");
        try {
            Path lunar = root.resolve("lunar");
            Files.createDirectories(lunar.resolve("db"));
            Path database = lunar.resolve("db/profiles.db");
            Path install = root.resolve("weave");
            Class.forName("org.sqlite.JDBC");
            try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + database)) {
                connection.createStatement().execute("CREATE TABLE profiles ("
                        + "id TEXT PRIMARY KEY, path TEXT, type TEXT, game_version TEXT, jvm_arguments TEXT)");
                connection.createStatement().execute("INSERT INTO profiles VALUES "
                        + "('test','1.8','lunar','1.8.9','-Xmx4G'),"
                        + "('custom','pvp','lunar','1.8.9','-Dlabel=\"two  spaces\"'),"
                        + "('other','1.21','lunar','1.21','-Xmx2G')");
            }
            LunarProfileService service = new LunarProfileService();
            service.configure(install, lunar, false);
            assertTrue(readArguments(database).contains("-javaagent:\""));
            String custom = readArguments(database, "custom");
            assertTrue(custom.startsWith("-Dlabel=\"two  spaces\" -javaagent:\""));
            assertEquals("-Xmx2G", readArguments(database, "other"));
            service.configure(install, lunar, false);
            assertEquals(custom, readArguments(database, "custom"));
            try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + database)) {
                connection.createStatement().execute("DELETE FROM profiles WHERE id='test'");
                connection.createStatement().execute("UPDATE profiles SET game_version='1.7.10' WHERE id='custom'");
            }
            service.configure(install, lunar, true);
            assertEquals("-Dlabel=\"two  spaces\"", readArguments(database, "custom"));
            assertEquals("-Xmx2G", readArguments(database, "other"));
        } finally {
            deleteTree(root);
        }
    }

    private static void deleteTree(Path root) throws Exception {
        if (!Files.exists(root)) {
            return;
        }
        try (java.util.stream.Stream<Path> paths = Files.walk(root)) {
            paths.sorted(java.util.Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (java.io.IOException exception) {
                    throw new java.io.UncheckedIOException(exception);
                }
            });
        }
    }
}
