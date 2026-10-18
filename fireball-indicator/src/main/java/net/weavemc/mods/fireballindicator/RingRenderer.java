package net.weavemc.mods.fireballindicator;

import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.WorldRenderer;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import org.lwjgl.opengl.GL11;

import java.util.List;

final class RingRenderer {
    private static final int RING_SEGMENTS = 72;
    private static final int ARC_SEGMENTS = 24;
    private static final float RING_THICKNESS = 1.0F;
    private static final float ARC_THICKNESS = 2.0F;
    private static final double MIN_ARC_HALF_WIDTH = 22.0;
    private static final double MAX_ARC_HALF_WIDTH = 40.0;
    /** How strongly the base ring inverts what is behind it, like the vanilla crosshair. */
    private static final float RING_INVERT = 0.55F;

    void render(FireballTracker tracker, float partialTicks) {
        Minecraft minecraft = Minecraft.getMinecraft();
        EntityPlayerSP player = minecraft.thePlayer;
        if (player == null || tracker.isEmpty()
                || minecraft.gameSettings.hideGUI || minecraft.gameSettings.thirdPersonView != 0) {
            return;
        }

        long now = System.currentTimeMillis();
        List<FireballTracker.Indicator> indicators = tracker.snapshot();
        double ringAlpha = 0.0;
        for (FireballTracker.Indicator indicator : indicators) {
            ringAlpha = Math.max(ringAlpha, FireballTracker.alpha(indicator, now));
        }
        if (ringAlpha <= 0.0) {
            return;
        }

        ScaledResolution resolution = new ScaledResolution(minecraft);
        // Matches the centre pixel of the vanilla crosshair icon.
        double centerX = resolution.getScaledWidth() / 2 + 0.5;
        double centerY = resolution.getScaledHeight() / 2 + 0.5;
        float radius = FireballIndicatorSettings.radius.get();
        int fadeDistance = FireballIndicatorSettings.fadeDistance.get();
        int color = FireballIndicatorSettings.color.get();
        float arcRed = ((color >> 16) & 255) / 255.0F;
        float arcGreen = ((color >> 8) & 255) / 255.0F;
        float arcBlue = (color & 255) / 255.0F;
        float arcOpacity = ((color >>> 24) & 255) / 255.0F;
        float yaw = (float) RingMath.lerp(player.prevRotationYaw, player.rotationYaw, partialTicks);
        double playerX = RingMath.lerp(player.lastTickPosX, player.posX, partialTicks);
        double playerZ = RingMath.lerp(player.lastTickPosZ, player.posZ, partialTicks);

        GlStateManager.pushMatrix();
        GlStateManager.disableTexture2D();
        GlStateManager.disableAlpha();
        GlStateManager.disableCull();
        GlStateManager.enableBlend();
        GlStateManager.shadeModel(GL11.GL_SMOOTH);
        try {
            GlStateManager.tryBlendFuncSeparate(
                    GL11.GL_ONE_MINUS_DST_COLOR, GL11.GL_ONE_MINUS_SRC_COLOR, GL11.GL_ONE, GL11.GL_ZERO);
            float invert = (float) (RING_INVERT * ringAlpha);
            drawBand(centerX, centerY, radius - RING_THICKNESS / 2.0F, radius + RING_THICKNESS / 2.0F,
                    -180.0, 180.0, RING_SEGMENTS, invert, invert, invert, 1.0F, false);

            GlStateManager.tryBlendFuncSeparate(
                    GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ZERO);
            for (FireballTracker.Indicator indicator : indicators) {
                double alpha = FireballTracker.alpha(indicator, now);
                if (alpha <= 0.0) {
                    continue;
                }
                double fireballX = RingMath.lerp(indicator.prevX, indicator.x, partialTicks);
                double fireballZ = RingMath.lerp(indicator.prevZ, indicator.z, partialTicks);
                double angle = RingMath.relativeAngle(yaw, fireballX - playerX, fireballZ - playerZ);
                double closeness = RingMath.closeness(indicator.distance, fadeDistance);
                double halfWidth = MIN_ARC_HALF_WIDTH
                        + (MAX_ARC_HALF_WIDTH - MIN_ARC_HALF_WIDTH) * closeness;
                float arcAlpha = (float) (alpha * (0.45 + 0.55 * closeness)) * arcOpacity;
                drawBand(centerX, centerY, radius - ARC_THICKNESS / 2.0F, radius + ARC_THICKNESS / 2.0F,
                        angle - halfWidth, angle + halfWidth, ARC_SEGMENTS,
                        arcRed, arcGreen, arcBlue, arcAlpha, true);
            }
        } finally {
            GlStateManager.tryBlendFuncSeparate(
                    GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ZERO);
            GlStateManager.shadeModel(GL11.GL_FLAT);
            GlStateManager.enableCull();
            GlStateManager.enableAlpha();
            GlStateManager.enableTexture2D();
            GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
            GlStateManager.popMatrix();
        }
    }

    /**
     * Draws part of a ring between two angles (0 = up, clockwise). With {@code taper}, alpha
     * fades towards both ends so the highlight blends into the ring.
     */
    private static void drawBand(double centerX, double centerY, double innerRadius, double outerRadius,
                                 double startAngle, double endAngle, int segments,
                                 float red, float green, float blue, float alpha, boolean taper) {
        Tessellator tessellator = Tessellator.getInstance();
        WorldRenderer buffer = tessellator.getWorldRenderer();
        buffer.begin(GL11.GL_TRIANGLE_STRIP, DefaultVertexFormats.POSITION_COLOR);
        for (int index = 0; index <= segments; index++) {
            double t = index / (double) segments;
            double radians = Math.toRadians(startAngle + (endAngle - startAngle) * t);
            double sin = Math.sin(radians);
            double cos = Math.cos(radians);
            float vertexAlpha = taper ? (float) (alpha * Math.sin(Math.PI * t)) : alpha;
            buffer.pos(centerX + sin * outerRadius, centerY - cos * outerRadius, 0.0)
                    .color(red, green, blue, vertexAlpha).endVertex();
            buffer.pos(centerX + sin * innerRadius, centerY - cos * innerRadius, 0.0)
                    .color(red, green, blue, vertexAlpha).endVertex();
        }
        tessellator.draw();
    }
}
