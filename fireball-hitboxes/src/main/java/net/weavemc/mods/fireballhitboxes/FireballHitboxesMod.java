package net.weavemc.mods.fireballhitboxes;

import net.minecraft.client.Minecraft;
import net.minecraft.util.ChatComponentText;
import net.weavemc.api.ModInitializer;
import net.weavemc.api.event.EventBus;
import net.weavemc.api.event.SubscribeEvent;
import net.weavemc.api.event.TickEvent;
import net.weavemc.mods.servercontext.ServerContext;

public final class FireballHitboxesMod implements ModInitializer {
    private boolean warned;
    @Override public void init() {
        FireballHitboxesSettings.register();
        EventBus.subscribe(this);
        System.out.println("[Fireball Hitboxes] Registered shared F3+B/Lunar outline correction.");
    }

    @SubscribeEvent public void onTick(TickEvent.Post event) {
        if (!warned && ServerContext.isAllowed() && FireballHitboxesSettings.isEnabled()
                && !Boolean.getBoolean("weave.fireballHitboxes.rendererCompatible")) {
            // Obtaining the manager ensures its class has had a chance to pass through the hook.
            Minecraft mc = Minecraft.getMinecraft();
            if (mc.getRenderManager() != null
                    && !Boolean.getBoolean("weave.fireballHitboxes.rendererCompatible")) {
                warned = true;
                mc.thePlayer.addChatMessage(new ChatComponentText(
                        "Fireball Hitboxes is incompatible with this client renderer. Fireball outlines have not been corrected."));
            }
        }
    }
}
