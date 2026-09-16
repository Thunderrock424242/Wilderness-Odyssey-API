package com.thunder.wildernessodysseyapi.tools.structureviewer.assets;

import com.google.gson.*;
import com.thunder.wildernessodysseyapi.tools.structureviewer.render.Vec3;
import java.util.*;

/** Converts Minecraft cuboid elements, face UVs, and element/blockstate rotations into local quads. */
final class ModelGeometry {
    static final List<String> SIDES = List.of("west","east","down","up","north","south");
    static final int[][] NORMALS = {{-1,0,0},{1,0,0},{0,-1,0},{0,1,0},{0,0,-1},{0,0,1}};
    private static final int[][][] CORNERS = {
            {{0,1,1},{0,1,0},{0,0,0},{0,0,1}},{{1,1,0},{1,1,1},{1,0,1},{1,0,0}},
            {{0,0,0},{1,0,0},{1,0,1},{0,0,1}},{{0,1,1},{1,1,1},{1,1,0},{0,1,0}},
            {{1,1,0},{0,1,0},{0,0,0},{1,0,0}},{{0,1,1},{1,1,1},{1,0,1},{0,0,1}}};
    private ModelGeometry() {}

    static List<Vec3> vertices(int side, Vec3 from, Vec3 to) {
        List<Vec3> result = new ArrayList<>(4);
        for (int[] p : CORNERS[side]) result.add(new Vec3(p[0] == 0 ? from.x() : to.x(),
                p[1] == 0 ? from.y() : to.y(),p[2] == 0 ? from.z() : to.z()));
        return result;
    }

    static Vec3 vector(JsonArray a) {
        return new Vec3(a.get(0).getAsDouble()/16,a.get(1).getAsDouble()/16,a.get(2).getAsDouble()/16);
    }

    static Vec3 rotate(Vec3 point, Vec3 origin, String axis, double degrees, boolean rescale) {
        Vec3 p = point.subtract(origin);
        double c = Math.cos(Math.toRadians(degrees)), s = Math.sin(Math.toRadians(degrees));
        double scale = rescale ? 1 / Math.max(0.0001, Math.abs(c)) : 1;
        return (switch (axis) {
            case "x" -> new Vec3(p.x(),(p.y()*c-p.z()*s)*scale,(p.y()*s+p.z()*c)*scale);
            case "y" -> new Vec3((p.x()*c+p.z()*s)*scale,p.y(),(-p.x()*s+p.z()*c)*scale);
            case "z" -> new Vec3((p.x()*c-p.y()*s)*scale,(p.x()*s+p.y()*c)*scale,p.z());
            default -> throw new IllegalArgumentException("Unsupported rotation axis " + axis);
        }).add(origin);
    }

    static Vec3 variant(Vec3 v, int x, int y) {
        Vec3 center = new Vec3(.5,.5,.5);
        return rotate(rotate(v,center,"x",-x,false),center,"y",-y,false);
    }

    static int rotatedSide(int side, int x, int y) {
        if (side < 0) return -1;
        int[] n = NORMALS[side];
        Vec3 v = variant(new Vec3(.5+n[0],.5+n[1],.5+n[2]),x,y).subtract(new Vec3(.5,.5,.5));
        for(int i=0;i<6;i++) if(v.dot(new Vec3(NORMALS[i][0],NORMALS[i][1],NORMALS[i][2]))>.999) return i;
        return -1;
    }

    static double[] uv(JsonObject face, int side, Vec3 from, Vec3 to) {
        double x1=from.x()*16,y1=from.y()*16,z1=from.z()*16,x2=to.x()*16,y2=to.y()*16,z2=to.z()*16;
        double[] rect = switch(side) {
            case 0 -> new double[]{z1,16-y2,z2,16-y1};
            case 1 -> new double[]{16-z2,16-y2,16-z1,16-y1};
            case 2 -> new double[]{x1,16-z2,x2,16-z1};
            case 3 -> new double[]{x1,z1,x2,z2};
            case 4 -> new double[]{16-x2,16-y2,16-x1,16-y1};
            default -> new double[]{x1,16-y2,x2,16-y1};
        };
        if(face.has("uv")) for(int i=0;i<4;i++) rect[i]=face.getAsJsonArray("uv").get(i).getAsDouble();
        double[] uv={rect[0],rect[1],rect[2],rect[1],rect[2],rect[3],rect[0],rect[3]};
        int rotation=face.has("rotation")?face.get("rotation").getAsInt()/90:0;
        return rotateUv(uv,rotation);
    }

    static double[] rotateUv(double[] uv,int steps) {
        double[] result=new double[8];
        for(int i=0;i<4;i++){int source=Math.floorMod(i+steps,4);result[i*2]=uv[source*2];result[i*2+1]=uv[source*2+1];}
        return result;
    }

    static BlockModel cube(Texture texture) {
        List<BlockModel.Quad> quads=new ArrayList<>();
        for(int side=0;side<6;side++) quads.add(new BlockModel.Quad(vertices(side,new Vec3(0,0,0),new Vec3(1,1,1)),
                new double[]{0,0,16,0,16,16,0,16},texture,0xffffff,side,true));
        return new BlockModel(quads,texture.opaque(),true);
    }
}
