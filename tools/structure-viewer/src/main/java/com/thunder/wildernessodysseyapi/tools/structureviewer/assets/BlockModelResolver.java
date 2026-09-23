package com.thunder.wildernessodysseyapi.tools.structureviewer.assets;

import com.google.gson.*;
import com.thunder.wildernessodysseyapi.tools.structureviewer.io.JsonInput;
import com.thunder.wildernessodysseyapi.tools.structureviewer.model.StructureData.BlockState;
import com.thunder.wildernessodysseyapi.tools.structureviewer.render.Vec3;
import java.io.IOException;
import java.util.*;

/**
 * Resolves vanilla element models and blockstate variants/multipart without game registration.
 * Dynamic/custom Java renderers are intentionally reported rather than executed in the tool.
 */
public final class BlockModelResolver {
    private final AssetRepository assets;
    private final BlockStateCatalog catalog;
    private final Map<BlockState,BlockModel> states=new HashMap<>();
    private final Map<String,JsonObject> models=new HashMap<>();
    private final Map<String,Texture> textures=new HashMap<>();
    private final Set<String> diagnostics=new LinkedHashSet<>();
    private long texturePixels;
    private int resolved,missing;

    /** Uses an already opened read-only asset stack. */
    public BlockModelResolver(AssetRepository assets){this(assets,BlockStateCatalog.empty());}
    /** Uses existing StructureGen snapshot defaults when available. */
    public BlockModelResolver(AssetRepository assets,BlockStateCatalog catalog){
        this.assets=assets;this.catalog=catalog;warn(catalog.description());
    }
    /** Resolves each unique palette state once; every failure has a renderable placeholder. */
    public BlockModel resolve(BlockState state){return states.computeIfAbsent(state,this::load);}
    /** Bounded and deduplicated diagnostics, including unavailable source packs. */
    public List<String> diagnostics(){List<String> result=new ArrayList<>(assets.diagnostics());result.addAll(diagnostics);return result;}
    /** Number of states with real model geometry. */
    public int resolvedCount(){return resolved;}
    /** Number of states requiring placeholder geometry. */
    public int missingCount(){return missing;}

    private void warn(String message){if(diagnostics.size()<1500)diagnostics.add(message);}
    private JsonObject json(String id,String folder) throws IOException {
        String path=AssetRepository.path(id,folder,".json");
        byte[] bytes=assets.read(path).orElseThrow(()->new IOException("Missing "+folder+" asset: "+id));
        return JsonInput.read(bytes);
    }

    private BlockModel load(BlockState source) {
        BlockState state=catalog.complete(source,this::warn);
        if(state.isAir())return ModelGeometry.cube(Texture.solid(0x6a9fb3));
        try {
            JsonObject blockstate=json(state.id(),"blockstates");
            List<JsonObject> applications=new ArrayList<>();
            if(blockstate.has("variants")){
                for(var entry:blockstate.getAsJsonObject("variants").entrySet())
                    if(matchesVariant(entry.getKey(),state.properties())){applications.add(choice(entry.getValue(),state.id()));break;}
            }
            if(blockstate.has("multipart"))for(JsonElement item:blockstate.getAsJsonArray("multipart")){
                JsonObject part=item.getAsJsonObject();
                if(!part.has("when")||matches(part.get("when"),state.properties()))applications.add(choice(part.get("apply"),state.id()));
            }
            if(applications.isEmpty() && blockstate.has("variants")) {
                for(var entry:blockstate.getAsJsonObject("variants").entrySet()) {
                    boolean compatible=true;
                    for(String pair:entry.getKey().split(",")) {
                        String[] kv=pair.split("=",2);
                        if(kv.length==2 && state.properties().containsKey(kv[0])
                                && !oneOf(kv[1],state.properties().get(kv[0]))) compatible=false;
                    }
                    if(compatible) {
                        applications.add(choice(entry.getValue(),state.id()));
                        warn(state.id()+": omitted properties lack registry defaults; preview uses compatible variant "+entry.getKey());
                        break;
                    }
                }
            }
            validateProperties(blockstate,state);
            if(applications.isEmpty())throw new IOException("No matching model for block state "+state.id()+state.properties());
            List<BlockModel.Quad> quads=new ArrayList<>();
            for(JsonObject apply:applications) bake(apply,state,quads);
            if(quads.isEmpty())throw new IOException("Model has no static geometry (custom/entity renderer): "+state.id());
            // Occlude neighbors only when an actual opaque full cube was supplied.
            boolean occludes=quads.size()==6;
            if(occludes) for(var q:quads){
                if(!q.texture().opaque()||q.cullSide()<0) {occludes=false;break;}
                for(Vec3 v:q.vertices()) if(!unitCorner(v)){occludes=false;break;}
            }
            resolved++;
            return new BlockModel(quads,occludes,false);
        }catch(IOException|RuntimeException e){warn(state.id()+state.properties()+": "+e.getMessage());missing++;
            return ModelGeometry.cube(Texture.missing());}
    }

