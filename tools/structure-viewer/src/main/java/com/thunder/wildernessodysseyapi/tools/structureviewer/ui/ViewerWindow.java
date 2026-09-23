package com.thunder.wildernessodysseyapi.tools.structureviewer.ui;

import com.thunder.wildernessodysseyapi.tools.structureviewer.assets.*;
import com.thunder.wildernessodysseyapi.tools.structureviewer.io.*;
import com.thunder.wildernessodysseyapi.tools.structureviewer.model.*;
import com.thunder.wildernessodysseyapi.tools.structureviewer.render.*;
import com.thunder.wildernessodysseyapi.tools.structureviewer.validation.StructureValidation;
import javax.swing.*;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.tree.*;
import java.awt.*;
import java.awt.event.*;
import java.io.IOException;
import java.nio.file.*;
import java.util.List;
import java.util.ArrayList;
import java.util.concurrent.*;

/** Desktop structure studio shell. Loading, assets, watching, and rendering have separate lifecycles. */
public final class ViewerWindow extends JFrame {
    private final Path project,settingsFile,build;
    private final List<Path> roots;
    private final List<Path> assetPacks=new ArrayList<>();
    private final JTree browser=new JTree(new DefaultMutableTreeNode("Structures"));
    private final JTextArea info=area(),inspector=area(),diagnostics=area(),metadata=area();
    private final JTabbedPane tabs=new JTabbedPane();
    private final JLabel status=new JLabel("Choose a structure to preview.");
    private final JCheckBox air=new JCheckBox("Stored air"),bounds=new JCheckBox("Bounds",true),wireframe=new JCheckBox("Wireframe"),
            edges=new JCheckBox("Block edges",true),textures=new JCheckBox("Textures",true),autoReload=new JCheckBox("Auto reload",true),
            entities=new JCheckBox("Entities"),blockEntities=new JCheckBox("Block entities"),coordinates=new JCheckBox("Coordinates");
    private final JComboBox<RenderQuality> quality=new JComboBox<>(RenderQuality.values());
    private final JSpinner layer=new JSpinner(new SpinnerNumberModel(-1,-1,0,1));
    private final StructureViewport viewport;
    private final ExecutorService loader=Executors.newSingleThreadExecutor(r->{
        Thread t=new Thread(r,"structure-viewer-loader");t.setDaemon(true);return t;
    });
    private Future<?> pending;
    private StructureFileWatcher watcher;
    private long generation;
    private boolean updatingControls,closed;
    private StructureData loaded;
    private Path currentFile;
    private String loadError;

