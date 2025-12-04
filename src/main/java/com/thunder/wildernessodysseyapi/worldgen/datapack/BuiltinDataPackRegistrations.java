package com.thunder.wildernessodysseyapi.WorldGen.datapack;

import com.thunder.wildernessodysseyapi.Core.ModConstants;
import net.minecraft.network.chat.Component;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.PathPackResources;
import net.minecraft.server.packs.PackLocationInfo;
import net.minecraft.server.packs.PackSelectionConfig;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackSource;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.AddPackFindersEvent;
import net.neoforged.bus.api.SubscribeEvent;

import java.nio.file.Path;
import java.util.Optional;

@EventBusSubscriber(modid = ModConstants.MOD_ID, bus = EventBusSubscriber.Bus.MOD)
public class BuiltinDataPackRegistrations {
    private static final String PLAINS_SPAWN_PACK_ID = ModConstants.MOD_ID + ":plains_spawn_pack";
    private static final String PLAINS_PACK_PATH = "builtin/plains_spawn";

    @SubscribeEvent
    public static void onAddPackFinders(AddPackFindersEvent event) {
        if (event.getPackType() != PackType.SERVER_DATA) {
            return;
        }

        var modFileInfo = ModList.get().getModFileById(ModConstants.MOD_ID);
        if (modFileInfo == null) {
            return;
        }

        Path packPath = modFileInfo.getFile().findResource(PLAINS_PACK_PATH);

        PackLocationInfo packLocationInfo = new PackLocationInfo(
                PLAINS_SPAWN_PACK_ID,
                Component.translatable("pack.%s.plains_spawn.title".formatted(ModConstants.MOD_ID)),
                PackSource.BUILT_IN,
                Optional.empty()
        );

        Pack.ResourcesSupplier supplier = new Pack.ResourcesSupplier() {
            @Override
            public PathPackResources openPrimary(PackLocationInfo location) {
                return new PathPackResources(location, packPath);
            }

            @Override
            public PathPackResources openFull(PackLocationInfo location, Pack.Metadata metadata) {
                return new PathPackResources(location, packPath);
            }
        };

        Pack pack = Pack.readMetaAndCreate(
                packLocationInfo,
                supplier,
                event.getPackType(),
                new PackSelectionConfig(true, Pack.Position.TOP, true)
        );

        if (pack != null) {
            event.addRepositorySource(consumer -> consumer.accept(pack));
        }
    }
}
