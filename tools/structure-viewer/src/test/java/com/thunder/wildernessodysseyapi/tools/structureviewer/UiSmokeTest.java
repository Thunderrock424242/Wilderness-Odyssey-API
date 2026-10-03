package com.thunder.wildernessodysseyapi.tools.structureviewer;

import com.thunder.wildernessodysseyapi.tools.structureviewer.ui.*;
import com.thunder.wildernessodysseyapi.tools.structureviewer.io.JsonInput;
import com.thunder.wildernessodysseyapi.tools.structureviewer.io.StructureCatalog;
import com.thunder.wildernessodysseyapi.tools.structureviewer.io.StructureSource;
import com.thunder.wildernessodysseyapi.tools.structureviewer.render.RenderQuality;
import javax.imageio.ImageIO;
import javax.swing.SwingUtilities;
import javax.swing.JTree;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.TreePath;
import java.awt.Component;
import java.awt.Container;
import java.awt.Graphics2D;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.nio.file.*;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicReference;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Real-window smoke validation using targeted Swing input events. It does not inject global
 * keyboard/mouse input into unrelated user applications and always disposes its own window.
 */
public final class UiSmokeTest {
    private UiSmokeTest() {}

    /** Opens an existing resource, exercises input handlers and selection, and captures the window. */
    public static void main(String[] args) throws Exception {
        if (Boolean.getBoolean("structureViewer.requirePackaged")
                && !StructureViewer.class.getProtectionDomain().getCodeSource().getLocation().getPath().endsWith(".jar"))
            throw new AssertionError("Bundled smoke test loaded application classes outside the packaged JAR.");
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
            verifyAppearanceAndLoading(window,settings,output);
            verifyLayout(window,output);
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
            // A selected layer must clamp before meshing when a fresh export becomes shorter.
            edt(() -> {window.selectLayer(4);return null;});
            await(() -> window.viewport().renderingIdle(),15000);
            json.addProperty("name","Shorter export check");
            json.getAsJsonArray("size").set(1,new com.google.gson.JsonPrimitive(1));
            var ground=new com.google.gson.JsonArray();
            for(var block:json.getAsJsonArray("blocks"))
                if(block.getAsJsonObject().getAsJsonArray("pos").get(1).getAsInt()==0)ground.add(block);
            json.add("blocks",ground);Files.writeString(watched,json.toString());
            await(() -> window.loadedStructure().name().equals("Shorter export check") && window.viewport().renderingIdle(),15000);
            if(!edt(() -> java.util.Arrays.stream(window.viewport().renderedFrame().blockIds()).anyMatch(id -> id>=0)))
                throw new AssertionError("Shrinking the export left an empty preview at the old Y layer.");
            verifyModpack(window,project,output);
            System.out.println("PHASE 2 GUI PASSED: JSON, atomic reload, camera preservation, failed reload recovery, Fast/Ultra resolution "+fastWidth+"/"+ultraWidth+", saved quality, G block focus.");
            System.out.println("GUI SMOKE PASSED: real window, existing " + fixture.getFileName()
                    + ", rendered blocks, click inspector, F2/W movement, mouse look, F1 orbit. Screenshot: " + output);
        } finally { try {snapshot(window,output.resolve("last-window.png"));} finally {edt(() -> {window.dispose();return null;});} }
    }

    private static void verifyModpack(ViewerWindow window,Path project,Path output) throws Exception {
        Path emptyPack=Files.createDirectories(output.resolve("empty-modpack"));
        edt(() -> {window.openModpack(emptyPack);return null;});
        await(() -> window.loadedStructure()==null && window.viewport().renderingIdle(),15000);
        snapshot(window,output.resolve("empty-state.png"));
        Path pack=Files.createDirectories(output.resolve("modpack-fixture"));
        Path mods=Files.createDirectories(pack.resolve("mods")),jar=mods.resolve("demo-structures.jar");
        byte[] shelter=Files.readAllBytes(project.resolve("src/main/structure_blueprints/test_shelter.json"));
        byte[] binary=Files.readAllBytes(FixtureNbt.write(output.resolve("archive-fixture.nbt"),FixtureNbt.structure(),true));
        writeMod(jar,binary,shelter,"Archive room");
        byte[] original=Files.readAllBytes(jar);
        edt(() -> {window.openModpack(pack);return null;});
        var house=new StructureSource(jar,"data/demo/structure/shelter.json");
        await(() -> selectInLibrary(window,house),15000);
        await(() -> house.equals(window.loadedSource()) && window.viewport().renderingIdle(),120000);
        var shelterView=edt(() -> window.viewport().cameraView());
        snapshot(window,output.resolve("modpack-library.png"));
        java.awt.Dimension fullSize=edt(window::getSize);
        edt(() -> {window.setSize(1024,680);window.validate();return null;});
        awaitSizedFrame(window);
        snapshot(window,output.resolve("laptop-modpack-library.png"));
        edt(() -> {window.setSize(fullSize);window.validate();return null;});
        awaitSizedFrame(window);
        var room=new StructureSource(jar,"data/demo/structure/room.json");
        edt(() -> {if(!selectInLibrary(window,room))throw new AssertionError("Room absent from the JAR library.");return null;});
        await(() -> room.equals(window.loadedSource()) && window.viewport().renderingIdle(),15000);
        if(shelterView.equals(edt(() -> window.viewport().cameraView())))throw new AssertionError("Switching entries in one JAR did not fit the new structure.");
        if(!edt(() -> window.loadedStructure().state(window.loadedStructure().blocks().getFirst()).id()).equals("demo:preview_block"))
            throw new AssertionError("Selected the wrong JAR entry.");
        if(!java.util.Arrays.equals(original,Files.readAllBytes(jar)))throw new AssertionError("Preview modified the mod JAR.");
        var archiveView=edt(() -> window.viewport().cameraView());
        Path replacement=mods.resolve("replacement.tmp");
        writeMod(replacement,binary,shelter,"Reloaded archive room");
        Files.move(replacement,jar,StandardCopyOption.REPLACE_EXISTING);
        await(() -> room.equals(window.loadedSource()) && window.loadedStructure().name().equals("Reloaded archive room")
                && window.viewport().renderingIdle(),15000);
        if(!archiveView.equals(edt(() -> window.viewport().cameraView())))throw new AssertionError("Archive reload reset the camera.");
        var binarySource=new StructureSource(jar,"data/demo/structures/template.nbt");
        edt(() -> {if(!selectInLibrary(window,binarySource))throw new AssertionError("NBT absent from the JAR library.");return null;});
        await(() -> binarySource.equals(window.loadedSource()) && window.viewport().renderingIdle(),120000);
        if(!edt(() -> window.loadedStructure().source()).equals(jar.toAbsolutePath().normalize()))
            throw new AssertionError("Archive source identity was lost.");
        System.out.println("MODPACK GUI PASSED: grouped JAR selection, NBT/JSON, source immutability, switching entries fits camera, atomic JAR reload preserves camera, shrinking Y layer.");
    }

    private static void verifyLayout(ViewerWindow window,Path output) throws Exception {
        java.awt.Dimension original=edt(window::getSize);
        edt(() -> {window.setSize(980,640);window.validate();return null;});
        awaitSizedFrame(window);
        edt(() -> {
            for(String name:java.util.List.of("open-modpack","open-structure","theme-toggle","render-quality","y-layer","view-options")) {
                Component control=find(window,name);
                if(control==null || !control.isShowing())throw new AssertionError("Missing visible control: "+name);
                for(Container parent=control.getParent();parent!=null;parent=parent.getParent()) {
                    java.awt.Rectangle rect=SwingUtilities.convertRectangle(control.getParent(),control.getBounds(),parent);
                    if(!new java.awt.Rectangle(0,0,parent.getWidth(),parent.getHeight()).contains(rect))
                        throw new AssertionError("Clipped control at laptop size: "+name+" in "+parent.getClass().getSimpleName());
                }
            }
            var spinner=(javax.swing.JSpinner)find(window,"y-layer");
            var field=((javax.swing.JSpinner.DefaultEditor)spinner.getEditor()).getTextField();
            if(!field.getText().equals("All"))throw new AssertionError("The full-structure layer should read All.");
            field.setText("All");field.commitEdit();
            if(!spinner.getValue().equals(-1))throw new AssertionError("Typing All did not restore the full-structure layer.");
            return null;
        });
        snapshot(window,output.resolve("laptop-layout.png"));
        edt(() -> {window.setSize(original);window.validate();return null;});
        awaitSizedFrame(window);
        var textured=edt(() -> window.viewport().renderedFrame());
        var structure=edt(window::loadedStructure);
        var camera=edt(() -> window.viewport().cameraView());
        clickViewOption(window,"Textures");
        await(() -> window.viewport().renderingIdle(),15000);
        var plain=edt(() -> window.viewport().renderedFrame());
        if(structure!=edt(window::loadedStructure) || !camera.equals(edt(() -> window.viewport().cameraView())))
            throw new AssertionError("Texture menu toggle reloaded the structure or changed the camera.");
        if(java.util.Arrays.stream(plain.blockIds()).noneMatch(id -> id>=0))throw new AssertionError("Texture menu toggle lost block picking.");
        if(java.util.Arrays.equals(textured.image().getRGB(0,0,textured.image().getWidth(),textured.image().getHeight(),null,0,textured.image().getWidth()),
                plain.image().getRGB(0,0,plain.image().getWidth(),plain.image().getHeight(),null,0,plain.image().getWidth())))
            throw new AssertionError("The actual View options menu did not change textured rendering.");
        clickViewOption(window,"Textures");
        await(() -> window.viewport().renderingIdle(),15000);
        // Cutout/translucent texels legitimately change visible picks when textures are off.
        // Restoring textures must restore the original picks for the same mesh and camera.
        if(!java.util.Arrays.equals(textured.blockIds(),edt(() -> window.viewport().renderedFrame().blockIds())))
            throw new AssertionError("Restoring textures did not restore the original block picks.");
        System.out.println("LAYOUT GUI PASSED: controls remain visible at 980x640, All layer input, View options texture toggle and restored picking.");
    }

    private static void verifyAppearanceAndLoading(ViewerWindow window,Path settings,Path output) throws Exception {
        var camera=edt(() -> window.viewport().cameraView());
        var structure=edt(window::loadedStructure);
        int light=edt(() -> window.viewport().renderedFrame().image().getRGB(0,0));
        edt(() -> {
            Component toggle=find(window,"theme-toggle");
            if(!(toggle instanceof javax.swing.JToggleButton button))throw new AssertionError("No Light/Dark switch in the header.");
            button.doClick();return null;
        });
        await(() -> window.viewport().renderingIdle(),15000);
        if(!ViewerSettings.read(settings).darkMode())throw new AssertionError("Dark appearance was not saved.");
        if(light==edt(() -> window.viewport().renderedFrame().image().getRGB(0,0)))throw new AssertionError("Dark appearance did not change the viewport background.");
        if(structure!=edt(window::loadedStructure)||!camera.equals(edt(() -> window.viewport().cameraView())))throw new AssertionError("Theme switching replaced the preview or camera.");
        snapshot(window,output.resolve("dark-structure.png"));
        edt(() -> {
            window.load(window.loadedSource());
            Component screen=find(window,"loading-screen");
            Component progress=find(window,"loading-progress");
            if(screen==null||!screen.isShowing()||!(progress instanceof javax.swing.JProgressBar bar)||!bar.isIndeterminate())
                throw new AssertionError("Loading a structure did not show progress.");
            ((javax.swing.JToggleButton)find(window,"theme-toggle")).doClick();
            snapshot(window,output.resolve("light-loading.png"));
            return null;
        });
        await(() -> window.viewport().renderingIdle() && !find(window,"loading-screen").isShowing(),120000);
        if(ViewerSettings.read(settings).darkMode())throw new AssertionError("Light appearance was not saved during loading.");
        edt(() -> {
            var button=(javax.swing.JButton)find(window,"open-modpack");
            if(contrast(button.getBackground(),button.getForeground())<4.5)throw new AssertionError("Open modpack text has insufficient contrast.");
            return null;
        });
        System.out.println("APPEARANCE GUI PASSED: saved Light/Dark switch, themed viewport, preserved camera, loading screen with progress, readable Open modpack.");
    }

    private static double contrast(java.awt.Color a,java.awt.Color b) {
        double first=luminance(a),second=luminance(b);return(Math.max(first,second)+.05)/(Math.min(first,second)+.05);
    }
    private static double luminance(java.awt.Color color) {
        double[] channels={color.getRed()/255.0,color.getGreen()/255.0,color.getBlue()/255.0};
        for(int i=0;i<3;i++)channels[i]=channels[i]<=.04045?channels[i]/12.92:Math.pow((channels[i]+.055)/1.055,2.4);
        return .2126*channels[0]+.7152*channels[1]+.0722*channels[2];
    }

    private static void awaitSizedFrame(ViewerWindow window) throws Exception {
        await(() -> {
            var viewport=window.viewport();if(!viewport.renderingIdle())return false;
            double scale=RenderQuality.HIGH.scale(viewport.getWidth(),viewport.getHeight());
            return viewport.renderedFrame().image().getWidth()==Math.max(1,(int)(viewport.getWidth()*scale))
                    && viewport.renderedFrame().image().getHeight()==Math.max(1,(int)(viewport.getHeight()*scale));
        },15000);
    }

    private static void clickViewOption(ViewerWindow window,String text) throws Exception {
        edt(() -> {
            ((javax.swing.JButton)find(window,"view-options")).doClick();
            for(var element:javax.swing.MenuSelectionManager.defaultManager().getSelectedPath()) {
                if(element.getComponent() instanceof javax.swing.JPopupMenu popup) {
                    for(Component component:popup.getComponents()) {
                        if(component instanceof javax.swing.JCheckBoxMenuItem item && item.getText().equals(text)) {
                            item.doClick();javax.swing.MenuSelectionManager.defaultManager().clearSelectedPath();return null;
                        }
                    }
                }
            }
            throw new AssertionError("View option not found: "+text);
        });
    }

    private static Component find(Container root,String name) {
        for(Component component:root.getComponents()) {
            if(name.equals(component.getName()))return component;
            if(component instanceof Container nested) {
                Component result=find(nested,name);if(result!=null)return result;
            }
        }
        return null;
    }

    private static boolean selectInLibrary(Container component,StructureSource source) {
        for(Component child:component.getComponents()) {
            if(child instanceof JTree tree) {
                var root=(DefaultMutableTreeNode)tree.getModel().getRoot();
                var nodes=root.depthFirstEnumeration();
                while(nodes.hasMoreElements()) {
                    var node=(DefaultMutableTreeNode)nodes.nextElement();
                    if(node.getUserObject() instanceof StructureCatalog.Entry entry && entry.source().equals(source)) {
                        tree.setSelectionPath(new TreePath(node.getPath()));return true;
                    }
                }
            }
            if(child instanceof Container nested && selectInLibrary(nested,source))return true;
        }
        return false;
    }

    private static void writeMod(Path jar,byte[] nbt,byte[] shelter,String roomName) throws Exception {
        try(var out=new ZipOutputStream(Files.newOutputStream(jar))) {
            put(out,"data/demo/structures/template.nbt",nbt);
            put(out,"data/demo/structure/shelter.json",shelter);
            put(out,"data/demo/structure/room.json",("{\"name\":\""+roomName+"\",\"size\":[1,1,1],\"blocks\":[{\"pos\":[0,0,0],\"block\":\"demo:preview_block\"}]}").getBytes(java.nio.charset.StandardCharsets.UTF_8));
        }
    }
    private static void put(ZipOutputStream out,String name,byte[] bytes) throws Exception {
        out.putNextEntry(new ZipEntry(name));out.write(bytes);out.closeEntry();
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
        if(SwingUtilities.isEventDispatchThread())return action.call();
        AtomicReference<T> result = new AtomicReference<>();
        AtomicReference<Throwable> failure = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> {try {result.set(action.call());}catch(Throwable e){failure.set(e);}});
        if(failure.get()!=null) throw new IllegalStateException("UI smoke failure",failure.get());
        return result.get();
    }
}

