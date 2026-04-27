package com.thunder.wildernessodysseyapi.util;

import java.util.Random;

/**
 * Lightweight single-octave 2-D Perlin/gradient noise.
 * Used to roughen crater shapes, rim height, and ejecta scatter.
 */
public class SimplexNoise {

    private final int[] perm = new int[512];

    private static final int[][] GRAD2 = {
        {1,1},{-1,1},{1,-1},{-1,-1},
        {1,0},{-1,0},{0,1},{0,-1}
    };

    public SimplexNoise(long seed) {
        Random rng = new Random(seed);
        int[] p = new int[256];
        for (int i = 0; i < 256; i++) p[i] = i;
        for (int i = 255; i > 0; i--) {
            int j = rng.nextInt(i + 1);
            int tmp = p[i]; p[i] = p[j]; p[j] = tmp;
        }
        for (int i = 0; i < 512; i++) perm[i] = p[i & 255];
    }

    private double fade(double t) { return t * t * t * (t * (t * 6 - 15) + 10); }
    private double lerp(double a, double b, double t) { return a + t * (b - a); }
    private double grad(int hash, double x, double y) {
        int[] g = GRAD2[hash & 7];
        return g[0] * x + g[1] * y;
    }

    /** Returns a value in roughly [-1, 1]. */
    public double noise(double x, double y) {
        int xi = (int) Math.floor(x) & 255;
        int yi = (int) Math.floor(y) & 255;
        double xf = x - Math.floor(x);
        double yf = y - Math.floor(y);
        double u = fade(xf), v = fade(yf);

        int aa = perm[perm[xi]     + yi];
        int ab = perm[perm[xi]     + yi + 1];
        int ba = perm[perm[xi + 1] + yi];
        int bb = perm[perm[xi + 1] + yi + 1];

        return lerp(
            lerp(grad(aa, xf,   yf),   grad(ba, xf - 1, yf),   u),
            lerp(grad(ab, xf,   yf - 1), grad(bb, xf - 1, yf - 1), u),
            v
        );
    }

    /**
     * Multi-octave fractal noise.
     * @param octaves      number of layers (3–4 is plenty)
     * @param persistence  amplitude decay per octave (0.5 = standard)
     */
    public double fractal(double x, double y, int octaves, double persistence) {
        double value = 0, amplitude = 1, maxValue = 0, frequency = 1;
        for (int i = 0; i < octaves; i++) {
            value    += noise(x * frequency, y * frequency) * amplitude;
            maxValue += amplitude;
            amplitude *= persistence;
            frequency *= 2.0;
        }
        return value / maxValue;
    }
}
