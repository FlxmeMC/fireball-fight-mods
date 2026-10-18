package net.weavemc.mods.endstone;

/** The Radius setting. Constant names are what the library saves, so keep them stable. */
enum EndstoneMode {
    RADIUS_3("3 blocks", EndstoneFeature.MODE_RADIUS_3),
    RADIUS_5("5 blocks", EndstoneFeature.MODE_RADIUS_5),
    ALWAYS("Always", EndstoneFeature.MODE_ALWAYS);

    private final String label;
    /** The feature's mode id, also the value the old properties file stored. */
    final String featureMode;

    EndstoneMode(String label, String featureMode) {
        this.label = label;
        this.featureMode = featureMode;
    }

    /** Parses a mode id from the old properties file, or null when it is missing or unknown. */
    static EndstoneMode fromFeatureMode(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        for (EndstoneMode mode : values()) {
            if (mode.featureMode.equals(trimmed)) {
                return mode;
            }
        }
        return null;
    }

    @Override
    public String toString() {
        return label;
    }
}
