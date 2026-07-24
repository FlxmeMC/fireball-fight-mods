package net.weavemc.mods.timer;

import net.weavemc.mods.hudeditor.EditableHudElement;

final class TimerHudElement implements EditableHudElement {
    private final TimerState state;
    private final TimerConfig config;

    TimerHudElement(TimerState state, TimerConfig config) {
        this.state = state;
        this.config = config;
    }

    @Override
    public String getId() {
        return "timer";
    }

    @Override
    public String getDisplayName() {
        return "Timer";
    }

    @Override
    public float getX() {
        return config.getX();
    }

    @Override
    public float getY() {
        return config.getY();
    }

    @Override
    public float getScale() {
        return config.getScale();
    }

    @Override
    public float getMinimumScale() {
        return TimerConfig.MIN_SCALE;
    }

    @Override
    public float getMaximumScale() {
        return TimerConfig.MAX_SCALE;
    }

    @Override
    public void setPosition(float x, float y) {
        config.setPosition(x, y);
    }

    @Override
    public void setScale(float scale) {
        config.setScale(scale);
    }

    @Override
    public String[] getPreviewLines() {
        TimerText text = state.isVisible()
                ? TimerText.fromMillis(state.elapsedMillis())
                : TimerText.fromMillis(0L);
        return new String[] {text.plainText()};
    }

    @Override
    public int getLineHeight() {
        return 9;
    }

    @Override
    public void reset() {
        config.reset();
    }

    @Override
    public void save() {
        config.save();
    }
}
