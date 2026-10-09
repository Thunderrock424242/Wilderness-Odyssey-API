# Aether service setup and verification

**Start here.** This is the operator walkthrough for the current protected Aether service. You do not need to read the design and historical verification documents to follow it.

For repeatable PowerShell preparation, SFTP staging, behavior updates, rollback and connection checks, see [Aether terminal operations for Kinetic](deploy/operator/README.md). Its ignored connection config can be filled in when the host details are available. Offline validation commands are `--validate-config <file>` and `--validate-prompts <file>`; relative `prompts_file` paths resolve beside the gateway configuration.

To fill in the settings from the resources area, run `.\deploy\operator\aether.ps1 -Action Init`, then edit the local connection, server and behavior files in [src/main/resources/aether/](src/main/resources/aether/README.md). The terminal tool uses those files by default. They are ignored by Git and excluded from the Minecraft mod JAR.

Use the **gateway-only JAR with separately managed Ollama** for this walkthrough. The Linux native bundle was rebuilt with the current protections on October 9; see [the current readiness record](docs/ai/aether-readiness-2026-10-09.md) for its artifact identity and unverified host checks. Older bundles, including the September Windows package, predate those protections.

There are two milestones:

1. **Protected service works:** Java starts, credentials work, and inference is correctly locked. No model needs to run for this check.
2. **Aether answers through Minecraft:** after host/model approval, real inference succeeds through the gateway and then through the mod.

