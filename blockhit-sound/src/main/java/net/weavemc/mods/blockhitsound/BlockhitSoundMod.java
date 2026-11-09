package net.weavemc.mods.blockhitsound;

import com.weave.lunarmods.LunarMods;
import net.weavemc.mods.servercontext.ServerContext;

import net.minecraft.client.Minecraft;
import net.minecraft.network.Packet;
import net.weavemc.api.ModInitializer;
import net.weavemc.api.event.EventBus;
import net.weavemc.api.event.PacketEvent;
import net.weavemc.api.event.ShutdownEvent;
import net.weavemc.api.event.SubscribeEvent;
import net.weavemc.api.event.TickEvent;
import net.weavemc.api.event.WorldEvent;

public final class BlockhitSoundMod implements ModInitializer {
    private final CombatSampler sampler = new CombatSampler(new Runnable() {
        @Override
        public void run() {
            playConfiguredSound();
        }
    });
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
        // Netty thread: never touch detection state here. onTick resets when inactive.
        if (!isActive()) {
            return;
        }
        final Packet<?> packet = event.getPacket();
        if (!CombatSampler.accepts(packet)) {
            return;
        }
        // Queued ahead of vanilla's own handler for this packet, so the sampler sees pre-packet state.
        runOnClientThread(new Runnable() {
            @Override
            public void run() {
                if (isActive()) {
                    sampler.handle(packet);
                }
            }
        });
    }

    @SubscribeEvent
    public void onTick(TickEvent.Post event) {
        Object session = ServerContext.connectionId();
        if (lastSession != session) {
            lastSession = session;
            resetDetection();
        }
        if (!isActive()) { resetDetection(); return; }
        sampler.tick();
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
        sampler.reset();
    }

    private static void runOnClientThread(Runnable action) {
        ServerContext.runOnClientThread(action);
    }
}
