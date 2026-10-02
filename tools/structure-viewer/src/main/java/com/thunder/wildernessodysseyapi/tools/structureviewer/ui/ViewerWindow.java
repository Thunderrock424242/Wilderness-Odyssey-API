package com.thunder.wildernessodysseyapi.tools.structureviewer.ui;

import com.thunder.wildernessodysseyapi.tools.structureviewer.assets.*;
import com.thunder.wildernessodysseyapi.tools.structureviewer.io.*;
import com.thunder.wildernessodysseyapi.tools.structureviewer.model.*;
import com.thunder.wildernessodysseyapi.tools.structureviewer.render.*;
import com.thunder.wildernessodysseyapi.tools.structureviewer.validation.StructureValidation;
import javax.swing.*;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.*;
import java.awt.event.*;
import java.io.IOException;
import java.nio.file.*;
import java.util.List;
import java.util.ArrayList;
import java.util.concurrent.*;

/** Desktop structure studio shell. Loading, assets, watching, and rendering have separate lifecycles. */
public final class ViewerWindow extends JFrame {
    private Path project;
    private final Path settingsFile,build;
    private long scanGeneration;
    private Future<?> scan;
    private final List<Path> assetPacks=new ArrayList<>();
    private final StructureBrowser browser=new StructureBrowser(entry->load(entry.source()));
    private final JTextArea info=area(),inspector=area(),diagnostics=area(),metadata=area();
    private final JTabbedPane tabs=new JTabbedPane();
    private final JLabel status=new JLabel("Choose a structure to preview.");
    private final JCheckBox air=new JCheckBox("Stored air"),bounds=new JCheckBox("Bounds"),wireframe=new JCheckBox("Wireframe"),
            edges=new JCheckBox("Block edges",true),textures=new JCheckBox("Textures",true),autoReload=new JCheckBox("Auto reload",true),
            entities=new JCheckBox("Entities"),blockEntities=new JCheckBox("Block entities"),coordinates=new JCheckBox("Coordinates");
    private final JComboBox<RenderQuality> quality=new JComboBox<>(RenderQuality.values());
    private final JSpinner layer=new JSpinner(new SpinnerNumberModel(-1,-1,0,1));
    private final StructureViewport viewport;
    private final ThreadPoolExecutor loader=new ThreadPoolExecutor(1,1,0,TimeUnit.MILLISECONDS,new ArrayBlockingQueue<>(4),r->{
        Thread t=new Thread(r,"structure-viewer-loader");t.setDaemon(true);return t;
    });
    private Future<?> pending;
    private StructureFileWatcher watcher;
    private long generation;
    private boolean updatingControls,closed;
    private StructureData loaded;
    private StructureSource currentSource,loadedSource;
    private PreparedPreview prepared;
    private record PreparedPreview(StructureData data,ResolvedModels models,String summary,String validation,List<DebugOverlay.Marker> markers) {}
    private String loadError;

