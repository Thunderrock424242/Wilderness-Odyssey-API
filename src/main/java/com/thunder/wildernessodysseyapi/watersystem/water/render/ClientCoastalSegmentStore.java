package com.thunder.wildernessodysseyapi.watersystem.water.render;

import com.thunder.wildernessodysseyapi.environment.glacial.GlacialBiomeManager;
import com.thunder.wildernessodysseyapi.watersystem.ocean.ClientOceanSeaState;
import com.thunder.wildernessodysseyapi.watersystem.ocean.OceanSeaState;
import com.thunder.wildernessodysseyapi.watersystem.ocean.coast.CoastalSegment;
import com.thunder.wildernessodysseyapi.watersystem.ocean.coast.CoastalSeasonModel;
import com.thunder.wildernessodysseyapi.watersystem.ocean.coast.CoastalWaveModel;
import com.thunder.wildernessodysseyapi.watersystem.ocean.coast.CoastalWaveProfile;
import com.thunder.wildernessodysseyapi.watersystem.ocean.tide.TideSystem;
import com.thunder.wildernessodysseyapi.watersystem.water.network.ClientWaterChunkSnapshot;
import com.thunder.wildernessodysseyapi.watersystem.water.network.ClientWaterSnapshotStore;
import com.thunder.wildernessodysseyapi.watersystem.water.volume.GeneratedWaterChunk;
import com.thunder.wildernessodysseyapi.worldgen.biome.ModBiomes;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.tags.BiomeTags;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Discovers and caches loaded ocean-to-beach topology for client presentation.
 *
 * <p>The refresh scans only immutable synchronized water snapshots and already
 * loaded client chunks. It never queries an unloaded neighbor, requests a
 * ticket, flood-fills a water body, or mutates terrain.</p>
 */
public final class ClientCoastalSegmentStore {

    private static final int SEGMENT_BUCKET_SIZE = 8;
    private static final int MAX_POINTS_PER_SEGMENT = 12;
    private static final int MOVE_REFRESH_DISTANCE_BLOCKS = 8;
    private static final int[][] CARDINALS = {
            {1, 0}, {-1, 0}, {0, 1}, {0, -1}
    };

    private static ClientLevel activeLevel;
    private static List<CoastalSegment> segments = List.of();
    private static long nextRefreshTick;
    private static int lastCenterX = Integer.MIN_VALUE;
    private static int lastCenterZ = Integer.MIN_VALUE;
    private static int lastCandidateCount;

    private ClientCoastalSegmentStore() {
    }

    /** Refreshes the bounded cache when its interval or movement threshold expires. */
    public static void tick(Minecraft minecraft) {
        ClientLevel level = minecraft.level;
        if (level == null || minecraft.player == null
                || !WaterRenderingConfig.coastalWavesEnabled(level)) {
            clear();
            return;
        }
        if (activeLevel != level) {
            clear();
            activeLevel = level;
        }

        int centerX = minecraft.player.getBlockX();
        int centerZ = minecraft.player.getBlockZ();
        long gameTime = level.getGameTime();
        int deltaX = centerX - lastCenterX;
        int deltaZ = centerZ - lastCenterZ;
        boolean moved = lastCenterX == Integer.MIN_VALUE
                || deltaX * deltaX + deltaZ * deltaZ
                >= MOVE_REFRESH_DISTANCE_BLOCKS * MOVE_REFRESH_DISTANCE_BLOCKS;
        if (!moved && gameTime < nextRefreshTick) {
            return;
        }

        refresh(level, centerX, centerZ);
        lastCenterX = centerX;
        lastCenterZ = centerZ;
        nextRefreshTick = gameTime + WaterRenderingConfig.coastalTopologyRefreshTicks();
    }

    /** Returns the immutable nearest-first segment cache for the active client level. */
    public static List<CoastalSegment> segments(ClientLevel level) {
        return activeLevel == level ? segments : List.of();
    }

    /** Number of raw boundary candidates observed by the latest bounded scan. */
    public static int lastCandidateCount() {
        return lastCandidateCount;
    }

