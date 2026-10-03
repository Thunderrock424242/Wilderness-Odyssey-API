package com.thunder.wildernessodysseyapi.quest.client.workshop;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class QuestWorkshopLayoutTest {
    @Test void smallWindowsCollapseInspectorWithoutOverlappingCanvasAndSidebar() {
        var layout = QuestWorkshopLayout.create(320, 240);
        assertFalse(layout.inspectorVisible()); assertTrue(layout.canvas().width() >= 100);
        assertTrue(layout.sidebar().x() + layout.sidebar().width() <= layout.canvas().x());
        assertTrue(layout.canvas().y() + layout.canvas().height() <= 240);
    }
    @Test void drawingAndHitTestingUseTheSamePanZoomTransform() {
        var layout = QuestWorkshopLayout.create(960, 540); assertTrue(layout.inspectorVisible());
        int screenX = layout.canvas().x() + 30 + 100; int screenY = layout.canvas().y() + 50 + 160;
        var position = layout.toWorld(screenX, screenY, 30, 50, 2);
        assertEquals(50, position.x()); assertEquals(80, position.y());
        assertTrue(layout.canvas().contains(screenX, screenY)); assertFalse(layout.canvas().contains(layout.sidebar().x(), layout.sidebar().y()));
    }
}
