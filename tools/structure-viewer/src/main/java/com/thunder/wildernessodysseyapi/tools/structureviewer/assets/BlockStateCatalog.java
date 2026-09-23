package com.thunder.wildernessodysseyapi.tools.structureviewer.assets;

import com.google.gson.*;
import com.thunder.wildernessodysseyapi.tools.structureviewer.io.JsonInput;
import com.thunder.wildernessodysseyapi.tools.structureviewer.model.StructureData.BlockState;
import java.nio.file.*;
import java.util.*;
import java.util.function.Consumer;

/** Reuses StructureGen's exported property domains/defaults without starting or duplicating a registry. */
public final class BlockStateCatalog {
    private final Map<String,JsonObject> blocks;
    private final String description;
    private BlockStateCatalog(Map<String,JsonObject> blocks,String description){this.blocks=Map.copyOf(blocks);this.description=description;}
    /** No registry defaults are available in an isolated pack-only test. */
    public static BlockStateCatalog empty(){return new BlockStateCatalog(Map.of(),"No StructureGen catalog available; only asset-defined states can be checked.");}
    /** Reads the first available existing snapshot. It is explicitly not certified against the live environment. */
    public static BlockStateCatalog discover(Path project,Path build){
        for(Path root:List.of(build,project.resolve("build"),project.resolve(".codex-build"))){
            Path file=root.resolve("generated/structuregen/catalog/available-content.json");
            if(!Files.isRegularFile(file))continue;
            try{
                Map<String,JsonObject> blocks=new HashMap<>();
                for(JsonElement item:JsonInput.read(file).getAsJsonArray("blocks")){
                    JsonObject block=item.getAsJsonObject();blocks.put(block.get("id").getAsString(),block);
                }
                return new BlockStateCatalog(blocks,"Cached StructureGen property snapshot: "+file
                        +" (environment fingerprint not revalidated; current mod registration is unverified).");
            }catch(Exception e){return new BlockStateCatalog(Map.of(),"Could not read cached StructureGen catalog: "+e.getMessage());}
        }
        return empty();
    }
    /** Fills omitted defaults while keeping every explicitly supplied property untouched. */
    public BlockState complete(BlockState source,Consumer<String> diagnostic){
        try{
            return completeChecked(source,diagnostic);
        }catch(RuntimeException error){
            diagnostic.accept(source.id()+": malformed cached catalog entry; supplied properties retained ("+error.getMessage()+").");
            return source;
        }
    }
    private BlockState completeChecked(BlockState source,Consumer<String> diagnostic){
        JsonObject block=blocks.get(source.id());
        if(block==null){
            if(!blocks.isEmpty())diagnostic.accept(source.id()+": absent from cached registry catalog; current registration is unknown.");
            return source;
        }
        Map<String,String> values=new LinkedHashMap<>();
        if(block.has("defaultProperties"))block.getAsJsonObject("defaultProperties").entrySet().forEach(e->values.put(e.getKey(),e.getValue().getAsString()));
        JsonObject allowed=block.has("properties")?block.getAsJsonObject("properties"):new JsonObject();
        source.properties().forEach((key,value)->{
            if(!allowed.has(key))diagnostic.accept(source.id()+": property '"+key+"' is absent from cached registry catalog.");
            else{
                boolean found=false;for(JsonElement candidate:allowed.getAsJsonArray(key))if(candidate.getAsString().equals(value))found=true;
                if(!found)diagnostic.accept("Invalid cached-catalog property "+source.id()+"["+key+"="+value+"]");
            }
            values.put(key,value);
        });
        return new BlockState(source.id(),values);
    }
    /** Clear provenance for the validation report. */
    public String description(){return description;}
}