    /** Formats the nearest cached topology and wave state for the Rendering debug page. */
    public static List<String> debugLines(ClientLevel level) {
        List<CoastalSegment> current = segments(level);
        if (current.isEmpty()) {
            return List.of("WO Coast nearest: none in loaded shoreline cache");
        }
        CoastalSegment segment = current.getFirst();
        OceanSeaState.Sample sea = WaterRenderingConfig.coastalWeatherInfluenceEnabled()
                ? ClientOceanSeaState.sampleAt(level, segment.centerX(), segment.centerZ())
                : OceanSeaState.CALM;
        float onshoreWind = sea.windDirectionX() * segment.landwardNormalX()
                + sea.windDirectionZ() * segment.landwardNormalZ();
        CoastalWaveModel.Sample wave = CoastalWaveModel.sample(
                segment.id(),
                level.getGameTime(),
                0.0f,
                segment.profile(),
                sea,
                segment.averageBeachSlope(),
                segment.underwaterSlope(),
                segment.averageWaterDepth(),
                onshoreWind
        );
        CoastalSeasonModel.Sample season = ClientCoastalClimate.sample(level, segment);
        wave = CoastalWaveModel.withTide(wave, TideSystem.getTideOffset(level),
                TideSystem.getTideRate(level), segment.averageBeachSlope());
        return List.of(
                String.format(
                        Locale.ROOT,
                        "WO Coast nearest: %s @ %d,%d | normal %.2f,%.2f | "
                                + "beach slope %.2f | water slope %.2f | depth %.2f",
                        segment.profile().shoreType().name().toLowerCase(Locale.ROOT),
                        segment.centerX(),
                        segment.centerZ(),
                        segment.landwardNormalX(),
                        segment.landwardNormalZ(),
                        segment.averageBeachSlope(),
                        segment.underwaterSlope(),
                        segment.averageWaterDepth()
                ),
                String.format(
                        Locale.ROOT,
                        "WO Coast wave: %s | energy %.2f | tide %.2f | crest %.2f | "
                                + "breaker %.2f | run-up %.2f/%.2f | foam %.2f | wetness %.2f",
                        wave.stage().name().toLowerCase(Locale.ROOT),
                        wave.energy(),
                        TideSystem.getTideOffset(level),
                        wave.crestDistanceFromShoreBlocks(),
                        wave.breakerDistanceBlocks(),
                        wave.runUpDistanceBlocks(),
                        wave.maximumRunUpDistanceBlocks(),
                        wave.foam(),
                        wave.wetness()
                ),
                String.format(
                        Locale.ROOT,
                        "WO Coast season: brightness %.2f | tropical %.2f | cold blue %.2f | "
                                + "mist %.2f | foam x%.2f",
                        season.brightness(),
                        season.tropicalClarity(),
                        season.coldBlue(),
                        season.mist(),
                        season.foamMultiplier()
                )
        );
    }

    /** Releases all cached topology on dimension change, disconnect, or disable. */
    public static void clear() {
        activeLevel = null;
        segments = List.of();
        nextRefreshTick = 0L;
        lastCenterX = Integer.MIN_VALUE;
        lastCenterZ = Integer.MIN_VALUE;
        lastCandidateCount = 0;
    }

