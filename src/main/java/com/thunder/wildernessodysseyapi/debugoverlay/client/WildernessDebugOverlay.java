package com.thunder.wildernessodysseyapi.debugoverlay.client;

import com.thunder.wildernessodysseyapi.debugoverlay.DebugEntry;
import com.thunder.wildernessodysseyapi.debugoverlay.DebugPage;
import com.thunder.wildernessodysseyapi.debugoverlay.DebugSection;
import com.thunder.wildernessodysseyapi.debugoverlay.DebugValue;
import com.thunder.wildernessodysseyapi.debugoverlay.config.DebugOverlayConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Draws the responsive Minecraft-native panel used by every debug page. */
public final class WildernessDebugOverlay {
    private static final int MARGIN = 4;
    private static final int PADDING = 6;
    private static final int HEADER_HEIGHT = 20;
    private static final int FOOTER_HEIGHT = 15;
    private static final int LINE_HEIGHT = 10;
    private static final int COLUMN_GAP = 14;
    private static final int MIN_COLUMN_WIDTH = 220;

    private static final int PANEL_COLOR = 0xD0181C22;
    private static final int HEADER_COLOR = 0xD0263039;
    private static final int DIVIDER_COLOR = 0xFF587080;
    private static final int TITLE_COLOR = 0xFF8FD3E8;
    private static final int SECTION_COLOR = 0xFF79BDD1;
    private static final int LABEL_COLOR = 0xFFA9B3BA;
    private static final int NORMAL_COLOR = 0xFFF0F3F5;
    private static final int GOOD_COLOR = 0xFF8FD18B;
    private static final int WARNING_COLOR = 0xFFF0CE72;
    private static final int ERROR_COLOR = 0xFFEF7777;
    private static final int UNAVAILABLE_COLOR = 0xFF7E8A92;

    /**
     * Renders one page, flows its visible rows into columns, and returns the
     * scroll offset clamped to the current content and screen capacity.
     */
    public int render(
            GuiGraphics graphics,
            DebugPage page,
            int pageIndex,
            int pageCount,
            List<DebugSection> sections,
            int requestedScrollOffset
    ) {
        Minecraft minecraft = Minecraft.getInstance();
        Font font = minecraft.font;
        int screenWidth = graphics.guiWidth();
        int screenHeight = graphics.guiHeight();
        int panelWidth = Math.max(1, screenWidth - MARGIN * 2);
        boolean hints = DebugOverlayConfig.SHOW_PAGE_HINTS.get();
        int footerHeight = hints ? FOOTER_HEIGHT : 0;

        List<RenderLine> lines = flatten(sections);
        int availableContentHeight = Math.max(LINE_HEIGHT,
                screenHeight - MARGIN * 2 - HEADER_HEIGHT - footerHeight - PADDING * 2);
        int maxRows = Math.max(1, availableContentHeight / LINE_HEIGHT);
        int maxColumns = Math.max(1, Math.min(3,
                (panelWidth - PADDING * 2 + COLUMN_GAP) / (MIN_COLUMN_WIDTH + COLUMN_GAP)));
        int capacity = maxRows * maxColumns;
        DebugViewport viewport = DebugViewport.calculate(requestedScrollOffset, lines.size(), capacity);
        lines = new ArrayList<>(lines.subList(viewport.offset(), viewport.endExclusive()));

        int usedColumns = Math.max(1, Math.min(maxColumns, (lines.size() + maxRows - 1) / maxRows));
        // Columns fill top-to-bottom, so every non-final column uses maxRows.
        int usedRows = Math.max(1, Math.min(maxRows, lines.size()));
        int panelHeight = Math.min(screenHeight - MARGIN * 2,
                HEADER_HEIGHT + PADDING * 2 + usedRows * LINE_HEIGHT + footerHeight);
        int panelX = MARGIN;
        int panelY = MARGIN;

        // A single panel is cheaper and calmer than vanilla's background rectangle per line.
        if (DebugOverlayConfig.DEBUG_HUD_BACKGROUND.get()) {
            graphics.fill(panelX, panelY, panelX + panelWidth, panelY + panelHeight, PANEL_COLOR);
            graphics.fill(panelX, panelY, panelX + panelWidth, panelY + HEADER_HEIGHT, HEADER_COLOR);
        }
        graphics.fill(panelX + PADDING, panelY + HEADER_HEIGHT - 1,
                panelX + panelWidth - PADDING, panelY + HEADER_HEIGHT, DIVIDER_COLOR);

        String title = clip(font, "WILDERNESS DEBUG", Math.max(20, panelWidth / 2 - PADDING));
        graphics.drawString(font, title, panelX + PADDING, panelY + 6, TITLE_COLOR, true);
        String pageLabel = page.displayName().toUpperCase(Locale.ROOT) + " " + (pageIndex + 1) + "/" + pageCount;
        pageLabel = clip(font, pageLabel, Math.max(20, panelWidth / 2));
        graphics.drawString(font, pageLabel,
                panelX + panelWidth - PADDING - font.width(pageLabel), panelY + 6, NORMAL_COLOR, true);

        int columnWidth = Math.max(1,
                (panelWidth - PADDING * 2 - COLUMN_GAP * (usedColumns - 1)) / usedColumns);
        int contentTop = panelY + HEADER_HEIGHT + PADDING;
        for (int column = 0; column < usedColumns; column++) {
            int start = column * maxRows;
            if (start >= lines.size()) {
                break;
            }
            int end = Math.min(lines.size(), start + maxRows);
            int columnX = panelX + PADDING + column * (columnWidth + COLUMN_GAP);
            renderColumn(graphics, font, lines.subList(start, end), columnX, contentTop, columnWidth);
        }

        if (hints) {
            renderFooter(graphics, font, panelX, panelY, panelWidth, panelHeight, viewport);
        }
        return viewport.offset();
    }

