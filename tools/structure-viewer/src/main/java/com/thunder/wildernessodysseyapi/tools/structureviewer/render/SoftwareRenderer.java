package com.thunder.wildernessodysseyapi.tools.structureviewer.render;

import com.thunder.wildernessodysseyapi.tools.structureviewer.assets.Texture;
import java.awt.*;
import java.awt.image.*;
import java.util.*;
import java.util.List;

/** Perspective-correct textured rasterization with depth-tested block selection and block outlines. */
public final class SoftwareRenderer {
    private static final double NEAR=.04;
    private record Vertex(Vec3 position,double u,double v){
        Vertex interpolate(Vertex b,double t){return new Vertex(position.add(b.position.subtract(position).multiply(t)),u+(b.u-u)*t,v+(b.v-v)*t);}
    }
    private record Visible(BlockMesh.Face face,Vertex[] polygon,double distance,double shade){}

    /** Compatibility overload for the Phase 1 geometry tests. */
    public Frame render(BlockMesh mesh,Camera.View camera,int width,int height,int selected,boolean wireframe,boolean bounds){
        return render(mesh,camera,width,height,selected,wireframe,bounds,false,true);
    }

    /** Draws all retained model geometry. Quality is controlled by the requested framebuffer size. */
    public Frame render(BlockMesh mesh,Camera.View camera,int width,int height,int selected,boolean wireframe,
                        boolean bounds,boolean blockEdges,boolean textures){
        BufferedImage image=new BufferedImage(width,height,BufferedImage.TYPE_INT_RGB);
        int[] pixels=((DataBufferInt)image.getRaster().getDataBuffer()).getData();
        Arrays.fill(pixels,0x18232e);
        float[] depth=new float[pixels.length];int[] ids=new int[pixels.length];Arrays.fill(ids,-1);
        double focal=height*.9;
        List<Visible> transparent=new ArrayList<>();
        if(mesh!=null)for(var face:mesh.faces()){
            var quad=face.quad();var p=face.position();Vec3 origin=new Vec3(p.x(),p.y(),p.z());
            Vec3 a=quad.vertices().get(0),b=quad.vertices().get(1),c=quad.vertices().get(2);
            Vec3 ab=b.subtract(a),ac=c.subtract(a);
            Vec3 normal=new Vec3(ab.y()*ac.z()-ab.z()*ac.y(),ab.z()*ac.x()-ab.x()*ac.z(),ab.x()*ac.y()-ab.y()*ac.x());
            if(normal.dot(camera.position().subtract(a.add(origin)))<=0)continue;
            Vec3 center=camera.transform(origin.add(new Vec3(.5,.5,.5)));
            if(center.z()<-3||center.z()>20000
                    ||Math.abs(center.x())>(center.z()+4)*width/(2*focal)+4
                    ||Math.abs(center.y())>(center.z()+4)*height/(2*focal)+4)continue;
            Vertex[] polygon=new Vertex[4];
            for(int i=0;i<4;i++)polygon[i]=new Vertex(camera.transform(quad.vertices().get(i).add(origin)),quad.uv()[i*2],quad.uv()[i*2+1]);
            polygon=clip(polygon);if(polygon.length<3)continue;
            double length=Math.sqrt(normal.dot(normal));
            double shade=quad.shade()? .65+.25*Math.max(0,normal.y()/length)+.1*Math.abs(normal.z()/length):1;
            Visible visible=new Visible(face,polygon,center.z(),shade);
            if(textures&&quad.texture().translucent())transparent.add(visible);
            else draw(visible,focal,width,height,selected,wireframe,textures,pixels,depth,ids);
        }
        // Translucent faces blend back-to-front but remain occluded by the opaque depth buffer.
        transparent.sort(Comparator.comparingDouble(Visible::distance).reversed());
        for(var visible:transparent)draw(visible,focal,width,height,selected,wireframe,textures,pixels,depth,ids);
        if(blockEdges)outlines(pixels,ids,width,height,selected);
        if(bounds&&mesh!=null)drawBounds(image,mesh,camera,focal);
        return new Frame(image,ids);
    }

    private static void draw(Visible visible,double focal,int width,int height,int selected,boolean wire,boolean textured,
                             int[] pixels,float[] depth,int[] ids){
        var p=visible.polygon;
        for(int i=1;i+1<p.length;i++)triangle(p[0],p[i],p[i+1],visible,focal,width,height,selected,wire,textured,pixels,depth,ids);
    }

    private static Vertex[] clip(Vertex[] polygon){
        Vertex[] output=new Vertex[8];int count=0;Vertex previous=polygon[polygon.length-1];
        for(Vertex current:polygon){
            if((current.position.z()>=NEAR)!=(previous.position.z()>=NEAR)){
                double t=(NEAR-previous.position.z())/(current.position.z()-previous.position.z());
                output[count++]=previous.interpolate(current,t);
            }
            if(current.position.z()>=NEAR)output[count++]=current;
            previous=current;
        }
        return Arrays.copyOf(output,count);
    }

