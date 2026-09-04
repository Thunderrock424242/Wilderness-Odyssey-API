package com.thunder.wildernessodysseyapi.watersystem.ocean.coast;

import com.thunder.wildernessodysseyapi.watersystem.ocean.OceanSeaState;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CoastalBreakAudioModelTest {

    private static final OceanSeaState.Sample STORM = new OceanSeaState.Sample(
            0.94f, 1.0f, 0.0f, 22.0f, 1.8f, 2.0f, 0.8f, 0.92f);

    @Test
    void calmDuneBreakIsAudibleEvenBelowTheOldSprayGate() {
        // The crest is raised before spray reaches its mid-break maximum.
        var wave = wave(CoastalWaveProfile.DUNE, OceanSeaState.CALM, 0.466f);
        assertTrue(wave.spray() < 0.055f);
        assertTrue(CoastalBreakAudioModel.isAudibleBreak(wave));
        assertTrue(CoastalBreakAudioModel.impactStrength(wave) > 0.4f);
    }

    @Test
    void disablingProfileTurbulenceDoesNotMuteTheBreakingWave() {
        var quietSpray = new CoastalWaveProfile(CoastalWaveProfile.ShoreType.DUNE,
                1.0f, 1.0f, 7.0f, 0.76f, 7.5f, 0.82f, 0.68f, 0.95f, 34.0f, 0.0f, 240);
        var wave = wave(quietSpray, OceanSeaState.CALM, 0.50f);
        assertEquals(0.0f, wave.spray());
        assertTrue(CoastalBreakAudioModel.isAudibleBreak(wave));
    }

    @Test
    void onlyTheRaisedBreakingCrestTriggersAudio() {
        for (float phase : new float[]{0.10f, 0.32f, 0.44f, 0.565f, 0.68f, 0.90f}) {
            assertFalse(CoastalBreakAudioModel.isAudibleBreak(
                    wave(CoastalWaveProfile.TEMPERATE, OceanSeaState.CALM, phase)), "phase " + phase);
        }
        assertTrue(CoastalBreakAudioModel.isAudibleBreak(
                wave(CoastalWaveProfile.TEMPERATE, OceanSeaState.CALM, 0.50f)));
    }

    @Test
    void allShoresHaveAnAudibleCalmFloorAndLouderStorms() {
        for (var type : CoastalWaveProfile.ShoreType.values()) {
            var profile = CoastalWaveProfile.forType(type);
            var calm = CoastalBreakAudioModel.mix(profile,
                    wave(profile, OceanSeaState.CALM, 0.50f), 1.0f, 42L);
            var storm = CoastalBreakAudioModel.mix(profile,
                    wave(profile, STORM, 0.50f), 1.0f, 42L);
            assertTrue(calm.impactVolume() >= 0.70f, type.toString());
            assertTrue(storm.impactVolume() > calm.impactVolume(), type.toString());
            assertTrue(storm.impactVolume() <= 2.0f);
            assertTrue(calm.washVolume() > 0.0f && calm.washVolume() < calm.impactVolume());
        }
    }

    @Test
    void userAndResourcePackMuteSilenceBothLayers() {
        var profile = CoastalWaveProfile.TEMPERATE;
        var wave = wave(profile, OceanSeaState.CALM, 0.50f);
        for (float volume : new float[]{0.0f, -1.0f, Float.NaN}) {
            var mix = CoastalBreakAudioModel.mix(profile, wave, volume, 5L);
            assertEquals(0.0f, mix.impactVolume());
            assertEquals(0.0f, mix.washVolume());
        }
        var muted = new CoastalWaveProfile(profile.shoreType(), 1.0f, 1.0f, 5.0f,
                1.0f, 5.0f, 1.0f, 1.0f, 0.0f, 36.0f, 1.0f, 180);
        assertEquals(0.0f, CoastalBreakAudioModel.mix(muted, wave, 1.0f, 5L).impactVolume());
    }

    @Test
    void variationIsDeterministicAndMaximumGainStaysBounded() {
        var profile = CoastalWaveProfile.ROCKY;
        var wave = wave(profile, STORM, 0.50f);
        assertEquals(CoastalBreakAudioModel.mix(profile, wave, 1.0f, 123L),
                CoastalBreakAudioModel.mix(profile, wave, 1.0f, 123L));
        for (long id = 0; id < 100; id++) {
            var mix = CoastalBreakAudioModel.mix(profile, wave, 2.0f, id);
            assertTrue(mix.impactVolume() <= 2.0f);
            assertTrue(mix.washVolume() <= 0.68f);
            assertTrue(mix.pitch() >= 0.82f && mix.pitch() <= 0.941f);
        }
    }

    private static CoastalWaveModel.Sample wave(
            CoastalWaveProfile profile, OceanSeaState.Sample sea, float phase
    ) {
        return CoastalWaveModel.sampleAtPhase(1L, phase, 7.4f, profile, sea, 0.05f, 0.0f);
    }
}
