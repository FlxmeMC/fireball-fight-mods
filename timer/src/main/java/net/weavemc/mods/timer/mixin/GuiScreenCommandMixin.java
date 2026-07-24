package net.weavemc.mods.timer.mixin;

import net.minecraft.client.gui.GuiScreen;
import net.weavemc.mods.timer.TimerMod;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GuiScreen.class)
public abstract class GuiScreenCommandMixin {
    @Inject(method = "sendChatMessage(Ljava/lang/String;Z)V", at = @At("HEAD"), cancellable = true)
    private void timer$handleLocalCommand(String message, boolean addToChat, CallbackInfo callback) {
        if (TimerMod.handleLocalCommand(message)) {
            callback.cancel();
        }
    }
}
