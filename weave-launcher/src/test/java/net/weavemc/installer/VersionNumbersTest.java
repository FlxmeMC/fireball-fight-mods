package net.weavemc.installer;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public final class VersionNumbersTest {
    @Test
    public void comparesSemanticVersionsNumerically() {
        assertTrue(VersionNumbers.compare("1.10.0", "1.9.0") > 0);
        assertTrue(VersionNumbers.compare("1.0.1", "1.0.0") > 0);
        assertTrue(VersionNumbers.compare("1.0.0", "1.0.1") < 0);
    }

    @Test
    public void treatsMissingAndLeadingZeroComponentsAsEquivalent() {
        assertEquals(0, VersionNumbers.compare("v1.0", "1.0.0"));
        assertEquals(0, VersionNumbers.compare("1.01.0", "1.1"));
    }
}
