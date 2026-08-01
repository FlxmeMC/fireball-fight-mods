package net.weavemc.mods.endstone;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public final class EndstoneConfigStoreTest {
    @Rule
    public final TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void persistsEnabledStateAcrossReloads() throws Exception {
        Path file = temporaryFolder.newFolder("config").toPath().resolve("endstone.properties");
        EndstoneConfigStore store = new EndstoneConfigStore(file);

        assertFalse(store.loadEnabled());
        store.saveEnabled(true);
        assertTrue(new EndstoneConfigStore(file).loadEnabled());
        store.saveEnabled(false);
        assertFalse(new EndstoneConfigStore(file).loadEnabled());
    }

    @Test
    public void invalidValueUsesDisabledDefault() throws Exception {
        Path file = temporaryFolder.newFolder("invalid").toPath().resolve("endstone.properties");
        Properties properties = new Properties();
        properties.setProperty("enabled", "maybe");
        try (OutputStream output = Files.newOutputStream(file)) {
            properties.store(output, "test");
        }

        assertFalse(new EndstoneConfigStore(file).loadEnabled());
    }

    @Test
    public void preservesInstallerSelectedModeWhenSavingToggle() throws Exception {
        Path file = temporaryFolder.newFolder("mode").toPath().resolve("endstone.properties");
        Properties properties = new Properties();
        properties.setProperty("mode", EndstoneFeature.MODE_ALWAYS);
        try (OutputStream output = Files.newOutputStream(file)) {
            properties.store(output, "test");
        }

        EndstoneConfigStore store = new EndstoneConfigStore(file);
        store.saveEnabled(true);
        assertEquals(EndstoneFeature.MODE_ALWAYS,
                new EndstoneConfigStore(file).loadMode());
    }
}
