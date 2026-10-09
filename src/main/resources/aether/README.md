# Fill in your Aether hosting configuration here

From the repository root, run this once (rerunning it preserves your edits):

```powershell
.\deploy\operator\aether.ps1 -Action Init
```

Then edit the three local files in this folder:

| File | What to fill in | Used by |
| --- | --- | --- |
| `aether-connection.local.json` | HTTPS gateway URL, Kinetic SFTP host/port/username, optional exact panel restart contract, and local JDK/runtime/model paths | The PowerShell terminal tool on your PC |
| `aether-server.yml` | Gateway port, private Ollama endpoint/model, request limits, matching Minecraft server ID, and verified activation settings | The standalone Aether server; `Prepare` stages your edited file for upload |
| `aether-prompts.yml` | Aether personality, specialist behavior and authoritative lore | The standalone Aether server; `Prepare` and `PrepareBehavior` stage your edited behavior |

`Init` copies an existing legacy connection config from `deploy/operator/aether-connection.local.json` and existing behavior from `.codex-build/aether-operator/behavior/aether-prompts.yml` when the new local files are absent. It leaves the originals and any existing new files intact. Otherwise it uses the examples and the existing server-owned prompt canon.

The local files are ignored by Git, and **this entire folder is excluded from the Minecraft mod JAR**. Keep a private backup of your filled-in files. The tracked `.example` files contain defaults for new installations. Credentials go in environment variables, not these files; the JSON/YAML fields name those variables. Leave unknown host details blank until you have them.

## Fields you need from Kinetic

In `aether-connection.local.json`, fill in:

- `gatewayUrl`: the approved HTTPS URL for Aether. A game-server IP/port does not supply this URL.
- `sftp.host`, `sftp.port`, `sftp.user`: copy exactly from the **Aether instance's** SFTP settings. Leave the password out; SFTP asks for it interactively.
- `panel.restartPath`, `panel.restartBody`: optional; copy the exact API contract from the signed-in panel documentation when available. Until then, restart through the hosting panel.
- `minecraftServerId`: match `security.minecraft_server_id` in `aether-server.yml` and `ai_backend.server_id` in the Minecraft config.

The JDK and local bundle-input paths are for your operator PC. An existing legacy config may already provide these. The Linux platform and model defaults are candidates to verify against the actual host, rather than proof that it supports them.

In `aether-server.yml`, keep the gateway bind on loopback, use a verified private Ollama address, and leave the activation flags false until commissioning. The default Ollama port `11435` is for the standalone bundled runtime; separately managed Ollama may use another private port.

## Check and prepare your filled-in settings

```powershell
.\deploy\operator\aether.ps1 -Action Check
.\deploy\operator\aether.ps1 -Action Prepare
```

`Check` validates the local YAML and prompts when the gateway validator is built. `Prepare` stages your edited server configuration and behavior with the gateway and regular mod JAR. It requires the existing build outputs; add `-BundleJar <path>` when preparing a full standalone bundle. Neither command uploads, starts a service, or enables the host. Follow [the terminal operations guide](../../../../deploy/operator/README.md) for SFTP staging and publication.

The Minecraft-side defaults remain in [`../ai_config.yaml`](../ai_config.yaml). On an existing Minecraft installation, merge the backend URL/server-ID settings into its existing `config/ai_config.yaml`; replacing a JAR does not overwrite that live config. Preserve its lore and other settings.

Voice uses a separate client config, `config/wildernessodysseyapi/wildernessodysseyapi-client.toml`, and the optional local speech service. This folder does not enable multiplayer voice; see [local voice](../../../../docs/ai/local-voice.md).
