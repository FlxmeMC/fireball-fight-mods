package net.weavemc.mods.hudeditor;

import java.util.List;

final class HudSnapMath {
    private HudSnapMath() {
    }

    static Result snap(float x, float y, float width, float height,
                       List<Bounds> targets, float threshold) {
        AxisSnap horizontal = bestAxisSnap(x, width, targets, threshold, true);
        AxisSnap vertical = bestAxisSnap(y, height, targets, threshold, false);
        return new Result(
                horizontal == null ? x : horizontal.position,
                vertical == null ? y : vertical.position,
                horizontal,
                vertical);
    }

    private static AxisSnap bestAxisSnap(float position, float size, List<Bounds> targets,
                                         float threshold, boolean horizontal) {
        AxisSnap best = null;
        for (Bounds target : targets) {
            float targetStart = horizontal ? target.left : target.top;
            float targetSize = horizontal ? target.width() : target.height();
            float targetCenter = targetStart + targetSize / 2.0F;
            float targetEnd = targetStart + targetSize;
            float[][] relationships = {
                    {0.0F, targetStart},
                    {size / 2.0F, targetCenter},
                    {size, targetEnd},
                    {0.0F, targetEnd},
                    {size, targetStart}
            };
            for (float[] relationship : relationships) {
                float sourceOffset = relationship[0];
                float targetAnchor = relationship[1];
                float candidate = targetAnchor - sourceOffset;
                float distance = Math.abs(candidate - position);
                if (distance <= threshold && (best == null || distance < best.distance)) {
                    best = new AxisSnap(candidate, targetAnchor, distance, target);
                }
            }
        }
        return best;
    }

    static final class Bounds {
        final float left;
        final float top;
        final float right;
        final float bottom;

        Bounds(float left, float top, float right, float bottom) {
            this.left = left;
            this.top = top;
            this.right = right;
            this.bottom = bottom;
        }

        float width() {
            return right - left;
        }

        float height() {
            return bottom - top;
        }
    }

    static final class AxisSnap {
        final float position;
        final float guide;
        final float distance;
        final Bounds target;

        private AxisSnap(float position, float guide, float distance, Bounds target) {
            this.position = position;
            this.guide = guide;
            this.distance = distance;
            this.target = target;
        }
    }

    static final class Result {
        final float x;
        final float y;
        final AxisSnap horizontal;
        final AxisSnap vertical;

        private Result(float x, float y, AxisSnap horizontal, AxisSnap vertical) {
            this.x = x;
            this.y = y;
            this.horizontal = horizontal;
            this.vertical = vertical;
        }
    }
}
