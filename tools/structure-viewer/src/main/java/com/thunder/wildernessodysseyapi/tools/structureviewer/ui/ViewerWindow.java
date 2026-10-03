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
    private final StructureBrowser browser=new StructureBrowser(entry->load(entry.source()),this::rescan);
    private final JTextArea info=area(),inspector=area(),diagnostics=area(),metadata=area();
    private final JTabbedPane tabs=new JTabbedPane();
    private final JLabel status=new JLabel("Choose a structure to preview.");
    private final JCheckBoxMenuItem air=new JCheckBoxMenuItem("Stored air"),bounds=new JCheckBoxMenuItem("Structure bounds"),wireframe=new JCheckBoxMenuItem("Wireframe"),
            edges=new JCheckBoxMenuItem("Block edges",true),textures=new JCheckBoxMenuItem("Textures",true),autoReload=new JCheckBoxMenuItem("Reload automatically",true),
            entities=new JCheckBoxMenuItem("Entities"),blockEntities=new JCheckBoxMenuItem("Block entities"),coordinates=new JCheckBoxMenuItem("Coordinates");
    private final JComboBox<RenderQuality> quality=new JComboBox<>(RenderQuality.values());
    private final JSpinner layer=new LayerSpinner();
    private final JToggleButton darkMode=new JToggleButton("Light",ViewerTheme.themeSwitchIcon());
    private final JPanel content=new JPanel(new CardLayout());
    private final LoadingScreen loadingScreen=new LoadingScreen();
    private JPanel headerPanel,previewToolbar,footer;
    private JButton openPack;
    private final ViewerUpdates updates;
    private boolean scanning,preparing,awaitingFrame;
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
        super("Wilderness Structure Viewer");
        this.project=project.toAbsolutePath().normalize();
        this.build=build;
        updates=new ViewerUpdates(this,build.resolve("tools/structure-viewer/updates"));
        setIconImages(List.of(ViewerTheme.icon(32),ViewerTheme.icon(64),ViewerTheme.icon(256)));
        settingsFile=Path.of(System.getProperty("structureViewer.settings",build.resolve("tools/structure-viewer/settings.properties").toString()));
        viewport=new StructureViewport(this::inspect,this::reload);
        viewport.onFrameReady(()->{
            if(closed||!awaitingFrame||preparing)return;
            awaitingFrame=false;updateLoading();viewport.requestFocusInWindow();
        });
        restoreSettings();
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        setMinimumSize(new Dimension(980,600));
        Dimension screen=Toolkit.getDefaultToolkit().getScreenSize();
        setSize(Math.min(1440,screen.width-60),Math.min(900,screen.height-90));
        setLocationRelativeTo(null);
        setLayout(new BorderLayout());
        add(header(),BorderLayout.NORTH);
        info.setFont(new Font("Segoe UI",Font.PLAIN,13));
        tabs.addTab("Summary",scroll(info));tabs.addTab("Block",scroll(inspector));
        tabs.addTab("Issues",scroll(diagnostics));tabs.addTab("Data",scroll(metadata));
        tabs.setToolTipTextAt(0,"Structure summary");tabs.setToolTipTextAt(1,"Selected block");
        tabs.setToolTipTextAt(2,"Validation and missing assets");tabs.setToolTipTextAt(3,"Original metadata");
        tabs.setTabLayoutPolicy(JTabbedPane.SCROLL_TAB_LAYOUT);
        JPanel inspection=new JPanel(new BorderLayout());
        JLabel inspectorTitle=new JLabel("Inspector");inspectorTitle.setFont(inspectorTitle.getFont().deriveFont(Font.BOLD,14f));
        inspectorTitle.setBorder(BorderFactory.createEmptyBorder(14,14,8,14));inspection.add(inspectorTitle,BorderLayout.NORTH);
        inspection.add(tabs);inspection.setPreferredSize(new Dimension(282,600));inspection.setMinimumSize(new Dimension(250,200));
        JPanel preview=new JPanel(new BorderLayout());preview.add(previewControls(),BorderLayout.NORTH);preview.add(viewport);
        preview.setMinimumSize(new Dimension(430,200));
        JSplitPane detail=new JSplitPane(JSplitPane.HORIZONTAL_SPLIT,preview,inspection);detail.setResizeWeight(1);
        JSplitPane main=new JSplitPane(JSplitPane.HORIZONTAL_SPLIT,browser,detail);main.setResizeWeight(0);
        main.setBorder(BorderFactory.createEmptyBorder());detail.setBorder(BorderFactory.createEmptyBorder());
        main.setContinuousLayout(true);detail.setContinuousLayout(true);
        content.add(main,"preview");content.add(loadingScreen,"loading");add(content,BorderLayout.CENTER);
        footer=new JPanel(new BorderLayout(12,0));
        footer.setBackground(ViewerTheme.BACKGROUND);
        footer.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createMatteBorder(1,0,0,0,ViewerTheme.BORDER),
                BorderFactory.createEmptyBorder(4,14,4,10)));
        status.setForeground(ViewerTheme.MUTED);status.setFont(status.getFont().deriveFont(12f));
        status.setToolTipText(project.toString());footer.add(status);
        JButton help=button("Controls",this::showControls);help.putClientProperty("JButton.buttonType","borderless");
        help.setToolTipText("Keyboard and mouse controls · Version "+System.getProperty("structureViewer.version","development"));
        help.setMargin(new Insets(3,8,3,8));
        JPanel footerActions=new JPanel(new FlowLayout(FlowLayout.RIGHT,8,0));footerActions.setOpaque(false);
        footerActions.add(updates.button());footerActions.add(help);footer.add(footerActions,BorderLayout.EAST);
        add(footer,BorderLayout.SOUTH);
        air.addActionListener(event->rebuild());
        layer.addChangeListener(event->{if(!updatingControls)rebuild();});
        for(JCheckBoxMenuItem box:List.of(bounds,wireframe,edges,textures,entities,blockEntities,coordinates))
            box.addActionListener(event->{displayOptions();saveSettings();});
        quality.addActionListener(event->{displayOptions();saveSettings();});
        autoReload.addActionListener(event->{watchCurrent();saveSettings();});
        darkMode.addActionListener(event->{
            ViewerTheme.install(darkMode.isSelected());com.formdev.flatlaf.FlatLaf.updateUI();applyTheme();saveSettings();
        });
        addWindowListener(new WindowAdapter(){
            @Override public void windowClosing(WindowEvent event){updates.close();}
            @Override public void windowClosed(WindowEvent event){
                closed=true;generation++;loadingScreen.stop();if(watcher!=null)watcher.close();
                updates.close();
                if(pending!=null)pending.cancel(true);if(scan!=null)scan.cancel(true);browser.close();loader.shutdownNow();viewport.close();
            }
        });
        inspector.setText("Left-click a visible block to inspect it. Press G to examine it closely.");
        info.setText("No structure selected\n\nOpen a modpack, then choose a template from the library. Its dimensions and contents will appear here.");
        SwingUtilities.invokeLater(()->{
            if(closed)return;
            main.setDividerLocation(250);detail.setDividerLocation(Math.max(430,detail.getWidth()-287));
        });
        applyTheme();displayOptions();rescan();
        if(System.getProperty("structureViewer.projectDir")==null&&!Boolean.getBoolean("structureViewer.disableUpdateCheck"))
            SwingUtilities.invokeLater(()->updates.check(false));
    }

    private JPanel header(){
        JPanel header=new JPanel(new BorderLayout(12,0));headerPanel=header;header.setName("app-header");
        header.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createMatteBorder(0,0,1,0,ViewerTheme.BORDER),
                BorderFactory.createEmptyBorder(12,16,12,16)));
        JLabel title=new JLabel("Structure Viewer",new ImageIcon(ViewerTheme.icon(28)),SwingConstants.LEFT);
        title.setIconTextGap(10);title.setFont(title.getFont().deriveFont(Font.BOLD,17f));header.add(title);
        JPanel actions=new JPanel(new FlowLayout(FlowLayout.RIGHT,6,0));
        openPack=button("Open modpack…",this::chooseModpack);openPack.setName("open-modpack");
        openPack.setIcon(ViewerTheme.folderIcon());openPack.setIconTextGap(8);openPack.setFont(openPack.getFont().deriveFont(Font.BOLD));
        openPack.setToolTipText("Choose a modpack instance, including CurseForge's minecraft/Instances folder");
        JButton openStructure=button("Open structure…",this::openFile);openStructure.setName("open-structure");
        JButton reload=button("Reload",this::reload);reload.setToolTipText("Reload the selected structure (R)");
        JPopupMenu assets=new JPopupMenu();menuItem(assets,"Add local assets…",this::addAssets);
        menuItem(assets,"Clear added assets",()->{assetPacks.clear();saveSettings();reload();});
        JButton assetButton=popupButton("Assets",assets);
        assetButton.setToolTipText("Mod textures and block models load automatically from this modpack. Add resource packs or missing client assets here.");
        actions.add(openPack);actions.add(openStructure);actions.add(reload);actions.add(assetButton);
        darkMode.setName("theme-toggle");darkMode.setIconTextGap(8);darkMode.setMargin(new Insets(6,8,6,8));
        darkMode.setPreferredSize(new Dimension(96,32));
        darkMode.setToolTipText("Switch between light and dark appearance; your choice is saved");
        darkMode.getAccessibleContext().setAccessibleName("Dark appearance");actions.add(darkMode);
        header.add(actions,BorderLayout.EAST);return header;
    }

    private JPanel previewControls(){
        JPanel controls=new JPanel(new FlowLayout(FlowLayout.LEFT,7,8));previewToolbar=controls;controls.setName("preview-toolbar");
        controls.setBackground(ViewerTheme.BACKGROUND);
        controls.setBorder(BorderFactory.createMatteBorder(0,0,1,0,ViewerTheme.BORDER));
        JLabel qualityLabel=new JLabel("Quality");qualityLabel.setLabelFor(quality);controls.add(qualityLabel);
        quality.setPreferredSize(new Dimension(100,30));quality.setName("render-quality");
        quality.setToolTipText("Rendering detail: Fast, Balanced, High, or Ultra");controls.add(quality);
        JLabel layerLabel=new JLabel("Layer");layerLabel.setLabelFor(layer);controls.add(layerLabel);
        layer.setPreferredSize(new Dimension(64,30));layer.setName("y-layer");controls.add(layer);
        JPopupMenu options=new JPopupMenu();
        menuItem(options,"Fit structure (F)",viewport::focusStructure);
        menuItem(options,"Focus selected block (G)",viewport::focusSelected);options.addSeparator();
        options.add(textures);options.add(edges);options.add(bounds);options.add(wireframe);options.add(air);
        options.addSeparator();options.add(entities);options.add(blockEntities);options.add(coordinates);
        options.addSeparator();options.add(autoReload);
        JButton view=popupButton("View options",options);view.setName("view-options");controls.add(view);
        return controls;
    }

    private void showControls(){
        JOptionPane.showMessageDialog(this,"Right-drag    Rotate or look around\nMouse wheel    Zoom or change flying speed\n\n"
                +"F1    Orbit camera\nF2    Free camera\nW / A / S / D    Move in free camera\nSpace / Shift    Move up / down\nCtrl    Precision movement\n\n"
                +"Left-click    Inspect a block\nF    Fit structure\nG    Focus selected block\nR    Reload structure", "Viewer controls · "+System.getProperty("structureViewer.version","development"),JOptionPane.INFORMATION_MESSAGE);
    }
    private void restoreSettings(){
        try{
            var saved=ViewerSettings.read(settingsFile);
            quality.setSelectedItem(saved.quality());edges.setSelected(saved.edges());textures.setSelected(saved.textures());
            autoReload.setSelected(saved.autoReload());assetPacks.addAll(saved.assets());
            darkMode.setSelected(saved.darkMode());
        }catch(IOException|RuntimeException e){quality.setSelectedItem(RenderQuality.HIGH);status.setText("Using default settings: "+e.getMessage());}
    }
    private void saveSettings(){
        try{new ViewerSettings((RenderQuality)quality.getSelectedItem(),edges.isSelected(),textures.isSelected(),
                autoReload.isSelected(),assetPacks,project,darkMode.isSelected()).save(settingsFile);}
        catch(IOException e){status.setText("Could not save viewer settings: "+e.getMessage());}
    }
    private void displayOptions(){
        viewport.setOverlays(wireframe.isSelected(),bounds.isSelected());
        viewport.setQuality((RenderQuality)quality.getSelectedItem(),edges.isSelected(),textures.isSelected());
        viewport.setDebugOverlays(entities.isSelected(),blockEntities.isSelected(),coordinates.isSelected());
    }

    private void applyTheme(){
        darkMode.setText(darkMode.isSelected()?"Dark":"Light");
        for(JTextArea text:List.of(info,inspector,diagnostics,metadata)){text.setBackground(ViewerTheme.PANEL);text.setForeground(ViewerTheme.TEXT);}
        status.setForeground(ViewerTheme.MUTED);browser.applyTheme();loadingScreen.applyTheme();viewport.applyTheme();
        headerPanel.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createMatteBorder(0,0,1,0,ViewerTheme.BORDER),BorderFactory.createEmptyBorder(12,16,12,16)));
        previewToolbar.setBackground(ViewerTheme.BACKGROUND);previewToolbar.setBorder(BorderFactory.createMatteBorder(0,0,1,0,ViewerTheme.BORDER));
        footer.setBackground(ViewerTheme.BACKGROUND);footer.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createMatteBorder(1,0,0,0,ViewerTheme.BORDER),BorderFactory.createEmptyBorder(4,14,4,10)));
        String primary=String.format("#%06x",ViewerTheme.PRIMARY.getRGB()&0xffffff),foreground=String.format("#%06x",ViewerTheme.PRIMARY_TEXT.getRGB()&0xffffff);
        openPack.putClientProperty("FlatLaf.style","background: "+primary+"; foreground: "+foreground+"; borderColor: "+primary+"; hoverBackground: "+primary+"; pressedBackground: "+primary);
        repaint();
    }

    private void updateLoading(){
        boolean busy=preparing||awaitingFrame||(scanning&&loaded==null);
        if(awaitingFrame)loadingScreen.showProgress("Loading structure","Drawing the preview…");
        ((CardLayout)content.getLayout()).show(content,busy?"loading":"preview");
        if(!busy)loadingScreen.stop();
    }

    private void loadProgress(long request,String message){
        SwingUtilities.invokeLater(()->{if(!closed&&request==generation&&preparing)loadingScreen.showProgress("Loading structure",message);});
    }
    private static JTextArea area(){
        JTextArea result=new JTextArea();result.setEditable(false);result.setLineWrap(true);result.setWrapStyleWord(true);
        result.setMargin(new Insets(16,14,16,14));result.setFont(new Font("Consolas",Font.PLAIN,12));
        result.setBackground(ViewerTheme.PANEL);result.setForeground(ViewerTheme.TEXT);return result;
    }
    private static JScrollPane scroll(JTextArea text){
        JScrollPane scroll=new JScrollPane(text);scroll.setBorder(BorderFactory.createEmptyBorder());return scroll;
    }
    private static JButton button(String title,Runnable action){
        JButton button=new JButton(title);button.addActionListener(event->action.run());return button;
    }
    private static void menuItem(JPopupMenu menu,String title,Runnable action){
        JMenuItem item=new JMenuItem(title);item.addActionListener(event->action.run());menu.add(item);
    }
    private static JButton popupButton(String title,JPopupMenu menu){
        JButton button=new JButton(title,ViewerTheme.dropdownIcon());
        button.setHorizontalTextPosition(SwingConstants.LEFT);button.setIconTextGap(8);
        button.addActionListener(event->{SwingUtilities.updateComponentTreeUI(menu);menu.show(button,0,button.getHeight());});return button;
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
        Path directory=ModpackLocation.chooserDirectory(project,Path.of(System.getProperty("user.home")));
        JFileChooser chooser=new JFileChooser(directory.toFile());chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        chooser.setDialogTitle("Choose a modpack instance (CurseForge: minecraft/Instances)");
        if(chooser.showOpenDialog(this)==JFileChooser.APPROVE_OPTION)openModpack(chooser.getSelectedFile().toPath());
    }

    /** Switches the read-only source root while keeping display preferences and cancelling stale imports. */
    public void openModpack(Path folder){
        project=ModpackLocation.normalize(folder);generation++;
        if(pending!=null)pending.cancel(true);loader.purge();
        if(watcher!=null){watcher.close();watcher=null;}
        currentSource=null;loadedSource=null;loaded=null;prepared=null;loadError=null;viewport.clear();browser.clear();
        preparing=false;awaitingFrame=false;air.setEnabled(true);layer.setEnabled(true);
        diagnostics.setText("");metadata.setText("");
        info.setText("Modpack: "+project+"\n\nSelect a template from a mod JAR in the library.");
        inspector.setText("Select a structure, then click a block to inspect it.");
        status.setToolTipText(project.toString());
        saveSettings();rescan();
    }

    private void rescan(){
        long request=++scanGeneration;Path root=project;browser.scanning();
        scanning=true;if(!preparing&&!awaitingFrame)loadingScreen.showProgress("Loading your library","Scanning mod JARs in "+(root.getFileName()==null?root:root.getFileName())+"…");updateLoading();
        if(scan!=null)scan.cancel(true);loader.purge();
        scan=loader.submit(()->{
            try{
                var catalog=StructureCatalog.scan(root,build);
                SwingUtilities.invokeLater(()->{
                    if(closed||request!=scanGeneration)return;
                    scanning=false;updateLoading();
                    browser.setCatalog(catalog);
                    status.setText(catalog.entries().size()+" structures in "+catalog.archives()+" scanned archives · "+root);
                    if(!catalog.diagnostics().isEmpty())diagnostics.setText(String.join("\n",catalog.diagnostics()));
                });
            }catch(Exception error){
                SwingUtilities.invokeLater(()->{if(!closed&&request==scanGeneration){scanning=false;updateLoading();status.setText("Could not scan this folder: "+error.getMessage());}});
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
        preparing=true;awaitingFrame=false;loadingScreen.showProgress("Loading structure","Reading "+source.filename()+"…");updateLoading();
        boolean showAir=air.isSelected();List<Path> packs=List.copyOf(assetPacks);Path root=project;
        pending=loader.submit(()->{
            try{
                PreparedPreview snapshot=reuse;
                if(snapshot==null){
                    StructureData data=source.read();
                    loadProgress(request,"Resolving block models and textures…");
                    ResolvedModels models;
                    try(var assets=AssetRepository.discover(root,packs)){
                        models=ResolvedModels.capture(data,new BlockModelResolver(assets,BlockStateCatalog.discover(root,build)),assets.sources());
                    }
                    snapshot=new PreparedPreview(data,models,StructureDetails.summary(data)+(source.archived()?"\n\nArchive entry\n"+source.entry():""),
                            String.join("\n",StructureValidation.inspect(data)),DebugOverlay.prepare(data));
                }
                PreparedPreview result=snapshot;StructureData data=result.data();
                // An export may shrink the structure while a higher layer is selected.
                int y=Math.min(requestedLayer,Math.max(0,data.size().y()-1));
                loadProgress(request,"Preparing visible block faces…");
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
                    preparing=false;awaitingFrame=true;updateLoading();
                    status.setText(data.name()+" · "+String.format(java.util.Locale.ROOT,"%,d",data.blocks().size())+" stored blocks · "
                            +mesh.resolvedStates()+" modeled states · "+mesh.fallbackStates()+" placeholders");
                    if(recenter)tabs.setSelectedIndex(0);
                });
            }catch(Exception error){
                SwingUtilities.invokeLater(()->{
                    if(closed||request!=generation)return;
                    preparing=false;awaitingFrame=false;updateLoading();
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
