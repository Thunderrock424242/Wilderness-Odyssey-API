package com.thunder.wildernessodysseyapi.playtest;

import com.thunder.wildernessodysseyapi.core.ModConstants;
import com.thunder.wildernessodysseyapi.config.WildernessConfigSpecs;
import com.thunder.wildernessodysseyapi.feedback.FeedbackConfig;
import com.thunder.wildernessodysseyapi.playtest.verification.MinecraftVerificationRelayConfig;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Loaded-server command and secret-config boundaries; no external endpoints are contacted. */
@GameTestHolder(ModConstants.MOD_ID)
@PrefixGameTestTemplate(false)
public final class PlaytestGameTests {
    private PlaytestGameTests() {
    }

    /** Exercises real command registration and permission predicates in the headless server runtime. */
    @GameTest(template = "empty", timeoutTicks = 20)
    public static void playtestCommandsAreRegisteredAndStatusIsOperatorOnly(GameTestHelper helper) {
        var server = helper.getLevel().getServer();
        var root = server.getCommands().getDispatcher().getRoot();
        var wo = root.getChild("wo");
        helper.assertTrue(wo != null && wo.getChild("link") != null, "/wo link was not registered");
        helper.assertTrue(root.getChild("feedback") != null, "/feedback was not registered");
        var status = wo.getChild("playtest");
        helper.assertTrue(status != null && status.getChild("status") != null, "/wo playtest status was not registered");
        helper.assertTrue(!status.canUse(server.createCommandSourceStack().withPermission(0)), "Players can access admin diagnostics");
        helper.assertTrue(status.canUse(server.createCommandSourceStack().withPermission(2)), "Operators cannot access diagnostics");
        helper.assertTrue(MinecraftVerificationRelayConfig.CONFIG_SPEC == WildernessConfigSpecs.commonSpec(),
                "Verification secrets are in synchronized config");
        helper.assertTrue(FeedbackConfig.CONFIG_SPEC == WildernessConfigSpecs.commonSpec(),
                "Feedback secrets are in synchronized config");
        helper.succeed();
    }
}