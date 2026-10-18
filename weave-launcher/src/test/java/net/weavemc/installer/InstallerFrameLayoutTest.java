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
import static org.junit.Assert.assertNull;

public final class InstallerFrameLayoutTest {
    @Test
    public void cardsHaveOnlyACheckboxAlignedToTheSameRightEdge() throws Exception {
        JPanel timer = createCard("timer", "Timer");
        JPanel blockhit = createCard("blockhit-sound", "Blockhit Sound");

        layoutTree(timer, 364, 140);
        layoutTree(blockhit, 364, 140);

        JCheckBox timerCheckbox = find(timer, JCheckBox.class, null);
        JCheckBox blockhitCheckbox = find(blockhit, JCheckBox.class, null);
        assertNotNull(timerCheckbox);
        assertNotNull(blockhitCheckbox);
        assertNull(find(blockhit, JButton.class, "Settings"));
        assertEquals(rightEdge(timerCheckbox, timer), rightEdge(blockhitCheckbox, blockhit));
    }

    private static JPanel createCard(String id, String name) throws Exception {
        ReleaseManifest.ModInfo mod = new ReleaseManifest.ModInfo();
        mod.id = id;
        mod.name = name;
        mod.version = "1.0.0";
        mod.description = "Fixture";
        Class<?> type = Class.forName("net.weavemc.installer.InstallerFrame$ModCard");
        Constructor<?> constructor = type.getDeclaredConstructor(
                ReleaseManifest.ModInfo.class, Runnable.class);
        constructor.setAccessible(true);
        return (JPanel) constructor.newInstance(mod, new Runnable() {
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
