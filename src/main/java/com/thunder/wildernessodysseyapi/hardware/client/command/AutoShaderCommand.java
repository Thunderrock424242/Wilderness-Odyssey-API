package com.thunder.wildernessodysseyapi.hardware.client.command;

import com.thunder.wildernessodysseyapi.Core.ModConstants;
import com.thunder.wildernessodysseyapi.hardware.client.hardware.HardwareRequirementChecker;
import com.thunder.wildernessodysseyapi.hardware.client.hardware.HardwareRequirementConfig;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.neoforged.fml.loading.FMLPaths;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumMap;
import java.util.Locale;
import java.util.Optional;
import java.util.Properties;
import java.util.stream.Stream;

/**
 * Client-side command that chooses the appropriate shader pack for the
 * current hardware tier and updates Iris' configuration to use it.
 */
public final class AutoShaderCommand {
    private AutoShaderCommand() {
    }

    public static int execute(CommandSourceStack source, HardwareRequirementChecker checker) {
        EnumMap<HardwareRequirementConfig.Tier, HardwareRequirementChecker.TierEvaluation> evaluations =
            checker.evaluateAll(checker.getSnapshot());

        Optional<HardwareRequirementConfig.Tier> maybeTier = checker.selectHighestMeetingTier(evaluations);
        if (maybeTier.isEmpty()) {
            source.sendFailure(Component.translatable("command.wildernessodyssey.autoshader.no_match"));
            return 0;
        }

        HardwareRequirementConfig.Tier tier = maybeTier.get();
        Optional<HardwareRequirementConfig.HardwareRequirementTier> tierConfig = checker.config().getTier(tier);
        if (tierConfig.isEmpty()) {
            source.sendFailure(Component.translatable("command.wildernessodyssey.autoshader.no_shader", tierComponent(tier)));
            return 0;
        }

        return applyShaderPack(source, tier, tierConfig.get());
    }

    public static int execute(CommandSourceStack source, HardwareRequirementChecker checker, HardwareRequirementConfig.Tier tier) {
        Optional<HardwareRequirementConfig.HardwareRequirementTier> tierConfig = checker.config().getTier(tier);
        if (tierConfig.isEmpty()) {
            source.sendFailure(Component.translatable("command.wildernessodyssey.autoshader.no_shader", tierComponent(tier)));
            return 0;
        }

        return applyShaderPack(source, tier, tierConfig.get());
    }

    private static int applyShaderPack(CommandSourceStack source, HardwareRequirementConfig.Tier tier,
                                       HardwareRequirementConfig.HardwareRequirementTier tierConfig) {
        Optional<String> shaderPack = tierConfig.shaderPack();
        if (shaderPack.isEmpty()) {
            source.sendFailure(Component.translatable("command.wildernessodyssey.autoshader.no_shader", tierComponent(tier)));
            return 0;
        }

        String desiredShaderPack = shaderPack.orElseThrow();
        ResolvedShaderPack resolved = resolveShaderPack(desiredShaderPack);

        try {
            updateIrisConfiguration(resolved.fileName());
        } catch (IOException ex) {
            ModConstants.LOGGER.error("Failed to update Iris configuration", ex);
            source.sendFailure(Component.translatable("command.wildernessodyssey.autoshader.write_error", ex.getMessage()));
            return 0;
        }

        ModConstants.LOGGER.info("Auto shader command set '{}' for tier {}", resolved.fileName(), tier.name());
        source.sendSuccess(() -> Component.translatable("command.wildernessodyssey.autoshader.success",
            resolved.fileName(), tierComponent(tier)), false);

        if (!resolved.exists()) {
            source.sendSuccess(() -> Component.translatable("command.wildernessodyssey.autoshader.pack_missing", desiredShaderPack), false);
        }

        return 1;
    }

    private static ResolvedShaderPack resolveShaderPack(String desiredShaderPack) {
        Path shaderpacksDir = FMLPaths.GAMEDIR.get().resolve("shaderpacks");
        if (!Files.isDirectory(shaderpacksDir)) {
            return new ResolvedShaderPack(desiredShaderPack, false);
        }

        String desiredNormalized = normalizeShaderName(desiredShaderPack);

        try (Stream<Path> stream = Files.list(shaderpacksDir)) {
            Path exact = stream
                .filter(path -> Files.isRegularFile(path) || Files.isDirectory(path))
                .filter(path -> path.getFileName().toString().equalsIgnoreCase(desiredShaderPack))
                .findFirst()
                .orElse(null);
            if (exact != null) {
                return new ResolvedShaderPack(exact.getFileName().toString(), true);
            }
        } catch (IOException ex) {
            ModConstants.LOGGER.warn("Unable to scan shaderpacks directory for '{}'", desiredShaderPack, ex);
            return new ResolvedShaderPack(desiredShaderPack, false);
        }

        try (Stream<Path> stream = Files.list(shaderpacksDir)) {
            return stream
                .filter(path -> Files.isRegularFile(path) || Files.isDirectory(path))
                .map(path -> path.getFileName().toString())
                .filter(name -> normalizeShaderName(name).equals(desiredNormalized))
                .findFirst()
                .map(name -> new ResolvedShaderPack(name, true))
                .orElseGet(() -> new ResolvedShaderPack(desiredShaderPack, false));
        } catch (IOException ex) {
            ModConstants.LOGGER.warn("Unable to scan shaderpacks directory for '{}'", desiredShaderPack, ex);
            return new ResolvedShaderPack(desiredShaderPack, false);
        }
    }

    private static void updateIrisConfiguration(String shaderPack) throws IOException {
        Path configDir = FMLPaths.CONFIGDIR.get();
        Path irisConfig = configDir.resolve("iris.properties");

        Properties properties = new Properties();
        if (Files.exists(irisConfig)) {
            try (Reader reader = Files.newBufferedReader(irisConfig, StandardCharsets.UTF_8)) {
                properties.load(reader);
            }
        }

        properties.setProperty("shaderPack", shaderPack);
        properties.setProperty("enableShaders", "true");

        Files.createDirectories(configDir);
        try (Writer writer = Files.newBufferedWriter(irisConfig, StandardCharsets.UTF_8)) {
            properties.store(writer, "Updated by Wilderness Odyssey auto shader command");
        }
    }

    private static Component tierComponent(HardwareRequirementConfig.Tier tier) {
        return Component.translatable("hardware.wildernessodyssey.tier." + tier.name().toLowerCase(Locale.ROOT));
    }

    private static String normalizeShaderName(String name) {
        String trimmed = stripExtension(name).trim().toLowerCase(Locale.ROOT);
        return trimmed.replaceAll("[\\s_-]+", " ");
    }

    private static String stripExtension(String name) {
        int dotIndex = name.lastIndexOf('.');
        return dotIndex > 0 ? name.substring(0, dotIndex) : name;
    }

    private record ResolvedShaderPack(String fileName, boolean exists) {
    }
}
