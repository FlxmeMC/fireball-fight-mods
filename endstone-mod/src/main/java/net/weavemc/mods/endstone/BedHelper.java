package net.weavemc.mods.endstone;

import net.minecraft.block.BlockBed;
import net.minecraft.block.state.IBlockState;
import net.minecraft.util.BlockPos;
import net.minecraft.util.EnumFacing;
import net.minecraft.world.IBlockAccess;

public final class BedHelper {
    public static final int GLASS_RADIUS = 5;
    private static final int[][] SEARCH_OFFSETS_3 = createSearchOffsets(3);
    private static final int[][] SEARCH_OFFSETS_5 = createSearchOffsets(5);

    private BedHelper() {
    }

    public static boolean isAdjacentToBed(IBlockAccess world, BlockPos pos) {
        if (world == null || pos == null) {
            return false;
        }

        for (EnumFacing facing : EnumFacing.values()) {
            IBlockState neighbor = getBlockStateSafely(world, pos.offset(facing));
            if (neighbor != null && neighbor.getBlock() instanceof BlockBed) {
                return true;
            }
        }
        return false;
    }

    /**
     * Returns true when a bed is no more than {@link #GLASS_RADIUS} block-centres
     * away, provided no bed shares a face with the candidate block.
     */
    public static boolean isNearButNotTouchingBed(IBlockAccess world, BlockPos pos) {
        return isNearButNotTouchingBed(world, pos, GLASS_RADIUS);
    }

    public static boolean isNearButNotTouchingBed(IBlockAccess world, BlockPos pos, int radius) {
        if (world == null || pos == null) {
            return false;
        }

        // Touching beds always win. Checking these six positions first also
        // avoids walking the larger radius for the common protected case.
        if (isAdjacentToBed(world, pos)) {
            return false;
        }
        int[][] offsets = radius <= 3 ? SEARCH_OFFSETS_3 : SEARCH_OFFSETS_5;
        for (int[] offset : offsets) {
            IBlockState state = getBlockStateSafely(
                    world, pos.add(offset[0], offset[1], offset[2]));
            if (state != null && state.getBlock() instanceof BlockBed) {
                return true;
            }
        }
        return false;
    }

    private static int[][] createSearchOffsets(int radius) {
        int radiusSquared = radius * radius;
        int count = 0;
        for (int x = -radius; x <= radius; x++) {
            for (int y = -radius; y <= radius; y++) {
                for (int z = -radius; z <= radius; z++) {
                    int distanceSquared = x * x + y * y + z * z;
                    if (distanceSquared > 1 && distanceSquared <= radiusSquared) {
                        count++;
                    }
                }
            }
        }
        int[][] offsets = new int[count][3];
        int index = 0;
        for (int x = -radius; x <= radius; x++) {
            for (int y = -radius; y <= radius; y++) {
                for (int z = -radius; z <= radius; z++) {
                    int distanceSquared = x * x + y * y + z * z;
                    if (distanceSquared > 1 && distanceSquared <= radiusSquared) {
                        offsets[index++] = new int[]{x, y, z};
                    }
                }
            }
        }
        return offsets;
    }

    private static IBlockState getBlockStateSafely(IBlockAccess world, BlockPos pos) {
        try {
            return world.getBlockState(pos);
        } catch (RuntimeException ignored) {
            // RegionRenderCache may reject a neighbor just beyond its compiled bounds.
            return null;
        }
    }
}
