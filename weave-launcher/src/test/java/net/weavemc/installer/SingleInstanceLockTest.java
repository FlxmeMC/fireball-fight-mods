package net.weavemc.installer;

import org.junit.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

public final class SingleInstanceLockTest {
    @Test
    public void rejectsSecondInstanceAndAllowsOneAfterRelease() throws Exception {
        Path directory = Files.createTempDirectory("fireball-fight-mods-lock-test");
        Path path = directory.resolve("installer.lock");

        SingleInstanceLock first = SingleInstanceLock.acquire(path);
        assertNotNull(first);
        try {
            assertNull(SingleInstanceLock.acquire(path));
        } finally {
            first.close();
        }

        SingleInstanceLock replacement = SingleInstanceLock.acquire(path);
        assertNotNull(replacement);
        replacement.close();
    }
}
