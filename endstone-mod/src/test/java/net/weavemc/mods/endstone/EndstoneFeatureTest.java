package net.weavemc.mods.endstone;

import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.util.BlockPos;
import net.minecraft.util.EnumWorldBlockLayer;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.IBlockAccess;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class EndstoneFeatureTest {
    private static final BlockPos CENTER = new BlockPos(4, 64, 4);
    private static Block endStoneBlock;
    private static IBlockState endStoneState;
    private static IBlockState airState;

    @BeforeClass
    public static void registerTestEndstone() {
        endStoneBlock = mock(Block.class);
        endStoneState = mock(IBlockState.class);
        Block airBlock = mock(Block.class);
        airState = mock(IBlockState.class);
        when(endStoneBlock.getBlockLayer()).thenReturn(EnumWorldBlockLayer.SOLID);
        when(endStoneState.getBlock()).thenReturn(endStoneBlock);
        when(airState.getBlock()).thenReturn(airBlock);
        Block.blockRegistry.register(0, new ResourceLocation("minecraft", "air"), airBlock);
        Block.blockRegistry.register(121, new ResourceLocation("minecraft", "end_stone"), endStoneBlock);
    }

    @Before
    public void setUp() {
        EndstoneFeature.setEnabled(false);
        EndstoneFeature.setGlassMode(EndstoneFeature.MODE_RADIUS_5);
        EndstoneFeature.clearChunkCompileContext();
    }

    @After
    public void tearDown() {
        EndstoneFeature.setEnabled(false);
        EndstoneFeature.clearChunkCompileContext();
    }

    @Test
    public void defaultsToDisabledAndCanToggle() {
        Assert.assertFalse(EndstoneFeature.isEnabled());
        EndstoneFeature.setEnabled(true);
        Assert.assertTrue(EndstoneFeature.isEnabled());
    }

    @Test
    public void enabledEndstoneNearNonTouchingBedUsesCutoutLayer() {
        IBlockAccess world = worldWithEndstoneAt(CENTER);
        IBlockState nearbyBed = bedState();
        when(world.getBlockState(CENTER.east(2))).thenReturn(nearbyBed);
        EndstoneFeature.setEnabled(true);
        EndstoneFeature.setChunkCompileContext(world, CENTER);

        Assert.assertTrue(EndstoneFeature.shouldTreatAsGlass(world, CENTER));
        Assert.assertEquals(
                EnumWorldBlockLayer.CUTOUT,
                EndstoneFeature.resolveRenderLayer(endStoneBlock)
        );
    }

    @Test
    public void endstoneWithoutNearbyBedStaysInTheSolidPass() {
        IBlockAccess world = worldWithEndstoneAt(CENTER);
        EndstoneFeature.setEnabled(true);
        EndstoneFeature.setChunkCompileContext(world, CENTER);

        Assert.assertFalse(EndstoneFeature.shouldTreatAsGlass(world, CENTER));
        Assert.assertEquals(EnumWorldBlockLayer.SOLID, EndstoneFeature.resolveRenderLayer(endStoneBlock));
    }

    @Test
    public void threeBlockModeLeavesDistantEndstoneSolid() {
        IBlockAccess world = worldWithEndstoneAt(CENTER);
        IBlockState distantBed = bedState();
        when(world.getBlockState(CENTER.east(4))).thenReturn(distantBed);
        EndstoneFeature.setEnabled(true);
        EndstoneFeature.setGlassMode(EndstoneFeature.MODE_RADIUS_3);
        EndstoneFeature.setChunkCompileContext(world, CENTER);

        Assert.assertFalse(EndstoneFeature.shouldTreatAsGlass(world, CENTER));
        Assert.assertEquals(EnumWorldBlockLayer.SOLID, EndstoneFeature.resolveRenderLayer(endStoneBlock));
    }

    @Test
    public void alwaysModeConvertsEndstoneWithoutABed() {
        IBlockAccess world = worldWithEndstoneAt(CENTER);
        EndstoneFeature.setEnabled(true);
        EndstoneFeature.setGlassMode(EndstoneFeature.MODE_ALWAYS);

        Assert.assertTrue(EndstoneFeature.shouldTreatAsGlass(world, CENTER));
    }

    @Test
    public void bedAdjacentEndstoneStaysInTheSolidPass() {
        IBlockAccess world = worldWithEndstoneAt(CENTER);
        IBlockState touchingBed = bedState();
        when(world.getBlockState(CENTER.up())).thenReturn(touchingBed);

        EndstoneFeature.setEnabled(true);
        EndstoneFeature.setChunkCompileContext(world, CENTER);

        Assert.assertFalse(EndstoneFeature.shouldTreatAsGlass(world, CENTER));
        Assert.assertEquals(
                EnumWorldBlockLayer.SOLID,
                EndstoneFeature.resolveRenderLayer(endStoneBlock)
        );
    }

    @Test
    public void missingCompileContextKeepsEndstoneInTheSolidPass() {
        EndstoneFeature.setEnabled(true);

        Assert.assertFalse(EndstoneFeature.shouldTreatAsGlassAtChunkPos(endStoneBlock));
        Assert.assertEquals(
                EnumWorldBlockLayer.SOLID,
                EndstoneFeature.resolveRenderLayer(endStoneBlock)
        );
    }

    @Test
    public void faceCullingUsesAlreadyOffsetNeighborPosition() {
        BlockPos suppliedNeighbor = CENTER.east();
        IBlockAccess world = worldWithEndstoneAt(suppliedNeighbor);
        IBlockState nearbyBed = bedState();
        when(world.getBlockState(suppliedNeighbor.east(2))).thenReturn(nearbyBed);
        EndstoneFeature.setEnabled(true);

        Assert.assertTrue(EndstoneFeature.shouldExposeFaceAgainst(world, suppliedNeighbor));
        Assert.assertFalse(EndstoneFeature.shouldExposeFaceAgainst(world, suppliedNeighbor.east()));
    }

    @Test
    public void enabledEndstoneNeedsACompilePositionToUseCutout() {
        EndstoneFeature.setEnabled(true);
        EndstoneFeature.clearChunkCompileContext();

        Assert.assertFalse(EndstoneFeature.shouldUseGlassRenderLayer(endStoneBlock));
        Assert.assertEquals(EnumWorldBlockLayer.SOLID, EndstoneFeature.resolveRenderLayer(endStoneBlock));
    }

    private static IBlockAccess worldWithEndstoneAt(BlockPos pos) {
        IBlockAccess world = mock(IBlockAccess.class);
        when(world.getBlockState(any(BlockPos.class))).thenReturn(airState);
        when(world.getBlockState(pos)).thenReturn(endStoneState);
        return world;
    }

    private static IBlockState bedState() {
        IBlockState bedState = mock(IBlockState.class);
        when(bedState.getBlock()).thenReturn(mock(net.minecraft.block.BlockBed.class));
        return bedState;
    }
}
