package com.spawnprot.mod;

import net.minecraft.client.Minecraft;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.item.Item;
import net.minecraft.item.ItemArmor;
import net.minecraft.item.ItemStack;
import net.minecraft.network.Packet;
import net.minecraft.network.play.server.S19PacketEntityStatus;
import net.minecraft.network.play.server.S45PacketTitle;
import net.minecraft.scoreboard.ScorePlayerTeam;
import net.minecraft.scoreboard.Team;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.IChatComponent;
import net.weavemc.api.ModInitializer;
import net.weavemc.api.event.ChatEvent;
import net.weavemc.api.event.EventBus;
import net.weavemc.api.event.PacketEvent;
import net.weavemc.api.event.RenderGameOverlayEvent;
import net.weavemc.api.event.ShutdownEvent;
import net.weavemc.api.event.SubscribeEvent;
import net.weavemc.api.event.TickEvent;
import net.weavemc.api.event.WorldEvent;
import net.weavemc.mods.hudeditor.HudElementRegistry;

public final class SpawnProtMod implements ModInitializer {
    private final DeathChatHandler deathChatHandler = new DeathChatHandler();
    private final TitlePacketHandler titlePacketHandler = new TitlePacketHandler();
    private final SpawnProtRenderer renderer = new SpawnProtRenderer();
    private KeyBinding toggleKey;

    @Override
    public void init() {
        System.out.println("[SpawnProt] Initializing Spawn Protection Tracker");
        SpawnProtState.loadConfig();
        HudElementRegistry.register(new SpawnProtHudElement());
        EventBus.subscribe(this);
        System.out.println("[SpawnProt] Spawn Protection Tracker initialized");
    }

    @SubscribeEvent
    public void onChatMessage(ChatEvent.Received event) {
        if (!SpawnProtState.isEnabled()) {
            return;
        }
        IChatComponent message = event.getMessage();
        if (message != null) {
            final String formattedText = message.getFormattedText();
            final String playerName = localPlayerName();
            final String teamColor = localTeamColor();
            final long receivedAtMs = System.currentTimeMillis();
            runOnClientThread(new Runnable() {
                @Override
                public void run() {
                    deathChatHandler.onChatMessage(
                            formattedText, playerName, teamColor, receivedAtMs,
                            new DeathChatHandler.PlayerColorResolver() {
                                @Override
                                public String colorFor(String name) {
                                    return playerTeamColor(name);
                                }
                            });
                }
            });
        }
    }

    @SubscribeEvent
    public void onPacketReceive(PacketEvent.Receive event) {
        if (!SpawnProtState.isEnabled()) {
            return;
        }
        Packet<?> packet = event.getPacket();
        if (packet instanceof S45PacketTitle) {
            final S45PacketTitle title = (S45PacketTitle) packet;
            final long receivedAtMs = System.currentTimeMillis();
            runOnClientThread(new Runnable() {
                @Override
                public void run() {
                    titlePacketHandler.onPacketReceive(title, receivedAtMs);
                }
            });
        } else if (packet instanceof S19PacketEntityStatus) {
            handleEntityStatusPacket((S19PacketEntityStatus) packet);
        }
    }

    @SubscribeEvent
    public void onRenderOverlay(RenderGameOverlayEvent.Post event) {
        if (SpawnProtState.isEnabled() && !HudElementRegistry.isEditing()) {
            renderer.onRenderOverlay();
        }
    }

    @SubscribeEvent
    public void onTick(TickEvent.Pre event) {
        if (toggleKey == null) {
            toggleKey = ModKeyBinding.tryRegister("Toggle SpawnProt");
        }
        if (toggleKey != null) {
            while (toggleKey.isPressed()) {
                boolean enabled = !SpawnProtState.isEnabled();
                SpawnProtState.setEnabled(enabled);
                SpawnProtState.saveConfig();
                Minecraft minecraft = Minecraft.getMinecraft();
                if (minecraft != null && minecraft.thePlayer != null) {
                    minecraft.thePlayer.addChatMessage(new ChatComponentText(
                            "SpawnProt: " + (enabled ? "ON" : "OFF")));
                }
            }
        }
    }

