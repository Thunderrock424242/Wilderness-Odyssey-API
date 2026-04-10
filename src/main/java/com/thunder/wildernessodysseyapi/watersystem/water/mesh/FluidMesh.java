package com.thunder.wildernessodysseyapi.watersystem.water.mesh;


import com.thunder.wildernessodysseyapi.watersystem.water.sph.SPHParticle;
import com.thunder.wildernessodysseyapi.watersystem.water.sph.SPHSimulator;

import java.util.List;

/**
 * FluidMesh
 *
 * Owns the density field and marching cubes extractor for one
 * SPHSimulator. Each frame the mesh is rebuilt from the latest
 * particle positions. The resulting vertex data is stored as a
 * float[] ready for upload by FluidRenderer.
 *
 * Rebuild is triggered by FluidRenderer on the render thread.
 */
public class FluidMesh {

    private final DensityField field   = new DensityField();
    private final MarchingCubes mc     = new MarchingCubes();

    // Latest extracted mesh data [x,y,z,nx,ny,nz, ...]
    public volatile float[] meshData = new float[0];

    // The simulator this mesh belongs to
    public final SPHSimulator simulator;

    public FluidMesh(SPHSimulator simulator) {
        this.simulator = simulator;
    }

    /**
     * Rebuild the mesh from current particle positions.
     * Called every render frame.
     */
    public void rebuild() {
        List<SPHParticle> particles = simulator.particles;
        if (particles.isEmpty()) { meshData = new float[0]; return; }

        field.rebuild(particles);
        meshData = mc.extract(field);
    }

    /** @return true if there is renderable geometry */
    public boolean hasGeometry() {
        float[] d = meshData;
        return d != null && d.length >= 18; // at least 1 triangle
    }
}
