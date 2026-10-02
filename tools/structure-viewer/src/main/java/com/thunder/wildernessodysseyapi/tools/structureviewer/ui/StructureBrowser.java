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
    private final JLabel count = new JLabel("Choose a modpack or open a structure");
    private final javax.swing.Timer debounce;
    private List<StructureCatalog.Entry> entries = List.of();
    private boolean updating;

    StructureBrowser(Consumer<StructureCatalog.Entry> open) {
        super(new BorderLayout(0,10));
        setBorder(BorderFactory.createEmptyBorder(12,12,12,6));setPreferredSize(new Dimension(290,600));
        JLabel title = new JLabel("STRUCTURE LIBRARY");title.setForeground(ViewerTheme.ACCENT);
        title.setFont(title.getFont().deriveFont(Font.BOLD,12));
        search.setToolTipText("Search by mod JAR, namespace, or structure name");
        search.setBorder(BorderFactory.createTitledBorder("Search structures"));
        JPanel header = new JPanel(new GridLayout(3,1,0,6));header.add(title);header.add(search);header.add(count);
        count.setForeground(ViewerTheme.MUTED);count.setFont(count.getFont().deriveFont(11f));add(header,BorderLayout.NORTH);
        tree.setRootVisible(false);tree.setShowsRootHandles(true);tree.setRowHeight(25);
        tree.setCellRenderer(new DefaultTreeCellRenderer() {
            @Override public Component getTreeCellRendererComponent(JTree t,Object value,boolean selected,boolean expanded,boolean leaf,int row,boolean focus) {
                super.getTreeCellRendererComponent(t,value,selected,expanded,leaf,row,focus);
                Object user=((DefaultMutableTreeNode)value).getUserObject();
                setToolTipText(user instanceof StructureCatalog.Entry entry?entry.source().description():user.toString());return this;
            }
        });
        ToolTipManager.sharedInstance().registerComponent(tree);
        tree.addTreeSelectionListener(event -> {
            if(updating)return;
            if(tree.getLastSelectedPathComponent() instanceof DefaultMutableTreeNode node
                    && node.getUserObject() instanceof StructureCatalog.Entry entry)open.accept(entry);
        });
        JScrollPane scroll = new JScrollPane(tree);scroll.setBorder(BorderFactory.createEmptyBorder());add(scroll);
        debounce = new javax.swing.Timer(180,event -> filter());debounce.setRepeats(false);
        search.getDocument().addDocumentListener(new DocumentListener() {
            public void insertUpdate(DocumentEvent e){debounce.restart();}
            public void removeUpdate(DocumentEvent e){debounce.restart();}
            public void changedUpdate(DocumentEvent e){debounce.restart();}
        });
        filter();
    }
    void setCatalog(StructureCatalog.Result catalog) { entries=catalog.entries();filter(); }
    void clear() { entries=List.of();filter(); }
    void scanning() { count.setText("Scanning local structures…"); }
    void close() { debounce.stop();ToolTipManager.sharedInstance().unregisterComponent(tree); }
    private void filter() {
        Object selected=tree.getLastSelectedPathComponent();
        var previous=selected instanceof DefaultMutableTreeNode node?node.getUserObject():null;
        var root=new DefaultMutableTreeNode("Structures");Map<String,DefaultMutableTreeNode> groups=new LinkedHashMap<>();
        String query=search.getText().strip().toLowerCase(Locale.ROOT);int shown=0;TreePath restore=null;
        for(var entry:entries){
            if(!entry.searchText().contains(query))continue;
            var group=groups.computeIfAbsent(entry.group(),name->{var node=new DefaultMutableTreeNode(name);root.add(node);return node;});
            var leaf=new DefaultMutableTreeNode(entry);group.add(leaf);shown++;
            if(entry.equals(previous))restore=new TreePath(leaf.getPath());
        }
        updating=true;tree.setModel(new DefaultTreeModel(root));
        // Expand source groups only; a search narrows their children without thousands of recursive expansions.
        for(var group:groups.values())tree.expandPath(new TreePath(group.getPath()));
        if(restore!=null)tree.setSelectionPath(restore);
        updating=false;
        count.setText(shown+" structures  ·  "+groups.size()+" sources");
    }
}
