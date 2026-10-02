package com.thunder.wildernessodysseyapi.tools.structureviewer.render;

import com.thunder.wildernessodysseyapi.tools.structureviewer.assets.Texture;
import java.awt.*;
import java.awt.image.*;
import java.util.*;
import java.util.List;

/** Perspective-correct textured rasterization with depth-tested block selection and block outlines. */
public final class SoftwareRenderer {
    private static final double NEAR=.04;
    private static final class Vertex {
        double x,y,z,u,v;
        Vertex(double x,double y,double z,double u,double v){set(x,y,z,u,v);}
        void set(double x,double y,double z,double u,double v){this.x=x;this.y=y;this.z=z;this.u=u;this.v=v;}
        Vertex interpolate(Vertex b,double t){return new Vertex(x+(b.x-x)*t,y+(b.y-y)*t,z+(b.z-z)*t,u+(b.u-u)*t,v+(b.v-v)*t);}
        Vertex copy(){return new Vertex(x,y,z,u,v);}
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
        for(int y=0;y<height;y++)Arrays.fill(pixels,y*width,(y+1)*width,blend(0x182536,0x101820,(double)y/height));
        float[] depth=new float[pixels.length];int[] ids=new int[pixels.length];Arrays.fill(ids,-1);
        double focal=height*.9;
        List<Visible> transparent=new ArrayList<>();
        Vec3 eye=camera.position(),right=camera.right(),up=camera.up(),forward=camera.forward();
        // Opaque faces are consumed immediately; only translucent faces need retained vertices.
        Vertex[] scratch={new Vertex(0,0,0,0,0),new Vertex(0,0,0,0,0),new Vertex(0,0,0,0,0),new Vertex(0,0,0,0,0)};
        if(mesh!=null)for(var face:mesh.faces()){
            if(Thread.currentThread().isInterrupted())throw new java.util.concurrent.CancellationException();
            var quad=face.quad();var p=face.position();
            Vec3 a=quad.vertices().getFirst(),normal=quad.normal();
            double dx=p.x()-eye.x(),dy=p.y()-eye.y(),dz=p.z()-eye.z();
            if(normal.x()*(-dx-a.x())+normal.y()*(-dy-a.y())+normal.z()*(-dz-a.z())<=0)continue;
            double cx=dx+.5,cy=dy+.5,cz=dz+.5;
            double z=cx*forward.x()+cy*forward.y()+cz*forward.z();
            if(z<-3||z>20000
                    ||Math.abs(cx*right.x()+cy*right.y()+cz*right.z())>(z+4)*width/(2*focal)+4
                    ||Math.abs(cx*up.x()+cy*up.y()+cz*up.z())>(z+4)*height/(2*focal)+4)continue;
            Vertex[] polygon=scratch;
            boolean needsClip=false;
            for(int i=0;i<4;i++){
                Vec3 v=quad.vertices().get(i);double vx=dx+v.x(),vy=dy+v.y(),vz=dz+v.z();
                double depthValue=vx*forward.x()+vy*forward.y()+vz*forward.z();
                needsClip|=depthValue<NEAR;
                polygon[i].set(vx*right.x()+vy*right.y()+vz*right.z(),
                        vx*up.x()+vy*up.y()+vz*up.z(),depthValue,quad.uv()[i*2],quad.uv()[i*2+1]);
            }
            if(needsClip){polygon=clip(polygon);if(polygon.length<3)continue;}
            Vec3 middle=quad.center();
            double distance=(dx+middle.x())*forward.x()+(dy+middle.y())*forward.y()+(dz+middle.z())*forward.z();
            if(textures&&quad.texture().translucent()){
                Vertex[] retained=new Vertex[polygon.length];
                for(int i=0;i<polygon.length;i++)retained[i]=polygon[i].copy();
                transparent.add(new Visible(face,retained,distance,quad.lighting()));
            }else draw(face,polygon,quad.lighting(),focal,width,height,selected,wireframe,textures,pixels,depth,ids);
        }
        // Translucent faces blend back-to-front but remain occluded by the opaque depth buffer.
        transparent.sort(Comparator.comparingDouble(Visible::distance).reversed());
        for(var visible:transparent)draw(visible.face,visible.polygon,visible.shade,focal,width,height,selected,wireframe,textures,pixels,depth,ids);
        if(blockEdges)outlines(pixels,ids,width,height,selected);
        if(bounds&&mesh!=null)drawBounds(image,mesh,camera,focal);
        return new Frame(image,ids);
    }

