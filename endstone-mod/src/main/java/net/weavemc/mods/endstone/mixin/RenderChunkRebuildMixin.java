package net.weavemc.mods.endstone.mixin;

import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.util.BlockPos;
import net.minecraft.util.EnumWorldBlockLayer;
import net.minecraft.world.IBlockAccess;
import net.weavemc.mods.endstone.EndstoneFeature;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(net.minecraft.client.renderer.chunk.RenderChunk.class)
public abstract class RenderChunkRebuildMixin {
    @Inject(method = "rebuildChunk", at = @At("HEAD"))
    private void clearStaleCompileContext(CallbackInfo callback) {
        EndstoneFeature.clearChunkCompileContext();
    }

    @Redirect(
            method = "rebuildChunk",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/IBlockAccess;getBlockState(Lnet/minecraft/util/BlockPos;)Lnet/minecraft/block/state/IBlockState;"
            )
    )
    private IBlockState captureCompileContext(IBlockAccess world, BlockPos pos) {
        EndstoneFeature.setChunkCompileContext(world, pos);
        return world.getBlockState(pos);
    }

    @Redirect(
            method = "rebuildChunk",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/block/Block;getBlockLayer()Lnet/minecraft/util/EnumWorldBlockLayer;"
            )
    )
    private EnumWorldBlockLayer useEndstoneRenderLayer(Block block) {
        return EndstoneFeature.resolveRenderLayer(block);
    }

    @Redirect(
            method = "rebuildChunk",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/block/Block;isOpaqueCube()Z"
            )
    )
    private boolean useEndstoneOpacity(Block block) {
        return EndstoneFeature.shouldTreatAsGlassAtChunkPos(block) ? false : block.isOpaqueCube();
    }

    @Inject(method = "rebuildChunk", at = @At("RETURN"))
    private void clearCompileContext(CallbackInfo callback) {
        EndstoneFeature.clearChunkCompileContext();
    }
}
