# Aether terminal operations for Kinetic

Use PowerShell 7 on your PC. The toolkit covers the standalone Aether JAR and the Wilderness Odyssey API mod. Host access, native Linux execution, actual model capacity, HTTPS routing and live Minecraft acceptance remain separate checks. `Check` never treats a built JAR as proof of a running host.

Kinetic documents [SFTP access](https://www.kinetichosting.com/articles/kinetic-panel/basics/how-to-connect-and-use-sftp) and [API keys/server actions](https://www.kinetichosting.com/for-developers). Its endpoint documentation is [inside the signed-in panel](https://kineticpanel.net/documentation). No API route is guessed by this toolkit. API keys and gateway credentials use environment references; your panel password stays in the interactive SFTP login.

## Configure the operator PC

From the repository root:

```powershell
.\deploy\operator\aether.ps1 -Action Init
.\deploy\operator\aether.ps1 -Action Check
```

`Init` creates your editable settings in [`src/main/resources/aether/`](../../src/main/resources/aether/README.md): `aether-connection.local.json`, `aether-server.yml`, and `aether-prompts.yml`. These local files are ignored by Git, and the entire folder is excluded from the mod JAR. Existing edits are preserved; an existing legacy connection config and behavior file are copied into the new locations only when the new files are absent. Keep a private backup. The tracked prompt canon remains `aether-server/src/main/resources/aether-prompts.yml`; only edit it when the change belongs in the repository release. Use `-ConnectionConfig`, `-ServerConfig` or `-PromptsPath` to select another local file explicitly.

Fill in the connection file when you know the host details:

| Field | Value to obtain |
| --- | --- |
| `gatewayUrl` | The approved HTTPS gateway address; leave blank until configured. Loopback HTTP works only when the calling process shares the gateway's network namespace. |
| `minecraftServerId` | Match the gateway's `security.minecraft_server_id` and Minecraft's `ai_backend.server_id`. |
| `javaHome` | Your JDK 21 installation; otherwise the tool uses `JAVA_HOME`/`java`. |
| `platform` | The actual host OS/architecture. The example's Linux amd64 value is a packaging candidate, not host detection. |
| `runtimeDirectory`, `modelDirectory` | Explicit local inputs for a full bundle rebuild. No automatic download occurs. |
| `sftp.host`, `sftp.port`, `sftp.user` | Exactly what the instance's SFTP page shows. |
| `sftp.identityFile` | Optional provider-supported SSH key. Leave blank for interactive password authentication. |
| `sftp.releaseDirectory`, `sftp.dataDirectory` | Relative staged-release directory and the existing application data directory. Initial examples use `aether-releases` and `aether`. |
| `panel.restartPath`, `panel.restartBody` | Exact POST path/body for the **Aether instance**, copied from the in-panel API docs. Leave blank until verified. |

The panel API token stays in `KINETIC_API_TOKEN`. The monitoring and inference tokens stay in their separately named environment variables. For example, load an existing token without displaying it:

```powershell
$env:AETHER_MONITORING_TOKEN = [Net.NetworkCredential]::new('', (Read-Host 'Monitoring token' -AsSecureString)).Password
$env:AETHER_INFERENCE_TOKEN = [Net.NetworkCredential]::new('', (Read-Host 'Inference token' -AsSecureString)).Password
```

These terminal values do not configure the hosted processes. Provision the matching values through the host's supported secret/environment controls. Minecraft needs only the inference token. Preserve the separate monitoring/admin identities.

## Build and prepare the matching software

```powershell
.\deploy\operator\aether.ps1 -Action Build
.\deploy\operator\aether.ps1 -Action Bundle
.\deploy\operator\aether.ps1 -Action Prepare -BundleJar .\aether-server\.codex-build\libs\Aether-AI-Server-linux-amd64.jar
```

`Build` runs the gateway tests, standalone boundary check, focused Minecraft AI tests and regular mod packaging in one sequential Gradle invocation. `Bundle` uses the checked-in Gradle task with the explicit runtime/model inputs. It does not install Ollama, pull models, launch Minecraft or enable inference. Run one repository Gradle/development process at a time, including processes started outside this toolkit.

`Prepare` creates a new candidate directory, stages your edited `src/main/resources/aether/aether-server.yml` and lore/specialist prompts, validates both the local and staged YAML, includes the current regular mod JAR, and records SHA-256 hashes in `release.json`. Initial settings keep activation flags false; preparation preserves your configuration and does not activate a host. Without `BundleJar`, the candidate includes a gateway-only JAR, requiring independently managed private Ollama. The bundled template points to private port 11435; change it to the external runtime's private endpoint for gateway-only deployment. Keep `prompts_file: "aether-prompts.yml"` so the staged behavior travels beside the config.

The files have different installation destinations:

- The platform-specific Aether JAR is the standalone application's startup JAR.
- `aether/aether-server.yml` and `aether/aether-prompts.yml` belong in that application's persistent data directory, with operator configuration merged/preserved on upgrades.
- `minecraft/wildernessodysseyapi-<version>.jar` is installed in the Minecraft server's `mods/` directory during its own maintenance window. The packaged mod reflects the current checkout, including any other local source changes.

Do not select Aether as the startup JAR of an existing Minecraft instance that must continue running Minecraft. Aether needs its own supported process/instance. Separate containers generally have separate loopback namespaces; sharing a provider or machine does not establish local connectivity.

The bundle's normal startup is `java -jar Aether-AI-Server-linux-amd64.jar`. Java 21 and compatible native libraries are required. First extraction and subsequent integrity checks can take time. Preserve `aether/` when upgrading. `--setup-only` extracts without inference, where the hosting startup configuration permits program arguments. The current default bundle stops before model launch until hosting/capacity/activation are explicitly configured.

## Stage a release with SFTP

Use the candidate path printed by `Prepare` or `PrepareBehavior`:

```powershell
.\deploy\operator\aether.ps1 -Action UploadPlan -ReleaseDirectory .\.codex-build\aether-operator\releases\CANDIDATE_ID
```

The tool verifies local hashes and writes/prints an SFTP plan. It uploads under a **new release directory** and writes its manifest last. Creation of an already-existing release directory must fail; never continue that plan after a failure. SFTP does not verify the remote content hash for you: for a release intended to become active, download the staged files back to a different local directory and compare `Get-FileHash -Algorithm SHA256` with `release.json` before publication. Do not reuse an active release ID.

For password-based SFTP, run the printed `sftp -P ...` connection command, authenticate in its prompt, and paste the commands. No password goes into a script or process argument. With a provider-supported key, `-ExecuteSftp` runs the batch directly and fails on a failed transfer. Host-key checking stays enabled; verify a new host fingerprint through a trusted provider channel.

Initial installation and JAR/config changes are separate publication steps: stop the intended Aether process, preserve existing configuration/state, choose the verified startup JAR, and restart through the panel. The toolkit does not overwrite the live server config or automatically replace the Minecraft mod.

## Change AI behavior after deployment

The gateway owns the personality, lore and specialist profiles. The base model is configured by prompts; editing these files is not model training. Edit the local `aether-prompts.yml`, then:

```powershell
.\deploy\operator\aether.ps1 -Action PrepareBehavior
.\deploy\operator\aether.ps1 -Action UploadPlan -ReleaseDirectory .\.codex-build\aether-operator\releases\BEHAVIOR_ID
```

Verify the staged remote file's hash, then stop **only the Aether instance** in the panel. With the initial external prompt file installed and `prompts_file: "aether-prompts.yml"` configured:

```powershell
.\deploy\operator\aether.ps1 -Action PromoteBehaviorPlan -ReleaseDirectory .\.codex-build\aether-operator\releases\BEHAVIOR_ID -ServerStopped
```

Run/paste that plan. It first renames the old behavior file to a uniquely named previous file, then moves the candidate into place. It never modifies model weights, the admission journal, player profiles or Minecraft worlds. `ServerStopped` is your explicit assertion; the tool cannot establish panel process state without a verified provider API. Keep the server stopped if either rename fails. Restore the previous file before restarting if publication fails.

Start Aether through the panel. Once the exact API restart contract is configured, the terminal can request that same operation with:

```powershell
$env:KINETIC_API_TOKEN = [Net.NetworkCredential]::new('', (Read-Host 'Panel API token' -AsSecureString)).Password
.\deploy\operator\aether.ps1 -Action Restart
.\deploy\operator\aether.ps1 -Action Probe
.\deploy\operator\aether.ps1 -Action Generate
```

Requests are bounded, do not follow redirects, and accept HTTPS for remote credentials. `Probe` shows anonymous health, monitoring health/readiness, and inference status. Expected anonymous health is 401; monitoring health is 200. Locked configuration has readiness 503 and `ACTIVATION_REQUIRED`. Activated, unpaused model availability has readiness 200 and `available: true`. `Generate` deliberately sends one synthetic prompt, checks its response ID/nonempty answer, and reports elapsed time. Run it twice to compare cold/warm behavior.

For rollback, stop Aether and rename the current behavior to a new failed-candidate filename, then rename the saved `aether-prompts.previous-<ID>.yml` to `aether-prompts.yml`. Restart and repeat probes/generation. Do not delete the durable journal to resolve a pause or readiness error. Archive old operator backups deliberately rather than letting them grow without review.

Some basic conversational instructions and the factual verifier are implemented in `PromptCatalog.java`; changes to those safeguards require code review, tests and a rebuilt application. Gateway limits/model parameters live in `aether-server.yml` and are read at startup. Changing actual weights/runtime or the active model requires a new bundle/runtime preparation, compatibility/capacity checks and renewed activation for that model. Clear `capacity_verified`, `inference_enabled` and `verified_model` before a model change. The Phase 1 HTTP API permits durable pause/resume only; it cannot download models, change models or execute shell commands.

## Complete the host and Minecraft acceptance

Before production activation, record evidence for each item:

1. Kinetic permits this custom application, native child process and appropriate Java 21 environment. Verify actual OS/CPU architecture and compatible libraries on the intended instance.
2. The instance has room for the bundle plus extracted runtime/model/state. Verify actual spare RAM/VRAM and cold/warm generation under representative Minecraft load; the previously recorded 8 GB allocation does not establish 8B suitability.
3. Private model connectivity and gateway loopback are reachable by an approved connector. Configure and test the final HTTPS hostname; an allocated game port is insufficient evidence. Keep raw Ollama and gateway origin ports private.
4. Credentials are present in the actual hosted process; anonymous/wrong-scope tests fail as expected. Saved config, behavior and state survive a supervised restart.
5. After qualifying and explicitly enabling the exact model, authenticated synthetic generation succeeds reliably within the configured deadline. A health response alone is insufficient.
6. Merge the backend section from [AETHER_SETUP.md](../../AETHER_SETUP.md#8-connect-minecraft) into the existing Minecraft config, with matching server ID, URL and inference credential. Preserve lore, profiles and other settings.
7. In a test world, addressed chat returns a real verified answer. Check specialist routing, player history isolation, and controlled outage/recovery without blocking Minecraft. Test voice separately if used.

The local test commands are:

```powershell
.\deploy\operator\Test-AetherOperator.ps1
.\deploy\operator\aether.ps1 -Action Build
```

Record remaining host checks as unverified until observed. [AETHER_SETUP.md](../../AETHER_SETUP.md) remains the commissioning walkthrough, and [the backend reference](../../docs/ai/aether-backend.md) describes Minecraft ownership and compatibility.
