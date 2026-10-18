package net.weavemc.mods.fireballindicator;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Works out who threw each fireball. The spawn packet carries the thrower's entity id, but the
 * client zeroes it while spawning the entity, so it is captured as the packet arrives.
 */
final class FireballOwnership {
    private static final int TYPE_LARGE_FIREBALL = 63;
    private static final int TYPE_SMALL_FIREBALL = 64;
    private static final int TYPE_WITHER_SKULL = 66;
    private static final int MAX_PENDING = 512;
    /** Without a thrower id, a fireball first seen this close to our eyes and leaving is ours. */
    static final double SPAWN_NEAR_DISTANCE = 4.0;

    private final Map<Integer, Integer> shooters = new ConcurrentHashMap<Integer, Integer>();

    /** Called on the network thread for every object spawn packet. */
    void onSpawnPacket(int entityId, int type, int shooterId) {
        if (type != TYPE_LARGE_FIREBALL && type != TYPE_SMALL_FIREBALL && type != TYPE_WITHER_SKULL) {
            return;
        }
        if (shooters.size() >= MAX_PENDING) {
            shooters.clear();
        }
        shooters.put(entityId, shooterId);
    }

    /** Returns and forgets the thrower id for a fireball, or null if its spawn was not seen. */
    Integer takeShooter(int entityId) {
        return shooters.remove(entityId);
    }

    void clear() {
        shooters.clear();
    }

    /**
     * @param shooterId thrower from the spawn packet, null if unknown, 0 if the server sent none
     * @param distance distance from our eyes when the fireball is first seen
     * @param headingDotOffset dot product of the fireball's heading with the offset from our
     *                         eyes to the fireball; positive means it is moving away from us
     */
    static boolean isOwn(Integer shooterId, int localEntityId, double distance, double headingDotOffset) {
        if (shooterId != null && shooterId != 0) {
            return shooterId == localEntityId;
        }
        return distance <= SPAWN_NEAR_DISTANCE && headingDotOffset > 0.0;
    }
}
