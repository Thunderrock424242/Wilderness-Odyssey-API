# Aether Playground Online for Windows

This edition contains the native Aether Teacher app and a one-time runtime downloader. It installs separately from the offline edition and uses the same private Aether workspace. The models and training tools are rebuilt to match the approved offline edition.

## Before resetting Windows

1. Upload `Aether-Playground-Online-Windows.zip` and its optional `.sha256` file to Google Drive or another drive.
2. In your current Aether Teacher, open **Advanced → Backup & restore → Create Backup**. Save that private ZIP separately. It contains your profiles, lessons, chats, theme, job history, and saved candidates. Keep its Drive sharing set to **Restricted**.
3. Copy any exports you saved outside Aether's workspace separately. Confirm both ZIPs can be downloaded before you wipe Windows.

## Install after the reset

1. On fully updated Windows 11 x64 or Windows 10 22H2 x64, extract the ZIP and run `Aether-Playground-Online-Setup-x64.exe`. Allow the included Microsoft Visual C++ runtime setup if prompted. This is an unsigned local build. Verify the release checksum after downloading it from Drive.
2. Leave **Download and prepare the Aether runtime** selected when Setup finishes. A console shows download and preparation progress. The one-time setup needs internet access, transfers about 15 GB, and checks for at least 60 GiB of free disk space at the start. It verifies the downloads and the final Qwen model hashes. If interrupted, open **Download Aether Runtime** from Start. Verified files are reused, and the interrupted file is fetched again.
3. When setup reports completion, open **Aether Playground Online** from Start. Chat and training run locally after setup. For NVIDIA acceleration and QLoRA training, install the supported NVIDIA driver for your GPU. The app still checks the selected training preset's disk, RAM, and VRAM requirements.
4. To restore, open **Advanced → Backup & restore → Import Backup** and select your private ZIP. Confirm and reopen Aether when prompted. Aether preserves the previous workspace as a recovery copy and keeps the runtime paths for this installation.

The default installation is `%LOCALAPPDATA%\Programs\Aether Playground Online`. Your profiles, lessons, chats, settings, and training artifacts are stored separately in `%LOCALAPPDATA%\WildernessOdyssey\AetherTeacher`. Backup import leaves a sibling `AetherTeacher-before-import-*` recovery folder. If another edition of Aether is open, close it before applying setup or restoring a backup.

The largest model shard is 5.3 GB. Each file has a 30-minute download deadline.

If you return to the earlier offline installer on the same Windows account, close Aether and move `runtime\qwen35-models\manifests\registry.ollama.ai\library\qwen3.5\4b` out of the workspace. That file is the stock chat manifest and Aether recreates it on launch. Run the offline guide's `--configure` command to rebind to the offline installation before reopening Aether.

The `developer-tools` folder contains optional signed standalone Python and Node.js/npm installers. For a trained candidate, set the destination under **Advanced → API handoff**, export from **Versions**, and follow your API host's reviewed import and activation process. The stock downloads need no account credentials.
