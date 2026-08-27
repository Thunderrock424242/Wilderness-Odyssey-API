package com.thunder.wildernessodysseyapi.ai.voice.network;

import com.thunder.wildernessodysseyapi.ai.voice.VoiceEmotion;
import com.thunder.wildernessodysseyapi.ai.voice.VoiceLine;
import com.thunder.wildernessodysseyapi.core.ModConstants;
import io.netty.handler.codec.DecoderException;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Sends one verified Aether display/spoken response to the private integrated client. */
public record AetherVoiceLinePayload(long responseId, VoiceLine line) implements CustomPacketPayload {
    public static final Type<AetherVoiceLinePayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(ModConstants.MOD_ID, "aether_voice_line")
    );
    public static final StreamCodec<FriendlyByteBuf, AetherVoiceLinePayload> STREAM_CODEC = StreamCodec.of(
            AetherVoiceLinePayload::write,
            AetherVoiceLinePayload::read
    );

    public AetherVoiceLinePayload {
        if (responseId <= 0L) {
            throw new DecoderException("Invalid Aether voice response id");
        }
        if (line == null) {
            throw new DecoderException("Missing Aether voice line");
        }
        line = new VoiceLine(
                line.speaker(),
                line.displayText(),
                line.speechText(),
                line.emotion(),
                line.radioEffect()
        );
    }

    private static void write(FriendlyByteBuf buffer, AetherVoiceLinePayload payload) {
        VoiceLine line = payload.line;
        buffer.writeVarLong(payload.responseId);
        buffer.writeUtf(line.speaker(), VoiceLine.MAX_SPEAKER_CHARACTERS);
        buffer.writeUtf(line.displayText(), VoiceLine.MAX_DISPLAY_CHARACTERS);
        buffer.writeUtf(line.speechText(), com.thunder.wildernessodysseyapi.ai.voice.VoiceTextSanitizer.MAX_SPEECH_CHARACTERS);
        buffer.writeUtf(line.emotion().wireName(), 16);
        buffer.writeFloat(line.radioEffect());
    }

    private static AetherVoiceLinePayload read(FriendlyByteBuf buffer) {
        long responseId = buffer.readVarLong();
        String speaker = buffer.readUtf(VoiceLine.MAX_SPEAKER_CHARACTERS);
        String display = buffer.readUtf(VoiceLine.MAX_DISPLAY_CHARACTERS);
        String speech = buffer.readUtf(com.thunder.wildernessodysseyapi.ai.voice.VoiceTextSanitizer.MAX_SPEECH_CHARACTERS);
        VoiceEmotion emotion = VoiceEmotion.fromModelValue(buffer.readUtf(16));
        float radioEffect = buffer.readFloat();
        if (!Float.isFinite(radioEffect)) {
            throw new DecoderException("Invalid Aether voice radio effect");
        }
        return new AetherVoiceLinePayload(
                responseId,
                new VoiceLine(speaker, display, speech, emotion, radioEffect)
        );
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
