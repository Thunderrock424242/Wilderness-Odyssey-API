# Aether Playground for Windows

This is the desktop app whose window is titled **Aether Teacher**. The installer includes its native Qt interface, Python 3.12.10, the CUDA 12.8 training environment, Ollama 0.17.7, the approved stock Qwen 3.5 4B chat and training weights, and the matching conversion tools. It can install and set up the stock model offline. The setup wizard checks for the x64 Microsoft Visual C++ runtime and runs the included Microsoft installer if needed.

## Before reinstalling Windows

1. Upload `release/Aether-Playground-Windows.zip` to Google Drive or another drive. You can also copy the complete installer folder. Keep the `.exe` and **every** `.bin` file together. `SHA256SUMS.txt` lists the hashes for checking your downloaded copy.
2. When Aether is idle, open **Advanced → Backup & restore → Create Backup**. Save the ZIP somewhere outside the Aether workspace. Keep its Drive sharing set to **Restricted**. This archive holds profiles, lessons, chats, appearance, export-folder preference, jobs, and trained candidates. Aether rebuilds the bundled stock-model cache and uses the new installation's runtime paths when you restore.
3. Save any exports you put outside the workspace separately. Confirm the installer ZIP and private workspace backup have finished uploading and that you can access both before wiping Windows.

### Manual backup for an older version

Let training finish and close Aether. Press **Win+R**, enter `%LOCALAPPDATA%\WildernessOdyssey`, and locate the `AetherTeacher` folder. Copy its contents, including `workspace.sqlite3` and any companion SQLite files, `settings.json`, `jobs`, `engine-state`, and `runtime\qwen35-models`. Aether recreates `workspace.lock` and `runtime\home`. The latter can contain Ollama's private identity keys, so leave it out.

To make a smaller backup for this release, use the PowerShell commands below. They skip the exact 2.7 GB stock Qwen cache file that the installer already contains. Aether verifies and recreates that file after restore. The copy keeps trained candidate files and every other model blob. Change `E:\Aether-Private-Backup` to an empty destination outside the workspace and upload the resulting folder privately.

```powershell
$workspace = Join-Path $env:LOCALAPPDATA 'WildernessOdyssey\AetherTeacher'
$backup = 'E:\Aether-Private-Backup'
$stockBlob = 'sha256-c74846553aea7923b4b2ade3394ed2ddd7a6bbdf56058247f727db6a3b13d38c'
if (!(Test-Path -LiteralPath (Join-Path $workspace 'workspace.sqlite3'))) { throw 'Aether workspace not found.' }
robocopy $workspace $backup /E /XJ /R:2 /W:2 /XD (Join-Path $workspace 'runtime\home') /XF workspace.lock native-smoke.json native-smoke.png startup-error.json $stockBlob
if ($LASTEXITCODE -ge 8) { throw 'The Aether backup copy failed. Check the robocopy output.' }
```

| Location | Contents |
| --- | --- |
| `%LOCALAPPDATA%\WildernessOdyssey\AetherTeacher\workspace.sqlite3` | Role and personality profiles, reviewed and draft lessons, chats, light/dark preference, export folder, job history, and selected local model. |
| `settings.json` and `installed-base-approval.json` in the workspace | Local runtime paths and the stock model identity. Paths are regenerated when you rebind on the new installation. |
| `jobs`, `engine-state`, and `runtime\qwen35-models` in the workspace | Private training inputs and artifacts, candidate state, and the local model store. |
| `%LOCALAPPDATA%\Programs\Aether Playground` by default | Installed application, software dependencies, and stock Qwen files. This folder is recreated by Setup. |

## Install and restore

1. Use fully updated Windows 11 x64 or Windows 10 22H2 x64. Download the installer ZIP and extract it completely to a local drive. Leave at least 30 GB free for the app and its first startup, plus room for the ZIP, its extracted files, and your workspace. Aether's training admission check requires at least 60 GiB free in the workspace at run time.
2. Double-click `Aether-Playground-Setup-x64.exe`. Keep the numbered `.bin` files beside it throughout installation. Approve the Microsoft Visual C++ x64 runtime setup if prompted. This personal build is unsigned, so verify its SHA-256 against your saved copy if Windows shows an unknown publisher. Setup adds **Aether Playground** to Start.
3. Open **Aether Playground** and let local setup finish. Go to **Advanced → Backup & restore → Import Backup**, select your private backup ZIP, and confirm. Aether checks the archive and closes. Reopen the app to finish importing. The workspace it replaced is kept in a sibling `AetherTeacher-before-import-*` folder under `%LOCALAPPDATA%\WildernessOdyssey`.
4. Confirm your profile, lessons, chat history, and appearance. Open **Advanced** to check runtime readiness, then send a test message. Review any restored local candidate under **Versions**.

