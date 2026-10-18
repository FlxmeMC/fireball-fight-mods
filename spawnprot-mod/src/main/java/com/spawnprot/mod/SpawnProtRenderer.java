package com.spawnprot.mod;

import com.weave.lunarmods.HudRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.weavemc.mods.servercontext.ServerContext;

import java.util.ArrayList;
import java.util.List;

/** Draws the countdown panels as Lunar's HUD element; Lunar has already applied its position and size. */
public final class SpawnProtRenderer implements HudRenderer {
    private static final int LINE_HEIGHT = 10;
    private static final int PANEL_GAP = 4;
    private static final int COLOR_WHITE = 0xFFFFFF;
    /** The text shadow is drawn one pixel right of and below the text. */
    private static final float SHADOW = 1.0F;
    private static final String[] PREVIEW = {"§cOpponent DIED!", "§eRespawning in §d3.0"};

    @Override
    public float width(boolean editing) {
        FontRenderer font = font();
        if (font == null) {
            return 0.0F;
        }
        int widest = 0;
        for (String[] panel : panels(editing)) {
            for (String line : panel) {
                widest = Math.max(widest, font.getStringWidth(line));
            }
        }
        return widest == 0 ? 0.0F : widest + SHADOW;
    }

    @Override
    public float height(boolean editing) {
        FontRenderer font = font();
        int count = panels(editing).size();
        if (font == null || count == 0) {
            return 0.0F;
        }
        // Each panel is two lines; the last line's glyphs are FONT_HEIGHT tall plus the shadow.
        return count * (LINE_HEIGHT * 2 + PANEL_GAP) - PANEL_GAP - LINE_HEIGHT + font.FONT_HEIGHT + SHADOW;
    }

    @Override
    public void render(float x, float y, boolean editing) {
        FontRenderer font = font();
        if (font == null) {
            return;
        }
        float lineY = y;
        for (String[] panel : panels(editing)) {
            font.drawStringWithShadow(panel[0], x, lineY, COLOR_WHITE);
            font.drawStringWithShadow(panel[1], x, lineY + LINE_HEIGHT, COLOR_WHITE);
            lineY += LINE_HEIGHT * 2 + PANEL_GAP;
        }
    }

    /** The live panels, a sample in the HUD editor when there are none, or nothing at all. */
    private static List<String[]> panels(boolean editing) {
        List<String[]> panels = new ArrayList<String[]>();
        if (ServerContext.isAllowed() && SpawnProtState.shouldRender()) {
            if (SpawnProtState.hasSelfSpawnProt()) {
                String countdown = SpawnProtState.formatCountdown(
                        SpawnProtState.getSelfSpawnProtSecondsRemaining());
                panels.add(new String[] {
                        "§aYour Spawn Protection", "§eExpires in: §d" + countdown});
            }
            for (SpawnProtState.TrackedPlayer player : SpawnProtState.getActiveTrackedPlayers()) {
                String countdown = SpawnProtState.formatCountdown(
                        SpawnProtState.getSecondsRemaining(player));
                if (player.phase == SpawnProtState.Phase.RESPAWNING) {
                    String deathColor = player.teammate ? "§a" : "§c";
                    panels.add(new String[] {
                            deathColor + player.name + " DIED!", "§eRespawning in §d" + countdown});
                } else if (player.phase == SpawnProtState.Phase.SPAWN_PROT) {
                    panels.add(new String[] {
                            "§a" + player.name + " Spawn Protection", "§eExpires in: §d" + countdown});
                }
            }
        }
        if (panels.isEmpty() && editing) {
            panels.add(PREVIEW);
        }
        return panels;
    }

    private static FontRenderer font() {
        Minecraft minecraft = Minecraft.getMinecraft();
        return minecraft == null ? null : minecraft.fontRendererObj;
    }
}
