"""Aether Teacher desktop entry point."""
import sys
import argparse
import json
from pathlib import Path


def main(argv: list[str] | None = None) -> int:
    from PySide6.QtWidgets import QApplication
    from tools.aether_teacher.ui.window import create_window
    from tools.aether_teacher.controller import Controller
    from tools.aether_teacher.setup import default_workspace, trusted_manifest
    parser=argparse.ArgumentParser(description="Native local Aether teaching workspace")
    parser.add_argument("--workspace",type=Path)
    parser.add_argument("--smoke-check",action="store_true")
    options=parser.parse_args(sys.argv[1:] if argv is None else argv)
    app = QApplication([sys.argv[0]])
    app.setApplicationName("Aether Teacher")
    app.setOrganizationName("WildernessOdyssey")
    controller = Controller(options.workspace or default_workspace(), trusted_manifest())
    window = create_window(controller)
    controller.attach(window)
    app.aboutToQuit.connect(controller.close)
    window.show()
    if options.smoke_check:
        from PySide6.QtCore import QTimer
        import time
        started=time.monotonic()
        timer=QTimer(app)
        def check():
            if controller.profile and controller.storage.pending==0 and controller.operations.pending==0:
                window.navigation.button(4).click()
                image=controller.workspace/"native-smoke.png"
                saved=window.grab().save(str(image))
                result={"native":app.platformName()=="windows","platform":app.platformName(),
                        "pages":window.pages.count(),"modelConnected":controller.runtime is not None,"imageSaved":saved}
                (controller.workspace/"native-smoke.json").write_text(json.dumps(result),encoding="utf-8")
                app.exit(0 if saved else 1)
            elif time.monotonic()-started>30:
                app.exit(1)
        timer.timeout.connect(check)
        timer.start(100)
    return app.exec()
