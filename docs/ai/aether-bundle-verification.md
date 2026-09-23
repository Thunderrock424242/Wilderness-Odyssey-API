# Bundled Aether server delivery and verification

Recorded 2026-09-22, Java 21.0.10. The user approved `llama3.1:8b` with Aether's existing personality and lore. These are complete platform-specific ZIP64 JARs, containing native Ollama 0.17.7, the selected model's weights/manifest, gateway classes, prompts, and license notices. The prompts configure the base model; this is not a separately trained set of Aether weights.

## Delivered artifacts

All paths below are relative to the repository root.

| Platform | Artifact | Bytes | Bundled assets |
| --- | --- | ---: | ---: |
| Windows x86-64 | `aether-server/.codex-build/libs/Aether-AI-Server-windows-amd64.jar` | 10402531635 | 675 |
| Linux x86-64 | `aether-server/.codex-build/libs/Aether-AI-Server-linux-amd64.jar` | 13163142822 | 71 |

SHA-256:

- Windows: `D92268052F966B206D02D6439998C464C2611C4FD8E41575B58421D3CBEBDF46`
- Linux: `98FF484B53EF04DBC82C18A432136B9B4880C41FAC444FDCB4600953D7AF24ED`

Each delivered archive's inventory was inspected: every declared asset exists at the expected size, the platform and model identities match, Aether's prompt resource is present, and no Minecraft/NeoForge classes are included. Model blob digests and native payload hashes are checked by the packaging task. Windows extraction additionally checked the installed payload against its SHA-256 inventory. The original Linux release archive was checked against its published SHA-256 before staging.

`Aether-Gateway.jar` is the smaller external-Ollama application. The historical `Aether-AI-Server.jar` from the earlier refactor is also gateway-only and must not be confused with either platform-labelled complete bundle above.

## Public access update: current validation

The user requested removal of access keys and deferred a monitoring system to a later task. The gateway now accepts readiness and generation requests without credentials; Minecraft does not require or send a key. Fresh setup generates no key. Existing key settings and environment variables are ignored, with saved configuration preserved. `RequestLimits` replaces credential enforcement with one bounded service-wide budget. New defaults use `limits.requests_per_minute`; the older per-server setting remains a fallback for the shared limit.

The artifact sizes and checksums above describe the rebuilt public-access bundles.

| Check | Result |
| --- | --- |
| Standalone `build`, executable gateway test, dependency/class boundary audit | Passed |
| Standalone JUnit | 33 tests, 0 failures, 0 errors, 0 skips |
| Root `aiTest`, including real mod-to-gateway HTTP integration with mocked Ollama | 63 tests, 0 failures, 0 errors, 0 skips |
| Minecraft production/test compilation and mod JAR packaging through the normal `aiTest` dependencies | Passed; `.codex-build/libs/wildernessodysseyapi-5.0.0.jar` |
| Complete Windows and Linux bundle packaging | Passed |
| Both archives compared with current standalone gateway classes and manifest asset sizes | Passed; obsolete `ApiSecurity` class absent |
| Real Windows bundle with existing application data and legacy key settings | Started successfully |
| Readiness with no Authorization header | HTTP 200 |
| Actual model generation with no Authorization header | HTTP 200, 2.50 seconds; display and speech matched |
| Console stop | Exit 0 |
| Focused diff whitespace check | Passed |
| Native Linux execution, Kinetic Hosting, remote HTTPS, live Minecraft gameplay | Not run |

The regression tests first reproduced gateway rejection, Minecraft refusing to send a key-free request, and first-launch credential creation. Coverage now includes ignored legacy credentials, key-free health/readiness/generation, shared admission limits, limit-window expiry, fallback, queue saturation, malformed input, and executable startup. Existing opt-in logs are unchanged in scope; no monitoring system or account system was added.

The root task emitted an existing StructureGen content-catalog fingerprint warning and completed successfully. The full unrelated Minecraft test suite was not rerun. Raw current smoke evidence is in ignored local output `.codex-build/aether-public-access-verification.json`; JUnit reports are under each project's `.codex-build/reports/tests/`.

## Original launcher validation before public access

The checks below describe the earlier keyed bundle and are retained as historical evidence. They do not claim a fresh repeat of every lifecycle check after the public-access update.