If you manually configured runtime assets inside the replaced workspace, find them in the recovery folder and select their new paths in **Local setup**. The bundled installer keeps its runtime assets in the installation folder.

### Restore a manual folder backup

Close Aether. If a new blank workspace already exists, rename that folder first. Copy your backed-up folder contents into `%LOCALAPPDATA%\WildernessOdyssey\AetherTeacher`, with `workspace.sqlite3` at its top level. Run the following in PowerShell to connect the restored workspace to the new installation. Use your chosen installation folder if you changed it in Setup.

   ```powershell
   $app = Join-Path $env:LOCALAPPDATA 'Programs\Aether Playground'
   & (Join-Path $app 'python\python.exe') -I -B (Join-Path $app 'tools\aether_teacher\packaging\windows_entry.py') --configure
   if ($LASTEXITCODE) { throw 'Aether could not rebind the restored workspace.' }
   ```

Open **Aether Playground** from Start. Give it time to verify the stock base and copy its chat model into the workspace on first launch.

The stock runtime uses the private loopback port `127.0.0.1:11435`. For NVIDIA acceleration and QLoRA training, install the current supported NVIDIA driver for your GPU after Windows setup. The bundled Torch CUDA runtime still needs the system's NVIDIA driver. The small preset checks for about 11,000 MiB of free GPU memory. The tested laptop has an RTX 4090 Laptop GPU with 16 GiB VRAM. Use at least 32 GiB of system RAM for training. CPU inference is available through Ollama and will be slower. The desktop release uses local files and has no server, Discord, or Hugging Face credentials to restore.

## Install Python and npm for your other tools

Run the signed `developer-tools\python-3.12.10-amd64.exe` if you need Python at a terminal. Choose **Add python.exe to PATH** during setup. Run `developer-tools\node-v24.22.0-x64.msi` to install Node.js 24 LTS and npm. Keep the **npm package manager** feature selected. Open a new terminal and check `python --version`, `node --version`, and `npm.cmd --version`. Aether uses its own Python and Ollama regardless of these separate installs.

## Send a trained candidate to your API

1. In Aether, open **Advanced → API handoff** and choose a local export folder. Choose it again after the reset if the old path changed. The folder is saved in the workspace and opens by default when you export from **Versions**.
2. In **Versions**, select a verified candidate and export the handoff. Keep the entire export folder together. It contains the model, `profile.json`, and the verification manifest.
3. Transfer the export to private storage on the inference host using your existing access method. Verify the manifests after transfer. From the export's `model` directory, use `ollama create aether-candidate:aether-v1 -f Modelfile` with a distinct tag for your candidate. Test the candidate on the host before changing the API's active model. Review the accompanying profile separately as operator configuration.
4. Configure the gateway's Ollama URL and model tag on the API host during your reviewed rollout. Restore gateway credentials separately through your existing private configuration. Follow the repository's [private server configuration guide](https://github.com/Thunderrock424242/Wilderness-Odyssey-API/blob/in-dev/docs/ai/aether-playground-server.md#private-configuration).

## Check the files after downloading

In PowerShell, change to the downloaded release folder and run:

```powershell
Get-Content .\SHA256SUMS.txt | ForEach-Object {
    $expected, $file = $_ -split '  ', 2
    if ((Get-FileHash -LiteralPath $file -Algorithm SHA256).Hash -ne $expected) { throw "Hash mismatch for $file" }
    Write-Host "Verified $file"
}
```

The app and runtime licenses ship with the installation. Qt and Shiboken are included as replaceable shared libraries under their [upstream terms](https://doc.qt.io/qtforpython-6/licenses.html). Qwen's license and the prepared stock model's provenance are in `models\base`. The training packages include their distribution metadata and license files. Windows can remove Aether Playground through **Settings → Apps → Installed apps**. Its private workspace remains in the data folder listed above.
