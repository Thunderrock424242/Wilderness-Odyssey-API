package com.thunder.wildernessodysseyapi.tools.structureviewer;

import com.thunder.wildernessodysseyapi.tools.structureviewer.ui.*;
import com.thunder.wildernessodysseyapi.tools.structureviewer.io.JsonInput;
import com.thunder.wildernessodysseyapi.tools.structureviewer.render.RenderQuality;
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
        Path settings = output.resolve("smoke-settings.properties");
        new ViewerSettings(RenderQuality.HIGH,true,true,true,java.util.List.of()).save(settings);
        System.setProperty("structureViewer.settings",settings.toString());
        Path fixture = build.resolve("generated/structuregen/resources/data/wildernessodysseyapi/structure/test_shelter.nbt");
        if (!Files.isRegularFile(fixture)) fixture = project.resolve("src/main/resources/data/wildernessodysseyapi/structures/bunker.nbt");
        String override = System.getProperty("structureViewer.smokeFile", "");
        if (!override.isBlank()) fixture = Path.of(override);
        Path requestedFixture = fixture.toAbsolutePath().normalize();
        StructureViewer.main(new String[]{"--open", requestedFixture.toString()});
        ViewerWindow window = edt(() -> java.util.Arrays.stream(java.awt.Window.getWindows())
                .filter(ViewerWindow.class::isInstance).map(ViewerWindow.class::cast).findFirst().orElseThrow());
        try {
            await(() -> {
                if (window.loadError() != null) throw new IllegalStateException(window.loadError());
                return window.loadedStructure() != null && window.viewport().renderingIdle();
            },120000);
            edt(() -> {
                if (!window.isShowing()) throw new AssertionError("Viewer window did not launch.");
                if (!window.loadedStructure().source().equals(requestedFixture))
                    throw new AssertionError("A different structure was selected during the smoke check: "+window.loadedStructure().source());
                System.out.println("Opened source: "+window.loadedStructure().source()+" | "+window.loadedStructure().blocks().size()+" blocks | "+window.loadedStructure().size());
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
            await(() -> window.viewport().renderingIdle(),120000);
            snapshot(window,output.resolve("structure-and-inspector.png"));
            edt(() -> {window.selectQuality(RenderQuality.FAST);return null;});
            await(() -> window.viewport().renderingIdle(),120000);
            int fastWidth = edt(() -> window.viewport().renderedFrame().image().getWidth());
            edt(() -> {window.selectQuality(RenderQuality.ULTRA);return null;});
            await(() -> window.viewport().renderingIdle(),120000);
            int ultraWidth = edt(() -> window.viewport().renderedFrame().image().getWidth());
            if(ultraWidth<=fastWidth)throw new AssertionError("Ultra did not increase rendering resolution.");
            if(ViewerSettings.read(settings).quality()!=RenderQuality.ULTRA)throw new AssertionError("Quality selection was not saved.");
            var allView = edt(() -> window.viewport().cameraView());
            edt(() -> {key(window.viewport(),KeyEvent.VK_G,true);key(window.viewport(),KeyEvent.VK_G,false);return null;});
            await(() -> window.viewport().renderingIdle(),120000);
            if(allView.position().equals(edt(() -> window.viewport().cameraView().position())))throw new AssertionError("G did not focus the selected block.");
            snapshot(window,output.resolve("ultra-block-detail.png"));
            edt(() -> {window.selectQuality(RenderQuality.HIGH);window.viewport().focusStructure();return null;});
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
            // Reload only a copied fixture, preserving authored templates and user preferences.
            Path watched = output.resolve("reload-fixture.json");
            Files.copy(project.resolve("src/main/structure_blueprints/test_shelter.json"),watched,StandardCopyOption.REPLACE_EXISTING);
            edt(() -> {window.load(watched);return null;});
            await(() -> window.loadedStructure().source().equals(watched.toAbsolutePath()) && window.viewport().renderingIdle(),120000);
            snapshot(window,output.resolve("json-textured-preview.png"));
            var reloadView = edt(() -> window.viewport().cameraView());
            var json = JsonInput.read(watched);
            json.addProperty("name","Automatic reload check");
            Path replacement = output.resolve("replacement.json");
            Files.writeString(replacement,json.toString());
            Files.move(replacement,watched,StandardCopyOption.REPLACE_EXISTING);
            await(() -> window.loadedStructure().name().equals("Automatic reload check") && window.viewport().renderingIdle(),120000);
            if(!reloadView.equals(edt(() -> window.viewport().cameraView())))throw new AssertionError("Reload reset the camera.");
            Files.writeString(watched,"{\"incomplete\":");
            await(() -> window.loadError()!=null,15000);
            if(!edt(() -> window.loadedStructure().name()).equals("Automatic reload check"))throw new AssertionError("Invalid reload lost the previous preview.");
            json.addProperty("name","Recovered reload check");
            Files.writeString(watched,json.toString());
            await(() -> window.loadedStructure().name().equals("Recovered reload check") && window.loadError()==null && window.viewport().renderingIdle(),15000);
            System.out.println("PHASE 2 GUI PASSED: JSON, atomic reload, camera preservation, failed reload recovery, Fast/Ultra resolution "+fastWidth+"/"+ultraWidth+", saved quality, G block focus.");
            System.out.println("GUI SMOKE PASSED: real window, existing " + fixture.getFileName()
                    + ", rendered blocks, click inspector, F2/W movement, mouse look, F1 orbit. Screenshot: " + output);
        } finally { try {snapshot(window,output.resolve("last-window.png"));} finally {edt(() -> {window.dispose();return null;});} }
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

