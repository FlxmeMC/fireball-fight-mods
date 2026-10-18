package net.weavemc.mods.endstone;

import net.weavemc.mods.servercontext.ServerContext;

import net.minecraft.client.Minecraft;
import net.minecraft.network.Packet;
import net.minecraft.network.play.client.C01PacketChatMessage;
import net.minecraft.util.ChatComponentText;
import net.weavemc.api.ModInitializer;
import net.weavemc.api.command.CommandBus;
import net.weavemc.api.event.EventBus;
import net.weavemc.api.event.PacketEvent;
import net.weavemc.api.event.ShutdownEvent;
import net.weavemc.api.event.SubscribeEvent;
import net.weavemc.api.event.TickEvent;
import net.weavemc.mods.endstone.command.EndstoneCommand;

import java.io.File;
import java.lang.instrument.Instrumentation;
import java.util.function.Consumer;

public final class EndstoneMod implements ModInitializer {
    private static final EndstoneCommand COMMAND = new EndstoneCommand();
    private static final String TOGGLE_HINT = "End Stone Glass: toggle it in Lunar's mod menu (Right Shift).";
    private LegacyConfig legacyConfig;
    private boolean wasActive;

    @Override
    public void preInit(Instrumentation instrumentation) {
        // No early instrumentation is required. Mixins are declared in the mod manifest.
    }

    @Override
    public void init() {
        EndstoneSettings.register();
        importLegacyConfig();
        EndstoneFeature.setGlassMode(EndstoneSettings.mode.get().featureMode);
        // The tick handler notices the change and rebuilds chunks on the client thread.
        EndstoneSettings.tile.onToggle(new Consumer<Boolean>() {
            @Override
            public void accept(Boolean enabled) {
                EndstoneFeature.setEnabled(enabled.booleanValue());
            }
        });
        EndstoneSettings.mode.onChange(new Consumer<EndstoneMode>() {
            @Override
            public void accept(EndstoneMode mode) {
                EndstoneFeature.setGlassMode(mode.featureMode);
                ServerContext.runOnClientThread(new Runnable() {
                    @Override
                    public void run() {
                        if (ServerContext.isAllowed() && EndstoneFeature.isEnabled()) {
                            reloadChunks();
                        }
                    }
                });
            }
        });
        CommandBus.register(COMMAND);
        EventBus.subscribe(this);
    }

    private void importLegacyConfig() {
        legacyConfig = LegacyConfig.read(new File(System.getProperty("user.home"), ".weave/config/endstone-mod.properties"));
        if (!legacyConfig.exists()) {
            return;
        }
        EndstoneMode mode = EndstoneMode.fromFeatureMode(legacyConfig.get("mode"));
        if (mode != null) {
            // Kept unless Lunar already has a saved value for this setting.
            EndstoneSettings.mode.set(mode);
        }
        System.out.println("[Endstone Mod] Imported the old Radius setting. The old on/off state is not "
                + "imported; switch End Stone Glass on in Lunar's mod menu.");
    }

    @SubscribeEvent
    public void onTick(TickEvent.Pre event) {
        boolean active = ServerContext.isAllowed() && EndstoneFeature.isEnabled();
        if (active != wasActive) {
            wasActive = active;
            reloadChunks();
        }
    }

    private static void reloadChunks() {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc != null && mc.theWorld != null && mc.renderGlobal != null) mc.renderGlobal.loadRenderers();
    }

    /** The tile can only be switched from Lunar's menu, so the command explains where to find it. */
    public static void showToggleHint() {
        ServerContext.runOnClientThread(new Runnable() {
            @Override public void run() {
                Minecraft minecraft = Minecraft.getMinecraft();
                if (minecraft.thePlayer != null) {
                    minecraft.thePlayer.addChatMessage(new ChatComponentText(TOGGLE_HINT));
                }
            }
        });
    }

    @SubscribeEvent
    public void onShutdown(ShutdownEvent event) {
        if (legacyConfig != null) {
            legacyConfig.retireIfImported();
        }
    }

    /** Handles the command before Lunar forwards it to its integrated server. */
    public static boolean handleLocalCommand(String message) {
        if (message == null) {
            return false;
        }
        String trimmed = message.trim();
        if (!trimmed.startsWith("/")) {
            return false;
        }
        String withoutPrefix = trimmed.substring(1);
        if (withoutPrefix.isEmpty()) {
            return false;
        }
        String[] args = withoutPrefix.split("\\s+");
        if (!args[0].equalsIgnoreCase("endstone")) {
            return false;
        }
        COMMAND.execute(args);
        return true;
    }

    /** Consumes the local command at NetworkManager on Lunar builds that bypass GuiScreen. */
    @SubscribeEvent
    public void onPacketSend(PacketEvent.Send event) {
        Packet<?> packet = event.getPacket();
        if (packet instanceof C01PacketChatMessage
                && handleLocalCommand(((C01PacketChatMessage) packet).getMessage())) {
            event.setCancelled(true);
        }
    }
}
