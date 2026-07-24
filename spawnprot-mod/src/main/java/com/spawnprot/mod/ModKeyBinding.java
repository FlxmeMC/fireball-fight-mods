package com.spawnprot.mod;

import net.minecraft.client.Minecraft;
import net.minecraft.client.settings.KeyBinding;
import org.lwjgl.input.Keyboard;

import java.util.Arrays;

final class ModKeyBinding {
    private static final String CATEGORY = "Weave Mods";

    private ModKeyBinding() {
    }

    static KeyBinding tryRegister(String description) {
        Minecraft minecraft = Minecraft.getMinecraft();
        if (minecraft == null || minecraft.gameSettings == null) {
            return null;
        }
        KeyBinding binding = new KeyBinding(description, Keyboard.KEY_NONE, CATEGORY);
        KeyBinding[] existing = minecraft.gameSettings.keyBindings;
        KeyBinding[] expanded = Arrays.copyOf(existing, existing.length + 1);
        expanded[existing.length] = binding;
        minecraft.gameSettings.keyBindings = expanded;
        minecraft.gameSettings.loadOptions();
        KeyBinding.resetKeyBindingArrayAndHash();
        return binding;
    }
}