    private static boolean unitCorner(Vec3 v){return bit(v.x())&&bit(v.y())&&bit(v.z());}
    private static boolean bit(double d){return Math.abs(d)<1e-6||Math.abs(d-1)<1e-6;}
    private JsonObject choice(JsonElement e,String block) {
        if(!e.isJsonArray())return e.getAsJsonObject();
        if(e.getAsJsonArray().isEmpty())throw new IllegalArgumentException("Empty model choice");
        if(e.getAsJsonArray().size()>1)warn(block+": weighted model alternatives use their first entry for a deterministic preview.");
        return e.getAsJsonArray().get(0).getAsJsonObject();
    }

    private static boolean matchesVariant(String key,Map<String,String> properties){
        if(key.isEmpty())return true;
        for(String pair:key.split(",")){
            String[] kv=pair.split("=",2);
            if(kv.length!=2||!oneOf(kv[1],properties.get(kv[0])))return false;
        }
        return true;
    }
    private static boolean oneOf(String choices,String value){return value!=null&&Arrays.asList(choices.split("\\|")).contains(value);}
    private static boolean matches(JsonElement condition,Map<String,String> properties){
        JsonObject object=condition.getAsJsonObject();
        for(var entry:object.entrySet()){
            if(entry.getKey().equals("OR")){
                boolean any=false;for(JsonElement child:entry.getValue().getAsJsonArray())any|=matches(child,properties);
                if(!any)return false;
            }else if(entry.getKey().equals("AND")){
                for(JsonElement child:entry.getValue().getAsJsonArray())if(!matches(child,properties))return false;
            }else if(!oneOf(entry.getValue().getAsString(),properties.get(entry.getKey())))return false;
        }
        return true;
    }

    private void validateProperties(JsonObject root,BlockState state){
        Map<String,Set<String>> allowed=new HashMap<>();
        if(root.has("variants"))for(String key:root.getAsJsonObject("variants").keySet())for(String pair:key.split(",")){
            String[] kv=pair.split("=",2);if(kv.length==2)allowed.computeIfAbsent(kv[0],k->new HashSet<>()).addAll(List.of(kv[1].split("\\|")));
        }
        if(root.has("multipart"))for(JsonElement part:root.getAsJsonArray("multipart")){
            JsonObject p=part.getAsJsonObject();if(p.has("when"))collectProperties(p.getAsJsonObject("when"),allowed);
        }
        state.properties().forEach((key,value)->{
            if(allowed.containsKey(key)&&!allowed.get(key).contains(value))warn("Invalid asset-defined state "+state.id()+"["+key+"="+value+"]");
        });
    }
    private static void collectProperties(JsonObject object,Map<String,Set<String>> allowed){
        for(var e:object.entrySet())if(e.getValue().isJsonArray())
            for(JsonElement child:e.getValue().getAsJsonArray())collectProperties(child.getAsJsonObject(),allowed);
        else allowed.computeIfAbsent(e.getKey(),k->new HashSet<>()).addAll(List.of(e.getValue().getAsString().split("\\|")));
    }

    private JsonObject model(String id,Set<String> chain) throws IOException {
        id=id.contains(":")?id:"minecraft:"+id;
        if(models.containsKey(id))return models.get(id);
        if(chain.size()>32||!chain.add(id))throw new IOException("Cyclic/deep model parent: "+id);
        JsonObject child=json(id,"models"), result=new JsonObject();
        if(child.has("parent")) {
            String parent=child.get("parent").getAsString();
            if(parent.contains("builtin/"))throw new IOException("Built-in Java renderer: "+parent);
            result=model(parent,chain).deepCopy();
        }
        JsonObject inherited=result.has("textures")?result.getAsJsonObject("textures").deepCopy():new JsonObject();
        if(child.has("textures"))child.getAsJsonObject("textures").entrySet().forEach(e->inherited.add(e.getKey(),e.getValue()));
        for(var entry:child.entrySet())if(!entry.getKey().equals("parent"))result.add(entry.getKey(),entry.getValue());
        result.add("textures",inherited);
        if(result.has("loader"))throw new IOException("Unsupported custom model loader: "+result.get("loader"));
        chain.remove(id);models.put(id,result);return result;
    }

