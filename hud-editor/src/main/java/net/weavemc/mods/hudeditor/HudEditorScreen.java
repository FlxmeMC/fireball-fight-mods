package net.weavemc.mods.hudeditor;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.renderer.GlStateManager;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

final class HudEditorScreen extends GuiScreen {
    private static final int RESET_BUTTON = 0;
    private static final int DONE_BUTTON = 1;
    private static final float SCALE_STEP = 0.1F;
    private static final float RESIZE_GRIP = 5.0F;
    private static final float SNAP_DISTANCE = 10.0F;
    private static final int HANDLE_SIZE = 5;
    private static final int GUIDE_COLOR = 0xFFFF35D3;
    private static final int GUIDE_SHADOW = 0xCC000000;
    private static final int GUIDE_TICK = 3;

    private final List<EditableHudElement> elements;
    private EditableHudElement selected;
    private EditableHudElement dragging;
    private float dragOffsetX;
    private float dragOffsetY;
    private EditableHudElement resizing;
    private ResizeEdge resizeEdge = ResizeEdge.NONE;
    private float resizeStartX;
    private float resizeStartY;
    private float resizeStartWidth;
    private float resizeStartHeight;
    private float resizeStartScale;
    private HudSnapMath.Result activeSnap;

    HudEditorScreen(List<EditableHudElement> elements) {
        this.elements = new ArrayList<EditableHudElement>(elements);
        if (!this.elements.isEmpty()) {
            selected = this.elements.get(0);
        }
    }

