package com.thunder.wildernessodysseyapi.tools.structureviewer.ui;

import javax.swing.*;
import javax.swing.plaf.ColorUIResource;
import javax.swing.plaf.FontUIResource;
import com.formdev.flatlaf.FlatLightLaf;
import com.formdev.flatlaf.FlatDarkLaf;
import java.awt.*;
import java.awt.image.BufferedImage;

/** Saved light/dark desktop controls with restrained colors and display scaling supplied by FlatLaf. */
public final class ViewerTheme {
    public static Color BACKGROUND = new Color(0xf5f6f8), PANEL = Color.WHITE,
            TEXT = new Color(0x242830), MUTED = new Color(0x69717e), ACCENT = new Color(0x1765bd),
            BORDER = new Color(0xdfe3e8), VIEWPORT = new Color(0xebedf0);
    public static Color PRIMARY = new Color(0x174a80), PRIMARY_TEXT = Color.WHITE;
    private ViewerTheme() {}

    /** Installs the look and feel before any application components are created. */
    public static void install() {
        install(false);
    }

    /** Called on the event thread; background rendering receives a captured background color. */
    public static void install(boolean dark) {
        if (!(dark?FlatDarkLaf.setup():FlatLightLaf.setup())) System.err.println("Could not install the viewer look and feel.");
        BACKGROUND=new Color(dark?0x24272c:0xf5f6f8);PANEL=new Color(dark?0x2c3036:0xffffff);
        TEXT=new Color(dark?0xe8ebef:0x242830);MUTED=new Color(dark?0xb0b7c2:0x69717e);
        ACCENT=new Color(dark?0x8cbaf0:0x1765bd);BORDER=new Color(dark?0x454b55:0xdfe3e8);
        VIEWPORT=new Color(dark?0x202328:0xebedf0);
        PRIMARY=new Color(dark?0x9ac5f7:0x174a80);PRIMARY_TEXT=new Color(dark?0x142033:0xffffff);
        UIManager.put("defaultFont", new FontUIResource("Segoe UI", Font.PLAIN, 13));
        UIManager.put("Panel.background", new ColorUIResource(PANEL));
        UIManager.put("Label.foreground", new ColorUIResource(TEXT));
        UIManager.put("Component.accentColor", new ColorUIResource(ACCENT));
        UIManager.put("Component.focusColor", new ColorUIResource(ACCENT));
        UIManager.put("Component.borderColor", new ColorUIResource(BORDER));
        UIManager.put("Component.arc", 6);
        UIManager.put("Button.arc", 6);
        UIManager.put("Button.margin", new Insets(6, 12, 6, 12));
        UIManager.put("Button.default.background", new ColorUIResource(ACCENT));
        UIManager.put("Button.default.foreground", new ColorUIResource(PRIMARY_TEXT));
        UIManager.put("Tree.background", new ColorUIResource(PANEL));
        UIManager.put("Tree.selectionBackground", new ColorUIResource(dark?0x384e69:0xe5effa));
        UIManager.put("Tree.selectionForeground", new ColorUIResource(TEXT));
        UIManager.put("Tree.selectionInactiveBackground", new ColorUIResource(dark?0x39414b:0xedf1f6));
        UIManager.put("Tree.selectionArc", 4);
        UIManager.put("TextComponent.arc", 6);
        UIManager.put("TabbedPane.tabInsets", new Insets(8, 10, 8, 10));
        UIManager.put("TabbedPane.underlineColor", new ColorUIResource(ACCENT));
        UIManager.put("TabbedPane.showContentSeparator", false);
        UIManager.put("SplitPane.dividerSize", 5);
        UIManager.put("SplitPaneDivider.gripDotCount", 0);
        UIManager.put("ScrollBar.width", 10);
        UIManager.put("ScrollBar.thumbArc", 999);
    }

    /** Compact painted switch; the selected state is available to keyboard/accessibility users. */
    static Icon themeSwitchIcon() {
        return new Icon() {
            public int getIconWidth() { return 30; }
            public int getIconHeight() { return 18; }
            public void paintIcon(Component component,Graphics graphics,int x,int y) {
                boolean selected=component instanceof AbstractButton button&&button.isSelected();
                Graphics2D g=(Graphics2D)graphics.create();
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);
                g.setColor(selected?ACCENT:MUTED);g.fillRoundRect(x,y+1,30,16,16,16);
                g.setColor(selected?PRIMARY_TEXT:Color.WHITE);g.fillOval(x+(selected?17:3),y+3,12,12);g.dispose();
            }
        };
    }

    static Icon folderIcon() {
        return new Icon() {
            public int getIconWidth() { return 18; }
            public int getIconHeight() { return 18; }
            public void paintIcon(Component component,Graphics graphics,int x,int y) {
                Graphics2D g=(Graphics2D)graphics.create();g.setColor(component.getForeground());
                g.setStroke(new BasicStroke(1.5f));g.drawPolyline(new int[]{x+2,x+2,x+7,x+9,x+16,x+16,x+2},
                        new int[]{y+14,y+4,y+4,y+6,y+6,y+14,y+14},7);g.dispose();
            }
        };
    }

    /** Uses a painted arrow so menu buttons do not depend on an installed font's symbol coverage. */
    static Icon dropdownIcon() {
        return new Icon() {
            public int getIconWidth() { return 10; }
            public int getIconHeight() { return 10; }
            public void paintIcon(Component component,Graphics graphics,int x,int y) {
                graphics.setColor(ViewerTheme.MUTED);
                graphics.fillPolygon(new int[]{x+1,x+9,x+5},new int[]{y+3,y+3,y+7},3);
            }
        };
    }
    /** Draws an isometric structure mark, also used by the Windows launcher icon. */
    public static BufferedImage icon(int size) {
        BufferedImage image = new BufferedImage(size,size,BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);
        g.scale(size/64.0,size/64.0);g.setColor(new Color(0x27384c));g.fillRoundRect(0,0,64,64,12,12);
        g.setColor(new Color(0xe8eef5));g.fillPolygon(new int[]{32,54,32,10},new int[]{10,22,35,22},4);
        g.setColor(new Color(0x94abc5));g.fillPolygon(new int[]{10,32,32,10},new int[]{25,38,55,42},4);
        g.setColor(new Color(0x4c80ba));g.fillPolygon(new int[]{35,54,54,35},new int[]{37,25,42,54},4);
        g.dispose();return image;
    }
}
