package net.weavemc.installer;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

final class MinecraftSoundCatalog {
    private MinecraftSoundCatalog() {
    }

    static List<String> load() {
        InputStream input = MinecraftSoundCatalog.class.getResourceAsStream(
                "/minecraft-1.8.9-sounds.txt");
        if (input == null) {
            return Collections.singletonList(InstallSettings.DEFAULT_SOUND);
        }
        List<String> sounds = new ArrayList<String>();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(input, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String sound = line.trim();
                if (!sound.isEmpty()) {
                    sounds.add(sound);
                }
            }
        } catch (IOException ignored) {
            return Collections.singletonList(InstallSettings.DEFAULT_SOUND);
        }
        return sounds.isEmpty()
                ? Collections.singletonList(InstallSettings.DEFAULT_SOUND)
                : Collections.unmodifiableList(sounds);
    }
}
