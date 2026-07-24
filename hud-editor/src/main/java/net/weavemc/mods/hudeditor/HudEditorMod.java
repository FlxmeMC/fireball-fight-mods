package net.weavemc.mods.hudeditor;

import net.minecraft.client.Minecraft;
import net.minecraft.client.settings.KeyBinding;
import net.weavemc.api.ModInitializer;
import net.weavemc.api.event.EventBus;
import net.weavemc.api.event.SubscribeEvent;
import net.weavemc.api.event.TickEvent;

public final class HudEditorMod implements ModInitializer {
    private KeyBinding editHudKey;

    @Override
    public void init() {
        EventBus.subscribe(this);
        System.out.println("[HUD Editor] initialized");
    }

    @SubscribeEvent
    public void onTick(TickEvent.Pre event) {
        if (editHudKey == null) {
            editHudKey = ModKeyBinding.tryRegister("Edit HUD");
        }

        Minecraft minecraft = Minecraft.getMinecraft();
        if (editHudKey != null) {
            boolean pressed = false;
            while (editHudKey.isPressed()) {
                pressed = true;
            }
            if (pressed && minecraft != null && minecraft.currentScreen == null) {
                HudElementRegistry.requestOpen();
            }
        }

        if (minecraft != null && minecraft.currentScreen == null
                && HudElementRegistry.consumeOpenRequest()) {
            minecraft.displayGuiScreen(new HudEditorScreen(HudElementRegistry.snapshot()));
        }
    }
}
