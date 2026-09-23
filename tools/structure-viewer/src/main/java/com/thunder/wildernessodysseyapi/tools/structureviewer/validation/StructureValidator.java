package com.thunder.wildernessodysseyapi.tools.structureviewer.validation;

import com.thunder.wildernessodysseyapi.tools.structureviewer.assets.*;
import com.thunder.wildernessodysseyapi.tools.structureviewer.io.StructureReaders;
import java.nio.file.Path;
import java.util.List;

/** Headless structure/asset diagnostics using exactly the viewer's import and model resolution paths. */
public final class StructureValidator {
    private StructureValidator(){}
    /** Validates one structure. Unreadable files fail; recoverable preview warnings are printed. */
    public static void main(String[] args)throws Exception{
        if(args.length!=1)throw new IllegalArgumentException("Usage: structureValidator -PstructureViewerFile=<file.nbt|file.json>");
        var data=StructureReaders.read(Path.of(args[0]));
        Path project=Path.of(System.getProperty("structureViewer.projectDir","."));
        System.out.println(data.name()+" | "+data.size()+" | "+data.blocks().size()+" stored blocks");
        for(String issue:StructureValidation.inspect(data))System.out.println("IMPORT: "+issue);
        try(var assets=AssetRepository.discover(project,List.of())){
            var models=new BlockModelResolver(assets,BlockStateCatalog.discover(project,
                    Path.of(System.getProperty("structureViewer.buildDir",project.resolve("build").toString()))));
            for(var palette:data.palettes())for(var state:palette)models.resolve(state);
            for(String source:assets.sources())System.out.println("ASSETS: "+source);
            for(String issue:models.diagnostics())System.out.println("ASSET: "+issue);
            System.out.println("Resolved states: "+models.resolvedCount()+" | Placeholder states: "+models.missingCount());
        }
        System.out.println("Readable preview. Registry-only properties and custom Java renderers require Minecraft validation.");
    }
}
