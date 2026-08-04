package com.thunder.wildernessodysseyapi.ecosystem.debug;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import com.thunder.wildernessodysseyapi.core.ModAttachments;
import com.thunder.wildernessodysseyapi.ecosystem.api.SpeciesBehaviorProfile;
import com.thunder.wildernessodysseyapi.ecosystem.config.EcosystemConfig;
import com.thunder.wildernessodysseyapi.ecosystem.data.ModdedMobBehaviorDetector;
import com.thunder.wildernessodysseyapi.ecosystem.data.SpeciesBehaviorProfileManager;
import com.thunder.wildernessodysseyapi.ecosystem.service.EcosystemServices;
import com.thunder.wildernessodysseyapi.ecosystem.service.EcosystemUpdateBudget;
import com.thunder.wildernessodysseyapi.ecosystem.state.AnimalNeedsState;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
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
                .then(Commands.literal("profiles").executes(EcosystemDebugCommand::profiles))
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
                        + " jsonProfiles=" + SpeciesBehaviorProfileManager.profiles().size()
                        + " updateTicks=" + EcosystemConfig.BEHAVIOR_UPDATE_FREQUENCY.get()
                        + " searchCap=" + EcosystemConfig.MAXIMUM_SEARCH_RADIUS.get()), false);
        context.getSource().sendSuccess(() -> Component.literal(
                "  budget tick=" + budget.tick()
                        + " used=" + budget.used() + "/" + budget.limit()
                        + " denied=" + budget.denied()), false);
        return 1;
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
        for (SpeciesBehaviorProfile profile : SpeciesBehaviorProfileManager.profiles()) {
            context.getSource().sendSuccess(() -> Component.literal(
                    "json " + profile.id() + " entities=" + profile.entities()
                            + " tags=" + profile.entityTags()
                            + " drink=" + onOff(profile.drinking().enabled())
                            + " shelter=" + onOff(profile.shelter().enabled())
                            + " herd=" + onOff(profile.herd().enabled())
                            + " prey=" + onOff(profile.prey().enabled())
                            + " predator=" + onOff(profile.predator().enabled())), false);
        }
        return Math.max(1,
                EcosystemConfig.behaviorTagAssignmentCount()
                        + SpeciesBehaviorProfileManager.configuredProfiles().size()
                        + SpeciesBehaviorProfileManager.autoDetectedProfiles().size()
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
                        + " vanillaTarget=" + (animal.getTarget() == null
                        ? "none" : animal.getTarget().getDisplayName().getString())), false);
        source.sendSuccess(() -> Component.literal(
                "  water=" + position(needs.waterPosition())
                        + " shelter=" + position(needs.shelterPosition())
                        + " threat=" + position(needs.threatPosition())
                        + " threatExpires=" + needs.threatExpiresAt()), false);
        source.sendSuccess(() -> Component.literal(
                "  evaluation lastTick=" + needs.lastEvaluatedAt()
                        + " nextTick=" + needs.nextEvaluationAt()
                        + " durationMicros=" + needs.lastEvaluationNanos() / 1_000L), false);
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
}
