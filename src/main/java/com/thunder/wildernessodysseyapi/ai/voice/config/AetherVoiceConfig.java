package com.thunder.wildernessodysseyapi.ai.voice.config;

import com.thunder.wildernessodysseyapi.ai.voice.VoiceInputMode;
import com.thunder.wildernessodysseyapi.config.WildernessConfigSpecs;
import net.neoforged.neoforge.common.ModConfigSpec;

/** Client-only settings for local microphone, speech, subtitle, and authored narration behavior. */
public final class AetherVoiceConfig {
    public static ModConfigSpec CONFIG_SPEC;
    public static ModConfigSpec.BooleanValue VOICE_ENABLED;
    public static ModConfigSpec.EnumValue<VoiceInputMode> INPUT_MODE;
    public static ModConfigSpec.ConfigValue<String> SERVICE_ENDPOINT;
    public static ModConfigSpec.ConfigValue<String> SERVICE_TOKEN;
    public static ModConfigSpec.ConfigValue<String> VOICE_NAME;
    public static ModConfigSpec.DoubleValue VOICE_VOLUME;
    public static ModConfigSpec.DoubleValue SPEECH_SPEED;
    public static ModConfigSpec.BooleanValue SUBTITLES;
    public static ModConfigSpec.BooleanValue RADIO_PROCESSING;
    public static ModConfigSpec.BooleanValue CINEMATIC_NARRATION;
    public static ModConfigSpec.BooleanValue LORE_READ_ALOUD;
    public static ModConfigSpec.IntValue REQUEST_TIMEOUT_SECONDS;

    static {
        WildernessConfigSpecs.initialize();
    }

    /** Defines the Aether voice category inside the unified client configuration. */
    public static void define(ModConfigSpec.Builder builder) {
        builder.comment("Optional local-only Aether speech, push-to-talk, cinematic narration, and lore reading.")
                .push("aether_voice");
        VOICE_ENABLED = builder
                .comment("Enable dynamically generated local speech playback. Text chat remains available when this is false or the service is unavailable; bundled cinematic narration has its own setting.")
                .define("enabled", false);
        INPUT_MODE = builder
                .comment("TEXT disables microphone capture. PUSH_TO_TALK uses the configurable Controls key. ALWAYS_LISTENING is reserved and currently behaves as TEXT.")
                .defineEnum("inputMode", VoiceInputMode.PUSH_TO_TALK);
        SERVICE_ENDPOINT = builder
                .comment("Loopback-only root URL of the separately started Aether voice service.")
                .define("serviceEndpoint", "http://127.0.0.1:8765");
        SERVICE_TOKEN = builder
                .comment("Optional bearer token shared with AETHER_VOICE_TOKEN. Leave blank only when local process isolation is sufficient.")
                .define("serviceToken", "");
        VOICE_NAME = builder
                .comment("Kokoro voice id requested from the local service. The default is Aether's subdued, intimate caretaker voice.")
                .define("voiceName", "af_nicole");
        VOICE_VOLUME = builder
                .comment("Client-local generated voice volume, where 1.0 is full configured volume.")
                .defineInRange("volume", 0.85D, 0.0D, 1.0D);
        SPEECH_SPEED = builder
                .comment("Base Kokoro speech speed before subtle emotion adjustments. The default favors calm, deliberate delivery.")
                .defineInRange("speechSpeed", 0.96D, 0.75D, 1.25D);
        SUBTITLES = builder
                .comment("Show the spoken form as a temporary subtitle without creating another chat message.")
                .define("subtitles", true);
        RADIO_PROCESSING = builder
                .comment("Allow service-side radio/corruption processing requested by authored metadata. Disabled by default to preserve Aether's natural delivery.")
                .define("radioProcessing", false);
        CINEMATIC_NARRATION = builder
                .comment("Allow registered authored cinematic narration cues to play bundled speech through the shared audio owner.")
                .define("cinematicNarration", true);
        LORE_READ_ALOUD = builder
                .comment("Show the Read Aloud action for recovered Codex lore pages.")
                .define("loreReadAloud", true);
        REQUEST_TIMEOUT_SECONDS = builder
                .comment("Timeout for one local transcription or speech request. Minecraft threads never wait for it.")
                .defineInRange("requestTimeoutSeconds", 30, 3, 120);
        builder.pop();
    }

    private AetherVoiceConfig() {
    }
}
