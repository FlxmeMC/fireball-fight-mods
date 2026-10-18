package net.weavemc.mods.endstone;

import com.weave.lunarmods.Choice;
import com.weave.lunarmods.LunarMod;
import com.weave.lunarmods.LunarMods;

/** The End Stone Glass tile in Lunar's mod menu. Its on/off switch enables the whole mod. */
final class EndstoneSettings {
    static LunarMod tile;
    static Choice<EndstoneMode> mode;

    private EndstoneSettings() {
    }

    /** Claims the tile; must run from the mod's init() so the tile exists before Lunar's menu is built. */
    static void register() {
        tile = LunarMods.mod("endstone-mod");
        mode = tile.choice("mode", "Radius", EndstoneMode.RADIUS_5);
    }
}
