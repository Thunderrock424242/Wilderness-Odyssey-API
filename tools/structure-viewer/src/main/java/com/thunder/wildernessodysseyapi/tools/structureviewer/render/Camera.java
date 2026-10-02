package com.thunder.wildernessodysseyapi.tools.structureviewer.render;

import com.thunder.wildernessodysseyapi.tools.structureviewer.model.StructureData.Position;

/** Free-flight/orbit camera. UI thread owns mutation; immutable views are sent to the renderer. */
public final class Camera {
    private Vec3 position = new Vec3(12, 12, -12);
    private Vec3 target = new Vec3(0, 0, 0);
    private double yaw = -0.65, pitch = -0.4, distance = 20, speed = 8;
    private boolean orbit = true;

    /** Recenters while preserving the current camera mode. */
    public void focus(Position size) {
        focus(size.x(),size.y(),size.z(),new Vec3(0,0,0));
    }

    private void focus(double x,double y,double z,Vec3 origin) {
        target = origin.add(new Vec3(x/2,y/2,z/2));
        distance = Math.max(6,Math.sqrt(x*x+y*y+z*z)*1.35);
        yaw = -0.65;
        pitch = -0.4;
        position = target.subtract(forward().multiply(distance));
        speed = Math.max(2, Math.min(64, distance / 8));
    }

    /** Focuses on an individual block without changing the chosen camera mode. */
    public void focusBlock(Position block) {
        target = new Vec3(block.x() + .5, block.y() + .5, block.z() + .5);
        distance = 4;
        position = target.subtract(forward().multiply(distance));
        speed = 2;
    }

    /** Frames occupied geometry instead of empty padding in large structure dimensions. */
    public void focus(BlockMesh mesh) {
        if(mesh.faces().isEmpty()){focus(mesh.data().size());return;}
        double minX=Double.POSITIVE_INFINITY,minY=Double.POSITIVE_INFINITY,minZ=Double.POSITIVE_INFINITY;
        double maxX=Double.NEGATIVE_INFINITY,maxY=Double.NEGATIVE_INFINITY,maxZ=Double.NEGATIVE_INFINITY;
        for(var face:mesh.faces()){
            var p=face.position();minX=Math.min(minX,p.x());minY=Math.min(minY,p.y());minZ=Math.min(minZ,p.z());
            maxX=Math.max(maxX,p.x()+1.0);maxY=Math.max(maxY,p.y()+1.0);maxZ=Math.max(maxZ,p.z()+1.0);
        }
        focus(maxX-minX,maxY-minY,maxZ-minZ,new Vec3(minX,minY,minZ));
    }

    /** Switches camera mode without jumping the current eye position. */
    public void setOrbit(boolean value) {
        if (value && !orbit) target = position.add(forward().multiply(distance));
        orbit = value;
    }

    /** Rotates using relative mouse movement; pitch avoids the vertical singularity. */
    public void look(double dx, double dy) {
        yaw += dx * 0.006;
        pitch = Math.max(-1.55, Math.min(1.55, pitch - dy * 0.006));
        if (orbit) position = target.subtract(forward().multiply(distance));
    }

    /** Wheel adjusts free-flight speed or orbit distance. */
    public void wheel(double rotation) {
        if (orbit) {
            distance = Math.max(0.5, Math.min(100000, distance * Math.pow(1.15, rotation)));
            position = target.subtract(forward().multiply(distance));
        } else speed = Math.max(0.1, Math.min(512, speed * Math.pow(1.2, -rotation)));
    }

    /** Moves through geometry without collision. Diagonal motion has the same speed as axial motion. */
    public void move(double forward, double right, double up, boolean precision, double seconds) {
        if (orbit) return;
        double length = Math.sqrt(forward * forward + right * right + up * up);
        if (length == 0) return;
        Vec3 delta = forward().multiply(forward).add(right().multiply(right)).add(new Vec3(0, up, 0));
        position = position.add(delta.multiply(speed * (precision ? 0.15 : 1) * Math.min(seconds, 0.1) / length));
    }

    private Vec3 forward() { return new Vec3(Math.sin(yaw) * Math.cos(pitch), Math.sin(pitch), Math.cos(yaw) * Math.cos(pitch)); }
    private Vec3 right() { return new Vec3(Math.cos(yaw), 0, -Math.sin(yaw)); }

    /** Stable camera basis for rendering a complete frame. */
    public View view() {
        return new View(position, forward(), right(),
                new Vec3(-Math.sin(yaw) * Math.sin(pitch), Math.cos(pitch), -Math.cos(yaw) * Math.sin(pitch)),
                orbit, speed);
    }

    /** Frame snapshot independent of Swing and input devices. */
    public record View(Vec3 position, Vec3 forward, Vec3 right, Vec3 up, boolean orbit, double speed) {
        /** Converts a world point to eye coordinates. */
        public Vec3 transform(Vec3 world) {
            Vec3 d = world.subtract(position);
            return new Vec3(d.dot(right), d.dot(up), d.dot(forward));
        }
    }
}

