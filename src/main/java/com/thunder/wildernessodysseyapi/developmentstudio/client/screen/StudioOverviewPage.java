package com.thunder.wildernessodysseyapi.developmentstudio.client.screen;

import com.thunder.wildernessodysseyapi.developmentstudio.module.StudioModule;
import com.thunder.wildernessodysseyapi.developmentstudio.campus.StudioCampusLayout;
import com.thunder.wildernessodysseyapi.developmentstudio.network.StudioCampusUpgradePayload;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

/** Summary page for the world module and honest later-phase integration status. */
final class StudioOverviewPage implements StudioPage {
    private final StudioModule module;

    StudioOverviewPage(StudioModule module) {
        this.module = module;
    }

    @Override
    public void init(StudioScreen screen) {
        if (!"world".equals(module.id().getPath()) || !needsCampusUpgrade(screen)) {
            return;
        }
        int left = screen.contentLeft() + 12;
        int width = screen.contentWidth() - 24;
        int top = screen.contentTop() + screen.contentHeight() - 28;
        screen.addStudioWidget(Button.builder(
                Component.literal("Upgrade legacy campus to 65x65 facility"),
                ignored -> PacketDistributor.sendToServer(new StudioCampusUpgradePayload())
        ).bounds(left, top, width, 20).build());
    }

    @Override
    public void render(StudioScreen screen, GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        int x = screen.contentLeft() + 12;
        int y = screen.contentTop() + 12;
        int maxY = screen.contentTop() + screen.contentHeight()
                - (needsCampusUpgrade(screen) ? 76 : 10);
        int textWidth = screen.contentWidth() - 24;

        graphics.drawString(screen.font(), Component.translatable(module.titleKey()), x, y, 0xFF8ED7FF, false);
        y += 17;
        graphics.drawString(screen.font(), Component.literal("Status: " + module.status()), x, y,
                switch (module.status()) {
                    case AVAILABLE -> 0xFF80E39B;
                    case FOUNDATION -> 0xFFFFD479;
                    case DEFERRED -> 0xFFAAB6BE;
                }, false);
        y += 18;
        y = screen.drawWrapped(graphics, Component.translatable(module.descriptionKey()),
                x, y, textWidth, 0xFFD8E2E8, maxY);

        if ("world".equals(module.id().getPath())) {
            y += 10;
            graphics.drawString(screen.font(), Component.literal(
                    "Development Studio world: " + (screen.snapshot().developmentStudioWorld() ? "yes" : "explicit test mode")
            ), x, y, 0xFFE4EDF2, false);
            y += 13;
            graphics.drawString(screen.font(), Component.literal("Seed: " + screen.snapshot().worldSeed()),
                    x, y, 0xFFE4EDF2, false);
            y += 13;
            graphics.drawString(screen.font(), Component.literal("Campus origin: "
                            + (screen.snapshot().campusOrigin() == null
                            ? "not placed"
                            : screen.snapshot().campusOrigin().toShortString())),
                    x, y, 0xFFE4EDF2, false);
            y += 13;
            graphics.drawString(screen.font(), Component.literal(
                    "Campus layout: v" + screen.snapshot().campusVersion()
                            + " / v" + StudioCampusLayout.CURRENT_VERSION
            ), x, y, needsCampusUpgrade(screen) ? 0xFFFFD479 : 0xFF80E39B, false);
            y += 20;
            screen.drawWrapped(graphics, Component.literal(
                            "Terrain uses Minecraft's normal Overworld multi-noise biome source and normal noise settings. "
                                    + "Wilderness worldgen, structures, water, ecosystem, weather, and compatible mod hooks therefore run through their real pipelines."
                    ), x, y, textWidth, 0xFFBFD2DC, maxY);
            if (needsCampusUpgrade(screen)) {
                screen.drawWrapped(graphics, Component.literal(
                                "Upgrade expands the mod-owned legacy scaffold around its existing hub center. "
                                        + "Move anything you want to keep outside the 65x65 campus footprint first."
                        ), x, screen.contentTop() + screen.contentHeight() - 70,
                        textWidth, 0xFFFFA878, screen.contentTop() + screen.contentHeight() - 30);
            }
        } else if ("debug".equals(module.id().getPath())) {
            y += 10;
            screen.drawWrapped(graphics, Component.literal(
                            "Test-region bounds and structure previews are independent overlays. "
                                    + "They default off and perform no data collection while disabled."
                    ), x, y, textWidth, 0xFFBFD2DC, maxY);
        }
    }

    private boolean needsCampusUpgrade(StudioScreen screen) {
        return screen.snapshot().campusOrigin() != null
                && screen.snapshot().campusVersion() > 0
                && screen.snapshot().campusVersion() < StudioCampusLayout.CURRENT_VERSION;
    }
}
