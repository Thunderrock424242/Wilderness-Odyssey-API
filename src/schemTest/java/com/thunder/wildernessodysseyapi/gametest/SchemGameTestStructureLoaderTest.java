package com.thunder.wildernessodysseyapi.gametest;

import com.sk89q.worldedit.WorldEditException;
import com.sk89q.worldedit.entity.BaseEntity;
import com.sk89q.worldedit.entity.Entity;
import com.sk89q.worldedit.extent.clipboard.Clipboard;
import com.sk89q.worldedit.function.operation.Operation;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.math.transform.Transform;
import com.sk89q.worldedit.regions.CuboidRegion;
import com.sk89q.worldedit.regions.Region;
import com.sk89q.worldedit.util.Location;
import com.sk89q.worldedit.util.formatting.text.Component;
import com.sk89q.worldedit.event.platform.PlatformsRegisteredEvent;
import com.sk89q.worldedit.world.block.BaseBlock;
import com.sk89q.worldedit.world.block.BlockState;
import com.sk89q.worldedit.world.block.BlockType;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeAll;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SchemGameTestStructureLoaderTest {

    @BeforeAll
    static void registerWorldEditPlatform() {
        var platformManager = com.sk89q.worldedit.WorldEdit.getInstance().getPlatformManager();
        boolean alreadyRegistered = platformManager.getPlatforms().stream().anyMatch(p -> p instanceof TestPlatform);
        if (!alreadyRegistered) {
            platformManager.register(new TestPlatform());
            platformManager.handlePlatformsRegistered(new PlatformsRegisteredEvent());
        }
    }

    @Test
    void convertsClipboardIntoStructureTemplate() {
        BlockType type = new BlockType("minecraft:stone");
        BlockState state = type.getDefaultState();
        BaseBlock baseBlock = state.toBaseBlock();

        Clipboard clipboard = new StubClipboard(BlockVector3.ZERO, BlockVector3.ONE, BlockVector3.ZERO, baseBlock);

        StructureTemplate template = SchematicClipboardAdapter.toTemplate(clipboard);
        assertEquals(new Vec3i(0, 0, 0), template.getSize(), "Template dimensions should mirror the clipboard bounds.");

        assertTrue(palettes(template).isEmpty() || palettes(template).getFirst().blocks().isEmpty(),
                "Empty clipboards should produce an empty palette without errors.");
    }

    @Test
    void ignoresUnsupportedExtensions() throws IOException {
        try (ByteArrayInputStream input = new ByteArrayInputStream(new byte[0])) {
            Optional<StructureTemplate> loaded = SchemGameTestStructureLoader.loadFromStream(".nbt", input);
            assertTrue(loaded.isEmpty(), "Unsupported formats should return an empty result.");
        }
    }

    private record StubClipboard(CuboidRegion region, BlockVector3 origin, BlockVector3 dimensions,
                                 BaseBlock block) implements Clipboard {

        StubClipboard(BlockVector3 min, BlockVector3 max, BlockVector3 dimensions, BaseBlock block) {
            this(new CuboidRegion(min, max), min, dimensions, block);
        }

        @Override
        public Region getRegion() {
            return region;
        }

        @Override
        public BlockVector3 getDimensions() {
            return dimensions;
        }

        @Override
        public BlockVector3 getOrigin() {
            return origin;
        }

        @Override
        public void setOrigin(BlockVector3 origin) {
        }

        @Override
        public boolean hasBiomes() {
            return false;
        }

        @Override
        public Clipboard transform(Transform transform) {
            return this;
        }

        @Override
        public BlockVector3 getMinimumPoint() {
            return region.getMinimumPoint();
        }

        @Override
        public BlockVector3 getMaximumPoint() {
            return region.getMaximumPoint();
        }

        @Override
        public List<? extends Entity> getEntities(Region region) {
            return Collections.emptyList();
        }

        @Override
        public List<? extends Entity> getEntities() {
            return Collections.emptyList();
        }

        @Override
        public Entity createEntity(Location location, BaseEntity baseEntity) {
            throw new UnsupportedOperationException("Entities are not supported in stub clipboard.");
        }

        @Override
        public com.sk89q.worldedit.world.block.BlockState getBlock(BlockVector3 position) {
            return block.toImmutableState();
        }

        @Override
        public BaseBlock getFullBlock(BlockVector3 position) {
            return block;
        }

        @Override
        public <T extends com.sk89q.worldedit.world.block.BlockStateHolder<T>> boolean setBlock(BlockVector3 position, T block) throws WorldEditException {
            throw new UnsupportedOperationException("Mutation is not supported in stub clipboard.");
        }

        @Override
        public boolean fullySupports3DBiomes() {
            return false;
        }

        @Override
        public boolean setBiome(com.sk89q.worldedit.math.BlockVector2 position, com.sk89q.worldedit.world.biome.BiomeType biome) {
            return false;
        }

        @Override
        public boolean setBiome(BlockVector3 position, com.sk89q.worldedit.world.biome.BiomeType biome) {
            return false;
        }

        @Override
        public Operation commit() {
            return null;
        }
    }

    private record TestPlatform() implements com.sk89q.worldedit.extension.platform.Platform {
        private static final TestRegistries REGISTRIES = new TestRegistries();
        private static final TestConfiguration CONFIGURATION = new TestConfiguration();

        @Override
        public com.sk89q.worldedit.util.io.ResourceLoader getResourceLoader() {
            return null;
        }

        @Override
        public com.sk89q.worldedit.util.translation.TranslationManager getTranslationManager() {
            return null;
        }

        @Override
        public com.sk89q.worldedit.world.registry.Registries getRegistries() {
            return REGISTRIES;
        }

        @Override
        public int getDataVersion() {
            return SharedConstants.getCurrentVersion().getDataVersion().getVersion();
        }

        @Override
        public com.sk89q.worldedit.world.DataFixer getDataFixer() {
            return null;
        }

        @Override
        public boolean isValidMobType(String type) {
            return false;
        }

        @Override
        public void reload() {
        }

        @Override
        public int schedule(long delay, long interval, Runnable task) {
            return 0;
        }

        @Override
        public List<? extends com.sk89q.worldedit.world.World> getWorlds() {
            return List.of();
        }

        @Override
        public com.sk89q.worldedit.entity.Player matchPlayer(com.sk89q.worldedit.entity.Player player) {
            return player;
        }

        @Override
        public com.sk89q.worldedit.world.World matchWorld(com.sk89q.worldedit.world.World world) {
            return world;
        }

        @Override
        public void registerCommands(org.enginehub.piston.CommandManager commandManager) {
        }

        @Override
        public void setGameHooksEnabled(boolean gameHooksEnabled) {
        }

        @Override
        public com.sk89q.worldedit.LocalConfiguration getConfiguration() {
            return CONFIGURATION;
        }

        @Override
        public String getVersion() {
            return "test";
        }

        @Override
        public String getPlatformName() {
            return "test";
        }

        @Override
        public String getPlatformVersion() {
            return "test";
        }

        @Override
        public java.util.Map<com.sk89q.worldedit.extension.platform.Capability, com.sk89q.worldedit.extension.platform.Preference> getCapabilities() {
            return java.util.Map.of(
                    com.sk89q.worldedit.extension.platform.Capability.GAME_HOOKS, com.sk89q.worldedit.extension.platform.Preference.NORMAL,
                    com.sk89q.worldedit.extension.platform.Capability.WORLD_EDITING, com.sk89q.worldedit.extension.platform.Preference.NORMAL
            );
        }

        @Override
        public Set<com.sk89q.worldedit.util.SideEffect> getSupportedSideEffects() {
            return Set.of();
        }

        @Override
        public long getTickCount() {
            return 0;
        }

        @Override
        public String id() {
            return "test";
        }
    }

    private static final class TestRegistries implements com.sk89q.worldedit.world.registry.Registries {
        private static final TestBlockRegistry BLOCK_REGISTRY = new TestBlockRegistry();
        private static final TestItemRegistry ITEM_REGISTRY = new TestItemRegistry();
        private static final TestEntityRegistry ENTITY_REGISTRY = new TestEntityRegistry();
        private static final TestBiomeRegistry BIOME_REGISTRY = new TestBiomeRegistry();
        private static final TestBlockCategoryRegistry BLOCK_CATEGORY_REGISTRY = new TestBlockCategoryRegistry();
        private static final TestItemCategoryRegistry ITEM_CATEGORY_REGISTRY = new TestItemCategoryRegistry();

        @Override
        public com.sk89q.worldedit.world.registry.BlockRegistry getBlockRegistry() {
            return BLOCK_REGISTRY;
        }

        @Override
        public com.sk89q.worldedit.world.registry.ItemRegistry getItemRegistry() {
            return ITEM_REGISTRY;
        }

        @Override
        public com.sk89q.worldedit.world.registry.EntityRegistry getEntityRegistry() {
            return ENTITY_REGISTRY;
        }

        @Override
        public com.sk89q.worldedit.world.registry.BiomeRegistry getBiomeRegistry() {
            return BIOME_REGISTRY;
        }

        @Override
        public com.sk89q.worldedit.world.registry.BlockCategoryRegistry getBlockCategoryRegistry() {
            return BLOCK_CATEGORY_REGISTRY;
        }

        @Override
        public com.sk89q.worldedit.world.registry.ItemCategoryRegistry getItemCategoryRegistry() {
            return ITEM_CATEGORY_REGISTRY;
        }
    }

    private static final class TestBlockRegistry implements com.sk89q.worldedit.world.registry.BlockRegistry {
        @Override
        public Component getRichName(BlockType type) {
            return null;
        }

        @Override
        public String getName(BlockType type) {
            return type.id();
        }

        @Override
        public com.sk89q.worldedit.world.registry.BlockMaterial getMaterial(BlockType type) {
            return new TestBlockMaterial();
        }

        @Override
        public java.util.Map<String, ? extends com.sk89q.worldedit.registry.state.Property<?>> getProperties(BlockType type) {
            return java.util.Collections.emptyMap();
        }

        @Override
        public OptionalInt getInternalBlockStateId(BlockState state) {
            return OptionalInt.empty();
        }
    }

    private static final class TestBlockMaterial implements com.sk89q.worldedit.world.registry.BlockMaterial {
        @Override
        public boolean isAir() {
            return false;
        }

        @Override
        public boolean isFullCube() {
            return true;
        }

        @Override
        public boolean isOpaque() {
            return true;
        }

        @Override
        public boolean isPowerSource() {
            return false;
        }

        @Override
        public boolean isLiquid() {
            return false;
        }

        @Override
        public boolean isSolid() {
            return true;
        }

        @Override
        public float getHardness() {
            return 1.0F;
        }

        @Override
        public float getResistance() {
            return 1.0F;
        }

        @Override
        public float getSlipperiness() {
            return 0.6F;
        }

        @Override
        public int getLightValue() {
            return 0;
        }

        @Override
        public boolean isFragileWhenPushed() {
            return false;
        }

        @Override
        public boolean isUnpushable() {
            return false;
        }

        @Override
        public boolean isTicksRandomly() {
            return false;
        }

        @Override
        public boolean isMovementBlocker() {
            return true;
        }

        @Override
        public boolean isBurnable() {
            return false;
        }

        @Override
        public boolean isToolRequired() {
            return false;
        }

        @Override
        public boolean isReplacedDuringPlacement() {
            return false;
        }

        @Override
        public boolean isTranslucent() {
            return false;
        }

        @Override
        public boolean hasContainer() {
            return false;
        }
    }

    private static final class TestItemRegistry implements com.sk89q.worldedit.world.registry.ItemRegistry {
        @Override
        public Component getRichName(com.sk89q.worldedit.world.item.ItemType type) {
            return null;
        }

        @Override
        public com.sk89q.worldedit.world.registry.ItemMaterial getMaterial(com.sk89q.worldedit.world.item.ItemType type) {
            return new TestItemMaterial();
        }
    }

    private static final class TestItemMaterial implements com.sk89q.worldedit.world.registry.ItemMaterial {
        @Override
        public int getMaxStackSize() {
            return 64;
        }

        @Override
        public int getMaxDamage() {
            return 0;
        }
    }

    private static final class TestEntityRegistry implements com.sk89q.worldedit.world.registry.EntityRegistry {
        @Override
        public BaseEntity createFromId(String id) {
            return null;
        }
    }

    private static final class TestBiomeRegistry implements com.sk89q.worldedit.world.registry.BiomeRegistry {
        @Override
        public Component getRichName(com.sk89q.worldedit.world.biome.BiomeType biomeType) {
            return null;
        }

        @Override
        public com.sk89q.worldedit.world.biome.BiomeData getData(com.sk89q.worldedit.world.biome.BiomeType biomeType) {
            return () -> "test";
        }
    }

    private static final class TestBlockCategoryRegistry implements com.sk89q.worldedit.world.registry.BlockCategoryRegistry {
        @Override
        public Set<com.sk89q.worldedit.world.block.BlockType> getCategorisedByName(String name) {
            return Set.of();
        }
    }

    private static final class TestItemCategoryRegistry implements com.sk89q.worldedit.world.registry.ItemCategoryRegistry {
        @Override
        public Set<com.sk89q.worldedit.world.item.ItemType> getCategorisedByName(String name) {
            return Set.of();
        }
    }

    private static final class TestConfiguration extends com.sk89q.worldedit.LocalConfiguration {
        @Override
        public void load() {
        }
    }

    @SuppressWarnings("unchecked")
    private static List<StructureTemplate.Palette> palettes(StructureTemplate template) {
        try {
            var field = StructureTemplate.class.getDeclaredField("palettes");
            field.setAccessible(true);
            return (List<StructureTemplate.Palette>) field.get(template);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("Unable to inspect structure palettes", e);
        }
    }
}
