# Self-contained Aether server JAR

Historical record: the subsequent public-access update removes access keys and credential generation. Current behavior and upgrade steps are in [the bundled server guide](aether-bundled-server.md). References to authentication below describe the earlier implementation.

The requested deliverable is one platform-specific executable Java 21 JAR containing the gateway, Ollama CLI/native libraries, the approved llama3.1:8b model and Aether's existing prompts. Upload it to a compatible dedicated hosting instance and start it with java -jar. This supersedes the previous manual Ollama installation requirement for this bundled distribution. The Minecraft mod remains a remote client.

## Startup contract

1. Detect the operating system/architecture and reject a mismatched bundle with an actionable message.
2. Lock a private application data directory so two launchers cannot share a model/runtime store.
3. Stream bundled files to the data directory with path, size and SHA-256 validation. Reject symlink traversal. Reuse verified files, repair damaged managed files, and never overwrite operator configuration.
4. Create a configuration and random bearer credential on first launch; never print the credential. Respect the hosting instance's SERVER_PORT or PORT when creating defaults. Store model data outside the Java heap.
5. Start the bundled Ollama executable on loopback with its own model directory. Do not attach to or stop an independently running Ollama process. Disable Ollama cloud features for this managed local-model service.
6. Register aether-custom:8b from the bundled llama3.1:8b base using Ollama's create API. The gateway still owns full canon/personality and factual verification.
7. Preload the model with a bounded startup allowance, then start the existing gateway only after model setup succeeds. Preserve authentication, request limits, privacy defaults and shutdown behavior.
8. Stop the owned runtime on shutdown; retain extracted models and configuration for the next start.

The public host must allow native child processes, enough disk/RAM for the model, and the assigned service port. Bundling cannot change hosting permissions, supply GPU drivers, allocate a port, configure DNS/HTTPS, or implement player enrollment. Hosting provider remains unconfirmed. The artifact does not run a Minecraft world or implement the Minecraft game protocol.

## Distribution

Use ZIP64 for the multi-gigabyte JAR. Model blobs and native files are streamed, with a compact verified manifest. Preserve the model license and Ollama license. Normal gateway-only builds remain available for existing external-Ollama deployments; bundled builds require explicit local runtime/model inputs and fail if those inputs are missing. Never publish a small gateway-only JAR as the self-contained artifact.

## Validation

Focused tests cover path validation, hash mismatch, interrupted/repeated extraction, configuration preservation, random credential generation, port validation, singleton locking, preload failure and owned-process cleanup. Executable checks cover platform rejection. No-follow symlink/reparse protections are implemented; live platform-specific link scenarios were not exercised. Existing gateway/integration tests remain. Exercise an actual Windows bundle locally using the installed approved model; Linux runtime behavior requires a compatible Linux host. Keep full-project checks separate from standalone launcher checks.