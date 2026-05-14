package com.thunder.wildernessodysseyapi.client.codex;

import com.thunder.wildernessodysseyapi.item.ModItems;
import com.thunder.wildernessodysseyapi.lorebook.CodexClientState;
import com.thunder.wildernessodysseyapi.lorebook.LoreBookConfig;
import com.thunder.wildernessodysseyapi.lorebook.LoreBookManager;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class CodexScreen extends Screen {
    private static final int BOOK_WIDTH = 404;
    private static final int BOOK_HEIGHT = 236;
    private static final int PAGE_WIDTH = 164;
    private static final int PAGE_HEIGHT = 188;
    private static final int PAGE_TOP = 34;
    private static final int LEFT_PAGE_X = 34;
    private static final int RIGHT_PAGE_X = 207;
    private static final int INK = 0xFF2A2118;
    private static final int FADED_INK = 0xFF6B5644;
    private static final int WARNING_INK = 0xFF7D1E18;
    private static final int PAPER = 0xFFE8D4AD;
    private static final int PAPER_DARK = 0xFFD0B787;
    private static final int LEATHER = 0xFF4A251C;
    private static final int LEATHER_DARK = 0xFF24100D;

    private int spreadIndex;

    public CodexScreen() {
        super(Component.literal("Wilderness Field Codex"));
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(graphics, mouseX, mouseY, partialTick);
        List<CodexPage> pages = pages();
        clampSpread(pages.size());

        int bookX = bookX();
        int bookY = bookY();
        renderBook(graphics, bookX, bookY);
        renderTabs(graphics, bookX, bookY, pages, mouseX, mouseY);

        renderPage(graphics, pages.get(spreadIndex), bookX + LEFT_PAGE_X, bookY + PAGE_TOP, false);
        if (spreadIndex + 1 < pages.size()) {
            renderPage(graphics, pages.get(spreadIndex + 1), bookX + RIGHT_PAGE_X, bookY + PAGE_TOP, true);
        } else {
            renderBlankPage(graphics, bookX + RIGHT_PAGE_X, bookY + PAGE_TOP);
        }

        renderNavigation(graphics, bookX, bookY, pages.size(), mouseX, mouseY);
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    private void renderBook(GuiGraphics graphics, int bookX, int bookY) {
        graphics.fill(0, 0, this.width, this.height, 0xD0090808);
        graphics.fill(bookX + 8, bookY + 10, bookX + BOOK_WIDTH + 8, bookY + BOOK_HEIGHT + 10, 0x66000000);
        graphics.fill(bookX, bookY, bookX + BOOK_WIDTH, bookY + BOOK_HEIGHT, LEATHER_DARK);
        graphics.fill(bookX + 6, bookY + 6, bookX + BOOK_WIDTH - 6, bookY + BOOK_HEIGHT - 6, LEATHER);
        graphics.fill(bookX + 17, bookY + 20, bookX + BOOK_WIDTH - 17, bookY + BOOK_HEIGHT - 14, 0xFFB38453);
        graphics.fill(bookX + 24, bookY + 24, bookX + 197, bookY + BOOK_HEIGHT - 18, PAPER_DARK);
        graphics.fill(bookX + 29, bookY + 29, bookX + 191, bookY + BOOK_HEIGHT - 23, PAPER);
        graphics.fill(bookX + 213, bookY + 24, bookX + BOOK_WIDTH - 24, bookY + BOOK_HEIGHT - 18, PAPER_DARK);
        graphics.fill(bookX + 218, bookY + 29, bookX + BOOK_WIDTH - 29, bookY + BOOK_HEIGHT - 23, PAPER);
        graphics.fill(bookX + 198, bookY + 16, bookX + 206, bookY + BOOK_HEIGHT - 10, 0xFF2E1713);
        graphics.fill(bookX + 201, bookY + 18, bookX + 203, bookY + BOOK_HEIGHT - 12, 0xFF6D3C2C);
        graphics.fill(bookX + 36, bookY + 26, bookX + 184, bookY + 28, 0x34FFFFFF);
        graphics.fill(bookX + 225, bookY + 26, bookX + 371, bookY + 28, 0x34FFFFFF);
    }

    private void renderTabs(GuiGraphics graphics, int bookX, int bookY, List<CodexPage> pages, int mouseX, int mouseY) {
        List<String> sections = sections(pages);
        int tabX = bookX + BOOK_WIDTH - 3;
        int tabY = bookY + 34;
        for (int i = 0; i < sections.size(); i++) {
            String section = sections.get(i);
            int y = tabY + i * 30;
            boolean hover = mouseX >= tabX && mouseX <= tabX + 58 && mouseY >= y && mouseY <= y + 24;
            int color = hover ? 0xFFE0C074 : 0xFF94653D;
            graphics.fill(tabX, y, tabX + 58, y + 24, color);
            graphics.fill(tabX, y + 22, tabX + 58, y + 24, 0x55200012);
            graphics.drawString(this.font, shortSection(section), tabX + 6, y + 8, 0xFF1C100B, false);
        }
    }

    private void renderPage(GuiGraphics graphics, CodexPage page, int x, int y, boolean rightPage) {
        graphics.drawString(this.font, Component.literal(page.section().toUpperCase(Locale.ROOT)), x, y, FADED_INK, false);
        graphics.hLine(x, x + PAGE_WIDTH - 8, y + 12, 0x77664D31);
        graphics.drawString(this.font, Component.literal(page.title()), x, y + 19, page.redacted() ? WARNING_INK : INK, false);

        int textY = y + 37;
        if (page.showIcon()) {
            graphics.renderItem(new ItemStack(ModItems.FIELD_CODEX.get()), x + PAGE_WIDTH - 24, y + 16);
        }

        for (String paragraph : page.paragraphs()) {
            textY = drawWrapped(graphics, Component.literal(paragraph), x, textY, PAGE_WIDTH - 8, page.redacted() ? 0xFF372A20 : INK);
            textY += 5;
            if (textY > y + PAGE_HEIGHT - 18) {
                break;
            }
        }

        if (page.redacted()) {
            renderRedactionBars(graphics, x, y + 58, rightPage);
        }
        graphics.drawString(this.font, Component.literal(String.valueOf(spreadIndex + (rightPage ? 2 : 1))), x + PAGE_WIDTH - 15, y + PAGE_HEIGHT - 8, FADED_INK, false);
    }

    private void renderBlankPage(GuiGraphics graphics, int x, int y) {
        graphics.drawCenteredString(this.font, Component.literal("End of current file"), x + PAGE_WIDTH / 2, y + 82, FADED_INK);
    }

    private int drawWrapped(GuiGraphics graphics, Component text, int x, int y, int width, int color) {
        List<FormattedCharSequence> lines = this.font.split(text, width);
        for (FormattedCharSequence line : lines) {
            graphics.drawString(this.font, line, x, y, color, false);
            y += this.font.lineHeight + 1;
        }
        return y;
    }

    private void renderRedactionBars(GuiGraphics graphics, int x, int y, boolean rightPage) {
        int offset = rightPage ? 7 : 0;
        graphics.fill(x + 12, y + 2, x + 118 + offset, y + 11, 0xF8000000);
        graphics.fill(x + 4, y + 33, x + 92 + offset, y + 42, 0xF8000000);
        graphics.fill(x + 58, y + 63, x + 148, y + 72, 0xF8000000);
        graphics.fill(x + 18, y + 96, x + 134 + offset, y + 105, 0xF8000000);
        graphics.drawString(this.font, Component.literal("CLEARANCE DENIED"), x + 20, y + 126, WARNING_INK, false);
    }

    private void renderNavigation(GuiGraphics graphics, int bookX, int bookY, int pageCount, int mouseX, int mouseY) {
        boolean canPrev = spreadIndex > 0;
        boolean canNext = spreadIndex + 2 < pageCount;
        drawNavButton(graphics, bookX + 35, bookY + BOOK_HEIGHT - 20, "<", canPrev, isInside(mouseX, mouseY, bookX + 35, bookY + BOOK_HEIGHT - 20, 28, 14));
        drawNavButton(graphics, bookX + BOOK_WIDTH - 63, bookY + BOOK_HEIGHT - 20, ">", canNext, isInside(mouseX, mouseY, bookX + BOOK_WIDTH - 63, bookY + BOOK_HEIGHT - 20, 28, 14));
        String progress = "Pages " + (spreadIndex + 1) + "-" + Math.min(spreadIndex + 2, pageCount) + " / " + pageCount;
        graphics.drawCenteredString(this.font, Component.literal(progress), bookX + BOOK_WIDTH / 2, bookY + BOOK_HEIGHT - 18, 0xFFDDC092);
    }

    private void drawNavButton(GuiGraphics graphics, int x, int y, String label, boolean enabled, boolean hover) {
        int color = enabled ? (hover ? 0xFFE0C074 : 0xFFC79B61) : 0xFF6E5840;
        graphics.fill(x, y, x + 28, y + 14, color);
        graphics.fill(x, y + 12, x + 28, y + 14, 0x55200012);
        graphics.drawCenteredString(this.font, Component.literal(label), x + 14, y + 3, enabled ? 0xFF21130D : 0xFF3A2E22);
    }

    private List<CodexPage> pages() {
        List<CodexPage> pages = new ArrayList<>();
        pages.add(new CodexPage("Field Guide", "Wilderness Field Codex", List.of(
                "Issued to any survivor leaving cryostasis with memory gaps, bad weather, or a suspicious amount of ash on their boots.",
                "This book is part guide, part archive. The useful pages are open now. The stranger files decrypt as you recover field documents."
        ), false, true));
        pages.add(new CodexPage("Field Guide", "First Hour Protocol", List.of(
                "1. Find shelter before nightfall.",
                "2. Mark bunker entrances and impact sites.",
                "3. Treat purple weather, glassed soil, and humming stone as active hazards.",
                "4. If a page is redacted, assume the missing part was expensive to learn."
        ), false, false));
        pages.add(new CodexPage("Field Guide", "Cryostasis Exit Notes", List.of(
                "Waking is not the same as being safe. Expect disorientation, pressure headaches, and false memories for the first few minutes.",
                "The cryo tube is your anchor. Leave a path back before chasing signals."
        ), false, false));
        pages.add(new CodexPage("Field Guide", "Riftfall Advisory", List.of(
                "Riftfall events change visibility, sound, and navigation confidence. Do not travel by memory during an active fall.",
                "If the sky dims without a storm front, get under cover and wait for the air to stop vibrating."
        ), false, false));
        pages.add(new CodexPage("Field Guide", "Impact Site Conduct", List.of(
                "Meteor sites are valuable and unstable. Approach from high ground, watch for exposed caves, and do not mine the center first.",
                "Recovered fragments may carry archive keys. Log everything before breaking it."
        ), false, false));
        pages.add(new CodexPage("Redacted Dossiers", "Document WO-7A", List.of(
                "Subject: post-collapse wilderness adaptation.",
                "Conclusion: standard survival guidance is insufficient. The subject is not only surviving the wilderness. The wilderness is answering back."
        ), true, false));
        pages.add(new CodexPage("Redacted Dossiers", "Order Signal Fragment", List.of(
                "The transmission repeats every seventeen minutes from below the old stone line.",
                "Do not answer with your real name. Do not answer with anyone else's."
        ), true, false));
        pages.add(new CodexPage("Redacted Dossiers", "The Before", List.of(
                "Cross-reference: temporal rift core, ancient capsule sites, missing expedition rosters.",
                "The destination is not a place. It is a version."
        ), true, false));

        List<LoreBookConfig.LoreBookEntry> entries = LoreBookManager.config().books();
        if (entries.isEmpty()) {
            pages.add(new CodexPage("Recovered Archive", "No Recovered Entries", List.of(
                    "No field documents are configured yet. Add archive entries to lore_books.json and they will appear here as recoverable records."
            ), false, false));
        }
        for (LoreBookConfig.LoreBookEntry entry : entries) {
            String id = entry.id() == null ? "unknown" : entry.id();
            String title = entry.title() == null || entry.title().isBlank() ? "Archive Entry " + id : entry.title();
            if (CodexClientState.hasCollected(id)) {
                pages.add(new CodexPage("Recovered Archive", title, paragraphs(entry), false, false));
            } else {
                pages.add(new CodexPage("Recovered Archive", title + " [LOCKED]", List.of(
                        "A matching field document exists, but this codex only has the index stub.",
                        "Recover the physical page in the world to restore the text."
                ), true, false));
            }
        }
        return pages;
    }

    private List<String> paragraphs(LoreBookConfig.LoreBookEntry entry) {
        if (entry.pages() == null || entry.pages().isEmpty()) {
            return List.of("Recovered entry has no written pages.");
        }
        return entry.pages();
    }

    private List<String> sections(List<CodexPage> pages) {
        List<String> sections = new ArrayList<>();
        for (CodexPage page : pages) {
            if (!sections.contains(page.section())) {
                sections.add(page.section());
            }
        }
        return sections;
    }

    private String shortSection(String section) {
        return switch (section) {
            case "Field Guide" -> "Guide";
            case "Redacted Dossiers" -> "Files";
            case "Recovered Archive" -> "Archive";
            default -> section.length() > 7 ? section.substring(0, 7) : section;
        };
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int bookX = bookX();
        int bookY = bookY();
        List<CodexPage> pages = pages();
        if (isInside((int) mouseX, (int) mouseY, bookX + 35, bookY + BOOK_HEIGHT - 20, 28, 14) && spreadIndex > 0) {
            spreadIndex = Math.max(0, spreadIndex - 2);
            return true;
        }
        if (isInside((int) mouseX, (int) mouseY, bookX + BOOK_WIDTH - 63, bookY + BOOK_HEIGHT - 20, 28, 14) && spreadIndex + 2 < pages.size()) {
            spreadIndex += 2;
            return true;
        }

        int tabX = bookX + BOOK_WIDTH - 3;
        int tabY = bookY + 34;
        List<String> sections = sections(pages);
        for (int i = 0; i < sections.size(); i++) {
            int y = tabY + i * 30;
            if (isInside((int) mouseX, (int) mouseY, tabX, y, 58, 24)) {
                jumpToSection(pages, sections.get(i));
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == 262) {
            List<CodexPage> pages = pages();
            if (spreadIndex + 2 < pages.size()) {
                spreadIndex += 2;
            }
            return true;
        }
        if (keyCode == 263) {
            spreadIndex = Math.max(0, spreadIndex - 2);
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private void jumpToSection(List<CodexPage> pages, String section) {
        for (int i = 0; i < pages.size(); i++) {
            if (section.equals(pages.get(i).section())) {
                spreadIndex = i % 2 == 0 ? i : i - 1;
                return;
            }
        }
    }

    private boolean isInside(int mouseX, int mouseY, int x, int y, int width, int height) {
        return mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + height;
    }

    private void clampSpread(int pageCount) {
        if (pageCount <= 0) {
            spreadIndex = 0;
            return;
        }
        int maxSpread = ((pageCount - 1) / 2) * 2;
        if (spreadIndex > maxSpread) {
            spreadIndex = maxSpread;
        }
        if (spreadIndex < 0) {
            spreadIndex = 0;
        }
    }

    private int bookX() {
        return Math.max(8, (this.width - BOOK_WIDTH) / 2);
    }

    private int bookY() {
        return Math.max(8, (this.height - BOOK_HEIGHT) / 2);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private record CodexPage(String section, String title, List<String> paragraphs, boolean redacted, boolean showIcon) {
    }
}
