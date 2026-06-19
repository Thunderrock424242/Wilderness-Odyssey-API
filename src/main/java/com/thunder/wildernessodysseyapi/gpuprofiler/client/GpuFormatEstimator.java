package com.thunder.wildernessodysseyapi.gpuprofiler.client;

/**
 * Estimates the logical storage requested from OpenGL. Driver compression,
 * alignment and residency decisions can make physical VRAM usage differ.
 */
final class GpuFormatEstimator {

    private GpuFormatEstimator() {
    }

    static long textureBytes(int internalFormat, int width, int height, int format, int type) {
        if (width <= 0 || height <= 0) {
            return 0L;
        }

        int fixedBytes = fixedBytesPerPixel(internalFormat);
        if (fixedBytes > 0) {
            return saturatedMultiply(saturatedMultiply(width, height), fixedBytes);
        }

        int packedBytes = packedTypeBytes(type);
        int bytesPerPixel = packedBytes > 0 ? packedBytes : components(format) * componentBytes(type);
        return saturatedMultiply(saturatedMultiply(width, height), Math.max(1, bytesPerPixel));
    }

    static long renderbufferBytes(int internalFormat, int width, int height) {
        int bytesPerPixel = fixedBytesPerPixel(internalFormat);
        // Unsized renderbuffer formats are driver-selected; four bytes is a
        // more useful conservative estimate than assuming a single channel.
        return saturatedMultiply(saturatedMultiply(Math.max(0, width), Math.max(0, height)), bytesPerPixel > 0 ? bytesPerPixel : 4);
    }

    private static int fixedBytesPerPixel(int internalFormat) {
        return switch (internalFormat) {
            // R8, R8I, R8UI, RED
            case 33321, 33329, 33330, 6403 -> 1;
            // RG8, RG8I, RG8UI, R16, R16F, DEPTH_COMPONENT16
            case 33323, 33335, 33336, 33322, 33325, 33189 -> 2;
            // RGB8, SRGB8, RGB
            case 32849, 35905, 6407 -> 3;
            // RGBA8, SRGB8_ALPHA8, RGBA, R32F, RG16F, RGB10_A2,
            // R11F_G11F_B10F, RGB9_E5, DEPTH24/32/32F, DEPTH24_STENCIL8
            case 32856, 35907, 6408, 33326, 33327, 32857, 35898, 35901,
                    33190, 33191, 36012, 35056 -> 4;
            // RGB16, RGB16F
            case 32852, 34843 -> 6;
            // RGBA16, RGBA16F, RG32F, DEPTH32F_STENCIL8
            case 32859, 34842, 33328, 36013 -> 8;
            // RGB32F
            case 34837 -> 12;
            // RGBA32F
            case 34836 -> 16;
            default -> 0;
        };
    }

    private static int components(int format) {
        return switch (format) {
            case 6403, 6406, 6409, 6402 -> 1; // RED, ALPHA, LUMINANCE, DEPTH
            case 33319, 6410 -> 2; // RG, LUMINANCE_ALPHA
            case 6407 -> 3; // RGB
            case 6408, 34041 -> 4; // RGBA, DEPTH_STENCIL (packed types override this)
            default -> 4;
        };
    }

    private static int componentBytes(int type) {
        return switch (type) {
            case 5120, 5121 -> 1; // BYTE, UNSIGNED_BYTE
            case 5122, 5123, 5131 -> 2; // SHORT, UNSIGNED_SHORT, HALF_FLOAT
            case 5124, 5125, 5126 -> 4; // INT, UNSIGNED_INT, FLOAT
            case 5130 -> 8; // DOUBLE
            default -> 1;
        };
    }

    private static int packedTypeBytes(int type) {
        return switch (type) {
            case 32818, 32819, 33635 -> 2;
            case 32820, 32821, 33640, 34042, 36269 -> 4;
            case 36270 -> 8;
            default -> 0;
        };
    }

    private static long saturatedMultiply(long left, long right) {
        if (left <= 0L || right <= 0L) {
            return 0L;
        }
        if (left > Long.MAX_VALUE / right) {
            return Long.MAX_VALUE;
        }
        return left * right;
    }
}
