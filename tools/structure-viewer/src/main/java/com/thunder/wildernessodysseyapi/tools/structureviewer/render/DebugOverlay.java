package com.thunder.wildernessodysseyapi.tools.structureviewer.render;

import com.thunder.wildernessodysseyapi.tools.structureviewer.model.StructureData;
import java.awt.*;
import java.util.*;
import java.util.List;

/** Bounded entity/block-entity markers; the preview never runs entity or mission behavior. */
public final class DebugOverlay {
    private DebugOverlay(){}
    /** Captured marker location and inspectable label. */
    public record Marker(Vec3 position,String label,boolean entity){}
    /** Prepares markers once while loading, instead of scanning all blocks during rendering. */
    public static List<Marker> prepare(StructureData data){
        List<Marker> markers=new ArrayList<>();
        for(var b:data.blocks()){
            if(markers.size()>=10000)break;
            String id=data.state(b).id();
            if(b.blockEntity()!=null||id.equals("minecraft:jigsaw")||id.equals("minecraft:structure_block")){
                var p=b.position();markers.add(new Marker(new Vec3(p.x()+.5,p.y()+.5,p.z()+.5),id,false));
            }
        }
        for(var entity:data.entities()){
            if(markers.size()>=10000)break;
            var pos=entity.compound().get("pos");
            if(pos==null||pos.list().size()!=3)continue;
            try{
                Vec3 p=new Vec3(((Number)pos.list().get(0).value()).doubleValue(),((Number)pos.list().get(1).value()).doubleValue(),
                        ((Number)pos.list().get(2).value()).doubleValue());
                markers.add(new Marker(p,"Entity",true));
            }catch(ClassCastException ignored){/* Malformed entity metadata remains available in diagnostics. */}
        }
        return List.copyOf(markers);
    }
    /** Draws a limited diagnostic overlay; markers intentionally remain visible through geometry. */
    public static void draw(Graphics2D g,int width,int height,Camera.View camera,List<Marker> markers,
                            boolean entities,boolean blockEntities,boolean labels){
        int drawn=0;
        for(Marker marker:markers){
            if(marker.entity?!entities:!blockEntities)continue;
            Vec3 p=camera.transform(marker.position);if(p.z()<.05)continue;
            int x=(int)(width*.5+p.x()*height*.9/p.z()),y=(int)(height*.5-p.y()*height*.9/p.z());
            if(x<0||x>=width||y<0||y>=height)continue;
            g.setColor(marker.entity?new Color(0xf7cb66):new Color(0x68e3ed));
            g.drawOval(x-3,y-3,6,6);
            if(labels&&drawn<30)g.drawString(marker.label+" "+(int)marker.position.x()+"/"+(int)marker.position.y()+"/"+(int)marker.position.z(),x+6,y-4);
            if(++drawn>=1000)break;
        }
    }
}
