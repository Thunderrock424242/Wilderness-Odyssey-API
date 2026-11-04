from pathlib import Path
import tempfile
import unittest
from tools.aether_teacher.domain import Rejected


class LeaseTest(unittest.TestCase):
    def test_only_one_live_application_can_own_workspace_and_death_releases_it(self):
        from tools.aether_teacher.lease import WorkspaceLease
        with tempfile.TemporaryDirectory() as tmp:
            root=Path(tmp)
            with WorkspaceLease(root):
                with self.assertRaisesRegex(Rejected,"WORKSPACE_ALREADY_OPEN"):
                    WorkspaceLease(root)
            with WorkspaceLease(root):pass
