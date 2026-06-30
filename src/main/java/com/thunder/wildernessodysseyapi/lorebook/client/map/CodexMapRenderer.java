package com.thunder.wildernessodysseyapi.lorebook.client.map;

import com.thunder.wildernessodysseyapi.lorebook.map.CodexMapConfig;
import com.thunder.wildernessodysseyapi.lorebook.CodexClientState;
import com.thunder.wildernessodysseyapi.lorebook.map.CodexMapPoi;
import com.thunder.wildernessodysseyapi.lorebook.map.CodexMapSettings;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;

/**
 * Draws the BlueMap-backed map spread inside the Wilderness Field Codex.
 *
 * <p>This renderer owns only client presentation. It reads configured BlueMap
 * tiles, overlays the local player, and leaves location authority to the live
 * client world/player state.</p>
 */
public final class CodexMapRenderer {
    private static final int INK = 0xFF2A2118;
    private static final int FADED_INK = 0xFF6B5644;
    private static final int WARNING_INK = 0xFF7D1E18;
    private static final int FRAME = 0xFF4A251C;
    private static final int FRAME_LIGHT = 0xFFB38453;
    private static final int MAP_BG = 0xFF162025;
    private static final int MAP_GRID = 0x335C7B7F;
    private static final int PLAYER = 0xFFEAF2FF;
    private static final int PLAYER_SHADOW = 0xAA0B1113;

    private int displayZoom = 2;

    /** Renders the full map spread across both Codex pages. */
    public void render(GuiGraphics graphics, Font font, int x, int y, int width, int height, int mouseX, int mouseY) {
        Minecraft minecraft = Minecraft.getInstance();
        Player player = minecraft.player;
        CodexMapSettings config = CodexClientState.mapSettings().orElseGet(CodexMapConfig::settings);

        graphics.drawString(font, Component.literal("FIELD MAP"), x, y, FADED_INK, false);
        graphics.hLine(x, x + width, y + 12, 0x77664D31);

        int mapX = x;
        int mapY = y + 20;
        int mapW = width;
        int mapH = height - 44;
        renderMapFrame(graphics, mapX, mapY, mapW, mapH);

        if (!config.enabled()) {
            renderCenteredStatus(graphics, font, mapX, mapY, mapW, mapH, "Map disabled in client config", WARNING_INK);
            renderFooter(graphics, font, player, config, x, y + height - 16, width);
            renderControls(graphics, font, x + width - 47, y + height - 18, mouseX, mouseY);
            return;
        }
        if (player == null || minecraft.level == null) {
            renderCenteredStatus(graphics, font, mapX, mapY, mapW, mapH, "No active world signal", WARNING_INK);
            renderFooter(graphics, font, player, config, x, y + height - 16, width);
            renderControls(graphics, font, x + width - 47, y + height - 18, mouseX, mouseY);
            return;
        }
        if (config.normalizedBaseUrl().isBlank() || config.normalizedTemplate().isBlank()) {
            renderCenteredStatus(graphics, font, mapX, mapY, mapW, mapH, "BlueMap URL not configured", WARNING_INK);
            renderFooter(graphics, font, player, config, x, y + height - 16, width);
            renderControls(graphics, font, x + width - 47, y + height - 18, mouseX, mouseY);
            return;
        }

        renderTiles(graphics, font, player, config, mapX + 4, mapY + 4, mapW - 8, mapH - 8);
        renderFooter(graphics, font, player, config, x, y + height - 16, width);
        renderControls(graphics, font, x + width - 47, y + height - 18, mouseX, mouseY);
    }

    /** Handles plus/minus clicks in the map spread. */
    public boolean mouseClicked(int mouseX, int mouseY, int x, int y, int width, int height) {
        int controlsX = x + width - 47;
        int controlsY = y + height - 18;
        if (inside(mouseX, mouseY, controlsX, controlsY, 18, 14)) {
            displayZoom = Math.max(0, displayZoom - 1);
            return true;
        }
        if (inside(mouseX, mouseY, controlsX + 23, controlsY, 18, 14)) {
            displayZoom = Math.min(5, displayZoom + 1);
            return true;
        }
        return false;
    }

