package com.spawnprot.mod;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.renderer.GlStateManager;

import java.util.List;

public final class SpawnProtRenderer {
    private static final int LINE_HEIGHT = 10;
    private static final int PANEL_GAP = 4;
    private static final int COLOR_WHITE = 0xFFFFFF;

    public void onRenderOverlay() {
        if (!SpawnProtState.shouldRender()) {
            return;
        }
        Minecraft minecraft = Minecraft.getMinecraft();
        if (minecraft == null || minecraft.fontRendererObj == null) {
            return;
        }

        FontRenderer fontRenderer = minecraft.fontRendererObj;
        GlStateManager.pushMatrix();
        try {
            GlStateManager.translate(SpawnProtState.hudX, SpawnProtState.hudY, 0.0F);
            GlStateManager.scale(SpawnProtState.hudScale, SpawnProtState.hudScale, 1.0F);
            float y = 0.0F;
            if (SpawnProtState.hasSelfSpawnProt()) {
                String countdown = SpawnProtState.formatCountdown(
                        SpawnProtState.getSelfSpawnProtSecondsRemaining());
                y = drawPanel(fontRenderer,
                        "\u00A7aYour Spawn Protection",
                        "\u00A7eExpires in: \u00A7d" + countdown, y);
            }

            List<SpawnProtState.TrackedPlayer> players = SpawnProtState.getActiveTrackedPlayers();
            for (SpawnProtState.TrackedPlayer player : players) {
                String countdown = SpawnProtState.formatCountdown(
                        SpawnProtState.getSecondsRemaining(player));
                if (player.phase == SpawnProtState.Phase.RESPAWNING) {
                    String deathColor = player.teammate ? "\u00A7a" : "\u00A7c";
                    y = drawPanel(fontRenderer,
                            deathColor + player.name + " DIED!",
                            "\u00A7eRespawning in \u00A7d" + countdown, y);
                } else if (player.phase == SpawnProtState.Phase.SPAWN_PROT) {
                    y = drawPanel(fontRenderer,
                            "\u00A7a" + player.name + " Spawn Protection",
                            "\u00A7eExpires in: \u00A7d" + countdown, y);
                }
            }
        } finally {
            GlStateManager.popMatrix();
        }
    }

    private static float drawPanel(FontRenderer fontRenderer, String first, String second, float y) {
        fontRenderer.drawStringWithShadow(first, 0.0F, y, COLOR_WHITE);
        fontRenderer.drawStringWithShadow(second, 0.0F, y + LINE_HEIGHT, COLOR_WHITE);
        return y + LINE_HEIGHT * 2 + PANEL_GAP;
    }
}
