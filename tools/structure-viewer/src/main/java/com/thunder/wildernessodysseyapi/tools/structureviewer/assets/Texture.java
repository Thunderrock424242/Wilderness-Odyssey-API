package com.thunder.wildernessodysseyapi.tools.structureviewer.assets;

import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
import java.io.*;
import java.util.Iterator;

/** Immutable pixel texture. Animated textures preview their first frame, preserving cutout alpha. */
public record Texture(int width, int height, int[] pixels, boolean translucent, boolean opaque) {
    /** Checked decode avoids allocating unbounded images from resource packs. */
    public static Texture decode(byte[] bytes, boolean animated) throws IOException {
        try (var input = ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
            Iterator<javax.imageio.ImageReader> readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) throw new IOException("Unsupported image format");
            var reader = readers.next();
            try {
                reader.setInput(input);
                int w = reader.getWidth(0), h = reader.getHeight(0);
                if (w < 1 || h < 1 || w > 2048 || h > 32768 || (long) w * h > 16_777_216)
                    throw new IOException("Texture dimensions exceed preview limits");
                BufferedImage image = reader.read(0);
                int frameHeight = animated ? Math.min(w, h) : h;
                int[] pixels = image.getRGB(0, 0, w, frameHeight, null, 0, w);
                boolean translucent = false, opaque = true;
                for (int pixel : pixels) { int alpha = pixel >>> 24; translucent |= alpha > 0 && alpha < 255; opaque &= alpha == 255; }
                return new Texture(w, frameHeight, pixels, translucent, opaque);
            } finally { reader.dispose(); }
        }
    }

    /** Nearest-neighbor sampling retains Minecraft's pixel-art detail. UV coordinates use 0..16. */
    public int sample(double u, double v) {
        int x = Math.max(0, Math.min(width - 1, (int) (u / 16 * width)));
        int y = Math.max(0, Math.min(height - 1, (int) (v / 16 * height)));
        return pixels[y * width + x];
    }

    /** Shared conspicuous missing-texture checkerboard. */
    public static Texture missing() {
        return new Texture(2, 2, new int[]{0xffff00ff,0xff191119,0xff191119,0xffff00ff},false,true);
    }

    /** Solid color used for air/debug fallback geometry. */
    public static Texture solid(int color) { return new Texture(1,1,new int[]{0xff000000 | color},false,true); }
}