    @Override
    public void initGui() {
        buttonList.clear();
        buttonList.add(new GuiButton(RESET_BUTTON, width / 2 - 105, height - 28, 100, 20, "Reset"));
        buttonList.add(new GuiButton(DONE_BUTTON, width / 2 + 5, height - 28, 100, 20, "Done"));
        updateButtonState();
        clampAllToScreen();
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        drawDefaultBackground();
        if (resizing != null) {
            activeSnap = null;
            updateResize(mouseX, mouseY);
        } else if (dragging != null) {
            updateDrag(mouseX, mouseY);
        } else {
            activeSnap = null;
        }

        ResizeHit hover = resizing == null ? resizeHitAt(mouseX, mouseY) : null;

        for (EditableHudElement element : elements) {
            if (element != selected) {
                drawElement(element, mouseX, mouseY, hover);
            }
        }
        if (selected != null) {
            drawElement(selected, mouseX, mouseY, hover);
        }
        drawAlignmentGuides();

        drawCenteredString(fontRendererObj, "\u00a7b\u00a7lHUD EDITOR", width / 2, 8, 0xFFFFFF);
        drawCenteredString(fontRendererObj,
                "\u00a77Drag to move and align | Drag borders/corners to resize | ESC saves",
                width / 2, 20, 0xFFFFFF);
        if (selected == null) {
            drawCenteredString(fontRendererObj, "\u00a77No editable HUD elements are loaded",
                    width / 2, height - 40, 0xFFFFFF);
        } else {
            String status = selected.getDisplayName() + "  " + formatScale(selected.getScale());
            if (resizing != null) {
                status = "Resizing " + selected.getDisplayName() + " - " + edgeLabel(resizeEdge)
                        + "  " + formatScale(selected.getScale());
            } else if (hover != null) {
                status = "Drag " + edgeLabel(hover.edge) + " to resize " + hover.element.getDisplayName();
            }
            drawCenteredString(fontRendererObj, "\u00a7f" + status,
                    width / 2, height - 40, 0xFFFFFF);
        }
        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    private void drawElement(EditableHudElement element, int mouseX, int mouseY, ResizeHit hover) {
        float renderedWidth = renderedWidth(element);
        float renderedHeight = renderedHeight(element);
        int color = 0xFF777777;
        if (element == selected) {
            color = 0xFF00FFFF;
        }
        if (contains(element, mouseX, mouseY)) {
            color = 0xFFFFFF00;
        }
        if (element == dragging) {
            color = 0xFF00FF00;
        }

        ResizeEdge emphasized = ResizeEdge.NONE;
        if (element == resizing) {
            emphasized = resizeEdge;
        } else if (hover != null && hover.element == element) {
            emphasized = hover.edge;
        }
        boolean showHandles = element == resizing || contains(element, mouseX, mouseY)
                || hover != null && hover.element == element;
        drawResizeFrame(element, renderedWidth, renderedHeight, color, emphasized, showHandles);

        GlStateManager.pushMatrix();
        try {
            GlStateManager.translate(element.getX(), element.getY(), 0.0F);
            GlStateManager.scale(element.getScale(), element.getScale(), 1.0F);
            float y = 0.0F;
            for (String line : element.getPreviewLines()) {
                fontRendererObj.drawStringWithShadow(line, 0.0F, y, 0xFFFFFF);
                y += element.getLineHeight();
            }
        } finally {
            GlStateManager.popMatrix();
        }
    }

    @Override
    protected void actionPerformed(GuiButton button) {
        switch (button.id) {
            case RESET_BUTTON:
                if (selected != null) {
                    selected.reset();
                    clampToScreen(selected);
                }
                break;
            case DONE_BUTTON:
                closeAndSave();
                break;
            default:
                break;
        }
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) {
        try {
            // Forge 1.8.9 removes the checked IOException from this method while
            // the Weave/MCP surface retains it. Catching Exception keeps one
            // source compatible with both signatures and preserves vanilla's
            // complete button dispatch behavior.
            super.mouseClicked(mouseX, mouseY, mouseButton);
        } catch (Exception failure) {
            throw new IllegalStateException("HUD editor mouse input failed", failure);
        }
        if (mouseButton == 0 && !isOverButton(mouseX, mouseY)) {
            ResizeHit resizeHit = resizeHitAt(mouseX, mouseY);
            if (resizeHit != null) {
                beginResize(resizeHit);
                return;
            }
            EditableHudElement hit = elementAt(mouseX, mouseY);
            if (hit != null) {
                selected = hit;
                dragging = hit;
                dragOffsetX = mouseX - hit.getX();
                dragOffsetY = mouseY - hit.getY();
                updateButtonState();
            }
        }
    }

    @Override
    protected void mouseReleased(int mouseX, int mouseY, int state) {
        if (resizing != null && state == 0) {
            updateResize(mouseX, mouseY);
        } else if (dragging != null && state == 0) {
            updateDrag(mouseX, mouseY);
        }
        dragging = null;
        resizing = null;
        resizeEdge = ResizeEdge.NONE;
        activeSnap = null;
        super.mouseReleased(mouseX, mouseY, state);
    }

    @Override
    public void handleMouseInput() {
        try {
            // GuiScreen owns click, hold, and release dispatch. Skipping this call
            // makes scrolling work but prevents mouseClicked/mouseReleased from
            // ever running, so a drag can never begin.
            super.handleMouseInput();
        } catch (Exception failure) {
            throw new IllegalStateException("HUD editor mouse input failed", failure);
        }

        int wheel = Mouse.getEventDWheel();
        if (wheel == 0) {
            return;
        }
        int mouseX = Mouse.getEventX() * width / mc.displayWidth;
        int mouseY = height - Mouse.getEventY() * height / mc.displayHeight - 1;
        EditableHudElement hit = elementAt(mouseX, mouseY);
        if (hit != null) {
            selected = hit;
            adjustSelectedScale(wheel > 0 ? SCALE_STEP : -SCALE_STEP);
            updateButtonState();
        }
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) {
        if (keyCode == Keyboard.KEY_ESCAPE) {
            closeAndSave();
            return;
        }
        if (keyCode == Keyboard.KEY_R && selected != null) {
            selected.reset();
            clampToScreen(selected);
            return;
        }
    }

    @Override
    public void onGuiClosed() {
        dragging = null;
        resizing = null;
        resizeEdge = ResizeEdge.NONE;
        activeSnap = null;
        for (EditableHudElement element : elements) {
            element.save();
        }
        super.onGuiClosed();
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }

    private void adjustSelectedScale(float delta) {
        if (selected == null) {
            return;
        }
        float requested = selected.getScale() + delta;
        selected.setScale(Math.max(effectiveMinimumScale(selected),
                Math.min(selected.getMaximumScale(), requested)));
        clampToScreen(selected);
    }

    private EditableHudElement elementAt(int mouseX, int mouseY) {
        if (selected != null && contains(selected, mouseX, mouseY)) {
            return selected;
        }
        for (int index = elements.size() - 1; index >= 0; index--) {
            EditableHudElement element = elements.get(index);
            if (element != selected && contains(element, mouseX, mouseY)) {
                return element;
            }
        }
        return null;
    }

    private ResizeHit resizeHitAt(int mouseX, int mouseY) {
        ResizeHit selectedHit = resizeHit(selected, mouseX, mouseY);
        if (selectedHit != null) {
            return selectedHit;
        }
        for (int index = elements.size() - 1; index >= 0; index--) {
            EditableHudElement element = elements.get(index);
            if (element != selected) {
                ResizeHit hit = resizeHit(element, mouseX, mouseY);
                if (hit != null) {
                    return hit;
                }
            }
        }
        return null;
    }

    private ResizeHit resizeHit(EditableHudElement element, int mouseX, int mouseY) {
        if (element == null) {
            return null;
        }
        float right = element.getX() + renderedWidth(element);
        float bottom = element.getY() + renderedHeight(element);
        float grip = ResizeEdge.adaptiveGrip(
                element.getX(), element.getY(), right, bottom, RESIZE_GRIP);
        ResizeEdge edge = ResizeEdge.hitTest(
                element.getX(), element.getY(), right, bottom,
                mouseX, mouseY, grip);
        return edge == ResizeEdge.NONE ? null : new ResizeHit(element, edge);
    }

    private void beginResize(ResizeHit hit) {
        selected = hit.element;
        dragging = null;
        resizing = hit.element;
        resizeEdge = hit.edge;
        resizeStartX = hit.element.getX();
        resizeStartY = hit.element.getY();
        resizeStartWidth = renderedWidth(hit.element);
        resizeStartHeight = renderedHeight(hit.element);
        resizeStartScale = hit.element.getScale();
        updateButtonState();
    }

    private void updateResize(int mouseX, int mouseY) {
        if (resizing == null || resizeEdge == ResizeEdge.NONE) {
            return;
        }
        HudResizeMath.Result result = HudResizeMath.resize(
                resizeEdge,
                resizeStartX, resizeStartY, resizeStartWidth, resizeStartHeight,
                resizeStartScale, mouseX, mouseY,
                effectiveMinimumScale(resizing), resizing.getMaximumScale());
        resizing.setScale(result.scale);
        resizing.setPosition(result.x, result.y);
        clampToScreen(resizing);
    }

    private void updateDrag(int mouseX, int mouseY) {
        if (dragging == null) {
            activeSnap = null;
            return;
        }
        float rawX = mouseX - dragOffsetX;
        float rawY = mouseY - dragOffsetY;
        List<HudSnapMath.Bounds> targets = new ArrayList<HudSnapMath.Bounds>();
        for (EditableHudElement element : elements) {
            if (element != dragging) {
                targets.add(boundsOf(element));
            }
        }
        activeSnap = HudSnapMath.snap(rawX, rawY, renderedWidth(dragging), renderedHeight(dragging),
                targets, SNAP_DISTANCE);
        dragging.setPosition(activeSnap.x, activeSnap.y);
        clampToScreen(dragging);
    }

    private void drawAlignmentGuides() {
        if (dragging == null || activeSnap == null) {
            return;
        }
        HudSnapMath.Bounds draggedBounds = boundsOf(dragging);
        if (activeSnap.horizontal != null) {
            HudSnapMath.Bounds target = activeSnap.horizontal.target;
            int guideX = Math.round(activeSnap.horizontal.guide);
            int top = Math.round(Math.min(draggedBounds.top, target.top)) - 6;
            int bottom = Math.round(Math.max(draggedBounds.bottom, target.bottom)) + 6;
            drawVerticalLine(guideX - 1, top, bottom, GUIDE_SHADOW);
            drawVerticalLine(guideX + 1, top, bottom, GUIDE_SHADOW);
            drawVerticalLine(guideX, top, bottom, GUIDE_COLOR);
            drawHorizontalLine(guideX - GUIDE_TICK, guideX + GUIDE_TICK, top, GUIDE_COLOR);
            drawHorizontalLine(guideX - GUIDE_TICK, guideX + GUIDE_TICK, bottom, GUIDE_COLOR);
        }
        if (activeSnap.vertical != null) {
            HudSnapMath.Bounds target = activeSnap.vertical.target;
            int guideY = Math.round(activeSnap.vertical.guide);
            int left = Math.round(Math.min(draggedBounds.left, target.left)) - 6;
            int right = Math.round(Math.max(draggedBounds.right, target.right)) + 6;
            drawHorizontalLine(left, right, guideY - 1, GUIDE_SHADOW);
            drawHorizontalLine(left, right, guideY + 1, GUIDE_SHADOW);
            drawHorizontalLine(left, right, guideY, GUIDE_COLOR);
            drawVerticalLine(left, guideY - GUIDE_TICK, guideY + GUIDE_TICK, GUIDE_COLOR);
            drawVerticalLine(right, guideY - GUIDE_TICK, guideY + GUIDE_TICK, GUIDE_COLOR);
        }
    }

    private void drawResizeFrame(EditableHudElement element, float renderedWidth, float renderedHeight,
                                 int baseColor, ResizeEdge emphasized, boolean showHandles) {
        int left = (int) element.getX() - 1;
        int top = (int) element.getY() - 1;
        int right = (int) (element.getX() + renderedWidth) + 1;
        int bottom = (int) (element.getY() + renderedHeight) + 1;
        int activeColor = element == resizing ? 0xFF00FF00 : 0xFFFFFFFF;

        drawEdgeHorizontal(left, right, top,
                emphasized.hasTop() ? activeColor : baseColor, emphasized.hasTop());
        drawEdgeHorizontal(left, right, bottom,
                emphasized.hasBottom() ? activeColor : baseColor, emphasized.hasBottom());
        drawEdgeVertical(left, top, bottom,
                emphasized.hasLeft() ? activeColor : baseColor, emphasized.hasLeft());
        drawEdgeVertical(right, top, bottom,
                emphasized.hasRight() ? activeColor : baseColor, emphasized.hasRight());

        if (element == selected && showHandles) {
            drawHandle(left, top, emphasized == ResizeEdge.TOP_LEFT ? activeColor : baseColor);
            drawHandle(right, top, emphasized == ResizeEdge.TOP_RIGHT ? activeColor : baseColor);
            drawHandle(left, bottom, emphasized == ResizeEdge.BOTTOM_LEFT ? activeColor : baseColor);
            drawHandle(right, bottom, emphasized == ResizeEdge.BOTTOM_RIGHT ? activeColor : baseColor);
            drawHandle((left + right) / 2, top, emphasized == ResizeEdge.TOP ? activeColor : baseColor);
            drawHandle((left + right) / 2, bottom, emphasized == ResizeEdge.BOTTOM ? activeColor : baseColor);
            drawHandle(left, (top + bottom) / 2, emphasized == ResizeEdge.LEFT ? activeColor : baseColor);
            drawHandle(right, (top + bottom) / 2, emphasized == ResizeEdge.RIGHT ? activeColor : baseColor);
        }
    }

    private void drawEdgeHorizontal(int left, int right, int y, int color, boolean thick) {
        drawHorizontalLine(left, right, y, color);
        if (thick) {
            drawHorizontalLine(left, right, y - 1, color);
            drawHorizontalLine(left, right, y + 1, color);
        }
    }

    private void drawEdgeVertical(int x, int top, int bottom, int color, boolean thick) {
        drawVerticalLine(x, top, bottom, color);
        if (thick) {
            drawVerticalLine(x - 1, top, bottom, color);
            drawVerticalLine(x + 1, top, bottom, color);
        }
    }

    private void drawHandle(int centerX, int centerY, int color) {
        int half = HANDLE_SIZE / 2;
        drawRect(centerX - half, centerY - half,
                centerX + half + 1, centerY + half + 1, 0xFF000000);
        drawRect(centerX - half + 1, centerY - half + 1,
                centerX + half, centerY + half, color);
    }

    private static String edgeLabel(ResizeEdge edge) {
        return edge.name().toLowerCase(Locale.ROOT).replace('_', ' ') + " edge";
    }

    private static final class ResizeHit {
        private final EditableHudElement element;
        private final ResizeEdge edge;

        private ResizeHit(EditableHudElement element, ResizeEdge edge) {
            this.element = element;
            this.edge = edge;
        }
    }

    private boolean contains(EditableHudElement element, int mouseX, int mouseY) {
        return mouseX >= element.getX() && mouseX <= element.getX() + renderedWidth(element)
                && mouseY >= element.getY() && mouseY <= element.getY() + renderedHeight(element);
    }

    private HudSnapMath.Bounds boundsOf(EditableHudElement element) {
        return new HudSnapMath.Bounds(
                element.getX(), element.getY(),
                element.getX() + renderedWidth(element),
                element.getY() + renderedHeight(element));
    }

    private boolean isOverButton(int mouseX, int mouseY) {
        for (Object item : buttonList) {
            GuiButton button = (GuiButton) item;
            if (button.mousePressed(mc, mouseX, mouseY)) {
                return true;
            }
        }
        return false;
    }

    private void clampAllToScreen() {
        for (EditableHudElement element : elements) {
            clampToScreen(element);
        }
    }

    private void clampToScreen(EditableHudElement element) {
        element.setScale(Math.max(effectiveMinimumScale(element),
                Math.min(element.getMaximumScale(), element.getScale())));
        float maximumX = Math.max(0.0F, width - renderedWidth(element));
        float maximumY = Math.max(0.0F, height - renderedHeight(element));
        element.setPosition(
                Math.max(0.0F, Math.min(element.getX(), maximumX)),
                Math.max(0.0F, Math.min(element.getY(), maximumY)));
    }

    private float renderedWidth(EditableHudElement element) {
        return unscaledWidth(element) * element.getScale();
    }

    private float unscaledWidth(EditableHudElement element) {
        int previewWidth = 0;
        for (String line : element.getPreviewLines()) {
            previewWidth = Math.max(previewWidth, fontRendererObj.getStringWidth(line));
        }
        return previewWidth;
    }

    private float renderedHeight(EditableHudElement element) {
        return unscaledHeight(element) * element.getScale();
    }

    private float unscaledHeight(EditableHudElement element) {
        return element.getPreviewLines().length * element.getLineHeight();
    }

    private float effectiveMinimumScale(EditableHudElement element) {
        return Math.min(element.getMaximumScale(), element.getMinimumScale());
    }

    private void updateButtonState() {
        boolean hasSelection = selected != null;
        for (Object item : buttonList) {
            GuiButton button = (GuiButton) item;
            if (button.id == RESET_BUTTON) {
                button.enabled = hasSelection;
            }
        }
    }

    private void closeAndSave() {
        mc.displayGuiScreen(null);
    }

    private static String formatScale(float scale) {
        return String.format(Locale.ROOT, "%.1fx", scale);
    }
}
