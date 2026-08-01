package com.spawnprot.mod;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class SpawnProtStateTest {
    @Before
    public void setUp() {
        SpawnProtState.setEnabled(true);
        SpawnProtState.clearAll();
    }

    @After
    public void tearDown() {
        SpawnProtState.setEnabled(true);
        SpawnProtState.clearAll();
        SpawnProtState.resetHud();
    }

    @Test
    public void hitOnlyCancelsActiveSpawnProtection() {
        SpawnProtState.onPlayerDeath("Alex");
        List<SpawnProtState.TrackedPlayer> players = SpawnProtState.getActiveTrackedPlayers();
        assertEquals(1, players.size());

        SpawnProtState.endProtectionAfterHit("Alex", "Steve");
        assertEquals(1, SpawnProtState.getActiveTrackedPlayers().size());

        players.get(0).phase = SpawnProtState.Phase.SPAWN_PROT;
        SpawnProtState.endProtectionAfterHit("aLeX", "Steve");
        assertTrue(SpawnProtState.getActiveTrackedPlayers().isEmpty());
    }

    @Test
    public void hitCancelsLocalSpawnProtection() {
        SpawnProtState.onSelfRespawnComplete();
        assertTrue(SpawnProtState.hasSelfSpawnProt());
        SpawnProtState.endProtectionAfterHit("Steve", "steve");
        assertFalse(SpawnProtState.hasSelfSpawnProt());
    }

    @Test
    public void toggleDisablesAndClearsTracking() {
        SpawnProtState.onPlayerDeath("Alex");
        SpawnProtState.setEnabled(false);
        assertFalse(SpawnProtState.isEnabled());
        assertTrue(SpawnProtState.getActiveTrackedPlayers().isEmpty());
    }

    @Test
    public void toggleStatePersistsAcrossReloads() throws Exception {
        assertTrue(SpawnProtState.verifyEnabledPersistence());
    }

    @Test
    public void lateTickKeepsOriginalAbsoluteTimeline() {
        long originalRespawn = SpawnProtState.respawnMs;
        long originalProtection = SpawnProtState.spawnProtMs;
        try {
            SpawnProtState.respawnMs = 3000L;
            SpawnProtState.spawnProtMs = 2500L;
            long death = System.currentTimeMillis() - 4000L;

            SpawnProtState.onPlayerDeath("Alex", false, death);
            SpawnProtState.tickAt(death + 4000L);

            SpawnProtState.TrackedPlayer alex =
                    SpawnProtState.getActiveTrackedPlayers().get(0);
            assertEquals(SpawnProtState.Phase.SPAWN_PROT, alex.phase);
            assertEquals(death + 3000L, alex.phaseStartMs);
            assertEquals(1.5, SpawnProtState.getSecondsRemaining(
                    alex, death + 4000L), 0.001);

            SpawnProtState.tickAt(death + 5500L);
            assertTrue(SpawnProtState.getActiveTrackedPlayers().isEmpty());
        } finally {
            SpawnProtState.respawnMs = originalRespawn;
            SpawnProtState.spawnProtMs = originalProtection;
        }
    }

    @Test
    public void staleHitCannotCancelProtectionThatStartedLater() {
        long originalRespawn = SpawnProtState.respawnMs;
        try {
            SpawnProtState.respawnMs = 1000L;
            long death = System.currentTimeMillis() - 1500L;
            SpawnProtState.onPlayerDeath("Alex", false, death);
            SpawnProtState.tickAt(death + 1500L);

            SpawnProtState.endProtectionAfterHitIfGeneration(
                    SpawnProtState.getWorldGeneration(), "Alex", "Steve", death + 500L);
            assertEquals(1, SpawnProtState.getActiveTrackedPlayers().size());

            SpawnProtState.endProtectionAfterHitIfGeneration(
                    SpawnProtState.getWorldGeneration(), "Alex", "Steve", death + 1200L);
            assertTrue(SpawnProtState.getActiveTrackedPlayers().isEmpty());
        } finally {
            SpawnProtState.respawnMs = originalRespawn;
        }
    }

    @Test
    public void tracksMultiplePlayersAtTheSameTime() {
        long now = System.currentTimeMillis();
        SpawnProtState.onPlayerDeath("Alex", false, now - 10L);
        SpawnProtState.onPlayerDeath("Jamie", true, now);

        List<SpawnProtState.TrackedPlayer> players =
                SpawnProtState.getActiveTrackedPlayers();
        assertEquals(2, players.size());
        assertEquals("Jamie", players.get(0).name);
        assertTrue(players.get(0).teammate);
        assertEquals("Alex", players.get(1).name);
        assertFalse(players.get(1).teammate);
    }
}
