package com.thunder.wildernessodysseyapi.client.biome;

import com.thunder.wildernessodysseyapi.item.ModSoundEvents;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;

final class ImpactSiteMusicSoundInstance extends AbstractTickableSoundInstance {
    private float targetVolume;

    ImpactSiteMusicSoundInstance() {
        super(ModSoundEvents.IMPACT_SITE_MUSIC.get(), SoundSource.MUSIC, RandomSource.create());
        this.looping = true;
        this.delay = 0;
        this.relative = true;
        this.volume = 0.0F;
        this.targetVolume = 0.0F;
    }

    void setTargetVolume(float targetVolume) {
        this.targetVolume = Math.max(0.0F, Math.min(1.0F, targetVolume));
    }

    @Override
    public boolean canStartSilent() {
        return true;
    }

    @Override
    public void tick() {
        if (this.targetVolume <= 0.001F && this.volume <= 0.001F) {
            this.stop();
            return;
        }

        float fadeStep = 0.02F;
        if (this.volume < this.targetVolume) {
            this.volume = Math.min(this.targetVolume, this.volume + fadeStep);
        } else if (this.volume > this.targetVolume) {
            this.volume = Math.max(this.targetVolume, this.volume - fadeStep);
        }
    }
}
