package net.weavemc.mods.blockhitsound;

import com.weave.lunarmods.Dropdown;
import com.weave.lunarmods.LunarMod;
import com.weave.lunarmods.LunarMods;
import com.weave.lunarmods.Slider;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/** The Blockhit Sound tile in Lunar's mod menu. Its on/off switch enables the whole mod. */
final class BlockhitSettings {
    static final String DEFAULT_SOUND = "random.anvil_land";
    private static final String CATALOG = "/minecraft-1.8.9-sounds.txt";

    static LunarMod tile;
    static Dropdown sound;
    static Slider<Float> volume;
    static Slider<Float> pitch;

    private BlockhitSettings() {
    }

    /**
     * Claims the tile; must run from the mod's init() so the tile exists before Lunar's menu is
     * built. Old saved values are applied before registration, where Lunar's own saved values win.
     */
    static void register(LegacyConfig.Values legacy, Runnable testSound) {
        List<String> choices = loadCatalog();
        if (legacy != null && !choices.contains(legacy.sound)
                && legacy.sound.matches("[a-zA-Z0-9_.:-]+")) {
            choices.add(legacy.sound);
        }

        tile = LunarMods.mod("blockhit-sound");
        sound = tile.dropdown("sound", "Sound", DEFAULT_SOUND, choices.toArray(new String[0]));
        volume = tile.slider("volume", "Volume", 1.0F, 0.0F, 1.0F);
        pitch = tile.slider("pitch", "Pitch", 2.0F, 0.5F, 2.0F);
        tile.button("test", "Play Test Sound", testSound);

        if (legacy != null) {
            sound.set(legacy.sound);
            volume.set(legacy.volume);
            pitch.set(legacy.pitch);
        }
    }

    static boolean isEnabled() {
        return tile != null && tile.isEnabled();
    }

    static List<String> loadCatalog() {
        List<String> sounds = new ArrayList<String>();
        InputStream input = BlockhitSettings.class.getResourceAsStream(CATALOG);
        if (input != null) {
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(input, StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    String name = line.trim();
                    if (!name.isEmpty() && !sounds.contains(name)) {
                        sounds.add(name);
                    }
                }
            } catch (IOException error) {
                System.err.println("[Blockhit Sound] Failed to read sound list: " + error.getMessage());
            }
        }
        if (!sounds.contains(DEFAULT_SOUND)) {
            sounds.add(0, DEFAULT_SOUND);
        }
        return sounds;
    }
}
