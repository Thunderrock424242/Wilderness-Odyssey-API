package com.thunder.wildernessodysseyapi.tools.structureviewer.ui;

import javax.swing.*;
import java.awt.*;

/** Progress for the existing background loader; the header's theme switch stays accessible. */
final class LoadingScreen extends JPanel {
    private final JLabel title=new JLabel("Opening Structure Viewer",SwingConstants.CENTER);
    private final JLabel detail=new JLabel("Preparing your library…",SwingConstants.CENTER);
    private final JProgressBar progress=new JProgressBar();

    LoadingScreen() {
        super(new GridBagLayout());setName("loading-screen");
        JPanel content=new JPanel();content.setOpaque(false);content.setLayout(new BoxLayout(content,BoxLayout.Y_AXIS));
        JLabel icon=new JLabel(new ImageIcon(ViewerTheme.icon(52)));icon.setAlignmentX(.5f);
        title.setAlignmentX(.5f);title.setFont(title.getFont().deriveFont(Font.BOLD,22f));
        detail.setAlignmentX(.5f);detail.setFont(detail.getFont().deriveFont(13f));
        progress.setName("loading-progress");progress.setAlignmentX(.5f);progress.setIndeterminate(true);
        progress.setMaximumSize(new Dimension(300,5));progress.setPreferredSize(new Dimension(300,5));
        JLabel hint=new JLabel("Use the Light / Dark switch above to choose your appearance.");
        hint.setAlignmentX(.5f);hint.setFont(hint.getFont().deriveFont(12f));hint.setName("loading-hint");
        content.add(icon);content.add(Box.createVerticalStrut(22));content.add(title);content.add(Box.createVerticalStrut(10));
        content.add(detail);content.add(Box.createVerticalStrut(24));content.add(progress);content.add(Box.createVerticalStrut(22));content.add(hint);
        add(content);applyTheme();
    }

    void showProgress(String heading,String message) { title.setText(heading);detail.setText(message);progress.setIndeterminate(true); }
    void stop() { progress.setIndeterminate(false); }
    void applyTheme() {
        setBackground(ViewerTheme.BACKGROUND);title.setForeground(ViewerTheme.TEXT);detail.setForeground(ViewerTheme.MUTED);
        for(Component child:((JPanel)getComponent(0)).getComponents())if(child instanceof JLabel label&&"loading-hint".equals(label.getName()))label.setForeground(ViewerTheme.MUTED);
    }
}
