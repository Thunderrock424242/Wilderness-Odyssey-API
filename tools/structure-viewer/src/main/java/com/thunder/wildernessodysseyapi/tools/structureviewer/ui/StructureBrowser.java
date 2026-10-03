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
    private final JPanel content = new JPanel(new CardLayout());
    private final JLabel emptyTitle = new JLabel("No structures found", SwingConstants.CENTER);
    private final JLabel emptyHint = new JLabel("Open a modpack to browse its templates.", SwingConstants.CENTER);
    private List<StructureCatalog.Entry> entries = List.of();
    private boolean updating;

    StructureBrowser(Consumer<StructureCatalog.Entry> open, Runnable rescan) {
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
        search.putClientProperty("JTextField.placeholderText", "Search structures…");
        search.putClientProperty("JTextField.showClearButton", true);
        search.getAccessibleContext().setAccessibleName("Search structures");
        search.setPreferredSize(new Dimension(180,32));
        JPanel header = new JPanel(new BorderLayout(0,12));header.add(titleRow,BorderLayout.NORTH);header.add(search);
        count.setForeground(ViewerTheme.MUTED);count.setFont(count.getFont().deriveFont(12f));
        add(header,BorderLayout.NORTH);add(count,BorderLayout.SOUTH);
        tree.setRootVisible(false);tree.setShowsRootHandles(true);tree.setRowHeight(28);
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
        filter();
    }
    void setCatalog(StructureCatalog.Result catalog) { entries=catalog.entries();filter(); }
    void clear() { entries=List.of();filter(); }
    void scanning() { count.setText("Scanning structures…"); }
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
        emptyTitle.setText(query.isEmpty()?"No structures found":"No matching structures");
        emptyHint.setText(query.isEmpty()?"Choose a modpack with stored templates.":"Try a different name or clear the search.");
        ((CardLayout)content.getLayout()).show(content,shown==0?"empty":"tree");
    }
}
