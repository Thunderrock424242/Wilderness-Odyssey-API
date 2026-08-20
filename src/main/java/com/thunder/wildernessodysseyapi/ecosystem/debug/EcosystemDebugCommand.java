package com.thunder.wildernessodysseyapi.ecosystem.debug;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import com.thunder.wildernessodysseyapi.core.ModAttachments;
import com.thunder.wildernessodysseyapi.ecosystem.api.SpeciesBehaviorProfile;
import com.thunder.wildernessodysseyapi.ecosystem.config.EcosystemConfig;
import com.thunder.wildernessodysseyapi.ecosystem.data.ModdedMobBehaviorDetector;
import com.thunder.wildernessodysseyapi.ecosystem.data.SpeciesBehaviorProfileManager;
import com.thunder.wildernessodysseyapi.ecosystem.distant.DistantWildlifeGroup;
import com.thunder.wildernessodysseyapi.ecosystem.distant.DistantWildlifeManager;
import com.thunder.wildernessodysseyapi.ecosystem.distant.DistantWildlifeSavedData;
import com.thunder.wildernessodysseyapi.ecosystem.distant.DistantWildlifeTransitionPolicy;
import com.thunder.wildernessodysseyapi.ecosystem.group.AnimalGroup;
import com.thunder.wildernessodysseyapi.ecosystem.memory.EnvironmentalMemory;
import com.thunder.wildernessodysseyapi.ecosystem.memory.EnvironmentalMemoryManager;
import com.thunder.wildernessodysseyapi.ecosystem.service.EcosystemServices;
import com.thunder.wildernessodysseyapi.ecosystem.service.EcosystemUpdateBudget;
import com.thunder.wildernessodysseyapi.ecosystem.simulation.EcosystemSimulationManager;
import com.thunder.wildernessodysseyapi.ecosystem.state.AnimalNeedsState;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.PathfinderMob;

import java.util.Locale;
import java.util.Optional;

/** Operator diagnostics for profiled animals and the current evaluation budget. */
public final class EcosystemDebugCommand {

    private EcosystemDebugCommand() {
    }

