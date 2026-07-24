package net.weavemc.mods.endstone.mixin;

import net.minecraft.world.ChunkCache;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(ChunkCache.class)
public interface ChunkCacheAccessor {
    @Accessor("worldObj")
    World getEndstoneWorld();
}
