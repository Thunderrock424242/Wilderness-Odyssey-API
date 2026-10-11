package com.thunder.wildernessodysseyapi.temporalrift.hybrid;

import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class RiftApertureClipTest {
    @Test
    void aFaceInsideTheApertureProducesNoRockSurface() {
        var face = List.of(vertex(-1, -1), vertex(1, -1), vertex(1, 1), vertex(-1, 1));
        assertTrue(RiftApertureClip.outside(face).isEmpty());
    }

    @Test
    void crossingFaceRetainsOutsideAreaWithoutCoveringTheOpening() {
        var face = List.of(vertex(-2, -2), vertex(2, -2), vertex(2, 2), vertex(-2, 2));
        var pieces = RiftApertureClip.outside(face);
        double area = 0;
        for (var polygon : pieces) {
            double signed = 0;
            for (int i = 0; i < polygon.size(); i++) {
                var a = polygon.get(i);
                var b = polygon.get((i + 1) % polygon.size());
                signed += a.rx() * b.ry() - b.rx() * a.ry();
            }
            area += Math.abs(signed) / 2;
        }
        assertEquals(7, area, 1.0E-5); // 4x4 face minus 3x3 hole
        assertTrue(pieces.stream().flatMap(List::stream).allMatch(v -> Math.abs(v.rx()) >= 1.4999 || Math.abs(v.ry()) >= 1.4999));
    }

    private static RiftApertureClip.Vertex vertex(float x, float y) {
        return new RiftApertureClip.Vertex(x, y, 0, x, y, (x + 2) / 4, (y + 2) / 4);
    }
}
