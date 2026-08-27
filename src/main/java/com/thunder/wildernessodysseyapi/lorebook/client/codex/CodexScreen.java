package com.thunder.wildernessodysseyapi.lorebook.client.codex;

import com.thunder.wildernessodysseyapi.ai.voice.client.AetherVoiceClient;
import com.thunder.wildernessodysseyapi.ai.voice.config.AetherVoiceConfig;
import com.thunder.wildernessodysseyapi.lorebook.CodexClientState;
import com.thunder.wildernessodysseyapi.lorebook.CodexJournalText;
import com.thunder.wildernessodysseyapi.lorebook.LoreBookConfig;
import com.thunder.wildernessodysseyapi.lorebook.LoreBookManager;
import com.thunder.wildernessodysseyapi.lorebook.network.SaveCodexJournalPayload;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.MultiLineEditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import static com.thunder.wildernessodysseyapi.lorebook.client.codex.CodexLayout.BOOK_HEIGHT;
import static com.thunder.wildernessodysseyapi.lorebook.client.codex.CodexLayout.BOOK_WIDTH;
import static com.thunder.wildernessodysseyapi.lorebook.client.codex.CodexLayout.LEFT_PAGE_X;
import static com.thunder.wildernessodysseyapi.lorebook.client.codex.CodexLayout.PAGE_HEIGHT;
import static com.thunder.wildernessodysseyapi.lorebook.client.codex.CodexLayout.PAGE_TOP;
import static com.thunder.wildernessodysseyapi.lorebook.client.codex.CodexLayout.PAGE_WIDTH;
import static com.thunder.wildernessodysseyapi.lorebook.client.codex.CodexLayout.RIGHT_PAGE_X;
import static com.thunder.wildernessodysseyapi.lorebook.client.codex.CodexLayout.TAB_HEIGHT;
import static com.thunder.wildernessodysseyapi.lorebook.client.codex.CodexLayout.TAB_WIDTH;

/**
 * Presents the Field Codex as a starter guide, writable journal, and lore library.
 *
 * <p>Personal text is edited locally and sent to the server as a complete,
 * bounded snapshot. Lore pages remain read-only and only appear after the
 * server confirms that the player recovered their physical journal.</p>
 */
public class CodexScreen extends Screen {
    private static final int INK = 0xFF2A2118;
    private static final int FADED_INK = 0xFF6B5644;
    private static final int WARNING_INK = 0xFF7D1E18;
    private static final int PAPER = 0xFFE8D4AD;
    private static final int PAPER_DARK = 0xFFD0B787;
    private static final int LEATHER = 0xFF4A251C;
    private static final int LEATHER_DARK = 0xFF24100D;

    private CodexView selectedView = CodexView.GUIDE;
    private MultiLineEditBox journalEditor;
    private Button saveButton;
    private Button readAloudButton;
    private String lastSavedText = "";
    private String draftText;
    private boolean journalDirty;
    private int loreSpreadIndex;

    /** Creates the non-pausing Codex screen after server state has synchronized. */
    public CodexScreen() {
        super(Component.literal("Wilderness Field Codex"));
    }

