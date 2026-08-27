package com.thunder.wildernessodysseyapi.client.notice;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

/**
 * Presents the Wilderness Odyssey alpha warning as an A.E.T.H.E.R expedition terminal.
 *
 * <p>The header and actions remain fixed while the wrapped warning body scrolls
 * inside its own clipped viewport. Standard Minecraft buttons retain normal
 * mouse, keyboard-focus, controller, tooltip, and narration behavior.</p>
 */
public final class AlphaNoticeScreen extends Screen {
    private static final int BACKDROP_TOP = 0xFF050708;
    private static final int BACKDROP_BOTTOM = 0xFF0C0B08;
    private static final int PANEL = 0xF20B0D0E;
    private static final int PANEL_INNER = 0xFF111313;
    private static final int PANEL_EDGE = 0xFF6B4A20;
    private static final int AMBER = 0xFFFFB44A;
    private static final int AMBER_MUTED = 0xFFC98535;
    private static final int TEXT = 0xFFE8E1D5;
    private static final int TEXT_MUTED = 0xFFAAA295;
    private static final int LINE_HEIGHT = 11;
    private static final int SCROLL_STEP = 22;

    private final Screen mainMenu;
    private AlphaNoticeLayout.Layout layout;
    private List<NoticeLine> bodyLines = List.of();
    private int bodyContentHeight;
    private int scrollOffset;

    /** Creates a notice that continues to the exact title screen supplied by Minecraft or another mod. */
    public AlphaNoticeScreen(Screen mainMenu) {
        super(Component.translatable("screen.wildernessodysseyapi.alpha_notice.title"));
        this.mainMenu = mainMenu;
    }

