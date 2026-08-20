package com.thunder.wildernessodysseyapi.worldgen.processor;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.thunder.wildernessodysseyapi.core.ModConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessor;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessorType;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import org.jetbrains.annotations.Nullable;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Final safety boundary for block-entity NBT in WO-owned structure templates.
 *
 * <p>The processor runs after caller-provided processors so it validates the
 * final block state. Invalid fields are discarded without loading or creating
 * a block entity during validation.</p>
 */
public final class BlockEntityNbtSanitizingProcessor extends StructureProcessor {
    private static final int MAXIMUM_LOGGED_SIGNATURES = 32;

    /** Data-driven codec retained even though runtime placers construct this processor directly. */
    public static final MapCodec<BlockEntityNbtSanitizingProcessor> CODEC =
            RecordCodecBuilder.mapCodec(instance -> instance.group(
                    ResourceLocation.CODEC.fieldOf("template")
                            .forGetter(processor -> processor.templateId)
            ).apply(instance, BlockEntityNbtSanitizingProcessor::new));

    private final ResourceLocation templateId;
    private final Set<String> loggedSignatures = ConcurrentHashMap.newKeySet();
    private final AtomicBoolean loggedLimit = new AtomicBoolean();

    /** Creates a guard whose diagnostics identify the owning structure template. */
    public BlockEntityNbtSanitizingProcessor(ResourceLocation templateId) {
        this.templateId = templateId;
    }

    @Override
    protected StructureProcessorType<?> getType() {
        return ModProcessors.BLOCK_ENTITY_NBT_SANITIZER.get();
    }

    @Override
    public StructureTemplate.StructureBlockInfo process(
            LevelReader level,
            BlockPos pos,
            BlockPos pivot,
            StructureTemplate.StructureBlockInfo raw,
            StructureTemplate.StructureBlockInfo placed,
            StructurePlaceSettings settings,
            @Nullable StructureTemplate template
    ) {
        Validation validation = validate(placed.state(), placed.nbt());
        if (validation.compatible()) {
            return placed;
        }

        logDiscard(placed, validation);
        // A non-null empty compound deliberately keeps vanilla's barrier-first
        // placement path. It clears any previous block entity before the final
        // state is installed, while none of the incompatible fields are loaded.
        return new StructureTemplate.StructureBlockInfo(
                placed.pos(),
                placed.state(),
                new CompoundTag()
        );
    }

    static Validation validate(BlockState state, @Nullable CompoundTag nbt) {
        if (nbt == null) {
            return Validation.valid();
        }
        if (!state.hasBlockEntity()) {
            return Validation.invalid(readId(nbt), "final block does not support a block entity");
        }
        if (!nbt.contains("id", Tag.TAG_STRING)) {
            return validateMetadata(true, null, false, false);
        }

        String rawId = nbt.getString("id");
        ResourceLocation id = ResourceLocation.tryParse(rawId);
        if (id == null) {
            return validateMetadata(true, rawId, false, false);
        }
        BlockEntityType<?> type = BuiltInRegistries.BLOCK_ENTITY_TYPE.getOptional(id).orElse(null);
        return validateMetadata(true, rawId, type != null, type != null && type.isValid(state));
    }

    static Validation validateMetadata(
            boolean blockSupportsBlockEntity,
            @Nullable String rawId,
            boolean registered,
            boolean validForFinalState
    ) {
        if (!blockSupportsBlockEntity) {
            return Validation.invalid(rawId == null ? "<missing>" : rawId,
                    "final block does not support a block entity");
        }
        if (rawId == null || rawId.isBlank()) {
            return Validation.invalid("<missing>", "block entity id is missing");
        }
        if (ResourceLocation.tryParse(rawId) == null) {
            return Validation.invalid(rawId, "block entity id is malformed");
        }
        if (!registered) {
            return Validation.invalid(rawId, "block entity id is not registered");
        }
        if (!validForFinalState) {
            return Validation.invalid(rawId, "block entity type is incompatible with the final block");
        }
        return Validation.valid();
    }

    private void logDiscard(StructureTemplate.StructureBlockInfo placed, Validation validation) {
        ResourceLocation blockId = BuiltInRegistries.BLOCK.getKey(placed.state().getBlock());
        String signature = blockId + "|" + validation.nbtId() + "|" + validation.reason();
        if (loggedSignatures.size() < MAXIMUM_LOGGED_SIGNATURES && loggedSignatures.add(signature)) {
            ModConstants.LOGGER.warn(
                    "[WO Structure] Discarded incompatible block entity NBT: template={}, position={}, block={}, nbtId={}, reason={}.",
                    templateId,
                    placed.pos(),
                    blockId,
                    validation.nbtId(),
                    validation.reason()
            );
        } else if (loggedSignatures.size() >= MAXIMUM_LOGGED_SIGNATURES
                && loggedLimit.compareAndSet(false, true)) {
            ModConstants.LOGGER.warn(
                    "[WO Structure] Further incompatible block-entity diagnostics for template {} are suppressed after {} unique signatures.",
                    templateId,
                    MAXIMUM_LOGGED_SIGNATURES
            );
        }
    }

    private static String readId(CompoundTag nbt) {
        return nbt.contains("id", Tag.TAG_STRING) ? nbt.getString("id") : "<missing>";
    }

    record Validation(boolean compatible, String nbtId, String reason) {
        private static Validation valid() {
            return new Validation(true, "<none>", "compatible");
        }

        private static Validation invalid(String nbtId, String reason) {
            return new Validation(false, nbtId, reason);
        }
    }
}
