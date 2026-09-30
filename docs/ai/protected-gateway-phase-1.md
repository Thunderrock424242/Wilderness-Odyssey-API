# Protected gateway — Phase 1

Status: Phase 1 implemented and focused validation passed. Production activation remains gated. Approved scope: protected gateway only.

## Requirements retained

- Minecraft -> authenticated Aether gateway -> private Ollama.
- Website -> Kinetic administration backend -> protected gateway. Browsers cannot call gateway operations.
- Separate inference, monitoring, and administration credentials and scopes.
- Production inference remains disabled until hosting and capacity are verified; 500% CPU / 8 GB RAM / unknown GPU does not establish suitability of the existing 8B model.
- No live deployments, service restarts, active-model changes, or remote production administration without separate approval.
- No new conversation retention, automatic moderation, Minecraft bans, or personality/lore changes.

## Work sequence

1. Add failing HTTP security tests and confirm the current unauthenticated gateway fails them.
2. Add fail-closed credential/configuration validation, origin authentication, scoped routes, server-identity binding, and activation gates.
3. Implement narrowly scoped, persistent and audited pause/resume with request validation, expiry, idempotency and revision checks.
4. Update the Minecraft transport to use its inference-only server credential and sanitized typed failures.
5. Preserve existing bounded queues/deadlines; test authentication independently of inference saturation.
6. Update deployment examples and migration documentation; run gateway tests, focused mod tests and package checks sequentially.
7. Review the complete Phase 1 diff and fix attributable findings.

## Execution decisions and evidence

- Work in the user's clean `in-dev` checkout. No unrelated changes were present at the beginning; no extra checkout is needed for this single-system phase.
- This document carries the approved in-chat design into a durable execution record. Later phases remain separate.
- Original authentication regression tests failed against the anonymous gateway, then passed after scoped authentication was added.
- The monitoring admission-status regression failed against the placeholder administration route, then passed after local admission state was implemented.
- The strict JSON regression reproduced acceptance of duplicate fields; strict parsing now rejects duplicate keys, non-JSON syntax and trailing input.
- Sequential gateway tests, standalone dependency/package check and focused Minecraft Aether tests completed successfully on 2026-09-29. Final counts and review fixes are recorded below.

## Implemented boundary

The standalone service binds to loopback and requires separate environment-backed inference, monitoring and administration secrets. Credentials are compared using SHA-256 digests and constant-time equality. Optional expiry and duplicate-secret rejection keep scopes independent. Minecraft carries only the inference credential, over HTTPS except for loopback development, and supplies the configured server identity. It never calls administration, monitoring or raw Ollama routes.

Administration requires both its own bearer credential and a signed Cloudflare Access service assertion verified against the pinned issuer, application audience, service identity and timestamps. Trusted issuer keys are fetched with bounded transport and cached refresh. Tests use locally generated RSA fixtures, not Cloudflare. The Kinetic backend remains responsible for authorizing staff; the gateway independently verifies the service and validates every operation. Its actor field is an audit attribution asserted by that authenticated backend, not a browser identity accepted directly.

Read-only monitoring covers liveness, readiness, queue counts and admission state. Minecraft receives only sanitized availability. Readiness requires commissioning, unpaused local admission and cached availability of the configured model. It is not a real inference capacity test.

## Pause/resume contract

The only Phase 1 mutation is POST /v1/admin/admission with application/json and exactly these fields:

```json
{
  "requestId": "a332a572-41ef-4873-9c15-65583048733b",
  "expectedRevision": 0,
  "expiresAt": "<UTC timestamp no more than 5 minutes in the future>",
  "paused": true,
  "actor": "<Kinetic-authorized staff identifier>"
}
```

The UUID, actor, boolean, integer revision and expiry are validated at the origin. Bodies are capped at 4 KiB. Unknown fields, duplicate JSON members, stale revisions and changed-payload replays are rejected. An identical unexpired request returns its original result without another mutation. Expired requests, including replays, must be replaced with a fresh request after reading current state.