    /** Handles keyboard zoom controls while the map tab is open. */
    public boolean keyPressed(int keyCode) {
        if (keyCode == 45 || keyCode == 333) {
            displayZoom = Math.max(0, displayZoom - 1);
            return true;
        }
        if (keyCode == 61 || keyCode == 334) {
            displayZoom = Math.min(5, displayZoom + 1);
            return true;
        }
        return false;
    }

    private void renderTiles(
            GuiGraphics graphics,
            Font font,
            Player player,
            CodexMapSettings config,
            int x,
            int y,
            int width,
            int height
    ) {
        int tilePixelSize = Math.max(1, config.tilePixelSize());
        int blocksPerTile = Math.max(1, config.blocksPerTile());
        BlueMapTileAddress center = BlueMapTileAddress.containing(
                player.getX(),
                player.getZ(),
                blocksPerTile,
                config.tileZoom()
        );
        double playerPixelX = center.pixelX(player.getX(), blocksPerTile, tilePixelSize);
        double playerPixelZ = center.pixelZ(player.getZ(), blocksPerTile, tilePixelSize);
        double scale = displayScale();
        int centerScreenX = x + width / 2;
        int centerScreenY = y + height / 2;
        boolean sawReadyTile = false;
        String lastStatus = "Waiting for BlueMap";

        graphics.enableScissor(x, y, x + width, y + height);
        renderMapBackground(graphics, x, y, width, height);

        for (int dz = -config.tileRadius(); dz <= config.tileRadius(); dz++) {
            for (int dx = -config.tileRadius(); dx <= config.tileRadius(); dx++) {
                BlueMapTileAddress address = center.offset(dx, dz);
                BlueMapTileClient.TileEntry entry = BlueMapTileClient.get().request(address, config);
                int drawSize = Math.max(1, (int) Math.round(tilePixelSize * scale));
                int drawX = (int) Math.round(centerScreenX + ((dx * tilePixelSize) - playerPixelX) * scale);
                int drawY = (int) Math.round(centerScreenY + ((dz * tilePixelSize) - playerPixelZ) * scale);

                var texture = BlueMapTileClient.get().textureFor(entry);
                if (texture != null) {
                    graphics.blit(
                            texture,
                            drawX,
                            drawY,
                            0.0F,
                            0.0F,
                            drawSize,
                            drawSize,
                            drawSize,
                            drawSize
                    );
                    sawReadyTile = true;
                } else if (entry.state() == BlueMapTileClient.TileState.ERROR) {
                    lastStatus = entry.status();
                    graphics.fill(drawX, drawY, drawX + drawSize, drawY + drawSize, 0x331F0D0D);
                } else {
                    graphics.fill(drawX, drawY, drawX + drawSize, drawY + drawSize, 0x221E3238);
                }
            }
        }

        renderGrid(graphics, x, y, width, height);
        renderPois(graphics, font, player, config, x, y, width, height, centerScreenX, centerScreenY, scale);
        renderPlayerMarker(graphics, centerScreenX, centerScreenY, player.getYRot());
        graphics.disableScissor();

        BlueMapTileClient.get().trimTo(config.cacheTiles());
        if (!sawReadyTile) {
            renderCenteredStatus(graphics, font, x, y, width, height, lastStatus, FADED_INK);
        }
    }

    private void renderMapFrame(GuiGraphics graphics, int x, int y, int width, int height) {
        graphics.fill(x - 3, y - 3, x + width + 3, y + height + 3, FRAME);
        graphics.fill(x - 1, y - 1, x + width + 1, y + height + 1, FRAME_LIGHT);
        graphics.fill(x, y, x + width, y + height, MAP_BG);
    }

    private void renderMapBackground(GuiGraphics graphics, int x, int y, int width, int height) {
        graphics.fill(x, y, x + width, y + height, MAP_BG);
        graphics.fillGradient(x, y, x + width, y + height, 0x221E4C56, 0x44090D11);
    }

    private void renderGrid(GuiGraphics graphics, int x, int y, int width, int height) {
        for (int gridX = x; gridX <= x + width; gridX += 32) {
            graphics.vLine(gridX, y, y + height, MAP_GRID);
        }
        for (int gridY = y; gridY <= y + height; gridY += 32) {
            graphics.hLine(x, x + width, gridY, MAP_GRID);
        }
    }

