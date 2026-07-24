package net.weavemc.mods.hudeditor;

final class HudResizeMath {
    private HudResizeMath() { }

    static Result resize(ResizeEdge edge,
                         float originalX, float originalY,
                         float originalWidth, float originalHeight,
                         float originalScale, float mouseX, float mouseY,
                         float minimumScale, float maximumScale) {
        if (edge == ResizeEdge.NONE || originalWidth <= 0.0F || originalHeight <= 0.0F
                || originalScale <= 0.0F) {
            return new Result(originalX, originalY, originalScale);
        }

        float right = originalX + originalWidth;
        float bottom = originalY + originalHeight;
        float factor;
        if (edge.isCorner()) {
            float anchorX = edge.hasLeft() ? right : originalX;
            float anchorY = edge.hasTop() ? bottom : originalY;
            float originalVectorX = edge.hasLeft() ? -originalWidth : originalWidth;
            float originalVectorY = edge.hasTop() ? -originalHeight : originalHeight;
            float currentVectorX = mouseX - anchorX;
            float currentVectorY = mouseY - anchorY;
            float denominator = originalVectorX * originalVectorX
                    + originalVectorY * originalVectorY;
            factor = (currentVectorX * originalVectorX + currentVectorY * originalVectorY)
                    / denominator;
        } else if (edge.hasLeft()) {
            factor = (right - mouseX) / originalWidth;
        } else if (edge.hasRight()) {
            factor = (mouseX - originalX) / originalWidth;
        } else if (edge.hasTop()) {
            factor = (bottom - mouseY) / originalHeight;
        } else {
            factor = (mouseY - originalY) / originalHeight;
        }

        float scale = clamp(originalScale * factor, minimumScale, maximumScale);
        float appliedFactor = scale / originalScale;
        float width = originalWidth * appliedFactor;
        float height = originalHeight * appliedFactor;

        float x;
        if (edge.hasLeft()) {
            x = right - width;
        } else if (edge.hasRight()) {
            x = originalX;
        } else {
            x = originalX + (originalWidth - width) * 0.5F;
        }

        float y;
        if (edge.hasTop()) {
            y = bottom - height;
        } else if (edge.hasBottom()) {
            y = originalY;
        } else {
            y = originalY + (originalHeight - height) * 0.5F;
        }
        return new Result(x, y, scale);
    }

    private static float clamp(float value, float minimum, float maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    static final class Result {
        final float x;
        final float y;
        final float scale;

        Result(float x, float y, float scale) {
            this.x = x;
            this.y = y;
            this.scale = scale;
        }
    }
}
