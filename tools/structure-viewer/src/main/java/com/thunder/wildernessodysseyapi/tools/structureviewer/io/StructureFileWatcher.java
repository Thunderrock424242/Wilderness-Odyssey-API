package com.thunder.wildernessodysseyapi.tools.structureviewer.io;

import java.io.IOException;
import java.nio.file.*;
import java.util.concurrent.*;
import java.util.function.Consumer;

/** Watches one parent directory, including atomic replacement, with a debounce and bounded lifecycle. */
public final class StructureFileWatcher implements AutoCloseable {
    private final WatchService watcher;
    private final Thread thread;
    private final Path file;
    private final Consumer<String> onError;
    private volatile boolean closed;
    /** Starts watching the selected file; callback runs on this worker, never the Swing event thread. */
    public StructureFileWatcher(Path file,Runnable onChange,Consumer<String> onError)throws IOException{
        this.file=file.toAbsolutePath().normalize();this.onError=onError;
        watcher=FileSystems.getDefault().newWatchService();
        this.file.getParent().register(watcher,StandardWatchEventKinds.ENTRY_CREATE,StandardWatchEventKinds.ENTRY_MODIFY,StandardWatchEventKinds.ENTRY_DELETE);
        thread=new Thread(()->loop(onChange),"structure-viewer-watch");thread.setDaemon(true);thread.start();
    }
    private void loop(Runnable onChange){
        long changed=0;
        try{
            while(!closed){
                WatchKey key=watcher.poll(150,TimeUnit.MILLISECONDS);
                if(key!=null){
                    for(WatchEvent<?> event:key.pollEvents())if(event.kind()==StandardWatchEventKinds.OVERFLOW
                            ||file.getFileName().equals(event.context()))changed=System.nanoTime();
                    if(!key.reset()){onError.accept("Structure directory is no longer available.");return;}
                }
                if(changed!=0&&System.nanoTime()-changed>500_000_000L&&Files.isRegularFile(file)){
                    changed=0;onChange.run();
                }
            }
        }catch(InterruptedException e){Thread.currentThread().interrupt();}
        catch(ClosedWatchServiceException ignored){/* Normal window/file lifecycle. */}
        catch(RuntimeException e){if(!closed)onError.accept("File watching failed: "+e.getMessage());}
    }
    /** Releases native directory handles and terminates the watch worker. */
    @Override public void close(){closed=true;try{watcher.close();}catch(IOException e){onError.accept("Could not close file watcher: "+e.getMessage());}thread.interrupt();}
}