    private Texture texture(String reference,JsonObject variables) throws IOException {
        Set<String> seen=new HashSet<>();
        while(reference.startsWith("#")){
            if(!seen.add(reference)||!variables.has(reference.substring(1)))throw new IOException("Unresolved/cyclic texture variable "+reference);
            reference=variables.get(reference.substring(1)).getAsString();
        }
        String id=reference.contains(":")?reference:"minecraft:"+reference;
        if(textures.containsKey(id))return textures.get(id);
        try{
            String path=AssetRepository.path(id,"textures",".png");
            Texture texture=Texture.decode(assets.read(path).orElseThrow(()->new IOException("Missing texture: "+id)),
                    assets.read(path+".mcmeta").isPresent());
            if(texturePixels+(long)texture.width()*texture.height()>32_000_000)throw new IOException("Texture memory budget exceeded");
            texturePixels+=(long)texture.width()*texture.height();textures.put(id,texture);return texture;
        }catch(IOException e){warn(e.getMessage());Texture fallback=Texture.missing();textures.put(id,fallback);return fallback;}
    }

    private void bake(JsonObject apply,BlockState state,List<BlockModel.Quad> output) throws IOException {
        String id=apply.get("model").getAsString();JsonObject model=model(id,new HashSet<>());
        if(!model.has("elements"))return;
        int x=apply.has("x")?apply.get("x").getAsInt():0,y=apply.has("y")?apply.get("y").getAsInt():0;
        boolean uvlock=apply.has("uvlock")&&apply.get("uvlock").getAsBoolean();
        for(JsonElement item:model.getAsJsonArray("elements")){
            JsonObject element=item.getAsJsonObject();
            Vec3 from=ModelGeometry.vector(element.getAsJsonArray("from")),to=ModelGeometry.vector(element.getAsJsonArray("to"));
            if(output.size()>4096)throw new IOException("Model exceeds 4096 faces");
            for(var faceEntry:element.getAsJsonObject("faces").entrySet()){
                int side=ModelGeometry.SIDES.indexOf(faceEntry.getKey());if(side<0)continue;
                JsonObject face=faceEntry.getValue().getAsJsonObject();
                List<Vec3> vertices=ModelGeometry.vertices(side,from,to);
                if(element.has("rotation")){
                    JsonObject rotation=element.getAsJsonObject("rotation");
                    Vec3 origin=rotation.has("origin")?ModelGeometry.vector(rotation.getAsJsonArray("origin")):new Vec3(0,0,0);
                    String axis=rotation.get("axis").getAsString();double angle=rotation.get("angle").getAsDouble();
                    boolean rescale=rotation.has("rescale")&&rotation.get("rescale").getAsBoolean();
                    vertices=vertices.stream().map(v->ModelGeometry.rotate(v,origin,axis,angle,rescale)).toList();
                }
                List<Vec3> rotated=vertices.stream().map(v->ModelGeometry.variant(v,x,y)).toList();
                double[] uv=ModelGeometry.uv(face,side,from,to);
                // Align the face's canonical corner ordering after quarter-turn blockstate rotations.
                if(uvlock) {
                    int rotatedSide=ModelGeometry.rotatedSide(side,x,y);
                    if(rotatedSide>=0){
                        List<Vec3> canonical=ModelGeometry.vertices(rotatedSide,new Vec3(0,0,0),new Vec3(1,1,1));
                        List<Vec3> unit=ModelGeometry.vertices(side,new Vec3(0,0,0),new Vec3(1,1,1));
                        Vec3 first=ModelGeometry.variant(unit.getFirst(),x,y);
                        int steps=0;double closest=Double.MAX_VALUE;
                        for(int i=0;i<4;i++){Vec3 d=first.subtract(canonical.get(i));double distance=d.dot(d);if(distance<closest){closest=distance;steps=i;}}
                        uv=ModelGeometry.rotateUv(uv,steps);
                    }
                }
                Texture texture;
                try{texture=texture(face.has("texture")?face.get("texture").getAsString():"#missing",model.getAsJsonObject("textures"));}
                catch(IOException e){warn(id+": "+e.getMessage());texture=Texture.missing();}
                int tint=face.has("tintindex")&&face.get("tintindex").getAsInt()>=0?
                        state.id().contains("redstone")?0xce2020:state.id().contains("water")?0x75a8e0:0x91bd59:0xffffff;
                int cull=face.has("cullface")?ModelGeometry.rotatedSide(ModelGeometry.SIDES.indexOf(face.get("cullface").getAsString()),x,y):-1;
                boolean shade=!element.has("shade")||element.get("shade").getAsBoolean();
                output.add(new BlockModel.Quad(rotated,uv,texture,tint,cull,shade));
            }
        }
    }
}
