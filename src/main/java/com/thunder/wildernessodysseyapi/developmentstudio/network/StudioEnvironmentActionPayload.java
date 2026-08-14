package com.thunder.wildernessodysseyapi.developmentstudio.network;

import com.thunder.wildernessodysseyapi.core.ModConstants;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * High-level environment request resolved entirely at the server player's position.
 *
 * <p>No coordinates, cell keys, water amounts, or weather scalar values cross
 * this boundary. Weather experiments map only to existing bounded authority methods.</p>
 */
public record StudioEnvironmentActionPayload(Action action) implements CustomPacketPayload {
    public static final Type<StudioEnvironmentActionPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(ModConstants.MOD_ID, "studio_environment_action")
    );
    public static final StreamCodec<FriendlyByteBuf, StudioEnvironmentActionPayload> STREAM_CODEC = StreamCodec.of(
            (buffer, payload) -> buffer.writeEnum(payload.action),
            buffer -> new StudioEnvironmentActionPayload(buffer.readEnum(Action.class))
    );

    public StudioEnvironmentActionPayload {
        action = action == null ? Action.INSPECT_WORLDGEN : action;
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public enum Action {
        INSPECT_WATER("water"),
        INSPECT_ECOSYSTEM("ecosystem"),
        INSPECT_WEATHER("weather"),
        INSPECT_WORLDGEN("worldgen"),
        WEATHER_CLEAR("weather"),
        WEATHER_RAIN("weather"),
        WEATHER_SNOW("weather"),
        WEATHER_HAIL("weather");

        private final String modulePath;

        Action(String modulePath) {
            this.modulePath = modulePath;
        }

        public String modulePath() {
            return modulePath;
        }
    }
}