    private static void refresh(ClientLevel level, int centerX, int centerZ) {
        int radius = WaterRenderingConfig.shorelineRenderDistanceBlocks();
        int stride = WaterRenderingConfig.coastalTopologyStride();
        int maximumRunUp = WaterRenderingConfig.coastalMaxRunUpBlocks();
        List<Candidate> candidates = new ArrayList<>();
        int radiusSquared = radius * radius;

        for (int offsetZ = -radius; offsetZ <= radius; offsetZ += stride) {
            for (int offsetX = -radius; offsetX <= radius; offsetX += stride) {
                int distanceSquared = offsetX * offsetX + offsetZ * offsetZ;
                if (distanceSquared > radiusSquared) {
                    continue;
                }
                int waterX = centerX + offsetX;
                int waterZ = centerZ + offsetZ;
                Candidate candidate = discoverCandidate(
                        level, waterX, waterZ, maximumRunUp, distanceSquared);
                if (candidate != null) {
                    candidates.add(candidate);
                }
            }
        }
        candidates.sort(Comparator.comparingInt(Candidate::distanceSquared));
        lastCandidateCount = candidates.size();

        int segmentBudget = WaterRenderingConfig.coastalSegmentBudget();
        Map<SegmentKey, SegmentBuilder> builders = new LinkedHashMap<>();
        for (Candidate candidate : candidates) {
            SegmentKey key = SegmentKey.of(candidate);
            SegmentBuilder builder = builders.get(key);
            if (builder == null) {
                if (builders.size() >= segmentBudget) {
                    continue;
                }
                builder = new SegmentBuilder(key, candidate);
                builders.put(key, builder);
            }
            builder.add(candidate);
        }

        List<CoastalSegment> rebuilt = new ArrayList<>(builders.size());
        int dimensionHash = level.dimension().location().hashCode();
        for (SegmentBuilder builder : builders.values()) {
            rebuilt.add(builder.build(dimensionHash));
        }
        segments = List.copyOf(rebuilt);
    }

    private static Candidate discoverCandidate(
            ClientLevel level,
            int waterX,
            int waterZ,
            int maximumRunUp,
            int distanceSquared
    ) {
        ClientWaterChunkSnapshot snapshot = ClientWaterSnapshotStore.getAtBlock(
                level, waterX, waterZ);
        if (snapshot == null) {
            return null;
        }
        ClientWaterChunkSnapshot.Column column = snapshot.column(waterX & 15, waterZ & 15);
        if (!isOpenOceanColumn(column)) {
            return null;
        }

        Candidate best = null;
        for (int direction = 0; direction < CARDINALS.length; direction++) {
            int landwardX = CARDINALS[direction][0];
            int landwardZ = CARDINALS[direction][1];
            Candidate candidate = traceLandward(
                    level,
                    waterX,
                    column.baseSurfaceY(),
                    waterZ,
                    landwardX,
                    landwardZ,
                    direction,
                    maximumRunUp,
                    distanceSquared
            );
            if (candidate != null && (best == null
                    || candidate.runUpCells().size() > best.runUpCells().size())) {
                best = candidate;
            }
        }
        return best;
    }

