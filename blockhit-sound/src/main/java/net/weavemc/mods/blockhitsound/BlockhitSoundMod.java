package net.weavemc.mods.blockhitsound;

import com.weave.lunarmods.LunarMods;
import net.weavemc.mods.servercontext.ServerContext;

import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemSword;
import net.minecraft.network.Packet;
import net.minecraft.network.play.server.S12PacketEntityVelocity;
import net.minecraft.network.play.server.S19PacketEntityStatus;
import net.weavemc.api.ModInitializer;
import net.weavemc.api.event.EventBus;
import net.weavemc.api.event.PacketEvent;
import net.weavemc.api.event.ShutdownEvent;
import net.weavemc.api.event.SubscribeEvent;
import net.weavemc.api.event.TickEvent;
import net.weavemc.api.event.WorldEvent;

public final class BlockhitSoundMod implements ModInitializer {
    private final BlockhitDetector detector = new BlockhitDetector();
    private final LegacyConfig legacyConfig = LegacyConfig.atDefaultLocation();

    private Object lastSession;

    @Override
    public void init() {
        BlockhitSettings.register(legacyConfig.read(), new Runnable() {
            @Override
            public void run() {
                // Lunar may run button actions on its UI thread.
                runOnClientThread(new Runnable() {
                    @Override
                    public void run() {
                        playSound(Minecraft.getMinecraft());
                    }
                });
            }
        });
        EventBus.subscribe(this);
        System.out.println("[Blockhit Sound] initialized");
    }

    @SubscribeEvent
    public void onPacketReceive(PacketEvent.Receive event) {
        if (!isActive()) { resetDetection(); return; }
        Packet<?> packet = event.getPacket();
        if (packet instanceof S19PacketEntityStatus
                && ((S19PacketEntityStatus) packet).getOpCode() == 2) {
            handleHurtStatus((S19PacketEntityStatus) packet);
        } else if (packet instanceof S12PacketEntityVelocity) {
            handleVelocity((S12PacketEntityVelocity) packet);
        }
    }

    private void handleHurtStatus(final S19PacketEntityStatus status) {
        runOnClientThread(new Runnable() {
            @Override
            public void run() {
                Minecraft minecraft = Minecraft.getMinecraft();
                if (minecraft == null || minecraft.theWorld == null) {
                    return;
                }
                Entity target = status.getEntity(minecraft.theWorld);
                boolean swordBlocking = isSwordBlocking(minecraft);
                detector.updateSwordBlocking(swordBlocking);
                if (target == minecraft.thePlayer
                        && detector.recordLocalHurt(System.nanoTime())) {
                    playConfiguredSound();
                }
            }
        });
    }

    private void handleVelocity(final S12PacketEntityVelocity velocity) {
        runOnClientThread(new Runnable() {
            @Override
            public void run() {
                Minecraft minecraft = Minecraft.getMinecraft();
                if (minecraft == null || minecraft.thePlayer == null
                        || velocity.getEntityID() != minecraft.thePlayer.getEntityId()) {
                    return;
                }
                detector.updateSwordBlocking(isSwordBlocking(minecraft));
                if (detector.recordLocalVelocity(System.nanoTime())) {
                    playConfiguredSound();
                }
            }
        });
    }

    @SubscribeEvent
    public void onTick(TickEvent.Pre event) {
        Object session = ServerContext.connectionId();
        if (lastSession != session) {
            lastSession = session;
            resetDetection();
        }
        Minecraft minecraft = Minecraft.getMinecraft();
        if (!isActive()) { resetDetection(); return; }
        detector.updateSwordBlocking(isSwordBlocking(minecraft));
    }

    @SubscribeEvent
    public void onWorldLoad(WorldEvent.Load event) {
        resetDetection();
    }

    @SubscribeEvent
    public void onWorldUnload(WorldEvent.Unload event) {
        resetDetection();
    }

    @SubscribeEvent
    public void onShutdown(ShutdownEvent event) {
        // Lunar now holds the imported values; keep the old file if Lunar never registered the tile.
        if (LunarMods.isInLunarMenu()) {
            legacyConfig.retire();
        }
    }

    private void playConfiguredSound() {
        if (isActive()) {
            playSound(Minecraft.getMinecraft());
        }
    }

    private static void playSound(Minecraft minecraft) {
        if (minecraft == null || minecraft.thePlayer == null) {
            return;
        }
        minecraft.thePlayer.playSound(BlockhitSettings.sound.get(),
                BlockhitSettings.volume.get(), BlockhitSettings.pitch.get());
    }

    private static boolean isActive() {
        return ServerContext.isAllowed() && BlockhitSettings.isEnabled();
    }

    private void resetDetection() {
        detector.reset();
    }

    private static boolean isSwordBlocking(Minecraft minecraft) {
        return minecraft != null && minecraft.thePlayer != null
                && minecraft.thePlayer.isBlocking()
                && isSword(minecraft.thePlayer.getHeldItem());
    }

    private static boolean isSword(ItemStack stack) {
        return stack != null && stack.getItem() instanceof ItemSword;
    }

    private static void runOnClientThread(Runnable action) {
        ServerContext.runOnClientThread(action);
    }
}
