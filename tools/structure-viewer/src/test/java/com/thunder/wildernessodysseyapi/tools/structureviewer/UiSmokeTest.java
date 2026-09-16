package com.thunder.wildernessodysseyapi.tools.structureviewer;

import com.thunder.wildernessodysseyapi.tools.structureviewer.ui.*;
import javax.imageio.ImageIO;
import javax.swing.SwingUtilities;
import java.awt.Graphics2D;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.nio.file.*;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Real-window smoke validation using targeted Swing input events. It does not inject global
 * keyboard/mouse input into unrelated user applications and always disposes its own window.
 */
public final class UiSmokeTest {
    private UiSmokeTest() {}

    /** Opens an existing resource, exercises input handlers and selection, and captures the window. */
    public static void main(String[] args) throws Exception {
        Path project = Path.of(System.getProperty("structureViewer.projectDir"));
        Path build = Path.of(System.getProperty("structureViewer.buildDir"));
        Path output = Path.of(args[0]); Files.createDirectories(output);
        Path fixture = build.resolve("generated/structuregen/resources/data/wildernessodysseyapi/structure/test_shelter.nbt");
        if (!Files.isRegularFile(fixture)) fixture = project.resolve("src/main/resources/data/wildernessodysseyapi/structures/bunker.nbt");
        String override = System.getProperty("structureViewer.smokeFile", "");
        if (!override.isBlank()) fixture = Path.of(override);
        StructureViewer.main(new String[]{"--open", fixture.toString()});
        ViewerWindow window = edt(() -> java.util.Arrays.stream(java.awt.Window.getWindows())
                .filter(ViewerWindow.class::isInstance).map(ViewerWindow.class::cast).findFirst().orElseThrow());
        try {
            await(() -> {
                if (window.loadError() != null) throw new IllegalStateException(window.loadError());
                return window.loadedStructure() != null && window.viewport().renderedFrame() != null;
            },120000);
            edt(() -> {
                if (!window.isShowing()) throw new AssertionError("Viewer window did not launch.");
                var viewport = window.viewport();
                var frame = viewport.renderedFrame();
                int x = -1, y = -1, blockIndex = -1;
                // Choose an actual component pixel, using the same scaled framebuffer mapping as a real click.
                search: for (int py = 60; py < viewport.getHeight(); py++) {
                    for (int px = 0; px < viewport.getWidth(); px++) {
                        int candidate = frame.pick(px * frame.image().getWidth() / viewport.getWidth(),
                                py * frame.image().getHeight() / viewport.getHeight());
                        if (candidate >= 0) { x = px; y = py; blockIndex = candidate; break search; }
                    }
                }
                if (blockIndex < 0) throw new AssertionError("Existing structure produced no visible blocks.");
                viewport.dispatchEvent(new MouseEvent(viewport,MouseEvent.MOUSE_PRESSED,System.currentTimeMillis(),0,x,y,1,false,MouseEvent.BUTTON1));
                String expected = window.loadedStructure().state(window.loadedStructure().blocks().get(blockIndex)).id();
                if (!window.inspectorText().contains(expected) || !window.inspectorText().contains("Block entity NBT"))
                    throw new AssertionError("Click did not populate the block inspector. Expected " + expected + "; got " + window.inspectorText());
                return null;
            });
            snapshot(window,output.resolve("structure-and-inspector.png"));
            var before = edt(() -> window.viewport().cameraView());
            edt(() -> {key(window.viewport(),KeyEvent.VK_F2,true);key(window.viewport(),KeyEvent.VK_F2,false);
                key(window.viewport(),KeyEvent.VK_W,true);return null;});
            Thread.sleep(300);
            edt(() -> {key(window.viewport(),KeyEvent.VK_W,false);return null;});
            var after = edt(() -> window.viewport().cameraView());
            if (after.orbit() || before.position().equals(after.position())) throw new AssertionError("F2/W freecam input failed.");
            edt(() -> {
                var v = window.viewport();
                v.dispatchEvent(new MouseEvent(v,MouseEvent.MOUSE_PRESSED,System.currentTimeMillis(),MouseEvent.BUTTON3_DOWN_MASK,100,100,1,false,MouseEvent.BUTTON3));
                v.dispatchEvent(new MouseEvent(v,MouseEvent.MOUSE_DRAGGED,System.currentTimeMillis(),MouseEvent.BUTTON3_DOWN_MASK,130,110,0,false,MouseEvent.NOBUTTON));
                return null;
            });
            if (after.forward().equals(edt(() -> window.viewport().cameraView().forward()))) throw new AssertionError("Mouse look failed.");
            edt(() -> {key(window.viewport(),KeyEvent.VK_F1,true);key(window.viewport(),KeyEvent.VK_F1,false);return null;});
            if (!edt(() -> window.viewport().cameraView().orbit())) throw new AssertionError("F1 orbit input failed.");
            System.out.println("GUI SMOKE PASSED: real window, existing " + fixture.getFileName()
                    + ", rendered blocks, click inspector, F2/W movement, mouse look, F1 orbit. Screenshot: " + output);
        } finally { snapshot(window,output.resolve("last-window.png")); edt(() -> {window.dispose();return null;}); }
    }

    private static void key(StructureViewport viewport,int code,boolean down) {
        var event = new KeyEvent(viewport,down ? KeyEvent.KEY_PRESSED : KeyEvent.KEY_RELEASED,
                System.currentTimeMillis(),0,code,KeyEvent.CHAR_UNDEFINED);
        for(var listener:viewport.getKeyListeners()) {
            if(down)listener.keyPressed(event);else listener.keyReleased(event);
        }
    }

    private static void snapshot(ViewerWindow window,Path output) throws Exception {
        BufferedImage image = edt(() -> {
            var result = new BufferedImage(window.getWidth(),window.getHeight(),BufferedImage.TYPE_INT_RGB);
            Graphics2D graphics = result.createGraphics();window.paint(graphics);graphics.dispose();return result;
        });
        ImageIO.write(image,"png",output.toFile());
    }

    private static void await(Callable<Boolean> condition,long timeout) throws Exception {
        long end = System.currentTimeMillis()+timeout;
        while(System.currentTimeMillis()<end){if(edt(condition))return;Thread.sleep(50);}
        throw new AssertionError("Timed out waiting for the viewer.");
    }

    private static <T> T edt(Callable<T> action) throws Exception {
        AtomicReference<T> result = new AtomicReference<>();
        AtomicReference<Throwable> failure = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> {try {result.set(action.call());}catch(Throwable e){failure.set(e);}});
        if(failure.get()!=null) throw new IllegalStateException("UI smoke failure",failure.get());
        return result.get();
    }
}

