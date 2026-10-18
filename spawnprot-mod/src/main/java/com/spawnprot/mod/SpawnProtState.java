package com.spawnprot.mod;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

public final class SpawnProtState {
    // How close a death message must be to our own death to be treated as our disguise.
    static final long SELF_DEATH_MATCH_MS = 1500L;

    public enum Phase {
        RESPAWNING,
        SPAWN_PROT
    }

    public static final class TrackedPlayer {
        public final String name;
        public final boolean teammate;
        public Phase phase;
        public long phaseStartMs;
        public long phaseEndMs;
        public final long deathTimeMs;
        final boolean unseen;

        TrackedPlayer(String name, boolean teammate, Phase phase,
                      long phaseStartMs, long phaseEndMs, long deathTimeMs, boolean unseen) {
            this.name = name;
            this.teammate = teammate;
            this.phase = phase;
            this.phaseStartMs = phaseStartMs;
            this.phaseEndMs = phaseEndMs;
            this.deathTimeMs = deathTimeMs;
            this.unseen = unseen;
        }
    }

    private static final Pattern COLOR_CODE_PATTERN = Pattern.compile("(?i)\u00A7[0-9A-FK-OR]");

    /** Kept in sync with the SpawnProt tile's sliders by {@link SpawnProtSettings}. */
    public static long respawnMs = 3000L;
    public static long spawnProtMs = 2500L;

    private static final Map<String, TrackedPlayer> TRACKED_PLAYERS =
            new LinkedHashMap<String, TrackedPlayer>();
    private static boolean selfRespawning;
    private static long selfRespawnEndMs;
    private static boolean selfSpawnProtActive;
    private static long selfSpawnProtStartMs;
    private static long selfSpawnProtEndMs;
    private static long lastSelfDeathMs;
    private static int worldGeneration;

    private SpawnProtState() {
    }

    public static synchronized void onPlayerDeath(String playerName) {
        onPlayerDeath(playerName, false, System.currentTimeMillis());
    }

    public static synchronized void onPlayerDeath(
            String playerName, boolean teammate, long deathTimeMs) {
        onPlayerDeath(playerName, teammate, deathTimeMs, false);
    }

    /**
     * @param unseen true when no player entity other than the local player has this name,
     *               which is how our own disguised name looks from the client
     */
    public static synchronized void onPlayerDeath(
            String playerName, boolean teammate, long deathTimeMs, boolean unseen) {
        if (playerName == null || playerName.isEmpty()) {
            return;
        }
        long now = Math.min(deathTimeMs, System.currentTimeMillis());
        if (unseen && isNearSelfDeath(now)) {
            return;
        }
        TRACKED_PLAYERS.put(playerName.toLowerCase(Locale.ROOT), new TrackedPlayer(
                playerName, teammate, Phase.RESPAWNING, now, now + respawnMs, now, unseen));
    }

    private static boolean isNearSelfDeath(long timeMs) {
        return lastSelfDeathMs != 0L && Math.abs(timeMs - lastSelfDeathMs) <= SELF_DEATH_MATCH_MS;
    }

    private static void recordSelfDeath(long deathTimeMs) {
        lastSelfDeathMs = deathTimeMs;
        Iterator<TrackedPlayer> iterator = TRACKED_PLAYERS.values().iterator();
        while (iterator.hasNext()) {
            TrackedPlayer player = iterator.next();
            if (player.unseen && player.phase == Phase.RESPAWNING
                    && isNearSelfDeath(player.deathTimeMs)) {
                iterator.remove();
            }
        }
    }

    public static synchronized void onSelfDeathCanRespawn() {
        onSelfDeathCanRespawn(System.currentTimeMillis());
    }

    public static synchronized void onSelfDeathCanRespawn(long deathTimeMs) {
        if (selfRespawning) {
            return;
        }
        long now = Math.min(deathTimeMs, System.currentTimeMillis());
        recordSelfDeath(now);
        selfRespawning = true;
        selfRespawnEndMs = now + respawnMs;
        selfSpawnProtActive = false;
        selfSpawnProtStartMs = 0L;
        selfSpawnProtEndMs = 0L;
    }

    public static synchronized void onSelfDeathFinal() {
        onSelfDeathFinal(System.currentTimeMillis());
    }

    public static synchronized void onSelfDeathFinal(long deathTimeMs) {
        if (!selfRespawning) {
            recordSelfDeath(Math.min(deathTimeMs, System.currentTimeMillis()));
        }
        selfRespawning = false;
        selfRespawnEndMs = 0L;
        selfSpawnProtActive = false;
        selfSpawnProtStartMs = 0L;
        selfSpawnProtEndMs = 0L;
    }

    public static synchronized void onSelfRespawnComplete() {
        onSelfRespawnComplete(System.currentTimeMillis());
    }

    private static void onSelfRespawnComplete(long protectionStartMs) {
        selfRespawning = false;
        selfRespawnEndMs = 0L;
        selfSpawnProtActive = true;
        selfSpawnProtStartMs = protectionStartMs;
        selfSpawnProtEndMs = protectionStartMs + spawnProtMs;
    }

