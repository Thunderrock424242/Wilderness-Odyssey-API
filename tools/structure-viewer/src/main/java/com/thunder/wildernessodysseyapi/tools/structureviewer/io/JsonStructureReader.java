package com.thunder.wildernessodysseyapi.tools.structureviewer.io;

import com.google.gson.*;
import com.thunder.wildernessodysseyapi.tools.structureviewer.model.*;
import com.thunder.wildernessodysseyapi.tools.structureviewer.model.StructureData.*;
import java.io.IOException;
import java.nio.file.Path;
import java.util.*;

/** Reads concrete Blueprint-v1 and palette-based structure JSON into the same model used by NBT. */
public final class JsonStructureReader implements StructureReader {
    @Override public StructureData read(Path path)throws IOException{
        JsonObject root=JsonInput.read(path);List<String> warnings=new ArrayList<>();
        if(root.has("formatVersion")&&root.get("formatVersion").getAsInt()!=1)throw new IOException("Unsupported blueprint formatVersion");
        Position size=position(root.get("size"));
        if(size.x()<0||size.y()<0||size.z()<0)throw new IOException("Negative structure dimensions");
        if(!root.has("blocks")||!root.get("blocks").isJsonArray())throw new IOException("Structure JSON requires a blocks array");
        List<BlockState> palette=new ArrayList<>();
        if(root.has("palette"))for(JsonElement e:root.getAsJsonArray("palette")){
            JsonObject state=e.getAsJsonObject();palette.add(state(state,"Name","Properties"));
        }
        Map<BlockState,Integer> indices=new LinkedHashMap<>();
        for(int i=0;i<palette.size();i++)indices.putIfAbsent(palette.get(i),i);
        List<Block> blocks=new ArrayList<>();int index=0;
        for(JsonElement e:root.getAsJsonArray("blocks")){
            if(blocks.size()>4_000_000)throw new IOException("Too many structure blocks");
            try{
                JsonObject entry=e.getAsJsonObject();Position pos=position(entry.get("pos"));int stateIndex;
                if(entry.has("block")){
                    BlockState state=state(entry,"block","properties");
                    stateIndex=indices.computeIfAbsent(state,s->{palette.add(s);return palette.size()-1;});
                    if(!state.id().contains(":"))warn(warnings,"Unresolved semantic material/block '"+state.id()+"'; compile through StructureGen to resolve its content policy.");
                }else stateIndex=entry.get("state").getAsBigDecimal().intValueExact();
                NbtValue blockEntity=null;
                if(entry.has("blockEntitySnbt"))blockEntity=snbt(entry.get("blockEntitySnbt"),warnings,"block "+index);
                else if(entry.has("nbt"))blockEntity=tree(entry.get("nbt"));
                Map<String,NbtValue> extra=new LinkedHashMap<>();
                for(var field:entry.entrySet())if(!Set.of("pos","block","state","properties","blockEntitySnbt","nbt").contains(field.getKey()))
                    extra.put(field.getKey(),tree(field.getValue()));
                blocks.add(new Block(pos,stateIndex,blockEntity,extra));
            }catch(RuntimeException|IOException error){warn(warnings,"Skipped malformed block "+index+": "+error.getMessage());}
            index++;
        }
        List<NbtValue> entities=new ArrayList<>();
        if(root.has("entities"))for(JsonElement e:root.getAsJsonArray("entities")){
            Map<String,NbtValue> entity=new LinkedHashMap<>(tree(e).compound());
            if(e.isJsonObject()&&e.getAsJsonObject().has("nbtSnbt"))entity.put("nbt",snbt(e.getAsJsonObject().get("nbtSnbt"),warnings,"entity"));
            entities.add(new NbtValue(10,entity));
        }
        Map<String,NbtValue> metadata=new LinkedHashMap<>();
        for(var entry:root.entrySet())if(!Set.of("blocks","palette","entities","size").contains(entry.getKey()))
            metadata.put(entry.getKey(),tree(entry.getValue()));
        String name=root.has("name")?root.get("name").getAsString():path.getFileName().toString();
        return new StructureData(name,path.toAbsolutePath().normalize(),size,blocks,List.of(palette),entities,metadata,warnings);
    }
    private static BlockState state(JsonObject object,String idKey,String propertiesKey){
        String id=object.has(idKey)?object.get(idKey).getAsString():BlockState.MISSING.id();
        Map<String,String> properties=new LinkedHashMap<>();
        if(object.has(propertiesKey))object.getAsJsonObject(propertiesKey).entrySet().forEach(e->properties.put(e.getKey(),e.getValue().getAsString()));
        return new BlockState(id,properties);
    }
    private static Position position(JsonElement value)throws IOException{
        if(value==null||!value.isJsonArray()||value.getAsJsonArray().size()!=3)throw new IOException("Position/size must contain three integers");
        var a=value.getAsJsonArray();
        try{return new Position(a.get(0).getAsBigDecimal().intValueExact(),a.get(1).getAsBigDecimal().intValueExact(),a.get(2).getAsBigDecimal().intValueExact());}
        catch(RuntimeException e){throw new IOException("Position/size must contain three exact integers",e);}
    }
    private static NbtValue snbt(JsonElement value,List<String> warnings,String context){
        try{return SnbtReader.read(value.getAsString());}
        catch(IOException|RuntimeException e){warn(warnings,"Malformed "+context+" SNBT: "+e.getMessage()+"; original text retained");return new NbtValue(8,value.toString());}
    }
    private static void warn(List<String> list,String warning){if(list.size()<200)list.add(warning);}
    private static NbtValue tree(JsonElement value){
        if(value.isJsonObject()){Map<String,NbtValue> result=new LinkedHashMap<>();value.getAsJsonObject().entrySet().forEach(e->result.put(e.getKey(),tree(e.getValue())));return new NbtValue(10,result);}
        if(value.isJsonArray()){List<NbtValue> result=new ArrayList<>();value.getAsJsonArray().forEach(e->result.add(tree(e)));return new NbtValue(9,result);}
        if(value.isJsonNull())return new NbtValue(8,"null");
        JsonPrimitive p=value.getAsJsonPrimitive();if(p.isBoolean())return new NbtValue(1,(byte)(p.getAsBoolean()?1:0));
        if(p.isNumber()){try{return new NbtValue(3,p.getAsBigDecimal().intValueExact());}catch(ArithmeticException ignored){return new NbtValue(6,p.getAsDouble());}}
        return new NbtValue(8,p.getAsString());
    }
}
