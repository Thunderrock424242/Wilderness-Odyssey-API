package com.thunder.wildernessodysseyapi.watersystem.water.worldgen.river.bank;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.thunder.wildernessodysseyapi.core.ModConstants;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;

import java.util.Map;

/** Loads bounded palettes from data/&lt;namespace&gt;/river/banks without retaining registry holders. */
public final class RiverBankProfileReloadListener extends SimpleJsonResourceReloadListener {
    public RiverBankProfileReloadListener() { super(new Gson(), "river/banks"); }

    @Override
    protected void apply(Map<ResourceLocation, JsonElement> resources, ResourceManager manager, ProfilerFiller profiler) {
        try {
            RiverBankProfiles.reload(resources, RiverBankProfiles::supportedBed, RiverBankProfiles::supportedPlant);
            ModConstants.LOGGER.info("Loaded {} immutable river bank profiles", RiverBankProfiles.snapshot().profiles().size());
        } catch (RuntimeException exception) {
            ModConstants.LOGGER.error("Rejected river bank profile generation; retaining the last complete generation: {}", exception.getMessage());
        }
    }
}
