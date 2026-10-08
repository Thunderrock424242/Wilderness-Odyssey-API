package com.thunder.wildernessodysseyapi.meteor.worldgen;

import com.thunder.wildernessodysseyapi.core.ModConstants;
import com.thunder.wildernessodysseyapi.environment.api.EnvironmentDimensionProfile;
import com.thunder.wildernessodysseyapi.temporalrift.echo.structure.EchoStructureMode;
import com.thunder.wildernessodysseyapi.temporalrift.echo.structure.EchoStructurePolicy;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.resources.ResourceKey;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.HolderSet;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.structure.StructureSet;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.levelgen.structure.StructureType;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.Optional;
import java.util.stream.Stream;

/** Registers the natural crater structure and its persisted bounded piece. */
public final class MeteorWorldgenRegistries {

    public static final ResourceLocation NATURAL_CRATER_SET =
            ResourceLocation.fromNamespaceAndPath(ModConstants.MOD_ID, "meteor_craters");

    private static final DeferredRegister<StructureType<?>> STRUCTURES =
            DeferredRegister.create(Registries.STRUCTURE_TYPE, ModConstants.MOD_ID);
    private static final DeferredRegister<StructurePieceType> PIECES =
            DeferredRegister.create(Registries.STRUCTURE_PIECE, ModConstants.MOD_ID);

    public static final DeferredHolder<StructureType<?>, StructureType<MeteorCraterStructure>> METEOR_CRATER =
            STRUCTURES.register("meteor_crater", () -> () -> MeteorCraterStructure.CODEC);
    public static final DeferredHolder<StructurePieceType, StructurePieceType> METEOR_CRATER_PIECE =
            PIECES.register("meteor_crater", () -> (StructurePieceType.ContextlessType) MeteorCraterPiece::new);

    private MeteorWorldgenRegistries() {
    }

    public static void register(IEventBus bus) {
        EchoStructurePolicy.register(NATURAL_CRATER_SET, EchoStructureMode.EARTH_ONLY);
        STRUCTURES.register(bus);
        PIECES.register(bus);
    }

    /** Filters natural candidates while retaining direct lookups for exclusion references. */
    public static HolderLookup<StructureSet> forDimension(HolderLookup<StructureSet> original,
                                                         ResourceKey<Level> dimension) {
        if (EnvironmentDimensionProfile.forDimension(dimension).naturalMeteors()) {
            return original;
        }
        return new HolderLookup<>() {
            @Override
            public Stream<Holder.Reference<StructureSet>> listElements() {
                return original.listElements().filter(holder -> !holder.key().location().equals(NATURAL_CRATER_SET));
            }

            @Override
            public Optional<Holder.Reference<StructureSet>> get(ResourceKey<StructureSet> key) {
                return original.get(key);
            }

            @Override
            public Optional<HolderSet.Named<StructureSet>> get(TagKey<StructureSet> tag) {
                return original.get(tag);
            }

            @Override
            public Stream<HolderSet.Named<StructureSet>> listTags() {
                return original.listTags();
            }
        };
    }
}
