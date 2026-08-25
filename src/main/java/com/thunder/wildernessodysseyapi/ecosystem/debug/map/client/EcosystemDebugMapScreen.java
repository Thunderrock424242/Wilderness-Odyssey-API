package com.thunder.wildernessodysseyapi.ecosystem.debug.map.client;

import com.thunder.wildernessodysseyapi.ecosystem.api.WildlifeSimulationLod;
import com.thunder.wildernessodysseyapi.ecosystem.debug.map.EcosystemDebugMapPayload;
import com.thunder.wildernessodysseyapi.ecosystem.debug.map.EcosystemDebugMapRequestPayload;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.List;
import java.util.Locale;

/**
 * Interactive schematic map of the server-owned regional wildlife simulation.
 *
 * <p>This screen is intentionally not a terrain minimap. Every visual is
 * derived from the bounded payload and the screen performs no world queries.</p>
 */
public final class EcosystemDebugMapScreen extends Screen {
    private static final int MARGIN = 12;
    private static final int HEADER_HEIGHT = 42;
    private static final int GAP = 10;
    private static final int PANEL_BACKGROUND = 0xF0162028;
    private static final int PANEL_BORDER = 0xFF55798C;
    private static final int TEXT = 0xFFDCE8EE;
    private static final int MUTED_TEXT = 0xFF91A6B1;
    private static final int ACCENT = 0xFFFFD479;

    private EcosystemDebugMapPayload snapshot;
    private final Screen parent;
    private Layer layer = Layer.ANIMALS;
    private int selectedCellX;
    private int selectedCellZ;
    private int mapLeft;
    private int mapTop;
    private int mapPixels;
    private int cellPixels;
    private int detailLeft;
    private int detailWidth;

    public EcosystemDebugMapScreen(EcosystemDebugMapPayload snapshot) {
        this(snapshot, null);
    }

    EcosystemDebugMapScreen(EcosystemDebugMapPayload snapshot, Screen parent) {
        super(Component.literal("Animal Ecosystem Map"));
        this.snapshot = snapshot;
        this.parent = parent;
        this.selectedCellX = snapshot.centerCellX();
        this.selectedCellZ = snapshot.centerCellZ();
    }

    /** Applies one requested refresh without closing the diagnostic screen. */
    public void applySnapshot(EcosystemDebugMapPayload updated) {
        this.snapshot = updated;
        this.selectedCellX = updated.centerCellX();
        this.selectedCellZ = updated.centerCellZ();
        rebuildWidgets();
    }

