package net.weavemc.mods.hudeditor;

import net.minecraft.client.Minecraft;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Shared registry used by mods that participate in the universal HUD editor. */
public final class HudElementRegistry {
    private static final Map<String, EditableHudElement> ELEMENTS =
            new LinkedHashMap<String, EditableHudElement>();
    private static boolean openRequested;

    private HudElementRegistry() {
    }

    public static synchronized void register(EditableHudElement element) {
        if (element == null || element.getId() == null || element.getId().trim().isEmpty()) {
            throw new IllegalArgumentException("HUD elements require a non-empty ID");
        }
        ELEMENTS.put(element.getId(), element);
    }

    public static synchronized List<EditableHudElement> snapshot() {
        List<EditableHudElement> copy = new ArrayList<EditableHudElement>(ELEMENTS.values());
        Collections.sort(copy, new Comparator<EditableHudElement>() {
            @Override
            public int compare(EditableHudElement left, EditableHudElement right) {
                return left.getDisplayName().compareToIgnoreCase(right.getDisplayName());
            }
        });
        return Collections.unmodifiableList(copy);
    }

    /** Safe to call from commands; the editor opens on the next client tick. */
    public static synchronized void requestOpen() {
        openRequested = true;
    }

    static synchronized boolean consumeOpenRequest() {
        boolean requested = openRequested;
        openRequested = false;
        return requested;
    }

    public static boolean isEditing() {
        Minecraft minecraft = Minecraft.getMinecraft();
        return minecraft != null && minecraft.currentScreen instanceof HudEditorScreen;
    }
}
