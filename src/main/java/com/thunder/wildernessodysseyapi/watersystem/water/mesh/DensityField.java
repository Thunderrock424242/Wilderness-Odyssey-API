package com.thunder.wildernessodysseyapi.watersystem.water.mesh;

import com.thunder.wildernessodysseyapi.watersystem.water.sph.SPHConstants;
import com.thunder.wildernessodysseyapi.watersystem.water.sph.SPHParticle;
import org.joml.Vector3f;

import java.util.List;

/**
 * DensityField
 *
 * A 3D scalar field built by "splatting" SPH particle densities
 * onto a uniform grid. The marching cubes algorithm samples this
 * field to extract the iso-surface (the water mesh).
 *
 * The field is rebuilt only when the owning simulation publishes a new render revision.
 * Grid dimensions are determined dynamically from the particle AABB
 * plus a small padding.
 */
public class DensityField {

    // Grid dimensions
    public int nx, ny, nz;

    // World-space origin of the grid
    public float originX, originY, originZ;

    // Flat array of density values [x + nx*(y + ny*z)]
    public float[] values;

    private final float cellSize   = SPHConstants.GRID_CELL_SIZE;
    private final float splatR     = SPHConstants.SPLAT_RADIUS;
    private final float splatR2    = splatR * splatR;
    private final float invCellSz  = 1f / cellSize;

    /**
     * Rebuild the density field from the current particle list.
     * @param particles list of SPH particles
     */
    public void rebuild(List<SPHParticle> particles) {
        if (particles.isEmpty()) { nx = ny = nz = 0; values = null; return; }

        // Compute AABB of particles
        float minX = Float.MAX_VALUE, minY = Float.MAX_VALUE, minZ = Float.MAX_VALUE;
        float maxX = -Float.MAX_VALUE, maxY = -Float.MAX_VALUE, maxZ = -Float.MAX_VALUE;

        for (SPHParticle p : particles) {
            minX = Math.min(minX, p.position.x);
            minY = Math.min(minY, p.position.y);
            minZ = Math.min(minZ, p.position.z);
            maxX = Math.max(maxX, p.position.x);
            maxY = Math.max(maxY, p.position.y);
            maxZ = Math.max(maxZ, p.position.z);
        }

        float pad = splatR + cellSize;
        originX = minX - pad;
        originY = minY - pad;
        originZ = minZ - pad;

        nx = (int)Math.ceil((maxX - originX + pad) * invCellSz) + 1;
        ny = (int)Math.ceil((maxY - originY + pad) * invCellSz) + 1;
        nz = (int)Math.ceil((maxZ - originZ + pad) * invCellSz) + 1;

        // Cap grid size to avoid OOM
        nx = Math.min(nx, 80);
        ny = Math.min(ny, 80);
        nz = Math.min(nz, 80);

        int total = nx * ny * nz;
        if (values == null || values.length < total) values = new float[total];
        else java.util.Arrays.fill(values, 0, total, 0f);

        // Splat each particle onto nearby grid cells
        int splatCells = (int)Math.ceil(splatR * invCellSz);

        for (SPHParticle p : particles) {
            int cx = (int)((p.position.x - originX) * invCellSz);
            int cy = (int)((p.position.y - originY) * invCellSz);
            int cz = (int)((p.position.z - originZ) * invCellSz);

            for (int dx = -splatCells; dx <= splatCells; dx++) {
                int gx = cx + dx;
                if (gx < 0 || gx >= nx) continue;
                float wx = originX + gx * cellSize;

                for (int dy = -splatCells; dy <= splatCells; dy++) {
                    int gy = cy + dy;
                    if (gy < 0 || gy >= ny) continue;
                    float wy = originY + gy * cellSize;

                    for (int dz = -splatCells; dz <= splatCells; dz++) {
                        int gz = cz + dz;
                        if (gz < 0 || gz >= nz) continue;
                        float wz = originZ + gz * cellSize;

                        float ex = p.position.x - wx;
                        float ey = p.position.y - wy;
                        float ez = p.position.z - wz;
                        float r2 = ex*ex + ey*ey + ez*ez;

                        if (r2 < splatR2) {
                            float t = 1f - r2 / splatR2;
                            values[gx + nx*(gy + ny*gz)] += t * t * t; // cubic falloff
                        }
                    }
                }
            }
        }
    }

    /** Sample the field at integer grid coordinates. */
    public float sample(int gx, int gy, int gz) {
        if (gx < 0 || gx >= nx || gy < 0 || gy >= ny || gz < 0 || gz >= nz) return 0f;
        return values[gx + nx*(gy + ny*gz)];
    }

    /** Convert grid coordinates to world space. */
    public void gridToWorld(int gx, int gy, int gz, Vector3f out) {
        out.set(originX + gx * cellSize,
                originY + gy * cellSize,
                originZ + gz * cellSize);
    }

    /**
     * Writes a smooth outward normal from the trilinear density gradient.
     *
     * @return whether the sampled gradient was large enough to normalize
     */
    public boolean outwardNormal(float worldX, float worldY, float worldZ, Vector3f out) {
        float gridX = (worldX - originX) * invCellSz;
        float gridY = (worldY - originY) * invCellSz;
        float gridZ = (worldZ - originZ) * invCellSz;
        int x0 = (int) Math.floor(gridX);
        int y0 = (int) Math.floor(gridY);
        int z0 = (int) Math.floor(gridZ);
        float tx = gridX - x0;
        float ty = gridY - y0;
        float tz = gridZ - z0;

        // Differentiate the containing trilinear cell analytically. This uses
        // eight density reads instead of six neighboring trilinear samples
        // (48 reads) for every emitted marching-cubes vertex.
        float c000 = sample(x0, y0, z0);
        float c100 = sample(x0 + 1, y0, z0);
        float c010 = sample(x0, y0 + 1, z0);
        float c110 = sample(x0 + 1, y0 + 1, z0);
        float c001 = sample(x0, y0, z0 + 1);
        float c101 = sample(x0 + 1, y0, z0 + 1);
        float c011 = sample(x0, y0 + 1, z0 + 1);
        float c111 = sample(x0 + 1, y0 + 1, z0 + 1);

        float gradientX = lerp(
                lerp(c100 - c000, c110 - c010, ty),
                lerp(c101 - c001, c111 - c011, ty),
                tz
        );
        float gradientY = lerp(
                lerp(c010 - c000, c110 - c100, tx),
                lerp(c011 - c001, c111 - c101, tx),
                tz
        );
        float gradientZ = lerp(
                lerp(c001 - c000, c101 - c100, tx),
                lerp(c011 - c010, c111 - c110, tx),
                ty
        );
        out.set(-gradientX, -gradientY, -gradientZ);
        float lengthSquared = out.lengthSquared();
        if (lengthSquared <= 1.0e-10f) {
            out.set(0.0f, 1.0f, 0.0f);
            return false;
        }
        out.mul(1.0f / (float) Math.sqrt(lengthSquared));
        return true;
    }

    private static float lerp(float from, float to, float alpha) {
        return from + (to - from) * alpha;
    }
}