    private static Candidate traceLandward(
            ClientLevel level,
            int waterX,
            float waterSurfaceY,
            int waterZ,
            int landwardX,
            int landwardZ,
            int direction,
            int maximumRunUp,
            int distanceSquared
    ) {
        List<CoastalSegment.RunUpCell> cells = new ArrayList<>(maximumRunUp);
        Holder<Biome> firstBiome = null;
        BlockState firstSurface = null;
        int firstTopY = 0;
        int previousTopY = (int) Math.floor(waterSurfaceY) - 1;
        float positiveRise = 0.0f;
        int laneOffset = 0;
        int detours = 0;

        for (int distance = 1; distance <= maximumRunUp; distance++) {
            int blockX = waterX + landwardX * distance - landwardZ * laneOffset;
            int blockZ = waterZ + landwardZ * distance + landwardX * laneOffset;
            BlockPos horizontal = new BlockPos(
                    blockX, (int) Math.floor(waterSurfaceY), blockZ);
            if (!level.hasChunkAt(horizontal)) {
                break;
            }
            ClientWaterChunkSnapshot landSnapshot = ClientWaterSnapshotStore.getAtBlock(
                    level, blockX, blockZ);
            if (landSnapshot != null
                    && landSnapshot.column(blockX & 15, blockZ & 15).wet()) {
                break;
            }

            LevelChunk chunk = level.getChunk(blockX >> 4, blockZ >> 4);
            int topY = chunk.getHeight(
                    Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                    blockX & 15,
                    blockZ & 15
            );
            BlockPos topPosition = new BlockPos(blockX, topY, blockZ);
            Holder<Biome> biome = level.getBiome(topPosition);
            BlockState surface = level.getBlockState(topPosition);
            if (!isCoastalBiome(biome)) {
                break;
            }
            if (distance > 1 && detours < 2
                    && (!isWettableSurface(surface) || Math.abs(topY - previousTopY) > 1)) {
                // At most two lateral turns per cached ribbon. Test both the
                // sideways cell and the forward cell so wash never cuts the
                // corner through a rock, wall, log or unloaded chunk.
                for (int side : new int[]{1, -1}) {
                    int sideX = -landwardZ * side;
                    int sideZ = landwardX * side;
                    CoastalSegment.RunUpCell lateral = detourCell(level,
                            blockX - landwardX + sideX, blockZ - landwardZ + sideZ,
                            previousTopY, distance + detours);
                    CoastalSegment.RunUpCell forward = detourCell(level,
                            blockX + sideX, blockZ + sideZ, previousTopY, distance + detours + 1);
                    if (lateral == null || forward == null) continue;
                    if (forward.distanceFromWaterBlocks() > maximumRunUp) break;
                    cells.add(lateral);
                    laneOffset += side;
                    detours++;
                    blockX = forward.blockX();
                    blockZ = forward.blockZ();
                    topY = forward.topBlockY();
                    topPosition = new BlockPos(blockX, topY, blockZ);
                    surface = level.getBlockState(topPosition);
                    break;
                }
            }
            if (!isWettableSurface(surface)) {
                // Built shore walls and other solid structures still define an
                // impact boundary, but they never receive the terrain sheet.
                if (distance == 1) {
                    firstBiome = biome;
                    firstSurface = surface;
                    firstTopY = topY;
                }
                break;
            }
            if (distance == 1 && topY > waterSurfaceY + 2.25f) {
                // A cliff still has breaker character, but no terrain sheet.
                firstBiome = biome;
                firstSurface = surface;
                firstTopY = topY;
                break;
            }
            if (Math.abs(topY - previousTopY) > 2) {
                break;
            }

            if (firstBiome == null) {
                firstBiome = biome;
                firstSurface = surface;
                firstTopY = topY;
            }
            positiveRise += Math.max(0, topY - previousTopY);
            previousTopY = topY;
            if (distance + detours > maximumRunUp) break;
            cells.add(new CoastalSegment.RunUpCell(blockX, topY, blockZ, distance + detours));
        }

        if (firstBiome == null) {
            return null;
        }
        CoastalWaveProfile profile = classifyProfile(firstBiome, firstSurface, firstTopY);
        NearshoreProfile nearshore = traceNearshore(
                level,
                waterX,
                waterZ,
                landwardX,
                landwardZ,
                profile
        );
        float slope = cells.isEmpty() ? 1.25f : positiveRise / cells.size();
        return new Candidate(
                waterX,
                waterSurfaceY,
                waterZ,
                landwardX,
                landwardZ,
                direction,
                profile,
                List.copyOf(cells),
                nearshore.cells(),
                slope,
                nearshore.averageDepth(),
                nearshore.underwaterSlope(),
                distanceSquared
        );
    }

