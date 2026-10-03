package com.thunder.wildernessodysseyapi.tools.structureviewer.ui;

import com.thunder.wildernessodysseyapi.tools.structureviewer.io.StructureCatalog;
import com.thunder.wildernessodysseyapi.tools.structureviewer.io.StructureSource;
import org.junit.jupiter.api.Test;
import javax.swing.*;
import javax.swing.tree.*;
import java.awt.*;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

class LibraryBrowserTest {
    @Test void startsCollapsedAndPreservesManualExpansionUntilTheNextScan()throws Exception {
        SwingUtilities.invokeAndWait(()->{
            var opened=new AtomicInteger();
            var browser=new StructureBrowser(entry->opened.incrementAndGet(),()->{},()->{});
            var catalog=new StructureCatalog.Result(List.of(entry("first.jar","house"),entry("second.jar","tower")),List.of(),2);
            try {
                browser.setCatalog(catalog);
                var tree=findTree(browser);
                assertEquals(2,tree.getRowCount(),"A fresh scan should show only the two mod groups.");
                assertFalse(tree.isExpanded(group(tree,0)));assertFalse(tree.isExpanded(group(tree,1)));
                tree.expandPath(group(tree,0));
                assertEquals(3,tree.getRowCount(),"Expanding a mod should reveal its templates.");
                var first=(DefaultMutableTreeNode)group(tree,0).getLastPathComponent();
                tree.setSelectionPath(new TreePath(((DefaultMutableTreeNode)first.getFirstChild()).getPath()));
                assertEquals(1,opened.get());
                browser.setView(LibraryView.ALL);
                assertTrue(tree.isExpanded(group(tree,0)),"Filtering should retain a manually opened mod.");
                assertFalse(tree.isExpanded(group(tree,1)),"Filtering should leave other mods collapsed.");
                tree.collapsePath(group(tree,0));
                browser.setView(LibraryView.STRUCTURES);
                assertFalse(tree.isExpanded(group(tree,0)),"Restoring a selected template must not reopen a collapsed mod.");
                assertEquals(1,opened.get(),"Filtering must not reload the selected structure.");
                tree.expandPath(group(tree,0));
                browser.setCatalog(catalog);
                assertEquals(2,tree.getRowCount(),"A new scan should start with every mod collapsed again.");
            } finally {browser.close();}
        });
    }
    private static StructureCatalog.Entry entry(String jar,String name) {
        String path="data/demo/structure/"+name+".nbt";
        return new StructureCatalog.Entry(jar,path,new StructureSource(Path.of(jar).toAbsolutePath(),path));
    }
    private static TreePath group(JTree tree,int index) {
        var root=(DefaultMutableTreeNode)tree.getModel().getRoot();
        return new TreePath(((DefaultMutableTreeNode)root.getChildAt(index)).getPath());
    }
    private static JTree findTree(Container root) {
        for(Component child:root.getComponents()) {
            if(child instanceof JTree tree)return tree;
            if(child instanceof Container nested) {var tree=findTree(nested);if(tree!=null)return tree;}
        }
        return null;
    }
}