    private void renderPlayerMarker(GuiGraphics graphics, int centerX, int centerY, float yaw) {
        graphics.fill(centerX - 5, centerY - 5, centerX + 6, centerY + 6, PLAYER_SHADOW);
        graphics.fill(centerX - 3, centerY - 3, centerX + 4, centerY + 4, PLAYER);

        double radians = Math.toRadians(yaw);
        int tipX = centerX - (int) Math.round(Math.sin(radians) * 9.0);
        int tipY = centerY + (int) Math.round(Math.cos(radians) * 9.0);
        graphics.hLine(Math.min(centerX, tipX), Math.max(centerX, tipX), tipY, 0xFFE1C15D);
        graphics.vLine(tipX, Math.min(centerY, tipY), Math.max(centerY, tipY), 0xFFE1C15D);
    }

    private void renderPois(
            GuiGraphics graphics,
            Font font,
            Player player,
            CodexMapSettings config,
            int x,
            int y,
            int width,
            int height,
            int centerScreenX,
            int centerScreenY,
            double scale
    ) {
        String currentDimension = player.level().dimension().location().toString();
        double pixelsPerBlock = (double) Math.max(1, config.tilePixelSize()) / Math.max(1, config.blocksPerTile()) * scale;
        for (CodexMapPoi poi : CodexClientState.mapPois()) {
            if (!currentDimension.equals(poi.dimension().toString())) {
                continue;
            }

            int markerX = (int) Math.round(centerScreenX + (poi.x() - player.getX()) * pixelsPerBlock);
            int markerY = (int) Math.round(centerScreenY + (poi.z() - player.getZ()) * pixelsPerBlock);
            if (markerX < x - 8 || markerX > x + width + 8 || markerY < y - 8 || markerY > y + height + 8) {
                continue;
            }

            int color = poi.color();
            graphics.fill(markerX - 4, markerY - 4, markerX + 5, markerY + 5, 0xAA000000);
            graphics.fill(markerX - 3, markerY - 3, markerX + 4, markerY + 4, color);
            graphics.fill(markerX - 1, markerY - 1, markerX + 2, markerY + 2, 0xFFFFFFFF);

            if (Math.abs(markerX - centerScreenX) < 90 && Math.abs(markerY - centerScreenY) < 52) {
                graphics.drawString(font, Component.literal(poi.label()), markerX + 6, markerY - 4, 0xFFEAD7AE, true);
            }
        }
    }

    private void renderFooter(
            GuiGraphics graphics,
            Font font,
            Player player,
            CodexMapSettings config,
            int x,
            int y,
            int width
    ) {
        String position = player == null
                ? "Position unavailable"
                : String.format("X %.0f  Z %.0f", player.getX(), player.getZ());
        graphics.drawString(font, Component.literal(position), x, y, INK, false);

        String source = config.normalizedMapId().isBlank()
                ? "BlueMap"
                : "BlueMap: " + config.normalizedMapId();
        graphics.drawString(font, Component.literal(source), x + width - font.width(source), y, FADED_INK, false);
    }

    private void renderControls(GuiGraphics graphics, Font font, int x, int y, int mouseX, int mouseY) {
        drawButton(graphics, font, x, y, "-", inside(mouseX, mouseY, x, y, 18, 14));
        drawButton(graphics, font, x + 23, y, "+", inside(mouseX, mouseY, x + 23, y, 18, 14));
    }

    private void drawButton(GuiGraphics graphics, Font font, int x, int y, String label, boolean hover) {
        int color = hover ? 0xFFE0C074 : 0xFFC79B61;
        graphics.fill(x, y, x + 18, y + 14, color);
        graphics.fill(x, y + 12, x + 18, y + 14, 0x55200012);
        graphics.drawCenteredString(font, Component.literal(label), x + 9, y + 3, 0xFF21130D);
    }

    private void renderCenteredStatus(
            GuiGraphics graphics,
            Font font,
            int x,
            int y,
            int width,
            int height,
            String status,
            int color
    ) {
        graphics.drawCenteredString(font, Component.literal(status), x + width / 2, y + height / 2 - 4, color);
    }

    private double displayScale() {
        return Mth.clamp(0.45D + displayZoom * 0.25D, 0.45D, 1.70D);
    }

    private boolean inside(int mouseX, int mouseY, int x, int y, int width, int height) {
        return mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + height;
    }
}
