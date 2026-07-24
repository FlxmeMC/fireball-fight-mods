package net.weavemc.mods.blockhitsound;

public final class SoundDefinition {
    private final String key;
    private final String eventName;
    private final float defaultVolume;
    private final float defaultPitch;

    public SoundDefinition(String key, String eventName, float defaultVolume, float defaultPitch) {
        if (isBlank(key) || isBlank(eventName)) {
            throw new IllegalArgumentException("Sound key and event name are required");
        }
        this.key = key;
        this.eventName = eventName;
        this.defaultVolume = positive(defaultVolume, "defaultVolume");
        this.defaultPitch = positive(defaultPitch, "defaultPitch");
    }

    public String getKey() {
        return key;
    }

    public String getEventName() {
        return eventName;
    }

    public float getDefaultVolume() {
        return defaultVolume;
    }

    public float getDefaultPitch() {
        return defaultPitch;
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private static float positive(float value, String name) {
        if (!Float.isFinite(value) || value <= 0.0F) {
            throw new IllegalArgumentException(name);
        }
        return value;
    }
}