    /** Builds the application on the event thread and restores explicit user preferences. */
    public ViewerWindow(Path project,Path build){
        super("Wilderness Odyssey · Structure Viewer");
        this.project=project.toAbsolutePath().normalize();
        this.build=build;
        roots=StructureDiscovery.roots(this.project,build);
        settingsFile=Path.of(System.getProperty("structureViewer.settings",build.resolve("tools/structure-viewer/settings.properties").toString()));
        viewport=new StructureViewport(this::inspect,this::reload);
        restoreSettings();
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        setMinimumSize(new Dimension(1080,680));
        Dimension screen=Toolkit.getDefaultToolkit().getScreenSize();
        setSize(Math.min(1540,screen.width-60),Math.min(960,screen.height-90));
        setLocationRelativeTo(null);
        setLayout(new BorderLayout(8,8));
        add(toolbars(),BorderLayout.NORTH);
        browser.setShowsRootHandles(true);
        browser.addTreeSelectionListener(event->{
            Object selected=browser.getLastSelectedPathComponent();
            if(selected instanceof DefaultMutableTreeNode node&&node.getUserObject() instanceof StructureFile file)load(file.path());
        });
        JScrollPane tree=new JScrollPane(browser);
        tree.setPreferredSize(new Dimension(270,600));tree.setBorder(BorderFactory.createTitledBorder("Structure browser"));
        tabs.addTab("Structure",new JScrollPane(info));tabs.addTab("Block",new JScrollPane(inspector));
        tabs.addTab("Diagnostics",new JScrollPane(diagnostics));tabs.addTab("Metadata",new JScrollPane(metadata));
        tabs.setPreferredSize(new Dimension(340,600));viewport.setMinimumSize(new Dimension(300,300));
        JSplitPane detail=new JSplitPane(JSplitPane.HORIZONTAL_SPLIT,viewport,tabs);detail.setResizeWeight(1);
        JSplitPane main=new JSplitPane(JSplitPane.HORIZONTAL_SPLIT,tree,detail);main.setResizeWeight(0);
        add(main,BorderLayout.CENTER);
        JPanel footer=new JPanel(new GridLayout(2,1,0,3));footer.setBorder(BorderFactory.createEmptyBorder(2,10,8,10));
        footer.add(status);footer.add(new JLabel("F1 orbit · F2 fly · Right-drag look · WASD move · Space/Shift vertical · Ctrl slow · Wheel speed/zoom · G focus selected block"));
        add(footer,BorderLayout.SOUTH);
        air.addActionListener(event->rebuild());
        layer.addChangeListener(event->{if(!updatingControls)rebuild();});
        for(JCheckBox box:List.of(bounds,wireframe,edges,textures,entities,blockEntities,coordinates))
            box.addActionListener(event->{displayOptions();saveSettings();});
        quality.addActionListener(event->{displayOptions();saveSettings();});
        autoReload.addActionListener(event->{watchCurrent();saveSettings();});
        addWindowListener(new WindowAdapter(){
            @Override public void windowClosed(WindowEvent event){
                closed=true;generation++;if(watcher!=null)watcher.close();
                if(pending!=null)pending.cancel(true);loader.shutdownNow();viewport.close();
            }
        });
        inspector.setText("Left-click a visible block to inspect it. Press G to examine it closely.");
        info.setText("NBT and Blueprint-v1 structure inspection\n\nReal block models/textures are read from local assets. "
                +"Missing assets remain visible as checkerboard placeholders.\n\nChoose High or Ultra and enable Block edges to distinguish individual blocks.");
        displayOptions();rescan();
    }