| Check | Result |
| --- | --- |
| Standalone `build`, tests, executable checks, dependency/class boundary audit | Passed |
| Original JUnit suite | 31 tests, 0 failures, 0 errors, 0 skips |
| Windows complete bundle packaging | Passed |
| Linux complete bundle packaging | Passed |
| Actual Windows first extraction and configuration creation | Passed |
| Restart reuses verified assets and preserves saved configuration/key | Passed |
| Native Ollama startup using an application-owned model store and home | Passed; fresh Ollama identity found under the owned home |
| Actual local `aether-custom:8b` creation from approved base model | Passed |
| Model preload before gateway opens | Passed |
| Authenticated readiness | HTTP 200 |
| Final real-model generation through gateway, normal 30-second limit | HTTP 200, 3.11 seconds; display and speech matched |
| Subsequent real-model generation | HTTP 200, 3.10 seconds; display and speech matched |
| Console stop after generation | Exit 0; zero remaining owned native processes |
| Console stop during native startup | Exit 0; zero remaining owned native processes |
| Linux bundle invoked on Windows | Rejected incompatible platform before creating a data directory |
| Gateway-only JAR invoked as a managed bundle | Rejected with an explicit missing-bundle message |
| Scoped final diff whitespace check | Passed |
| Native Linux execution | Not run; requires a compatible Linux host |
| Remote HTTPS and actual hosting panel | Not run; provider/plan unconfirmed |
| Live Minecraft single-player/multiplayer and audio playback | Not run in this launcher extension |

The 31 tests comprise 14 gateway HTTP tests, 9 installer/configuration tests, 1 launcher executable test, 4 managed-process tests, and 3 existing Echo prompt-boundary tests. The report is at `aether-server/.codex-build/reports/tests/test/index.html`.

The first unprepared real request reached the previous 30-second deadline during model loading. A diagnostic run with a longer request allowance completed in 35.64 seconds, then 2.30 seconds warm. Managed startup now preloads the model before opening the gateway; the final checks used the normal 30-second request setting. These sample timings describe this Windows machine, not capacity or latency on a hosting plan.

Real-model inspection also found copied example text and disagreement between displayed and spoken answers. Regression tests reproduced both defects. The prompt now requests one actual answer without sample placeholder values, the gateway derives speech from that same canonical answer before factual verification, and the old copied placeholder values fail response validation. The final two live requests passed this contract. This is not an exhaustive assessment of model factual accuracy or personality quality.

## Ownership and recovery

- `BundleManifest` and `BundleInstaller`: bounded manifests, validated paths, streamed integrity checks, repair of managed assets and scoped abandoned-part cleanup.
- `DataDirectory` and `SetupConfig`: exclusive ownership, no-follow path checks, private first-launch settings and preservation of operator changes.
- `ManagedOllama`: private runtime/home/model paths, bounded registration/preload, bounded diagnostics and cleanup of the owned process tree.
- `AetherLauncher`: managed/external CLI modes, early console cancellation, startup/shutdown ownership and gateway lifecycle.
- `bundle.gradle`: explicit runtime/model inputs, approved model-blob verification, platform labels and ZIP64 packaging.
- `PromptCatalog` and `OllamaAdapter`: one canonical displayed/spoken reply with the existing factual-verification pass and unchanged public gateway schema.

An independent initial review identified the home-directory isolation, incomplete configuration publication and abandoned-part issues; these were fixed and checked by the main agent. The independent follow-up review did not complete, so it is not reported as a second approval. Live shutdown evidence is for console stop; service-manager signals, forced termination and power failure are distinct conditions. Platform-specific symlink/junction rejection and disk-full interruption were not exercised as live fault-injection scenarios.

## Rebuild and remaining deployment work

See [the installation and rebuild guide](aether-bundled-server.md). Normal builds produce the external gateway; `bundleJar` with explicit runtime/model inputs produces the complete distribution. Generated runtime/model data and JARs remain in ignored build directories and are not source-controlled.

Use the bundle matching the hosting OS. The provider must allow Java 21, native child processes, sufficient native RAM/GPU memory and disk space for both the JAR and extracted payload. Configure a reachable HTTPS gateway address for remote single-player installations. No access key is needed. The application does not configure DNS, certificates, firewalls, game-hosting permissions or account enrollment.

The JSON request/response schema is unchanged. The public-access update rebuilt the Minecraft mod and ran its focused AI suite, as recorded above. Earlier broader results and two unrelated full-suite failures are preserved in [the historical refactor report](aether-backend-verification.md); focused validation does not establish a fully green Minecraft release.