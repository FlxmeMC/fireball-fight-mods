package net.weavemc.mods.endstone.mixin;

import net.minecraft.block.state.IBlockState;
import net.minecraft.client.renderer.BlockModelRenderer;
import net.minecraft.client.renderer.BlockRendererDispatcher;
import net.minecraft.client.renderer.WorldRenderer;
import net.minecraft.client.resources.model.IBakedModel;
import net.minecraft.util.BlockPos;
import net.minecraft.world.IBlockAccess;
import net.weavemc.mods.endstone.EndstoneFeature;
import net.weavemc.mods.endstone.GlassLightingBlockAccess;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(BlockRendererDispatcher.class)
public abstract class BlockRendererDispatcherMixin {
    @Redirect(
            method = "renderBlock",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/BlockModelRenderer;renderModel(Lnet/minecraft/world/IBlockAccess;Lnet/minecraft/client/resources/model/IBakedModel;Lnet/minecraft/block/state/IBlockState;Lnet/minecraft/util/BlockPos;Lnet/minecraft/client/renderer/WorldRenderer;)Z"
            )
    )
    private boolean renderEndstoneWithGlassModel(
            BlockModelRenderer renderer,
            IBlockAccess blockAccess,
            IBakedModel originalModel,
            IBlockState state,
            BlockPos pos,
            WorldRenderer worldRenderer
    ) {
        IBakedModel model = originalModel;
        IBlockState renderState = state;
        GlassLightingBlockAccess glassAccess = EndstoneFeature.isEnabled()
                ? new GlassLightingBlockAccess(blockAccess, pos)
                : null;
        if (glassAccess != null && glassAccess.isVisualGlass(pos)) {
            IBakedModel glassModel = EndstoneFeature.getGlassModel();
            if (glassModel != null) {
                model = glassModel;
                renderState = net.minecraft.init.Blocks.glass.getDefaultState();
            }
        }

        IBlockAccess lightingAccess = glassAccess != null && glassAccess.hasVisualGlass()
                ? glassAccess
                : blockAccess;
        return renderer.renderModel(lightingAccess, model, renderState, pos, worldRenderer);
    }
}
