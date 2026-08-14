package com.thunder.wildernessodysseyapi.developmentstudio.client.screen;

import com.thunder.wildernessodysseyapi.developmentstudio.entity.StudioEntityOption;
import com.thunder.wildernessodysseyapi.developmentstudio.network.StudioEntityActionPayload;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.List;

/** Fixed entity allowlist and controls restricted to tagged Entity Lab occupants. */
final class StudioEntitiesPage implements StudioPage {
    private int selectedIndex;

    @Override
    public void init(StudioScreen screen) {
        List<StudioEntityOption> options = screen.snapshot().entityTypes();
        if (options.isEmpty()) {
            return;
        }
        selectedIndex = Math.max(0, Math.min(selectedIndex, options.size() - 1));
        int left = screen.contentLeft() + 12;
        int top = screen.contentTop() + 55;
        int width = screen.contentWidth() - 24;
        int gap = 4;
        int third = Math.max(72, (width - gap * 2) / 3);

        screen.addStudioWidget(Button.builder(Component.literal("Entity: " + selected(options).displayName()), ignored -> {
            selectedIndex = (selectedIndex + 1) % options.size();
            screen.rebuildStudioWidgets();
        }).bounds(left, top, third, 20).build());
        screen.addStudioWidget(Button.builder(Component.literal("Spawn 1"), ignored ->
                send(StudioEntityActionPayload.Action.SPAWN, selected(options), 1)
        ).bounds(left + third + gap, top, third, 20).build());
        screen.addStudioWidget(Button.builder(Component.literal("Spawn 5"), ignored ->
                send(StudioEntityActionPayload.Action.SPAWN, selected(options), 5)
        ).bounds(left + (third + gap) * 2, top, third, 20).build());

        int rowTwo = top + 25;
        screen.addStudioWidget(Button.builder(Component.literal("Freeze Tagged"), ignored ->
                send(StudioEntityActionPayload.Action.FREEZE, selected(options), 1)
        ).bounds(left, rowTwo, third, 20).build());
        screen.addStudioWidget(Button.builder(Component.literal("Unfreeze Tagged"), ignored ->
                send(StudioEntityActionPayload.Action.UNFREEZE, selected(options), 1)
        ).bounds(left + third + gap, rowTwo, third, 20).build());
        screen.addStudioWidget(Button.builder(Component.literal("Clear Tagged"), ignored ->
                send(StudioEntityActionPayload.Action.CLEAR, selected(options), 1)
        ).bounds(left + (third + gap) * 2, rowTwo, third, 20).build());

        int rowThree = rowTwo + 25;
        int half = Math.max(80, (width - gap) / 2);
        screen.addStudioWidget(Button.builder(Component.literal("Make Invulnerable"), ignored ->
                send(StudioEntityActionPayload.Action.MAKE_INVULNERABLE, selected(options), 1)
        ).bounds(left, rowThree, half, 20).build());
        screen.addStudioWidget(Button.builder(Component.literal("Make Vulnerable"), ignored ->
                send(StudioEntityActionPayload.Action.MAKE_VULNERABLE, selected(options), 1)
        ).bounds(left + half + gap, rowThree, half, 20).build());
    }

    @Override
    public void render(StudioScreen screen, GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        int x = screen.contentLeft() + 12;
        int y = screen.contentTop() + 12;
        int maxY = screen.contentTop() + screen.contentHeight() - 10;
        graphics.drawString(screen.font(), Component.literal("Entity Lab"), x, y, 0xFF8ED7FF, false);
        screen.drawWrapped(graphics, Component.literal(
                        "Spawn requests use a fixed server allowlist. Freeze, invulnerability, and Clear affect only Studio-tagged entities still inside the registered lab."
                ), x, y + 16, screen.contentWidth() - 24, 0xFFD8E2E8, maxY);
        graphics.drawString(screen.font(), Component.literal(
                "Tagged entities currently in lab: " + screen.snapshot().entityLabEntityCount()
        ), x, screen.contentTop() + 140, 0xFFBFD2DC, false);
    }

    private StudioEntityOption selected(List<StudioEntityOption> options) {
        return options.get(Math.max(0, Math.min(selectedIndex, options.size() - 1)));
    }

    private void send(StudioEntityActionPayload.Action action, StudioEntityOption option, int count) {
        PacketDistributor.sendToServer(new StudioEntityActionPayload(action, option.id(), count));
    }
}
