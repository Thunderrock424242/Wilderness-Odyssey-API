"""Reproducible Windows GUI package; weights/CUDA remain external."""
from pathlib import Path
import shutil
from tools.aether_teacher.domain import require
from tools.aether_teacher.setup import trusted_manifest, verify_engine


def verify_package(root):
    root=Path(root)
    required=("AetherTeacher.exe","_internal/PySide6/plugins/platforms/qwindows.dll",
              "_internal/tools/aether_teacher/resources/engine-manifest.json","THIRD_PARTY_NOTICES.txt")
    require(all((root/name).is_file() for name in required),"INCOMPLETE_GUI_PACKAGE")
    for path in root.rglob("*"):
        parts={part.lower() for part in path.relative_to(root).parts}
        require(not parts.intersection({"torch","transformers","peft","bitsandbytes","gguf","qtwebenginecore","qtwebenginewidgets"}),"TRAINING_OR_BROWSER_PAYLOAD")
        require(path.suffix.lower() not in {".gguf",".safetensors",".sqlite3",".env"},"PRIVATE_OR_MODEL_PAYLOAD")
        require("webengine" not in path.name.lower(),"BROWSER_PAYLOAD")


def main():
    import PyInstaller.__main__
    import importlib.metadata
    import sys
    import threading
    root=Path(__file__).resolve().parents[3]
    verify_engine(root,trusted_manifest())
    output=root/".codex-build/aether-teacher"
    arguments=[str(Path(__file__).with_name("AetherTeacher.spec")),"--noconfirm",
               "--distpath",str(output/"dist"),"--workpath",str(output/"package-work")]
    # Modulegraph performs deeply nested imports/AST compilation. Keep the build
    # worker's stack explicit; this changes only this process, not Windows policy.
    failures=[]
    def freeze():
        try:PyInstaller.__main__.run(arguments)
        except BaseException as error:failures.append(error)
    previous=threading.stack_size(16*1024*1024)
    try:
        worker=threading.Thread(target=freeze,name="aether-package-build")
        worker.start()
    finally:threading.stack_size(previous)
    worker.join()
    if failures:raise failures[0]
    destination=output/"dist/AetherTeacher"
    shutil.copyfile(Path(__file__).parents[1]/"resources/THIRD_PARTY_NOTICES.txt",destination/"THIRD_PARTY_NOTICES.txt")
    licenses=destination/"THIRD_PARTY_LICENSES"
    licenses.mkdir(exist_ok=True)
    for name in ("PySide6_Essentials","shiboken6","psutil","pyinstaller"):
        distribution=importlib.metadata.distribution(name)
        for relative in distribution.files or ():
            if "license" in str(relative).lower() and distribution.locate_file(relative).is_file():
                shutil.copyfile(distribution.locate_file(relative),licenses/(name+"-"+Path(relative).name))
    python_license=Path(sys.base_prefix)/"LICENSE.txt"
    if python_license.is_file():shutil.copyfile(python_license,licenses/"Python-LICENSE.txt")
    source=Path(__file__).parents[1]/"resources/licenses"
    require(source.is_dir() and (source/"LGPL-3.0.txt").is_file(),"SOURCE_LICENSE_TEXTS_REQUIRED")
    for path in source.iterdir():
        if path.is_file():shutil.copyfile(path,licenses/path.name)
    verify_package(destination)
    print(destination/"AetherTeacher.exe")


if __name__=="__main__":main()
