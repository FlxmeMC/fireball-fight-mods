package net.weavemc.mods.fireballindicator;

import com.weave.lunarmods.ColorPicker;
import com.weave.lunarmods.LunarMod;
import com.weave.lunarmods.LunarMods;
import com.weave.lunarmods.Slider;

/** The Fireball Indicator tile in Lunar's mod menu. Its on/off switch enables the whole mod. */
final class FireballIndicatorSettings {
    static LunarMod tile;
    /** Ring radius in GUI pixels. The vanilla crosshair is about 7.5 from center to tip. */
    static Slider<Float> radius;
    static ColorPicker color;
    /** Distance in blocks at which a fireball's highlight is at its faintest. */
    static Slider<Integer> fadeDistance;

    private FireballIndicatorSettings() {
    }

    /** Claims the tile; must run from the mod's init() so the tile exists before Lunar's menu is built. */
    static void register() {
        tile = LunarMods.mod("fireball-indicator");
        radius = tile.slider("radius", "Ring Size", 11.0F, 6.0F, 30.0F);
        color = tile.color("color", "Highlight Color", 0xFFFF6B1A);
        fadeDistance = tile.slider("fade-distance", "Fade Distance (blocks)", 40, 10, 150);
    }

    static boolean isEnabled() {
        return tile != null && tile.isEnabled();
    }
}
