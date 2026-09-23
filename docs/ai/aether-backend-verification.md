# Aether backend refactor: delivery and verification

Historical record: the subsequent public-access update removes access keys and credential generation. Current behavior and upgrade steps are in [the bundled server guide](aether-bundled-server.md). References to authentication below describe the earlier implementation.

This is the historical 2026-09-21 gateway-only report. The later [complete bundle delivery and live-model evidence](aether-bundle-verification.md) supersedes its installation requirements and standalone artifact names; its root Minecraft test results remain historical evidence.

Verification recorded on 2026-09-21, using Java 21.0.10, Minecraft 1.21.1 and the current checkout's NeoForge 21.1.250 configuration. The current mod version is 5.0.0.

## Delivered behavior

The logical Minecraft server sends structured, authenticated requests to a separate Aether gateway. The gateway owns permanent prompts, specialist selection, model generation and factual verification, and calls privately hosted Ollama. Minecraft no longer discovers, launches, downloads or manages Ollama. Deterministic fallback remains available when authentication, networking, model availability, validation or queue admission fails.

The gateway is independently buildable, executable with Java 21, and contains no Minecraft or NeoForge dependencies. API keys remain server-side. Defaults disable conversation-content logging and profile-note sharing. Existing lore, specialist personas, local profile controls, onboarding, and optional manually started voice support remain in place.

## Verification results

| Check | Result |
| --- | --- |
| Current Minecraft production source compilation | Passed, Java 21 / NeoForge 1.21.1 |
| Focused `:aiTest` | 62 tests, 0 failures, 0 errors, 0 skipped |
| Independent gateway `-p aether-server build` | Passed; 12 tests, executable-JAR smoke test and dependency/class audit |
| Integrated gateway `:aether-server:build` | Passed; 12 tests, 0 failures, 0 errors, 0 skipped |
| Full existing `:test` | 1,213 tests: 1,208 passed, 2 failed, 3 skipped |
| Mod `:build` packaging | Completed and produced the regular mod JAR |
| Mod JAR boundary inspection | Remote client present; 0 standalone gateway classes; 0 retired Ollama runtime/client classes |
| Actual mod-side client -> actual gateway -> mocked Ollama | Health, generation and gateway outage/fallback passed |
| Header timeout regression | Failed before listener limits, then passed with the fix |
| Live Minecraft multiplayer/tick timing | Not run |
| Real Ollama/model inference and GPU readiness | Not run |
| Docker, systemd and remote HTTPS deployment | Not run |

The targeted AI tests are also included in the full suite; their counts should not be added as unique test coverage. The full command exited unsuccessfully because `:test` failed, even though `--continue` allowed both packaging tasks to finish. Root `build` intentionally depends on assembly only in this repository, so packaging is not evidence that all tests passed.

Commands used, sequentially from the repository root:

```powershell
$env:JAVA_HOME='C:\Program Files\Java\jdk-21.0.10'
.\gradlew.bat -p aether-server build '-PcodexBuildDir=.codex-build' --no-parallel --console=plain
.\gradlew.bat :compileJava '-PcodexBuildDir=.codex-build' --no-parallel --console=plain
.\gradlew.bat :aiTest :test :build :aether-server:build '-PcodexBuildDir=.codex-build' --no-parallel --continue --console=plain
```

Reports:

- `.codex-build/reports/tests/aiTest/index.html`
- `.codex-build/reports/tests/test/index.html`
- `aether-server/.codex-build/reports/tests/test/index.html`

## Full-suite failures outside the refactor

1. `ReleaseArtifactContractTest.packagedRecipesAndTagsUseMinecraft121SingularDirectories`: first failing assertion reports missing packaged `logo.png` (line 75).
2. `CryoWakeupVoiceAssetsTest.manifestMatchesSourceTextAndEveryMeasuredFemaleVoiceClip`: `filtration_offline` manifest expects 87 subtitle ticks; WAV measurement produces 88 (line 71).

The failing test sources, relevant cinematic implementation/assets and missing logo are unchanged relative to the pre-refactor baseline. They were not modified to make the suite pass. The three skips are two platform-dependent StructureGen symbolic-link tests and the opt-in bunker fixture regression.

The build also warned that the existing StructureGen catalog fingerprint does not match the current dependency environment. Its generator used supported fallbacks and successfully verified all three current blueprints. This warning is independent of the Aether network path.