    private JPanel toolbars(){
        JPanel rows=new JPanel(new GridLayout(2,1));
        JToolBar files=new JToolBar();files.setFloatable(false);
        button(files,"Open NBT / JSON…",this::openFile);button(files,"Reload (R)",this::reload);
        button(files,"Rescan",this::rescan);button(files,"Add assets…",this::addAssets);
        button(files,"Clear added assets",()->{assetPacks.clear();saveSettings();reload();});
        button(files,"Focus all (F)",viewport::focusStructure);button(files,"Focus block (G)",viewport::focusSelected);
        files.add(autoReload);rows.add(files);
        JToolBar view=new JToolBar();view.setFloatable(false);view.add(new JLabel("Quality: "));
        quality.setMaximumSize(new Dimension(125,30));view.add(quality);
        view.add(edges);view.add(textures);view.add(bounds);view.add(wireframe);view.add(air);
        view.add(new JLabel("Y (-1 = all): "));layer.setMaximumSize(new Dimension(75,30));view.add(layer);
        view.add(entities);view.add(blockEntities);view.add(coordinates);rows.add(view);return rows;
    }
    private void restoreSettings(){
        try{
            var saved=ViewerSettings.read(settingsFile);
            quality.setSelectedItem(saved.quality());edges.setSelected(saved.edges());textures.setSelected(saved.textures());
            autoReload.setSelected(saved.autoReload());assetPacks.addAll(saved.assets());
        }catch(IOException|RuntimeException e){quality.setSelectedItem(RenderQuality.HIGH);status.setText("Using default settings: "+e.getMessage());}
    }
    private void saveSettings(){
        try{new ViewerSettings((RenderQuality)quality.getSelectedItem(),edges.isSelected(),textures.isSelected(),
                autoReload.isSelected(),assetPacks).save(settingsFile);}
        catch(IOException e){status.setText("Could not save viewer settings: "+e.getMessage());}
    }
    private void displayOptions(){
        viewport.setOverlays(wireframe.isSelected(),bounds.isSelected());
        viewport.setQuality((RenderQuality)quality.getSelectedItem(),edges.isSelected(),textures.isSelected());
        viewport.setDebugOverlays(entities.isSelected(),blockEntities.isSelected(),coordinates.isSelected());
    }
    private static JTextArea area(){
        JTextArea result=new JTextArea();result.setEditable(false);result.setLineWrap(true);result.setWrapStyleWord(true);
        result.setMargin(new Insets(12,12,12,12));result.setFont(new Font(Font.MONOSPACED,Font.PLAIN,12));return result;
    }
    private static void button(JToolBar bar,String title,Runnable action){
        JButton button=new JButton(title);button.addActionListener(event->action.run());bar.add(button);
    }
    private void openFile(){
        JFileChooser chooser=new JFileChooser(project.toFile());
        chooser.setFileFilter(new FileNameExtensionFilter("Structures (*.nbt, *.json)","nbt","json"));
        if(chooser.showOpenDialog(this)==JFileChooser.APPROVE_OPTION)load(chooser.getSelectedFile().toPath());
    }
    private void addAssets(){
        JFileChooser chooser=new JFileChooser(project.toFile());chooser.setFileSelectionMode(JFileChooser.FILES_AND_DIRECTORIES);
        chooser.setFileFilter(new FileNameExtensionFilter("Resource pack / mod JAR (*.zip, *.jar)","zip","jar"));
        if(chooser.showOpenDialog(this)==JFileChooser.APPROVE_OPTION){
            Path path=chooser.getSelectedFile().toPath().toAbsolutePath().normalize();assetPacks.remove(path);assetPacks.addFirst(path);
            if(assetPacks.size()>32)assetPacks.removeLast();saveSettings();reload();
        }
    }
    private void rescan(){
        loader.submit(()->{
            try{
                List<Path> files=StructureDiscovery.discover(roots);
                SwingUtilities.invokeLater(()->{
                    if(closed)return;
                    var root=new DefaultMutableTreeNode("Structures ("+files.size()+")");
                    for(Path file:files){
                        Path resourceRoot=roots.stream().filter(file::startsWith).findFirst().orElse(project);
                        String group=resourceRoot.equals(project.resolve("src/main/resources"))?"Authored resources"
                                :resourceRoot.equals(project.resolve("src/main/structure_blueprints"))?"JSON blueprints"
                                :resourceRoot.equals(project.resolve("src/generated/resources"))?"Generated resources"
                                :"StructureGen ("+resourceRoot.getParent().getParent().getParent().getFileName()+")";
                        DefaultMutableTreeNode parent=folder(root,group);
                        Path relative=resourceRoot.relativize(file);
                        for(int i=0;i<relative.getNameCount()-1;i++){
                            String part=relative.getName(i).toString();
                            if(!List.of("data","structure","structures").contains(part))parent=folder(parent,part);
                        }
                        parent.add(new DefaultMutableTreeNode(new StructureFile(file)));
                    }
                    browser.setModel(new DefaultTreeModel(root));for(int row=0;row<browser.getRowCount();row++)browser.expandRow(row);
                });
            }catch(Exception e){SwingUtilities.invokeLater(()->{if(!closed)status.setText("Browser scan failed: "+e.getMessage());});}
        });
    }
    private static DefaultMutableTreeNode folder(DefaultMutableTreeNode parent,String name){
        for(int i=0;i<parent.getChildCount();i++){var child=(DefaultMutableTreeNode)parent.getChildAt(i);if(name.equals(child.getUserObject()))return child;}
        var child=new DefaultMutableTreeNode(name);parent.add(child);return child;
    }

    /** Opens NBT or supported JSON; reloading the same source preserves the camera and layer. */
    public void load(Path file){
        Path absolute=file.toAbsolutePath().normalize();
        boolean recenter=loaded==null||!absolute.equals(loaded.source());
        currentFile=absolute;loadError=null;watchCurrent();
        startLoad(absolute,null,recenter,recenter?-1:(int)layer.getValue());
    }
    private void reload(){if(currentFile!=null)load(currentFile);}
    private void rebuild(){if(loaded!=null)startLoad(loaded.source(),loaded,false,(int)layer.getValue());}

