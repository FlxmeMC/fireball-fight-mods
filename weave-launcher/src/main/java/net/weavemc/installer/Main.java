package net.weavemc.installer;

import javax.swing.SwingUtilities;
import com.formdev.flatlaf.FlatDarkLaf;

import javax.swing.JOptionPane;
import javax.swing.UIManager;
import java.io.IOException;

public final class Main {
    static final String VERSION = "1.0.9";
    private static SingleInstanceLock instanceLock;

    private Main() {
    }

    public static void main(String[] arguments) {
        if (arguments.length == 1 && "--verify-manifest".equals(arguments[0])) {
            try {
                ReleaseManifest manifest = new ManifestClient().fetch();
                System.out.println("VERIFIED_MANIFEST " + manifest.releaseVersion
                        + " files=" + manifest.files.size());
            } catch (Exception exception) {
                exception.printStackTrace();
                System.exit(1);
            }
            return;
        }
        if (!System.getProperty("os.name", "").toLowerCase(java.util.Locale.ROOT)
                .contains("windows")) {
            System.err.println("Weave Installer currently supports Windows only.");
            return;
        }
        FlatDarkLaf.setup();
        UIManager.put("Component.arc", Integer.valueOf(10));
        UIManager.put("Button.arc", Integer.valueOf(10));
        UIManager.put("ProgressBar.arc", Integer.valueOf(4));
        UIManager.put("ScrollBar.width", Integer.valueOf(8));
        try {
            instanceLock = SingleInstanceLock.acquire();
        } catch (IOException exception) {
            JOptionPane.showMessageDialog(null,
                    "Fireball Fight Mods could not create its single-instance lock.\n\n" + exception.getMessage(),
                    "Fireball Fight Mods", JOptionPane.ERROR_MESSAGE);
            return;
        }
        if (instanceLock == null) {
            JOptionPane.showMessageDialog(null,
                    "Fireball Fight Mods is already open.",
                    "Fireball Fight Mods", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        Runtime.getRuntime().addShutdownHook(new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    instanceLock.close();
                } catch (IOException ignored) {
                    // The process is already shutting down; Windows releases the lock regardless.
                }
            }
        }, "fireball-fight-mods-lock-release"));
        SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                new InstallerFrame().setVisible(true);
            }
        });
    }
}
