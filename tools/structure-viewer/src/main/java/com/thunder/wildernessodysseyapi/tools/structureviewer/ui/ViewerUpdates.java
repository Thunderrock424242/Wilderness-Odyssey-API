package com.thunder.wildernessodysseyapi.tools.structureviewer.ui;

import com.thunder.wildernessodysseyapi.tools.structureviewer.update.*;
import javax.swing.*;
import java.awt.*;
import java.io.IOException;
import java.nio.file.Path;
import java.util.concurrent.*;

/** One background update operation per window; Later defers automatic reminders until the next launch. */
public final class ViewerUpdates implements AutoCloseable {
    @FunctionalInterface public interface InstallerLauncher { void launch(Path installer)throws IOException; }
    private final JFrame owner;
    private final Path directory;
    private final String current;
    private final UpdateSource source;
    private final InstallerLauncher launcher;
    private final JButton button=new JButton("Check for updates");
    private final ExecutorService worker=new ThreadPoolExecutor(1,1,0,TimeUnit.MILLISECONDS,new ArrayBlockingQueue<>(1),r->{
        Thread thread=new Thread(r,"structure-viewer-updates");thread.setDaemon(true);return thread;
    });
    private volatile boolean closed;
    private boolean busy;
    private JDialog prompt;

    public ViewerUpdates(JFrame owner,Path directory,String current,UpdateSource source,InstallerLauncher launcher) {
        this.owner=owner;this.directory=directory;this.current=current;this.source=source;this.launcher=launcher;
        button.setName("check-updates");button.setMargin(new Insets(3,8,3,8));
        button.setToolTipText("Check GitHub for a newer Structure Viewer version");button.addActionListener(event->check(true));
    }
    public ViewerUpdates(JFrame owner,Path directory) {
        this(owner,directory,System.getProperty("structureViewer.version","development"),new GitHubUpdates(),
                installer->new ProcessBuilder(installer.toString()).directory(installer.getParent().toFile()).start());
    }
    public JButton button(){return button;}

    /** Automatic checks are quiet when offline; explicit checks always report their outcome. */
    public void check(boolean manual) {
        if(closed||busy)return;
        if(prompt!=null&&prompt.isDisplayable()){prompt.toFront();return;}
        var version=ViewerRelease.Version.parse(current);
        if(version.isEmpty()) {
            if(manual)message("Development build","Update checks are available in the installed Windows application.");
            return;
        }
        busy=true;button.setEnabled(false);button.setText("Checking…");
        worker.submit(()->{
            try {
                var release=source.latest();
                SwingUtilities.invokeLater(()->{
                    if(closed)return;idle();
                    if(release.isPresent()&&release.get().version().compareTo(version.get())>0)offer(release.get());
                    else if(release.isEmpty()) {
                        button.setToolTipText("No published viewer installer was found. Try again later.");
                        if(manual)message("No update available","No published Structure Viewer installer was found. Try again later.");
                    }else if(manual)message("You're up to date","Structure Viewer "+current+" is the newest available version.");
                });
            }catch(Exception error){
                SwingUtilities.invokeLater(()->{
                    if(closed)return;idle();button.setToolTipText("Update check unavailable. Click to try again.");
                    if(manual)message("Couldn't check for updates","Check your internet connection and try again later.\n\n"+reason(error));
                });
            }
        });
    }

    private void idle(){busy=false;button.setEnabled(true);button.setText("Check for updates");button.setToolTipText("Check GitHub for a newer Structure Viewer version");}
    private JDialog dialog(String title) {
        JDialog dialog=new JDialog(owner,title,Dialog.ModalityType.MODELESS);dialog.setName("viewer-update-prompt");
        dialog.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);dialog.setResizable(false);
        dialog.setLayout(new BorderLayout(0,14));
        ((JComponent)dialog.getContentPane()).setBorder(BorderFactory.createEmptyBorder(22,24,18,24));
        prompt=dialog;return dialog;
    }
    private void show(JDialog dialog){dialog.pack();dialog.setLocationRelativeTo(owner);dialog.setVisible(true);}
    private static JTextArea text(String value) {
        JTextArea text=new JTextArea(value);text.setName("update-message");text.setEditable(false);text.setOpaque(false);
        text.setFont(UIManager.getFont("Label.font"));text.setLineWrap(true);text.setWrapStyleWord(true);text.setColumns(42);
        return text;
    }
    private void message(String title,String explanation) {
        JDialog dialog=dialog(title);dialog.add(text(explanation));
        JButton close=new JButton("OK");close.setName("close-update");close.addActionListener(event->dialog.dispose());
        JPanel actions=new JPanel(new FlowLayout(FlowLayout.RIGHT,0,0));actions.add(close);dialog.add(actions,BorderLayout.SOUTH);
        dialog.getRootPane().setDefaultButton(close);show(dialog);
    }
    private void offer(ViewerRelease release) {
        JDialog dialog=dialog("Update available");
        JPanel body=new JPanel(new BorderLayout(0,12));
        JLabel heading=new JLabel("Structure Viewer "+release.version()+" is available");heading.setFont(heading.getFont().deriveFont(Font.BOLD,17f));
        body.add(heading,BorderLayout.NORTH);
        JTextArea explanation=text("You have version "+current+".\n\nUpdate downloads the Windows installer and closes the viewer when setup starts. Your modpack files and settings are kept.\n\nChoose Later to be reminded the next time you open the viewer.");
        explanation.setRows(8);
        body.add(explanation);JProgressBar progress=new JProgressBar(0,100);progress.setName("update-progress");progress.setVisible(false);body.add(progress,BorderLayout.SOUTH);
        dialog.add(body);
        JButton later=new JButton("Later"),update=new JButton("Update");later.setName("update-later");update.setName("install-update");
        JPanel actions=new JPanel(new FlowLayout(FlowLayout.RIGHT,8,0));actions.add(later);actions.add(update);dialog.add(actions,BorderLayout.SOUTH);
        later.addActionListener(event->dialog.dispose());dialog.getRootPane().setDefaultButton(update);
        update.addActionListener(event->{
            if(closed||busy)return;
            busy=true;button.setEnabled(false);update.setEnabled(false);later.setEnabled(false);
            progress.setVisible(true);progress.setStringPainted(true);progress.setValue(0);progress.setString("Downloading update…");
            dialog.setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);dialog.pack();
            worker.submit(()->{
                try {
                    Path installer=source.download(release,directory,percent->SwingUtilities.invokeLater(()->{
                        if(!closed){progress.setValue(percent);progress.setString(percent==100?"Verifying installer…":percent+"% downloaded");}
                    }));
                    if(closed||Thread.currentThread().isInterrupted())return;
                    launcher.launch(installer);
                    SwingUtilities.invokeLater(()->{if(!closed){dialog.dispose();owner.dispose();}});
                }catch(Exception error){SwingUtilities.invokeLater(()->{
                    if(closed)return;idle();update.setEnabled(true);later.setEnabled(true);progress.setVisible(false);
                    dialog.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
                    explanation.setText("The update couldn't start. The viewer is still open.\n\n"+reason(error)+"\n\nChoose Update to try again, or Later to be reminded on your next launch.");explanation.setRows(12);dialog.pack();
                });}
            });
        });show(dialog);
    }
    private static String reason(Exception error){String message=error.getMessage();return message==null?"The update service is unavailable.":message.length()>300?message.substring(0,300)+"…":message;}
    @Override public void close(){closed=true;source.close();worker.shutdownNow();if(prompt!=null)prompt.dispose();}
}
