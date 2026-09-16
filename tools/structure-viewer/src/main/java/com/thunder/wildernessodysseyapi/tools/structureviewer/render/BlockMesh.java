package com.thunder.wildernessodysseyapi.tools.structureviewer.render;

import com.thunder.wildernessodysseyapi.tools.structureviewer.assets.*;
import com.thunder.wildernessodysseyapi.tools.structureviewer.model.StructureData;
import com.thunder.wildernessodysseyapi.tools.structureviewer.model.StructureData.Position;
import java.util.*;

/** Cached exposed model faces; source block identities are preserved at every quality setting. */
public record BlockMesh(StructureData data,List<Face> faces,int duplicatePositions,List<String> diagnostics,
                        int resolvedStates,int fallbackStates,List<String> assetSources) {
    static final int[][] NORMALS={{-1,0,0},{1,0,0},{0,-1,0},{0,1,0},{0,0,-1},{0,0,1}};

    /** Basic cube path retained for isolated geometry tests and unavailable assets. */
    public static BlockMesh build(StructureData data,boolean showAir,int layer){
        return build(data,showAir,layer,null,List.of());
    }

    /** Builds an asset-backed mesh without culling neighbors behind partial or transparent shapes. */
    public static BlockMesh build(StructureData data,boolean showAir,int layer,BlockModelResolver resolver,List<String> sources){
        var stateModels=new HashMap<StructureData.BlockState,BlockModel>();
        for(var palette:data.palettes())for(var state:palette)
            stateModels.computeIfAbsent(state,s->resolver==null?cube(s):resolver.resolve(s));
        var occupied=new HashMap<Position,Integer>();
        int duplicates=0;
        for(int i=0;i<data.blocks().size();i++){
            if((i&4095)==0&&Thread.currentThread().isInterrupted())throw new java.util.concurrent.CancellationException();
            var b=data.blocks().get(i);
            if((layer<0||b.position().y()==layer)&&(showAir||!data.state(b).isAir()))
                if(occupied.put(b.position(),i)!=null)duplicates++;
        }
        List<Face> faces=new ArrayList<>();
        BlockModel missing=cube(StructureData.BlockState.MISSING);
        for(var entry:occupied.entrySet()){
            if(Thread.currentThread().isInterrupted())throw new java.util.concurrent.CancellationException();
            var p=entry.getKey();int index=entry.getValue();
            var block=data.blocks().get(index);var state=data.state(block);
            BlockModel model=stateModels.getOrDefault(state,missing);
            for(var quad:model.quads()){
                int side=quad.cullSide();
                if(side>=0){
                    int[] normal=NORMALS[side];Integer neighbor=occupied.get(p.offset(normal[0],normal[1],normal[2]));
                    if(neighbor!=null){
                        var adjacent=data.state(data.blocks().get(neighbor));
                        BlockModel other=stateModels.getOrDefault(adjacent,missing);
                        if(other.occludes() || (adjacent.equals(state)&&!model.occludes()&&model.quads().size()==6))continue;
                    }
                }
                faces.add(new Face(p,index,side,BlockAppearance.color(state),quad));
            }
        }
        return new BlockMesh(data,List.copyOf(faces),duplicates,resolver==null?List.of():resolver.diagnostics(),
                resolver==null?0:resolver.resolvedCount(),resolver==null?0:resolver.missingCount(),sources);
    }

    private static BlockModel cube(StructureData.BlockState state){
        List<BlockModel.Quad> quads=new ArrayList<>();
        int[][][] points={
                {{0,1,1},{0,1,0},{0,0,0},{0,0,1}},{{1,1,0},{1,1,1},{1,0,1},{1,0,0}},
                {{0,0,0},{1,0,0},{1,0,1},{0,0,1}},{{0,1,1},{1,1,1},{1,1,0},{0,1,0}},
                {{1,1,0},{0,1,0},{0,0,0},{1,0,0}},{{0,1,1},{1,1,1},{1,0,1},{0,0,1}}};
        for(int side=0;side<6;side++){
            List<Vec3> v=new ArrayList<>();for(int[] p:points[side])v.add(new Vec3(p[0],p[1],p[2]));
            quads.add(new BlockModel.Quad(v,new double[]{0,0,16,0,16,16,0,16},
                    Texture.solid(BlockAppearance.color(state)),0xffffff,side,true));
        }
        return new BlockModel(quads,true,true);
    }

    /** One local-space model face located at an original structure coordinate. */
    public record Face(Position position,int blockIndex,int side,int color,BlockModel.Quad quad){}
}
