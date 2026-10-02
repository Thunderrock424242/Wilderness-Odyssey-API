package com.thunder.wildernessodysseyapi.tools.structureviewer.ui;

import javax.swing.*;
import javax.swing.plaf.ColorUIResource;
import javax.swing.plaf.FontUIResource;
import javax.swing.plaf.metal.DefaultMetalTheme;
import javax.swing.plaf.metal.MetalLookAndFeel;
import java.awt.*;
import java.awt.image.BufferedImage;

/** Consistent desktop styling and a vector-drawn application mark; no native theme dependency. */
public final class ViewerTheme {
    public static final Color BACKGROUND = new Color(0x111a24), PANEL = new Color(0x1c2938),
            TEXT = new Color(0xdfebf4), MUTED = new Color(0x9aafc3), ACCENT = new Color(0x58d6b0);
    private ViewerTheme() {}
    public static void install() {
        MetalLookAndFeel.setCurrentTheme(new DefaultMetalTheme() {
            @Override protected ColorUIResource getPrimary1(){return new ColorUIResource(0x264a50);}
            @Override protected ColorUIResource getPrimary2(){return new ColorUIResource(0x315967);}
            @Override protected ColorUIResource getPrimary3(){return new ColorUIResource(PANEL);}
            @Override protected ColorUIResource getSecondary1(){return new ColorUIResource(0x385066);}
            @Override protected ColorUIResource getSecondary2(){return new ColorUIResource(0x263747);}
            @Override protected ColorUIResource getSecondary3(){return new ColorUIResource(BACKGROUND);}
        });
        try { UIManager.setLookAndFeel(UIManager.getCrossPlatformLookAndFeelClassName()); }
        catch (ReflectiveOperationException | UnsupportedLookAndFeelException error) { System.err.println("Using default theme: " + error.getMessage()); }
        for (Object key : java.util.Collections.list(UIManager.getDefaults().keys())) {
            if (UIManager.get(key) instanceof Font) UIManager.put(key,new FontUIResource("Segoe UI",Font.PLAIN,13));
        }
        for (String name : java.util.List.of("Panel","Viewport","ScrollPane","ToolBar","TabbedPane","CheckBox","Label")) {
            UIManager.put(name+".background",new ColorUIResource(BACKGROUND));
            UIManager.put(name+".foreground",new ColorUIResource(TEXT));
        }
        for (String name : java.util.List.of("Button","ToggleButton","ComboBox","TextField","TextArea","Tree","Spinner","FormattedTextField")) {
            UIManager.put(name+".background",new ColorUIResource(PANEL));
            UIManager.put(name+".foreground",new ColorUIResource(TEXT));
            UIManager.put(name+".selectionBackground",new ColorUIResource(0x315967));
            UIManager.put(name+".selectionForeground",new ColorUIResource(TEXT));
            UIManager.put(name+".caretForeground",new ColorUIResource(ACCENT));
        }
        UIManager.put("Tree.textBackground",new ColorUIResource(PANEL));
        UIManager.put("Tree.textForeground",new ColorUIResource(TEXT));
        UIManager.put("Tree.selectionBorderColor",new ColorUIResource(ACCENT));
        UIManager.put("TabbedPane.selected",new ColorUIResource(PANEL));
        UIManager.put("TabbedPane.unselectedBackground",new ColorUIResource(BACKGROUND));
        UIManager.put("TabbedPane.contentAreaColor",new ColorUIResource(PANEL));
        UIManager.put("SplitPane.background",new ColorUIResource(BACKGROUND));
        UIManager.put("SplitPane.dividerSize",7);
        UIManager.put("ScrollBar.background",new ColorUIResource(BACKGROUND));
        UIManager.put("ScrollBar.thumb",new ColorUIResource(0x385066));
        UIManager.put("ScrollBar.width",12);
        UIManager.put("Button.select",new ColorUIResource(0x315967));
        UIManager.put("Button.focus",new ColorUIResource(ACCENT));
        UIManager.put("TitledBorder.titleColor",new ColorUIResource(MUTED));
        UIManager.put("ToolTip.background",new ColorUIResource(PANEL));
        UIManager.put("ToolTip.foreground",new ColorUIResource(TEXT));
    }
    /** Draws an isometric structure mark, also used by the Windows launcher icon. */
    public static BufferedImage icon(int size) {
        BufferedImage image = new BufferedImage(size,size,BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);
        g.scale(size/64.0,size/64.0);g.setColor(BACKGROUND);g.fillRoundRect(0,0,64,64,16,16);
        g.setColor(ACCENT);g.fillPolygon(new int[]{32,54,32,10},new int[]{10,22,35,22},4);
        g.setColor(new Color(0x2c957f));g.fillPolygon(new int[]{10,32,32,10},new int[]{25,38,55,42},4);
        g.setColor(new Color(0x6b98bc));g.fillPolygon(new int[]{35,54,54,35},new int[]{37,25,42,54},4);
        g.dispose();return image;
    }
}
