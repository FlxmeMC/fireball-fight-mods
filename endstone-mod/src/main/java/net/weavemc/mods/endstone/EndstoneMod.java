package net.weavemc.mods.endstone;

import net.minecraft.client.Minecraft;
import net.minecraft.client.settings.KeyBinding;
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

import java.lang.instrument.Instrumentation;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public final class EndstoneMod implements ModInitializer {
    private static final EndstoneCommand COMMAND = new EndstoneCommand();
    private static EndstoneConfigStore configStore;
    private KeyBinding toggleKey;

    static boolean verifyEnabledPersistence() throws IOException {
        Path directory = Files.createTempDirectory("weave-endstone-persistence-");
        Path file = directory.resolve("endstone.properties");
        try {
            EndstoneConfigStore store = new EndstoneConfigStore(file);
            store.saveEnabled(true);
            boolean enabledReloaded = new EndstoneConfigStore(file).loadEnabled();
            store.saveEnabled(false);
            boolean disabledReloaded = !new EndstoneConfigStore(file).loadEnabled();
            return enabledReloaded && disabledReloaded;
        } finally {
            Files.deleteIfExists(file);
            Files.deleteIfExists(directory);
        }
    }

    @Override
    public void preInit(Instrumentation instrumentation) {
        // No early instrumentation is required. Mixins are declared in the mod manifest.
    }

    @Override
    public void init() {
        configStore = EndstoneConfigStore.inUserHome();
        try {
            EndstoneFeature.setEnabled(configStore.loadEnabled());
            EndstoneFeature.setGlassMode(configStore.loadMode());
        } catch (IOException exception) {
            EndstoneFeature.setEnabled(false);
            System.err.println("[Endstone Mod] Failed to load config: " + exception.getMessage());
        }
        CommandBus.register(COMMAND);
        EventBus.subscribe(this);
    }

    @SubscribeEvent
    public void onTick(TickEvent.Pre event) {
        if (toggleKey == null) {
            toggleKey = ModKeyBinding.tryRegister("Toggle Endstone Mod");
        }
        if (toggleKey == null) {
            return;
        }
        while (toggleKey.isPressed()) {
            toggleFeature();
        }
    }

    public static void toggleFeature() {
        boolean enabled = !EndstoneFeature.isEnabled();
        EndstoneFeature.setEnabled(enabled);
        saveEnabledState();

        Minecraft minecraft = Minecraft.getMinecraft();
        if (minecraft.thePlayer != null) {
            minecraft.thePlayer.addChatMessage(
                    new ChatComponentText("End stone glass: " + (enabled ? "ON" : "OFF")));
        }
        if (minecraft.renderGlobal != null) {
            minecraft.renderGlobal.loadRenderers();
        }
    }

    @SubscribeEvent
    public void onShutdown(ShutdownEvent event) {
        saveEnabledState();
    }

    private static void saveEnabledState() {
        EndstoneConfigStore store = configStore;
        if (store == null) {
            return;
        }
        try {
            store.saveEnabled(EndstoneFeature.isEnabled());
        } catch (IOException exception) {
            System.err.println("[Endstone Mod] Failed to save config: " + exception.getMessage());
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