Jump to: [files](#2-obtain-the-current-files) · [settings](#3-create-locked-gateway-settings) · [startup](#4-supply-the-credentials-and-start-the-locked-gateway) · [connection checks](#5-prove-authentication-and-the-activation-lock-work) · [model test](#7-activate-only-the-verified-model-then-request-one-answer) · [Minecraft](#8-connect-minecraft) · [troubleshooting](#troubleshooting).

For the official Kinetic host, **500% CPU, 8 GB RAM and an unknown GPU are not yet a capacity approval**. Do not assume `aether-custom:8b` fits alongside Minecraft. Production deployment, activation, live restarts, model changes and remote administration still require the owner's separate approval. The commands below are instructions to run at the appropriate milestone; creating this guide does not perform or approve those actions.

## 1. Know which pieces you need

```text
Minecraft server -- inference credential --> Aether Gateway --> private Ollama

Website browser --> Kinetic admin backend --> protected gateway administration
                    (later integration)
```

| Piece | Where it runs | What you need |
| --- | --- | --- |
| Aether Gateway | Its own Java process | Java 21, `Aether-Gateway.jar`, a settings file, persistent writable state |
| Ollama | Same host initially, or a verified private host | A separately installed runtime and an explicitly selected local model |
| Minecraft | Your modded server | Current regular Wilderness Odyssey API mod JAR, backend settings, inference credential |
| Monitoring | Trusted operator terminal/backend | A different monitoring credential |

The gateway JAR is **not a Minecraft mod**: do not put it in `mods/`. The regular `wildernessodysseyapi-<version>.jar` is the mod; do not install a `-sources.jar`.

The simplest arrangement puts the gateway and Ollama on the same machine. Gateway port: **8085**. Private Ollama port: **11434**. These are separate from the Minecraft game port. `127.0.0.1` always means the machine or container running the command—not your remote host.

Before planning the Kinetic deployment, confirm that its plan allows a second Java/native process or suitable container, a connector that can reach gateway loopback, persistent writable storage, and enough RAM remaining after Minecraft. Record the actual CPU/share and GPU/VRAM. If those are unavailable, use a separately approved AI host; allocating a Minecraft port alone does not supply this architecture.

## 2. Obtain the current files

From the repository root, build the gateway with JDK 21. Run only one repository Gradle process at a time:

```powershell
java -version
.\gradlew.bat :aether-server:build '-PcodexBuildDir=.codex-build' --no-parallel
```

Expected: Java 21 and `BUILD SUCCESSFUL`. Output:

```text
aether-server/.codex-build/libs/Aether-Gateway.jar
```

Use the regular Minecraft mod JAR from the same protected-gateway source/release. If you need to rebuild it, run this separately after the gateway build finishes:

```powershell
.\gradlew.bat jar '-PcodexBuildDir=.codex-build' --no-parallel
```

The mod is under `.codex-build/libs/`; its version comes from the checkout. A JAR build is not a live gameplay test.

Create a service directory outside the repository, for example `C:\Aether-Service` on Windows or `/srv/aether` on Linux, and copy **only the gateway JAR** there. The remaining examples use that directory as the working directory. Keep configuration and state when replacing a JAR.

## 3. Create locked gateway settings

Save the following as `aether-server.yml` beside the gateway JAR. This is a complete initial configuration. It intentionally starts without model activation.

```yaml
server:
  bind: "127.0.0.1"
  port: 8085
  request_timeout_seconds: 10
ollama:
  url: "http://127.0.0.1:11434"
  model: "MODEL_NOT_SELECTED"
  timeout_seconds: 30
  max_output_tokens: 256
limits:
  max_concurrent_generations: 1
  max_queue_size: 2
  requests_per_minute: 60
  max_request_bytes: 65536
  http_workers: 8
logging:
  log_requests: false
  log_player_messages: false
  log_responses: false
prompts_file: ""
security:
  minecraft_server_id: "wilderness-server"
  inference_token_env: "AETHER_INFERENCE_TOKEN"
  monitoring_token_env: "AETHER_MONITORING_TOKEN"
  administration_token_env: "AETHER_ADMINISTRATION_TOKEN"
  administration_enabled: false
  access_issuer: ""
  access_audience: ""
  monitoring_client_id: ""
  administration_client_id: ""
activation:
  hosting_verified: false
  capacity_verified: false
  inference_enabled: false
  verified_model: ""
```

`MODEL_NOT_SELECTED` is a placeholder, not a download target. One worker and a small queue make the initial test easier to observe; they do not establish that a model fits.

Leaving `state_directory` out places `aether-state/` next to this settings file. That directory must be writable by the gateway account and persistent across restarts. With a service manager, an explicit absolute state path is preferable. The generic Linux deployment example uses `/var/lib/aether`; do not blindly copy that path into a Windows setup.

Leave `prompts_file` empty to use the existing bundled Aether lore/personality. Never change the listener to `0.0.0.0`; the gateway rejects public bindings. Ollama URLs accept localhost or private/loopback IP literals, not arbitrary DNS names.

## 4. Supply the credentials and start the locked gateway

Generate and save **two different random secrets** in your password/secret manager: one for inference, one for monitoring. Use 64 random hexadecimal characters each. Set the exact names `AETHER_INFERENCE_TOKEN` and `AETHER_MONITORING_TOKEN` in the gateway process environment. Do not paste the values into YAML, command-line arguments, the public modpack, screenshots or chat.

An administration secret is not needed for this initial setup. Leave it unset and keep administration disabled. When administration is implemented and approved, it must have a third distinct secret plus its Cloudflare Access identity.

For a local Windows check, open **PowerShell 7**, enter the service directory, and load the saved secrets through masked prompts:

```powershell
Set-Location 'C:\Aether-Service'
$env:AETHER_INFERENCE_TOKEN = [System.Net.NetworkCredential]::new('', (Read-Host 'Inference secret' -AsSecureString)).Password
$env:AETHER_MONITORING_TOKEN = [System.Net.NetworkCredential]::new('', (Read-Host 'Monitoring secret' -AsSecureString)).Password
java -jar .\Aether-Gateway.jar --external --config .\aether-server.yml
```

For a Linux terminal, enter the corresponding directory and use:

```bash
cd /srv/aether
read -r -s -p 'Inference secret: ' AETHER_INFERENCE_TOKEN; printf '\n'
read -r -s -p 'Monitoring secret: ' AETHER_MONITORING_TOKEN; printf '\n'
export AETHER_INFERENCE_TOKEN AETHER_MONITORING_TOKEN
java -jar ./Aether-Gateway.jar --external --config ./aether-server.yml
```

For a hosting panel, use its secret/environment controls and the same Java startup arguments. Uploading the file does not automatically set process environment variables. Terminal assignments apply only to that shell and its child processes; they are not persistent panel/service configuration.

Expected startup message: **`Protected Aether gateway listening on port 8085`**. Leave this terminal running. `--external` means the gateway does not start or install Ollama. A missing model should not prevent this locked gateway check.

## 5. Prove authentication and the activation lock work

Open a **second PowerShell 7 terminal on the gateway machine**. Use the same two saved secrets, not newly generated values. The following helper prints only HTTP status and response body; it does not print the credential headers:

```powershell
$aetherBase = 'http://127.0.0.1:8085'
$env:AETHER_INFERENCE_TOKEN = [System.Net.NetworkCredential]::new('', (Read-Host 'Same inference secret' -AsSecureString)).Password
$env:AETHER_MONITORING_TOKEN = [System.Net.NetworkCredential]::new('', (Read-Host 'Same monitoring secret' -AsSecureString)).Password
$inferenceHeaders = @{ Authorization = "Bearer $env:AETHER_INFERENCE_TOKEN" }
$monitorHeaders = @{ Authorization = "Bearer $env:AETHER_MONITORING_TOKEN" }

function Test-AetherEndpoint {
    param([string]$Path, [hashtable]$Headers = @{})
    $result = Invoke-WebRequest -Uri "$aetherBase$Path" -Headers $Headers -TimeoutSec 10 -SkipHttpErrorCheck
    [pscustomobject]@{ HttpStatus = [int]$result.StatusCode; Body = $result.Content }
}

Test-AetherEndpoint '/health'
Test-AetherEndpoint '/health' $monitorHeaders
Test-AetherEndpoint '/health' $inferenceHeaders
Test-AetherEndpoint '/v1/aether/status' $inferenceHeaders
Test-AetherEndpoint '/ready' $monitorHeaders
Test-AetherEndpoint '/v1/admin/admission' $monitorHeaders
```

| Check | Expected before activation | Meaning |
| --- | --- | --- |
| `/health`, no credential | **401** | Anonymous access is blocked |
| `/health`, monitoring credential | **200**, `status: UP` | The gateway is alive; Ollama may still be down |
| `/health`, inference credential | **403** | An inference credential cannot monitor/administer |
| `/v1/aether/status`, inference credential | **200**, `available: false`, `ACTIVATION_REQUIRED` | Authentication works and the activation gate is closed |
| `/ready`, monitoring credential | **503**, `ready: false` | The service is correctly not ready for inference |
| `/v1/admin/admission`, monitoring credential | **200**, `healthy: true`, initially `paused: false`, `revision: 0` | Local state is readable; an existing installation may have a different saved revision/pause state |

**Milestone 1 passes when these results match.** A 200 health response alone does not prove that Aether can answer. A browser tab without credentials should fail; use the terminal checks, not a public website button.

On Linux without PowerShell 7, open a second Bash terminal on the gateway host. Load the same secrets with the masked prompts from step 4, then run these equivalent local checks. The header is passed over standard input rather than placing the token in curl's command-line arguments, using curl's documented [header-file option](https://curl.se/docs/manpage.html#-H):

```bash
curl --include --max-time 10 http://127.0.0.1:8085/health
printf 'Authorization: Bearer %s\n' "$AETHER_MONITORING_TOKEN" | curl --header @- --include --max-time 10 http://127.0.0.1:8085/health
printf 'Authorization: Bearer %s\n' "$AETHER_INFERENCE_TOKEN" | curl --header @- --include --max-time 10 http://127.0.0.1:8085/health
printf 'Authorization: Bearer %s\n' "$AETHER_INFERENCE_TOKEN" | curl --header @- --include --max-time 10 http://127.0.0.1:8085/v1/aether/status
printf 'Authorization: Bearer %s\n' "$AETHER_MONITORING_TOKEN" | curl --header @- --include --max-time 10 http://127.0.0.1:8085/ready
printf 'Authorization: Bearer %s\n' "$AETHER_MONITORING_TOKEN" | curl --header @- --include --max-time 10 http://127.0.0.1:8085/v1/admin/admission
```

You can use the PowerShell checks from your Windows PC through the approved HTTPS connector later by setting `$aetherBase` to that gateway URL. Do not forward the raw gateway port just to run a probe.

## 6. Prepare and qualify the private model

Keep the gateway activation flags false during preparation. Installing/starting a model on the production host or changing its active model requires the separate approval already agreed. Capacity qualification should first happen in an approved isolated test environment; do not mark capacity verified merely to get past an error.

1. Install the host-appropriate runtime from [Ollama's official download page](https://ollama.com/download), or use an existing approved installation.
2. Keep its listener on `127.0.0.1:11434` for the same-host arrangement. Configure `OLLAMA_HOST=127.0.0.1:11434` and `OLLAMA_NO_CLOUD=1` in the **Ollama process**, not only the gateway process. Existing Ollama processes need an approved restart to adopt changes. These controls are described in the [official Ollama FAQ](https://docs.ollama.com/faq).
3. Select a model based on actual spare RAM/VRAM and measured performance. Have the operator install that specific approved model locally. If a download is needed, the explicit operator command is `ollama pull <approved-local-model-tag>`; replace the placeholder and do not run it for an unreviewed model. The gateway cannot download a model for you. See the [Ollama CLI reference](https://docs.ollama.com/cli).
4. On the Ollama machine, run `ollama ls` to confirm the exact installed model tag. For a manually managed fresh test instance, `ollama serve` starts the service; do not start a second instance if one already owns the port.
5. Check `GET http://127.0.0.1:11434/api/tags` **locally**, or from the approved private gateway host. It should list the selected model. This is the [model inventory endpoint](https://docs.ollama.com/api/tags), not an inference test.
6. In the approved test environment, exercise the selected model and record cold/warm latency, total memory, CPU/GPU use and Minecraft tick health under representative load. `ollama ps` shows loaded-model placement; its processor column distinguishes CPU/GPU usage. See the [Ollama FAQ](https://docs.ollama.com/faq#how-can-i-tell-if-my-model-was-loaded-onto-the-gpu).

The gateway adds Aether's existing prompt catalog; an arbitrary model called `aether-custom:8b` is not installed automatically. Select the exact approved local tag, and leave the prompt catalog intact. Compatibility still needs the gateway test below, including its factual-verification pass.

## 7. Activate only the verified model, then request one answer

**Approval checkpoint:** hosting capability, the selected model's capacity, and the intended deployment/activation must be approved. On the current shared Kinetic plan these remain open checks. Stop here for production until they are resolved.

In `aether-server.yml`, replace `MODEL_NOT_SELECTED` with the approved installed tag. Then replace the existing activation section with:

```yaml
activation:
  hosting_verified: true
  capacity_verified: true
  inference_enabled: true
  verified_model: "EXACT_SAME_TAG_AS_ollama.model"
```

The value must match `ollama.model` exactly. Keep `administration_enabled: false`. Apply the file through an approved gateway restart; configuration is read at startup. Do not run a second gateway against the same state directory. The gateway-only terminal stops with Ctrl+C; a hosted service uses its service manager.

Repeat the step 5 probes. Give the dependency cache about ten seconds to refresh. Expected: `/ready` returns **200**, and `/v1/aether/status` returns `available: true`. If `paused` is true, preserve that state and resolve it through the approved administration workflow; do not delete the journal to make it ready.

In the second PowerShell terminal from step 5, submit one synthetic setup request:

```powershell
$aetherRequestId = [guid]::NewGuid().ToString()
$aetherRequest = @{
    requestId = $aetherRequestId
    serverId = 'wilderness-server'
    world = 'minecraft:overworld'
    playerId = [guid]::NewGuid().ToString()
    playerName = 'SetupCheck'
    speaker = 'Aether'
    message = 'Aether, introduce yourself briefly.'
} | ConvertTo-Json

$aetherTimer = [System.Diagnostics.Stopwatch]::StartNew()
$aetherReply = Invoke-WebRequest -Uri "$aetherBase/v1/aether/generate" -Method Post -Headers $inferenceHeaders -ContentType 'application/json' -Body $aetherRequest -TimeoutSec 40 -SkipHttpErrorCheck
$aetherTimer.Stop()
[pscustomobject]@{ HttpStatus = [int]$aetherReply.StatusCode; ElapsedSeconds = $aetherTimer.Elapsed.TotalSeconds; Body = $aetherReply.Content }
```

Expected: **HTTP 200**, `success: true`, matching `requestId`, `speaker: Aether`, and a nonempty `response`. The returned model should be your approved tag. A health response or an Ollama inventory listing cannot substitute for this result.

For the same check from your Linux Bash terminal, the following uses fixed synthetic setup IDs and the already-loaded inference secret:

```bash
printf 'Authorization: Bearer %s\n' "$AETHER_INFERENCE_TOKEN" | curl --header @- \
  --header 'Content-Type: application/json' --include --max-time 40 \
  --write-out '\nElapsed seconds: %{time_total}\n' \
  --data-binary '{"requestId":"00000000-0000-0000-0000-000000000001","serverId":"wilderness-server","world":"minecraft:overworld","playerId":"00000000-0000-0000-0000-000000000002","playerName":"SetupCheck","speaker":"Aether","message":"Aether, introduce yourself briefly."}' \
  http://127.0.0.1:8085/v1/aether/generate
```

Run this small test again to compare cold and warm behavior. The default gateway deadline is 30 seconds for **queue wait, drafting and verification together**. The 40-second terminal timeout only allows the test tool to receive that result; it does not increase the model deadline. If the model cannot meet it reliably with the intended server load, return to capacity/model selection instead of declaring setup complete.

## 8. Connect Minecraft

Install the current regular mod JAR on the appropriate Minecraft installation. Merge the following section into the server's existing `config/ai_config.yaml`; **do not replace its lore, personality, memory or other settings**:

```yaml
ai_backend:
  enabled: true
  mode: "remote"
  server_id: "wilderness-server"
  inference_token_env: "AETHER_INFERENCE_TOKEN"
  circuit_cooldown_seconds: 30
  max_concurrent_requests: 1
  send_player_memory: false
  remote:
    base_url: "http://127.0.0.1:8085"
    timeout_seconds: 30
    retry_attempts: 1
    retry_backoff_millis: 250
  local_dev:
    enabled: false
    base_url: "http://127.0.0.1:8085"
```

Use that loopback URL only when Minecraft and the gateway share the same reachable network namespace. For a different machine/container, use the approved gateway **HTTPS** address instead. Never use the Ollama address as Minecraft's backend URL.

Set **only `AETHER_INFERENCE_TOKEN`** in the Minecraft server process to the same inference secret used by the gateway. The server ID must match `security.minecraft_server_id`. Keep monitoring/admin secrets out of Minecraft and out of player installations. In single-player, the integrated server lives in the client process; do not distribute the official server's secret to players or a public modpack.

After the approved Minecraft restart, join a test world and send addressed chat such as **`Aether, introduce yourself briefly.`** Confirm a model answer, not a scripted unavailable reply. Existing onboarding can run first on a fresh player; finish that flow before evaluating the normal model conversation.

In a non-production test session, stop just the gateway and confirm Minecraft stays responsive and offers its existing offline fallback. Start the gateway again and allow the configured 30-second cooldown before retrying. Recheck the local HTTP probes if recovery fails. Do not perform this outage exercise on the live server without approval.

**Milestone 2 passes when the authenticated generation request succeeds, Minecraft receives a real reply, and the controlled outage/recovery behaves correctly.** Record the model tag, host resources, cold/warm response times and observed gameplay impact. This evidence is needed before wider player access.

## 9. Keep it running and know what is not set up yet

- Use the panel's supported process supervision or the reviewed [systemd template](deploy/systemd/aether-ai.service) after deployment approval. A foreground terminal is only a manual test runner. The unit expects a service account, JAR/config paths, protected credentials environment file and `/var/lib/aether` state as described in the [deployment reference](deploy/README.md).
- Keep `aether-state/` backed up with the configuration. It holds pause state and audit records. Never clear it as a routine fix. Secrets belong in the operator's secret store and are not included in public backups or issue reports.
- A remote Minecraft connection needs a local connector terminating HTTPS and reaching gateway loopback. Preserve the inference bearer header. Do not expose Ollama or the raw origin port. Test authenticated and anonymous requests through the final hostname after approval; local success does not validate that route.
- Website/Discord administration is a later Kinetic-backend integration. This setup does not enable a website admin panel, remote model management, report collection or player restrictions. Keep administration disabled until that backend and its separate Access identity are configured and approved. The browser must never call Ollama or administer Minecraft directly.

## Troubleshooting

| Symptom | Check and next step |
| --- | --- |
| Java exits immediately | Check Java 21, YAML indentation, loopback bind, token format/distinctness, writable state, and whether another process owns the port/state directory. Startup errors intentionally omit secret details. |
| Connection refused | Confirm the process is running and the URL names the correct machine/network namespace. A local-only listener cannot be reached through an arbitrary public host port. |
| 401 `UNAUTHORIZED` | Check that this process inherited the correct nonexpired secret; restart only through the approved procedure after changing environment variables. Old API-key names do not work. |
| 403 `FORBIDDEN` | Use monitoring for `/health` and `/ready`, and inference for `/v1/aether/status` and `/v1/aether/generate`. |
| 403 `SERVER_ID_MISMATCH` | Match the request/Minecraft server ID to the gateway's configured server ID. |
| `ACTIVATION_REQUIRED` | Expected during the locked test. After approval, check all three flags and exact verified-model match. Do not bypass a missing capacity decision. |
| Health works but readiness is 503 | Check activation, saved pause state and the locally installed model name. Allow the ten-second model-status refresh. |
| `INFERENCE_PAUSED` | Read admission state with monitoring. Resume requires approved administration; keep the journal intact. |
| `MODEL_UNAVAILABLE`, missing model, or timeout | Check Ollama locally, its model inventory and actual resources. The gateway neither installs nor starts external Ollama. |
| `UNVERIFIED_RESPONSE` / `INVALID_MODEL_RESPONSE` | The model failed the required response/verification contract. Preserve the factual safeguards and qualify the model again. |
| 429 `RATE_LIMITED` / 503 `OVERLOADED` | Reduce request rate and concurrent callers; repeated retries add load. Do not increase concurrency on the unverified 8 GB host. |
| `AUDIT_UNAVAILABLE` / journal startup failure | Check writable persistent storage, disk space and duplicate ownership. Preserve the files for diagnosis; do not erase audit/pause state. |
| PowerShell rejects `-SkipHttpErrorCheck` | Run the checks in PowerShell 7 (`pwsh`), not Windows PowerShell 5.1. |
| Minecraft says it cannot authorize the server | Check its inference environment variable, server ID and URL; HTTP is allowed only on loopback. |

For deeper details only: [deployment reference](deploy/README.md), [Minecraft backend reference](docs/ai/aether-backend.md), and [Phase 1 implementation/testing record](docs/ai/protected-gateway-phase-1.md). Historical verification records describe their original artifacts, not a fresh test of your host.
