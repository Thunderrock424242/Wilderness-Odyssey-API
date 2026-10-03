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
    private RenderQuality quality = RenderQuality.HIGH;
    private boolean blockEdges = true, textures = true, showEntities, showBlockEntities, showCoordinates;
    private java.util.List<DebugOverlay.Marker> markers = java.util.List.of();
    private Runnable onFrameReady=()->{};

    /** Creates a focusable viewport with freecam/orbit bindings and block picking. */
    public StructureViewport(IntConsumer onSelect, Runnable onReload) {
        this.onSelect = onSelect;
        this.onReload = onReload;
        setFocusable(true);
        setBackground(ViewerTheme.VIEWPORT);
        setToolTipText("Right-drag: look / orbit. Left-click: inspect. F1: orbit. F2: free camera.");
        addKeyListener(new KeyAdapter() {
            @Override public void keyPressed(KeyEvent event) {
                if (!keys.add(event.getKeyCode())) return;
                switch (event.getKeyCode()) {
                    case KeyEvent.VK_F1 -> camera.setOrbit(true);
                    case KeyEvent.VK_F2 -> camera.setOrbit(false);
                    case KeyEvent.VK_F -> focusStructure();
                    case KeyEvent.VK_G -> focusSelected();
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
        boolean wire = wireframe, box = bounds, edges = blockEdges, textured = textures;
        boolean entities = showEntities, blockEntities = showBlockEntities, coordinates = showCoordinates;
        var currentMarkers = markers;
        int background=ViewerTheme.VIEWPORT.getRGB();
        double scale = quality.scale(getWidth(), getHeight());
        int width = Math.max(1, (int) (getWidth() * scale)), height = Math.max(1, (int) (getHeight() * scale));
        renderer.submit(() -> {
            try {
                var result = new SoftwareRenderer().render(current, view, width, height, select, wire, box, edges, textured,background);
                java.awt.Graphics2D overlay = result.image().createGraphics();
                DebugOverlay.draw(overlay, width, height, view, currentMarkers, entities, blockEntities, coordinates);
                overlay.dispose();
                SwingUtilities.invokeLater(() -> {
                    rendering = false;
                    if (closed || mesh != current) return;
                    frame = result;
                    renderError = null;
                    dirty |= submitted != version;
                    if(!dirty)onFrameReady.run();
                    repaint();
                });
            } catch (RuntimeException error) {
                SwingUtilities.invokeLater(() -> {
                    rendering = false;
                    if(closed||mesh!=current||submitted!=version)return;
                    renderError = error.getClass().getSimpleName() + ": " + error.getMessage();
                    if(!closed)onFrameReady.run();
                    repaint();
                });
            }
        });
    }

    /** Publishes a prepared mesh; new files recenter while layer changes preserve the camera. */
    public void setMesh(BlockMesh mesh, boolean recenter) {
        this.mesh = mesh; frame = null; selected = -1;
        if (recenter) camera.focus(mesh);
        changed();
    }

    /** Fits the current structure. */
    public void focusStructure() { if (mesh != null) camera.focus(mesh); changed(); }
    /** Clears stale pack content when switching the source root. */
    public void clear() { mesh=null;frame=null;selected=-1;renderError=null;markers=java.util.List.of();changed(); }
    /** Changes display overlays without rebuilding geometry. */
    public void setOverlays(boolean wireframe, boolean bounds) {
        this.wireframe = wireframe; this.bounds = bounds; changed();
    }
    /** Focuses the camera on one selected block at an inspectable distance. */
    public void focusSelected() {
        if (mesh != null && selected >= 0 && selected < mesh.data().blocks().size()) {
            camera.focusBlock(mesh.data().blocks().get(selected).position());
            changed();
        }
    }
    /** Applies a manual quality choice without rebuilding or dropping model geometry. */
    public void setQuality(RenderQuality quality, boolean edges, boolean textures) {
        this.quality = quality; this.blockEdges = edges; this.textures = textures; changed();
    }
    /** Installs precomputed diagnostic marker positions. */
    public void setMarkers(java.util.List<DebugOverlay.Marker> markers) { this.markers = markers; changed(); }
    /** Controls bounded diagnostic overlays separately from block geometry. */
    public void setDebugOverlays(boolean entities, boolean blockEntities, boolean coordinates) {
        showEntities = entities; showBlockEntities = blockEntities; showCoordinates = coordinates; changed();
    }
    /** Read-only camera state for diagnostics and input regression checks. */
    public Camera.View cameraView() { return camera.view(); }
    /** Latest rendered pixels and block IDs, useful for independent smoke validation. */
    public SoftwareRenderer.Frame renderedFrame() { return frame; }

    /** Whether the current mesh and camera have finished producing a frame. */
    public boolean renderingIdle() { return frame != null && !dirty && !rendering; }

    /** Completion returns to the event thread so the loading screen can release the preview. */
    public void onFrameReady(Runnable listener) { onFrameReady=listener; }
    public void applyTheme() { setBackground(ViewerTheme.VIEWPORT);changed(); }

    @Override protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        if (frame != null) {
            java.awt.Graphics2D scaled = (java.awt.Graphics2D) g.create();
            scaled.setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION,
                    frame.image().getWidth() > getWidth() ? java.awt.RenderingHints.VALUE_INTERPOLATION_BILINEAR
                            : java.awt.RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
            scaled.drawImage(frame.image(), 0, 0, getWidth(), getHeight(), null);
            scaled.dispose();
        }
        java.awt.Graphics2D overlay = (java.awt.Graphics2D)g.create();
        overlay.setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING, java.awt.RenderingHints.VALUE_ANTIALIAS_ON);
        if (mesh == null) {
            int center = getWidth()/2, y = Math.max(90, getHeight()/2-65);
            overlay.drawImage(ViewerTheme.icon(44),center-22,y-50,null);
            centered(overlay,"Open a modpack to get started",center,y+24,18,true,ViewerTheme.TEXT);
            centered(overlay,"Choose a structure from the library to preview it here.",center,y+52,13,false,ViewerTheme.MUTED);
            centered(overlay,"Use Open structure to inspect a single NBT or JSON file.",center,y+74,12,false,ViewerTheme.MUTED);
        } else {
            var view = camera.view();
            String cameraHint = view.orbit() ? "Orbit  ·  Right-drag to rotate  ·  F2 to fly" :
                    "Free camera  ·  WASD to move  ·  F1 to orbit";
            badge(overlay,cameraHint,14,getHeight()-38,false);
            if (frame != null) badge(overlay,frame.image().getWidth()+" × "+frame.image().getHeight(),getWidth()-14,getHeight()-38,true);
            if (showCoordinates) {
                String coordinates = String.format(java.util.Locale.ROOT,"Camera  X %.1f  Y %.1f  Z %.1f",view.position().x(),view.position().y(),view.position().z());
                badge(overlay,coordinates,14,14,false);
                if (selected >= 0) {
                    var p = mesh.data().blocks().get(selected).position();
                    badge(overlay,"Block  X "+p.x()+"  Y "+p.y()+"  Z "+p.z(),14,46,false);
                }
            }
        }
        if (renderError != null) badge(overlay,"Preview error: "+renderError,14,14,false);
        overlay.dispose();
    }

    private static void centered(java.awt.Graphics2D g,String text,int x,int y,int size,boolean bold,Color color) {
        g.setFont(new java.awt.Font("Segoe UI",bold?java.awt.Font.BOLD:java.awt.Font.PLAIN,size));g.setColor(color);
        g.drawString(text,x-g.getFontMetrics().stringWidth(text)/2,y);
    }

    private static void badge(java.awt.Graphics2D g,String text,int x,int y,boolean alignRight) {
        g.setFont(new java.awt.Font("Segoe UI",java.awt.Font.PLAIN,12));
        int width=g.getFontMetrics().stringWidth(text)+20;
        if(alignRight)x-=width;
        Color panel=ViewerTheme.PANEL;g.setColor(new Color(panel.getRed(),panel.getGreen(),panel.getBlue(),230));g.fillRoundRect(x,y,width,26,6,6);
        g.setColor(ViewerTheme.TEXT);g.drawString(text,x+10,y+18);
    }

    /** Stops this viewport's timer and renderer when its window closes. */
    @Override public void close() { closed = true; timer.stop(); keys.clear(); renderer.shutdownNow(); }
}

