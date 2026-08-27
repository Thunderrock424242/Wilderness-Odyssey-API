package com.thunder.wildernessodysseyapi.client.notice;

/**
 * Calculates alpha-notice geometry independently from rendering.
 *
 * <p>The footer remains fixed inside the viewport while the notice body uses
 * the remaining space as a scrollable region. Narrow viewports stack the two
 * actions so neither button is compressed beyond a useful keyboard target.</p>
 */
final class AlphaNoticeLayout {
    private static final int VIEWPORT_MARGIN = 6;
    private static final int MAX_PANEL_WIDTH = 620;
    private static final int MAX_PANEL_HEIGHT = 430;
    private static final int PANEL_INSET = 16;
    private static final int HEADER_HEIGHT = 70;
    private static final int BUTTON_HEIGHT = 20;
    private static final int BUTTON_GAP = 4;
    private static final int FOOTER_MARGIN = 10;
    private static final int STACK_BUTTONS_BELOW_WIDTH = 350;

    private AlphaNoticeLayout() {
    }

    static Layout calculate(int viewportWidth, int viewportHeight) {
        int safeWidth = Math.max(1, viewportWidth);
        int safeHeight = Math.max(1, viewportHeight);
        int horizontalMargin = Math.min(VIEWPORT_MARGIN, Math.max(0, (safeWidth - 1) / 2));
        int verticalMargin = Math.min(VIEWPORT_MARGIN, Math.max(0, (safeHeight - 1) / 2));
        int panelWidth = Math.min(MAX_PANEL_WIDTH, safeWidth - horizontalMargin * 2);
        int panelHeight = Math.min(MAX_PANEL_HEIGHT, safeHeight - verticalMargin * 2);
        int panelLeft = (safeWidth - panelWidth) / 2;
        int panelTop = (safeHeight - panelHeight) / 2;
        Bounds panel = new Bounds(panelLeft, panelTop, panelLeft + panelWidth, panelTop + panelHeight);

        int inset = Math.min(PANEL_INSET, Math.max(1, (panelWidth - 1) / 4));
        int innerLeft = panel.left() + inset;
        int innerRight = Math.max(innerLeft + 1, panel.right() - inset);
        int innerWidth = innerRight - innerLeft;
        boolean stackedButtons = innerWidth < STACK_BUTTONS_BELOW_WIDTH;
        int footerHeight = stackedButtons ? BUTTON_HEIGHT * 2 + BUTTON_GAP : BUTTON_HEIGHT;
        int buttonTop = Math.max(panel.top(), panel.bottom() - FOOTER_MARGIN - footerHeight);

        Bounds discordButton;
        Bounds continueButton;
        if (stackedButtons) {
            discordButton = new Bounds(innerLeft, buttonTop, innerRight, buttonTop + BUTTON_HEIGHT);
            continueButton = new Bounds(
                    innerLeft,
                    buttonTop + BUTTON_HEIGHT + BUTTON_GAP,
                    innerRight,
                    buttonTop + BUTTON_HEIGHT * 2 + BUTTON_GAP
            );
        } else {
            int gap = 8;
            int buttonWidth = Math.max(1, (innerWidth - gap) / 2);
            discordButton = new Bounds(innerLeft, buttonTop, innerLeft + buttonWidth, buttonTop + BUTTON_HEIGHT);
            continueButton = new Bounds(innerRight - buttonWidth, buttonTop, innerRight, buttonTop + BUTTON_HEIGHT);
        }

        int bodyBottom = Math.max(panel.top() + 1, buttonTop - 8);
        int bodyTop = Math.min(panel.top() + HEADER_HEIGHT, bodyBottom - 1);
        bodyTop = Math.max(panel.top() + 1, bodyTop);
        Bounds body = new Bounds(innerLeft, bodyTop, innerRight, Math.max(bodyTop + 1, bodyBottom));
        return new Layout(panel, body, discordButton, continueButton, stackedButtons);
    }

    record Layout(
            Bounds panel,
            Bounds body,
            Bounds discordButton,
            Bounds continueButton,
            boolean stackedButtons
    ) {
    }

    record Bounds(int left, int top, int right, int bottom) {
        int width() {
            return right - left;
        }

        int height() {
            return bottom - top;
        }

        boolean contains(double x, double y) {
            return x >= left && x < right && y >= top && y < bottom;
        }

        boolean contains(Bounds other) {
            return other.left >= left && other.right <= right && other.top >= top && other.bottom <= bottom;
        }
    }
}
