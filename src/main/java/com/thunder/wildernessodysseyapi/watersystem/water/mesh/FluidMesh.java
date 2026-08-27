package com.thunder.wildernessodysseyapi.watersystem.water.mesh;


import com.thunder.wildernessodysseyapi.watersystem.water.sph.SPHParticle;
import com.thunder.wildernessodysseyapi.watersystem.water.sph.SPHSimulator;

import java.util.List;

/**
 * FluidMesh
 *
 * Owns the density field and marching cubes extractor for one
 * SPHSimulator. The mesh is rebuilt only after the simulator publishes an
 * eligible render revision. The resulting vertex data is stored as a
 * float[] ready for upload by FluidRenderer.
 *
 * Rebuild is triggered by FluidRenderer on the render thread.
 */
public class FluidMesh {

    private final DensityField field   = new DensityField();
    private final MarchingCubes mc     = new MarchingCubes();

    // Latest extracted mesh data [x,y,z,nx,ny,nz, ...]
    public volatile float[] meshData = new float[0];
    private long builtRevision = -1L;

    // The simulator this mesh belongs to
    public final SPHSimulator simulator;

    public FluidMesh(SPHSimulator simulator) {
        this.simulator = simulator;
    }

    /**
     * Rebuilds the mesh when the simulator revision passes the configured interval.
     */
    public void rebuild() {
        rebuildIfNeeded(1);
    }

    public void rebuild(int revisionInterval) {
        rebuildIfNeeded(revisionInterval);
    }

    /** Returns whether the simulator has published a revision eligible for extraction. */
    public boolean needsRebuild(int revisionInterval) {
        return needsRebuild(simulator.getRenderRevision(), revisionInterval);
    }

    private boolean needsRebuild(long revision, int revisionInterval) {
        return revision != builtRevision
                && (builtRevision < 0L
                || revision - builtRevision >= Math.max(1, revisionInterval));
    }

    /** Rebuilds one eligible revision and reports whether extraction work ran. */
    public boolean rebuildIfNeeded(int revisionInterval) {
        long revision = simulator.getRenderRevision();
        if (!needsRebuild(revision, revisionInterval)) return false;

        List<SPHParticle> particles = simulator.getRenderParticles();
        if (particles.isEmpty()) {
            meshData = new float[0];
            builtRevision = revision;
            return true;
        }

        field.rebuild(particles);
        meshData = mc.extract(field);
        builtRevision = revision;
        return true;
    }

    /** @return true if there is renderable geometry */
    public boolean hasGeometry() {
        float[] d = meshData;
        return d != null && d.length >= 18; // at least 1 triangle
    }
}
