package com.thunder.wildernessodysseyapi.developmentstudio.client.screen;

import com.thunder.wildernessodysseyapi.developmentstudio.network.StudioStructureActionPayload;
import com.thunder.wildernessodysseyapi.developmentstudio.structure.StudioStructureOption;
import com.thunder.wildernessodysseyapi.developmentstudio.structure.StudioStructurePreview;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.List;

/** Template selection, transformed previews, exact lab placement, and bounded reset controls. */
final class StudioStructuresPage implements StudioPage {
    private int selectedIndex;
    private Rotation rotation = Rotation.NONE;
    private Mirror mirror = Mirror.NONE;

    @Override
    public void init(StudioScreen screen) {
        List<StudioStructureOption> options = screen.snapshot().structures();
        if (options.isEmpty()) {
            return;
        }
        selectedIndex = Math.max(0, Math.min(selectedIndex, options.size() - 1));
        int left = screen.contentLeft() + 12;
        int top = screen.contentTop() + 58;
        int width = screen.contentWidth() - 24;
        int gap = 4;
        int third = Math.max(72, (width - gap * 2) / 3);

        screen.addStudioWidget(Button.builder(Component.literal("Template: " + selected(options).displayName()), ignored -> {
            selectedIndex = (selectedIndex + 1) % options.size();
            screen.rebuildStudioWidgets();
        }).bounds(left, top, third, 20).build());
        screen.addStudioWidget(Button.builder(Component.literal("Rotation: " + shortRotation()), ignored -> {
            rotation = rotation.getRotated(Rotation.CLOCKWISE_90);
            screen.rebuildStudioWidgets();
        }).bounds(left + third + gap, top, third, 20).build());
        screen.addStudioWidget(Button.builder(Component.literal("Mirror: " + mirror), ignored -> {
            mirror = switch (mirror) {
                case NONE -> Mirror.LEFT_RIGHT;
                case LEFT_RIGHT -> Mirror.FRONT_BACK;
                case FRONT_BACK -> Mirror.NONE;
            };
            screen.rebuildStudioWidgets();
        }).bounds(left + (third + gap) * 2, top, third, 20).build());

        int half = Math.max(80, (width - gap) / 2);
        int rowTwo = top + 25;
        screen.addStudioWidget(Button.builder(Component.literal("Preview in Lab"), ignored ->
                send(StudioStructureActionPayload.Action.PREVIEW_LAB, selected(options))
        ).bounds(left, rowTwo, half, 20).build());
        screen.addStudioWidget(Button.builder(Component.literal("Preview 8 Blocks Ahead"), ignored ->
                send(StudioStructureActionPayload.Action.PREVIEW_HERE, selected(options))
        ).bounds(left + half + gap, rowTwo, half, 20).build());

        int rowThree = rowTwo + 25;
        Button place = Button.builder(Component.literal("Place in Lab"), ignored ->
                send(StudioStructureActionPayload.Action.PLACE_LAB, selected(options))
        ).bounds(left, rowThree, third, 20).build();
        place.active = selected(options).labPlaceable();
        screen.addStudioWidget(place);
        screen.addStudioWidget(Button.builder(Component.literal("Reset Lab"), ignored ->
                send(StudioStructureActionPayload.Action.RESET_LAB, selected(options))
        ).bounds(left + third + gap, rowThree, third, 20).build());
        screen.addStudioWidget(Button.builder(Component.literal("Reload Template"), ignored ->
                send(StudioStructureActionPayload.Action.RELOAD_TEMPLATE, selected(options))
        ).bounds(left + (third + gap) * 2, rowThree, third, 20).build());
    }

    @Override
    public void render(StudioScreen screen, GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        int x = screen.contentLeft() + 12;
        int y = screen.contentTop() + 12;
        int maxY = screen.contentTop() + screen.contentHeight() - 10;
        graphics.drawString(screen.font(), Component.literal("Structure Lab"), x, y, 0xFF8ED7FF, false);
        y = screen.drawWrapped(graphics, Component.literal(
                        "Previews are server-computed. Placement is restricted to the internal 5x5 fixture and the registered Structure Lab; Reset restores its first persisted baseline."
                ), x, y + 16, screen.contentWidth() - 24, 0xFFD8E2E8, maxY);

        List<StudioStructureOption> options = screen.snapshot().structures();
        if (options.isEmpty()) {
            graphics.drawString(screen.font(), Component.literal("No structure templates are currently available."),
                    x, y + 8, 0xFFFFA878, false);
            return;
        }
        StudioStructureOption selected = selected(options);
        int detailsY = screen.contentTop() + 142;
        graphics.drawString(screen.font(), Component.literal(
                "Selected: " + selected.id() + "  Size: " + selected.size().getX() + "x"
                        + selected.size().getY() + "x" + selected.size().getZ()
        ), x, detailsY, 0xFFBFD2DC, false);
        graphics.drawString(screen.font(), Component.literal(
                selected.labPlaceable() ? "Lab placement: allowed" : "Lab placement: preview-only"
        ), x, detailsY + 14, selected.labPlaceable() ? 0xFF80E39B : 0xFFFFD479, false);

        StudioStructurePreview preview = screen.snapshot().structurePreview();
        if (preview != null) {
            graphics.drawString(screen.font(), Component.literal(
                    "Preview: " + preview.min().toShortString() + " to " + preview.max().toShortString()
            ), x, detailsY + 31, 0xFFFFD479, false);
        }
    }

    private StudioStructureOption selected(List<StudioStructureOption> options) {
        return options.get(Math.max(0, Math.min(selectedIndex, options.size() - 1)));
    }

    private void send(StudioStructureActionPayload.Action action, StudioStructureOption option) {
        PacketDistributor.sendToServer(new StudioStructureActionPayload(action, option.id(), rotation, mirror));
    }

    private String shortRotation() {
        return switch (rotation) {
            case NONE -> "0";
            case CLOCKWISE_90 -> "90";
            case CLOCKWISE_180 -> "180";
            case COUNTERCLOCKWISE_90 -> "270";
        };
    }
}