    @Override
    protected void init() {
        int buttonY = 17;
        addRenderableWidget(Button.builder(
                Component.literal("Layer: " + layer.displayName),
                button -> {
                    layer = layer.next();
                    button.setMessage(Component.literal("Layer: " + layer.displayName));
                }
        ).bounds(Math.max(MARGIN, width - 312), buttonY, 150, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Refresh"), button ->
                PacketDistributor.sendToServer(new EcosystemDebugMapRequestPayload())
        ).bounds(Math.max(MARGIN + 154, width - 158), buttonY, 72, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Close"), button -> onClose())
                .bounds(Math.max(MARGIN + 230, width - 82), buttonY, 70, 20).build());
        updateLayout();
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, width, height, 0xFC080D12);
        graphics.drawString(font, title, MARGIN, 9, 0xFFEAF8FF, false);
        graphics.drawString(font, Component.literal(
                snapshot.dimension() + "  tick " + snapshot.serverGameTime()
                        + "  center " + snapshot.playerBlockX() + ", " + snapshot.playerBlockZ()
        ), MARGIN, 25, MUTED_TEXT, false);

        drawMap(graphics);
        EcosystemDebugMapPayload.CellSnapshot hovered = cellAt(mouseX, mouseY);
        drawDetails(graphics, hovered == null ? selectedCell() : hovered, hovered != null);
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            EcosystemDebugMapPayload.CellSnapshot cell = cellAt(mouseX, mouseY);
            if (cell != null) {
                selectedCellX = cell.cellX();
                selectedCellZ = cell.cellZ();
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void onClose() {
        if (minecraft != null) {
            minecraft.setScreen(parent);
        }
    }

    private void updateLayout() {
        int gridWidth = snapshot.radiusCells() * 2 + 1;
        detailWidth = Math.max(180, Math.min(270, width / 3));
        int availableMapWidth = Math.max(gridWidth * 5, width - MARGIN * 2 - GAP - detailWidth);
        int availableMapHeight = Math.max(gridWidth * 5, height - HEADER_HEIGHT - MARGIN - 14);
        cellPixels = Math.max(5, Math.min(availableMapWidth, availableMapHeight) / gridWidth);
        mapPixels = cellPixels * gridWidth;
        mapLeft = MARGIN;
        mapTop = HEADER_HEIGHT;
        detailLeft = mapLeft + mapPixels + GAP;
        detailWidth = Math.max(80, width - detailLeft - MARGIN);
    }

    private void drawMap(GuiGraphics graphics) {
        graphics.fill(mapLeft - 2, mapTop - 2, mapLeft + mapPixels + 2, mapTop + mapPixels + 2,
                PANEL_BORDER);
        int minimumCellX = snapshot.centerCellX() - snapshot.radiusCells();
        int minimumCellZ = snapshot.centerCellZ() - snapshot.radiusCells();
        for (EcosystemDebugMapPayload.CellSnapshot cell : snapshot.cells()) {
            int x = mapLeft + (cell.cellX() - minimumCellX) * cellPixels;
            int y = mapTop + (cell.cellZ() - minimumCellZ) * cellPixels;
            int color = cellColor(cell);
            graphics.fill(x, y, x + cellPixels, y + cellPixels, lodBorder(cell.simulationLevel()));
            graphics.fill(x + 1, y + 1, x + cellPixels - 1, y + cellPixels - 1, color);
            if (cell.cellX() == selectedCellX && cell.cellZ() == selectedCellZ) {
                outline(graphics, x, y, cellPixels, ACCENT);
            }
            if (cellPixels >= 13 && cell.totalPopulation() > 0) {
                graphics.drawCenteredString(font, Integer.toString(cell.totalPopulation()),
                        x + cellPixels / 2, y + Math.max(2, (cellPixels - font.lineHeight) / 2), 0xFFFFFFFF);
            }
        }

        for (EcosystemDebugMapPayload.GroupSnapshot group : snapshot.groups()) {
            drawGroup(graphics, group, minimumCellX, minimumCellZ);
        }
        drawPlayer(graphics, minimumCellX, minimumCellZ);
        graphics.drawCenteredString(font, "N", mapLeft + mapPixels / 2, mapTop + 3, 0xFFFFFFFF);
    }

    private void drawGroup(
            GuiGraphics graphics,
            EcosystemDebugMapPayload.GroupSnapshot group,
            int minimumCellX,
            int minimumCellZ
    ) {
        double minimumBlockX = (double) minimumCellX * snapshot.cellSize();
        double minimumBlockZ = (double) minimumCellZ * snapshot.cellSize();
        double pixelsPerBlock = cellPixels / (double) snapshot.cellSize();
        int x = mapLeft + (int) Math.round((group.blockX() - minimumBlockX) * pixelsPerBlock);
        int y = mapTop + (int) Math.round((group.blockZ() - minimumBlockZ) * pixelsPerBlock);
        int radius = Math.max(2, Math.min(5, 2 + group.populationEstimate() / 16));
        int color = speciesColor(group.species());
        int directionLength = Math.max(3, cellPixels / 3);
        int directionX = (int) Math.round(group.directionX() * directionLength);
        int directionZ = (int) Math.round(group.directionZ() * directionLength);
        line(graphics, x, y, x + directionX, y + directionZ, 0xDFFFFFFF);
        graphics.fill(x - radius - 1, y - radius - 1, x + radius + 2, y + radius + 2, 0xCC071017);
        graphics.fill(x - radius, y - radius, x + radius + 1, y + radius + 1, color);
    }

    private void drawPlayer(GuiGraphics graphics, int minimumCellX, int minimumCellZ) {
        double minimumBlockX = (double) minimumCellX * snapshot.cellSize();
        double minimumBlockZ = (double) minimumCellZ * snapshot.cellSize();
        double pixelsPerBlock = cellPixels / (double) snapshot.cellSize();
        int x = mapLeft + (int) Math.round((snapshot.playerBlockX() - minimumBlockX) * pixelsPerBlock);
        int y = mapTop + (int) Math.round((snapshot.playerBlockZ() - minimumBlockZ) * pixelsPerBlock);
        graphics.hLine(x - 4, x + 4, y, 0xFFFFFFFF);
        graphics.vLine(x, y - 4, y + 4, 0xFFFFFFFF);
        graphics.fill(x - 1, y - 1, x + 2, y + 2, ACCENT);
    }

    private void drawDetails(
            GuiGraphics graphics,
            EcosystemDebugMapPayload.CellSnapshot cell,
            boolean hovered
    ) {
        int panelBottom = Math.max(mapTop + mapPixels, height - MARGIN);
        graphics.fill(detailLeft - 2, mapTop - 2, detailLeft + detailWidth + 2, panelBottom + 2,
                PANEL_BORDER);
        graphics.fill(detailLeft, mapTop, detailLeft + detailWidth, panelBottom, PANEL_BACKGROUND);
        int x = detailLeft + 8;
        int y = mapTop + 8;
        int maxY = panelBottom - 9;
        if (cell == null) {
            graphics.drawString(font, "No cell selected", x, y, MUTED_TEXT, false);
            return;
        }

        graphics.drawString(font, Component.literal((hovered ? "Hover" : "Selected")
                + " cell " + cell.cellX() + ", " + cell.cellZ()), x, y, ACCENT, false);
        y += 14;
        int minimumX = cell.cellX() * snapshot.cellSize();
        int minimumZ = cell.cellZ() * snapshot.cellSize();
        y = row(graphics, "Blocks", minimumX + ".." + (minimumX + snapshot.cellSize() - 1)
                + ", " + minimumZ + ".." + (minimumZ + snapshot.cellSize() - 1), x, y, maxY);
        y = row(graphics, "LOD", cell.simulationLevel().name().toLowerCase(Locale.ROOT), x, y, maxY);
        y = row(graphics, "Groups / animals", cell.groupCount() + " / " + cell.totalPopulation(), x, y, maxY);
        y = row(graphics, "Capacity", snapshot.regionalCarryingCapacity() + " ("
                + percent(cell.totalPopulation() / (float) snapshot.regionalCarryingCapacity()) + ")", x, y, maxY);
        y += 4;
        y = meterRow(graphics, "Food", cell.foodAvailability(), x, y, maxY, 0xFF6FCF77);
        y = meterRow(graphics, "Water", cell.waterAvailability(), x, y, maxY, 0xFF63BDE8);
        y = meterRow(graphics, "Food pressure", cell.foodPressure(), x, y, maxY, 0xFFE7B45E);
        y = meterRow(graphics, "Disturbance", cell.disturbance(), x, y, maxY, 0xFFE36659);
        y = meterRow(graphics, "Weather impact", cell.weatherImpact(), x, y, maxY, 0xFFB384E8);
        if (cell.hasMigrationTarget()) {
            y = row(graphics, "Migration", cell.migrationCellX() + ", " + cell.migrationCellZ(), x, y, maxY);
        }
        if (cell.lastUpdatedTick() > 0L) {
            y = row(graphics, "Population age", Math.max(0L,
                    snapshot.serverGameTime() - cell.lastUpdatedTick()) + " ticks", x, y, maxY);
        }

        if (!cell.species().isEmpty() && y + 24 < maxY) {
            y += 5;
            graphics.drawString(font, "Species", x, y, 0xFF8ED7FF, false);
            y += 12;
            for (EcosystemDebugMapPayload.SpeciesPopulation species : cell.species()) {
                if (y + font.lineHeight > maxY) {
                    break;
                }
                graphics.drawString(font, Component.literal(shortSpecies(species.species())
                        + "  x" + species.population()), x + 4, y, TEXT, false);
                y += 11;
            }
        }

        if (y + 22 < maxY) {
            y += 5;
            graphics.drawString(font, Component.literal("Systems: zones " + onOff(snapshot.ecosystemEnabled())
                    + ", distant " + onOff(snapshot.distantWildlifeEnabled())
                    + ", population " + onOff(snapshot.populationEcologyEnabled())),
                    x, y, MUTED_TEXT, false);
        }
    }

    private int row(GuiGraphics graphics, String label, String value, int x, int y, int maxY) {
        if (y + font.lineHeight > maxY) {
            return y;
        }
        graphics.drawString(font, label + ":", x, y, 0xFF9FC8DE, false);
        int valueX = x + Math.min(98, Math.max(62, detailWidth / 3));
        graphics.drawString(font, Component.literal(value), valueX, y, TEXT, false);
        return y + 12;
    }

    private int meterRow(
            GuiGraphics graphics,
            String label,
            float value,
            int x,
            int y,
            int maxY,
            int color
    ) {
        if (y + 10 > maxY) {
            return y;
        }
        int meterX = x + Math.min(98, Math.max(62, detailWidth / 3));
        int meterWidth = Math.max(28, detailWidth - (meterX - detailLeft) - 32);
        graphics.drawString(font, label + ":", x, y, 0xFF9FC8DE, false);
        graphics.fill(meterX, y + 2, meterX + meterWidth, y + 8, 0xFF27333B);
        graphics.fill(meterX, y + 2, meterX + Math.round(meterWidth * value), y + 8, color);
        graphics.drawString(font, percent(value), meterX + meterWidth + 3, y, TEXT, false);
        return y + 12;
    }

    private EcosystemDebugMapPayload.CellSnapshot cellAt(double mouseX, double mouseY) {
        if (mouseX < mapLeft || mouseY < mapTop
                || mouseX >= mapLeft + mapPixels || mouseY >= mapTop + mapPixels) {
            return null;
        }
        int x = snapshot.centerCellX() - snapshot.radiusCells()
                + (int) ((mouseX - mapLeft) / cellPixels);
        int z = snapshot.centerCellZ() - snapshot.radiusCells()
                + (int) ((mouseY - mapTop) / cellPixels);
        return findCell(x, z);
    }

    private EcosystemDebugMapPayload.CellSnapshot selectedCell() {
        return findCell(selectedCellX, selectedCellZ);
    }

    private EcosystemDebugMapPayload.CellSnapshot findCell(int cellX, int cellZ) {
        for (EcosystemDebugMapPayload.CellSnapshot cell : snapshot.cells()) {
            if (cell.cellX() == cellX && cell.cellZ() == cellZ) {
                return cell;
            }
        }
        return null;
    }

    private int cellColor(EcosystemDebugMapPayload.CellSnapshot cell) {
        return switch (layer) {
            case ANIMALS -> populationColor(cell.totalPopulation(), snapshot.regionalCarryingCapacity());
            case LOD -> darken(lodBorder(cell.simulationLevel()), 0.68F);
            case FOOD -> gradient(0xFF4B2524, 0xFF3D8D55, cell.foodAvailability());
            case WATER -> gradient(0xFF30292A, 0xFF277EAD, cell.waterAvailability());
            case PRESSURE -> gradient(0xFF254A38, 0xFFB3533D, cell.foodPressure());
            case DISTURBANCE -> gradient(0xFF222D35, 0xFFC33E39, cell.disturbance());
            case WEATHER -> gradient(0xFF252C38, 0xFF834AA3, cell.weatherImpact());
        };
    }

    private static int populationColor(int population, int capacity) {
        if (population <= 0) {
            return 0xFF172129;
        }
        float density = Math.min(1.0F, population / (float) Math.max(1, capacity));
        if (density < 0.5F) {
            return gradient(0xFF224234, 0xFF4C9563, density * 2.0F);
        }
        return gradient(0xFF4C9563, 0xFFD47A43, (density - 0.5F) * 2.0F);
    }

    private static int lodBorder(WildlifeSimulationLod simulationLevel) {
        return switch (simulationLevel) {
            case ACTIVE -> 0xFF59CF79;
            case NEAR -> 0xFF4CC6C8;
            case DISTANT -> 0xFFE1A34D;
            case DORMANT -> 0xFF735A87;
        };
    }

    private static int speciesColor(ResourceLocation species) {
        int hash = species.hashCode();
        int red = 96 + Math.floorMod(hash, 144);
        int green = 96 + Math.floorMod(hash >>> 8, 144);
        int blue = 96 + Math.floorMod(hash >>> 16, 144);
        return 0xFF000000 | red << 16 | green << 8 | blue;
    }

    private static int gradient(int start, int end, float amount) {
        float bounded = Math.max(0.0F, Math.min(1.0F, amount));
        int red = Math.round(((start >> 16) & 0xFF) * (1.0F - bounded) + ((end >> 16) & 0xFF) * bounded);
        int green = Math.round(((start >> 8) & 0xFF) * (1.0F - bounded) + ((end >> 8) & 0xFF) * bounded);
        int blue = Math.round((start & 0xFF) * (1.0F - bounded) + (end & 0xFF) * bounded);
        return 0xFF000000 | red << 16 | green << 8 | blue;
    }

    private static int darken(int color, float amount) {
        return gradient(0xFF10151A, color, amount);
    }

    private static void outline(GuiGraphics graphics, int x, int y, int size, int color) {
        graphics.hLine(x, x + size - 1, y, color);
        graphics.hLine(x, x + size - 1, y + size - 1, color);
        graphics.vLine(x, y, y + size - 1, color);
        graphics.vLine(x + size - 1, y, y + size - 1, color);
    }

    private static void line(GuiGraphics graphics, int x0, int y0, int x1, int y1, int color) {
        int dx = Math.abs(x1 - x0);
        int sx = x0 < x1 ? 1 : -1;
        int dy = -Math.abs(y1 - y0);
        int sy = y0 < y1 ? 1 : -1;
        int error = dx + dy;
        while (true) {
            graphics.fill(x0, y0, x0 + 1, y0 + 1, color);
            if (x0 == x1 && y0 == y1) {
                return;
            }
            int doubled = error * 2;
            if (doubled >= dy) {
                error += dy;
                x0 += sx;
            }
            if (doubled <= dx) {
                error += dx;
                y0 += sy;
            }
        }
    }

    private static String shortSpecies(ResourceLocation species) {
        String path = species.getPath();
        int separator = path.lastIndexOf('/');
        return (separator >= 0 ? path.substring(separator + 1) : path).replace('_', ' ');
    }

    private static String percent(float value) {
        return Math.round(Math.max(0.0F, value) * 100.0F) + "%";
    }

    private static String onOff(boolean enabled) {
        return enabled ? "on" : "off";
    }

    private enum Layer {
        ANIMALS("Animals"),
        LOD("LOD"),
        FOOD("Food"),
        WATER("Water"),
        PRESSURE("Food pressure"),
        DISTURBANCE("Disturbance"),
        WEATHER("Weather impact");

        private static final List<Layer> VALUES = List.of(values());
        private final String displayName;

        Layer(String displayName) {
            this.displayName = displayName;
        }

        private Layer next() {
            return VALUES.get((ordinal() + 1) % VALUES.size());
        }
    }
}
