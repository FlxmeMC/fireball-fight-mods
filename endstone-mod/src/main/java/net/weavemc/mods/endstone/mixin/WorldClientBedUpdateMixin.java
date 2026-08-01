package net.weavemc.mods.endstone.mixin;

import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.util.BlockPos;
import net.weavemc.mods.endstone.BedHelper;
import net.weavemc.mods.endstone.EndstoneFeature;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(WorldClient.class)
public abstract class WorldClientBedUpdateMixin {
    @Inject(method = "invalidateRegionAndSetBlock", at = @At("HEAD"))
    private void invalidateEndstoneGlassAroundChangedBed(
            BlockPos pos,
            IBlockState newState,
            CallbackInfoReturnable<Boolean> callback
    ) {
        if (!EndstoneFeature.isEnabled()) {
            return;
        }

        WorldClient world = (WorldClient) (Object) this;
        IBlockState oldState;
        try {
            oldState = world.getBlockState(pos);
        } catch (RuntimeException ignored) {
            oldState = null;
        }
        if (!BedHelper.isBedState(oldState) && !BedHelper.isBedState(newState)) {
            return;
        }

        Minecraft minecraft = Minecraft.getMinecraft();
        if (minecraft == null || minecraft.renderGlobal == null) {
            return;
        }
        BlockPos min = BedHelper.getRenderUpdateMin(pos);
        BlockPos max = BedHelper.getRenderUpdateMax(pos);
        minecraft.renderGlobal.markBlockRangeForRenderUpdate(
                min.getX(), min.getY(), min.getZ(),
                max.getX(), max.getY(), max.getZ());
    }
}
