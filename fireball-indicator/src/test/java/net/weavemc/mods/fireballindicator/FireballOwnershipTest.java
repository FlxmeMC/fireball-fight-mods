package net.weavemc.mods.fireballindicator;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public final class FireballOwnershipTest {
    @Test
    public void throwerFromSpawnPacketDecidesOwnership() {
        assertTrue(FireballOwnership.isOwn(42, 42, 30.0, -1.0));
        // Another player's fireball spawned right next to us and coming our way.
        assertFalse(FireballOwnership.isOwn(17, 42, 1.5, 1.0));
    }

    @Test
    public void withoutThrowerANearbyDepartingFireballIsOurs() {
        assertTrue(FireballOwnership.isOwn(0, 42, 2.0, 1.0));
        assertTrue(FireballOwnership.isOwn(null, 42, 2.0, 1.0));
        assertFalse(FireballOwnership.isOwn(null, 42, 2.0, -1.0));
        assertFalse(FireballOwnership.isOwn(null, 42, 20.0, 1.0));
    }

    @Test
    public void onlyFireballSpawnsAreRemembered() {
        FireballOwnership ownership = new FireballOwnership();
        ownership.onSpawnPacket(100, 63, 42);
        ownership.onSpawnPacket(101, 60, 42);

        assertEquals(Integer.valueOf(42), ownership.takeShooter(100));
        assertNull(ownership.takeShooter(100));
        assertNull(ownership.takeShooter(101));
    }
}
