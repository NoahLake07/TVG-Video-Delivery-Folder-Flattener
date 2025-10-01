import javax.swing.*;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.TableRowSorter;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.List;
import java.util.*;
import java.util.stream.Collectors;

public class VideoFolderFlattener extends JFrame {

    // Add/adjust extensions as desired (lowercase, no dots)
    private static final Set<String> VIDEO_EXTS = new HashSet<>(Arrays.asList(
            "mp4", "mov", "m4v", "mkv", "avi", "wmv", "flv", "webm", "mpeg", "mpg", "3gp"
    ));

    private final JTextField rootField = new JTextField();
    private final JButton browseBtn = new JButton("Choose Folder…");
    private final JButton scanBtn = new JButton("Scan Subfolders");
    private final JButton selectAllBtn = new JButton("Select All");
    private final JButton clearSelBtn = new JButton("Clear Selection");
    private final JButton moveBtn = new JButton("Move Selected");
    private final JTextArea logArea = new JTextArea(8, 80);

    private Path rootDir = null;
    private final MatchTableModel tableModel = new MatchTableModel();
    private final JTable table = new JTable(tableModel);

    public VideoFolderFlattener() {
        super("Video Folder Flattener");
        setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        setLayout(new BorderLayout(10, 10));

        // Top: chooser + scan
        JPanel top = new JPanel(new BorderLayout(8, 8));
        rootField.setEditable(false);
        JPanel choose = new JPanel(new BorderLayout(8, 8));
        choose.add(rootField, BorderLayout.CENTER);
        choose.add(browseBtn, BorderLayout.EAST);
        top.add(choose, BorderLayout.NORTH);

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        actions.add(scanBtn);
        actions.add(selectAllBtn);
        actions.add(clearSelBtn);
        actions.add(moveBtn);
        top.add(actions, BorderLayout.SOUTH);

        add(top, BorderLayout.NORTH);

        // Center: table
        table.setFillsViewportHeight(true);
        table.setAutoCreateRowSorter(true);
        ((TableRowSorter<?>) table.getRowSorter()).setSortable(0, false); // checkbox column unsortable
        table.getColumnModel().getColumn(0).setMaxWidth(80);
        table.getColumnModel().getColumn(2).setMaxWidth(120);
        table.getColumnModel().getColumn(3).setMaxWidth(180);
        add(new JScrollPane(table), BorderLayout.CENTER);

        // Bottom: log
        logArea.setEditable(false);
        logArea.setLineWrap(true);
        logArea.setWrapStyleWord(true);
        add(new JScrollPane(logArea), BorderLayout.SOUTH);

        // Wire up actions
        browseBtn.addActionListener(this::chooseRoot);
        scanBtn.addActionListener(this::scanSubfolders);
        selectAllBtn.addActionListener(e -> tableModel.selectAll(true));
        clearSelBtn.addActionListener(e -> tableModel.selectAll(false));
        moveBtn.addActionListener(this::moveSelected);

        setPreferredSize(new Dimension(1000, 600));
        setLocationByPlatform(true);
        pack();
    }

    private void chooseRoot(ActionEvent e) {
        JFileChooser fc = new JFileChooser();
        fc.setDialogTitle("Choose Root Folder");
        fc.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        fc.setAcceptAllFileFilterUsed(false);
        if (rootDir != null) {
            fc.setCurrentDirectory(rootDir.toFile());
        }
        if (fc.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            rootDir = fc.getSelectedFile().toPath().toAbsolutePath().normalize();
            rootField.setText(rootDir.toString());
            tableModel.setMatches(Collections.emptyList());
            log("Root set to: " + rootDir);
        }
    }

    private void scanSubfolders(ActionEvent e) {
        if (!ensureRoot()) return;
        log("Scanning immediate subfolders of: " + rootDir);
        List<Match> matches = new ArrayList<>();
        try (DirectoryStream<Path> ds = Files.newDirectoryStream(rootDir)) {
            for (Path sub : ds) {
                if (Files.isDirectory(sub)) {
                    Optional<Match> m = analyzeSubfolder(sub);
                    m.ifPresent(matches::add);
                }
            }
        } catch (IOException ex) {
            error("Scan failed: " + ex.getMessage());
        }
        matches.sort(Comparator.comparing(m -> m.folder.toString().toLowerCase()));
        tableModel.setMatches(matches);
        log("Found " + matches.size() + " folder(s) that contain only video files.");
    }

