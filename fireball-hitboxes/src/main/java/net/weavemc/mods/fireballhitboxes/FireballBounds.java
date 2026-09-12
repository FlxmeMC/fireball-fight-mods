package net.weavemc.mods.fireballhitboxes;

import net.minecraft.entity.Entity;
import net.minecraft.entity.projectile.EntityFireball;
import net.minecraft.util.AxisAlignedBB;
import net.weavemc.mods.servercontext.ServerContext;

public final class FireballBounds {
    private FireballBounds() { }

    public static AxisAlignedBB forDebugRender(Entity entity) {
        AxisAlignedBB bounds = entity.getEntityBoundingBox();
        return entity instanceof EntityFireball && entity.canBeCollidedWith() && ServerContext.isAllowed()
                ? expand(bounds, entity.getCollisionBorderSize()) : bounds;
    }

    static AxisAlignedBB expand(AxisAlignedBB bounds, float border) {
        return bounds.expand((double) border, (double) border, (double) border);
    }
}
