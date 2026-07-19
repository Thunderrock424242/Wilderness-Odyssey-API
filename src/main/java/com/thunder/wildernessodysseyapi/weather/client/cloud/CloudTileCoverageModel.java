package com.thunder.wildernessodysseyapi.weather.client.cloud;

/**
 * Proves whether a blocky cloud tile overlaps a continuous precipitation field.
 *
 * <p>Atmospheric values are bilinear between cell centers. A 12-block cloud
 * tile can cross a cell-center boundary when the configured atmosphere cells
 * are as small as 16 blocks, so sampling only the tile center is insufficient.
 * This model evaluates every corner of the bilinear sub-rectangles inside the
 * tile; a bilinear field reaches its extrema at those corners.</p>
 */
public final class CloudTileCoverageModel {

    private CloudTileCoverageModel() {
    }

    /**
     * Returns whether any part of one cloud tile meets the rain-cover threshold.
     *
     * @param worldTileX cloud tile X coordinate
     * @param worldTileZ cloud tile Z coordinate
     * @param atmosphericCellSize synchronized atmosphere cell width in blocks
     * @param sampler continuous precipitation sampler
     */
    public static boolean overlapsPrecipitation(
            int worldTileX,
            int worldTileZ,
            int atmosphericCellSize,
            PrecipitationSampler sampler
    ) {
        if (sampler == null) {
            return false;
        }
        int safeCellSize = Math.max(1, atmosphericCellSize);
        double minimumX = worldTileX * (double) CloudCoverageModel.CLOUD_TILE_SIZE;
        double minimumZ = worldTileZ * (double) CloudCoverageModel.CLOUD_TILE_SIZE;
        double maximumX = minimumX + CloudCoverageModel.CLOUD_TILE_SIZE;
        double maximumZ = minimumZ + CloudCoverageModel.CLOUD_TILE_SIZE;
        double xBoundary = interpolationBoundary(minimumX, maximumX, safeCellSize);
        double zBoundary = interpolationBoundary(minimumZ, maximumZ, safeCellSize);
        int xCount = Double.isNaN(xBoundary) ? 2 : 3;
        int zCount = Double.isNaN(zBoundary) ? 2 : 3;

        for (int zIndex = 0; zIndex < zCount; zIndex++) {
            double z = coordinate(zIndex, minimumZ, maximumZ, zBoundary);
            for (int xIndex = 0; xIndex < xCount; xIndex++) {
                double x = coordinate(xIndex, minimumX, maximumX, xBoundary);
                if (sampler.sample(x, z)
                        >= CloudCoverageModel.PRECIPITATION_COVERAGE_THRESHOLD) {
                    return true;
                }
            }
        }
        return false;
    }

    private static double interpolationBoundary(double minimum, double maximum, int cellSize) {
        double grid = minimum / cellSize - 0.5;
        double boundaryIndex = Math.floor(grid) + 1.0;
        double boundary = (boundaryIndex + 0.5) * cellSize;
        return boundary > minimum && boundary < maximum ? boundary : Double.NaN;
    }

    private static double coordinate(
            int index,
            double minimum,
            double maximum,
            double boundary
    ) {
        if (index == 0) {
            return minimum;
        }
        if (index == 1 && !Double.isNaN(boundary)) {
            return boundary;
        }
        return maximum;
    }

    /** Allocation-free callback used to query the current precipitation field. */
    @FunctionalInterface
    public interface PrecipitationSampler {
        /** Returns normalized precipitation at world X/Z coordinates. */
        double sample(double blockX, double blockZ);
    }
}
