package net.weavemc.mods.timer;

import com.weave.lunarmods.HudRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.renderer.GlStateManager;
import net.weavemc.mods.servercontext.ServerContext;

/** Draws the timer as Lunar's HUD element; Lunar has already applied its position and size. */
final class TimerRenderer implements HudRenderer {
    private static final int CLOCK_COLOR = 0xFFFFFF;
    private static final int DECIMAL_COLOR = 0xB8B8B8;
    private static final float DECIMAL_SCALE = 0.75F;
    /** The text shadow is drawn one pixel right of and below the text. */
    private static final float SHADOW = 1.0F;

    private final TimerState state;

    TimerRenderer(TimerState state) {
        this.state = state;
    }

    @Override
    public float width(boolean editing) {
        TimerText text = textToShow(editing);
        FontRenderer font = font();
        if (text == null || font == null) {
            return 0.0F;
        }
        return font.getStringWidth(text.getClock())
                + font.getStringWidth(text.getDecimals()) * DECIMAL_SCALE + SHADOW;
    }

    @Override
    public float height(boolean editing) {
        FontRenderer font = font();
        return textToShow(editing) == null || font == null ? 0.0F : font.FONT_HEIGHT + SHADOW;
    }

    @Override
    public void render(float x, float y, boolean editing) {
        TimerText text = textToShow(editing);
        FontRenderer font = font();
        if (text == null || font == null) {
            return;
        }
        float clockWidth = font.getStringWidth(text.getClock());
        font.drawStringWithShadow(text.getClock(), x, y, CLOCK_COLOR);
        GlStateManager.pushMatrix();
        try {
            float decimalBaseline = font.FONT_HEIGHT * (1.0F - DECIMAL_SCALE);
            GlStateManager.translate(x + clockWidth, y + decimalBaseline, 0.0F);
            GlStateManager.scale(DECIMAL_SCALE, DECIMAL_SCALE, 1.0F);
            font.drawStringWithShadow(text.getDecimals(), 0.0F, 0.0F, DECIMAL_COLOR);
        } finally {
            GlStateManager.popMatrix();
        }
    }

    /** The running clock, a zeroed preview in the HUD editor, or null when nothing should show. */
    private TimerText textToShow(boolean editing) {
        if (ServerContext.isAllowed() && state.isVisible()) {
            return TimerText.fromMillis(state.elapsedMillis());
        }
        return editing ? TimerText.fromMillis(0L) : null;
    }

    private static FontRenderer font() {
        Minecraft minecraft = Minecraft.getMinecraft();
        return minecraft == null ? null : minecraft.fontRendererObj;
    }
}
