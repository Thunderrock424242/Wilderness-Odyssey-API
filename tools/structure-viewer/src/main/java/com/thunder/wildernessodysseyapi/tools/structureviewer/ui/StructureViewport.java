package com.thunder.wildernessodysseyapi.tools.structureviewer.ui;

import com.thunder.wildernessodysseyapi.tools.structureviewer.render.*;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import java.awt.Color;
import java.awt.Graphics;
import java.awt.event.*;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.IntConsumer;

/** Swing input surface with a single background renderer and no world collision or game lifecycle. */
public final class StructureViewport extends JPanel implements AutoCloseable {
    private final Camera camera = new Camera();
    private final Set<Integer> keys = new HashSet<>();
    private final ExecutorService renderer = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "structure-viewer-render"); t.setDaemon(true); return t;
    });
    private final Timer timer;
    private final IntConsumer onSelect;
    private final Runnable onReload;
    private BlockMesh mesh;
    private SoftwareRenderer.Frame frame;
    private boolean dirty = true, rendering, closed, bounds = true, wireframe;
    private long version, lastTick = System.nanoTime();
    private int selected = -1, mouseX, mouseY;
    private String renderError;

    /** Creates a focusable viewport with freecam/orbit bindings and block picking. */
    public StructureViewport(IntConsumer onSelect, Runnable onReload) {
        this.onSelect = onSelect;
        this.onReload = onReload;
        setFocusable(true);
        setBackground(new Color(0x18232e));
        setToolTipText("Right-drag: look / orbit. Left-click: inspect. F1: orbit. F2: free camera.");
        addKeyListener(new KeyAdapter() {
            @Override public void keyPressed(KeyEvent event) {
                if (!keys.add(event.getKeyCode())) return;
                switch (event.getKeyCode()) {
                    case KeyEvent.VK_F1 -> camera.setOrbit(true);
                    case KeyEvent.VK_F2 -> camera.setOrbit(false);
                    case KeyEvent.VK_F -> focusStructure();
                    case KeyEvent.VK_R -> StructureViewport.this.onReload.run();
                    default -> { }
                }
                changed();
            }
            @Override public void keyReleased(KeyEvent event) { keys.remove(event.getKeyCode()); }
        });
        addFocusListener(new FocusAdapter() {
            @Override public void focusLost(FocusEvent event) { keys.clear(); }
        });
        MouseAdapter mouse = new MouseAdapter() {
            @Override public void mousePressed(MouseEvent event) {
                requestFocusInWindow();
                mouseX = event.getX(); mouseY = event.getY();
                if (SwingUtilities.isLeftMouseButton(event) && frame != null) {
                    selected = frame.pick(event.getX() * frame.image().getWidth() / Math.max(1, getWidth()),
                            event.getY() * frame.image().getHeight() / Math.max(1, getHeight()));
                    StructureViewport.this.onSelect.accept(selected);
                    changed();
                }
            }
            @Override public void mouseDragged(MouseEvent event) {
                if ((event.getModifiersEx() & MouseEvent.BUTTON3_DOWN_MASK) != 0) {
                    camera.look(event.getX() - mouseX, event.getY() - mouseY);
                    changed();
                }
                mouseX = event.getX(); mouseY = event.getY();
            }
            @Override public void mouseWheelMoved(MouseWheelEvent event) {
                camera.wheel(event.getPreciseWheelRotation()); changed();
            }
        };
        addMouseListener(mouse);
        addMouseMotionListener(mouse);
        addMouseWheelListener(mouse);
        addComponentListener(new ComponentAdapter() {
            @Override public void componentResized(ComponentEvent e) { changed(); }
        });
        timer = new Timer(16, event -> tick());
        timer.start();
    }

    private int key(int code) { return keys.contains(code) ? 1 : 0; }
    private void changed() { dirty = true; version++; repaint(); }
    private void tick() {
        long now = System.nanoTime();
        double dt = (now - lastTick) / 1e9;
        lastTick = now;
        if (!camera.view().orbit() && !keys.isEmpty()) {
            Camera.View before = camera.view();
            camera.move(key(KeyEvent.VK_W) - key(KeyEvent.VK_S), key(KeyEvent.VK_D) - key(KeyEvent.VK_A),
                    key(KeyEvent.VK_SPACE) - key(KeyEvent.VK_SHIFT), keys.contains(KeyEvent.VK_CONTROL), dt);
            if (!before.position().equals(camera.view().position())) changed();
        }
        if (!dirty || rendering || closed || getWidth() < 1 || getHeight() < 1) return;
        dirty = false;
        rendering = true;
        long submitted = version;
        BlockMesh current = mesh;
        Camera.View view = camera.view();
        int select = selected;
        boolean wire = wireframe, box = bounds;
        double scale = Math.min(1, Math.min(1100.0 / getWidth(), 800.0 / getHeight()));
        int width = Math.max(1, (int) (getWidth() * scale)), height = Math.max(1, (int) (getHeight() * scale));
        renderer.submit(() -> {
            try {
                var result = new SoftwareRenderer().render(current, view, width, height, select, wire, box);
                SwingUtilities.invokeLater(() -> {
                    rendering = false;
                    if (closed || mesh != current) return;
                    frame = result;
                    renderError = null;
                    dirty |= submitted != version;
                    repaint();
                });
            } catch (RuntimeException error) {
                SwingUtilities.invokeLater(() -> {
                    rendering = false;
                    renderError = error.getClass().getSimpleName() + ": " + error.getMessage();
                    repaint();
                });
            }
        });
    }

    /** Publishes a prepared mesh; new files recenter while layer changes preserve the camera. */
    public void setMesh(BlockMesh mesh, boolean recenter) {
        this.mesh = mesh; frame = null; selected = -1;
        if (recenter) camera.focus(mesh.data().size());
        changed();
    }

    /** Fits the current structure. */
    public void focusStructure() { if (mesh != null) camera.focus(mesh.data().size()); changed(); }
    /** Changes display overlays without rebuilding geometry. */
    public void setOverlays(boolean wireframe, boolean bounds) {
        this.wireframe = wireframe; this.bounds = bounds; changed();
    }
    /** Read-only camera state for diagnostics and input regression checks. */
    public Camera.View cameraView() { return camera.view(); }
    /** Latest rendered pixels and block IDs, useful for independent smoke validation. */
    public SoftwareRenderer.Frame renderedFrame() { return frame; }

    @Override protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        if (frame != null) g.drawImage(frame.image(), 0, 0, getWidth(), getHeight(), null);
        g.setColor(new Color(0xc2d7df));
        var view = camera.view();
        g.drawString(view.orbit() ? "ORBIT CAMERA  ·  F2 to fly inside" :
                String.format(java.util.Locale.ROOT, "FREE CAMERA  ·  %.1f blocks/s  ·  Ctrl for precision", view.speed()), 16, 24);
        g.drawString(String.format(java.util.Locale.ROOT, "X %.2f   Y %.2f   Z %.2f",
                view.position().x(), view.position().y(), view.position().z()), 16, 44);
        if (mesh == null) g.drawString("Select an NBT structure from the browser or use Open NBT.", 16, 78);
        if (renderError != null) { g.setColor(Color.PINK); g.drawString("Preview error: " + renderError, 16, 100); }
    }

    /** Stops this viewport's timer and renderer when its window closes. */
    @Override public void close() { closed = true; timer.stop(); keys.clear(); renderer.shutdownNow(); }
}

