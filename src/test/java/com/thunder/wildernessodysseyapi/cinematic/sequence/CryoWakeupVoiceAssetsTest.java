package com.thunder.wildernessodysseyapi.cinematic.sequence;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CryoWakeupVoiceAssetsTest {
    private static final String ROOT = "assets/wildernessodysseyapi/voice/cryo/";

    @Test
    void manifestMatchesEveryPackagedFemaleVoiceClipAndMeasuredDuration() throws Exception {
        ClassLoader loader = getClass().getClassLoader();
        try (InputStream input = loader.getResourceAsStream(ROOT + "manifest.json")) {
            assertNotNull(input, "missing cryo voice manifest");
            JsonObject manifest = JsonParser.parseReader(
                    new InputStreamReader(input, StandardCharsets.UTF_8)
            ).getAsJsonObject();
            assertEquals("Microsoft Zira Desktop", manifest.get("voice").getAsString());
            JsonObject clips = manifest.getAsJsonObject("clips");
            assertEquals(20, clips.size());
            for (String key : clips.keySet()) {
                JsonObject clip = clips.getAsJsonObject(key);
                String file = clip.get("file").getAsString();
                byte[] header;
                try (InputStream wav = loader.getResourceAsStream(ROOT + file)) {
                    assertNotNull(wav, "missing authored clip " + file);
                    header = wav.readNBytes(12);
                }
                assertEquals(12, header.length, "short WAV header for " + file);
                assertEquals("RIFF", new String(header, 0, 4, StandardCharsets.US_ASCII));
                assertEquals("WAVE", new String(header, 8, 4, StandardCharsets.US_ASCII));
                ResourceLocation cue = ResourceLocation.fromNamespaceAndPath(
                        "wildernessodysseyapi",
                        "cinematic/cryo_wakeup/narration/" + key
                );
                assertEquals(
                        clip.get("duration_ticks").getAsInt(),
                        CryoWakeupSequence.narrationDurationTicks(cue),
                        "duration drift for " + key
                );
                assertTrue(clip.get("duration_seconds").getAsDouble() > 0.0D);
            }
        }
    }
}
