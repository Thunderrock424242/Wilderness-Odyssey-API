from __future__ import annotations

import os
import unittest
from unittest.mock import patch

from aether_voice_service.config import ServiceSettings


class ServiceSettingsTest(unittest.TestCase):
    def test_defaults_to_numeric_loopback_with_downloads_disabled(self) -> None:
        with patch.dict(os.environ, {}, clear=False):
            self._clear_aether_environment()
            settings = ServiceSettings.from_environment()

        self.assertEqual("127.0.0.1", settings.host)
        self.assertEqual(8765, settings.port)
        self.assertFalse(settings.allow_model_downloads)
        self.assertEqual("small.en", settings.whisper_model)
        self.assertEqual("af_nicole", settings.default_voice)

    def test_rejects_remote_bind_addresses(self) -> None:
        with patch.dict(os.environ, {}, clear=False):
            self._clear_aether_environment()
            os.environ["AETHER_VOICE_HOST"] = "0.0.0.0"
            with self.assertRaisesRegex(ValueError, "127.0.0.1 or ::1"):
                ServiceSettings.from_environment()

    @staticmethod
    def _clear_aether_environment() -> None:
        for name in tuple(os.environ):
            if name.startswith("AETHER_"):
                os.environ.pop(name)


if __name__ == "__main__":
    unittest.main()
