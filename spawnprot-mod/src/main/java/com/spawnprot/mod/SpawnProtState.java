package com.spawnprot.mod;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.regex.Pattern;

public final class SpawnProtState {
    static final float MIN_HUD_SCALE = 0.5F;
    static final float MAX_HUD_SCALE = 5.0F;
    private static final float DEFAULT_HUD_X = 10.0F;
    private static final float DEFAULT_HUD_Y = 10.0F;
    private static final float DEFAULT_HUD_SCALE = 2.0F;

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

        TrackedPlayer(String name, boolean teammate, Phase phase,
                      long phaseStartMs, long phaseEndMs, long deathTimeMs) {
            this.name = name;
            this.teammate = teammate;
            this.phase = phase;
            this.phaseStartMs = phaseStartMs;
            this.phaseEndMs = phaseEndMs;
            this.deathTimeMs = deathTimeMs;
        }
    }

    private static final Pattern COLOR_CODE_PATTERN = Pattern.compile("(?i)\u00A7[0-9A-FK-OR]");

    public static long respawnMs = 3000L;
    public static long spawnProtMs = 2500L;
    public static float hudX = DEFAULT_HUD_X;
    public static float hudY = DEFAULT_HUD_Y;
    public static float hudScale = DEFAULT_HUD_SCALE;

    private static final Map<String, TrackedPlayer> TRACKED_PLAYERS =
            new LinkedHashMap<String, TrackedPlayer>();
    private static boolean selfRespawning;
    private static long selfRespawnEndMs;
    private static boolean selfSpawnProtActive;
    private static long selfSpawnProtStartMs;
    private static long selfSpawnProtEndMs;
    private static boolean enabled = true;
    private static int worldGeneration;
    private static File configFile;

    private SpawnProtState() {
    }

    public static synchronized void onPlayerDeath(String playerName) {
        onPlayerDeath(playerName, false, System.currentTimeMillis());
    }

    public static synchronized void onPlayerDeath(
            String playerName, boolean teammate, long deathTimeMs) {
        if (playerName == null || playerName.isEmpty()) {
            return;
        }
        long now = Math.min(deathTimeMs, System.currentTimeMillis());
        TRACKED_PLAYERS.put(playerName.toLowerCase(Locale.ROOT), new TrackedPlayer(
                playerName, teammate, Phase.RESPAWNING, now, now + respawnMs, now));
    }

    public static synchronized void onSelfDeathCanRespawn() {
        onSelfDeathCanRespawn(System.currentTimeMillis());
    }

    public static synchronized void onSelfDeathCanRespawn(long deathTimeMs) {
        if (selfRespawning) {
            return;
        }
        long now = Math.min(deathTimeMs, System.currentTimeMillis());
        selfRespawning = true;
        selfRespawnEndMs = now + respawnMs;
        selfSpawnProtActive = false;
        selfSpawnProtStartMs = 0L;
        selfSpawnProtEndMs = 0L;
    }

    public static synchronized void onSelfDeathFinal() {
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

    public static boolean isEnabled() {
        return enabled;
    }

    public static void setEnabled(boolean value) {
        enabled = value;
        if (!enabled) {
            clearAll();
        }
    }

    static void resetHud() {
        hudX = DEFAULT_HUD_X;
        hudY = DEFAULT_HUD_Y;
        hudScale = DEFAULT_HUD_SCALE;
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

    public static void loadConfig() {
        File weaveHome = new File(System.getProperty("user.home"), ".weave");
        loadConfig(new File(weaveHome, "config/spawnprot"));
    }

    static void loadConfig(File dataDirectory) {
        configFile = new File(dataDirectory, "config.properties");
        if (!configFile.isFile()) {
            return;
        }
        Properties properties = new Properties();
        try (FileInputStream input = new FileInputStream(configFile)) {
            properties.load(input);
            respawnMs = Long.parseLong(properties.getProperty("respawn-ms", "3000"));
            spawnProtMs = Long.parseLong(properties.getProperty("spawn-prot-ms", "2500"));
            hudX = Float.parseFloat(properties.getProperty("hud-x", "10"));
            hudY = Float.parseFloat(properties.getProperty("hud-y", "10"));
            hudScale = Float.parseFloat(properties.getProperty("hud-scale", "2.0"));
            enabled = Boolean.parseBoolean(properties.getProperty("enabled", "true"));
            System.out.println("[SpawnProt] Loaded configuration");
        } catch (Exception exception) {
            System.err.println("[SpawnProt] Failed to load config: " + exception.getMessage());
        }
    }

    public static void saveConfig() {
        if (configFile == null) {
            return;
        }
        Properties properties = new Properties();
        properties.setProperty("respawn-ms", String.valueOf(respawnMs));
        properties.setProperty("spawn-prot-ms", String.valueOf(spawnProtMs));
        properties.setProperty("hud-x", String.valueOf(hudX));
        properties.setProperty("hud-y", String.valueOf(hudY));
        properties.setProperty("hud-scale", String.valueOf(hudScale));
        properties.setProperty("enabled", String.valueOf(enabled));
        File parent = configFile.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            System.err.println("[SpawnProt] Failed to create config directory: " + parent);
            return;
        }
        try (FileOutputStream output = new FileOutputStream(configFile)) {
            properties.store(output, "SpawnProt Config");
        } catch (Exception exception) {
            System.err.println("[SpawnProt] Failed to save config: " + exception.getMessage());
        }
    }

    static boolean verifyEnabledPersistence() throws IOException {
        Path directory = Files.createTempDirectory("weave-spawnprot-persistence-");
        Path file = directory.resolve("config.properties");
        try {
            loadConfig(directory.toFile());
            setEnabled(false);
            saveConfig();
            setEnabled(true);
            loadConfig(directory.toFile());
            boolean disabledReloaded = !isEnabled();
            setEnabled(true);
            saveConfig();
            setEnabled(false);
            loadConfig(directory.toFile());
            return disabledReloaded && isEnabled();
        } finally {
            Files.deleteIfExists(file);
            Files.deleteIfExists(directory);
        }
    }
}
