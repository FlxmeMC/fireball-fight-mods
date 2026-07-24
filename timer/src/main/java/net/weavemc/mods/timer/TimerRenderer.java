package net.weavemc.mods.timer;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;

final class TimerRenderer {
    private static final int CLOCK_COLOR = 0xFFFFFF;
    private static final int DECIMAL_COLOR = 0xB8B8B8;
    private static final float DECIMAL_SCALE = 0.75F;

    private final TimerState state;
    private final TimerConfig config;

    TimerRenderer(TimerState state, TimerConfig config) {
        this.state = state;
        this.config = config;
    }

    void render() {
        if (!state.isVisible()) {
            return;
        }
        Minecraft minecraft = Minecraft.getMinecraft();
        if (minecraft == null || minecraft.thePlayer == null || minecraft.fontRendererObj == null) {
            return;
        }

        FontRenderer font = minecraft.fontRendererObj;
        TimerText text = TimerText.fromMillis(state.elapsedMillis());
        float clockWidth = font.getStringWidth(text.getClock());
        float decimalsWidth = font.getStringWidth(text.getDecimals()) * DECIMAL_SCALE;
        float contentWidth = clockWidth + decimalsWidth;
        float contentHeight = font.FONT_HEIGHT;
        float scale = config.getScale();
        ScaledResolution screen = new ScaledResolution(minecraft);
        float x = clamp(config.getX(), 0.0F,
                Math.max(0.0F, screen.getScaledWidth() - contentWidth * scale));
        float y = clamp(config.getY(), 0.0F,
                Math.max(0.0F, screen.getScaledHeight() - contentHeight * scale));

        GlStateManager.pushMatrix();
        try {
            GlStateManager.translate(x, y, 0.0F);
            GlStateManager.scale(scale, scale, 1.0F);
            font.drawStringWithShadow(text.getClock(), 0.0F, 0.0F, CLOCK_COLOR);
            GlStateManager.pushMatrix();
            try {
                float decimalBaseline = font.FONT_HEIGHT * (1.0F - DECIMAL_SCALE);
                GlStateManager.translate(clockWidth, decimalBaseline, 0.0F);
                GlStateManager.scale(DECIMAL_SCALE, DECIMAL_SCALE, 1.0F);
                font.drawStringWithShadow(text.getDecimals(), 0.0F, 0.0F, DECIMAL_COLOR);
            } finally {
                GlStateManager.popMatrix();
            }
        } finally {
            GlStateManager.popMatrix();
        }
    }

    private static float clamp(float value, float minimum, float maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }
}
