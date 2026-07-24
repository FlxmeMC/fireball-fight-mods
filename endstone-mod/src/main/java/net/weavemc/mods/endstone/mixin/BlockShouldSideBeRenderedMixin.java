package net.weavemc.mods.endstone.mixin;

import net.minecraft.block.Block;
import net.minecraft.util.BlockPos;
import net.minecraft.util.EnumFacing;
import net.minecraft.world.IBlockAccess;
import net.weavemc.mods.endstone.EndstoneFeature;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Block.class)
public abstract class BlockShouldSideBeRenderedMixin {
    @Inject(method = "shouldSideBeRendered", at = @At("HEAD"), cancellable = true)
    private void showFacesAgainstGlassEndstone(
            IBlockAccess world,
            BlockPos suppliedNeighborPos,
            EnumFacing side,
            CallbackInfoReturnable<Boolean> callback
    ) {
        // Vanilla's caller has already offset the current position by side.
        if (EndstoneFeature.shouldExposeFaceAgainst(world, suppliedNeighborPos)) {
            callback.setReturnValue(true);
        }
    }
}
