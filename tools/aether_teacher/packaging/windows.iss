[Setup]
AppId={{C8EE42BD-95EE-47D5-A8F0-64C854906586}
AppName=Aether Playground
AppVersion=1.0.1
AppPublisher=Wilderness Odyssey
DefaultDirName={localappdata}\Programs\Aether Playground
DefaultGroupName=Aether Playground
DisableProgramGroupPage=yes
AllowNoIcons=yes
PrivilegesRequired=lowest
MinVersion=10.0.19045
ArchitecturesAllowed=x64os
ArchitecturesInstallIn64BitMode=x64os
OutputBaseFilename=Aether-Playground-Setup-x64
Compression=lzma2/fast
LZMANumBlockThreads=6
SolidCompression=yes
DiskSpanning=yes
DiskSliceSize=2000000000
SlicesPerDisk=20
CloseApplications=yes
RestartApplications=no
UninstallDisplayIcon={app}\python\pythonw.exe
VersionInfoDescription=Aether Playground for Windows

[Tasks]
Name: desktopicon; Description: "Create a desktop shortcut"; Flags: unchecked

[Files]
Source: "{#Prerequisite}"; DestName: "vc_redist.x64.exe"; Flags: dontcopy noencryption
Source: "{#Payload}\*"; DestDir: "{app}"; Flags: ignoreversion recursesubdirs createallsubdirs

[Icons]
Name: "{group}\Aether Playground"; Filename: "{app}\python\pythonw.exe"; Parameters: "-I -B ""{app}\tools\aether_teacher\packaging\windows_entry.py"""; WorkingDir: "{app}"
Name: "{userdesktop}\Aether Playground"; Filename: "{app}\python\pythonw.exe"; Parameters: "-I -B ""{app}\tools\aether_teacher\packaging\windows_entry.py"""; WorkingDir: "{app}"; Tasks: desktopicon

[Run]
Filename: "{app}\python\pythonw.exe"; Parameters: "-I -B ""{app}\tools\aether_teacher\packaging\windows_entry.py"""; WorkingDir: "{app}"; Description: "Open Aether Playground"; Flags: postinstall unchecked nowait skipifsilent

[Code]
function VcRuntimePresent: Boolean;
var
  Installed, Major, Minor, Build: Cardinal;
  Key: String;
begin
  Key := 'SOFTWARE\Microsoft\VisualStudio\14.0\VC\Runtimes\x64';
  Result := RegQueryDWordValue(HKLM64, Key, 'Installed', Installed) and (Installed = 1) and
    RegQueryDWordValue(HKLM64, Key, 'Major', Major) and
    RegQueryDWordValue(HKLM64, Key, 'Minor', Minor) and
    RegQueryDWordValue(HKLM64, Key, 'Bld', Build) and
    ((Major > 14) or ((Major = 14) and ((Minor > 51) or ((Minor = 51) and (Build >= 36247)))));
end;

function PrepareToInstall(var NeedsRestart: Boolean): String;
var
  ResultCode: Integer;
begin
  Result := '';
  if VcRuntimePresent then
    exit;
  ExtractTemporaryFile('vc_redist.x64.exe');
  if not ShellExec('runas', ExpandConstant('{tmp}\vc_redist.x64.exe'), '/install /passive /norestart', '',
      SW_SHOWNORMAL, ewWaitUntilTerminated, ResultCode) then
    Result := 'Allow the Microsoft Visual C++ x64 runtime installation, then run Aether Setup again.'
  else if ResultCode = 3010 then begin
    NeedsRestart := True;
    Result := 'Restart Windows to finish the Microsoft Visual C++ runtime installation, then run Aether Setup again.';
  end else if not VcRuntimePresent then
    Result := 'Microsoft Visual C++ x64 runtime setup did not finish (exit code ' + IntToStr(ResultCode) + ').';
end;
