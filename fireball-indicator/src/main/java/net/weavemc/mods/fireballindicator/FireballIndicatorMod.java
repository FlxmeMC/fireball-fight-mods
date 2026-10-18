package net.weavemc.mods.fireballindicator;

import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.entity.Entity;
import net.minecraft.entity.projectile.EntityFireball;
import net.minecraft.network.Packet;
import net.minecraft.network.play.server.S0EPacketSpawnObject;
import net.weavemc.api.ModInitializer;
import net.weavemc.api.event.EventBus;
import net.weavemc.api.event.PacketEvent;
import net.weavemc.api.event.RenderGameOverlayEvent;
import net.weavemc.api.event.SubscribeEvent;
import net.weavemc.api.event.TickEvent;
import net.weavemc.api.event.WorldEvent;
import net.weavemc.mods.servercontext.ServerContext;

public final class FireballIndicatorMod implements ModInitializer {
    private final FireballTracker tracker = new FireballTracker();
    private final FireballOwnership ownership = new FireballOwnership();
    private final RingRenderer renderer = new RingRenderer();
    private Object lastSession;

    @Override
    public void init() {
        System.out.println("[Fireball Indicator] Initializing");
        FireballIndicatorSettings.register();
        EventBus.subscribe(this);
        System.out.println("[Fireball Indicator] Initialized");
    }

    @SubscribeEvent
    public void onTick(TickEvent.Post event) {
        Object session = ServerContext.connectionId();
        if (lastSession != session) {
            lastSession = session;
            tracker.clear();
            ownership.clear();
        }

        Minecraft minecraft = Minecraft.getMinecraft();
        if (!ServerContext.isAllowed() || !FireballIndicatorSettings.isEnabled()
                || minecraft.theWorld == null || minecraft.thePlayer == null) {
            tracker.clear();
            return;
        }
        scanFireballs(minecraft.thePlayer, minecraft);
    }

    @SubscribeEvent
    public void onPacketReceive(PacketEvent.Receive event) {
        Packet<?> packet = event.getPacket();
        if (packet instanceof S0EPacketSpawnObject) {
            S0EPacketSpawnObject spawn = (S0EPacketSpawnObject) packet;
            ownership.onSpawnPacket(spawn.getEntityID(), spawn.getType(), spawn.func_149009_m());
        }
    }

    @SubscribeEvent
    public void onRenderOverlay(RenderGameOverlayEvent.Post event) {
        if (ServerContext.isAllowed() && FireballIndicatorSettings.isEnabled()) {
            renderer.render(tracker, event.getPartialTicks());
        }
    }

    @SubscribeEvent
    public void onWorldLoad(WorldEvent.Load event) {
        tracker.clear();
        ownership.clear();
    }

    @SubscribeEvent
    public void onWorldUnload(WorldEvent.Unload event) {
        tracker.clear();
        ownership.clear();
    }

    // Reads the client's entity list rather than relying on sounds or rendering, so fireballs
    // behind the player or outside the view frustum are still picked up. Every loaded fireball is
    // shown wherever it is on the map, except the ones we threw.
    private void scanFireballs(EntityPlayerSP player, Minecraft minecraft) {
        long now = System.currentTimeMillis();
        double eyeX = player.posX;
        double eyeY = player.posY + player.getEyeHeight();
        double eyeZ = player.posZ;
        tracker.beginTick();
        for (Entity entity : minecraft.theWorld.loadedEntityList) {
            if (!(entity instanceof EntityFireball) || entity.isDead) {
                continue;
            }
            EntityFireball fireball = (EntityFireball) entity;
            double dx = fireball.posX - eyeX;
            double dy = fireball.posY - eyeY;
            double dz = fireball.posZ - eyeZ;
            double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);
            double vx = fireball.posX - fireball.lastTickPosX;
            double vy = fireball.posY - fireball.lastTickPosY;
            double vz = fireball.posZ - fireball.lastTickPosZ;
            if (vx == 0.0 && vy == 0.0 && vz == 0.0) {
                // Freshly spawned fireballs have not moved yet; their acceleration is their heading.
                vx = fireball.accelerationX;
                vy = fireball.accelerationY;
                vz = fireball.accelerationZ;
            }
            int entityId = fireball.getEntityId();
            boolean own = false;
            if (!tracker.isTracked(entityId)) {
                own = FireballOwnership.isOwn(ownership.takeShooter(entityId), player.getEntityId(),
                        distance, vx * dx + vy * dy + vz * dz);
            }
            tracker.observe(entityId, own, fireball.posX, fireball.posZ, distance, now);
        }
        tracker.endTick(now);
    }
}
