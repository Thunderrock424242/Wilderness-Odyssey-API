package com.thunder.wildernessodysseyapi.ecosystem.config;

import com.thunder.wildernessodysseyapi.ecosystem.api.AnimalBehaviorTag;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;

/**
 * Immutable parsed behavior assignments from the ecosystem server config.
 *
 * <p>Selectors may be exact entity IDs or Minecraft entity-type tag IDs prefixed
 * with {@code #}. Exact assignments always win over broader tag assignments.</p>
 */
public final class BehaviorTagRules {

    public static final BehaviorTagRules EMPTY = new BehaviorTagRules(Map.of(), List.of(), List.of());

    private final Map<ResourceLocation, Set<AnimalBehaviorTag>> exact;
    private final List<Rule> entityTags;
    private final List<Rule> all;

    private BehaviorTagRules(
            Map<ResourceLocation, Set<AnimalBehaviorTag>> exact,
            List<Rule> entityTags,
            List<Rule> all
    ) {
        this.exact = exact;
        this.entityTags = entityTags;
        this.all = all;
    }

    /** Parses validated assignment strings into deterministic immutable rules. */
    public static BehaviorTagRules parse(List<? extends String> assignments) {
        Map<ResourceLocation, Set<AnimalBehaviorTag>> exact = new LinkedHashMap<>();
        List<Rule> entityTags = new ArrayList<>();
        List<Rule> all = new ArrayList<>();
        for (String assignment : assignments) {
            Rule rule = parseRule(assignment);
            all.add(rule);
            if (rule.entityTypeTag()) {
                entityTags.add(rule);
            } else {
                exact.put(rule.selector(), rule.behaviorTags());
            }
        }
        return new BehaviorTagRules(Map.copyOf(exact), List.copyOf(entityTags), List.copyOf(all));
    }

    /** Returns whether a raw value is a valid config assignment. */
    public static boolean isValid(Object value) {
        if (!(value instanceof String assignment)) {
            return false;
        }
        try {
            parseRule(assignment);
            return true;
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    /**
     * Resolves behavior tags for an entity.
     *
     * @param entityId exact entity type ID
     * @param entityTagMatcher predicate that checks membership in a tag ID
     */
    public Optional<Set<AnimalBehaviorTag>> resolve(
            ResourceLocation entityId,
            Predicate<ResourceLocation> entityTagMatcher
    ) {
        Set<AnimalBehaviorTag> exactMatch = exact.get(entityId);
        if (exactMatch != null) {
            return Optional.of(exactMatch);
        }
        for (Rule rule : entityTags) {
            if (entityTagMatcher.test(rule.selector())) {
                return Optional.of(rule.behaviorTags());
            }
        }
        return Optional.empty();
    }

    /** Returns every parsed rule in config order for diagnostics. */
    public List<Rule> rules() {
        return all;
    }

    /** Returns the number of exact and entity-tag selectors. */
    public int size() {
        return all.size();
    }

    private static Rule parseRule(String assignment) {
        if (assignment == null) {
            throw new IllegalArgumentException("behavior assignment cannot be null");
        }
        int separator = assignment.indexOf('=');
        if (separator <= 0 || separator == assignment.length() - 1) {
            throw new IllegalArgumentException("expected selector=behavior,behavior");
        }
        String selectorText = assignment.substring(0, separator).trim();
        boolean entityTypeTag = selectorText.startsWith("#");
        String locationText = entityTypeTag ? selectorText.substring(1).trim() : selectorText;
        ResourceLocation selector = ResourceLocation.tryParse(locationText);
        if (selector == null) {
            throw new IllegalArgumentException("invalid entity selector " + selectorText);
        }

        EnumSet<AnimalBehaviorTag> behaviorTags = EnumSet.noneOf(AnimalBehaviorTag.class);
        for (String rawTag : assignment.substring(separator + 1).split(",")) {
            AnimalBehaviorTag tag = AnimalBehaviorTag.parse(rawTag)
                    .orElseThrow(() -> new IllegalArgumentException("unknown behavior tag " + rawTag.trim()));
            behaviorTags.add(tag);
        }
        if (behaviorTags.isEmpty()) {
            throw new IllegalArgumentException("at least one behavior tag is required");
        }
        if (behaviorTags.contains(AnimalBehaviorTag.DISABLED) && behaviorTags.size() > 1) {
            throw new IllegalArgumentException("disabled cannot be combined with behavior tags");
        }
        return new Rule(entityTypeTag, selector, Set.copyOf(behaviorTags));
    }

    /** One exact or entity-type-tag selector and its generated behavior labels. */
    public record Rule(
            boolean entityTypeTag,
            ResourceLocation selector,
            Set<AnimalBehaviorTag> behaviorTags
    ) {
        /** Returns the selector in the same form accepted by config. */
        public String selectorExpression() {
            return (entityTypeTag ? "#" : "") + selector;
        }
    }
}
