package com.thunder.wildernessodysseyapi.vegetation.api;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BooleanProperty;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Public compatibility registry for blocks that support regional vegetation reactions.
 *
 * <p>Registration is expected during common setup. Identity keys avoid registry
 * lookups in the hot path, and no mixin or block-entity installation is needed
 * for third-party plants.</p>
 */
public final class ReactivePlantRegistry {

    private static final Map<Block, ReactivePlantDefinition> DEFINITIONS =
            Collections.synchronizedMap(new IdentityHashMap<>());

    private ReactivePlantRegistry() {
    }

    /** Registers one block and rejects an accidental duplicate owner. */
    public static void register(Block block, ReactivePlantDefinition definition) {
        Block safeBlock = Objects.requireNonNull(block, "block");
        ReactivePlantDefinition safeDefinition = Objects.requireNonNull(definition, "definition");
        synchronized (DEFINITIONS) {
            ReactivePlantDefinition existing = DEFINITIONS.putIfAbsent(safeBlock, safeDefinition);
            if (existing != null) {
                throw new IllegalStateException("Reactive plant is already registered: " + safeBlock);
            }
        }
    }

    /**
     * Registers a flower whose existing block state contains an open/closed property.
     *
     * <p>The scheduler changes only that property and uses client-only update
     * flags, allowing the block's two models to carry the visual difference.</p>
     */
    public static void registerFlower(Block block, BooleanProperty openProperty) {
        Objects.requireNonNull(block, "block");
        Objects.requireNonNull(openProperty, "openProperty");
        if (!block.defaultBlockState().hasProperty(openProperty)) {
            throw new IllegalArgumentException("Flower block does not contain property " + openProperty.getName());
        }
        register(block, ReactivePlantDefinition.of(
                Set.of(ReactivePlantTrait.FLOWER),
                context -> context.state().setValue(openProperty, context.flowerShouldBeOpen())
        ));
    }

    /** Returns the immutable definition for a runtime block state, if registered. */
    public static Optional<ReactivePlantDefinition> definition(BlockState state) {
        Objects.requireNonNull(state, "state");
        return Optional.ofNullable(DEFINITIONS.get(state.getBlock()));
    }

    /** Returns a stable compatibility snapshot for diagnostics and tooling. */
    public static Map<Block, ReactivePlantDefinition> registrations() {
        synchronized (DEFINITIONS) {
            return Map.copyOf(DEFINITIONS);
        }
    }

    /** Registers built-in conservative behavior without overriding another owner. */
    public static boolean registerIfAbsent(Block block, ReactivePlantDefinition definition) {
        Objects.requireNonNull(block, "block");
        Objects.requireNonNull(definition, "definition");
        synchronized (DEFINITIONS) {
            if (DEFINITIONS.containsKey(block)) {
                return false;
            }
            DEFINITIONS.put(block, definition);
            return true;
        }
    }

    static BlockState resolve(
            ReactivePlantDefinition definition,
            ReactivePlantUpdateContext context
    ) {
        return definition.resolve(context);
    }
}