    public static synchronized void endProtectionAfterHit(String playerName, String localPlayerName) {
        endProtectionAfterHit(playerName, localPlayerName, System.currentTimeMillis());
    }

    private static void endProtectionAfterHit(
            String playerName, String localPlayerName, long hitReceivedAtMs) {
        if (playerName == null || playerName.isEmpty()) {
            return;
        }
        if (localPlayerName != null && localPlayerName.equalsIgnoreCase(playerName)
                && selfSpawnProtActive && hitReceivedAtMs >= selfSpawnProtStartMs) {
            selfSpawnProtActive = false;
            selfSpawnProtStartMs = 0L;
            selfSpawnProtEndMs = 0L;
        }
        TrackedPlayer tracked = TRACKED_PLAYERS.get(playerName.toLowerCase(Locale.ROOT));
        if (tracked != null && tracked.phase == Phase.SPAWN_PROT
                && hitReceivedAtMs >= tracked.phaseStartMs) {
            TRACKED_PLAYERS.remove(playerName.toLowerCase(Locale.ROOT));
        }
    }

    static synchronized void endProtectionAfterHitIfGeneration(
            int expectedGeneration, String playerName, String localPlayerName,
            long hitReceivedAtMs) {
        if (worldGeneration == expectedGeneration) {
            endProtectionAfterHit(playerName, localPlayerName, hitReceivedAtMs);
        }
    }

    static synchronized int getWorldGeneration() {
        return worldGeneration;
    }

    public static synchronized void tick() {
        tickAt(System.currentTimeMillis());
    }

    static synchronized void tickAt(long now) {
        Iterator<Map.Entry<String, TrackedPlayer>> iterator = TRACKED_PLAYERS.entrySet().iterator();
        while (iterator.hasNext()) {
            TrackedPlayer player = iterator.next().getValue();
            if (player.phase == Phase.RESPAWNING && now >= player.phaseEndMs) {
                player.phase = Phase.SPAWN_PROT;
                player.phaseStartMs = player.phaseEndMs;
                player.phaseEndMs = player.phaseStartMs + spawnProtMs;
            }
            if (player.phase == Phase.SPAWN_PROT && now >= player.phaseEndMs) {
                iterator.remove();
            }
        }

        if (selfRespawning && now >= selfRespawnEndMs) {
            onSelfRespawnComplete(selfRespawnEndMs);
        }
        if (selfSpawnProtActive && now >= selfSpawnProtEndMs) {
            selfSpawnProtActive = false;
            selfSpawnProtStartMs = 0L;
            selfSpawnProtEndMs = 0L;
        }
    }

    public static synchronized boolean hasSelfSpawnProt() {
        return selfSpawnProtActive;
    }

    public static synchronized double getSelfSpawnProtSecondsRemaining() {
        return selfSpawnProtActive
                ? Math.max(0.0, (selfSpawnProtEndMs - System.currentTimeMillis()) / 1000.0)
                : 0.0;
    }

    public static synchronized List<TrackedPlayer> getActiveTrackedPlayers() {
        List<TrackedPlayer> active = new ArrayList<TrackedPlayer>(TRACKED_PLAYERS.values());
        Collections.sort(active, new Comparator<TrackedPlayer>() {
            @Override
            public int compare(TrackedPlayer first, TrackedPlayer second) {
                return Long.compare(second.deathTimeMs, first.deathTimeMs);
            }
        });
        return active;
    }

    public static double getSecondsRemaining(TrackedPlayer player) {
        return getSecondsRemaining(player, System.currentTimeMillis());
    }

    static double getSecondsRemaining(TrackedPlayer player, long now) {
        return Math.max(0.0, (player.phaseEndMs - now) / 1000.0);
    }

    public static boolean shouldRender() {
        tick();
        return hasSelfSpawnProt() || !TRACKED_PLAYERS.isEmpty();
    }

    public static synchronized void clearAll() {
        TRACKED_PLAYERS.clear();
        lastSelfDeathMs = 0L;
        selfRespawning = false;
        selfRespawnEndMs = 0L;
        selfSpawnProtActive = false;
        selfSpawnProtStartMs = 0L;
        selfSpawnProtEndMs = 0L;
    }

    public static synchronized void onWorldChange() {
        clearAll();
        worldGeneration++;
    }

    public static boolean isMatchEndMessage(String formattedText) {
        if (formattedText == null) {
            return false;
        }
        return formattedText.contains("\u00A7r\u00A7e\u00A7lMatch Results\u00A7r\u00A77 (Click to view)")
                || formattedText.contains("\u00A7e forfeited.")
                || formattedText.contains("\u00A7e disconnected.");
    }

    public static boolean shouldIgnoreChat(String stripped) {
        return stripped == null || stripped.isEmpty()
                || stripped.startsWith("(From")
                || stripped.startsWith("(To")
                || stripped.contains(":");
    }

    public static String stripColorCodes(String text) {
        return text == null ? "" : COLOR_CODE_PATTERN.matcher(text).replaceAll("");
    }

    public static String formatCountdown(double seconds) {
        return String.format(Locale.ROOT, "%.1f", seconds);
    }
}
