package com.thunder.wildernessodysseyapi.mixin;

import com.google.gson.JsonObject;
import net.minecraft.server.dedicated.DedicatedServerProperties;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Properties;

@Mixin(DedicatedServerProperties.class)
public abstract class DedicatedServerPropertiesMixin {

    @Shadow
    @Final
    @Mutable
    private DedicatedServerProperties.WorldDimensionData worldDimensionData;

    @Inject(method = "<init>", at = @At("TAIL"))
    private void wilderness$forceLargeBiomes(Properties properties, CallbackInfo ci) {
        String current = this.worldDimensionData.levelType();

        // Only replace the vanilla/default preset.
        // Keeps custom presets from other mods from getting stomped.
        if (current == null
                || current.isBlank()
                || current.equals("default")
                || current.equals("minecraft:normal")) {

            this.worldDimensionData = new DedicatedServerProperties.WorldDimensionData(
                    new JsonObject(),
                    "minecraft:large_biomes"
            );
        }
    }
}