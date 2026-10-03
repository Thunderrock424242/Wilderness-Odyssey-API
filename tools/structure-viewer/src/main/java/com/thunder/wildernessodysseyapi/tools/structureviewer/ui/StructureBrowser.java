package com.thunder.wildernessodysseyapi.tools.structureviewer.ui;

import com.thunder.wildernessodysseyapi.tools.structureviewer.io.StructureCatalog;
import javax.swing.*;
import javax.swing.event.*;
import javax.swing.tree.*;
import java.awt.*;
import java.util.*;
import java.util.List;
import java.util.function.Consumer;

/** Searchable source tree; filtering never reopens JARs or reparses structures. */
final class StructureBrowser extends JPanel {
    private final JTree tree = new JTree();
    private final JTextField search = new JTextField();
    private final JComboBox<LibraryView> view = new JComboBox<>(LibraryView.values());
    private final JLabel count = new JLabel("Choose a modpack or open a structure");
    private final javax.swing.Timer debounce;
    private final JPanel content = new JPanel(new CardLayout());
    private final JLabel emptyTitle = new JLabel("No structures found", SwingConstants.CENTER);
    private final JLabel emptyHint = new JLabel("Open a modpack to browse its templates.", SwingConstants.CENTER);
    private List<StructureCatalog.Entry> entries = List.of();
    private final Set<String> expandedGroups = new HashSet<>();
    private boolean updating;

