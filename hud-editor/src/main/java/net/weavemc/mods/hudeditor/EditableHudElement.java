package net.weavemc.mods.hudeditor;

/** A HUD preview whose placement can be edited by the shared HUD editor. */
public interface EditableHudElement {
    String getId();

    String getDisplayName();

    float getX();

    float getY();

    float getScale();

    float getMinimumScale();

    float getMaximumScale();

    void setPosition(float x, float y);

    void setScale(float scale);

    String[] getPreviewLines();

    int getLineHeight();

    void reset();

    void save();
}
