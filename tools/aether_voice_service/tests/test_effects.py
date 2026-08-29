from __future__ import annotations

import unittest

from aether_voice_service.effects import adjusted_speed


class VoiceEffectsTest(unittest.TestCase):
    def test_calm_delivery_stays_near_normal_conversational_pace(self) -> None:
        normal = adjusted_speed(1.0, "normal")
        calm = adjusted_speed(1.0, "calm")

        self.assertAlmostEqual(1.0, normal)
        self.assertAlmostEqual(0.98, calm)
        self.assertGreater(calm, 0.95)


if __name__ == "__main__":
    unittest.main()
