package net.weavemc.mods.fireballhitboxes;

import com.weave.lunarmods.LunarMod;
import com.weave.lunarmods.LunarMods;

/** The Fireball Hitboxes tile in Lunar's mod menu. It has no settings beyond its on/off switch. */
final class FireballHitboxesSettings {
    private static LunarMod tile;

    private FireballHitboxesSettings() {
    }

    /** Claims the tile; must run from the mod's init() so the tile exists before Lunar's menu is built. */
    static void register() {
        tile = LunarMods.mod("fireball-hitboxes");
    }

    static boolean isEnabled() {
        return tile != null && tile.isEnabled();
    }
}
