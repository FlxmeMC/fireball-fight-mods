package net.weavemc.mods.timer;

import com.weave.lunarmods.HudAnchor;
import com.weave.lunarmods.LunarMod;
import com.weave.lunarmods.LunarMods;

/** The Timer tile in Lunar's mod menu. Its on/off switch shows or hides the timer HUD. */
final class TimerSettings {
    static LunarMod tile;

    private TimerSettings() {
    }

    /** Claims the tile; must run from the mod's init() so the tile exists before Lunar's menu is built. */
    static void register(TimerRenderer renderer) {
        tile = LunarMods.mod("timer");
        tile.hud(renderer).position(HudAnchor.TOP_CENTER, 0.0F, 10.0F);
    }
}
