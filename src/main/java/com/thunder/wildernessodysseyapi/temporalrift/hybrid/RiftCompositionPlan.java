package com.thunder.wildernessodysseyapi.temporalrift.hybrid;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/** Bounded deterministic sector composition; independent from chunk access and block placement. */
public final class RiftCompositionPlan {
    private RiftCompositionPlan() { }

    public record Variant(String id, int width, int height, int depth) {
        public Variant {
            if (id == null || id.isBlank() || width < 1 || height < 1 || depth < 1) {
                throw new IllegalArgumentException("Invalid rift structure dimensions or id");
            }
        }
    }

    /** Exclusive upper bounds match block volumes, including negative rotated coordinates. */
    public record Box(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
        public boolean overlaps(Box other, int spacing) {
            return minX < other.maxX + spacing && maxX + spacing > other.minX
                    && minY < other.maxY + spacing && maxY + spacing > other.minY
                    && minZ < other.maxZ + spacing && maxZ + spacing > other.minZ;
        }
    }

    public record Piece(Variant variant, int rotation, int x, int y, int z, Box box) { }

    public static List<Piece> plan(long seed, int diameter, int count, int spacing, List<Variant> variants) {
        if (variants.isEmpty()) return List.of();
        Random random = new Random(seed);
        int radius = Math.clamp(diameter, 15, 30) / 2;
        int slots = Math.clamp(count, 1, 12);
        int gap = Math.clamp(spacing, 0, 4);
        double phase = random.nextDouble() * Math.PI * 2;
        List<Piece> pieces = new ArrayList<>();
        Box ring = new Box(-3, -1, -3, 3, 6, 3);
        for (int slot = 0; slot < slots; slot++) {
            for (int attempt = 0; attempt < 12; attempt++) {
                Variant variant = variants.get(random.nextInt(variants.size()));
                if (variant.width > 9 || variant.depth > 9 || variant.height > 8) continue;
                int rotation = random.nextInt(4);
                int width = rotation % 2 == 0 ? variant.width : variant.depth;
                int depth = rotation % 2 == 0 ? variant.depth : variant.width;
                double angle = phase + slot * Math.PI * 2 / slots + (random.nextDouble() - 0.5) * 0.32;
                double reach = 5.5 + random.nextDouble() * Math.max(0, radius - 7);
                int minX = (int) Math.round(Math.cos(angle) * reach) - width / 2;
                int minZ = (int) Math.round(Math.sin(angle) * reach) - depth / 2;
                // Shallow height variation keeps the formation explorable rather than a uniform halo.
                int y = (slot % 3) + random.nextInt(3) - 2;
                Box box = new Box(minX, y, minZ, minX + width, y + variant.height, minZ + depth);
                if (box.minX < -radius || box.maxX > radius || box.minZ < -radius || box.maxZ > radius
                        || box.overlaps(ring, gap) || pieces.stream().anyMatch(piece -> box.overlaps(piece.box, gap))) continue;
                // Vanilla rotates around (0,0,0), so compensate for its negative footprint.
                int x = minX + (rotation == 1 ? variant.depth - 1 : rotation == 2 ? variant.width - 1 : 0);
                int z = minZ + (rotation == 2 ? variant.depth - 1 : rotation == 3 ? variant.width - 1 : 0);
                pieces.add(new Piece(variant, rotation, x, y, z, box));
                break;
            }
        }
        return List.copyOf(pieces);
    }
}