The gateway owns a single-writer local admission journal. It writes and forces the accepted transaction to storage before changing its in-memory revision or pause state. Restart replays the journal; a corrupt or incomplete journal fails startup. A write failure closes inference admission for the current process. The journal retains at most 4096 mutations or 2 MiB and then rejects new mutations; there is no automatic audit deletion or remote reset. Operators must arrange reviewed archival/rotation before this limit. Request metadata is separate, bounded to two approximately 1 MiB files, and contains no message bodies or credentials.

Pause rejects new admission and queued work when it reaches a generation worker. Already-running inference drains within its existing total deadline. Resume changes only pause state: it cannot bypass hosting/capacity/model verification. Neither mutation depends on Kinetic remaining online after it is applied. Existing Minecraft bans, player access controls, lore and conversation-memory ownership are untouched.

## Commissioning and future phases

The supplied configurations keep hosting_verified, capacity_verified, inference_enabled and administration_enabled false. verified_model is empty. The bundled launcher checks activation before launching/registering/preloading its native model; extracting files in setup-only mode does not approve inference. The gateway-only process never starts or changes Ollama.

Kinetic must confirm the actual CPU model/share, available RAM after Minecraft, GPU/VRAM, native-process/container permission, private networking/local connector, writable persistent storage and measured load/latency before commissioning. A 500% CPU limit with 8 GB RAM and unknown GPU does not approve aether-custom:8b. A different active model requires a new verification and separate user approval.

Later phases own the Kinetic administration UI/backend adapter, explicit player-submitted reports and locally persisted Aether access restrictions. They must remain separate from Minecraft bans and must not add broad conversation retention. Model administration must use validate -> pause/drain -> activate/test -> commit/rollback; no Phase 1 endpoint permits model changes, downloads, deletion, shell execution or arbitrary Ollama forwarding.

No live deployment, production administration enablement, model change or service restart was performed. Runtime acceptance on Kinetic and live Minecraft remains outstanding.

## Final validation and review

The final sequential command completed with BUILD SUCCESSFUL:

```powershell
.\gradlew.bat :aether-server:test :aether-server:verifyStandalone aiTest '-PcodexBuildDir=.codex-build' --no-parallel --console=plain
```

- Gateway: 53 tests, zero failures or errors, including real local HTTP, signed RSA Access fixtures, journal restart/replay/corruption, strict JSON, private endpoint configuration, overload, timeouts and executable JAR startup against mock Ollama.
- Minecraft Aether: 65 tests, zero failures or errors. Production Java compilation passed. The actual Minecraft transport and actual gateway were tested together with only Ollama mocked.
- Standalone package check passed; the gateway has no Minecraft or NeoForge dependencies/classes.
- git diff --check passed. Test output includes the existing StructureGen catalog-fingerprint warning; the generated local test/build prerequisites and focused suites completed successfully.
- The packaged-startup test originally encountered a Windows file-sharing failure while deleting its temporary log. Capturing the child's bounded output through a pipe removed that test-file dependency; subsequent startup tests passed.
- Direct source/diff review tightened Ollama configuration to private/loopback literals, made expiry evaluation occur after acquiring the journal writer lock, and closed transport resources when journal initialization fails. The public-upstream regression failed before the fix and passed afterward.
- An independent reviewer was dispatched but stopped at an account usage limit without returning findings. This is not recorded as an independent review approval. Final source/diff review was performed by the implementing agent.

Current locally generated artifacts:

| Artifact | SHA-256 |
| --- | --- |
| aether-server/.codex-build/libs/Aether-Gateway.jar | 4FCBBBBE1699373583718D32C00D9B969F120E8DF0569439B1D0346F12BFCB4C |
| .codex-build/libs/wildernessodysseyapi-5.0.0.jar | C05A5F390748AE982BE3132CEE6959BDF41A9E89598FC7AFE491300F622EBB9D |

The regular Minecraft JAR is the mod artifact; do not install a sources JAR. Existing platform-specific Aether bundles predate these changes and were not rebuilt or approved for deployment. Docker/systemd examples were updated as disabled configuration templates but were not run. Full unrelated Minecraft tests, a live Minecraft session, real model inference, real Cloudflare Access and Kinetic host capacity/performance have not been validated by this phase.
