package com.thunder.wildernessodysseyapi.developmentstudio.network;

import com.thunder.wildernessodysseyapi.core.ModConstants;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;

/** High-level structure-lab request with no client-controlled position or bounds. */
public record StudioStructureActionPayload(
        Action action,
        ResourceLocation structureId,
        Rotation rotation,
        Mirror mirror
) implements CustomPacketPayload {
    public static final Type<StudioStructureActionPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(ModConstants.MOD_ID, "studio_structure_action")
    );
    public static final StreamCodec<FriendlyByteBuf, StudioStructureActionPayload> STREAM_CODEC = StreamCodec.of(
            StudioStructureActionPayload::write,
            StudioStructureActionPayload::read
    );

    public StudioStructureActionPayload {
        action = action == null ? Action.PREVIEW_LAB : action;
        structureId = structureId == null
                ? ResourceLocation.fromNamespaceAndPath(ModConstants.MOD_ID, "development_studio_lab_fixture")
                : structureId;
        rotation = rotation == null ? Rotation.NONE : rotation;
        mirror = mirror == null ? Mirror.NONE : mirror;
    }

    private static void write(FriendlyByteBuf buffer, StudioStructureActionPayload payload) {
        buffer.writeEnum(payload.action);
        buffer.writeResourceLocation(payload.structureId);
        buffer.writeEnum(payload.rotation);
        buffer.writeEnum(payload.mirror);
    }

    private static StudioStructureActionPayload read(FriendlyByteBuf buffer) {
        return new StudioStructureActionPayload(
                buffer.readEnum(Action.class),
                buffer.readResourceLocation(),
                buffer.readEnum(Rotation.class),
                buffer.readEnum(Mirror.class)
        );
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public enum Action {
        PREVIEW_LAB,
        PREVIEW_HERE,
        PLACE_LAB,
        RESET_LAB,
        RELOAD_TEMPLATE
    }
}
