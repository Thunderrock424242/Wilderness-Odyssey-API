package com.thunder.wildernessodysseyapi.item;

import com.thunder.wildernessodysseyapi.item.cloak.CloakItem;
import com.thunder.wildernessodysseyapi.item.cloak.BreathingMaskItem;
import com.thunder.wildernessodysseyapi.item.cloak.InhalerItem;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.JukeboxSong;
import net.minecraft.world.item.Rarity;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

import static com.thunder.wildernessodysseyapi.core.ModConstants.MOD_ID;

/**
 * The type Mod items.
 */
public class ModItems {
    /**
     * The constant ITEMS.
     */
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(MOD_ID);

    public static final DeferredItem<Item> CLOAK_ITEM = ITEMS.register(
            "cloak_item",
            () -> new CloakItem(new Item.Properties().stacksTo(1))
    );
    public static final DeferredItem<Item> INHALER = ITEMS.register(
            "inhaler",
            () -> new InhalerItem(new Item.Properties().stacksTo(1).durability(50))
    );
    public static final DeferredItem<Item> BREATHING_MASK = ITEMS.register(
            "breathing_mask",
            () -> new BreathingMaskItem(new Item.Properties().stacksTo(1))
    );

    public static final DeferredItem<Item> MUSIC_DISC_DUSTWINDS = ITEMS.register(
            "music_disc_dustwinds",
            () -> new Item(createMusicDiscProperties("dont_be_so_serious"))
    );

    public static final DeferredItem<Item> MUSIC_DISC_STARFALL = ITEMS.register(
            "music_disc_starfall",
            () -> new Item(createMusicDiscProperties("outside_the_box"))
    );

    private static Item.Properties createMusicDiscProperties(String songPath) {
        ResourceLocation songId = ResourceLocation.fromNamespaceAndPath(MOD_ID, songPath);
        ResourceKey<JukeboxSong> songKey = ResourceKey.create(Registries.JUKEBOX_SONG, songId);
        return new Item.Properties()
                .stacksTo(1)
                .rarity(Rarity.RARE)
                .jukeboxPlayable(songKey);
    }

    /**
     * Register.
     *
     * @param eventBus the event bus
     */
    public static void register(IEventBus eventBus) {
        ITEMS.register(eventBus);
    }
}