    /** Registers disabled-by-default server diagnostics under {@code /woecosystem}. */
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("woecosystem")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("status").executes(EcosystemDebugCommand::status))
                .then(Commands.literal("distant").executes(EcosystemDebugCommand::distant))
                .then(Commands.literal("profiles").executes(EcosystemDebugCommand::profiles))
                .then(Commands.literal("memory")
                        .executes(context -> memory(context, context.getSource().getPlayerOrException().blockPosition()))
                        .then(Commands.literal("clear").executes(EcosystemDebugCommand::clearMemory))
                        .then(Commands.argument("pos", BlockPosArgument.blockPos())
                                .executes(context -> memory(
                                        context,
                                        BlockPosArgument.getLoadedBlockPos(context, "pos")))))
                .then(Commands.literal("inspect")
                        .then(Commands.argument("entity", EntityArgument.entity())
                                .executes(EcosystemDebugCommand::inspect))));
    }

    private static int status(CommandContext<CommandSourceStack> context) {
        if (!debugEnabled(context.getSource())) {
            return 0;
        }
        EcosystemUpdateBudget.Snapshot budget = EcosystemServices.budget().snapshot(context.getSource().getLevel());
        context.getSource().sendSuccess(() -> Component.literal(
                "WO ecosystem enabled=" + onOff(EcosystemConfig.ENABLED.get())
                        + " configAssignments=" + EcosystemConfig.behaviorTagAssignmentCount()
                        + " generatedProfiles=" + SpeciesBehaviorProfileManager.configuredProfiles().size()
                        + " autoDetection=" + onOff(EcosystemConfig.AUTO_DETECT_MODDED_ANIMALS.get())
                        + " autoDetectedProfiles=" + SpeciesBehaviorProfileManager.autoDetectedProfiles().size()
                        + " compatibilityProfiles=" + SpeciesBehaviorProfileManager.compatibilityProfiles().size()
                        + " jsonProfiles=" + SpeciesBehaviorProfileManager.profiles().size()
                        + " memoryCells=" + EnvironmentalMemoryManager.getActiveCellCount(context.getSource().getLevel())
                        + " updateTicks=" + EcosystemConfig.BEHAVIOR_UPDATE_FREQUENCY.get()
                        + " searchCap=" + EcosystemConfig.MAXIMUM_SEARCH_RADIUS.get()), false);
        context.getSource().sendSuccess(() -> Component.literal(
                "  budget tick=" + budget.tick()
                        + " used=" + budget.used() + "/" + budget.limit()
                        + " denied=" + budget.denied()), false);
        var simulation = EcosystemSimulationManager.get().metrics(context.getSource().getLevel());
        context.getSource().sendSuccess(() -> Component.literal(
                "  zones enabled=" + onOff(EcosystemConfig.SIMULATION_ZONES_ENABLED.get())
                        + " active/near/distant/dormant=" + simulation.activeCells()
                        + "/" + simulation.nearCells()
                        + "/" + simulation.distantCells()
                        + "/" + simulation.dormantCells()
                        + " fullEntities=" + simulation.fullySimulatedEntityCount()
                        + " abstractPopulation=" + simulation.abstractPopulationCount()), false);
        context.getSource().sendSuccess(() -> Component.literal(
                "  regionalUpdates=" + simulation.regionUpdates()
                        + "/" + EcosystemConfig.MAX_REGION_UPDATES_PER_TICK.get()
                        + " pendingRegionUpdates=" + simulation.pendingRegionalUpdates()
                        + " ecosystemMicros=" + simulation.updateNanos() / 1_000L
                        + " transitionRate=" + EcosystemConfig.ENTITY_TRANSITION_RATE.get()), false);
        context.getSource().sendSuccess(() -> Component.literal(
                "  wildlifeScan tick=" + simulation.wildlifeScanTick()
                        + " loadedEntities=" + simulation.scannedLoadedEntityCount()
                        + " profiledWildlife=" + simulation.profiledWildlifeCount()
                        + " scanMicros=" + simulation.wildlifeScanNanos() / 1_000L), false);
        var groups = EcosystemServices.groups().snapshot(context.getSource().getLevel());
        context.getSource().sendSuccess(() -> Component.literal(
                "  groupAI=" + onOff(EcosystemConfig.GROUP_AI_ENABLED.get())
                        + " groups=" + groups.groupCount()
                        + " members=" + groups.memberCount()
                        + " leaderDecisionsPerMinute=" + groups.leaderDecisionsPerMinute()
                        + " decisionTicks=" + EcosystemConfig.LEADER_DECISION_INTERVAL.get()
                        + " validationTicks=" + EcosystemConfig.MEMBER_VALIDATION_INTERVAL.get()), false);
        DistantWildlifeManager.Diagnostics distant = DistantWildlifeManager.get()
                .diagnostics(context.getSource().getLevel());
        context.getSource().sendSuccess(() -> Component.literal(
                "  distant enabled=" + onOff(distant.enabled())
                        + " groups=" + distant.groups()
                        + " represented=" + distant.representedAnimals()
                        + " avoidedEntities~=" + distant.representedAnimals()
                        + " absorbed/materialized=" + distant.absorbedLastUpdate()
                        + "/" + distant.materializedLastUpdate()
                        + " packets=" + distant.packetsLastUpdate()
                        + " updateMicros=" + distant.lastUpdateMicros()), false);
        return 1;
    }

    private static int distant(CommandContext<CommandSourceStack> context) {
        if (!debugEnabled(context.getSource())) {
            return 0;
        }
        var source = context.getSource();
        var settings = EcosystemConfig.distantWildlifeSettings();
        DistantWildlifeSavedData data = DistantWildlifeSavedData.get(source.getLevel());
        source.sendSuccess(() -> Component.literal(
                "WO distant wildlife enabled=" + onOff(settings.enabled())
                        + " groups=" + data.groups().size() + "/" + settings.maximumGroups()
                        + " represented=" + data.representedAnimals()
                        + "/" + settings.maximumRepresentedAnimals()
                        + " distances=" + settings.realEntityDistance()
                        + "+" + settings.transitionBuffer()
                        + ".." + settings.distantWildlifeDistance()
                        + " updateTicks=" + settings.updateInterval()), false);
        long gameTime = source.getLevel().getGameTime();
        for (DistantWildlifeGroup group : data.groups()) {
            var position = group.positionAt(gameTime);
            double distance = position.distanceTo(source.getPosition());
            var lod = DistantWildlifeTransitionPolicy.lodState(
                    distance,
                    settings.realEntityDistance(),
                    settings.distantWildlifeDistance(),
                    settings.transitionBuffer()
            );
            source.sendSuccess(() -> Component.literal(
                    "  #" + group.id()
                            + " " + group.species()
                            + " population=" + group.populationEstimate()
                            + " pos=" + number(position.x) + "," + number(position.y) + "," + number(position.z)
                            + " speed=" + number(group.speed())
                            + " form=" + group.form()
                            + " lod=" + lod
                            + " foodPressure=" + number(group.foodPressure())
                            + " water=" + number(group.waterAvailability())
                            + " disturbance=" + number(group.disturbance())
                            + " weatherImpact=" + number(group.weatherImpact())), false);
        }
        return Math.max(1, data.groups().size());
    }

    private static int memory(CommandContext<CommandSourceStack> context, BlockPos position) {
        CommandSourceStack source = context.getSource();
        if (!debugEnabled(source)) {
            return 0;
        }
        Optional<EnvironmentalMemory> memory = EnvironmentalMemoryManager.getMemory(source.getLevel(), position);
        int activeCells = EnvironmentalMemoryManager.getActiveCellCount(source.getLevel());
        if (memory.isEmpty()) {
            var cell = new net.minecraft.world.level.ChunkPos(position);
            source.sendSuccess(() -> Component.literal(
                    "WO memory cell=" + cell.x + "," + cell.z
                            + " disturbance=0.000 storedCells=" + activeCells), false);
            return 1;
        }
        EnvironmentalMemory snapshot = memory.get();
        source.sendSuccess(() -> Component.literal(
                "WO memory cell=" + snapshot.cell().x + "," + snapshot.cell().z
                        + " disturbance=" + number(snapshot.disturbance())
                        + " traffic=" + number(snapshot.playerTraffic())
                        + " combat=" + number(snapshot.recentCombatActivity())
                        + " fire=" + number(snapshot.recentFireActivity())), false);
        source.sendSuccess(() -> Component.literal(
                "  lastUpdate=" + snapshot.lastUpdatedGameTime()
                        + " elapsed=" + snapshot.elapsedTicks()
                        + " lazyDecay=" + number(snapshot.disturbanceDecayApplied())
                        + " source=" + snapshot.lastSource().serializedName()
                        + " sourcePos=" + position(snapshot.lastSourcePosition())
                        + " sourceEntity=" + (snapshot.lastSourceId() == null ? "none" : snapshot.lastSourceId())), false);
        source.sendSuccess(() -> Component.literal("  storedCells=" + activeCells), false);
        return 1;
    }

    private static int clearMemory(CommandContext<CommandSourceStack> context)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        if (!debugEnabled(source)) {
            return 0;
        }
        BlockPos position = source.getPlayerOrException().blockPosition();
        boolean removed = EnvironmentalMemoryManager.clearRegion(
                source.getLevel(), new net.minecraft.world.level.ChunkPos(position));
        source.sendSuccess(() -> Component.literal(removed
                ? "Cleared the current environmental-memory chunk."
                : "The current chunk had no stored environmental memory."), true);
        return removed ? 1 : 0;
    }

    private static int profiles(CommandContext<CommandSourceStack> context) {
        if (!debugEnabled(context.getSource())) {
            return 0;
        }
        for (var rule : EcosystemConfig.behaviorTagRules()) {
            context.getSource().sendSuccess(() -> Component.literal(
                    "config " + rule.selectorExpression() + "="
                            + rule.behaviorTags().stream()
                            .map(tag -> tag.serializedName())
                            .sorted()
                            .toList()), false);
        }
        for (SpeciesBehaviorProfile profile : SpeciesBehaviorProfileManager.configuredProfiles()) {
            context.getSource().sendSuccess(() -> Component.literal(
                    "generated " + profile.id() + " entities=" + profile.entities()
                            + " herd=" + onOff(profile.herd().enabled())
                            + " prey=" + onOff(profile.prey().enabled())
                            + " predator=" + onOff(profile.predator().enabled())), false);
        }
        for (SpeciesBehaviorProfile profile : SpeciesBehaviorProfileManager.autoDetectedProfiles()) {
            context.getSource().sendSuccess(() -> Component.literal(
                    "auto-detected " + profile.id() + " entities=" + profile.entities()
                            + " drink=" + onOff(profile.drinking().enabled())
                            + " shelter=" + onOff(profile.shelter().enabled())
                            + " herd=" + onOff(profile.herd().enabled())
                            + " prey=" + onOff(profile.prey().enabled())
                            + " predator=" + onOff(profile.predator().enabled())), false);
        }
        for (SpeciesBehaviorProfile profile : SpeciesBehaviorProfileManager.compatibilityProfiles()) {
            context.getSource().sendSuccess(() -> Component.literal(
                    "compatibility " + profile.id() + " entities=" + profile.entities()
                            + " tags=" + profile.entityTags()
                            + " activeTime=" + profile.environment().activeTime().serializedName()
                            + " states=" + profile.environment().supportedStates()), false);
        }
        for (SpeciesBehaviorProfile profile : SpeciesBehaviorProfileManager.profiles()) {
            context.getSource().sendSuccess(() -> Component.literal(
                    "json " + profile.id() + " entities=" + profile.entities()
                            + " tags=" + profile.entityTags()
                            + " drink=" + onOff(profile.drinking().enabled())
                            + " shelter=" + onOff(profile.shelter().enabled())
                            + " herd=" + onOff(profile.herd().enabled())
                            + " prey=" + onOff(profile.prey().enabled())
                            + " predator=" + onOff(profile.predator().enabled())
                            + " activeTime=" + profile.environment().activeTime().serializedName()
                            + " states=" + profile.environment().supportedStates()), false);
        }
        return Math.max(1,
                EcosystemConfig.behaviorTagAssignmentCount()
                        + SpeciesBehaviorProfileManager.configuredProfiles().size()
                        + SpeciesBehaviorProfileManager.autoDetectedProfiles().size()
                        + SpeciesBehaviorProfileManager.compatibilityProfiles().size()
                        + SpeciesBehaviorProfileManager.profiles().size());
    }

    private static int inspect(CommandContext<CommandSourceStack> context) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        if (!debugEnabled(source)) {
            return 0;
        }
        Entity entity = EntityArgument.getEntity(context, "entity");
        if (!(entity instanceof PathfinderMob animal)) {
            source.sendFailure(Component.literal("Selected entity is not a pathfinding mob."));
            return 0;
        }
        Optional<SpeciesBehaviorProfile> profile = SpeciesBehaviorProfileManager.profileFor(animal);
        if (profile.isEmpty()) {
            source.sendFailure(Component.literal("Selected mob has no loaded ecosystem profile."));
            return 0;
        }
        AnimalNeedsState needs = animal.getData(ModAttachments.ANIMAL_NEEDS);
        source.sendSuccess(() -> Component.literal(
                "WO ecosystem " + animal.getDisplayName().getString()
                        + " profile=" + profile.get().id()
                        + " state=" + needs.behavior()), false);
        EcosystemConfig.behaviorTagsFor(animal).ifPresent(tags -> source.sendSuccess(() -> Component.literal(
                "  configBehaviorTags=" + tags.stream()
                        .map(tag -> tag.serializedName())
                        .sorted()
                        .toList()), false));
        if (profile.get().id().getPath().startsWith("detected/")) {
            ModdedMobBehaviorDetector.detect(animal).ifPresent(tags -> source.sendSuccess(() -> Component.literal(
                    "  autoDetectedBehaviorTags=" + tags.stream()
                            .map(tag -> tag.serializedName())
                            .sorted()
                            .toList()), false));
        }
        source.sendSuccess(() -> Component.literal(
                "  needs thirst=" + number(needs.thirst())
                        + " hunger=" + number(needs.hunger())
                        + " rest=" + number(needs.rest())
                        + " social=" + number(needs.social())
                        + " safetyConcern=" + number(needs.safetyConcern())), false);
        source.sendSuccess(() -> Component.literal(
                "  target=" + position(needs.behaviorTarget())
                        + " reason=" + needs.decisionReason()
                        + " weatherResponse=" + needs.weatherResponse().serializedName()
                        + " vanillaTarget=" + (animal.getTarget() == null
                        ? "none" : animal.getTarget().getDisplayName().getString())), false);
        source.sendSuccess(() -> Component.literal(
                "  water=" + position(needs.waterPosition())
                        + " shelter=" + position(needs.shelterPosition())
                        + " threat=" + position(needs.threatPosition())
                        + " threatExpires=" + needs.threatExpiresAt()), false);
        Optional<AnimalGroup> animalGroup = EcosystemServices.groups().groupFor(animal);
        if (animalGroup.isPresent()) {
            AnimalGroup group = animalGroup.get();
            String leader = EcosystemServices.groups().resolveLeader(group)
                    .map(loaded -> loaded.getDisplayName().getString() + "/" + shortId(loaded.getUUID()))
                    .orElse("unloaded/" + shortId(group.getLeader()));
            source.sendSuccess(() -> Component.literal(
                    "  group id=" + group.id()
                            + " role=" + group.roleOf(animal.getUUID()).orElse(null)
                            + " leader=" + leader
                            + " members=" + group.memberCount()
                            + " state=" + group.state()
                            + " destination=" + position(group.destination())
                            + " leaderDecisionsPerMinute="
                            + group.leaderDecisionsPerMinute(source.getLevel().getGameTime())), false);
        } else {
            source.sendSuccess(() -> Component.literal(
                    "  group=none (created lazily for eligible social profiles)"), false);
        }
        var reaction = EcosystemServices.stormReactions()
                .cached(animal, profile.get())
                .orElse(needs.weatherReaction());
        var forecast = reaction.forecast();
        var sensitivity = reaction.sensitivity();
        source.sendSuccess(() -> Component.literal(String.format(
                Locale.ROOT,
                "  incomingWeather=%s intensity=%.2f distance=%s ETA=%s response=%s group=%s(%d) decisionMaker=%s sensitivity=[distance=%d minimum=%.2f shelter=%s alertness=%.2f]",
                forecast.type().name().toLowerCase(Locale.ROOT),
                forecast.intensity(),
                forecast.incoming() ? String.format(Locale.ROOT, "%.0f", forecast.distanceBlocks()) : "none",
                forecast.incoming() ? String.format(Locale.ROOT, "%.1fmin", forecast.estimatedArrivalTicks() / 1_200.0) : "none",
                reaction.response().name().toLowerCase(Locale.ROOT),
                reaction.inherited() ? "follower" : "leader/individual",
                reaction.groupSize(),
                reaction.decisionMakerId() == null
                        ? "none" : reaction.decisionMakerId().toString().substring(0, 8),
                sensitivity.detectionDistanceBlocks(),
                sensitivity.minimumIntensity(),
                sensitivity.shelterPreference().name().toLowerCase(Locale.ROOT),
                sensitivity.alertness()
        )), false);
        EcosystemServices.groups().groupFor(animal).ifPresent(group ->
                source.sendSuccess(() -> Component.literal(
                        "  groupResponse=" + group.state().name().toLowerCase(Locale.ROOT)
                                + " role=" + group.roleOf(animal.getUUID())
                                .map(role -> role.name().toLowerCase(Locale.ROOT))
                                .orElse("none")
                                + " members=" + group.memberCount()
                                + " leader=" + group.getLeader().toString().substring(0, 8)
                                + " destination=" + position(group.destination())), false));
        source.sendSuccess(() -> Component.literal(
                "  evaluation lastTick=" + needs.lastEvaluatedAt()
                        + " nextTick=" + needs.nextEvaluationAt()
                        + " durationMicros=" + needs.lastEvaluationNanos() / 1_000L
                        + " zone=" + needs.simulationLod()
                        + " zoneSuspendedAi=" + needs.simulationAiSuspended()), false);
        EcosystemUpdateBudget.Snapshot budget = EcosystemServices.budget().snapshot(source.getLevel());
        source.sendSuccess(() -> Component.literal(
                "  levelBudget=" + budget.used() + "/" + budget.limit()
                        + " denied=" + budget.denied()), false);
        return 1;
    }

    private static boolean debugEnabled(CommandSourceStack source) {
        if (EcosystemConfig.DEBUG_COMMANDS_ENABLED.get()) {
            return true;
        }
        source.sendFailure(Component.literal(
                "Ecosystem diagnostics are disabled. Enable ecosystem.debugCommandsEnabled in the server config."));
        return false;
    }

    private static String position(net.minecraft.core.BlockPos position) {
        return position == null ? "none" : position.getX() + "," + position.getY() + "," + position.getZ();
    }

    private static String number(double value) {
        return String.format(Locale.ROOT, "%.3f", value);
    }

    private static String onOff(boolean value) {
        return value ? "ON" : "OFF";
    }

    private static String shortId(java.util.UUID id) {
        return id == null ? "none" : id.toString().substring(0, 8);
    }
}
