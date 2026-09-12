package net.weavemc.mods.fireballhitboxes;

import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.Vec3;
import org.junit.Test;
import static org.junit.Assert.*;

public final class FireballBoundsTest {
    @Test public void outlineIncludesPunchableBorderWithoutMutatingPhysicalBounds() {
        AxisAlignedBB physical = new AxisAlignedBB(10, 20, 30, 11, 21, 31);
        AxisAlignedBB attack = FireballBounds.expand(physical, 1F);
        assertEquals(9, attack.minX, 0); assertEquals(19, attack.minY, 0);
        assertEquals(29, attack.minZ, 0); assertEquals(12, attack.maxX, 0);
        assertEquals(22, attack.maxY, 0); assertEquals(32, attack.maxZ, 0);
        assertEquals(10, physical.minX, 0); assertEquals(11, physical.maxX, 0);
        Vec3 from = new Vec3(9.5, 20.5, 26), to = new Vec3(9.5, 20.5, 34);
        assertNull(physical.calculateIntercept(from, to));
        assertNotNull(attack.calculateIntercept(from, to));
    }
    @Test public void expansionCommutesWithRenderTranslationAndUsesSuppliedBorder() {
        AxisAlignedBB bounds = new AxisAlignedBB(-2, 4, 8, -1, 5, 9);
        AxisAlignedBB translated = FireballBounds.expand(bounds, 0.5F).offset(7.25, -2, 3);
        AxisAlignedBB expected = FireballBounds.expand(bounds.offset(7.25, -2, 3), 0.5F);
        assertEquals(expected.minX, translated.minX, 0);
        assertEquals(expected.maxY, translated.maxY, 0);
        assertEquals(expected.maxZ, translated.maxZ, 0);
    }
}
