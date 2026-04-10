package com.thunder.wildernessodysseyapi.watersystem.water.mesh;

import com.thunder.wildernessodysseyapi.watersystem.water.sph.SPHConstants;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;

/**
 * MarchingCubes
 *
 * Extracts a triangle mesh from a DensityField by running the
 * Marching Cubes algorithm (Lorensen & Cline, 1987).
 *
 * Output: interleaved float array [ x,y,z, nx,ny,nz, x,y,z, nx,ny,nz, ... ]
 * Each triplet of 6-float entries forms one triangle.
 *
 * Normals are computed per-vertex by sampling the gradient of the
 * density field (central differences).
 */
public class MarchingCubes {

    // Scratch edge vertex positions (12 possible per cube)
    private final Vector3f[] edgeVerts = new Vector3f[12];

    // Output buffer — rebuilt each frame
    private final List<Float> vertices = new ArrayList<>(4096);

    public MarchingCubes() {
        for (int i = 0; i < 12; i++) edgeVerts[i] = new Vector3f();
    }

    /**
     * Run marching cubes over the given density field.
     * @param field  the density field to polygonise
     * @return       float[] of interleaved position+normal data, length % 18 == 0
     */
    public float[] extract(DensityField field) {
        vertices.clear();
        if (field.nx < 2 || field.ny < 2 || field.nz < 2) return new float[0];

        float iso = SPHConstants.ISO_THRESHOLD;

        for (int gz = 0; gz < field.nz - 1; gz++) {
            for (int gy = 0; gy < field.ny - 1; gy++) {
                for (int gx = 0; gx < field.nx - 1; gx++) {
                    polygoniseCube(field, gx, gy, gz, iso);
                }
            }
        }

        float[] result = new float[vertices.size()];
        for (int i = 0; i < result.length; i++) result[i] = vertices.get(i);
        return result;
    }

    // -------------------------------------------------------------------------
    // Per-cube polygonisation
    // -------------------------------------------------------------------------

    private void polygoniseCube(DensityField field, int gx, int gy, int gz, float iso) {
        // Sample density at the 8 corners of this cube
        float[] val = {
            field.sample(gx,   gy,   gz),
            field.sample(gx+1, gy,   gz),
            field.sample(gx+1, gy,   gz+1),
            field.sample(gx,   gy,   gz+1),
            field.sample(gx,   gy+1, gz),
            field.sample(gx+1, gy+1, gz),
            field.sample(gx+1, gy+1, gz+1),
            field.sample(gx,   gy+1, gz+1)
        };

        // Determine which corners are inside the isosurface
        int cubeIndex = 0;
        if (val[0] > iso) cubeIndex |= 1;
        if (val[1] > iso) cubeIndex |= 2;
        if (val[2] > iso) cubeIndex |= 4;
        if (val[3] > iso) cubeIndex |= 8;
        if (val[4] > iso) cubeIndex |= 16;
        if (val[5] > iso) cubeIndex |= 32;
        if (val[6] > iso) cubeIndex |= 64;
        if (val[7] > iso) cubeIndex |= 128;

        int edgeMask = MarchingCubesTables.EDGE_TABLE[cubeIndex];
        if (edgeMask == 0) return;

        // Compute world positions of the 8 corners
        Vector3f[] corners = new Vector3f[8];
        int[][] offsets = {
            {0,0,0},{1,0,0},{1,0,1},{0,0,1},
            {0,1,0},{1,1,0},{1,1,1},{0,1,1}
        };
        for (int i = 0; i < 8; i++) {
            corners[i] = new Vector3f();
            field.gridToWorld(gx + offsets[i][0],
                              gy + offsets[i][1],
                              gz + offsets[i][2], corners[i]);
        }

        // Interpolate edge vertices
        int[][] edges = {
            {0,1},{1,2},{2,3},{3,0},
            {4,5},{5,6},{6,7},{7,4},
            {0,4},{1,5},{2,6},{3,7}
        };
        for (int i = 0; i < 12; i++) {
            if ((edgeMask & (1 << i)) != 0) {
                int a = edges[i][0], b = edges[i][1];
                float t = (iso - val[a]) / (val[b] - val[a] + 1e-8f);
                t = Math.max(0f, Math.min(1f, t));
                edgeVerts[i].set(
                    corners[a].x + t * (corners[b].x - corners[a].x),
                    corners[a].y + t * (corners[b].y - corners[a].y),
                    corners[a].z + t * (corners[b].z - corners[a].z)
                );
            }
        }

        // Emit triangles
        int[] tris = MarchingCubesTables.TRI_TABLE[cubeIndex];
        for (int i = 0; i < tris.length && tris[i] != -1; i += 3) {
            Vector3f v0 = edgeVerts[tris[i]];
            Vector3f v1 = edgeVerts[tris[i+1]];
            Vector3f v2 = edgeVerts[tris[i+2]];

            // Compute face normal
            float ex = v1.x - v0.x, ey = v1.y - v0.y, ez = v1.z - v0.z;
            float fx = v2.x - v0.x, fy = v2.y - v0.y, fz = v2.z - v0.z;
            float nx = ey*fz - ez*fy;
            float ny = ez*fx - ex*fz;
            float nz = ex*fy - ey*fx;
            float len = (float)Math.sqrt(nx*nx + ny*ny + nz*nz);
            if (len > 1e-6f) { nx/=len; ny/=len; nz/=len; }

            emitVertex(v0.x, v0.y, v0.z, nx, ny, nz);
            emitVertex(v1.x, v1.y, v1.z, nx, ny, nz);
            emitVertex(v2.x, v2.y, v2.z, nx, ny, nz);
        }
    }

    private void emitVertex(float x, float y, float z,
                             float nx, float ny, float nz) {
        vertices.add(x); vertices.add(y); vertices.add(z);
        vertices.add(nx); vertices.add(ny); vertices.add(nz);
    }
}
