package net.weavemc.mods.endstone.mixin;

import net.minecraft.client.gui.GuiScreen;
import net.weavemc.mods.endstone.EndstoneMod;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GuiScreen.class)
public abstract class GuiScreenCommandMixin {
    @Inject(method = "sendChatMessage(Ljava/lang/String;Z)V", at = @At("HEAD"), cancellable = true)
    private void endstone$handleLocalCommand(String message, boolean addToChat, CallbackInfo callback) {
        if (EndstoneMod.handleLocalCommand(message)) {
            callback.cancel();
        }
    }
}
