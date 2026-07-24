package net.weavemc.mods.hudeditor;

enum ResizeEdge {
    NONE(false, false, false, false),
    LEFT(true, false, false, false),
    RIGHT(false, true, false, false),
    TOP(false, false, true, false),
    BOTTOM(false, false, false, true),
    TOP_LEFT(true, false, true, false),
    TOP_RIGHT(false, true, true, false),
    BOTTOM_LEFT(true, false, false, true),
    BOTTOM_RIGHT(false, true, false, true);

    private final boolean left;
    private final boolean right;
    private final boolean top;
    private final boolean bottom;

    ResizeEdge(boolean left, boolean right, boolean top, boolean bottom) {
        this.left = left;
        this.right = right;
        this.top = top;
        this.bottom = bottom;
    }

    boolean hasLeft() { return left; }
    boolean hasRight() { return right; }
    boolean hasTop() { return top; }
    boolean hasBottom() { return bottom; }
    boolean isCorner() { return (left || right) && (top || bottom); }

    static float adaptiveGrip(float left, float top, float right, float bottom, float maximum) {
        float widthAllowance = Math.max(0.0F, right - left) / 4.0F;
        float heightAllowance = Math.max(0.0F, bottom - top) / 4.0F;
        return Math.max(1.5F, Math.min(maximum, Math.min(widthAllowance, heightAllowance)));
    }

    static ResizeEdge hitTest(float left, float top, float right, float bottom,
                              int mouseX, int mouseY, float grip) {
        if (mouseX < left - grip || mouseX > right + grip
                || mouseY < top - grip || mouseY > bottom + grip) {
            return NONE;
        }

        boolean nearLeft = Math.abs(mouseX - left) <= grip;
        boolean nearRight = Math.abs(mouseX - right) <= grip;
        boolean nearTop = Math.abs(mouseY - top) <= grip;
        boolean nearBottom = Math.abs(mouseY - bottom) <= grip;

        if (nearLeft && nearTop) return TOP_LEFT;
        if (nearRight && nearTop) return TOP_RIGHT;
        if (nearLeft && nearBottom) return BOTTOM_LEFT;
        if (nearRight && nearBottom) return BOTTOM_RIGHT;
        if (nearLeft && mouseY >= top && mouseY <= bottom) return LEFT;
        if (nearRight && mouseY >= top && mouseY <= bottom) return RIGHT;
        if (nearTop && mouseX >= left && mouseX <= right) return TOP;
        if (nearBottom && mouseX >= left && mouseX <= right) return BOTTOM;
        return NONE;
    }
}
