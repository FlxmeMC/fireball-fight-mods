package net.weavemc.installer;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ImageIcon;
import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.imageio.ImageIO;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Desktop;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridLayout;
import java.awt.Insets;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.net.URI;
import java.net.URL;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

final class InstallerFrame extends JFrame implements InstallService.Progress {
    private static final long serialVersionUID = 1L;
    private static final String TERMS_URL = "https://fbfight-updates.flxme.cc/terms/";
    private static final Color CANVAS = new Color(15, 16, 18);
    private static final Color SURFACE = new Color(23, 24, 27);
    private static final Color SURFACE_HOVER = new Color(28, 29, 33);
    private static final Color BORDER = new Color(47, 49, 55);
    private static final Color TEXT = new Color(244, 244, 245);
    private static final Color MUTED = new Color(157, 160, 168);
    private static final Color ACCENT = new Color(245, 159, 66);
    private static final String CREATOR_CREDIT =
            "Mods created by Flxme, original ideas by adoring_";

    private final JPanel modGrid = new JPanel(new GridLayout(0, 2, 12, 12));
    private final JLabel operationStatus = new JLabel("Connecting securely");
    private final JProgressBar progress = new JProgressBar(0, 100);
    private final JButton installButton = new JButton("Install selected mods");
    private final JButton uninstallButton = new JButton("Uninstall");
    private final JButton diagnosticsButton = new JButton("Logs");
    private final JButton retryButton = new JButton("Retry");
    private final Map<String, ModCard> modCards = new LinkedHashMap<String, ModCard>();
    private final InstallService service = new InstallService();
    private ReleaseManifest manifest;
    private boolean installationCompleted;
    private boolean pendingChanges;
    private boolean updatePromptShown;

    InstallerFrame() {
        super(ManifestClient.isLocalMode() ? "Fireball Fight Mods - Local build" : "Fireball Fight Mods");
        ImageIcon appIcon = loadInstallerIcon(32);
        if (appIcon.getIconWidth() > 0) {
            setIconImage(appIcon.getImage());
        }
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setMinimumSize(new Dimension(720, 560));
        setSize(1040, 860);
        java.awt.Rectangle screen = java.awt.GraphicsEnvironment.getLocalGraphicsEnvironment().getMaximumWindowBounds();
        setSize(Math.min(getWidth(), screen.width), Math.min(getHeight(), screen.height));
        setLocationRelativeTo(null);
        getContentPane().setBackground(CANVAS);
        setLayout(new BorderLayout());

        JPanel content = new JPanel(new BorderLayout(0, 28));
        content.setBackground(CANVAS);
        content.setBorder(BorderFactory.createEmptyBorder(36, 42, 30, 42));
        content.add(buildHeader(), BorderLayout.NORTH);
        content.add(buildModArea(), BorderLayout.CENTER);
        content.add(buildFooter(), BorderLayout.SOUTH);
        add(content, BorderLayout.CENTER);

        installButton.addActionListener(event -> install());
        uninstallButton.addActionListener(event -> uninstall());
        diagnosticsButton.addActionListener(event -> DiagnosticsWindow.open());
        retryButton.addActionListener(event -> loadManifest());
        setBusy(true);
        loadManifest();
    }

    private JPanel buildHeader() {
        JPanel header = new JPanel(new BorderLayout());
        header.setOpaque(false);
        JPanel copy = new JPanel();
        copy.setOpaque(false);
        copy.setLayout(new BoxLayout(copy, BoxLayout.Y_AXIS));
        JLabel title = new JLabel("Fireball Fight Mods");
        title.setForeground(TEXT);
        title.setFont(new Font("Segoe UI", Font.BOLD, 30));
        JPanel titleRow = new JPanel();
        titleRow.setOpaque(false);
        titleRow.setLayout(new BoxLayout(titleRow, BoxLayout.X_AXIS));
        titleRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        titleRow.add(new JLabel(loadInstallerIcon(30)));
        titleRow.add(Box.createHorizontalStrut(10));
        titleRow.add(title);
        JLabel creatorCredit = new JLabel(new CreditIcon(12, MUTED));
        creatorCredit.setToolTipText(CREATOR_CREDIT);
        creatorCredit.getAccessibleContext().setAccessibleName("Creator credits");
        creatorCredit.getAccessibleContext().setAccessibleDescription(CREATOR_CREDIT);
        creatorCredit.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent event) {
                creatorCredit.setIcon(new CreditIcon(12, ACCENT));
            }

