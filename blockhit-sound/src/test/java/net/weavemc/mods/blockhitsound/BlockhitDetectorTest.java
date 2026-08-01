package net.weavemc.mods.blockhitsound;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class BlockhitDetectorTest {
    @Test
    public void requiresAnActiveSwordBlock() {
        BlockhitDetector detector = new BlockhitDetector();
        assertFalse(detector.recordLocalHurt(1_000L));
        detector.updateSwordBlocking(true);
        assertFalse(detector.recordLocalHurt(1_000L));
        assertTrue(detector.recordLocalVelocity(2_000L));
        detector.updateSwordBlocking(false);
        assertFalse(detector.recordLocalHurt(2_000L));
    }

    @Test
    public void playsOnlyOnceForDuplicateHurtSignals() {
        BlockhitDetector detector = new BlockhitDetector();
        detector.updateSwordBlocking(true);
        long firstHit = 5_000_000_000L;
        assertFalse(detector.recordLocalVelocity(firstHit));
        assertTrue(detector.recordLocalHurt(firstHit + 1_000_000L));
        assertFalse(detector.recordLocalHurt(firstHit + 1_000_000L));
        assertFalse(detector.recordLocalVelocity(firstHit + 2_000_000L));
        long secondHit = firstHit + BlockhitDetector.DUPLICATE_WINDOW_NANOS;
        assertFalse(detector.recordLocalHurt(secondHit));
        assertTrue(detector.recordLocalVelocity(secondHit + 1_000_000L));
    }

    @Test
    public void rejectsEnvironmentalHurtWithoutCombatVelocity() {
        BlockhitDetector detector = new BlockhitDetector();
        detector.updateSwordBlocking(true);
        assertFalse(detector.recordLocalHurt(1_000_000_000L));
        assertFalse(detector.recordLocalVelocity(
                1_000_000_000L + BlockhitDetector.COMBAT_SIGNAL_WINDOW_NANOS + 1L));
    }

    @Test
    public void resetClearsBlockingState() {
        BlockhitDetector detector = new BlockhitDetector();
        detector.updateSwordBlocking(true);
        detector.reset();
        assertFalse(detector.recordLocalHurt(10_000L));
    }
}