    /**
     * A folder qualifies if:
     *  - It contains at least one file.
     *  - It contains NO subdirectories.
     *  - Every non-hidden file is a recognized video by extension.
     * Hidden files (e.g., .DS_Store) are ignored for the check.
     */
    private Optional<Match> analyzeSubfolder(Path folder) {
        try (DirectoryStream<Path> ds = Files.newDirectoryStream(folder)) {
            long totalBytes = 0L;
            int videoCount = 0;
            boolean sawAFile = false;

            for (Path p : ds) {
                if (Files.isHidden(p)) continue; // ignore hidden files/folders
                if (Files.isDirectory(p)) {
                    // Contains a subdirectory → not a pure video-only folder
                    return Optional.empty();
                } else {
                    sawAFile = true;
                    String ext = extensionOf(p.getFileName().toString()).toLowerCase(Locale.ROOT);
                    if (!VIDEO_EXTS.contains(ext)) {
                        return Optional.empty();
                    }
                    videoCount++;
                    try {
                        totalBytes += Files.size(p);
                    } catch (IOException ignore) {
                        // best effort
                    }
                }
            }
            if (!sawAFile || videoCount == 0) return Optional.empty();
            return Optional.of(new Match(folder, videoCount, totalBytes, true));
        } catch (IOException io) {
            // Couldn't read this folder; skip it.
            return Optional.empty();
        }
    }

    private void moveSelected(ActionEvent e) {
        if (!ensureRoot()) return;

        List<Match> selected = tableModel.getSelected();
        if (selected.isEmpty()) {
            JOptionPane.showMessageDialog(this, "No folders selected.", "Nothing to do",
                    JOptionPane.INFORMATION_MESSAGE);
            return;
        }

        int confirm = JOptionPane.showConfirmDialog(
                this,
                "Move all video files from " + selected.size() + " folder(s)\n" +
                        "into the root folder?\n\nRoot: " + rootDir,
                "Confirm Move",
                JOptionPane.OK_CANCEL_OPTION
        );
        if (confirm != JOptionPane.OK_OPTION) return;

        log("Starting move of selected folders...");
        int movedFiles = 0;
        int deletedDirs = 0;

        for (Match m : selected) {
            log("Processing: " + m.folder);
            // Move video files up to root
            try (DirectoryStream<Path> ds = Files.newDirectoryStream(m.folder)) {
                for (Path file : ds) {
                    if (Files.isDirectory(file)) continue; // should not happen for a qualified match
                    if (Files.isHidden(file)) continue;
                    String ext = extensionOf(file.getFileName().toString()).toLowerCase(Locale.ROOT);
                    if (!VIDEO_EXTS.contains(ext)) continue; // extra safety

                    Path target = rootDir.resolve(file.getFileName());
                    target = nextAvailableName(target);
                    try {
                        Files.move(file, target, StandardCopyOption.ATOMIC_MOVE);
                        movedFiles++;
                        log("  Moved: " + file.getFileName() + " → " + rootDir.relativize(target));
                    } catch (AtomicMoveNotSupportedException amnse) {
                        Files.move(file, target);
                        movedFiles++;
                        log("  Moved (non-atomic fs): " + file.getFileName() + " → " + rootDir.relativize(target));
                    }
                }
            } catch (IOException ex) {
                error("  Failed reading folder: " + m.folder + " (" + ex.getMessage() + ")");
                continue;
            }

            // Try to delete the (now hopefully empty) folder tree if it's empty
            try {
                if (isDirEmpty(m.folder)) {
                    Files.delete(m.folder);
                    deletedDirs++;
                    log("  Deleted empty folder: " + m.folder.getFileName());
                } else {
                    // If some hidden/non-video remnants exist, try a safe cleanup of empty sub-entries
                    cleanupEmptyDescendants(m.folder);
                    if (isDirEmpty(m.folder)) {
                        Files.delete(m.folder);
                        deletedDirs++;
                        log("  Deleted (after cleanup) folder: " + m.folder.getFileName());
                    } else {
                        log("  Skipped deletion; folder not empty: " + m.folder.getFileName());
                    }
                }
            } catch (IOException ex) {
                error("  Could not delete folder: " + m.folder + " (" + ex.getMessage() + ")");
            }
        }

        log("Done. Moved files: " + movedFiles + ", Folders deleted: " + deletedDirs);
        // Re-scan to refresh the list (some may no longer qualify or exist)
        scanSubfolders(null);
    }

