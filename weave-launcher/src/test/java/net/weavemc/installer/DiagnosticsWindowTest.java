package net.weavemc.installer;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public final class DiagnosticsWindowTest {
    @Rule
    public final TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void combinesWeaveLunarAuditAndCrashLogs() throws Exception {
        Path home = temporaryFolder.newFolder("home").toPath();
        Path weave = home.resolve(".weave");
        Path lunar = home.resolve(".lunarclient");
        write(weave.resolve("logs/latest.log"), "weave loader line\n");
        write(weave.resolve("audit/session.json"), "{\"status\":\"stopped\"}\n");
        write(lunar.resolve("logs/launcher/main.log"), "lunar launcher line\n");
        write(lunar.resolve("offline/multiver/logs/latest.log"), "minecraft line\n");
        write(home.resolve("hs_err_pid42.log"), "fatal jvm line\n");

        DiagnosticsWindow.Snapshot snapshot =
                DiagnosticsWindow.collect(home, weave, lunar);

        assertEquals(5, snapshot.fileCount);
        assertTrue(snapshot.text.contains("weave loader line"));
        assertTrue(snapshot.text.contains("\"status\":\"stopped\""));
        assertTrue(snapshot.text.contains("lunar launcher line"));
        assertTrue(snapshot.text.contains("minecraft line"));
        assertTrue(snapshot.text.contains("fatal jvm line"));
    }

    private static void write(Path path, String text) throws Exception {
        Files.createDirectories(path.getParent());
        Files.write(path, text.getBytes(StandardCharsets.UTF_8));
    }
}