    private static void renderFooter(
            GuiGraphics graphics,
            Font font,
            int panelX,
            int panelY,
            int panelWidth,
            int panelHeight,
            DebugViewport viewport
    ) {
        int footerY = panelY + panelHeight - FOOTER_HEIGHT + 3;
        String previous = DebugKeyMappings.PREVIOUS_PAGE.getTranslatedKeyMessage().getString() + "  ← Previous";
        String next = "Next →  " + DebugKeyMappings.NEXT_PAGE.getTranslatedKeyMessage().getString();

        if (!viewport.scrollable()) {
            previous = clip(font, previous, Math.max(20, panelWidth / 2 - PADDING));
            next = clip(font, next, Math.max(20, panelWidth / 2 - PADDING));
            graphics.drawString(font, previous, panelX + PADDING, footerY, UNAVAILABLE_COLOR, true);
            graphics.drawString(font, next,
                    panelX + panelWidth - PADDING - font.width(next), footerY, UNAVAILABLE_COLOR, true);
            return;
        }

        int sideWidth = Math.max(20, panelWidth / 4 - PADDING);
        int centerWidth = Math.max(20, panelWidth / 2 - PADDING * 2);
        String scroll = DebugKeyMappings.SCROLL_UP.getTranslatedKeyMessage().getString()
                + " / " + DebugKeyMappings.SCROLL_DOWN.getTranslatedKeyMessage().getString()
                + "  " + viewport.firstVisibleLine() + "-" + viewport.lastVisibleLine()
                + "/" + viewport.totalLines();
        previous = clip(font, previous, sideWidth);
        next = clip(font, next, sideWidth);
        scroll = clip(font, scroll, centerWidth);

        graphics.drawString(font, previous, panelX + PADDING, footerY, UNAVAILABLE_COLOR, true);
        graphics.drawString(font, scroll,
                panelX + (panelWidth - font.width(scroll)) / 2, footerY, UNAVAILABLE_COLOR, true);
        graphics.drawString(font, next,
                panelX + panelWidth - PADDING - font.width(next), footerY, UNAVAILABLE_COLOR, true);
    }

    private static void renderColumn(
            GuiGraphics graphics,
            Font font,
            List<RenderLine> lines,
            int x,
            int top,
            int width
    ) {
        int labelWidth = 0;
        for (RenderLine line : lines) {
            if (line.kind() == LineKind.ENTRY) {
                labelWidth = Math.max(labelWidth, font.width(line.label()));
            }
        }
        labelWidth = Math.min(labelWidth,
                Math.min(Math.max(45, width / 2), Math.max(1, width - 8)));

        for (int row = 0; row < lines.size(); row++) {
            RenderLine line = lines.get(row);
            int y = top + row * LINE_HEIGHT;
            switch (line.kind()) {
                case SECTION -> graphics.drawString(font, clip(font, line.value(), width), x, y, SECTION_COLOR, true);
                case RAW -> graphics.drawString(font, clip(font, line.value(), width), x, y, color(line.tone()), true);
                case ENTRY -> {
                    String label = clip(font, line.label(), labelWidth);
                    int valueX = x + labelWidth + 8;
                    int valueWidth = Math.max(1, width - labelWidth - 8);
                    graphics.drawString(font, label, x, y, LABEL_COLOR, true);
                    graphics.drawString(font, clip(font, line.value(), valueWidth), valueX, y, color(line.tone()), true);
                }
                case SPACER -> {
                    // Spacers intentionally consume one row to separate neighboring sections.
                }
            }
        }
    }

    private static List<RenderLine> flatten(List<DebugSection> sections) {
        List<RenderLine> lines = new ArrayList<>();
        for (DebugSection section : sections) {
            if (!section.title().isBlank()) {
                lines.add(RenderLine.section(section.title()));
            }
            for (DebugEntry entry : section.entries()) {
                lines.add(entry.raw()
                        ? RenderLine.raw(entry.value().text(), entry.value().tone())
                        : RenderLine.entry(entry.label(), entry.value()));
            }
            lines.add(RenderLine.spacer());
        }
        if (!lines.isEmpty() && lines.getLast().kind() == LineKind.SPACER) {
            lines.removeLast();
        }
        return lines;
    }

    private static String clip(Font font, String value, int width) {
        if (value == null || width <= 0) {
            return "";
        }
        if (font.width(value) <= width) {
            return value;
        }
        String ellipsis = "…";
        return font.plainSubstrByWidth(value, Math.max(0, width - font.width(ellipsis))) + ellipsis;
    }

    private static int color(DebugValue.Tone tone) {
        return switch (tone) {
            case GOOD -> GOOD_COLOR;
            case WARNING -> WARNING_COLOR;
            case ERROR -> ERROR_COLOR;
            case UNAVAILABLE -> UNAVAILABLE_COLOR;
            case NORMAL -> NORMAL_COLOR;
        };
    }

    private enum LineKind {
        SECTION,
        ENTRY,
        RAW,
        SPACER
    }

    private record RenderLine(LineKind kind, String label, String value, DebugValue.Tone tone) {
        static RenderLine section(String title) {
            return new RenderLine(LineKind.SECTION, "", title, DebugValue.Tone.NORMAL);
        }

        static RenderLine entry(String label, DebugValue value) {
            return new RenderLine(LineKind.ENTRY, label, value.text(), value.tone());
        }

        static RenderLine raw(String value, DebugValue.Tone tone) {
            return new RenderLine(LineKind.RAW, "", value, tone);
        }

        static RenderLine spacer() {
            return new RenderLine(LineKind.SPACER, "", "", DebugValue.Tone.NORMAL);
        }
    }
}