    StructureBrowser(Consumer<StructureCatalog.Entry> open, Runnable rescan,Runnable viewChanged) {
        super(new BorderLayout(0,10));
        setBorder(BorderFactory.createEmptyBorder(14,14,10,10));
        setPreferredSize(new Dimension(250,600));setMinimumSize(new Dimension(210,200));
        JLabel title = new JLabel("Library");title.setFont(title.getFont().deriveFont(Font.BOLD,14));
        JButton refresh = new JButton("Refresh");
        refresh.putClientProperty("JButton.buttonType", "borderless");
        refresh.setMargin(new Insets(4,6,4,6));refresh.setToolTipText("Rescan this modpack for structure templates");
        refresh.addActionListener(event -> rescan.run());
        JPanel titleRow = new JPanel(new BorderLayout());titleRow.add(title);titleRow.add(refresh,BorderLayout.EAST);
        search.setToolTipText("Search by mod JAR, namespace, or structure name");
        search.setName("library-search");
        search.putClientProperty("JTextField.placeholderText", "Search structures…");
        search.putClientProperty("JTextField.showClearButton", true);
        search.getAccessibleContext().setAccessibleName("Search structures");
        search.setPreferredSize(new Dimension(180,32));
        view.setName("library-filter");view.setPreferredSize(new Dimension(180,32));
        view.getAccessibleContext().setAccessibleName("Library view");
        view.setToolTipText("Structures hides clear decorations by name or folder. All templates shows every indexed entry.");
        JPanel filters=new JPanel(new BorderLayout(0,8));filters.add(search,BorderLayout.NORTH);filters.add(view);
        JPanel header = new JPanel(new BorderLayout(0,12));header.add(titleRow,BorderLayout.NORTH);header.add(filters);
        count.setForeground(ViewerTheme.MUTED);count.setFont(count.getFont().deriveFont(12f));
        count.setName("library-count");
        add(header,BorderLayout.NORTH);add(count,BorderLayout.SOUTH);
        tree.setRootVisible(false);tree.setShowsRootHandles(true);tree.setRowHeight(28);
        tree.setExpandsSelectedPaths(false);
        tree.addTreeExpansionListener(new TreeExpansionListener() {
            public void treeExpanded(TreeExpansionEvent event){remember(event,true);}
            public void treeCollapsed(TreeExpansionEvent event){remember(event,false);}
            private void remember(TreeExpansionEvent event,boolean expanded) {
                if(updating||event.getPath().getPathCount()!=2)return;
                String group=((DefaultMutableTreeNode)event.getPath().getLastPathComponent()).getUserObject().toString();
                if(expanded)expandedGroups.add(group);else expandedGroups.remove(group);
            }
        });
        tree.setCellRenderer(new DefaultTreeCellRenderer() {
            @Override public Component getTreeCellRendererComponent(JTree t,Object value,boolean selected,boolean expanded,boolean leaf,int row,boolean focus) {
                super.getTreeCellRendererComponent(t,value,selected,expanded,leaf,row,focus);
                Object user=((DefaultMutableTreeNode)value).getUserObject();
                if(user instanceof StructureCatalog.Entry entry) {
                    setText(entry.toString()+(entry.feature()?" · Feature":""));
                    setToolTipText(entry.source().description()+(entry.feature()?" · Decorative name or folder":""));
                }else setToolTipText(user.toString());return this;
            }
        });
        ToolTipManager.sharedInstance().registerComponent(tree);
        tree.addTreeSelectionListener(event -> {
            if(updating)return;
            if(tree.getLastSelectedPathComponent() instanceof DefaultMutableTreeNode node
                    && node.getUserObject() instanceof StructureCatalog.Entry entry)open.accept(entry);
        });
        JScrollPane scroll = new JScrollPane(tree);scroll.setBorder(BorderFactory.createEmptyBorder());content.add(scroll,"tree");
        JPanel empty = new JPanel();empty.setLayout(new BoxLayout(empty,BoxLayout.Y_AXIS));
        emptyTitle.setAlignmentX(.5f);emptyHint.setAlignmentX(.5f);emptyHint.setForeground(ViewerTheme.MUTED);
        emptyHint.setFont(emptyHint.getFont().deriveFont(12f));
        empty.add(Box.createVerticalStrut(30));empty.add(emptyTitle);empty.add(Box.createVerticalStrut(8));empty.add(emptyHint);
        content.add(empty,"empty");add(content);
        debounce = new javax.swing.Timer(180,event -> filter());debounce.setRepeats(false);
        search.getDocument().addDocumentListener(new DocumentListener() {
            public void insertUpdate(DocumentEvent e){debounce.restart();}
            public void removeUpdate(DocumentEvent e){debounce.restart();}
            public void changedUpdate(DocumentEvent e){debounce.restart();}
        });
        view.addActionListener(event->{if(!updating){filter();viewChanged.run();}});
        filter();
    }
    LibraryView view(){return (LibraryView)view.getSelectedItem();}
    void setView(LibraryView value){updating=true;view.setSelectedItem(value);updating=false;filter();}
    void setCatalog(StructureCatalog.Result catalog) { expandedGroups.clear();entries=catalog.entries();filter(); }
    void clear() { expandedGroups.clear();entries=List.of();filter(); }
    void scanning() { count.setText("Scanning structures…"); }
    void applyTheme() { count.setForeground(ViewerTheme.MUTED);emptyHint.setForeground(ViewerTheme.MUTED); }
    void close() { debounce.stop();ToolTipManager.sharedInstance().unregisterComponent(tree); }
    private void filter() {
        Object selected=tree.getLastSelectedPathComponent();
        var previous=selected instanceof DefaultMutableTreeNode node?node.getUserObject():null;
        var root=new DefaultMutableTreeNode("Structures");Map<String,DefaultMutableTreeNode> groups=new LinkedHashMap<>();
        String query=search.getText().strip().toLowerCase(Locale.ROOT);int shown=0,hiddenFeatures=0;TreePath restore=null;
        LibraryView selectedView=view();
        for(var entry:entries){
            if(!entry.searchText().contains(query))continue;
            if(selectedView==LibraryView.STRUCTURES&&entry.feature()){hiddenFeatures++;continue;}
            if(selectedView==LibraryView.FEATURES&&!entry.feature())continue;
            var group=groups.computeIfAbsent(entry.group(),name->{var node=new DefaultMutableTreeNode(name);root.add(node);return node;});
            var leaf=new DefaultMutableTreeNode(entry);group.add(leaf);shown++;
            if(entry.equals(previous))restore=new TreePath(leaf.getPath());
        }
        updating=true;tree.setModel(new DefaultTreeModel(root));
        // New scans start collapsed; filtering retains only groups the user opened.
        for(var group:groups.values())if(expandedGroups.contains(group.getUserObject().toString()))tree.expandPath(new TreePath(group.getPath()));
        if(restore!=null)tree.setSelectionPath(restore);
        updating=false;
        count.setText("<html>"+shown+" templates  ·  "+groups.size()+" sources"+(hiddenFeatures>0?"<br>"+hiddenFeatures+" features hidden":"")+"</html>");
        emptyTitle.setText(query.isEmpty()?(selectedView==LibraryView.FEATURES?"No features found":"No structures found"):"No matching templates");
        emptyHint.setText(hiddenFeatures>0?"<html><center>Choose All templates<br>to see features.</center></html>":query.isEmpty()?"Choose a modpack with stored templates.":"Try a different name or clear the search.");
        ((CardLayout)content.getLayout()).show(content,shown==0?"empty":"tree");
    }
}
