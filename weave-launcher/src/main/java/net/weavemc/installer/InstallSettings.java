package net.weavemc.installer;

final class InstallSettings {
    static final String DEFAULT_SOUND = "random.anvil_land";
    static final double DEFAULT_PITCH = 2.0D;
    static final String ENDSTONE_RADIUS_3 = "radius_3";
    static final String ENDSTONE_RADIUS_5 = "radius_5";
    static final String ENDSTONE_ALWAYS = "always";

    String blockhitSound;
    double blockhitPitch;
    String endstoneMode;

    InstallSettings(String blockhitSound, double blockhitPitch, String endstoneMode) {
        this.blockhitSound = blockhitSound;
        this.blockhitPitch = blockhitPitch;
        this.endstoneMode = endstoneMode;
    }

    static InstallSettings defaults() {
        return new InstallSettings(DEFAULT_SOUND, DEFAULT_PITCH, ENDSTONE_RADIUS_5);
    }
}
