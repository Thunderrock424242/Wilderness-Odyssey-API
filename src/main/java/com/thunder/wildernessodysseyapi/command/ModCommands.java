package com.thunder.wildernessodysseyapi.command;

import com.mojang.brigadier.CommandDispatcher;
import com.thunder.wildernessodysseyapi.changelog.command.ChangelogCommand;
import com.thunder.wildernessodysseyapi.donations.command.DonateCommand;
import com.thunder.wildernessodysseyapi.ecosystem.debug.EcosystemDebugCommand;
import com.thunder.wildernessodysseyapi.faq.FaqCommand;
import com.thunder.wildernessodysseyapi.feedback.FeedbackCommand;
import com.thunder.wildernessodysseyapi.lorebook.command.LoreBookCommand;
import com.thunder.wildernessodysseyapi.meteor.command.MeteorCommand;
import com.thunder.wildernessodysseyapi.modlisttracker.commands.ConfigAuditCommand;
import com.thunder.wildernessodysseyapi.modlisttracker.commands.ConfigCleanupCommand;
import com.thunder.wildernessodysseyapi.modlisttracker.commands.ModListDiffCommand;
import com.thunder.wildernessodysseyapi.modlisttracker.commands.ModListVersionCommand;
import com.thunder.wildernessodysseyapi.modpack.structure.command.ModpackStructureCommand;
import com.thunder.wildernessodysseyapi.playtest.verification.MinecraftVerificationCommands;
import com.thunder.wildernessodysseyapi.telemetry.TelemetryQueueStatsCommand;
import com.thunder.wildernessodysseyapi.watersystem.water.command.WaterDebugCommand;
import com.thunder.wildernessodysseyapi.weather.debug.WeatherDebugCommand;
import com.thunder.wildernessodysseyapi.worldgen.command.StructureInfoCommand;
import com.thunder.wildernessodysseyapi.worldgen.command.StructurePlacementDebugCommand;
import com.thunder.wildernessodysseyapi.worldgen.command.WorldGenScanCommand;
import com.thunder.wildernessodysseyapi.worldupgrade.command.WorldUpgradeCommand;
import net.minecraft.commands.CommandSourceStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/**
 * Collects command registration without coupling command implementations to the mod entrypoint.
 */
public final class ModCommands {

    private ModCommands() {
    }

    /**
     * Registers server commands when NeoForge rebuilds the active command dispatcher.
     *
     * @param event the dispatcher registration event for the current server
     */
    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();
        ModListDiffCommand.register(dispatcher);
        ModListVersionCommand.register(dispatcher);
        ConfigAuditCommand.register(dispatcher);
        ConfigCleanupCommand.register(dispatcher);
        StructureInfoCommand.register(dispatcher);
        FaqCommand.register(dispatcher);
        DonateCommand.register(dispatcher);
        ChangelogCommand.register(dispatcher);
        WorldGenScanCommand.register(dispatcher);
        StructurePlacementDebugCommand.register(dispatcher);
        LoreBookCommand.register(dispatcher);
        ModpackStructureCommand.register(dispatcher);
        TelemetryQueueStatsCommand.register(dispatcher);
        FeedbackCommand.register(dispatcher);
        WorldUpgradeCommand.register(dispatcher);
        MeteorCommand.register(dispatcher);
        UnstuckCommand.register(dispatcher);
        MinecraftVerificationCommands.register(dispatcher);
        WaterDebugCommand.register(dispatcher);
        WeatherDebugCommand.register(dispatcher);
        EcosystemDebugCommand.register(dispatcher);
    }
}
