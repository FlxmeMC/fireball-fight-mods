package net.weavemc.mods.endstone;

import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.BlockPos;
import net.minecraft.util.EnumFacing;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.WorldType;
import net.minecraft.world.biome.BiomeGenBase;

/**
 * Rendering-only world view which gives transformed endstone the opacity and
 * lighting behaviour of glass. The real server/world blocks are never changed.
 */
public final class GlassLightingBlockAccess implements IBlockAccess {
    private static final int GLASS_LIGHT_LEVEL = 15;
    private final IBlockAccess delegate;
    private final BlockPos[] visualGlass = new BlockPos[7];
    private int visualGlassCount;

    public GlassLightingBlockAccess(IBlockAccess delegate, BlockPos renderPos) {
        this.delegate = delegate;
        captureIfGlass(renderPos);
        for (EnumFacing facing : EnumFacing.values()) {
            captureIfGlass(renderPos.offset(facing));
        }
    }

    private void captureIfGlass(BlockPos pos) {
        if (EndstoneFeature.shouldTreatAsGlass(delegate, pos)) {
            visualGlass[visualGlassCount++] = pos;
        }
    }

    public boolean isVisualGlass(BlockPos pos) {
        for (int index = 0; index < visualGlassCount; index++) {
            if (visualGlass[index].equals(pos)) {
                return true;
            }
        }
        return false;
    }

    public boolean hasVisualGlass() {
        return visualGlassCount > 0;
    }

    @Override
    public TileEntity getTileEntity(BlockPos pos) {
        return delegate.getTileEntity(pos);
    }

    @Override
    public int getCombinedLight(BlockPos pos, int lightValue) {
        // Never walk outward from an AO lighting request. Lunar's parallel
        // renderer can already be asking about the edge of its ChunkCache, so
        // another offset can escape the cache and crash a chunk worker. Raising
        // the light floor at the requested position gives the transparent
        // result without performing any additional positional query.
        return delegate.getCombinedLight(pos, lightValueFor(hasVisualGlass(), lightValue));
    }

    static int lightValueFor(boolean hasGlass, int requestedLight) {
        return hasGlass ? Math.max(GLASS_LIGHT_LEVEL, requestedLight) : requestedLight;
    }

    @Override
    public IBlockState getBlockState(BlockPos pos) {
        return isVisualGlass(pos) ? Blocks.glass.getDefaultState() : delegate.getBlockState(pos);
    }

    @Override
    public boolean isAirBlock(BlockPos pos) {
        return delegate.isAirBlock(pos);
    }

    @Override
    public BiomeGenBase getBiomeGenForCoords(BlockPos pos) {
        return delegate.getBiomeGenForCoords(pos);
    }

    @Override
    public boolean extendedLevelsInChunkCache() {
        return delegate.extendedLevelsInChunkCache();
    }

    @Override
    public int getStrongPower(BlockPos pos, EnumFacing direction) {
        return delegate.getStrongPower(pos, direction);
    }

    @Override
    public WorldType getWorldType() {
        return delegate.getWorldType();
    }
}