            @Override
            public void mouseExited(MouseEvent event) {
                creatorCredit.setIcon(new CreditIcon(12, MUTED));
            }
        });
        JLabel subtitle = new JLabel(ManifestClient.isLocalMode()
                ? "Local build"
                : "Download mods for completely free in seconds.");
        subtitle.setForeground(MUTED);
        subtitle.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        JPanel subtitleRow = new JPanel();
        subtitleRow.setOpaque(false);
        subtitleRow.setLayout(new BoxLayout(subtitleRow, BoxLayout.X_AXIS));
        subtitleRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        subtitleRow.add(subtitle);
        subtitleRow.add(Box.createHorizontalStrut(6));
        subtitleRow.add(creatorCredit);
        copy.add(titleRow);
        copy.add(Box.createVerticalStrut(5));
        copy.add(subtitleRow);
        header.add(copy, BorderLayout.WEST);

        JButton discordButton = new JButton("Join Discord");
        styleSecondaryButton(discordButton);
        discordButton.setForeground(ACCENT);
        discordButton.addActionListener(event -> openDiscord());
        JPanel discordArea = new JPanel(new BorderLayout());
        discordArea.setOpaque(false);
        discordArea.setBorder(BorderFactory.createEmptyBorder(2, 18, 0, 0));
        discordArea.add(discordButton, BorderLayout.NORTH);
        header.add(discordArea, BorderLayout.EAST);
        return header;
    }

    private static final class CreditIcon implements Icon {
        private final int size;
        private final Color color;

        private CreditIcon(int size, Color color) {
            this.size = size;
            this.color = color;
        }

        @Override
        public int getIconWidth() {
            return size;
        }

        @Override
        public int getIconHeight() {
            return size;
        }

        @Override
        public void paintIcon(Component component, Graphics graphics, int x, int y) {
            Graphics2D graphics2D = (Graphics2D) graphics.create();
            try {
                graphics2D.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                        RenderingHints.VALUE_ANTIALIAS_ON);
                graphics2D.setColor(color);
                graphics2D.drawOval(x + 1, y + 1, size - 3, size - 3);
                graphics2D.fillOval(x + size / 2 - 1, y + 4, 3, 3);
                graphics2D.fillRoundRect(x + size / 2 - 1, y + 8, 3, 6, 2, 2);
            } finally {
                graphics2D.dispose();
            }
        }
    }

    private static ImageIcon loadInstallerIcon(int size) {
        URL resource = InstallerFrame.class.getResource("/icons/app.png");
        if (resource == null) {
            return new ImageIcon();
        }
        try {
            BufferedImage source = trimTransparentPadding(ImageIO.read(resource));
            double scale = Math.min((double) size / source.getWidth(),
                    (double) size / source.getHeight());
            int width = Math.max(1, (int) Math.round(source.getWidth() * scale));
            int height = Math.max(1, (int) Math.round(source.getHeight() * scale));
            BufferedImage scaled = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
            Graphics2D graphics = scaled.createGraphics();
            try {
                graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                        RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
                graphics.drawImage(source, (size - width) / 2, (size - height) / 2,
                        width, height, null);
            } finally {
                graphics.dispose();
            }
            return new ImageIcon(scaled);
        } catch (Exception exception) {
            return new ImageIcon();
        }
    }

    private static BufferedImage trimTransparentPadding(BufferedImage source) {
        int minX = source.getWidth();
        int minY = source.getHeight();
        int maxX = -1;
        int maxY = -1;
        for (int y = 0; y < source.getHeight(); y++) {
            for (int x = 0; x < source.getWidth(); x++) {
                if ((source.getRGB(x, y) >>> 24) > 8) {
                    minX = Math.min(minX, x);
                    minY = Math.min(minY, y);
                    maxX = Math.max(maxX, x);
                    maxY = Math.max(maxY, y);
                }
            }
        }
        if (maxX < minX || maxY < minY) {
            return source;
        }
        return source.getSubimage(minX, minY, maxX - minX + 1, maxY - minY + 1);
    }

    private void openDiscord() {
        try {
            if (!Desktop.isDesktopSupported()
                    || !Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                throw new IllegalStateException("Opening web links is not supported on this computer.");
            }
            Desktop.getDesktop().browse(new URI("https://fbfight.flxme.cc/"));
        } catch (Exception exception) {
            showError("Could not open the Discord link.\nhttps://fbfight.flxme.cc/");
        }
    }

    private Component buildModArea() {
        JPanel area = new JPanel(new BorderLayout(0, 12));
        area.setOpaque(false);
        JLabel section = new JLabel("Available mods");
        section.setForeground(TEXT);
        section.setFont(new Font("Segoe UI", Font.BOLD, 14));
        area.add(section, BorderLayout.NORTH);
        modGrid.setOpaque(false);
        JScrollPane scroll = new JScrollPane(modGrid,
                JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED, JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        scroll.setOpaque(false);
        scroll.getViewport().setOpaque(false);
        scroll.getVerticalScrollBar().setUnitIncrement(24);
        area.add(scroll, BorderLayout.CENTER);
        return area;
    }

    private JPanel buildFooter() {
        JPanel footer = new JPanel(new BorderLayout(0, 14));
        footer.setOpaque(false);
        JPanel state = new JPanel(new BorderLayout());
        state.setOpaque(false);
        operationStatus.setForeground(MUTED);
        operationStatus.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        state.add(operationStatus, BorderLayout.WEST);
        styleLinkButton(retryButton);
        retryButton.setToolTipText("Try to reach the release server again");
        retryButton.setVisible(false);
        state.add(retryButton, BorderLayout.EAST);
        footer.add(state, BorderLayout.NORTH);

        progress.setValue(0);
        progress.setStringPainted(false);
        progress.setForeground(ACCENT);
        progress.setBackground(new Color(35, 36, 40));
        progress.setPreferredSize(new Dimension(100, 4));
        progress.setBorder(BorderFactory.createEmptyBorder());
        footer.add(progress, BorderLayout.CENTER);

        JPanel bottom = new JPanel();
        bottom.setOpaque(false);
        bottom.setLayout(new BoxLayout(bottom, BoxLayout.Y_AXIS));

        JPanel consent = new JPanel(new FlowLayout(FlowLayout.RIGHT, 5, 0));
        consent.setOpaque(false);
        consent.setAlignmentX(Component.RIGHT_ALIGNMENT);
        JLabel consentCopy = new JLabel("By downloading these free mods, you agree to the");
        consentCopy.setForeground(MUTED);
        consentCopy.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        JButton termsButton = new JButton("Terms of Service");
        styleLinkButton(termsButton);
        termsButton.setToolTipText(TERMS_URL);
        termsButton.addActionListener(event -> openTerms());
        consent.add(consentCopy);
        consent.add(termsButton);
        bottom.add(consent);
        bottom.add(Box.createVerticalStrut(10));

        JPanel actions = new JPanel(new BorderLayout(10, 0));
        actions.setOpaque(false);
        actions.setAlignmentX(Component.RIGHT_ALIGNMENT);
        stylePrimaryButton(installButton);
        styleSecondaryButton(uninstallButton);
        styleSecondaryButton(diagnosticsButton);
        diagnosticsButton.setPreferredSize(uninstallButton.getPreferredSize());
        JPanel secondaryActions = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        secondaryActions.setOpaque(false);
        secondaryActions.add(uninstallButton);
        secondaryActions.add(diagnosticsButton);
        actions.add(secondaryActions, BorderLayout.WEST);
        actions.add(installButton, BorderLayout.EAST);
        bottom.add(actions);
        footer.add(bottom, BorderLayout.SOUTH);
        return footer;
    }

    private static void styleLinkButton(JButton button) {
        button.setForeground(ACCENT);
        button.setFont(new Font("Segoe UI", Font.BOLD, 11));
        button.setContentAreaFilled(false);
        button.setFocusPainted(false);
        button.setBorder(BorderFactory.createEmptyBorder(2, 0, 2, 0));
        button.setMargin(new Insets(0, 0, 0, 0));
        button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
    }

    private static void stylePrimaryButton(JButton button) {
        button.setBackground(ACCENT);
        button.setForeground(new Color(24, 20, 16));
        button.setFont(new Font("Segoe UI", Font.BOLD, 13));
        button.setFocusPainted(false);
        button.setBorder(BorderFactory.createEmptyBorder(11, 22, 11, 22));
        button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
    }

    private static void styleSecondaryButton(JButton button) {
        button.setBackground(SURFACE);
        button.setForeground(MUTED);
        button.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        button.setFocusPainted(false);
        button.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(BORDER),
                BorderFactory.createEmptyBorder(10, 17, 10, 17)));
        button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
    }

    private void openTerms() {
        openWebPage(TERMS_URL, "Terms of Service");
    }

    private void openWebPage(String url, String label) {
        try {
            if (!Desktop.isDesktopSupported()
                    || !Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                throw new IllegalStateException("Opening web links is not supported on this computer.");
            }
            Desktop.getDesktop().browse(new URI(url));
        } catch (Exception exception) {
            showError("Could not open " + label + ".\n" + url);
        }
    }

    private void loadManifest() {
        setBusy(true);
        retryButton.setVisible(false);
        update(8, ManifestClient.isLocalMode() ? "Checking local build artifacts..." : "Verifying the latest release...");
        new SwingWorker<ReleaseManifest, Void>() {
            @Override
            protected ReleaseManifest doInBackground() throws Exception {
                return new ManifestClient().fetch();
            }

            @Override
            protected void done() {
                boolean loaded = false;
                try {
                    manifest = get();
                    showMods();
                    update(0, manifest.localDirectory != null ? "Local build - ready to test"
                            : service.isInstalled() ? "Ready to update your installation" : "Ready to install");
                    loaded = true;
                } catch (Exception exception) {
                    manifest = null;
                    InstallerLog.error("The release manifest could not be loaded", exception);
                    update(0, ManifestClient.isLocalMode() ? "Local build unavailable - rebuild with launch-local-app.ps1"
                            : service.isInstalled() ? "Release server unavailable - uninstall remains available"
                            : "Could not reach the release server");
                    retryButton.setVisible(true);
                    showError(rootMessage(exception)
                            + "\n\nDetails were saved to " + InstallerLog.file()
                            + "\nUse the Logs button and \"Copy all logs\" when asking for help.");
                } finally {
                    setBusy(false);
                }
                if (loaded) {
                    promptForLauncherUpdate();
                }
            }
        }.execute();
    }

    private void promptForLauncherUpdate() {
        if (manifest.localDirectory != null || updatePromptShown || !LauncherUpdater.isUpdateAvailable(manifest)) {
            return;
        }
        updatePromptShown = true;
        Object[] options = {"Install update", "Later"};
        int answer = JOptionPane.showOptionDialog(this,
                "A new version of Fireball Fight Mods is available.\n\n"
                        + "Available: " + manifest.installerVersion + "\n"
                        + "Installed: " + Main.VERSION + "\n\n"
                        + "Install the update now?",
                "Update available",
                JOptionPane.YES_NO_OPTION,
                JOptionPane.INFORMATION_MESSAGE,
                null,
                options,
                options[0]);
        if (answer == JOptionPane.YES_OPTION) {
            installLauncherUpdate();
        }
    }

    private void installLauncherUpdate() {
        setBusy(true);
        update(12, "Downloading Fireball Fight Mods " + manifest.installerVersion + "\u2026");
        new SwingWorker<Path, Void>() {
            @Override
            protected Path doInBackground() throws Exception {
                return LauncherUpdater.downloadAndVerify(manifest);
            }

            @Override
            protected void done() {
                try {
                    Path updateInstaller = get();
                    update(100, "Starting the update\u2026");
                    LauncherUpdater.launch(updateInstaller);
                    dispose();
                    System.exit(0);
                } catch (Exception exception) {
                    update(0, "Update could not be installed");
                    setBusy(false);
                    showError("The update could not be installed.\n\n" + rootMessage(exception));
                }
            }
        }.execute();
    }

    private void showMods() {
        modGrid.removeAll();
        modCards.clear();
        for (ReleaseManifest.ModInfo mod : manifest.mods) {
            ModCard card = new ModCard(mod, new Runnable() {
                @Override
                public void run() {
                    markPendingChanges();
                }
            });
            modGrid.add(card);
            modCards.put(mod.id, card);
        }
        int rows = (modCards.size() + 1) / 2;
        modGrid.setPreferredSize(new Dimension(800, rows * 156 + Math.max(0, rows - 1) * 12));
        modGrid.revalidate();
        modGrid.repaint();
    }

    private void install() {
        if (manifest == null) {
            showError("The verified release is not available.");
            return;
        }
        final Set<String> selected = new HashSet<String>();
        for (Map.Entry<String, ModCard> entry : modCards.entrySet()) {
            if (entry.getValue().isSelected()) {
                selected.add(entry.getKey());
            }
        }
        runOperation(new Operation() {
            @Override
            public void run() throws Exception {
                if (service.isLunarRunning()) {
                    int answer = askCloseLunar();
                    if (answer != JOptionPane.YES_OPTION) {
                        throw new OperationCancelled("Installation cancelled · Lunar Client is still open");
                    }
                    update(2, "Closing Lunar Client…");
                    service.closeLunar();
                }
                service.install(manifest, selected, InstallerFrame.this);
                installationCompleted = true;
                pendingChanges = false;
            }
        }, new Runnable() {
            @Override
            public void run() {
                showCommunityPrompt(
                        "Installation complete",
                        "Your selected mods were installed successfully.",
                        "Have a suggestion or feedback? Join the Discord and share it with us.");
            }
        });
    }

    private void markPendingChanges() {
        if (installationCompleted) {
            pendingChanges = true;
            setBusy(false);
        }
    }

    private void uninstall() {
        if (!service.isInstalled()) {
            showError("No installed mods were found.");
            return;
        }
        if (JOptionPane.showConfirmDialog(this,
                "Remove all installed mods and the Lunar JVM argument?\nSettings and backups will stay in place.",
                "Uninstall mods", JOptionPane.YES_NO_OPTION,
                JOptionPane.WARNING_MESSAGE) != JOptionPane.YES_OPTION) {
            return;
        }
        runOperation(new Operation() {
            @Override
            public void run() throws Exception {
                if (service.isLunarRunning()) {
                    int answer = askCloseLunar();
                    if (answer != JOptionPane.YES_OPTION) {
                        throw new OperationCancelled("Uninstall cancelled · Lunar Client is still open");
                    }
                    service.closeLunar();
                }
                service.uninstall(InstallerFrame.this);
                installationCompleted = false;
                pendingChanges = false;
            }
        }, new Runnable() {
            @Override
            public void run() {
                showCommunityPrompt(
                        "Uninstall complete",
                        "The mods were uninstalled successfully.",
                        "Tell us what we could improve, or share your feedback and review in the Discord.");
            }
        });
    }

    private int askCloseLunar() throws Exception {
        final int[] answer = new int[1];
        SwingUtilities.invokeAndWait(new Runnable() {
            @Override
            public void run() {
                answer[0] = JOptionPane.showConfirmDialog(InstallerFrame.this,
                        "Lunar Client is open. Close it and continue?\n"
                                + "This also closes a running Minecraft game.",
                        "Close Lunar Client", JOptionPane.YES_NO_OPTION,
                        JOptionPane.WARNING_MESSAGE);
            }
        });
        return answer[0];
    }

    private void runOperation(final Operation operation, final Runnable successAction) {
        setBusy(true);
        new SwingWorker<Void, Void>() {
            @Override
            protected Void doInBackground() throws Exception {
                operation.run();
                return null;
            }

            @Override
            protected void done() {
                boolean succeeded = false;
                try {
                    get();
                    succeeded = true;
                } catch (Exception exception) {
                    String message = rootMessage(exception);
                    update(progress.getValue(), message);
                    if (!(rootCause(exception) instanceof OperationCancelled)) {
                        showError(message);
                    }
                } finally {
                    setBusy(false);
                }
                if (succeeded && successAction != null) {
                    successAction.run();
                }
            }
        }.execute();
    }

    private void showCommunityPrompt(String title, String heading, String copy) {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        JLabel headingLabel = new JLabel(heading);
        headingLabel.setFont(new Font("Segoe UI", Font.BOLD, 15));
        headingLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        JLabel copyLabel = new JLabel("<html><body style='width:330px'>" + copy + "</body></html>");
        copyLabel.setForeground(MUTED);
        copyLabel.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        copyLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        panel.add(headingLabel);
        panel.add(Box.createVerticalStrut(8));
        panel.add(copyLabel);

        Object[] options = {"Join Discord", "Done"};
        int answer = JOptionPane.showOptionDialog(this, panel, title,
                JOptionPane.DEFAULT_OPTION, JOptionPane.PLAIN_MESSAGE,
                loadInstallerIcon(30), options, options[0]);
        if (answer == 0) {
            openDiscord();
        }
    }

    private void setBusy(boolean busy) {
        boolean installedAndCurrent = installationCompleted && !pendingChanges;
        installButton.setEnabled(!busy && manifest != null && !installedAndCurrent);
        uninstallButton.setEnabled(!busy && service.isInstalled());
        for (ModCard card : modCards.values()) {
            card.setSelectionEnabled(!busy);
        }
        if (!busy) {
            if (installedAndCurrent) {
                installButton.setText("Installed");
            } else if (pendingChanges) {
                installButton.setText("Apply changes");
            } else {
                installButton.setText(service.isInstalled()
                        ? "Update installation" : "Install selected mods");
            }
        }
    }

    @Override
    public void update(final int percent, final String status) {
        SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                progress.setValue(percent);
                operationStatus.setText(status);
            }
        });
    }

    @Override
    public void log(String message) {
        // Detailed download messages are intentionally kept out of the primary UI.
    }

    private void showError(final String message) {
        SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                JOptionPane.showMessageDialog(InstallerFrame.this, message,
                        "Fireball Fight Mods", JOptionPane.ERROR_MESSAGE);
            }
        });
    }

    private static Throwable rootCause(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        return current;
    }

    private static String rootMessage(Throwable throwable) {
        Throwable current = rootCause(throwable);
        return current.getMessage() == null ? current.toString() : current.getMessage();
    }

    private interface Operation {
        void run() throws Exception;
    }

    private static final class OperationCancelled extends Exception {
        private static final long serialVersionUID = 1L;

        private OperationCancelled(String message) {
            super(message);
        }
    }

    private static final class ModCard extends JPanel {
        private static final long serialVersionUID = 1L;
        private final JCheckBox selected = new JCheckBox();
        private boolean hovered;

        private ModCard(ReleaseManifest.ModInfo mod, Runnable selectionChanged) {
            setOpaque(false);
            setLayout(new BorderLayout(12, 0));
            setBorder(BorderFactory.createEmptyBorder(18, 16, 18, 14));
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

            JLabel icon = new JLabel(loadModIcon(mod.id));
            icon.setPreferredSize(new Dimension(48, 48));
            icon.setHorizontalAlignment(SwingConstants.CENTER);
            icon.setVerticalAlignment(SwingConstants.TOP);
            add(icon, BorderLayout.WEST);

            JPanel copy = new JPanel();
            copy.setOpaque(false);
            copy.setLayout(new BoxLayout(copy, BoxLayout.Y_AXIS));
            JLabel name = new JLabel(mod.name);
            name.setForeground(TEXT);
            name.setFont(new Font("Segoe UI", Font.BOLD, 15));
            name.setAlignmentX(Component.LEFT_ALIGNMENT);
            JLabel version = new JLabel("Version " + mod.version);
            version.setForeground(new Color(122, 125, 133));
            version.setFont(new Font("Segoe UI", Font.PLAIN, 11));
            version.setAlignmentX(Component.LEFT_ALIGNMENT);
            JTextArea description = new JTextArea(mod.description == null ? "" : mod.description);
            description.setForeground(MUTED);
            description.setFont(new Font("Segoe UI", Font.PLAIN, 12));
            description.setEditable(false);
            description.setFocusable(false);
            description.setOpaque(false);
            description.setLineWrap(true);
            description.setWrapStyleWord(true);
            description.setRows(2);
            description.setBorder(BorderFactory.createEmptyBorder());
            description.setAlignmentX(Component.LEFT_ALIGNMENT);
            copy.add(name);
            copy.add(Box.createVerticalStrut(3));
            copy.add(version);
            copy.add(Box.createVerticalStrut(11));
            copy.add(description);
            add(copy, BorderLayout.CENTER);

            selected.setSelected(true);
            selected.addItemListener(event -> selectionChanged.run());
            selected.setOpaque(false);
            selected.setToolTipText("Include " + mod.name);
            selected.setHorizontalAlignment(SwingConstants.RIGHT);
            selected.setAlignmentX(Component.RIGHT_ALIGNMENT);
            selected.setBorder(BorderFactory.createEmptyBorder());
            selected.setMargin(new Insets(0, 0, 0, 0));
            JPanel controls = new JPanel();
            controls.setOpaque(false);
            controls.setLayout(new BoxLayout(controls, BoxLayout.Y_AXIS));
            controls.setPreferredSize(new Dimension(64, 0));
            controls.setMinimumSize(new Dimension(64, 0));
            controls.add(selected);
            add(controls, BorderLayout.EAST);

            MouseAdapter mouse = new MouseAdapter() {
                @Override
                public void mouseEntered(MouseEvent event) {
                    hovered = true;
                    repaint();
                }

                @Override
                public void mouseExited(MouseEvent event) {
                    hovered = false;
                    repaint();
                }

                @Override
                public void mouseClicked(MouseEvent event) {
                    if (selected.isEnabled() && event.getSource() != selected) {
                        selected.setSelected(!selected.isSelected());
                    }
                }
            };
            addMouseListener(mouse);
            copy.addMouseListener(mouse);
            icon.addMouseListener(mouse);
            name.addMouseListener(mouse);
            version.addMouseListener(mouse);
            description.addMouseListener(mouse);
        }

        private static ImageIcon loadModIcon(String modId) {
            String fileName;
            if ("blockhit-sound".equals(modId)) {
                fileName = "blockhit.png";
            } else if ("endstone-mod".equals(modId)) {
                fileName = "endstone.png";
            } else if ("spawnprot-mod".equals(modId)) {
                fileName = "spawnprot.png";
            } else if ("timer".equals(modId)) {
                fileName = "timer.png";
            } else if ("fireball-hitboxes".equals(modId)) {
                fileName = "fireball.png";
            } else if ("fireball-indicator".equals(modId)) {
                fileName = "fireball-indicator.png";
            } else {
                return new ImageIcon();
            }

            URL resource = InstallerFrame.class.getResource("/icons/" + fileName);
            if (resource == null) {
                return new ImageIcon();
            }
            try {
                BufferedImage source = ImageIO.read(resource);
                int size = 48;
                double scale = Math.min((double) size / source.getWidth(),
                        (double) size / source.getHeight());
                int width = Math.max(1, (int) Math.round(source.getWidth() * scale));
                int height = Math.max(1, (int) Math.round(source.getHeight() * scale));
                BufferedImage scaled = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
                Graphics2D graphics = scaled.createGraphics();
                try {
                    graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                            RenderingHints.VALUE_INTERPOLATION_BICUBIC);
                    graphics.setRenderingHint(RenderingHints.KEY_RENDERING,
                            RenderingHints.VALUE_RENDER_QUALITY);
                    graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                            RenderingHints.VALUE_ANTIALIAS_ON);
                    graphics.drawImage(source, (size - width) / 2, (size - height) / 2,
                            width, height, null);
                } finally {
                    graphics.dispose();
                }
                return new ImageIcon(scaled);
            } catch (Exception exception) {
                return new ImageIcon();
            }
        }

        private boolean isSelected() {
            return selected.isSelected();
        }

        private void setSelectionEnabled(boolean enabled) {
            selected.setEnabled(enabled);
            setCursor(enabled ? Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
                    : Cursor.getDefaultCursor());
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            Graphics2D graphics2D = (Graphics2D) graphics.create();
            try {
                graphics2D.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                        RenderingHints.VALUE_ANTIALIAS_ON);
                graphics2D.setColor(hovered ? SURFACE_HOVER : SURFACE);
                graphics2D.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 12, 12);
                graphics2D.setColor(hovered ? new Color(65, 67, 74) : BORDER);
                graphics2D.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 12, 12);
            } finally {
                graphics2D.dispose();
            }
            super.paintComponent(graphics);
        }
    }

}
