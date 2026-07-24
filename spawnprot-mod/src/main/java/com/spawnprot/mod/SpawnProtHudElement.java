package com.spawnprot.mod;

import net.weavemc.mods.hudeditor.EditableHudElement;

final class SpawnProtHudElement implements EditableHudElement {
    @Override
    public String getId() {
        return "spawnprot";
    }

    @Override
    public String getDisplayName() {
        return "SpawnProt";
    }

    @Override
    public float getX() {
        return SpawnProtState.hudX;
    }

    @Override
    public float getY() {
        return SpawnProtState.hudY;
    }

    @Override
    public float getScale() {
        return SpawnProtState.hudScale;
    }

    @Override
    public float getMinimumScale() {
        return SpawnProtState.MIN_HUD_SCALE;
    }

    @Override
    public float getMaximumScale() {
        return SpawnProtState.MAX_HUD_SCALE;
    }

    @Override
    public void setPosition(float x, float y) {
        SpawnProtState.hudX = Math.max(0.0F, x);
        SpawnProtState.hudY = Math.max(0.0F, y);
    }

    @Override
    public void setScale(float scale) {
        SpawnProtState.hudScale = Math.max(
                SpawnProtState.MIN_HUD_SCALE,
                Math.min(SpawnProtState.MAX_HUD_SCALE, scale));
    }

    @Override
    public String[] getPreviewLines() {
        return new String[] {
                "\u00A7cOpponent DIED!",
                "\u00A7eRespawning in \u00A7d3.0"
        };
    }

    @Override
    public int getLineHeight() {
        return 10;
    }

    @Override
    public void reset() {
        SpawnProtState.resetHud();
    }

    @Override
    public void save() {
        SpawnProtState.saveConfig();
    }
}