    private void startLoad(Path file,StructureData reuse,boolean recenter,int y){
        long request=++generation;if(pending!=null)pending.cancel(true);
        air.setEnabled(false);layer.setEnabled(false);status.setText("Loading models and structure "+file.getFileName()+"…");
        boolean showAir=air.isSelected();List<Path> packs=List.copyOf(assetPacks);
        pending=loader.submit(()->{
            try{
                StructureData data=reuse==null?StructureReaders.read(file):reuse;
                BlockMesh mesh;
                try(var assets=AssetRepository.discover(project,packs)){
                    mesh=BlockMesh.build(data,showAir,y,new BlockModelResolver(assets,BlockStateCatalog.discover(project,build)),assets.sources());
                }
                String summary=StructureDetails.summary(data);
                String report=StructureDetails.diagnostics(mesh)+"\n\nStructure validation\n"+String.join("\n",StructureValidation.inspect(data));
                List<DebugOverlay.Marker> markers=DebugOverlay.prepare(data);
                SwingUtilities.invokeLater(()->{
                    if(closed||request!=generation)return;
                    loaded=data;air.setEnabled(true);layer.setEnabled(true);updatingControls=true;
                    layer.setModel(new SpinnerNumberModel(Math.min(y,Math.max(0,data.size().y()-1)),-1,Math.max(0,data.size().y()-1),1));updatingControls=false;
                    info.setText(summary);info.setCaretPosition(0);diagnostics.setText(report);diagnostics.setCaretPosition(0);
                    metadata.setText(new NbtValue(10,data.metadata()).display());metadata.setCaretPosition(0);
                    inspector.setText("Left-click a block; G focuses closely on the selection.");
                    viewport.setMesh(mesh,recenter);viewport.setMarkers(markers);
                    status.setText(data.name()+" · "+data.blocks().size()+" blocks · "+mesh.resolvedStates()+" modeled states · "
                            +mesh.fallbackStates()+" placeholders · "+mesh.faces().size()+" faces");
                    viewport.requestFocusInWindow();
                });
            }catch(Exception e){
                SwingUtilities.invokeLater(()->{
                    if(closed||request!=generation)return;
                    air.setEnabled(true);layer.setEnabled(true);loadError=e.getClass().getSimpleName()+": "+e.getMessage();
                    status.setText("Could not load "+file.getFileName()+". Previous preview retained.");
                    diagnostics.setText(file+"\n\n"+loadError);tabs.setSelectedIndex(2);
                });
            }
        });
    }
    private void watchCurrent(){
        if(watcher!=null){watcher.close();watcher=null;}
        if(closed||!autoReload.isSelected()||currentFile==null)return;
        Path watched=currentFile;
        try{
            watcher=new StructureFileWatcher(watched,()->SwingUtilities.invokeLater(()->{
                if(!closed&&watched.equals(currentFile)&&autoReload.isSelected())reload();
            }),message->SwingUtilities.invokeLater(()->{if(!closed)status.setText(message);}));
        }catch(IOException e){status.setText("Automatic reload unavailable: "+e.getMessage());}
    }
    private void inspect(int index){
        if(loaded==null)return;inspector.setText(StructureDetails.inspection(loaded,index));inspector.setCaretPosition(0);
        if(index>=0)tabs.setSelectedIndex(1);
    }

    /** Viewport access for focused UI validation. */
    public StructureViewport viewport(){return viewport;}
    /** Most recent successful import. */
    public StructureData loadedStructure(){return loaded;}
    /** Current block inspector text. */
    public String inspectorText(){return inspector.getText();}
    /** Last import failure, retaining the previous preview. */
    public String loadError(){return loadError;}
    /** Changes the real quality control for smoke tests and accessibility automation. */
    public void selectQuality(RenderQuality value){quality.setSelectedItem(value);}
    private record StructureFile(Path path){@Override public String toString(){return path.getFileName().toString();}}
}
