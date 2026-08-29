package com.thunder.wildernessodysseyapi.cinematic.sequence;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CryoWakeupVoiceAssetsTest {
    private static final String ROOT = "assets/wildernessodysseyapi/voice/cryo/";
    private static final String NARRATION_PREFIX = "cinematic.wildernessodysseyapi.cryo.narration.";
    private static final int SUBTITLE_FADE_TICKS = 6;

    @Test
    void manifestMatchesSourceTextAndEveryMeasuredFemaleVoiceClip() throws Exception {
        ClassLoader loader = getClass().getClassLoader();
        JsonObject language;
        try (InputStream input = loader.getResourceAsStream("assets/wildernessodysseyapi/lang/en_us.json")) {
            assertNotNull(input, "missing English language source");
            language = JsonParser.parseReader(
                    new InputStreamReader(input, StandardCharsets.UTF_8)
            ).getAsJsonObject();
        }
        try (InputStream input = loader.getResourceAsStream(ROOT + "manifest.json")) {
            assertNotNull(input, "missing cryo voice manifest");
            JsonObject manifest = JsonParser.parseReader(
                    new InputStreamReader(input, StandardCharsets.UTF_8)
            ).getAsJsonObject();
            assertEquals("Kokoro-82M 0.9.4", manifest.get("engine").getAsString());
            assertEquals("af_bella", manifest.get("voice").getAsString());
            assertEquals("grounded_conversational_caretaker", manifest.get("style").getAsString());
            assertEquals(1.0D, manifest.get("speed").getAsDouble());
            assertEquals(24_000, manifest.get("sample_rate").getAsInt());
            JsonObject clips = manifest.getAsJsonObject("clips");
            assertEquals(20, clips.size());
            for (String key : clips.keySet()) {
                JsonObject clip = clips.getAsJsonObject(key);
                String file = clip.get("file").getAsString();
                assertEquals(
                        language.get(NARRATION_PREFIX + key).getAsString(),
                        clip.get("source_text").getAsString(),
                        "subtitle source drift for " + key
                );
                try (InputStream inputWav = loader.getResourceAsStream(ROOT + file)) {
                    assertNotNull(inputWav, "missing authored clip " + file);
                    try (AudioInputStream wav = AudioSystem.getAudioInputStream(inputWav)) {
                        AudioFormat format = wav.getFormat();
                        assertEquals(AudioFormat.Encoding.PCM_SIGNED, format.getEncoding());
                        assertEquals(24_000.0F, format.getSampleRate());
                        assertEquals(16, format.getSampleSizeInBits());
                        assertEquals(1, format.getChannels());
                        double measuredSeconds = wav.getFrameLength() / format.getFrameRate();
                        int measuredDurationTicks = (int) Math.ceil(measuredSeconds * 20.0D)
                                + SUBTITLE_FADE_TICKS;
                        assertEquals(
                                clip.get("duration_seconds").getAsDouble(),
                                measuredSeconds,
                                0.001D,
                                "WAV duration drift for " + key
                        );
                        assertEquals(
                                clip.get("duration_ticks").getAsInt(),
                                measuredDurationTicks,
                                "subtitle duration no longer matches WAV for " + key
                        );
                    }
                }
                ResourceLocation cue = ResourceLocation.fromNamespaceAndPath(
                        "wildernessodysseyapi",
                        "cinematic/cryo_wakeup/narration/" + key
                );
                assertEquals(
                        clip.get("duration_ticks").getAsInt(),
                        CryoWakeupSequence.narrationDurationTicks(cue),
                        "duration drift for " + key
                );
                assertTrue(clip.get("source_text").getAsString().length() <= 240);
            }
        }
    }
}
