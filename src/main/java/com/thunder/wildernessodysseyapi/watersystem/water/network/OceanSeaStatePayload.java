package com.thunder.wildernessodysseyapi.watersystem.water.network;

import com.thunder.wildernessodysseyapi.core.ModConstants;
import com.thunder.wildernessodysseyapi.watersystem.ocean.OceanSeaState;
import com.thunder.wildernessodysseyapi.watersystem.ocean.OceanSeaStateField;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;

/**
 * Synchronizes a bounded regional sea-state lattice to one client world.
 *
 * <p>The packet contains only already-calculated wave response. Clients never
 * infer server physics from unsynchronized vanilla rain flags, and a disabled
 * packet explicitly returns them to the vanilla/external weather fallback.</p>
 */
public record OceanSeaStatePayload(
        boolean localized,
        int cellSize,
        List<CellSnapshot> cells
) implements CustomPacketPayload {

    /** Maximum 9 by 9 regional lattice permitted by the server config. */
    public static final int MAX_CELLS = 81;
    private static final int MIN_CELL_SIZE = 64;
    private static final int MAX_CELL_SIZE = 512;

    /** Payload identifier used by NeoForge's play protocol. */
    public static final Type<OceanSeaStatePayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(ModConstants.MOD_ID, "ocean_sea_state")
    );
    /** Strict bounded codec for regional sea-state snapshots. */
    public static final StreamCodec<FriendlyByteBuf, OceanSeaStatePayload> STREAM_CODEC =
            StreamCodec.of(OceanSeaStatePayload::encode, OceanSeaStatePayload::decode);

    public OceanSeaStatePayload {
        if (cellSize < MIN_CELL_SIZE || cellSize > MAX_CELL_SIZE) {
            throw new IllegalArgumentException("Invalid sea-state cell size: " + cellSize);
        }
        cells = cells == null ? List.of() : List.copyOf(cells);
        if (cells.size() > MAX_CELLS) {
            throw new IllegalArgumentException("Sea-state payload exceeds " + MAX_CELLS + " cells");
        }
        if (!localized && !cells.isEmpty()) {
            cells = List.of();
        }
    }

    /** Creates a regional client snapshot from server field views. */
    public static OceanSeaStatePayload regional(
            int cellSize,
            List<OceanSeaStateField.CellView> views
    ) {
        List<CellSnapshot> cells = new ArrayList<>(views.size());
        for (OceanSeaStateField.CellView view : views) {
            cells.add(new CellSnapshot(view.cellX(), view.cellZ(), view.sample()));
        }
        return new OceanSeaStatePayload(true, cellSize, cells);
    }

    /** Tells a client to use synchronized vanilla or external weather again. */
    public static OceanSeaStatePayload disabled(int cellSize) {
        return new OceanSeaStatePayload(false, cellSize, List.of());
    }

    private static void encode(FriendlyByteBuf buffer, OceanSeaStatePayload payload) {
        buffer.writeBoolean(payload.localized);
        buffer.writeVarInt(payload.cellSize);
        buffer.writeVarInt(payload.cells.size());
        for (CellSnapshot cell : payload.cells) {
            buffer.writeInt(cell.cellX);
            buffer.writeInt(cell.cellZ);
            writeSample(buffer, cell.sample);
        }
    }

    private static OceanSeaStatePayload decode(FriendlyByteBuf buffer) {
        boolean localized = buffer.readBoolean();
        int cellSize = buffer.readVarInt();
        int count = buffer.readVarInt();
        if (count < 0 || count > MAX_CELLS) {
            throw new IllegalArgumentException("Invalid sea-state cell count: " + count);
        }
        List<CellSnapshot> cells = new ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            cells.add(new CellSnapshot(
                    buffer.readInt(),
                    buffer.readInt(),
                    readSample(buffer)
            ));
        }
        return new OceanSeaStatePayload(localized, cellSize, cells);
    }

    private static void writeSample(FriendlyByteBuf buffer, OceanSeaState.Sample sample) {
        OceanSeaState.Sample safe = sample == null ? OceanSeaState.CALM : sample;
        buffer.writeFloat(safe.strength());
        buffer.writeFloat(safe.windDirectionX());
        buffer.writeFloat(safe.windDirectionZ());
        buffer.writeFloat(safe.windSpeed());
        buffer.writeFloat(safe.swellScale());
        buffer.writeFloat(safe.chopScale());
        buffer.writeFloat(safe.directionBlend());
        buffer.writeFloat(safe.breakingStrength());
    }

    private static OceanSeaState.Sample readSample(FriendlyByteBuf buffer) {
        return new OceanSeaState.Sample(
                buffer.readFloat(),
                buffer.readFloat(),
                buffer.readFloat(),
                buffer.readFloat(),
                buffer.readFloat(),
                buffer.readFloat(),
                buffer.readFloat(),
                buffer.readFloat()
        );
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    /** One immutable regional cell and its bounded physical response. */
    public record CellSnapshot(int cellX, int cellZ, OceanSeaState.Sample sample) {
        public CellSnapshot {
            sample = sample == null ? OceanSeaState.CALM : sample;
        }

        /** Returns the stable packed signed cell key used by client stores. */
        public long packedKey() {
            return ((long) cellX << 32) | (cellZ & 0xFFFFFFFFL);
        }
    }
}
