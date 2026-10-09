# Aether local readiness and terminal operations — October 9, 2026

The current protected gateway and Linux amd64 native bundle were built locally, with a matching Wilderness Odyssey API 4.2.0 mod candidate. Deployment, native Linux execution on Kinetic, real model capacity, final HTTPS routing and live Minecraft acceptance remain unverified. No production upload, model activation or host restart was performed.

## Implemented operator workflow

[Terminal operations for Kinetic](../../deploy/operator/README.md) provides an ignored local connection config, explicit Gradle build/bundle commands, offline prompt/config validation, release manifests, versioned SFTP staging, behavior publication with a previous-file backup, HTTP probes, synthetic generation and an optional configured panel restart request. The actual Kinetic restart path/body must come from its signed-in API docs; the toolkit supplies no assumed route. Password-based SFTP remains interactive; supported key-based SFTP can run the batch directly.

Behavior edits use the existing server-owned external prompt catalog and apply on restart. Relative prompt paths now resolve beside the configuration. For an older operator config that used a working-directory-relative path, update that path explicitly or use an absolute path. Existing configs, lore, profiles, model storage, worlds and admission journals are preserved; the new initial template keeps all commissioning flags false.

The CLI supports `--validate-config <file>` and `--validate-prompts <file>`. These use the actual safe YAML and prompt loaders without opening a listener, creating state or starting Ollama. Validation is not credential provisioning, capacity approval or model inference.

## Fresh local evidence

| Check | Result |
| --- | --- |
| Standalone gateway suite | 57 tests; zero failures/errors |
| Focused Wilderness API `aiTest` | 65 tests; zero failures/errors; production/test compilation completed |
| Standalone dependency/class boundary check | Passed |
| Operator `Build` command | Completed with `BUILD SUCCESSFUL`, including regular mod packaging |
| Linux native bundle Gradle packaging | Completed with `BUILD SUCCESSFUL`; 71 assets / 13,161,659,466 payload bytes verified |
| Linux bundle embedded gateway/resources compared with current gateway JAR | Matched; selected platform/model and asset entry sizes verified |
| Operator PowerShell checks | Passed: real local HTTP JSON, response bound, redirect rejection, HTTPS/loopback policy, modified artifact/path rejection, versioned staging, backup-preserving publication and stale/wrong-platform bundle rejection |
| Offline validation of complete staged operator config and original specialist canon | Passed; activation remains false |
| Focused diff whitespace review | Passed |

The Java gateway/Minecraft suites use mocked Ollama. They prove transport/contract behavior, not current real model inference or tick performance. The build emitted the existing StructureGen catalog-fingerprint warning; it completed successfully. The initial sandbox denied the Gradle wrapper cache lock; approved cache access and quoted PowerShell property arguments resolved validation startup. No permissions or security settings were changed.

The principal successful invocations were:

```powershell
.\deploy\operator\aether.ps1 -Action Build
.\deploy\operator\Test-AetherOperator.ps1
.\gradlew.bat -p aether-server test verifyStandalone bundleJar '-PcodexBuildDir=.codex-build' '-PaetherBundlePlatform=linux-amd64' '-PaetherRuntimeDirectory=<existing-staged-linux-runtime>' '-PaetherModelDirectory=<existing-installed-models>' --no-parallel --console=plain
```

The bundle reused existing Ollama 0.17.7 and `llama3.1:8b` inputs. No download, model switch or live inference occurred. The Windows native bundle was not rebuilt and remains historical.

## Prepared candidate identity

Candidate directory: `.codex-build/aether-operator/releases/aether-20261009-152054-02dd73af`. Its `release.json` records SHA-256, size and relative destination for each staged file, with `hostVerified: false`.

| Artifact | Bytes | SHA-256 |
| --- | ---: | --- |
| `Aether-AI-Server-linux-amd64.jar` | 13,165,170,985 | `3C2ED84D98460C6C22C01B51815D8C71BA7D713EF788780612740B0812D66B2A` |
| `Aether-Gateway.jar` | 1,570,940 | `76EB0C4CB60936D1C39124C08DC8CC104B2F6BD6E0E47B3EC51F9A59576328BF` |
| `minecraft/wildernessodysseyapi-4.2.0.jar` | 26,786,373 | `6690680787D7F2ACD59D9FE17435F254EF99F421F09245E3024EF062C7DE291B` |

The normal build outputs are under `aether-server/.codex-build/libs/` and `.codex-build/libs/`. The mod candidate reflects the checkout when built, including pre-existing unrelated work; this task did not modify that other work. Preserve/review it before treating the candidate as a release.

## Host acceptance still required

The operator did not yet have instance/address details and requested a config to fill in later. Accordingly `gatewayUrl`, SFTP destination and documented restart route remain blank. `Check` reports them as unconfigured, with capacity and live Minecraft unverified. Live checks cannot run until these values and the separately provisioned process credentials exist.

Confirm the actual Kinetic OS/architecture, native child-process support, independent Aether process/instance, storage, available RAM/VRAM and private connectivity. Obtain a supported local connector/HTTPS route while preserving private Ollama and gateway listeners. After qualifying the model, perform authenticated/wrong-scope probes, cold/warm generation, persistent-state restart, real Minecraft chat and controlled outage/recovery. Voice is a separate acceptance check if enabled. Follow [AETHER_SETUP.md](../../AETHER_SETUP.md) for commissioning.
