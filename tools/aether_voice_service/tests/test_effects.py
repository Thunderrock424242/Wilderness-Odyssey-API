from __future__ import annotations

import unittest

from aether_voice_service.effects import adjusted_speed


class VoiceEffectsTest(unittest.TestCase):
    def test_calm_delivery_is_slower_than_normal_without_becoming_drawn_out(self) -> None:
        normal = adjusted_speed(1.0, "normal")
        calm = adjusted_speed(1.0, "calm")

        self.assertAlmostEqual(1.0, normal)
        self.assertAlmostEqual(0.93, calm)
        self.assertGreater(calm, 0.85)


if __name__ == "__main__":
    unittest.main()
