package com.spawnprot.mod;

import com.weave.lunarmods.HudAnchor;
import com.weave.lunarmods.HudRenderer;
import com.weave.lunarmods.LunarMod;
import com.weave.lunarmods.LunarMods;
import com.weave.lunarmods.Slider;
import net.weavemc.mods.servercontext.ServerContext;

import java.util.function.Consumer;

/** The SpawnProt tile in Lunar's mod menu. Its on/off switch enables the whole mod. */
final class SpawnProtSettings {
    static LunarMod tile;
    static Slider<Float> respawnSeconds;
    static Slider<Float> spawnProtSeconds;

    private SpawnProtSettings() {
    }

    /** Claims the tile; must run from the mod's init() so the tile exists before Lunar's menu is built. */
    static void register(HudRenderer renderer, LegacyConfig legacy) {
        tile = LunarMods.mod("spawnprot-mod");
        respawnSeconds = tile.slider("respawn-seconds", "Respawn Time (s)", 3.0F, 0.5F, 10.0F)
                .onChange(new Consumer<Float>() {
                    @Override
                    public void accept(Float seconds) {
                        SpawnProtState.respawnMs = toMillis(seconds);
                    }
                });
        spawnProtSeconds = tile.slider("spawn-prot-seconds", "Spawn Protection Time (s)", 2.5F, 0.5F, 10.0F)
                .onChange(new Consumer<Float>() {
                    @Override
                    public void accept(Float seconds) {
                        SpawnProtState.spawnProtMs = toMillis(seconds);
                    }
                });
        tile.hud(renderer).position(HudAnchor.TOP_LEFT, 10.0F, 10.0F);
        tile.onDisable(new Runnable() {
            @Override
            public void run() {
                ServerContext.runOnClientThread(new Runnable() {
                    @Override
                    public void run() {
                        SpawnProtState.clearAll();
                    }
                });
            }
        });

        // Values set before Lunar registers the tile are kept unless Lunar already has saved ones.
        Long legacyRespawnMs = legacy.longValue("respawn-ms");
        if (legacyRespawnMs != null) {
            respawnSeconds.set(legacyRespawnMs / 1000.0F);
        }
        Long legacySpawnProtMs = legacy.longValue("spawn-prot-ms");
        if (legacySpawnProtMs != null) {
            spawnProtSeconds.set(legacySpawnProtMs / 1000.0F);
        }
        SpawnProtState.respawnMs = toMillis(respawnSeconds.get());
        SpawnProtState.spawnProtMs = toMillis(spawnProtSeconds.get());
    }

    static boolean isEnabled() {
        return tile != null && tile.isEnabled();
    }

    private static long toMillis(Float seconds) {
        return Math.round(seconds * 1000.0);
    }
}