    private boolean ensureRoot() {
        if (rootDir == null) {
            JOptionPane.showMessageDialog(this, "Please choose a root folder first.",
                    "No Root Folder", JOptionPane.WARNING_MESSAGE);
            return false;
        }
        if (!Files.isDirectory(rootDir)) {
            error("Selected root is not a directory.");
            return false;
        }
        return true;
    }

    private static boolean isDirEmpty(Path dir) throws IOException {
        try (DirectoryStream<Path> ds = Files.newDirectoryStream(dir)) {
            return !ds.iterator().hasNext();
        }
    }

    private static void cleanupEmptyDescendants(Path dir) throws IOException {
        // Walk bottom-up and delete any empty directories (ignores files)
        Files.walkFileTree(dir, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult postVisitDirectory(Path d, IOException exc) throws IOException {
                if (exc != null) return FileVisitResult.TERMINATE;
                if (dir.equals(d)) return FileVisitResult.CONTINUE; // don't delete the root here
                if (isDirEmpty(d)) {
                    try {
                        Files.delete(d);
                    } catch (IOException ignored) { }
                }
                return FileVisitResult.CONTINUE;
            }
        });
    }

    /** If target exists, returns a variant like "name (1).ext", "name (2).ext", ... */
    private static Path nextAvailableName(Path target) {
        if (!Files.exists(target)) return target;
        String base = stripExtension(target.getFileName().toString());
        String ext = extensionOf(target.getFileName().toString());
        int i = 1;
        while (true) {
            String candidate = ext.isEmpty()
                    ? String.format("%s (%d)", base, i)
                    : String.format("%s (%d).%s", base, i, ext);
            Path alt = target.getParent().resolve(candidate);
            if (!Files.exists(alt)) return alt;
            i++;
        }
    }

    private static String extensionOf(String filename) {
        int dot = filename.lastIndexOf('.');
        return (dot >= 0 && dot < filename.length() - 1) ? filename.substring(dot + 1) : "";
    }

    private static String stripExtension(String filename) {
        int dot = filename.lastIndexOf('.');
        return (dot > 0) ? filename.substring(0, dot) : filename;
    }

    private void log(String msg) {
        logArea.append(msg + "\n");
        logArea.setCaretPosition(logArea.getDocument().getLength());
    }

    private void error(String msg) {
        log("[ERROR] " + msg);
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new VideoFolderFlattener().setVisible(true));
    }

    // === Data model ===

    private static class Match {
        final Path folder;
        final int videoCount;
        final long totalBytes;
        boolean selected;

        Match(Path folder, int videoCount, long totalBytes, boolean selected) {
            this.folder = folder;
            this.videoCount = videoCount;
            this.totalBytes = totalBytes;
            this.selected = selected;
        }

        String sizeHuman() {
            double b = totalBytes;
            String[] units = {"B", "KB", "MB", "GB", "TB"};
            int i = 0;
            while (b >= 1024 && i < units.length - 1) {
                b /= 1024.0;
                i++;
            }
            return String.format(Locale.US, "%.1f %s", b, units[i]);
        }
    }

    private static class MatchTableModel extends AbstractTableModel {
        private final String[] cols = {"Select", "Folder", "Videos", "Total Size"};
        private final Class<?>[] types = {Boolean.class, String.class, Integer.class, String.class};
        private List<Match> matches = new ArrayList<>();

        public void setMatches(List<Match> ms) {
            this.matches = new ArrayList<>(ms);
            fireTableDataChanged();
        }

        public void selectAll(boolean sel) {
            for (Match m : matches) m.selected = sel;
            fireTableRowsUpdated(0, Math.max(0, matches.size() - 1));
        }

        public List<Match> getSelected() {
            return matches.stream().filter(m -> m.selected).collect(Collectors.toList());
        }

        @Override public int getRowCount() { return matches.size(); }
        @Override public int getColumnCount() { return cols.length; }
        @Override public String getColumnName(int c) { return cols[c]; }
        @Override public Class<?> getColumnClass(int c) { return types[c]; }
        @Override public boolean isCellEditable(int r, int c) { return c == 0; }

        @Override
        public Object getValueAt(int rowIndex, int columnIndex) {
            Match m = matches.get(rowIndex);
            switch (columnIndex) {
                case 0: return m.selected;
                case 1: return m.folder.toString();
                case 2: return m.videoCount;
                case 3: return m.sizeHuman();
            }
            return null;
        }

        @Override
        public void setValueAt(Object aValue, int row, int col) {
            if (col == 0 && aValue instanceof Boolean) {
                matches.get(row).selected = (Boolean) aValue;
                fireTableCellUpdated(row, col);
            }
        }
    }
}
