package com.thunder.wildernessodysseyapi.client.notice;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Protects fixed-action visibility across common and compact GUI viewports. */
class AlphaNoticeLayoutTest {

    @Test
    void panelBodyAndButtonsStayInsideEachViewport() {
        int[][] viewports = {
                {320, 180},
                {410, 234},
                {854, 480},
                {1920, 1080}
        };

        for (int[] viewport : viewports) {
            AlphaNoticeLayout.Layout layout = AlphaNoticeLayout.calculate(viewport[0], viewport[1]);
            AlphaNoticeLayout.Bounds screen = new AlphaNoticeLayout.Bounds(0, 0, viewport[0], viewport[1]);

            assertTrue(screen.contains(layout.panel()));
            assertTrue(layout.panel().contains(layout.body()));
            assertTrue(layout.panel().contains(layout.discordButton()));
            assertTrue(layout.panel().contains(layout.continueButton()));
            assertTrue(layout.body().width() > 0);
            assertTrue(layout.body().height() > 0);
            assertTrue(layout.discordButton().width() > 0);
            assertTrue(layout.continueButton().width() > 0);
            assertFalse(overlaps(layout.discordButton(), layout.continueButton()));
        }
    }

    @Test
    void compactLayoutsStackActionsAndWideLayoutsKeepOneFooterRow() {
        assertTrue(AlphaNoticeLayout.calculate(320, 180).stackedButtons());
        assertFalse(AlphaNoticeLayout.calculate(854, 480).stackedButtons());
    }

    private static boolean overlaps(AlphaNoticeLayout.Bounds first, AlphaNoticeLayout.Bounds second) {
        return first.left() < second.right()
                && first.right() > second.left()
                && first.top() < second.bottom()
                && first.bottom() > second.top();
    }
}
