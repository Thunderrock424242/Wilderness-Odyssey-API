package com.thunder.wildernessodysseyapi.quest.client.workshop;

import com.thunder.wildernessodysseyapi.quest.workshop.QuestWorkshopDraft;

/** One geometry contract for draw, clipping, hit tests and responsive inspector collapse. */
public record QuestWorkshopLayout(Rect sidebar, Rect canvas, Rect inspector, boolean inspectorVisible) {
    public record Rect(int x, int y, int width, int height) {
        public boolean contains(double px, double py) { return px >= x && py >= y && px < x + width && py < y + height; }
    }
    public static QuestWorkshopLayout create(int width, int height) {
        int side = width >= 600 ? 150 : 100; boolean visible = width >= 700; int inspector = visible ? 200 : 0;
        return new QuestWorkshopLayout(new Rect(6, 62, side, Math.max(40, height - 100)),
                new Rect(side + 12, 62, Math.max(100, width - side - inspector - 24), Math.max(40, height - 100)),
                new Rect(width - inspector - 6, 62, inspector, Math.max(40, height - 100)), visible);
    }
    public QuestWorkshopDraft.Position toWorld(double x, double y, double panX, double panY, double zoom) {
        return new QuestWorkshopDraft.Position((int) Math.clamp(Math.round((x - canvas.x() - panX) / zoom), -100_000, 100_000),
                (int) Math.clamp(Math.round((y - canvas.y() - panY) / zoom), -100_000, 100_000));
    }
}