    private static void triangle(Vertex a,Vertex b,Vertex c,Visible visible,double focal,int width,int height,int selected,
                                 boolean wire,boolean textured,int[] pixels,float[] depth,int[] ids){
        double za=1/a.position.z(),zb=1/b.position.z(),zc=1/c.position.z();
        double ax=width*.5+a.position.x()*focal*za,ay=height*.5-a.position.y()*focal*za;
        double bx=width*.5+b.position.x()*focal*zb,by=height*.5-b.position.y()*focal*zb;
        double cx=width*.5+c.position.x()*focal*zc,cy=height*.5-c.position.y()*focal*zc;
        double area=edge(ax,ay,bx,by,cx,cy);if(Math.abs(area)<.00001)return;
        int minX=Math.max(0,(int)Math.floor(Math.min(ax,Math.min(bx,cx)))),maxX=Math.min(width-1,(int)Math.ceil(Math.max(ax,Math.max(bx,cx))));
        int minY=Math.max(0,(int)Math.floor(Math.min(ay,Math.min(by,cy)))),maxY=Math.min(height-1,(int)Math.ceil(Math.max(ay,Math.max(by,cy))));
        var face=visible.face;Texture texture=face.quad().texture();int id=face.blockIndex();
        for(int y=minY;y<=maxY;y++)for(int x=minX;x<=maxX;x++){
            double u=edge(bx,by,cx,cy,x+.5,y+.5)/area,v=edge(cx,cy,ax,ay,x+.5,y+.5)/area,w=1-u-v;
            if(u<-.000001||v<-.000001||w<-.000001)continue;
            float z=(float)(u*za+v*zb+w*zc);int offset=y*width+x;if(z<=depth[offset])continue;
            int texel=textured?texture.sample((u*a.u*za+v*b.u*zb+w*c.u*zc)/z,(u*a.v*za+v*b.v*zb+w*c.v*zc)/z):0xff000000|face.color();
            int alpha=texel>>>24;if(alpha<12)continue;
            int color=tint(texel,face.quad().tint(),visible.shade);
            if(id==selected)color=blend(color,0x44eacf,.28);
            if(wire&&Math.min(u,Math.min(v,w))<.025)color=0xc8fff0;
            pixels[offset]=alpha<255?blend(pixels[offset],color,alpha/255.0):color;
            depth[offset]=z;ids[offset]=id;
        }
    }
    private static int tint(int color,int tint,double shade){
        return ((int)(((color>>16)&255)*((tint>>16)&255)/255.0*shade)<<16)
                |((int)(((color>>8)&255)*((tint>>8)&255)/255.0*shade)<<8)|(int)((color&255)*(tint&255)/255.0*shade);
    }
    private static int blend(int background,int foreground,double a){
        return ((int)(((background>>16)&255)*(1-a)+((foreground>>16)&255)*a)<<16)
                |((int)(((background>>8)&255)*(1-a)+((foreground>>8)&255)*a)<<8)
                |(int)((background&255)*(1-a)+(foreground&255)*a);
    }
    private static double edge(double ax,double ay,double bx,double by,double px,double py){return(px-ax)*(by-ay)-(py-ay)*(bx-ax);}
    private static void outlines(int[] pixels,int[] ids,int width,int height,int selected){
        for(int y=1;y<height-1;y++)for(int x=1;x<width-1;x++){
            int i=y*width+x,id=ids[i];if(id<0)continue;
            if(ids[i-1]!=id||ids[i+1]!=id||ids[i-width]!=id||ids[i+width]!=id)
                pixels[i]=id==selected?0x55ffe0:blend(pixels[i],0x071019,.5);
        }
    }

    private static void drawBounds(BufferedImage image,BlockMesh mesh,Camera.View camera,double focal){
        Graphics2D g=image.createGraphics();g.setColor(new Color(0x57b7b8));var size=mesh.data().size();
        Vec3[] corners=new Vec3[8];
        for(int i=0;i<8;i++)corners[i]=camera.transform(new Vec3((i&1)==0?0:size.x(),(i&2)==0?0:size.y(),(i&4)==0?0:size.z()));
        for(int i=0;i<8;i++)for(int bit:new int[]{1,2,4})if((i&bit)==0){
            Vec3 a=corners[i],b=corners[i|bit];if(a.z()<NEAR||b.z()<NEAR)continue;
            g.drawLine((int)(image.getWidth()/2.0+a.x()*focal/a.z()),(int)(image.getHeight()/2.0-a.y()*focal/a.z()),
                    (int)(image.getWidth()/2.0+b.x()*focal/b.z()),(int)(image.getHeight()/2.0-b.y()*focal/b.z()));
        }
        g.dispose();
    }

    /** Matching color and source-block buffers for exact selection at any render resolution. */
    public record Frame(BufferedImage image,int[] blockIds){
        /** Returns the source block or -1 for background. */
        public int pick(int x,int y){return x<0||y<0||x>=image.getWidth()||y>=image.getHeight()?-1:blockIds[y*image.getWidth()+x];}
    }
}
