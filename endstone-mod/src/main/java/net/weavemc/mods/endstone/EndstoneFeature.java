package net.weavemc.mods.endstone;

import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.model.IBakedModel;
import net.minecraft.init.Blocks;
import net.minecraft.util.BlockPos;
import net.minecraft.util.EnumWorldBlockLayer;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.IBlockAccess;
import net.weavemc.mods.endstone.mixin.ChunkCacheAccessor;

import java.util.HashMap;
import java.util.Map;

public final class EndstoneFeature {
    public static final String MODE_RADIUS_3 = "radius_3";
    public static final String MODE_RADIUS_5 = "radius_5";
    public static final String MODE_ALWAYS = "always";
    private static final ThreadLocal<CompileContext> CHUNK_CONTEXT = new ThreadLocal<CompileContext>();
    private static volatile boolean enabled;
    private static volatile int glassRadius = 5;
    private static volatile boolean alwaysGlass;

    private EndstoneFeature() {
    }

    public static boolean isEnabled() {
        return enabled;
    }

    public static void setEnabled(boolean value) {
        enabled = value;
    }

    public static void setGlassMode(String mode) {
        alwaysGlass = MODE_ALWAYS.equals(mode);
        glassRadius = MODE_RADIUS_3.equals(mode) ? 3 : 5;
    }

    public static String getGlassMode() {
        if (alwaysGlass) {
            return MODE_ALWAYS;
        }
        return glassRadius == 3 ? MODE_RADIUS_3 : MODE_RADIUS_5;
    }

    public static EnumWorldBlockLayer getGlassRenderLayer() {
        return EnumWorldBlockLayer.CUTOUT;
    }

    public static IBakedModel getGlassModel() {
        Minecraft minecraft = Minecraft.getMinecraft();
        if (minecraft == null || minecraft.getBlockRendererDispatcher() == null) {
            return null;
        }

        return minecraft.getBlockRendererDispatcher()
                .getBlockModelShapes()
                .getModelForState(Blocks.glass.getDefaultState());
    }

    public static boolean isEndStoneBlock(Block block) {
        if (block == null) {
            return false;
        }
        try {
            ResourceLocation id = Block.blockRegistry.getNameForObject(block);
            if (id != null) {
                return "minecraft".equals(id.getResourceDomain())
                        && "end_stone".equals(id.getResourcePath());
            }
        } catch (RuntimeException ignored) {
            // Fall through to the vanilla singleton check.
        }

        try {
            return block == Blocks.end_stone;
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    public static boolean shouldTreatAsGlass(IBlockAccess world, BlockPos pos) {
        if (!enabled || world == null || pos == null) {
            return false;
        }

        IBlockState state;
        try {
            state = world.getBlockState(pos);
        } catch (RuntimeException ignored) {
            return false;
        }
        if (state == null || !isEndStoneBlock(state.getBlock())) {
            return false;
        }
        CompileContext context = CHUNK_CONTEXT.get();
        Long positionKey = context == null ? null : Long.valueOf(pos.toLong());
        if (context != null && context.glassCache.containsKey(positionKey)) {
            return context.glassCache.get(positionKey).booleanValue();
        }
        boolean result = alwaysGlass || BedHelper.isNearButNotTouchingBed(
                resolveBedAccess(world), pos, glassRadius);
        if (context != null) {
            context.glassCache.put(positionKey, Boolean.valueOf(result));
        }
        return result;
    }

    private static IBlockAccess resolveBedAccess(IBlockAccess renderAccess) {
        if (renderAccess instanceof ChunkCacheAccessor) {
            IBlockAccess fullWorld = ((ChunkCacheAccessor) renderAccess).getEndstoneWorld();
            if (fullWorld != null) {
                return fullWorld;
            }
        }
        return renderAccess;
    }

    public static boolean shouldExposeFaceAgainst(IBlockAccess world, BlockPos suppliedNeighborPos) {
        return shouldTreatAsGlass(world, suppliedNeighborPos);
    }

    public static EnumWorldBlockLayer resolveRenderLayer(Block block) {
        return shouldUseGlassRenderLayer(block) ? getGlassRenderLayer() : block.getBlockLayer();
    }

    public static boolean shouldUseGlassRenderLayer(Block block) {
        return enabled && isEndStoneBlock(block);
    }

    public static boolean shouldTreatAsGlassAtChunkPos(Block block) {
        return shouldTreatAsGlassInContext(block, CHUNK_CONTEXT.get());
    }

    private static boolean shouldTreatAsGlassInContext(Block block, CompileContext context) {
        return context != null
                && isEndStoneBlock(block)
                && shouldTreatAsGlass(context.world, context.pos);
    }

    public static void setChunkCompileContext(IBlockAccess world, BlockPos pos) {
        if (world == null || pos == null) {
            CHUNK_CONTEXT.remove();
        } else {
            CompileContext context = CHUNK_CONTEXT.get();
            if (context == null || context.world != world) {
                CHUNK_CONTEXT.set(new CompileContext(world, pos));
            } else {
                context.pos = pos;
            }
        }
    }

    public static void clearChunkCompileContext() {
        CHUNK_CONTEXT.remove();
    }

    private static final class CompileContext {
        private final IBlockAccess world;
        private BlockPos pos;
        private final Map<Long, Boolean> glassCache = new HashMap<Long, Boolean>();

        private CompileContext(IBlockAccess world, BlockPos pos) {
            this.world = world;
            this.pos = pos;
        }
    }

}
