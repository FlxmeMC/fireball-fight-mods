package net.weavemc.mods.endstone;

import net.minecraft.block.BlockBed;
import net.minecraft.block.state.IBlockState;
import net.minecraft.util.BlockPos;
import net.minecraft.util.EnumFacing;
import net.minecraft.world.IBlockAccess;
import org.junit.Assert;
import org.junit.Test;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class BedHelperTest {
    private static final BlockPos CENTER = new BlockPos(8, 64, 8);

    @Test
    public void detectsBedsOnEveryTouchingFace() {
        for (EnumFacing facing : EnumFacing.values()) {
            IBlockAccess world = worldReturning(mock(IBlockState.class));
            IBlockState bedState = mock(IBlockState.class);
            when(bedState.getBlock()).thenReturn(mock(BlockBed.class));
            when(world.getBlockState(CENTER.offset(facing))).thenReturn(bedState);

            Assert.assertTrue("Expected bed at " + facing, BedHelper.isAdjacentToBed(world, CENTER));
        }
    }

    @Test
    public void returnsFalseWithoutBed() {
        Assert.assertFalse(BedHelper.isAdjacentToBed(worldReturning(mock(IBlockState.class)), CENTER));
    }

    @Test
    public void acceptsBedsUpToFiveBlocksAway() {
        for (EnumFacing facing : EnumFacing.values()) {
            IBlockAccess world = worldReturning(mock(IBlockState.class));
            IBlockState bed = bedState();
            when(world.getBlockState(CENTER.offset(facing, BedHelper.GLASS_RADIUS)))
                    .thenReturn(bed);

            Assert.assertTrue(
                    "Expected bed within radius at " + facing,
                    BedHelper.isNearButNotTouchingBed(world, CENTER)
            );
        }
    }

    @Test
    public void threeBlockModeRejectsBedFourBlocksAway() {
        IBlockAccess world = worldReturning(mock(IBlockState.class));
        IBlockState distantBed = bedState();
        when(world.getBlockState(CENTER.east(4))).thenReturn(distantBed);

        Assert.assertFalse(BedHelper.isNearButNotTouchingBed(world, CENTER, 3));
    }

    @Test
    public void acceptsDiagonalBedInsideFiveBlockSphere() {
        IBlockAccess world = worldReturning(mock(IBlockState.class));
        IBlockState bed = bedState();
        when(world.getBlockState(CENTER.add(3, 2, 1))).thenReturn(bed);

        Assert.assertTrue(BedHelper.isNearButNotTouchingBed(world, CENTER));
    }

    @Test
    public void touchingBedOverridesAnotherBedInRange() {
        IBlockAccess world = worldReturning(mock(IBlockState.class));
        IBlockState touchingBed = bedState();
        IBlockState nearbyBed = bedState();
        when(world.getBlockState(CENTER.east())).thenReturn(touchingBed);
        when(world.getBlockState(CENTER.west(2))).thenReturn(nearbyBed);

        Assert.assertFalse(BedHelper.isNearButNotTouchingBed(world, CENTER));
    }

    @Test
    public void toleratesRenderCacheBoundaryFailure() {
        IBlockAccess world = worldReturning(mock(IBlockState.class));
        when(world.getBlockState(CENTER.north()))
                .thenThrow(new ArrayIndexOutOfBoundsException("outside RegionRenderCache"));

        Assert.assertFalse(BedHelper.isAdjacentToBed(world, CENTER));
    }

    @Test
    public void bedRenderUpdateRangeCrossesRenderSectionBoundaries() {
        BlockPos bedAtSectionEdge = new BlockPos(15, 64, 31);

        Assert.assertEquals(new BlockPos(10, 59, 26),
                BedHelper.getRenderUpdateMin(bedAtSectionEdge));
        Assert.assertEquals(new BlockPos(20, 69, 36),
                BedHelper.getRenderUpdateMax(bedAtSectionEdge));
    }

    private static IBlockAccess worldReturning(IBlockState state) {
        IBlockAccess world = mock(IBlockAccess.class);
        when(world.getBlockState(any(BlockPos.class))).thenReturn(state);
        return world;
    }

    private static IBlockState bedState() {
        IBlockState state = mock(IBlockState.class);
        when(state.getBlock()).thenReturn(mock(BlockBed.class));
        return state;
    }
}
