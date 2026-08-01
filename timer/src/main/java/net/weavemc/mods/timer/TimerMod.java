package net.weavemc.mods.timer;

import net.minecraft.network.Packet;
import net.minecraft.network.play.client.C01PacketChatMessage;
import net.minecraft.network.play.server.S08PacketPlayerPosLook;
import net.weavemc.api.ModInitializer;
import net.weavemc.api.command.CommandBus;
import net.weavemc.api.event.ChatEvent;
import net.weavemc.api.event.EventBus;
import net.weavemc.api.event.PacketEvent;
import net.weavemc.api.event.RenderGameOverlayEvent;
import net.weavemc.api.event.ShutdownEvent;
import net.weavemc.api.event.SubscribeEvent;
import net.weavemc.api.event.WorldEvent;
import net.weavemc.mods.hudeditor.HudElementRegistry;

public final class TimerMod implements ModInitializer {
    private final TimerState state = new TimerState();
    private TimerConfig config;
    private TimerRenderer renderer;
    private static TimerCommand command;

    static boolean verifyServerMessageSecurity() {
        return MatchLifecycleParser.parse("Match started!") == MatchLifecycleParser.Signal.START
                && MatchLifecycleParser.parse("Match Results (Click to view)")
                        == MatchLifecycleParser.Signal.END
                && MatchLifecycleParser.parse("Opponent forfeited.")
                        == MatchLifecycleParser.Signal.END
                && MatchLifecycleParser.parse("Opponent disconnected.")
                        == MatchLifecycleParser.Signal.END
                && MatchLifecycleParser.parse("[xFlxme] Match started!")
                        == MatchLifecycleParser.Signal.NONE
                && MatchLifecycleParser.parse("[Famous] xFlxme AURA: Match started!")
                        == MatchLifecycleParser.Signal.NONE
                && MatchLifecycleParser.parse("[xFlxme] Match Results (Click to view)")
                        == MatchLifecycleParser.Signal.NONE;
    }

    @Override
    public void init() {
        config = TimerConfig.loadDefault();
        renderer = new TimerRenderer(state, config);
        command = new TimerCommand(state);
        CommandBus.register(command);
        HudElementRegistry.register(new TimerHudElement(state, config));
        EventBus.subscribe(this);
        System.out.println("[Timer] initialized");
    }

    /** Stops Lunar from forwarding the local-only command to the server. */
    public static boolean handleLocalCommand(String message) {
        if (message == null || !"/timer".equalsIgnoreCase(message.trim())) {
            return false;
        }
        TimerCommand activeCommand = command;
        if (activeCommand == null) {
            return false;
        }
        activeCommand.execute(new String[] {"timer"});
        return true;
    }

    /**
     * Current Lunar can load GuiScreen and EntityPlayerSP before Weave registers hooks.
     * NetworkManager is loaded later, so the outgoing packet is the reliable final point at
     * which a local command can be consumed without ever reaching the server.
     */
    @SubscribeEvent
    public void onPacketSend(PacketEvent.Send event) {
        Packet<?> packet = event.getPacket();
        if (packet instanceof C01PacketChatMessage
                && handleLocalCommand(((C01PacketChatMessage) packet).getMessage())) {
            event.setCancelled(true);
        }
    }

    @SubscribeEvent
    public void onPacketReceive(PacketEvent.Receive event) {
        if (state.isFinishedMatch() && event.getPacket() instanceof S08PacketPlayerPosLook) {
            clear();
        }
    }

    @SubscribeEvent
    public void onChat(ChatEvent.Received event) {
        if (event.getMessage() == null) {
            return;
        }
        MatchLifecycleParser.Signal signal = MatchLifecycleParser.parse(
                event.getMessage().getFormattedText());
        if (signal == MatchLifecycleParser.Signal.START) {
            state.startFresh();
        } else if (signal == MatchLifecycleParser.Signal.END) {
            state.finish();
        }
    }

    @SubscribeEvent
    public void onRender(RenderGameOverlayEvent.Post event) {
        if (!HudElementRegistry.isEditing()) {
            renderer.render();
        }
    }

    @SubscribeEvent
    public void onWorldLoad(WorldEvent.Load event) {
        clear();
    }

    @SubscribeEvent
    public void onWorldUnload(WorldEvent.Unload event) {
        clear();
    }

    private void clear() {
        state.reset();
    }

    @SubscribeEvent
    public void onShutdown(ShutdownEvent event) {
        config.save();
    }
}
