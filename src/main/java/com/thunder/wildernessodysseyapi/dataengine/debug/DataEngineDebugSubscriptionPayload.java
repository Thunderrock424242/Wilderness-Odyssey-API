package com.thunder.wildernessodysseyapi.dataengine.debug;

import com.thunder.wildernessodysseyapi.core.ModConstants;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Infrequent client request to begin/end operator-only Data Engine debug sync. */
public record DataEngineDebugSubscriptionPayload(boolean subscribed) implements CustomPacketPayload {
    public static final Type<DataEngineDebugSubscriptionPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(ModConstants.MOD_ID, "data_engine_debug_subscription")
    );
    public static final StreamCodec<RegistryFriendlyByteBuf, DataEngineDebugSubscriptionPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.BOOL,
                    DataEngineDebugSubscriptionPayload::subscribed,
                    DataEngineDebugSubscriptionPayload::new
            );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
