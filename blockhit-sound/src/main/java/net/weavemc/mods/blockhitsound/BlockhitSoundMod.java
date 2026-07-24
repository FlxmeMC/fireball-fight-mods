package net.weavemc.mods.blockhitsound;

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
    private BlockhitConfig config;

    @Override
    public void init() {
        config = BlockhitConfig.loadDefault();
        config.save();
        EventBus.subscribe(this);
        System.out.println("[Blockhit Sound] initialized with "
                + config.getSound().getKey());
    }

    @SubscribeEvent
    public void onPacketReceive(PacketEvent.Receive event) {
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
        Minecraft minecraft = Minecraft.getMinecraft();
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
        config.save();
    }

    private void playConfiguredSound() {
        Minecraft minecraft = Minecraft.getMinecraft();
        if (minecraft == null || minecraft.thePlayer == null) {
            return;
        }
        SoundDefinition sound = config.getSound();
        minecraft.thePlayer.playSound(
                sound.getEventName(), config.getVolume(), config.getPitch());
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
        Minecraft minecraft = Minecraft.getMinecraft();
        if (minecraft == null) {
            return;
        }
        if (minecraft.isCallingFromMinecraftThread()) {
            action.run();
        } else {
            minecraft.addScheduledTask(action);
        }
    }
}
