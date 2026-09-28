# Protected gateway — Phase 1

Status: implementation in progress. Approved scope: protected gateway only.

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
- Baseline and test results will be recorded below as commands complete.