## Outputs

- Regular mod JAR: `.codex-build/libs/wildernessodysseyapi-5.0.0.jar`
  - SHA-256: `F4CC7FB220353196D8F900C11F90F2B3E3A54E76C3C20C8BEEEEA1F0DEF87AA8`
- Standalone executable: `aether-server/.codex-build/libs/Aether-AI-Server.jar`
  - SHA-256: `496D87EA582D898E90B48F02A46EBAE42C81F3B99F6CED07B7A477EF13D92F0E`

Use the regular mod JAR in Minecraft, not a sources JAR. Run the gateway separately; it does not belong in the Minecraft mods folder. The hashes identify the packages inspected during this run; subsequent builds can replace them.

## Remaining acceptance work

- Install/create the configured model independently and supply matching gateway/Minecraft credentials. No model or service was installed by this refactor.
- Run the [manual game checks](aether-backend.md#manual-acceptance), including two players/two Minecraft servers, backend outage, legacy config migration, specialist routing, lore context and shutdown/reconnect behavior.
- Measure actual server tick responsiveness under simultaneous inference. Mocked concurrency tests exercise admission, response isolation and responsive health; they do not measure a running Minecraft server.
- Verify optional voice playback in its supported private-world environment.
- Exercise the Linux/HTTPS deployment on the target host. Model listing readiness is not proof of warm inference or GPU capacity.
- Resolve the two broader release-test failures in their owning systems before claiming a fully green release.

## File responsibilities

- `AIClient`, `AIChatListener` and `AIChatRequestGate`: authoritative context capture, bounded off-thread requests, history isolation, lifecycle cleanup and fallback delivery.
- `AIBackendConfig`, `AIConfigLoader` and settings: explicit remote/local-development configuration, environment credentials and safe legacy deprecation.
- `AetherBackendClient`, request/status records and bounded response subscriber: structured transport, total deadlines, narrow retries, circuit cooldown, response validation and cached status.
- `MemoryStore` and profile/onboarding integration: bounded transient history and preserved local controls.
- `AetherServer`: authenticated HTTP routes, bounded inference queue, responsive health, request deadlines and shutdown.
- Gateway `GenerateRequest`, `JsonHttp`, `ServerConfig`, `ApiSecurity`, `PromptCatalog` and `OllamaAdapter`: schema validation, bounded JSON, safe configuration, credential/rate enforcement, server-owned canon and model I/O.
- Tests: transport failures, privacy, multiple identities, queue pressure, actual cross-module integration and executable process behavior with mocked Ollama.
- Deployment resources: operator configuration, local setup, HTTPS guidance, systemd and optional Docker examples.

## File manifest

The list below records the refactor relative to starting commit `27bc157fdc8e0ff26f711f87eae3ed1f223ff11e`. Much of it was incorporated into repository commit `ad011382` during the task. Shared files also include later Echo integration and other work; those changes were preserved. Unrelated weather, structures, telemetry, rendering and other working-tree edits are outside this manifest.

A = added, M = modified, D = removed.

| Change | File |
| --- | --- |
| A | `aether-server/.dockerignore` |
| A | `aether-server/build.gradle` |
| A | `aether-server/settings.gradle` |
| A | `aether-server/src/main/java/com/thunder/aether/server/AetherServer.java` |
| A | `aether-server/src/main/java/com/thunder/aether/server/api/GenerateRequest.java` |
| A | `aether-server/src/main/java/com/thunder/aether/server/api/JsonHttp.java` |
| A | `aether-server/src/main/java/com/thunder/aether/server/config/ServerConfig.java` |
| A | `aether-server/src/main/java/com/thunder/aether/server/model/PromptCatalog.java` |
| A | `aether-server/src/main/java/com/thunder/aether/server/ollama/OllamaAdapter.java` |
| A | `aether-server/src/main/java/com/thunder/aether/server/security/ApiSecurity.java` |
| A | `aether-server/src/main/resources/aether-prompts.yml` |
| A | `aether-server/src/main/resources/aether-server.yml` |
| A | `aether-server/src/test/java/com/thunder/aether/server/AetherServerTest.java` |
| A | `deploy/README.md` |
| A | `deploy/aether-server.example.yml` |
| A | `deploy/docker/Dockerfile` |
| A | `deploy/docker/aether-server.docker.yml` |
| A | `deploy/docker/docker-compose.yml` |
| A | `deploy/systemd/aether-ai.service` |
| A | `docs/ai/aether-backend.md` |
| M | `docs/ai/local-voice.md` |
| M | `docs/ai/ollama-autostart.md` |
| M | `docs/ai/purpose-scope.md` |
| M | `src/main/java/com/thunder/wildernessodysseyapi/ai/perf/MemoryStore.java` |
| A | `src/main/java/com/thunder/wildernessodysseyapi/ai/story/AIBackendConfig.java` |
| M | `src/main/java/com/thunder/wildernessodysseyapi/ai/story/AIChatAccessPolicy.java` |
| M | `src/main/java/com/thunder/wildernessodysseyapi/ai/story/AIChatListener.java` |
| A | `src/main/java/com/thunder/wildernessodysseyapi/ai/story/AIChatRequestGate.java` |
| M | `src/main/java/com/thunder/wildernessodysseyapi/ai/story/AIClient.java` |
| M | `src/main/java/com/thunder/wildernessodysseyapi/ai/story/AIConfig.java` |
| M | `src/main/java/com/thunder/wildernessodysseyapi/ai/story/AIConfigLoader.java` |
| M | `src/main/java/com/thunder/wildernessodysseyapi/ai/story/AIFallbackResponder.java` |
| M | `src/main/java/com/thunder/wildernessodysseyapi/ai/story/AIOnboardingStore.java` |
| M | `src/main/java/com/thunder/wildernessodysseyapi/ai/story/AISettings.java` |
| M | `src/main/java/com/thunder/wildernessodysseyapi/ai/story/AetherSystemPrompt.java` |
| A | `src/main/java/com/thunder/wildernessodysseyapi/ai/story/provider/AetherBackendClient.java` |
| A | `src/main/java/com/thunder/wildernessodysseyapi/ai/story/provider/AetherRequest.java` |
| A | `src/main/java/com/thunder/wildernessodysseyapi/ai/story/provider/BackendStatus.java` |
| A | `src/main/java/com/thunder/wildernessodysseyapi/ai/story/provider/BoundedResponseBody.java` |
| D | `src/main/java/com/thunder/wildernessodysseyapi/ai/story/provider/OllamaChatClient.java` |
| D | `src/main/java/com/thunder/wildernessodysseyapi/ai/story/provider/OllamaLocalRuntime.java` |
| M | `src/main/java/com/thunder/wildernessodysseyapi/ai/voice/client/AetherVoiceClient.java` |
| M | `src/main/resources/ai_config.yaml` |
| M | `src/test/java/com/thunder/wildernessodysseyapi/ai/perf/MemoryStoreTest.java` |
| A | `src/test/java/com/thunder/wildernessodysseyapi/ai/story/AIBackendConfigTest.java` |
| M | `src/test/java/com/thunder/wildernessodysseyapi/ai/story/AIChatAccessPolicyTest.java` |
| A | `src/test/java/com/thunder/wildernessodysseyapi/ai/story/AIChatRequestGateTest.java` |
| A | `src/test/java/com/thunder/wildernessodysseyapi/ai/story/AIClientBackendTest.java` |
| A | `src/test/java/com/thunder/wildernessodysseyapi/ai/story/AIClientGatewayIntegrationTest.java` |
| M | `src/test/java/com/thunder/wildernessodysseyapi/ai/story/AIConfigLoaderTest.java` |
| M | `src/test/java/com/thunder/wildernessodysseyapi/ai/story/AISettingsTest.java` |
| A | `src/test/java/com/thunder/wildernessodysseyapi/ai/story/provider/AetherBackendClientTest.java` |
| D | `src/test/java/com/thunder/wildernessodysseyapi/ai/story/provider/OllamaChatClientTest.java` |
| D | `src/test/java/com/thunder/wildernessodysseyapi/ai/story/provider/OllamaLocalRuntimeTest.java` |
| M | `.gitignore` (Aether-specific additions only) |
| M | `README.md` (Aether-specific additions only) |
| M | `build.gradle` (Aether-specific additions only) |
| M | `settings.gradle` (Aether-specific additions only) |
| A | `docs/ai/aether-backend-verification.md` |
