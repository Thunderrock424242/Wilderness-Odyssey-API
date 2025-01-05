package com.thunder.wildernessodysseyapi.BugFixes.smoke.particles;

import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.client.multiplayer.ClientLevel;
import org.jetbrains.annotations.NotNull;

public class CustomSmokeParticle extends TextureSheetParticle {

    private final int maxBuildHeight;

    protected CustomSmokeParticle(ClientLevel world, double x, double y, double z, SpriteSet sprite, int maxHeight) {
        super(world, x, y, z);
        this.xd = 0; // Horizontal speed
        this.yd = 0.05; // Vertical speed
        this.zd = 0; // Horizontal speed
        this.lifetime = maxHeight * 2; // Roughly matches travel time to max height
        this.quadSize *= 2.0F; // Particle size
        this.maxBuildHeight = maxHeight;
        this.setSpriteFromAge(sprite);
    }

    @Override
    public @NotNull ParticleRenderType getRenderType() {
        return ParticleRenderType.PARTICLE_SHEET_OPAQUE;
    }

    @Override
    public void tick() {
        super.tick();

        // Gradually fade out as the particle nears max build height
        if (this.y >= maxBuildHeight - 5) {
            this.alpha = Math.max(0, this.alpha - 0.02F); // Reduce alpha each tick
        }

        // Despawn if it has faded out completely or reached max height
        if (this.y >= maxBuildHeight || this.alpha <= 0) {
            this.remove();
        }
    }
}
