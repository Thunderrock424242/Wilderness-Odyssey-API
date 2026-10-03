package com.thunder.wildernessodysseyapi.tools.structureviewer;

import com.thunder.wildernessodysseyapi.tools.structureviewer.ui.ViewerUpdates;
import com.thunder.wildernessodysseyapi.tools.structureviewer.update.*;
import javax.swing.*;
import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.net.URI;
import java.nio.file.*;
import java.util.Optional;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.function.IntConsumer;

/** Exercises actual update buttons/dialogs, replacing only HTTP and installer execution. */
public final class UpdateUiSmoke {
    private UpdateUiSmoke() {}
    public static void run(Path output)throws Exception {
        Files.createDirectories(output);
        FakeSource source=new FakeSource();
        try(Harness app=new Harness(output,source,path->{})) {
            edt(()->{app.updates.button().doClick();return null;});
            await(()->app.prompt()!=null);
            edt(()->{snapshot(app.prompt(),output.resolve("update-available.png"));click(app.prompt(),"update-later");return null;});
            await(()->app.prompt()==null);
            require(edt(app.window::isShowing)&&source.downloads.get()==0,"Later should leave the viewer open without downloading.");
            edt(()->{app.updates.button().doClick();return null;});await(()->app.prompt()!=null);
            require(source.checks.get()==2,"A manual check after Later should offer the update again.");
        }
        try(Harness app=new Harness(output,source,path->{})) {
            edt(()->{app.updates.check(false);return null;});await(()->app.prompt()!=null);
            require(source.checks.get()==3,"Next launch should remind about the deferred update.");
        }
        source=new FakeSource();source.release=release(new ViewerRelease.Version(1,0,2));
        try(Harness app=new Harness(output,source,path->{})) {
            edt(()->{app.updates.button().doClick();return null;});await(()->app.prompt()!=null);
            require(edt(()->message(app.prompt()).contains("newest available")),"Manual check should report up to date.");
            require(edt(()->find(app.prompt(),"install-update")==null),"An equal version must not offer installation.");
        }
        source=new FakeSource();source.offline=true;
        try(Harness app=new Harness(output,source,path->{})) {
            edt(()->{app.updates.check(false);return null;});await(()->app.updates.button().isEnabled());
            require(edt(()->app.prompt()==null),"Offline automatic checks should leave the viewer usable.");
            edt(()->{app.updates.button().doClick();return null;});await(()->app.prompt()!=null);
            require(edt(()->message(app.prompt()).contains("internet connection")),"Offline manual checks should explain how to retry.");
        }
        AtomicInteger launches=new AtomicInteger();AtomicBoolean failLaunch=new AtomicBoolean(true);
        source=new FakeSource();source.failDownload=true;
        FakeSource retrySource=source;
        try(Harness app=new Harness(output,source,path->{launches.incrementAndGet();if(failLaunch.get())throw new IOException("Setup couldn't start.");})) {
            edt(()->{app.updates.button().doClick();return null;});await(()->app.prompt()!=null);
            edt(()->{click(app.prompt(),"install-update");return null;});
            await(()->message(app.prompt()).contains("failed verification"));
            require(launches.get()==0&&edt(app.window::isShowing),"A failed download must never start setup or close the viewer.");
            retrySource.failDownload=false;
            edt(()->{click(app.prompt(),"install-update");return null;});await(()->message(app.prompt()).contains("Setup couldn't start"));
            require(edt(app.window::isShowing),"A setup launch failure must leave the viewer open.");
            failLaunch.set(false);edt(()->{click(app.prompt(),"install-update");return null;});await(()->!app.window.isDisplayable());
            require(launches.get()==2&&retrySource.downloads.get()==3,"Retry should launch setup and close the viewer only after success.");
        }
        source=new FakeSource();source.blockDownload=true;FakeSource blocked=source;AtomicInteger cancelledLaunches=new AtomicInteger();
        try(Harness app=new Harness(output,source,path->cancelledLaunches.incrementAndGet())) {
            edt(()->{app.updates.button().doClick();return null;});await(()->app.prompt()!=null);
            edt(()->{click(app.prompt(),"install-update");return null;});
            require(blocked.entered.await(5,TimeUnit.SECONDS),"Download didn't start.");
            edt(()->{require(!app.updates.button().isEnabled(),"A second operation must be disabled.");app.window.dispose();return null;});
            require(blocked.stopped.await(5,TimeUnit.SECONDS),"Closing the window didn't cancel the download.");
            require(cancelledLaunches.get()==0,"A cancelled update must not launch setup.");
        }
        System.out.println("UPDATE GUI PASSED: manual checks, Update/Later, next-launch reminder, equal-version and offline messages, download/launch retry, successful close, shutdown cancellation.");
    }
    private static ViewerRelease release(ViewerRelease.Version version) {
        return new ViewerRelease(version,URI.create(ViewerRelease.REPOSITORY),URI.create(ViewerRelease.REPOSITORY),1,"a".repeat(64),URI.create(ViewerRelease.REPOSITORY));
    }
    private static final class FakeSource implements UpdateSource {
        final AtomicInteger checks=new AtomicInteger(),downloads=new AtomicInteger();
        final CountDownLatch entered=new CountDownLatch(1),stopped=new CountDownLatch(1);
        ViewerRelease release=release(new ViewerRelease.Version(1,0,3));
        volatile boolean offline,failDownload,blockDownload;
        @Override public Optional<ViewerRelease> latest()throws IOException {checks.incrementAndGet();if(offline)throw new IOException("Offline fixture.");return Optional.of(release);}
        @Override public Path download(ViewerRelease release,Path directory,IntConsumer progress)throws IOException {
            downloads.incrementAndGet();entered.countDown();
            if(blockDownload)try {new CountDownLatch(1).await();}catch(InterruptedException error){Thread.currentThread().interrupt();throw new IOException("Cancelled.",error);}finally {stopped.countDown();}
            if(failDownload)throw new IOException("The installer failed verification.");
            Files.createDirectories(directory);Path installer=directory.resolve(release.installerName());Files.write(installer,new byte[]{1});progress.accept(100);return installer;
        }
    }
    private static final class Harness implements AutoCloseable {
        final JFrame window;final ViewerUpdates updates;
        Harness(Path output,UpdateSource source,ViewerUpdates.InstallerLauncher launcher)throws Exception {
            window=edt(()->{JFrame frame=new JFrame("Structure Viewer update check");frame.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);frame.setSize(620,430);frame.setLocationRelativeTo(null);return frame;});
            updates=edt(()->{var value=new ViewerUpdates(window,output.resolve("downloads"),"1.0.2",source,launcher);window.add(value.button(),BorderLayout.SOUTH);window.addWindowListener(new WindowAdapter(){@Override public void windowClosed(WindowEvent event){value.close();}});window.setVisible(true);return value;});
        }
        JDialog prompt(){for(Window owned:window.getOwnedWindows())if(owned instanceof JDialog dialog&&dialog.isShowing())return dialog;return null;}
        @Override public void close()throws Exception {edt(()->{updates.close();window.dispose();return null;});}
    }
    private static void require(boolean value,String message){if(!value)throw new AssertionError(message);}
    private static String message(Container root){if(root==null)return "";Component value=find(root,"update-message");return value instanceof JTextArea area?area.getText():"";}
    private static void click(Container root,String name){((JButton)find(root,name)).doClick();}
    private static Component find(Container root,String name){for(Component c:root.getComponents()){if(name.equals(c.getName()))return c;if(c instanceof Container nested){Component found=find(nested,name);if(found!=null)return found;}}return null;}
    private static void snapshot(JDialog dialog,Path file)throws Exception {BufferedImage image=new BufferedImage(dialog.getWidth(),dialog.getHeight(),BufferedImage.TYPE_INT_RGB);Graphics2D g=image.createGraphics();dialog.paint(g);g.dispose();ImageIO.write(image,"png",file.toFile());}
    private static void await(Callable<Boolean> test)throws Exception {long end=System.nanoTime()+5_000_000_000L;while(System.nanoTime()<end){if(edt(test))return;Thread.sleep(25);}throw new AssertionError("Update UI timed out.");}
    private static <T>T edt(Callable<T> action)throws Exception {
        if(SwingUtilities.isEventDispatchThread())return action.call();AtomicReference<T> result=new AtomicReference<>();AtomicReference<Throwable> failure=new AtomicReference<>();
        SwingUtilities.invokeAndWait(()->{try{result.set(action.call());}catch(Throwable error){failure.set(error);}});
        if(failure.get()!=null)throw new IllegalStateException("Update UI smoke failed",failure.get());return result.get();
    }
}
