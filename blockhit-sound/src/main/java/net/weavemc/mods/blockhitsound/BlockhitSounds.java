package net.weavemc.mods.blockhitsound;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/** Registry of named presets with support for every valid Minecraft sound event ID. */
public final class BlockhitSounds {
    public static final String DEFAULT_KEY = "anvil_place";
    private static final Map<String, SoundDefinition> SOUNDS =
            new LinkedHashMap<String, SoundDefinition>();

    static {
        // block.anvil.place is the modern name requested by the user. Minecraft
        // 1.8.9 exposes the same anvil placement/landing sample as
        // random.anvil_land; the modern ID is rejected as an unknown event.
        register(new SoundDefinition(DEFAULT_KEY, "random.anvil_land", 1.0F, 2.0F));
    }

    private BlockhitSounds() {
    }

    public static synchronized void register(SoundDefinition sound) {
        if (sound == null) {
            throw new IllegalArgumentException("sound");
        }
        SOUNDS.put(normalize(sound.getKey()), sound);
    }

    public static synchronized SoundDefinition resolve(String key) {
        String eventName = key == null ? "" : key.trim();
        String normalized = normalize(key);
        SoundDefinition sound = SOUNDS.get(normalized);
        if (sound != null) {
            return sound;
        }
        if (eventName.isEmpty() || !eventName.matches("[a-zA-Z0-9_.:-]+")) {
            return SOUNDS.get(DEFAULT_KEY);
        }
        return new SoundDefinition(eventName, eventName, 1.0F, 1.0F);
    }

    private static String normalize(String key) {
        return key == null ? "" : key.trim().toLowerCase(Locale.ROOT);
    }
}
