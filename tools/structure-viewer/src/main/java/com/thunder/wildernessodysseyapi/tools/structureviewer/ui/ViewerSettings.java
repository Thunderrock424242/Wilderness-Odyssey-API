package com.thunder.wildernessodysseyapi.tools.structureviewer.ui;

import com.thunder.wildernessodysseyapi.tools.structureviewer.render.RenderQuality;
import java.io.*;
import java.nio.file.*;
import java.util.*;

/** Explicit local preferences under ignored tool output; quality is never auto-overridden. */
public record ViewerSettings(RenderQuality quality,boolean edges,boolean textures,boolean autoReload,List<Path> assets) {
    public ViewerSettings{assets=List.copyOf(assets);}
    /** Restores saved controls or reports invalid settings to the caller. */
    public static ViewerSettings read(Path file)throws IOException{
        Properties values=new Properties();
        if(Files.isRegularFile(file)){
            if(Files.size(file)>65536)throw new IOException("Viewer settings exceed 64 KiB");
            try(var stream=Files.newInputStream(file)){values.load(stream);}
        }
        RenderQuality quality;
        try{quality=RenderQuality.valueOf(values.getProperty("quality","HIGH"));}
        catch(IllegalArgumentException e){quality=RenderQuality.HIGH;}
        List<Path> assets=new ArrayList<>();
        for(int i=0;i<32;i++){String path=values.getProperty("asset."+i);if(path!=null)assets.add(Path.of(path));}
        return new ViewerSettings(quality,Boolean.parseBoolean(values.getProperty("edges","true")),
                Boolean.parseBoolean(values.getProperty("textures","true")),Boolean.parseBoolean(values.getProperty("autoReload","true")),assets);
    }
    /** Saves via a neighboring temporary file so interruption cannot truncate valid settings. */
    public void save(Path file)throws IOException{
        Files.createDirectories(file.toAbsolutePath().getParent());Properties values=new Properties();
        values.setProperty("quality",quality.name());values.setProperty("edges",Boolean.toString(edges));
        values.setProperty("textures",Boolean.toString(textures));values.setProperty("autoReload",Boolean.toString(autoReload));
        for(int i=0;i<assets.size();i++)values.setProperty("asset."+i,assets.get(i).toString());
        Path temporary=Files.createTempFile(file.toAbsolutePath().getParent(),"viewer-settings-",".tmp");
        try{
            try(var output=Files.newOutputStream(temporary)){values.store(output,"Structure Viewer preferences");}
            try{Files.move(temporary,file,StandardCopyOption.REPLACE_EXISTING,StandardCopyOption.ATOMIC_MOVE);}
            catch(AtomicMoveNotSupportedException e){Files.move(temporary,file,StandardCopyOption.REPLACE_EXISTING);}
        }finally{Files.deleteIfExists(temporary);}
    }
}
