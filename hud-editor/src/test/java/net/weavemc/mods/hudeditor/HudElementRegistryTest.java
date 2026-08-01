package net.weavemc.mods.hudeditor;

import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

public final class HudElementRegistryTest {
    @Test
    public void duplicateIdReplacesExistingElement() {
        FakeElement first = new FakeElement("registry-replace", "Zulu");
        FakeElement replacement = new FakeElement("registry-replace", "Alpha");

        HudElementRegistry.register(first);
        HudElementRegistry.register(replacement);

        assertSame(replacement, find(HudElementRegistry.snapshot(), "registry-replace"));
    }

    @Test
    public void snapshotIsSortedAndImmutable() {
        HudElementRegistry.register(new FakeElement("registry-zulu", "Zulu"));
        HudElementRegistry.register(new FakeElement("registry-alpha", "Alpha"));

        List<EditableHudElement> snapshot = HudElementRegistry.snapshot();
        assertTrue(indexOf(snapshot, "registry-alpha") < indexOf(snapshot, "registry-zulu"));

        boolean immutable = false;
        try {
            snapshot.clear();
        } catch (UnsupportedOperationException expected) {
            immutable = true;
        }
        assertTrue(immutable);
    }

    private static EditableHudElement find(List<EditableHudElement> elements, String id) {
        for (EditableHudElement element : elements) {
            if (id.equals(element.getId())) {
                return element;
            }
        }
        return null;
    }

    private static int indexOf(List<EditableHudElement> elements, String id) {
        for (int index = 0; index < elements.size(); index++) {
            if (id.equals(elements.get(index).getId())) {
                return index;
            }
        }
        return -1;
    }

    private static final class FakeElement implements EditableHudElement {
        private final String id;
        private final String name;

        private FakeElement(String id, String name) {
            this.id = id;
            this.name = name;
        }

        @Override public String getId() { return id; }
        @Override public String getDisplayName() { return name; }
        @Override public float getX() { return 0.0F; }
        @Override public float getY() { return 0.0F; }
        @Override public float getScale() { return 1.0F; }
        @Override public float getMinimumScale() { return 0.5F; }
        @Override public float getMaximumScale() { return 5.0F; }
        @Override public void setPosition(float x, float y) { }
        @Override public void setScale(float scale) { }
        @Override public String[] getPreviewLines() { return new String[] {name}; }
        @Override public int getLineHeight() { return 9; }
        @Override public void reset() { }
        @Override public void save() { }
    }
}
