package com.thunder.wildernessodysseyapi.temporalrift.echo.structure;

import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.HolderSet;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.levelgen.structure.StructureSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.stream.Stream;

/**
 * Opt-in structure-set policy for content registration before worlds are opened.
 * The immutable snapshot passed to worldgen preserves unmarked modded sets, holder identity,
 * placements, exclusion references and vanilla ring seeds. It never edits global registries.
 */
public final class EchoStructurePolicy {
    private static final Map<ResourceLocation, Rule> RULES = new LinkedHashMap<>();

    private EchoStructurePolicy() { }

    /** Registers a structure-set relationship during common setup; variants use registerVariant. */
    public static synchronized void register(ResourceLocation id, EchoStructureMode mode) {
        if (mode == EchoStructureMode.ECHO_VARIANT) throw new IllegalArgumentException("Use registerVariant with an authored Echo set");
        Rule rule = new Rule(mode, null);
        requireCompatible(id, rule);
        RULES.put(id, rule);
    }

    /**
     * Replaces an Earth set with a separately registered Echo set and excludes that variant from Earth.
     * For corresponding coordinates, author matching placement salt/spacing/ring parameters in both sets.
     * If the target is absent, Echo keeps the source so a missing optional pack cannot erase content.
     */
    public static synchronized void registerVariant(ResourceLocation earthSet, ResourceLocation echoSet) {
        if (earthSet.equals(echoSet)) throw new IllegalArgumentException("A variant needs a distinct structure set");
        Rule source = new Rule(EchoStructureMode.ECHO_VARIANT, echoSet);
        Rule target = new Rule(EchoStructureMode.ECHO_ONLY, null);
        requireCompatible(earthSet, source);
        requireCompatible(echoSet, target);
        RULES.put(earthSet, source);
        RULES.put(echoSet, target);
    }

    /** Captures registration definitions without retaining any server or world object. */
    public static synchronized Map<ResourceLocation, Rule> snapshot() {
        return Map.copyOf(RULES);
    }

    /** Immutable content rule, also usable by future custom structure providers. */
    public record Rule(EchoStructureMode mode, ResourceLocation variant) {
        public Rule {
            java.util.Objects.requireNonNull(mode);
            if ((mode == EchoStructureMode.ECHO_VARIANT) != (variant != null)) {
                throw new IllegalArgumentException("Only ECHO_VARIANT rules require a target");
            }
        }
    }

    /** Pure resolution shared by the generation lookup and content integration tests. */
    public static Optional<ResourceLocation> resolve(ResourceLocation id, boolean echo,
            Map<ResourceLocation, Rule> rules, Predicate<ResourceLocation> exists) {
        Rule rule = rules.get(id);
        if (rule == null) return Optional.of(id);
        if (echo && rule.mode == EchoStructureMode.EARTH_ONLY
                || !echo && rule.mode == EchoStructureMode.ECHO_ONLY) return Optional.empty();
        if (echo && rule.mode == EchoStructureMode.ECHO_VARIANT && exists.test(rule.variant)) {
            return Optional.of(rule.variant);
        }
        return Optional.of(id);
    }

    /** Filters only the generation candidates; direct lookups retain original exclusion-zone references. */
    public static HolderLookup<StructureSet> forDimension(HolderLookup<StructureSet> original, boolean echo) {
        Map<ResourceLocation, Rule> rules = snapshot();
        if (rules.isEmpty()) return original;
        Map<ResourceLocation, Holder.Reference<StructureSet>> available = new LinkedHashMap<>();
        original.listElements().forEach(holder -> available.put(holder.key().location(), holder));
        Map<ResourceLocation, Holder.Reference<StructureSet>> selected = new LinkedHashMap<>();
        available.forEach((id, holder) -> resolve(id, echo, rules, available::containsKey)
                .ifPresent(target -> selected.putIfAbsent(target, available.get(target))));
        var candidates = java.util.List.copyOf(selected.values());
        return new HolderLookup<>() {
            @Override
            public Stream<Holder.Reference<StructureSet>> listElements() { return candidates.stream(); }
            @Override
            public Optional<Holder.Reference<StructureSet>> get(ResourceKey<StructureSet> key) { return original.get(key); }
            @Override
            public Optional<HolderSet.Named<StructureSet>> get(TagKey<StructureSet> tag) { return original.get(tag); }
            @Override
            public Stream<HolderSet.Named<StructureSet>> listTags() { return original.listTags(); }
        };
    }

    private static void requireCompatible(ResourceLocation id, Rule rule) {
        java.util.Objects.requireNonNull(id);
        Rule previous = RULES.get(id);
        if (previous != null && !previous.equals(rule)) throw new IllegalArgumentException("Conflicting Echo structure rule: " + id);
    }
}