    private static void draw(BlockMesh.Face face,Vertex[] p,double shade,double focal,int width,int height,int selected,boolean wire,boolean textured,
                             int[] pixels,float[] depth,int[] ids){
        for(int i=1;i+1<p.length;i++)triangle(p[0],p[i],p[i+1],face,shade,focal,width,height,selected,wire,textured,pixels,depth,ids);
    }

    private static Vertex[] clip(Vertex[] polygon){
        Vertex[] output=new Vertex[8];int count=0;Vertex previous=polygon[polygon.length-1];
        for(Vertex current:polygon){
            if((current.z>=NEAR)!=(previous.z>=NEAR)){
                double t=(NEAR-previous.z)/(current.z-previous.z);
                output[count++]=previous.interpolate(current,t);
            }
            if(current.z>=NEAR)output[count++]=current;
            previous=current;
        }
        return Arrays.copyOf(output,count);
    }

    private static void triangle(Vertex a,Vertex b,Vertex c,BlockMesh.Face face,double shade,double focal,int width,int height,int selected,
                                 boolean wire,boolean textured,int[] pixels,float[] depth,int[] ids){
        double za=1/a.z,zb=1/b.z,zc=1/c.z;
        double ax=width*.5+a.x*focal*za,ay=height*.5-a.y*focal*za;
        double bx=width*.5+b.x*focal*zb,by=height*.5-b.y*focal*zb;
        double cx=width*.5+c.x*focal*zc,cy=height*.5-c.y*focal*zc;
        double area=edge(ax,ay,bx,by,cx,cy);if(Math.abs(area)<.00001)return;
        int minX=Math.max(0,(int)Math.floor(Math.min(ax,Math.min(bx,cx)))),maxX=Math.min(width-1,(int)Math.ceil(Math.max(ax,Math.max(bx,cx))));
        int minY=Math.max(0,(int)Math.floor(Math.min(ay,Math.min(by,cy)))),maxY=Math.min(height-1,(int)Math.ceil(Math.max(ay,Math.max(by,cy))));
        Texture texture=face.quad().texture();int id=face.blockIndex();
        double du=(cy-by)/area,dv=(ay-cy)/area;
        for(int y=minY;y<=maxY;y++){
            double rowU=edge(bx,by,cx,cy,minX+.5,y+.5)/area,rowV=edge(cx,cy,ax,ay,minX+.5,y+.5)/area;
            for(int x=minX;x<=maxX;x++){
                double u=rowU+(x-minX)*du,v=rowV+(x-minX)*dv,w=1-u-v;
                if(u<-.000001||v<-.000001||w<-.000001)continue;
                float z=(float)(u*za+v*zb+w*zc);int offset=y*width+x;if(z<=depth[offset])continue;
                int texel=textured?texture.sample((u*a.u*za+v*b.u*zb+w*c.u*zc)/z,(u*a.v*za+v*b.v*zb+w*c.v*zc)/z):0xff000000|face.color();
                int alpha=texel>>>24;if(alpha<12)continue;
                int color=tint(texel,face.quad().tint(),shade);
                if(id==selected)color=blend(color,0x44eacf,.28);
                if(wire&&Math.min(u,Math.min(v,w))<.025)color=0xc8fff0;
                pixels[offset]=alpha<255?blend(pixels[offset],color,alpha/255.0):color;
                depth[offset]=z;ids[offset]=id;
            }
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
