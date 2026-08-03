package net.weavemc.installer;

import org.junit.Test;

import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import java.awt.Component;
import java.awt.Container;
import java.awt.Point;
import java.lang.reflect.Constructor;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

public final class InstallerFrameLayoutTest {
    @Test
    public void checkboxAndSettingsUseOneRightEdgeAcrossCards() throws Exception {
        JPanel withSettings = createCard(true);
        JPanel withoutSettings = createCard(false);

        layoutTree(withSettings, 364, 140);
        layoutTree(withoutSettings, 364, 140);

        JCheckBox settingsCheckbox = find(withSettings, JCheckBox.class, null);
        JButton settingsButton = find(withSettings, JButton.class, "Settings");
        JCheckBox plainCheckbox = find(withoutSettings, JCheckBox.class, null);
        assertNotNull(settingsCheckbox);
        assertNotNull(settingsButton);
        assertNotNull(plainCheckbox);

        int expectedRight = rightEdge(settingsButton, withSettings);
        assertEquals(expectedRight, rightEdge(settingsCheckbox, withSettings));
        assertEquals(expectedRight, rightEdge(plainCheckbox, withoutSettings));
    }

    private static JPanel createCard(boolean settings) throws Exception {
        ReleaseManifest.ModInfo mod = new ReleaseManifest.ModInfo();
        mod.id = "timer";
        mod.name = "Timer";
        mod.version = "1.0.0";
        mod.description = "Automatic match timer";
        Class<?> type = Class.forName("net.weavemc.installer.InstallerFrame$ModCard");
        Constructor<?> constructor = type.getDeclaredConstructor(
                ReleaseManifest.ModInfo.class, Runnable.class, Runnable.class);
        constructor.setAccessible(true);
        Runnable settingsAction = settings ? new Runnable() {
            @Override public void run() { }
        } : null;
        return (JPanel) constructor.newInstance(mod, settingsAction, new Runnable() {
            @Override public void run() { }
        });
    }

    private static void layoutTree(Container container, int width, int height) {
        container.setSize(width, height);
        container.doLayout();
        for (Component component : container.getComponents()) {
            if (component instanceof Container) {
                Container child = (Container) component;
                child.doLayout();
                layoutChildren(child);
            }
        }
    }

    private static void layoutChildren(Container container) {
        container.doLayout();
        for (Component component : container.getComponents()) {
            if (component instanceof Container) {
                layoutChildren((Container) component);
            }
        }
    }

    private static int rightEdge(Component component, Container card) {
        Point point = SwingUtilities.convertPoint(component.getParent(),
                component.getX(), component.getY(), card);
        return point.x + component.getWidth();
    }

    private static <T extends Component> T find(
            Container container, Class<T> type, String text) {
        for (Component component : container.getComponents()) {
            if (type.isInstance(component)
                    && (text == null || text.equals(((JButton) component).getText()))) {
                return type.cast(component);
            }
            if (component instanceof Container) {
                T nested = find((Container) component, type, text);
                if (nested != null) {
                    return nested;
                }
            }
        }
        return null;
    }
}
