package com.thunder.wildernessodysseyapi.HigherSmoke.Particles;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.SimpleAnimatedParticle;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.core.particles.SimpleParticleType;

public class CustomCampfireParticle extends SimpleAnimatedParticle {

    protected CustomCampfireParticle(ClientLevel level, double x, double y, double z, double xSpeed, double ySpeed, double zSpeed, SpriteSet sprite) {
        super(level, x, y, z, sprite, 0.1F); // Gravity is set to 0.1F for natural rise
        this.xd = xSpeed;
        this.yd = ySpeed;
        this.zd = zSpeed;
        this.lifetime = 200; // Allows particles to reach max build height
    }

    public static class Factory implements ParticleProvider<SimpleParticleType> {
        private final SpriteSet sprites;

        public Factory(SpriteSet sprites) {
            this.sprites = sprites;
        }

        /**
         * Creates a custom campfire particle with extended lifetime and adjusted vertical motion.
         */
        @Override
        public Particle createParticle(SimpleParticleType type, ClientLevel level, double x, double y, double z, double xSpeed, double ySpeed, double zSpeed) {
            return new CustomCampfireParticle(level, x, y, z, xSpeed, ySpeed + 0.1, zSpeed, this.sprites);
        }
    }
}
