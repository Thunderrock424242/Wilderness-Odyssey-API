package com.thunder.wildernessodysseyapi.worldgen.datapack;

import com.thunder.wildernessodysseyapi.Core.ModConstants;
import net.minecraft.network.chat.Component;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.PathPackResources;
import net.minecraft.server.packs.Pack;
import net.minecraft.server.packs.repository.PackSource;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.AddPackFindersEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.loading.moddiscovery.ModFile;

import java.nio.file.Path;
import java.util.function.Supplier;

@EventBusSubscriber(modid = ModConstants.MOD_ID, bus = EventBusSubscriber.Bus.MOD)
public class BuiltinDataPackRegistrations {
    private static final String PLAINS_SPAWN_PACK_ID = ModConstants.MOD_ID + ":plains_spawn_pack";
    private static final String PLAINS_PACK_PATH = "builtin/plains_spawn";

    @SubscribeEvent
    public static void onAddPackFinders(AddPackFindersEvent event) {
        if (event.getPackType() != PackType.SERVER_DATA) {
            return;
        }

        ModFile modFile = ModList.get().getModFileById(ModConstants.MOD_ID).getFile();
        Path packPath = modFile.findResource(PLAINS_PACK_PATH);

        Supplier<PathPackResources> supplier = () -> new PathPackResources(PLAINS_SPAWN_PACK_ID, true, packPath);

        Pack pack = Pack.readMetaAndCreate(
                PLAINS_SPAWN_PACK_ID,
                Component.translatable("pack.%s.plains_spawn.title".formatted(ModConstants.MOD_ID)),
                false,
                supplier,
                event.getPackType(),
                Pack.Position.BOTTOM,
                PackSource.BUILT_IN,
                false
        );

        if (pack != null) {
            event.addRepositorySource(consumer -> consumer.accept(pack));
        }
    }
}
