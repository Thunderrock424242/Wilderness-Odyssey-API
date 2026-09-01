package com.thunder.wildernessodysseyapi.environment.glacial.debug;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.thunder.wildernessodysseyapi.environment.glacial.GlacialBiomeManager;
import com.thunder.wildernessodysseyapi.environment.glacial.GlacialSeason;
import com.thunder.wildernessodysseyapi.environment.glacial.GlacialSeasonManager;
import com.thunder.wildernessodysseyapi.environment.glacial.GlacialSeasonSnapshot;
import com.thunder.wildernessodysseyapi.environment.glacial.GlacialWaterColorModel;
import com.thunder.wildernessodysseyapi.environment.glacial.network.GlacialSeasonSyncService;
import com.thunder.wildernessodysseyapi.environment.glacial.runtime.GlacialFreezeManager;
import com.thunder.wildernessodysseyapi.weather.api.SeasonalClimateState;
import com.thunder.wildernessodysseyapi.weather.api.WeatherServices;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;

import java.util.Locale;

/** Permission-gated glacier diagnostics and Wilderness-only season overrides. */
public final class GlacialDebugCommand {

    private GlacialDebugCommand() {
    }

    /** Registers equivalent {@code /wilderness glacier} and {@code /wo glacier} trees. */
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(root("wilderness"));
        dispatcher.register(root("wo"));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> root(String name) {
        LiteralArgumentBuilder<CommandSourceStack> season = Commands.literal("season");
        for (GlacialSeason value : GlacialSeason.values()) {
            season.then(Commands.literal(value.name().toLowerCase(Locale.ROOT))
                    .executes(context -> setSeason(context.getSource(), value)));
        }
        season.then(Commands.literal("clear")
                .executes(context -> clearSeason(context.getSource())));
        return Commands.literal(name)
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("glacier")
                        .executes(context -> info(context.getSource()))
                        .then(Commands.literal("info")
                                .executes(context -> info(context.getSource())))
                        .then(season));
    }

    private static int info(CommandSourceStack source) {
        ServerLevel level = source.getLevel();
        BlockPos position = BlockPos.containing(source.getPosition());
        var biome = level.getBiome(position);
        String biomeId = biome.unwrapKey().map(key -> key.location().toString()).orElse("unregistered");
        GlacialBiomeManager.Family family = GlacialBiomeManager.family(biome).orElse(null);
        SeasonalClimateState climate = WeatherServices.query().seasonalClimateAt(level, position);
        GlacialSeasonSnapshot season = GlacialSeasonManager.sample(level, position);
        int tint = family == null ? biome.value().getWaterColor()
                : GlacialWaterColorModel.surfaceTint(family, biome.value().getWaterColor(), season.meltFraction());
        GlacialFreezeManager.Diagnostics diagnostics = GlacialFreezeManager.diagnostics(level);
        source.sendSuccess(() -> Component.literal(String.format(
                Locale.ROOT,
                "Glacier at %d %d %d: biome %s, family %s, calendar %s, season %s%s, temperature offset %+.2f C, melt %.3f, freeze %.3f, water #%06X.",
                position.getX(), position.getY(), position.getZ(), biomeId,
                family == null ? "none" : family.name().toLowerCase(Locale.ROOT),
                GlacialSeasonManager.calendarSource(),
                season.season().name().toLowerCase(Locale.ROOT),
                season.debugOverride() ? " (override)" : "",
                climate.temperatureOffsetCelsius(),
                season.meltFraction(), season.freezeFraction(), tint & 0xFFFFFF
        )), false);
        source.sendSuccess(() -> Component.literal(String.format(
                Locale.ROOT,
                "Glacier scheduler: %d loaded glacial chunks; tick %d inspected %d bounded positions, changed %d (%d frozen, %d thawed).",
                diagnostics.loadedChunks(), diagnostics.tick(), diagnostics.inspected(),
                diagnostics.changed(), diagnostics.frozen(), diagnostics.thawed()
        )), false);
        return family == null ? 0 : 1;
    }

    private static int setSeason(CommandSourceStack source, GlacialSeason season) {
        GlacialSeasonManager.setDebugOverride(source.getLevel(), season);
        GlacialSeasonSyncService.publishNow(source.getLevel());
        source.sendSuccess(() -> Component.literal(
                "Set the Wilderness glacier override to "
                        + season.name().toLowerCase(Locale.ROOT)
                        + ". External season calendars were not changed."
        ), true);
        return 1;
    }

    private static int clearSeason(CommandSourceStack source) {
        boolean cleared = GlacialSeasonManager.clearDebugOverride(source.getLevel());
        GlacialSeasonSyncService.publishNow(source.getLevel());
        source.sendSuccess(() -> Component.literal(cleared
                ? "Cleared the Wilderness glacier override; calendar integration is active again."
                : "No Wilderness glacier override was active."), true);
        return cleared ? 1 : 0;
    }
}
