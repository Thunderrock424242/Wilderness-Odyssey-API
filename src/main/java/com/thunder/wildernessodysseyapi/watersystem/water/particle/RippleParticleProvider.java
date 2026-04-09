package com.thunder.wildernessodysseyapi.watersystem.water.particle;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.*;
import net.minecraft.core.particles.SimpleParticleType;

/**
 * RippleParticleProvider + RippleParticle
 *
 * A sprite-based particle that renders as an expanding flat circle.
 * The particle relies on the ripple sprite defined in:
 *   assets/wilderness/particles/ripple.json
 *
 * Visual behaviour:
 *  - Starts small, expands outward over its lifetime
 *  - Fades from semi-transparent to invisible
 *  - No gravity, no movement
 */
public class RippleParticleProvider implements ParticleProvider<SimpleParticleType> {

    private final SpriteSet sprites;

    public RippleParticleProvider(SpriteSet sprites) {
        this.sprites = sprites;
    }

    @Override
    public Particle createParticle(SimpleParticleType type, ClientLevel level,
                                    double x, double y, double z,
                                    double dx, double dy, double dz) {
        return new RippleParticle(level, x, y, z, sprites);
    }

    // ---- Inner particle class ----

    public static class RippleParticle extends TextureSheetParticle {

        private static final int LIFETIME = 30; // ticks

        protected RippleParticle(ClientLevel level, double x, double y, double z,
                                   SpriteSet sprites) {
            super(level, x, y, z, 0, 0, 0);

            this.pickSprite(sprites);

            this.lifetime   = LIFETIME;
            this.hasPhysics = false;
            this.gravity    = 0f;
            this.xd = 0;
            this.yd = 0;
            this.zd = 0;

            // Start tiny
            this.quadSize = 0.05f;
            this.alpha    = 0.8f;
            this.rCol = 0.7f;
            this.gCol = 0.9f;
            this.bCol = 1.0f;
        }

        @Override
        public void tick() {
            super.tick();

            float life = (float) this.age / this.lifetime; // 0 → 1

            // Expand radius
            this.quadSize = 0.05f + life * 1.6f;

            // Fade out in second half of life
            if (life > 0.5f) {
                this.alpha = 0.8f * (1f - (life - 0.5f) * 2f);
            }
        }

        @Override
        public ParticleRenderType getRenderType() {
            return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
        }
    }
}