    /** Builds the application on the event thread and restores explicit user preferences. */
    public ViewerWindow(Path project,Path build){
        super("Wilderness Odyssey · Structure Viewer");
        this.project=project.toAbsolutePath().normalize();
        this.build=build;
        setIconImages(List.of(ViewerTheme.icon(32),ViewerTheme.icon(64),ViewerTheme.icon(256)));
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
        tabs.addTab("Structure",new JScrollPane(info));tabs.addTab("Block",new JScrollPane(inspector));
        tabs.addTab("Diagnostics",new JScrollPane(diagnostics));tabs.addTab("Metadata",new JScrollPane(metadata));
        tabs.setPreferredSize(new Dimension(340,600));viewport.setMinimumSize(new Dimension(300,300));
        JSplitPane detail=new JSplitPane(JSplitPane.HORIZONTAL_SPLIT,viewport,tabs);detail.setResizeWeight(1);
        JSplitPane main=new JSplitPane(JSplitPane.HORIZONTAL_SPLIT,browser,detail);main.setResizeWeight(0);
        main.setBorder(BorderFactory.createEmptyBorder());detail.setBorder(BorderFactory.createEmptyBorder());
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
                if(pending!=null)pending.cancel(true);if(scan!=null)scan.cancel(true);browser.close();loader.shutdownNow();viewport.close();
            }
        });
        inspector.setText("Left-click a visible block to inspect it. Press G to examine it closely.");
        info.setText("NBT and Blueprint-v1 structure inspection\n\nReal block models/textures are read from local assets. "
                +"Missing assets remain visible as checkerboard placeholders.\n\nChoose High or Ultra and enable Block edges to distinguish individual blocks.");
        displayOptions();rescan();
    }

    private JPanel toolbars(){
        JPanel rows=new JPanel(new BorderLayout(0,8));rows.setBorder(BorderFactory.createEmptyBorder(12,14,8,14));
        JPanel heading=new JPanel(new BorderLayout());
        JLabel title=new JLabel("WILDERNESS ODYSSEY  /  Structure Viewer");
        title.setFont(title.getFont().deriveFont(Font.BOLD,18f));title.setForeground(ViewerTheme.TEXT);
        heading.add(title,BorderLayout.WEST);JLabel hint=new JLabel("Explore • Inspect • Build");hint.setForeground(ViewerTheme.MUTED);
        heading.add(hint,BorderLayout.EAST);rows.add(heading,BorderLayout.NORTH);
        JPanel controls=new JPanel(new GridLayout(2,1,0,5));
        JToolBar files=new JToolBar();files.setFloatable(false);
        button(files,"Open modpack…",this::chooseModpack);button(files,"Open structure…",this::openFile);button(files,"Reload (R)",this::reload);
        button(files,"Rescan",this::rescan);button(files,"Add assets…",this::addAssets);
        button(files,"Clear added assets",()->{assetPacks.clear();saveSettings();reload();});
        button(files,"Focus all (F)",viewport::focusStructure);button(files,"Focus block (G)",viewport::focusSelected);
        files.add(autoReload);controls.add(files);
        JToolBar view=new JToolBar();view.setFloatable(false);view.add(new JLabel("Quality: "));
        quality.setMaximumSize(new Dimension(125,30));view.add(quality);
        view.add(edges);view.add(textures);view.add(bounds);view.add(wireframe);view.add(air);
        view.add(new JLabel("Y (-1 = all): "));layer.setMaximumSize(new Dimension(75,30));view.add(layer);
        view.add(entities);view.add(blockEntities);view.add(coordinates);controls.add(view);
        JScrollPane horizontal=new JScrollPane(controls,JScrollPane.VERTICAL_SCROLLBAR_NEVER,JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
        horizontal.setBorder(BorderFactory.createEmptyBorder());horizontal.setPreferredSize(new Dimension(900,controls.getPreferredSize().height+15));
        rows.add(horizontal,BorderLayout.CENTER);return rows;
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
                autoReload.isSelected(),assetPacks,project).save(settingsFile);}
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
        JButton button=new JButton(title);button.setFocusable(false);button.setMargin(new Insets(6,10,6,10));button.addActionListener(event->action.run());bar.add(button);
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
    private void chooseModpack(){
        JFileChooser chooser=new JFileChooser(project.toFile());chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        chooser.setDialogTitle("Choose the modpack folder or its mods folder");
        if(chooser.showOpenDialog(this)==JFileChooser.APPROVE_OPTION)openModpack(chooser.getSelectedFile().toPath());
    }

    /** Switches the read-only source root while keeping display preferences and cancelling stale imports. */
    public void openModpack(Path folder){
        project=ModpackLocation.normalize(folder);generation++;
        if(pending!=null)pending.cancel(true);loader.purge();
        if(watcher!=null){watcher.close();watcher=null;}
        currentSource=null;loadedSource=null;loaded=null;prepared=null;loadError=null;viewport.clear();browser.clear();
        diagnostics.setText("");metadata.setText("");
        info.setText("Modpack: "+project+"\n\nSelect a template from a mod JAR in the library.");
        inspector.setText("Select a structure, then click a block to inspect it.");
        saveSettings();rescan();
    }

    private void rescan(){
        long request=++scanGeneration;Path root=project;browser.scanning();
        if(scan!=null)scan.cancel(true);loader.purge();
        scan=loader.submit(()->{
            try{
                var catalog=StructureCatalog.scan(root,build);
                SwingUtilities.invokeLater(()->{
                    if(closed||request!=scanGeneration)return;
                    browser.setCatalog(catalog);
                    status.setText(catalog.entries().size()+" structures in "+catalog.archives()+" scanned archives · "+root);
                    if(!catalog.diagnostics().isEmpty())diagnostics.setText(String.join("\n",catalog.diagnostics()));
                });
            }catch(Exception error){
                SwingUtilities.invokeLater(()->{if(!closed&&request==scanGeneration)status.setText("Could not scan this folder: "+error.getMessage());});
            }
        });
    }

    /** Opens a loose structure without changing its contents. */
    public void load(Path file){load(StructureSource.file(file));}

    /** Distinguishes entries inside the same JAR; only reloading the exact source preserves its camera. */
    public void load(StructureSource source){
        boolean recenter=loaded==null||!source.equals(loadedSource);
        currentSource=source;loadError=null;watchCurrent();
        startLoad(source,null,recenter,recenter?-1:(int)layer.getValue());
    }
    private void reload(){if(currentSource!=null)load(currentSource);}
    private void rebuild(){if(prepared!=null)startLoad(loadedSource,prepared,false,(int)layer.getValue());}

    private void startLoad(StructureSource source,PreparedPreview reuse,boolean recenter,int requestedLayer){
        long request=++generation;if(pending!=null)pending.cancel(true);loader.purge();
        air.setEnabled(false);layer.setEnabled(false);status.setText("Preparing "+source.filename()+"…");
        boolean showAir=air.isSelected();List<Path> packs=List.copyOf(assetPacks);Path root=project;
        pending=loader.submit(()->{
            try{
                PreparedPreview snapshot=reuse;
                if(snapshot==null){
                    StructureData data=source.read();
                    ResolvedModels models;
                    try(var assets=AssetRepository.discover(root,packs)){
                        models=ResolvedModels.capture(data,new BlockModelResolver(assets,BlockStateCatalog.discover(root,build)),assets.sources());
                    }
                    snapshot=new PreparedPreview(data,models,(source.archived()?"Archive entry: "+source.entry()+"\n\n":"")+StructureDetails.summary(data),
                            String.join("\n",StructureValidation.inspect(data)),DebugOverlay.prepare(data));
                }
                PreparedPreview result=snapshot;StructureData data=result.data();
                // An export may shrink the structure while a higher layer is selected.
                int y=Math.min(requestedLayer,Math.max(0,data.size().y()-1));
                BlockMesh mesh=BlockMesh.build(data,showAir,y,result.models());
                String report=StructureDetails.diagnostics(mesh)+"\n\nStructure validation\n"+result.validation();
                SwingUtilities.invokeLater(()->{
                    if(closed||request!=generation)return;
                    prepared=result;loaded=data;loadedSource=source;loadError=null;
                    air.setEnabled(true);layer.setEnabled(true);updatingControls=true;
                    layer.setModel(new SpinnerNumberModel(y,-1,Math.max(0,data.size().y()-1),1));updatingControls=false;
                    info.setText(result.summary());info.setCaretPosition(0);diagnostics.setText(report);diagnostics.setCaretPosition(0);
                    metadata.setText(new NbtValue(10,data.metadata()).display());metadata.setCaretPosition(0);
                    inspector.setText("Left-click a block; G focuses closely on the selection.");
                    viewport.setMesh(mesh,recenter);viewport.setMarkers(result.markers());
                    status.setText(data.name()+" · "+String.format(java.util.Locale.ROOT,"%,d",data.blocks().size())+" stored blocks · "
                            +mesh.resolvedStates()+" modeled states · "+mesh.fallbackStates()+" placeholders");
                    if(recenter)tabs.setSelectedIndex(0);
                    viewport.requestFocusInWindow();
                });
            }catch(Exception error){
                SwingUtilities.invokeLater(()->{
                    if(closed||request!=generation)return;
                    air.setEnabled(true);layer.setEnabled(true);loadError=error.getClass().getSimpleName()+": "+error.getMessage();
                    status.setText("Could not load "+source.filename()+". Previous preview retained.");
                    diagnostics.setText(source.description()+"\n\n"+loadError);tabs.setSelectedIndex(2);
                });
            }
        });
    }
    private void watchCurrent(){
        if(watcher!=null){watcher.close();watcher=null;}
        if(closed||!autoReload.isSelected()||currentSource==null)return;
        StructureSource watched=currentSource;
        try{
            watcher=new StructureFileWatcher(watched.container(),()->SwingUtilities.invokeLater(()->{
                if(!closed&&watched.equals(currentSource)&&autoReload.isSelected())reload();
            }),message->SwingUtilities.invokeLater(()->{if(!closed)status.setText(message);}));
        }catch(IOException error){status.setText("Automatic reload unavailable: "+error.getMessage());}
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
    /** Selects a layer through the actual control. */
    public void selectLayer(int y){layer.setValue(y);}
    /** Exact loaded archive entry for inspection and reload diagnostics. */
    public StructureSource loadedSource(){return loadedSource;}
}
