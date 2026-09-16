package com.thunder.wildernessodysseyapi.tools.structureviewer.ui;

import com.thunder.wildernessodysseyapi.tools.structureviewer.io.NbtStructureReader;
import com.thunder.wildernessodysseyapi.tools.structureviewer.io.StructureDiscovery;
import com.thunder.wildernessodysseyapi.tools.structureviewer.model.NbtValue;
import com.thunder.wildernessodysseyapi.tools.structureviewer.model.StructureData;
import com.thunder.wildernessodysseyapi.tools.structureviewer.render.BlockMesh;
import javax.swing.*;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.tree.*;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.*;

/** External development window; owns discovery/loading and disposes all background work on close. */
public final class ViewerWindow extends JFrame {
    private final Path project;
    private final List<Path> roots;
    private final JTree browser = new JTree(new DefaultMutableTreeNode("Structures"));
    private final JTextArea info = area(), inspector = area(), diagnostics = area(), metadata = area();
    private final JTabbedPane tabs = new JTabbedPane();
    private final JLabel status = new JLabel("Choose a structure to preview.");
    private final JCheckBox air = new JCheckBox("Show stored air"), bounds = new JCheckBox("Bounds", true),
            wireframe = new JCheckBox("Wireframe");
    private final JSpinner layer = new JSpinner(new SpinnerNumberModel(-1, -1, 0, 1));
    private final StructureViewport viewport;
    private final ExecutorService loader = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "structure-viewer-loader"); t.setDaemon(true); return t;
    });
    private Future<?> pending;
    private long generation;
    private boolean updatingControls, closed;
    private StructureData loaded;
    private Path currentFile;
    private String loadError;

    /** Builds a standalone window and discovers resources; call setVisible on the Swing event thread. */
    public ViewerWindow(Path project, Path build) {
        super("Wilderness Odyssey · Structure Viewer");
        this.project = project.toAbsolutePath().normalize();
        roots = StructureDiscovery.roots(this.project, build);
        viewport = new StructureViewport(this::inspect, this::reload);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        setMinimumSize(new Dimension(1000, 650));
        Dimension screen = Toolkit.getDefaultToolkit().getScreenSize();
        setSize(Math.min(1480, screen.width - 60), Math.min(940, screen.height - 90));
        setLocationRelativeTo(null);
        setLayout(new BorderLayout(8, 8));

        JToolBar toolbar = new JToolBar();
        toolbar.setFloatable(false);
        button(toolbar, "Open NBT…", this::openFile);
        button(toolbar, "Reload (R)", this::reload);
        button(toolbar, "Rescan browser", this::rescan);
        toolbar.addSeparator();
        button(toolbar, "Focus (F)", viewport::focusStructure);
        toolbar.add(bounds); toolbar.add(wireframe); toolbar.add(air);
        toolbar.addSeparator();
        toolbar.add(new JLabel("Y layer (-1 = all): "));
        layer.setMaximumSize(new Dimension(85, 30));
        toolbar.add(layer);
        add(toolbar, BorderLayout.NORTH);

        browser.setRootVisible(true);
        browser.setShowsRootHandles(true);
        browser.addTreeSelectionListener(event -> {
            Object selected = browser.getLastSelectedPathComponent();
            if (selected instanceof DefaultMutableTreeNode node && node.getUserObject() instanceof StructureFile file)
                load(file.path());
        });
        JScrollPane treeScroll = new JScrollPane(browser);
        treeScroll.setPreferredSize(new Dimension(270, 600));
        treeScroll.setBorder(BorderFactory.createTitledBorder("Structure browser"));

        tabs.addTab("Structure", new JScrollPane(info));
        tabs.addTab("Block", new JScrollPane(inspector));
        tabs.addTab("Diagnostics", new JScrollPane(diagnostics));
        tabs.addTab("Metadata", new JScrollPane(metadata));
        tabs.setPreferredSize(new Dimension(345, 600));
        viewport.setMinimumSize(new Dimension(300, 300));
        JSplitPane detailSplit = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, viewport, tabs);
        detailSplit.setResizeWeight(1);
        JSplitPane mainSplit = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, treeScroll, detailSplit);
        mainSplit.setResizeWeight(0);
        add(mainSplit, BorderLayout.CENTER);
        JPanel footer = new JPanel(new GridLayout(2, 1, 0, 3));
        footer.setBorder(BorderFactory.createEmptyBorder(2, 10, 8, 10));
        footer.add(status);
        footer.add(new JLabel("F1 orbit · F2 freecam · Right-drag look · WASD move · Space up · Shift down · Ctrl slow · Wheel speed/zoom · Left-click inspect"));
        add(footer, BorderLayout.SOUTH);
        bounds.addActionListener(event -> viewport.setOverlays(wireframe.isSelected(), bounds.isSelected()));
        wireframe.addActionListener(event -> viewport.setOverlays(wireframe.isSelected(), bounds.isSelected()));
        air.addActionListener(event -> rebuild());
        layer.addChangeListener(event -> { if (!updatingControls) rebuild(); });
        addWindowListener(new WindowAdapter() {
            @Override public void windowClosed(WindowEvent event) {
                closed = true; generation++;
                if (pending != null) pending.cancel(true);
                loader.shutdownNow(); viewport.close();
            }
        });
        inspector.setText("Left-click a visible block to inspect it.");
        info.setText("Phase 1: NBT structure inspection\n\nChoose a discovered structure or open an external NBT file.\n\n"
                + "Files are read-only. No Minecraft world is started.\n\nRight-drag to look; press F2 to fly through rooms.");
        rescan();
    }

    private static JTextArea area() {
        JTextArea area = new JTextArea();
        area.setEditable(false); area.setLineWrap(true); area.setWrapStyleWord(true);
        area.setMargin(new Insets(12, 12, 12, 12));
        area.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        return area;
    }

    private static void button(JToolBar bar, String title, Runnable action) {
        JButton button = new JButton(title);
        button.addActionListener(event -> action.run());
        bar.add(button);
    }

    private void openFile() {
        JFileChooser chooser = new JFileChooser(project.toFile());
        chooser.setFileFilter(new FileNameExtensionFilter("Minecraft structure NBT (*.nbt)", "nbt"));
        if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) load(chooser.getSelectedFile().toPath());
    }

    private void rescan() {
        loader.submit(() -> {
            try {
                List<Path> files = StructureDiscovery.discover(roots);
                SwingUtilities.invokeLater(() -> {
                    if (closed) return;
                    var root = new DefaultMutableTreeNode("Structures (" + files.size() + ")");
                    for (Path file : files) {
                        Path resourceRoot = roots.stream().filter(file::startsWith).findFirst().orElse(project);
                        String group = resourceRoot.equals(project.resolve("src/main/resources")) ? "Authored resources"
                                : resourceRoot.equals(project.resolve("src/generated/resources")) ? "Generated resources"
                                : "StructureGen (" + resourceRoot.getParent().getParent().getParent().getFileName() + ")";
                        DefaultMutableTreeNode parent = folder(root, group);
                        Path relative = resourceRoot.relativize(file);
                        for (int i = 0; i < relative.getNameCount() - 1; i++) {
                            String part = relative.getName(i).toString();
                            if (!part.equals("data") && !part.equals("structure") && !part.equals("structures"))
                                parent = folder(parent, part);
                        }
                        parent.add(new DefaultMutableTreeNode(new StructureFile(file)));
                    }
                    browser.setModel(new DefaultTreeModel(root));
                    for (int row = 0; row < browser.getRowCount(); row++) browser.expandRow(row);
                    if (files.isEmpty()) status.setText("No NBT resources found. Use Open NBT or generate structures separately.");
                });
            } catch (Exception exception) {
                SwingUtilities.invokeLater(() -> { if (!closed) status.setText("Browser scan failed: " + exception.getMessage()); });
            }
        });
    }

    private static DefaultMutableTreeNode folder(DefaultMutableTreeNode parent, String name) {
        for (int i = 0; i < parent.getChildCount(); i++) {
            var child = (DefaultMutableTreeNode) parent.getChildAt(i);
            if (name.equals(child.getUserObject())) return child;
        }
        var child = new DefaultMutableTreeNode(name); parent.add(child); return child;
    }

    /** Begins an asynchronous import; a newer selection cancels and supersedes this request. */
    public void load(Path file) {
        currentFile = file.toAbsolutePath().normalize();
        loadError = null;
        air.setEnabled(false); layer.setEnabled(false);
        status.setText("Loading " + currentFile.getFileName() + "…");
        boolean showAir = air.isSelected();
        long request = ++generation;
        if (pending != null) pending.cancel(true);
        pending = loader.submit(() -> {
            try {
                StructureData data = new NbtStructureReader().read(file);
                BlockMesh mesh = BlockMesh.build(data, showAir, -1);
                String summary = StructureDetails.summary(data), report = StructureDetails.diagnostics(mesh);
                SwingUtilities.invokeLater(() -> {
                    if (closed || request != generation) return;
                    loaded = data;
                    air.setEnabled(true); layer.setEnabled(true);
                    updatingControls = true;
                    layer.setModel(new SpinnerNumberModel(-1, -1, Math.max(0, data.size().y() - 1), 1));
                    updatingControls = false;
                    info.setText(summary); info.setCaretPosition(0);
                    diagnostics.setText(report); diagnostics.setCaretPosition(0);
                    metadata.setText(new NbtValue(10, data.metadata()).display()); metadata.setCaretPosition(0);
                    inspector.setText("Left-click a visible block to inspect it.");
                    viewport.setMesh(mesh, true);
                    status.setText("Loaded " + data.name() + " · " + data.blocks().size() + " stored blocks · "
                            + mesh.faces().size() + " exposed faces · approximate cube preview");
                    viewport.requestFocusInWindow();
                });
            } catch (Exception exception) {
                SwingUtilities.invokeLater(() -> {
                    if (closed || request != generation) return;
                    air.setEnabled(true); layer.setEnabled(true);
                    loadError = exception.getClass().getSimpleName() + ": " + exception.getMessage();
                    status.setText("Could not load " + file.getFileName() + ". Previous preview retained.");
                    diagnostics.setText(file + "\n\n" + loadError);
                });
            }
        });
    }

    private void reload() { if (currentFile != null) load(currentFile); }

    private void rebuild() {
        if (loaded == null) return;
        StructureData data = loaded;
        boolean showAir = air.isSelected();
        int y = (int) layer.getValue();
        long request = ++generation;
        if (pending != null) pending.cancel(true);
        status.setText("Preparing visible blocks…");
        pending = loader.submit(() -> {
            try {
                BlockMesh mesh = BlockMesh.build(data, showAir, y);
                String report = StructureDetails.diagnostics(mesh);
                SwingUtilities.invokeLater(() -> {
                    if (closed || request != generation) return;
                    viewport.setMesh(mesh, false);
                    inspector.setText("Left-click a visible block to inspect it.");
                    diagnostics.setText(report);
                    status.setText(data.name() + " · " + (y < 0 ? "all layers" : "Y = " + y)
                            + " · " + mesh.faces().size() + " exposed faces");
                });
            } catch (CancellationException ignored) {
                // A newer selection owns the result.
            }
        });
    }

    private void inspect(int index) {
        if (loaded != null) { inspector.setText(StructureDetails.inspection(loaded, index)); inspector.setCaretPosition(0); if (index >= 0) tabs.setSelectedIndex(1); }
    }

    /** Exposes the real viewport to the standalone GUI smoke test. */
    public StructureViewport viewport() { return viewport; }
    /** Current imported structure, or null while the first file loads. */
    public StructureData loadedStructure() { return loaded; }
    /** Current inspector text for accessibility and smoke validation. */
    public String inspectorText() { return inspector.getText(); }
    /** Last import failure, without hiding it behind a modal dialog. */
    public String loadError() { return loadError; }

    private record StructureFile(Path path) {
        @Override public String toString() { return path.getFileName().toString(); }
    }
}

