package net.weavemc.installer;

import javax.swing.BorderFactory;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTextArea;
import javax.swing.ListSelectionModel;
import javax.swing.SwingWorker;
import javax.swing.Timer;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Desktop;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.FileTime;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Stream;

final class DiagnosticsWindow {
    private static final int MAX_FILES = 100;
    private static final int MAX_BYTES_PER_FILE = 128 * 1024;
    private static final int INDEX_REFRESH_TICKS = 5;

    private DiagnosticsWindow() {
    }

    static void open() {
        Path home = Paths.get(System.getProperty("user.home")).toAbsolutePath().normalize();
        Path weave = configuredPath("weave.installRoot", home.resolve(".weave"));
        Path lunar = configuredPath("weave.lunarRoot", home.resolve(".lunarclient"));

        JFrame frame = new JFrame("Weave diagnostics");
        frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        frame.setMinimumSize(new Dimension(760, 460));
        frame.setSize(1050, 700);
        frame.setLocationByPlatform(true);
        frame.getContentPane().setBackground(new Color(15, 16, 18));
        frame.setLayout(new BorderLayout());

        JLabel status = new JLabel(" Finding diagnostic files...");
        status.setForeground(new Color(220, 220, 220));
        status.setBackground(new Color(28, 29, 33));
        status.setOpaque(true);
        status.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        frame.add(status, BorderLayout.NORTH);

        DefaultListModel<LogEntry> model = new DefaultListModel<LogEntry>();
        JList<LogEntry> fileList = new JList<LogEntry>(model);
        fileList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        fileList.setBackground(new Color(20, 21, 24));
        fileList.setForeground(new Color(226, 226, 226));
        fileList.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        fileList.setFixedCellHeight(25);

        JTextArea logs = new JTextArea("Choose a log from the list.\n");
        logs.setEditable(false);
        logs.setLineWrap(false);
        logs.setBackground(new Color(12, 13, 15));
        logs.setForeground(new Color(226, 226, 226));
        logs.setCaretColor(Color.WHITE);
        logs.setFont(new Font("Consolas", Font.PLAIN, 12));

        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT,
                new JScrollPane(fileList), new JScrollPane(logs));
        split.setResizeWeight(0.26D);
        split.setDividerLocation(275);
        split.setBorder(BorderFactory.createEmptyBorder());
        frame.add(split, BorderLayout.CENTER);

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 7));
        actions.setBackground(new Color(28, 29, 33));
        JButton openFolders = new JButton("Open log folders");
        JButton copyAll = new JButton("Copy all logs");
        JButton copySelected = new JButton("Copy selected");
        JButton refresh = new JButton("Refresh now");
        actions.add(openFolders);
        actions.add(copyAll);
        actions.add(copySelected);
        actions.add(refresh);
        frame.add(actions, BorderLayout.SOUTH);

        AtomicBoolean indexing = new AtomicBoolean();
        AtomicBoolean reading = new AtomicBoolean();
        String[] displayedSignature = {""};
        int[] tick = {INDEX_REFRESH_TICKS};

        Runnable refreshSelected = new Runnable() {
            @Override
            public void run() {
                LogEntry selected = fileList.getSelectedValue();
                if (selected == null || !reading.compareAndSet(false, true)) {
                    return;
                }
                String signature = signature(selected.path);
                if (signature.equals(displayedSignature[0])) {
                    reading.set(false);
                    return;
                }
                new SwingWorker<Tail, Void>() {
                    @Override
                    protected Tail doInBackground() throws Exception {
                        return readTail(selected.path);
                    }

                    @Override
                    protected void done() {
                        try {
                            if (selected.equals(fileList.getSelectedValue())) {
                                Tail tail = get();
                                String heading = selected.path + "\n"
                                        + (tail.truncated
                                        ? "[showing the newest " + MAX_BYTES_PER_FILE + " bytes]\n\n"
                                        : "\n");
                                boolean atBottom = logs.getCaretPosition()
                                        >= logs.getDocument().getLength() - 2;
                                int caret = logs.getCaretPosition();
                                logs.setText(heading + tail.text);
                                logs.setCaretPosition(atBottom
                                        ? logs.getDocument().getLength()
                                        : Math.min(caret, logs.getDocument().getLength()));
                                displayedSignature[0] = signature;
                                status.setText(" Live · " + model.size()
                                        + " log files · selected file refreshes only when changed");
                                status.setForeground(new Color(170, 230, 170));
                            }
                        } catch (Exception exception) {
                            status.setText(" Could not read log: " + rootMessage(exception));
                            status.setForeground(new Color(255, 150, 140));
                        } finally {
                            reading.set(false);
                        }
                    }
                }.execute();
            }
        };

        Runnable refreshIndex = new Runnable() {
            @Override
            public void run() {
                if (!indexing.compareAndSet(false, true) || !frame.isDisplayable()) {
                    return;
                }
                Path selectedPath = fileList.getSelectedValue() == null
                        ? null : fileList.getSelectedValue().path;
                new SwingWorker<List<Path>, Void>() {
                    @Override
                    protected List<Path> doInBackground() {
                        return findFiles(home, weave, lunar);
                    }

                    @Override
                    protected void done() {
                        try {
                            List<Path> files = get();
                            boolean unchanged = files.size() == model.size();
                            for (int index = 0; unchanged && index < files.size(); index++) {
                                unchanged = files.get(index).equals(model.get(index).path);
                            }
                            if (unchanged) {
                                return;
                            }
                            model.clear();
                            int selectedIndex = -1;
                            for (int index = 0; index < files.size(); index++) {
                                Path file = files.get(index);
                                model.addElement(new LogEntry(file, weave, lunar));
                                if (file.equals(selectedPath)) {
                                    selectedIndex = index;
                                }
                            }
                            if (!model.isEmpty()) {
                                fileList.setSelectedIndex(selectedIndex >= 0 ? selectedIndex : 0);
                            } else {
                                logs.setText("No diagnostic files were found yet.\n"
                                        + "Launch Lunar once, then leave this window open.\n");
                                status.setText(" No diagnostic files found · checking every 5 seconds");
                            }
                        } catch (Exception exception) {
                            status.setText(" Could not index logs: " + rootMessage(exception));
                            status.setForeground(new Color(255, 150, 140));
                        } finally {
                            indexing.set(false);
                        }
                    }
                }.execute();
            }
        };

        fileList.addListSelectionListener(event -> {
            if (!event.getValueIsAdjusting()) {
                displayedSignature[0] = "";
                refreshSelected.run();
            }
        });
        refresh.addActionListener(event -> {
            tick[0] = INDEX_REFRESH_TICKS;
            displayedSignature[0] = "";
            refreshIndex.run();
            refreshSelected.run();
        });
        copySelected.addActionListener(event -> Toolkit.getDefaultToolkit()
                .getSystemClipboard().setContents(new StringSelection(logs.getText()), null));
        copyAll.addActionListener(event -> copyAllAsync(
                frame, status, copyAll, home, weave, lunar));
        openFolders.addActionListener(event -> {
            openDirectory(weave.resolve("logs"));
            openDirectory(lunar.resolve("logs"));
        });

        Timer timer = new Timer(1000, event -> {
            refreshSelected.run();
            tick[0]++;
            if (tick[0] >= INDEX_REFRESH_TICKS) {
                tick[0] = 0;
                refreshIndex.run();
            }
        });
        frame.addWindowListener(new java.awt.event.WindowAdapter() {
            @Override
            public void windowClosed(java.awt.event.WindowEvent event) {
                timer.stop();
            }
        });

        frame.setVisible(true);
        refreshIndex.run();
        timer.start();
    }

    private static void copyAllAsync(JFrame frame, JLabel status, JButton button,
                                     Path home, Path weave, Path lunar) {
        button.setEnabled(false);
        status.setText(" Preparing all logs for the clipboard...");
        new SwingWorker<Snapshot, Void>() {
            @Override
            protected Snapshot doInBackground() {
                return collect(home, weave, lunar);
            }

            @Override
            protected void done() {
                try {
                    Snapshot snapshot = get();
                    if (frame.isDisplayable()) {
                        Toolkit.getDefaultToolkit().getSystemClipboard().setContents(
                                new StringSelection(snapshot.text), null);
                        status.setText(" Copied " + snapshot.fileCount
                                + " logs to the clipboard"
                                + (snapshot.truncated ? " · large logs were tailed" : ""));
                    }
                } catch (Exception exception) {
                    status.setText(" Could not copy logs: " + rootMessage(exception));
                } finally {
                    button.setEnabled(true);
                }
            }
        }.execute();
    }

    static Snapshot collect(Path home, Path weave, Path lunar) {
        List<Path> files = findFiles(home, weave, lunar);
        boolean fileListTruncated = files.size() > MAX_FILES;
        if (fileListTruncated) {
            files = new ArrayList<Path>(files.subList(0, MAX_FILES));
        }

        StringBuilder output = new StringBuilder();
        output.append("WEAVE DIAGNOSTICS\n")
                .append("Weave: ").append(weave).append('\n')
                .append("Lunar: ").append(lunar).append("\n\n");
        boolean contentTruncated = false;
        for (Path file : files) {
            output.append("===== ").append(file).append(" =====\n");
            try {
                Tail tail = readTail(file);
                if (tail.truncated) {
                    output.append("[showing newest ").append(MAX_BYTES_PER_FILE)
                            .append(" bytes]\n");
                    contentTruncated = true;
                }
                output.append(tail.text);
                if (!tail.text.endsWith("\n")) {
                    output.append('\n');
                }
            } catch (IOException exception) {
                output.append("[could not read: ").append(exception.getMessage()).append("]\n");
            }
            output.append('\n');
        }
        return new Snapshot(output.toString(), files.size(),
                fileListTruncated || contentTruncated);
    }

    private static List<Path> findFiles(Path home, Path weave, Path lunar) {
        List<Path> files = new ArrayList<Path>();
        addRootLogs(files, weave);
        addLogs(files, weave.resolve("logs"), 3);
        addLogs(files, weave.resolve("audit"), 2);
        addLogs(files, lunar.resolve("logs"), 8);
        addLogs(files, lunar.resolve("offline").resolve("multiver").resolve("logs"), 6);
        addCrashLogs(files, home);
        addCrashLogs(files, lunar);

        Collections.sort(files, new Comparator<Path>() {
            @Override
            public int compare(Path left, Path right) {
                return modified(right).compareTo(modified(left));
            }
        });
        List<Path> unique = new ArrayList<Path>();
        for (Path file : files) {
            if (!unique.contains(file)) {
                unique.add(file);
            }
        }
        return unique;
    }

    private static void addRootLogs(List<Path> output, Path root) {
        if (!Files.isDirectory(root)) {
            return;
        }
        try (Stream<Path> paths = Files.list(root)) {
            paths.filter(Files::isRegularFile)
                    .filter(DiagnosticsWindow::isDiagnosticFile)
                    .forEach(output::add);
        } catch (IOException ignored) {
            // Other readable roots should still be displayed.
        }
    }

    private static void addLogs(List<Path> output, Path root, int depth) {
        if (!Files.isDirectory(root)) {
            return;
        }
        try (Stream<Path> paths = Files.walk(root, depth)) {
            paths.filter(Files::isRegularFile)
                    .filter(DiagnosticsWindow::isDiagnosticFile)
                    .forEach(output::add);
        } catch (IOException | RuntimeException ignored) {
            // Other readable roots should still be displayed.
        }
    }

    private static void addCrashLogs(List<Path> output, Path root) {
        if (!Files.isDirectory(root)) {
            return;
        }
        try (Stream<Path> paths = Files.list(root)) {
            paths.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().toLowerCase(Locale.ROOT)
                            .matches("hs_err_pid.*\\.log"))
                    .forEach(output::add);
        } catch (IOException ignored) {
            // Crash logs are optional.
        }
    }

    private static boolean isDiagnosticFile(Path path) {
        String name = path.getFileName().toString().toLowerCase(Locale.ROOT);
        return name.endsWith(".log") || name.endsWith(".txt") || name.endsWith(".json");
    }

    private static Tail readTail(Path file) throws IOException {
        long length = Files.size(file);
        long offset = Math.max(0L, length - MAX_BYTES_PER_FILE);
        byte[] data = new byte[(int) (length - offset)];
        try (RandomAccessFile input = new RandomAccessFile(file.toFile(), "r")) {
            input.seek(offset);
            input.readFully(data);
        }
        String text = new String(data, StandardCharsets.UTF_8);
        if (offset > 0L) {
            int newline = text.indexOf('\n');
            if (newline >= 0 && newline + 1 < text.length()) {
                text = text.substring(newline + 1);
            }
        }
        return new Tail(text, offset > 0L);
    }

    private static String signature(Path path) {
        try {
            return Files.size(path) + ":" + Files.getLastModifiedTime(path).toMillis();
        } catch (IOException ignored) {
            return "missing";
        }
    }

    private static FileTime modified(Path path) {
        try {
            return Files.getLastModifiedTime(path);
        } catch (IOException ignored) {
            return FileTime.fromMillis(0L);
        }
    }

    private static Path configuredPath(String property, Path fallback) {
        String value = System.getProperty(property);
        return value == null || value.trim().isEmpty()
                ? fallback : Paths.get(value).toAbsolutePath().normalize();
    }

    private static void openDirectory(Path directory) {
        try {
            Files.createDirectories(directory);
            if (Desktop.isDesktopSupported()) {
                Desktop.getDesktop().open(directory.toFile());
            }
        } catch (IOException | RuntimeException ignored) {
            // The logs remain visible even if Explorer cannot be opened.
        }
    }

    private static String rootMessage(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        return current.getMessage() == null
                ? current.getClass().getSimpleName() : current.getMessage();
    }

    private static final class LogEntry {
        final Path path;
        private final String display;

        LogEntry(Path path, Path weave, Path lunar) {
            this.path = path;
            Path relative;
            String source;
            if (path.startsWith(weave)) {
                relative = weave.relativize(path);
                source = "Weave";
            } else if (path.startsWith(lunar)) {
                relative = lunar.relativize(path);
                source = "Lunar";
            } else {
                relative = path.getFileName();
                source = "JVM";
            }
            String time = DateFormat.getTimeInstance(DateFormat.SHORT)
                    .format(new Date(modified(path).toMillis()));
            display = "[" + source + "] " + relative + "  ·  " + time;
        }

        @Override
        public String toString() {
            return display;
        }

        @Override
        public boolean equals(Object value) {
            return value instanceof LogEntry && path.equals(((LogEntry) value).path);
        }

        @Override
        public int hashCode() {
            return path.hashCode();
        }
    }

    private static final class Tail {
        final String text;
        final boolean truncated;

        Tail(String text, boolean truncated) {
            this.text = text;
            this.truncated = truncated;
        }
    }

    static final class Snapshot {
        final String text;
        final int fileCount;
        final boolean truncated;

        Snapshot(String text, int fileCount, boolean truncated) {
            this.text = text;
            this.fileCount = fileCount;
            this.truncated = truncated;
        }
    }
}