    @SubscribeEvent
    public void onWorldLoad(WorldEvent.Load event) {
        SpawnProtState.onWorldChange();
    }

    @SubscribeEvent
    public void onWorldUnload(WorldEvent.Unload event) {
        SpawnProtState.onWorldChange();
    }

    @SubscribeEvent
    public void onShutdown(ShutdownEvent event) {
        SpawnProtState.saveConfig();
        System.out.println("[SpawnProt] Spawn Protection Tracker shut down");
    }

    private static void handleEntityStatusPacket(final S19PacketEntityStatus packet) {
        if (packet.getOpCode() != 2) {
            return;
        }
        final int generation = SpawnProtState.getWorldGeneration();
        final long receivedAtMs = System.currentTimeMillis();
        runOnClientThread(new Runnable() {
            @Override
            public void run() {
                Minecraft minecraft = Minecraft.getMinecraft();
                if (minecraft == null || minecraft.theWorld == null) {
                    return;
                }
                Entity entity = packet.getEntity(minecraft.theWorld);
                if (entity instanceof EntityPlayer) {
                    SpawnProtState.endProtectionAfterHitIfGeneration(
                            generation, entity.getName(), localPlayerName(), receivedAtMs);
                }
            }
        });
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

    private static String localPlayerName() {
        Minecraft minecraft = Minecraft.getMinecraft();
        return minecraft == null || minecraft.thePlayer == null
                ? null
                : minecraft.thePlayer.getName();
    }

    private static String localTeamColor() {
        Minecraft minecraft = Minecraft.getMinecraft();
        if (minecraft == null || minecraft.thePlayer == null) {
            return null;
        }
        Team team = minecraft.thePlayer.getTeam();
        String scoreboardColor = team instanceof ScorePlayerTeam
                ? DeathChatHandler.lastColorCode(((ScorePlayerTeam) team).getColorPrefix()) : null;
        if (scoreboardColor != null) {
            return scoreboardColor;
        }
        String armorColor = armorTeamColor(minecraft.thePlayer);
        return armorColor != null ? armorColor : woolTeamColor(minecraft.thePlayer);
    }

    private static String playerTeamColor(String playerName) {
        Minecraft minecraft = Minecraft.getMinecraft();
        if (minecraft == null || minecraft.theWorld == null || playerName == null) {
            return null;
        }
        for (EntityPlayer player : minecraft.theWorld.playerEntities) {
            if (!playerName.equalsIgnoreCase(player.getName())) {
                continue;
            }
            Team team = player.getTeam();
            if (team instanceof ScorePlayerTeam) {
                String color = DeathChatHandler.lastColorCode(
                        ((ScorePlayerTeam) team).getColorPrefix());
                if (color != null) {
                    return color;
                }
            }
            return armorTeamColor(player);
        }
        return null;
    }

    private static String armorTeamColor(EntityPlayer player) {
        if (player == null || player.inventory == null) {
            return null;
        }
        for (ItemStack stack : player.inventory.armorInventory) {
            if (stack == null || !(stack.getItem() instanceof ItemArmor)) {
                continue;
            }
            ItemArmor armor = (ItemArmor) stack.getItem();
            if (!armor.hasColor(stack)) {
                continue;
            }
            int color = armor.getColor(stack);
            int red = (color >> 16) & 255;
            int blue = color & 255;
            if (red >= blue + 32) {
                return "c";
            }
            if (blue >= red + 32) {
                return "9";
            }
        }
        return null;
    }

    private static String woolTeamColor(EntityPlayer player) {
        if (player == null || player.inventory == null) {
            return null;
        }
        Item wool = Item.getItemFromBlock(Blocks.wool);
        for (ItemStack stack : player.inventory.mainInventory) {
            if (stack != null && stack.getItem() == wool) {
                int metadata = stack.getMetadata();
                if (metadata == 14) {
                    return "c";
                }
                if (metadata == 11) {
                    return "9";
                }
            }
        }
        return null;
    }
}
