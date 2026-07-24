package net.weavemc.mods.endstone.mixin;

import net.minecraft.block.Block;
import net.minecraft.util.EnumWorldBlockLayer;
import net.weavemc.mods.endstone.EndstoneFeature;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Keeps the Lunar/OptiFine render pass visual-only; block solidity stays native. */
@Mixin(Block.class)
public abstract class BlockEndstoneRenderLayerMixin {
    @Inject(method = "getBlockLayer", at = @At("HEAD"), cancellable = true)
    private void endstoneUsesGlassLayer(CallbackInfoReturnable<EnumWorldBlockLayer> callback) {
        Block block = (Block) (Object) this;
        if (EndstoneFeature.shouldUseGlassRenderLayer(block)) {
            callback.setReturnValue(EndstoneFeature.getGlassRenderLayer());
        }
    }
}