    private static CoastalSegment.RunUpCell detourCell(ClientLevel level, int x, int z, int previousY, int distance) {
        BlockPos probe = new BlockPos(x, previousY, z);
        if (!level.hasChunkAt(probe)) return null;
        var snapshot = ClientWaterSnapshotStore.getAtBlock(level, x, z);
        if (snapshot != null && snapshot.column(x & 15, z & 15).wet()) return null;
        LevelChunk chunk = level.getChunk(x >> 4, z >> 4);
        int top = chunk.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x & 15, z & 15);
        BlockPos pos = new BlockPos(x, top, z);
        if (Math.abs(top - previousY) > 1 || !isCoastalBiome(level.getBiome(pos))
                || !isWettableSurface(level.getBlockState(pos))
                || !level.getBlockState(pos.above()).isAir()) return null;
        return new CoastalSegment.RunUpCell(x, top, z, distance);
    }

    private static NearshoreProfile traceNearshore(
            ClientLevel level,
            int shorelineX,
            int shorelineZ,
            int landwardX,
            int landwardZ,
            CoastalWaveProfile profile
    ) {
        int maximumDistance = Math.min(16, (int) Math.ceil(profile.breakerDistanceBlocks()) + 3);
        List<CoastalSegment.NearshoreCell> cells = new ArrayList<>(maximumDistance + 1);
        float summedDepth = 0.0f;
        float depthRise = 0.0f;
        float previousDepth = 0.0f;
        for (int distance = 0; distance <= maximumDistance; distance++) {
            int blockX = shorelineX - landwardX * distance;
            int blockZ = shorelineZ - landwardZ * distance;
            ClientWaterChunkSnapshot snapshot = ClientWaterSnapshotStore.getAtBlock(
                    level, blockX, blockZ);
            if (snapshot == null) {
                break;
            }
            ClientWaterChunkSnapshot.Column column = snapshot.column(blockX & 15, blockZ & 15);
            if (!isOpenOceanColumn(column)) {
                break;
            }
            float depth = column.depth();
            cells.add(new CoastalSegment.NearshoreCell(
                    blockX,
                    column.baseSurfaceY(),
                    blockZ,
                    depth,
                    distance
            ));
            summedDepth += depth;
            if (distance > 0) {
                depthRise += Math.max(0.0f, depth - previousDepth);
            }
            previousDepth = depth;
        }
        int count = Math.max(1, cells.size());
        return new NearshoreProfile(
                List.copyOf(cells),
                summedDepth / count,
                depthRise / Math.max(1, cells.size() - 1)
        );
    }

    private static boolean isOpenOceanColumn(ClientWaterChunkSnapshot.Column column) {
        return column.wet()
                && !column.surfaceCovered()
                && (column.bodyType() == GeneratedWaterChunk.BodyType.OCEAN
                || column.oceanWeight() >= 128);
    }

    private static boolean isCoastalBiome(Holder<Biome> biome) {
        return biome.is(BiomeTags.IS_BEACH) || GlacialBiomeManager.isGlacial(biome);
    }

    private static boolean isWettableSurface(BlockState state) {
        return state.is(BlockTags.SAND)
                || state.is(BlockTags.DIRT)
                || state.is(BlockTags.BASE_STONE_OVERWORLD)
                || state.is(Blocks.GRAVEL)
                || state.is(Blocks.CLAY)
                || state.is(Blocks.SNOW)
                || state.is(Blocks.SNOW_BLOCK)
                || state.is(Blocks.ICE)
                || state.is(Blocks.PACKED_ICE)
                || state.is(Blocks.BLUE_ICE)
                || state.is(Blocks.FROSTED_ICE);
    }

    private static CoastalWaveProfile classifyProfile(
            Holder<Biome> biome,
            BlockState surface,
            int topY
    ) {
        CoastalWaveProfile.ShoreType authoredType = ModBiomes.coastalShoreType(biome).orElse(null);
        if (authoredType != null) {
            return ClientCoastalWaveProfiles.profile(authoredType);
        }
        if (GlacialBiomeManager.isGlacial(biome)
                || surface.is(Blocks.BLUE_ICE)
                || surface.is(Blocks.PACKED_ICE)) {
            return ClientCoastalWaveProfiles.profile(CoastalWaveProfile.ShoreType.GLACIAL);
        }
        if (surface.is(Blocks.GRAVEL)
                || (surface.is(BlockTags.BASE_STONE_OVERWORLD)
                && !surface.is(BlockTags.SAND))) {
            return ClientCoastalWaveProfiles.profile(CoastalWaveProfile.ShoreType.ROCKY);
        }
        float temperature = biome.value().getBaseTemperature();
        float downfall = biome.value().getModifiedClimateSettings().downfall();
        if (temperature < 0.30f || topY > 118) {
            return ClientCoastalWaveProfiles.profile(CoastalWaveProfile.ShoreType.COLD);
        }
        if (temperature >= 0.90f && downfall >= 0.55f) {
            return ClientCoastalWaveProfiles.profile(CoastalWaveProfile.ShoreType.TROPICAL);
        }
        if (temperature > 1.0f && downfall < 0.30f) {
            return ClientCoastalWaveProfiles.profile(CoastalWaveProfile.ShoreType.DUNE);
        }
        return ClientCoastalWaveProfiles.profile(CoastalWaveProfile.ShoreType.TEMPERATE);
    }

    private record Candidate(
            int waterX,
            float waterSurfaceY,
            int waterZ,
            int landwardX,
            int landwardZ,
            int direction,
            CoastalWaveProfile profile,
            List<CoastalSegment.RunUpCell> runUpCells,
            List<CoastalSegment.NearshoreCell> nearshoreCells,
            float slope,
            float averageWaterDepth,
            float underwaterSlope,
            int distanceSquared
    ) {
    }

    private record NearshoreProfile(
            List<CoastalSegment.NearshoreCell> cells,
            float averageDepth,
            float underwaterSlope
    ) {
    }

    private record SegmentKey(
            int bucketX,
            int bucketZ,
            int direction,
            CoastalWaveProfile.ShoreType shoreType
    ) {
        private static SegmentKey of(Candidate candidate) {
            return new SegmentKey(
                    Math.floorDiv(candidate.waterX(), SEGMENT_BUCKET_SIZE),
                    Math.floorDiv(candidate.waterZ(), SEGMENT_BUCKET_SIZE),
                    candidate.direction(),
                    candidate.profile().shoreType()
            );
        }
    }

    private static final class SegmentBuilder {
        private final SegmentKey key;
        private final CoastalWaveProfile profile;
        private final int landwardX;
        private final int landwardZ;
        private final List<CoastalSegment.ShorelinePoint> points = new ArrayList<>();
        private long summedX;
        private long summedZ;
        private float summedSurfaceY;
        private float summedSlope;
        private float summedWaterDepth;
        private float summedUnderwaterSlope;

        private SegmentBuilder(SegmentKey key, Candidate first) {
            this.key = key;
            this.profile = first.profile();
            this.landwardX = first.landwardX();
            this.landwardZ = first.landwardZ();
        }

        private void add(Candidate candidate) {
            if (points.size() >= MAX_POINTS_PER_SEGMENT) {
                return;
            }
            points.add(new CoastalSegment.ShorelinePoint(
                    candidate.waterX(),
                    candidate.waterSurfaceY(),
                    candidate.waterZ(),
                    candidate.runUpCells(),
                    candidate.nearshoreCells()
            ));
            summedX += candidate.waterX();
            summedZ += candidate.waterZ();
            summedSurfaceY += candidate.waterSurfaceY();
            summedSlope += candidate.slope();
            summedWaterDepth += candidate.averageWaterDepth();
            summedUnderwaterSlope += candidate.underwaterSlope();
        }

        private CoastalSegment build(int dimensionHash) {
            int count = Math.max(1, points.size());
            return new CoastalSegment(
                    stableId(dimensionHash, key),
                    profile,
                    (int) Math.round(summedX / (double) count),
                    summedSurfaceY / count,
                    (int) Math.round(summedZ / (double) count),
                    landwardX,
                    landwardZ,
                    summedSlope / count,
                    summedWaterDepth / count,
                    summedUnderwaterSlope / count,
                    points
            );
        }
    }

    private static long stableId(int dimensionHash, SegmentKey key) {
        long value = 0xcbf29ce484222325L;
        value = (value ^ dimensionHash) * 0x100000001b3L;
        value = (value ^ key.bucketX()) * 0x100000001b3L;
        value = (value ^ key.bucketZ()) * 0x100000001b3L;
        value = (value ^ key.direction()) * 0x100000001b3L;
        return (value ^ key.shoreType().ordinal()) * 0x100000001b3L;
    }
}