    // Widgets are positioned inside the parchment pages so resize/re-init keeps
    // the editor aligned with the hand-drawn book frame.
    @Override
    protected void init() {
        int bookX = bookX();
        int bookY = bookY();
        if (draftText == null) {
            draftText = CodexClientState.journalText();
            lastSavedText = draftText;
        }

        this.journalEditor = this.addRenderableWidget(new MultiLineEditBox(
                this.font,
                bookX + LEFT_PAGE_X,
                bookY + PAGE_TOP + 39,
                PAGE_WIDTH - 10,
                126,
                Component.literal("Write your field notes here..."),
                Component.literal("Personal journal")
        ));
        this.journalEditor.setCharacterLimit(CodexJournalText.MAX_LENGTH);
        this.journalEditor.setValue(draftText);
        this.journalEditor.setValueListener(this::onJournalChanged);
        this.journalDirty = !draftText.equals(lastSavedText);

        this.saveButton = this.addRenderableWidget(Button.builder(
                Component.literal("Save Journal"),
                ignored -> saveJournal()
        ).bounds(bookX + RIGHT_PAGE_X + 4, bookY + PAGE_TOP + PAGE_HEIGHT - 28, PAGE_WIDTH - 16, 18).build());
        this.readAloudButton = this.addRenderableWidget(Button.builder(
                Component.literal("Read Aloud"),
                ignored -> readCurrentLoreSpread()
        ).bounds(bookX + BOOK_WIDTH / 2 - 45, bookY + BOOK_HEIGHT - 40, 90, 16).build());
        updateWidgetVisibility();
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        int bookX = bookX();
        int bookY = bookY();
        renderBook(graphics, bookX, bookY);
        renderTabs(graphics, bookX, bookY, mouseX, mouseY);
        switch (selectedView) {
            case GUIDE -> renderGuide(graphics, bookX, bookY);
            case JOURNAL -> renderJournal(graphics, bookX, bookY);
            case LORE -> renderLoreLibrary(graphics, bookX, bookY, mouseX, mouseY);
        }

        // Render interactive widgets last so the editor and save button sit on
        // top of the parchment artwork.
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    private void renderBook(GuiGraphics graphics, int bookX, int bookY) {
        graphics.fill(0, 0, this.width, this.height, 0xD0090808);
        graphics.fill(bookX + 8, bookY + 10, bookX + BOOK_WIDTH + 8, bookY + BOOK_HEIGHT + 10, 0x66000000);
        graphics.fill(bookX, bookY, bookX + BOOK_WIDTH, bookY + BOOK_HEIGHT, LEATHER_DARK);
        graphics.fill(bookX + 6, bookY + 6, bookX + BOOK_WIDTH - 6, bookY + BOOK_HEIGHT - 6, LEATHER);
        graphics.fill(bookX + 17, bookY + 20, bookX + BOOK_WIDTH - 17, bookY + BOOK_HEIGHT - 14, 0xFFB38453);
        graphics.fill(bookX + 24, bookY + 24, bookX + 191, bookY + BOOK_HEIGHT - 18, PAPER_DARK);
        graphics.fill(bookX + 29, bookY + 29, bookX + 186, bookY + BOOK_HEIGHT - 23, PAPER);
        graphics.fill(bookX + 197, bookY + 24, bookX + BOOK_WIDTH - 24, bookY + BOOK_HEIGHT - 18, PAPER_DARK);
        graphics.fill(bookX + 202, bookY + 29, bookX + BOOK_WIDTH - 29, bookY + BOOK_HEIGHT - 23, PAPER);
        graphics.fill(bookX + 190, bookY + 16, bookX + 198, bookY + BOOK_HEIGHT - 10, 0xFF2E1713);
        graphics.fill(bookX + 193, bookY + 18, bookX + 195, bookY + BOOK_HEIGHT - 12, 0xFF6D3C2C);
    }

    private void renderTabs(GuiGraphics graphics, int bookX, int bookY, int mouseX, int mouseY) {
        int tabY = CodexLayout.tabY(bookY);
        drawTab(graphics, "Guide", CodexView.GUIDE, CodexLayout.tabX(bookX, 0), tabY, mouseX, mouseY);
        drawTab(graphics, "Journal", CodexView.JOURNAL, CodexLayout.tabX(bookX, 1), tabY, mouseX, mouseY);
        drawTab(graphics, "Lore", CodexView.LORE, CodexLayout.tabX(bookX, 2), tabY, mouseX, mouseY);
    }

    // The guide is deliberately short and always available. It explains only
    // verified starting behavior and points players toward the two lasting
    // Codex activities: writing notes and recovering lore journals.
    private void renderGuide(GuiGraphics graphics, int bookX, int bookY) {
        int leftX = bookX + LEFT_PAGE_X;
        int rightX = bookX + RIGHT_PAGE_X;
        int pageY = bookY + PAGE_TOP;

        renderPageHeading(graphics, leftX, pageY, "FIELD GUIDE", "Your First Day");
        renderGuideParagraphs(graphics, leftX, pageY + 39, List.of(
                "1. Leave the cryo tube and take stock. You cannot climb back inside.",
                "2. Find shelter before nightfall. Gather food, wood, stone, and light.",
                "3. Save coordinates, plans, and discoveries in your Journal."
        ));

        renderPageHeading(graphics, rightX, pageY, "FIELD GUIDE", "Know the Signs");
        renderGuideParagraphs(graphics, rightX, pageY + 39, List.of(
                "Purple skies and violent storms in the Echo warn of Riftfall activity. Seek cover.",
                "Meteor craters may be unstable and irradiated. Keep your distance.",
                "Find lore journals in generated chests. Carry them to preserve their pages."
        ));
    }

    private void renderGuideParagraphs(GuiGraphics graphics, int x, int y, List<String> paragraphs) {
        for (String paragraph : paragraphs) {
            y = drawWrapped(graphics, Component.literal(paragraph), x, y, PAGE_WIDTH - 10, INK);
            y += 7;
        }
    }

    private void drawTab(
            GuiGraphics graphics,
            String label,
            CodexView view,
            int x,
            int y,
            int mouseX,
            int mouseY
    ) {
        boolean hover = isInside(mouseX, mouseY, x, y, TAB_WIDTH, TAB_HEIGHT);
        boolean selected = selectedView == view;
        int color = selected ? 0xFFE0C074 : hover ? 0xFFC79B61 : 0xFF94653D;
        graphics.fill(x, y, x + TAB_WIDTH, y + TAB_HEIGHT, color);
        graphics.fill(x, y + TAB_HEIGHT - 2, x + TAB_WIDTH, y + TAB_HEIGHT, 0x55200012);
        graphics.drawCenteredString(this.font, Component.literal(label), x + TAB_WIDTH / 2, y + 6, 0xFF1C100B);
    }

    private void renderJournal(GuiGraphics graphics, int bookX, int bookY) {
        int leftX = bookX + LEFT_PAGE_X;
        int rightX = bookX + RIGHT_PAGE_X;
        int pageY = bookY + PAGE_TOP;

        renderPageHeading(graphics, leftX, pageY, "PERSONAL JOURNAL", "Field Notes");
        renderPageHeading(graphics, rightX, pageY, "PERSONAL JOURNAL", "About This Book");

        int textY = pageY + 39;
        textY = drawWrapped(graphics, Component.literal(
                "Use this space for coordinates, discoveries, plans, or anything else you want to remember."
        ), rightX, textY, PAGE_WIDTH - 10, INK);
        textY += 8;
        textY = drawWrapped(graphics, Component.literal(
                "Your writing is saved to your player journal on the server and follows you after respawning."
        ), rightX, textY, PAGE_WIDTH - 10, INK);
        textY += 10;

        String status = journalDirty ? "Unsaved changes" : "Journal saved";
        int statusColor = journalDirty ? WARNING_INK : FADED_INK;
        graphics.drawString(this.font, Component.literal(status), rightX, textY, statusColor, false);
        graphics.drawString(this.font, Component.literal(
                journalEditor.getValue().length() + " / " + CodexJournalText.MAX_LENGTH + " characters"
        ), rightX, textY + 13, FADED_INK, false);
    }

    private void renderLoreLibrary(GuiGraphics graphics, int bookX, int bookY, int mouseX, int mouseY) {
        List<LorePage> pages = lorePages();
        clampLoreSpread(pages.size());

        int leftX = bookX + LEFT_PAGE_X;
        int rightX = bookX + RIGHT_PAGE_X;
        int pageY = bookY + PAGE_TOP;
        if (pages.isEmpty()) {
            renderPageHeading(graphics, leftX, pageY, "LORE JOURNALS", "No Journals Recovered");
            drawWrapped(graphics, Component.literal(
                    "Lore journals found while exploring will be preserved here after you collect the physical written book."
            ), leftX, pageY + 39, PAGE_WIDTH - 10, INK);
            renderPageHeading(graphics, rightX, pageY, "COLLECTION", collectionProgress());
            drawWrapped(graphics, Component.literal(
                    "Search generated chests and bring recovered journals into your inventory to add them to this library."
            ), rightX, pageY + 39, PAGE_WIDTH - 10, FADED_INK);
        } else {
            renderLorePage(graphics, pages.get(loreSpreadIndex), leftX, pageY);
            if (loreSpreadIndex + 1 < pages.size()) {
                renderLorePage(graphics, pages.get(loreSpreadIndex + 1), rightX, pageY);
            } else {
                renderPageHeading(graphics, rightX, pageY, "LORE JOURNALS", "End of Recovered Text");
            }
        }

        renderLoreNavigation(graphics, bookX, bookY, pages.size(), mouseX, mouseY);
    }

    private void renderLorePage(GuiGraphics graphics, LorePage page, int x, int y) {
        renderPageHeading(graphics, x, y, "LORE JOURNAL", page.title());
        graphics.drawString(this.font, Component.literal("by " + page.author()), x, y + 32, FADED_INK, false);
        drawWrapped(graphics, Component.literal(page.text()), x, y + 48, PAGE_WIDTH - 10, INK);
        graphics.drawString(this.font, Component.literal(
                "Page " + page.pageNumber() + " of " + page.pageCount()
        ), x + PAGE_WIDTH - 69, y + PAGE_HEIGHT - 9, FADED_INK, false);
    }

    private void renderPageHeading(GuiGraphics graphics, int x, int y, String section, String title) {
        graphics.drawString(this.font, Component.literal(section.toUpperCase(Locale.ROOT)), x, y, FADED_INK, false);
        graphics.hLine(x, x + PAGE_WIDTH - 8, y + 12, 0x77664D31);
        graphics.drawString(this.font, Component.literal(title), x, y + 19, INK, false);
    }

    private void renderLoreNavigation(
            GuiGraphics graphics,
            int bookX,
            int bookY,
            int pageCount,
            int mouseX,
            int mouseY
    ) {
        boolean canPrevious = loreSpreadIndex > 0;
        boolean canNext = loreSpreadIndex + 2 < pageCount;
        int y = bookY + BOOK_HEIGHT - 20;
        drawNavButton(graphics, bookX + 35, y, "<", canPrevious,
                isInside(mouseX, mouseY, bookX + 35, y, 28, 14));
        drawNavButton(graphics, bookX + BOOK_WIDTH - 63, y, ">", canNext,
                isInside(mouseX, mouseY, bookX + BOOK_WIDTH - 63, y, 28, 14));
        graphics.drawCenteredString(this.font, Component.literal(collectionProgress()),
                bookX + BOOK_WIDTH / 2, y + 3, 0xFFDDC092);
    }

    private void drawNavButton(GuiGraphics graphics, int x, int y, String label, boolean enabled, boolean hover) {
        int color = enabled ? (hover ? 0xFFE0C074 : 0xFFC79B61) : 0xFF6E5840;
        graphics.fill(x, y, x + 28, y + 14, color);
        graphics.fill(x, y + 12, x + 28, y + 14, 0x55200012);
        graphics.drawCenteredString(this.font, Component.literal(label), x + 14, y + 3,
                enabled ? 0xFF21130D : 0xFF3A2E22);
    }

    private int drawWrapped(GuiGraphics graphics, Component text, int x, int y, int width, int color) {
        List<FormattedCharSequence> lines = this.font.split(text, width);
        for (FormattedCharSequence line : lines) {
            if (y > bookY() + PAGE_TOP + PAGE_HEIGHT - 20) {
                break;
            }
            graphics.drawString(this.font, line, x, y, color, false);
            y += this.font.lineHeight + 1;
        }
        return y;
    }

    private List<LorePage> lorePages() {
        List<LorePage> pages = new ArrayList<>();
        for (LoreBookConfig.LoreBookEntry entry : collectedLoreEntries()) {
            String title = entry.title() == null || entry.title().isBlank() ? "Recovered Journal" : entry.title();
            String author = entry.author() == null || entry.author().isBlank() ? "Unknown" : entry.author();
            List<String> entryPages = entry.pages() == null || entry.pages().isEmpty()
                    ? List.of("This recovered journal contains no readable text.")
                    : entry.pages();
            for (int index = 0; index < entryPages.size(); index++) {
                String text = entryPages.get(index) == null ? "" : entryPages.get(index);
                pages.add(new LorePage(title, author, text, index + 1, entryPages.size()));
            }
        }
        return pages;
    }

    private List<LoreBookConfig.LoreBookEntry> collectedLoreEntries() {
        return LoreBookManager.config().books().stream()
                .filter(entry -> entry.id() != null && !entry.id().isBlank())
                .filter(entry -> CodexClientState.hasCollected(entry.id()))
                .toList();
    }

    private String collectionProgress() {
        long configuredCount = LoreBookManager.config().books().stream()
                .filter(entry -> entry.id() != null && !entry.id().isBlank())
                .count();
        return collectedLoreEntries().size() + " / " + configuredCount + " collected";
    }

    private void onJournalChanged(String text) {
        this.draftText = CodexJournalText.sanitize(text);
        this.journalDirty = !draftText.equals(lastSavedText);
        if (saveButton != null) {
            saveButton.active = journalDirty;
        }
    }

    private void saveJournal() {
        if (journalEditor == null || !journalDirty) {
            return;
        }

        String text = CodexJournalText.sanitize(journalEditor.getValue());
        PacketDistributor.sendToServer(new SaveCodexJournalPayload(text));
        CodexClientState.syncJournal(text);
        draftText = text;
        lastSavedText = text;
        journalDirty = false;
        saveButton.active = false;
    }

    private void selectView(CodexView view) {
        if (selectedView == view) {
            return;
        }
        if (selectedView == CodexView.JOURNAL) {
            saveJournal();
        }
        selectedView = view;
        updateWidgetVisibility();
    }

    private void updateWidgetVisibility() {
        if (journalEditor != null) {
            journalEditor.visible = selectedView == CodexView.JOURNAL;
            journalEditor.active = selectedView == CodexView.JOURNAL;
        }
        if (saveButton != null) {
            saveButton.visible = selectedView == CodexView.JOURNAL;
            saveButton.active = selectedView == CodexView.JOURNAL && journalDirty;
        }
        if (readAloudButton != null) {
            boolean canRead = selectedView == CodexView.LORE
                    && AetherVoiceClient.isVoiceAvailable()
                    && AetherVoiceConfig.LORE_READ_ALOUD.get()
                    && !lorePages().isEmpty();
            readAloudButton.visible = canRead;
            readAloudButton.active = canRead;
        }
    }

    private void readCurrentLoreSpread() {
        List<LorePage> pages = lorePages();
        if (loreSpreadIndex < 0 || loreSpreadIndex >= pages.size()) {
            return;
        }
        LorePage left = pages.get(loreSpreadIndex);
        List<String> pageTexts = new ArrayList<>();
        pageTexts.add(left.text());
        if (loreSpreadIndex + 1 < pages.size()) {
            pageTexts.add(pages.get(loreSpreadIndex + 1).text());
        }
        AetherVoiceClient.speakAuthored(
                LoreNarration.fromSpread(left.title(), left.author(), pageTexts),
                false
        );
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int bookX = bookX();
        int bookY = bookY();
        int tabY = CodexLayout.tabY(bookY);
        if (button == 0 && isInside((int) mouseX, (int) mouseY,
                CodexLayout.tabX(bookX, 0), tabY, TAB_WIDTH, TAB_HEIGHT)) {
            selectView(CodexView.GUIDE);
            return true;
        }
        if (button == 0 && isInside((int) mouseX, (int) mouseY,
                CodexLayout.tabX(bookX, 1), tabY, TAB_WIDTH, TAB_HEIGHT)) {
            selectView(CodexView.JOURNAL);
            return true;
        }
        if (button == 0 && isInside((int) mouseX, (int) mouseY,
                CodexLayout.tabX(bookX, 2), tabY, TAB_WIDTH, TAB_HEIGHT)) {
            selectView(CodexView.LORE);
            return true;
        }

        if (selectedView == CodexView.LORE && button == 0) {
            List<LorePage> pages = lorePages();
            int navigationY = bookY + BOOK_HEIGHT - 20;
            if (isInside((int) mouseX, (int) mouseY, bookX + 35, navigationY, 28, 14)
                    && loreSpreadIndex > 0) {
                loreSpreadIndex = Math.max(0, loreSpreadIndex - 2);
                return true;
            }
            if (isInside((int) mouseX, (int) mouseY, bookX + BOOK_WIDTH - 63, navigationY, 28, 14)
                    && loreSpreadIndex + 2 < pages.size()) {
                loreSpreadIndex += 2;
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (selectedView == CodexView.LORE) {
            if (keyCode == 262 && loreSpreadIndex + 2 < lorePages().size()) {
                loreSpreadIndex += 2;
                return true;
            }
            if (keyCode == 263 && loreSpreadIndex > 0) {
                loreSpreadIndex = Math.max(0, loreSpreadIndex - 2);
                return true;
            }
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public void onClose() {
        saveJournal();
        super.onClose();
    }

    private void clampLoreSpread(int pageCount) {
        int maxSpread = pageCount <= 0 ? 0 : ((pageCount - 1) / 2) * 2;
        loreSpreadIndex = Math.max(0, Math.min(loreSpreadIndex, maxSpread));
    }

    private boolean isInside(int mouseX, int mouseY, int x, int y, int width, int height) {
        return mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + height;
    }

    private int bookX() {
        return CodexLayout.bookX(this.width);
    }

    private int bookY() {
        return CodexLayout.bookY(this.height);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private enum CodexView {
        GUIDE,
        JOURNAL,
        LORE
    }

    private record LorePage(String title, String author, String text, int pageNumber, int pageCount) {
    }
}