    @Override
    protected void init() {
        this.layout = AlphaNoticeLayout.calculate(this.width, this.height);
        rebuildBodyLines();
        this.scrollOffset = Math.min(this.scrollOffset, maxScroll());

        AlphaNoticeLayout.Bounds discord = this.layout.discordButton();
        this.addRenderableWidget(Button.builder(
                Component.translatable("screen.wildernessodysseyapi.alpha_notice.join_discord"),
                ignored -> AlphaNoticeManager.openDiscord()
        ).bounds(discord.left(), discord.top(), discord.width(), discord.height()).build());

        AlphaNoticeLayout.Bounds continueButton = this.layout.continueButton();
        this.addRenderableWidget(Button.builder(
                Component.translatable("screen.wildernessodysseyapi.alpha_notice.continue"),
                ignored -> AlphaNoticeManager.acknowledgeAndContinue(this.minecraft, this.mainMenu)
        ).bounds(
                continueButton.left(),
                continueButton.top(),
                continueButton.width(),
                continueButton.height()
        ).build());
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderTerminalBackdrop(graphics);
        renderPanel(graphics);
        renderHeader(graphics);
        renderBody(graphics);
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    /** The screen owns an opaque terminal backdrop and must not receive Minecraft's blur pass. */
    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (scrollY != 0.0D && this.layout.body().contains(mouseX, mouseY) && maxScroll() > 0) {
            scrollBy((int) Math.round(-scrollY * SCROLL_STEP));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        switch (keyCode) {
            case GLFW.GLFW_KEY_PAGE_UP -> {
                scrollBy(-Math.max(SCROLL_STEP, this.layout.body().height() - LINE_HEIGHT));
                return true;
            }
            case GLFW.GLFW_KEY_PAGE_DOWN -> {
                scrollBy(Math.max(SCROLL_STEP, this.layout.body().height() - LINE_HEIGHT));
                return true;
            }
            case GLFW.GLFW_KEY_HOME -> {
                this.scrollOffset = 0;
                return true;
            }
            case GLFW.GLFW_KEY_END -> {
                this.scrollOffset = maxScroll();
                return true;
            }
            default -> {
                return super.keyPressed(keyCode, scanCode, modifiers);
            }
        }
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return false;
    }

    @Override
    public Component getNarrationMessage() {
        return Component.translatable("screen.wildernessodysseyapi.alpha_notice.narration");
    }

    private void renderTerminalBackdrop(GuiGraphics graphics) {
        graphics.fillGradient(0, 0, this.width, this.height, BACKDROP_TOP, BACKDROP_BOTTOM);
        for (int y = 0; y < this.height; y += 6) {
            graphics.fill(0, y, this.width, y + 1, 0x09000000);
        }
    }

    private void renderPanel(GuiGraphics graphics) {
        AlphaNoticeLayout.Bounds panel = this.layout.panel();
        graphics.fill(panel.left() + 5, panel.top() + 6, panel.right() + 5, panel.bottom() + 6, 0x88000000);
        graphics.fill(panel.left(), panel.top(), panel.right(), panel.bottom(), PANEL_EDGE);
        graphics.fill(panel.left() + 1, panel.top() + 1, panel.right() - 1, panel.bottom() - 1, PANEL);
        graphics.fill(panel.left() + 5, panel.top() + 5, panel.right() - 5, panel.bottom() - 5, PANEL_INNER);

        int accentY = panel.top() + 3;
        int firstBreak = Math.min(panel.right() - 8, panel.left() + 116);
        graphics.fill(panel.left() + 8, accentY, firstBreak, accentY + 2, AMBER);
        if (firstBreak + 14 < panel.right() - 8) {
            graphics.fill(firstBreak + 14, accentY, panel.right() - 8, accentY + 2, AMBER_MUTED);
        }
        graphics.fill(panel.left() + 3, panel.top() + 14, panel.left() + 5, panel.bottom() - 20, 0xFF3E2B16);
        graphics.fill(panel.right() - 5, panel.top() + 28, panel.right() - 3, panel.bottom() - 8, 0xFF3E2B16);
    }

    private void renderHeader(GuiGraphics graphics) {
        AlphaNoticeLayout.Bounds panel = this.layout.panel();
        int iconLeft = panel.left() + 15;
        int iconTop = panel.top() + 12;
        graphics.fill(iconLeft, iconTop, iconLeft + 15, iconTop + 15, AMBER_MUTED);
        graphics.fill(iconLeft + 2, iconTop + 2, iconLeft + 13, iconTop + 13, 0xFF23190D);
        graphics.drawCenteredString(this.font, Component.literal("!"), iconLeft + 7, iconTop + 3, AMBER);
        graphics.drawString(
                this.font,
                Component.translatable("screen.wildernessodysseyapi.alpha_notice.aether"),
                iconLeft + 22,
                iconTop + 3,
                AMBER_MUTED,
                false
        );

        int centerX = (panel.left() + panel.right()) / 2;
        graphics.drawCenteredString(
                this.font,
                Component.translatable("screen.wildernessodysseyapi.alpha_notice.brand"),
                centerX,
                panel.top() + 31,
                TEXT
        );
        graphics.drawCenteredString(
                this.font,
                Component.translatable("screen.wildernessodysseyapi.alpha_notice.heading"),
                centerX,
                panel.top() + 43,
                AMBER
        );
        graphics.fill(panel.left() + 10, panel.top() + 56, panel.right() - 10, panel.top() + 66, 0xFF1A140D);
        graphics.drawCenteredString(
                this.font,
                Component.translatable("screen.wildernessodysseyapi.alpha_notice.status"),
                centerX,
                panel.top() + 57,
                AMBER_MUTED
        );
    }

    private void renderBody(GuiGraphics graphics) {
        AlphaNoticeLayout.Bounds body = this.layout.body();
        graphics.enableScissor(body.left(), body.top(), body.right(), body.bottom());
        int y = body.top() - this.scrollOffset;
        for (NoticeLine line : this.bodyLines) {
            y += line.topGap();
            if (y + LINE_HEIGHT >= body.top() && y < body.bottom()) {
                graphics.drawString(this.font, line.text(), body.left(), y, line.color(), false);
            }
            y += LINE_HEIGHT;
        }
        graphics.disableScissor();

        if (maxScroll() > 0) {
            renderScrollBar(graphics, body);
        }
    }

    private void renderScrollBar(GuiGraphics graphics, AlphaNoticeLayout.Bounds body) {
        int trackLeft = body.right() - 3;
        graphics.fill(trackLeft, body.top(), body.right(), body.bottom(), 0xFF231D15);
        int thumbHeight = Math.max(12, body.height() * body.height() / Math.max(1, this.bodyContentHeight));
        thumbHeight = Math.min(body.height(), thumbHeight);
        int travel = body.height() - thumbHeight;
        int thumbTop = body.top() + (maxScroll() == 0 ? 0 : travel * this.scrollOffset / maxScroll());
        graphics.fill(trackLeft, thumbTop, body.right(), thumbTop + thumbHeight, AMBER_MUTED);
    }

    private void rebuildBodyLines() {
        List<NoticeLine> lines = new ArrayList<>();
        int wrapWidth = Math.max(20, this.layout.body().width() - 9);
        addWrapped(lines, "screen.wildernessodysseyapi.alpha_notice.intro", wrapWidth, TEXT, 0);
        addWrapped(lines, "screen.wildernessodysseyapi.alpha_notice.active_development", wrapWidth, TEXT, 6);
        addWrapped(lines, "screen.wildernessodysseyapi.alpha_notice.because", wrapWidth, AMBER, 7);
        addWrapped(lines, "screen.wildernessodysseyapi.alpha_notice.bullet.worlds", wrapWidth, TEXT, 3);
        addWrapped(lines, "screen.wildernessodysseyapi.alpha_notice.bullet.bugs", wrapWidth, TEXT, 2);
        addWrapped(lines, "screen.wildernessodysseyapi.alpha_notice.bullet.features", wrapWidth, TEXT, 2);
        addWrapped(lines, "screen.wildernessodysseyapi.alpha_notice.bullet.backups", wrapWidth, AMBER, 2);
        addWrapped(lines, "screen.wildernessodysseyapi.alpha_notice.community", wrapWidth, TEXT_MUTED, 8);
        this.bodyLines = List.copyOf(lines);
        this.bodyContentHeight = lines.stream().mapToInt(line -> line.topGap() + LINE_HEIGHT).sum();
    }

    private void addWrapped(List<NoticeLine> lines, String translationKey, int width, int color, int topGap) {
        List<FormattedCharSequence> wrapped = this.font.split(Component.translatable(translationKey), width);
        for (int index = 0; index < wrapped.size(); index++) {
            lines.add(new NoticeLine(wrapped.get(index), color, index == 0 ? topGap : 0));
        }
    }

    private void scrollBy(int amount) {
        this.scrollOffset = Math.max(0, Math.min(maxScroll(), this.scrollOffset + amount));
    }

    private int maxScroll() {
        return Math.max(0, this.bodyContentHeight - this.layout.body().height());
    }

    private record NoticeLine(FormattedCharSequence text, int color, int topGap) {
    }
}
