# Native onedir package, built only with the isolated GUI interpreter.
from pathlib import Path
from PyInstaller.utils.hooks import copy_metadata

root=Path(SPECPATH).resolve().parents[2]
feature=root/'tools/aether_teacher'
a=Analysis([str(feature/'launch.py')],pathex=[str(root)],
    datas=[(str(feature/'resources'),'tools/aether_teacher/resources'),*copy_metadata('psutil')],
    hiddenimports=[],excludes=['torch','transformers','peft','bitsandbytes','gguf',
      'PySide6.QtWebEngineCore','PySide6.QtWebEngineWidgets','PySide6.QtPdf','tkinter'],
    noarchive=False)
# Qt's wheel supplies a newer compatible MSVC runtime than CPython's NuGet
# package. Put one consistent family at the loader's top-level search location.
import PySide6
qt=Path(PySide6.__file__).parent
crt=('VCRUNTIME140.dll','VCRUNTIME140_1.dll','MSVCP140.dll','MSVCP140_1.dll','MSVCP140_2.dll')
a.binaries=[entry for entry in a.binaries if entry[0].upper() not in {name.upper() for name in crt}]
a.binaries.extend((name,str(qt/name),'BINARY') for name in crt)
pyz=PYZ(a.pure)
exe=EXE(pyz,a.scripts,[],exclude_binaries=True,name='AetherTeacher',debug=False,
    bootloader_ignore_signals=False,strip=False,upx=False,console=False,disable_windowed_traceback=True)
coll=COLLECT(exe,a.binaries,a.datas,strip=False,upx=False,name='AetherTeacher')
