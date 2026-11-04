"""Native source/frozen entry point, independent of the working directory."""
import sys
from pathlib import Path

if not getattr(sys, "frozen", False):
    sys.path.insert(0, str(Path(__file__).resolve().parents[2]))

if __name__ == "__main__":
    try:
        from tools.aether_teacher.app import main
        result=main()
    except Exception as error:
        import json,re,traceback,os
        category=str(error) if re.fullmatch("[A-Z0-9_]{1,96}",str(error)) else type(error).__name__.upper()
        diagnostic={"error":category,"frames":[{"file":Path(frame.filename).name,"line":frame.lineno,"function":frame.name}
                    for frame in traceback.extract_tb(error.__traceback__)[-8:]]}
        if isinstance(error,ImportError):
            name=getattr(error,"name",None)
            if isinstance(name,str) and re.fullmatch("[A-Za-z0-9_.]{1,96}",name):diagnostic["module"]=name
            raw=str(error).lower()
            for marker,label in (("dll load failed","DLL_LOAD_FAILED"),("procedure could not be found","MISSING_PROCEDURE"),
                                 ("module could not be found","MISSING_DLL"),("cannot import name","MISSING_EXPORT")):
                if marker in raw:diagnostic[label]=True
        try:
            root=Path(sys.argv[sys.argv.index("--workspace")+1]) if "--workspace" in sys.argv else Path(os.environ["LOCALAPPDATA"])/"WildernessOdyssey/AetherTeacher"
            from tools.aether_teacher.domain import local_path
            root=local_path(root);root.mkdir(parents=True,exist_ok=True)
            (root/"startup-error.json").write_text(json.dumps(diagnostic),encoding="utf-8")
        except Exception:pass
        if "--smoke-check" not in sys.argv:
            import ctypes
            ctypes.windll.user32.MessageBoxW(None,"Aether Teacher could not start.\n"+category+"\nCheck the private startup-error.json diagnostic.","Aether Teacher",0x10)
        result=1
    raise SystemExit(result)
