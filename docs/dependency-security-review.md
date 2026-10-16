# Minecraft and Aether advisory review

Reviewed on 2026-10-16 against commit `c4a9397469e094d532c6654b01315451ba38541b`:
Minecraft 1.21.1, NeoForge 21.1.262, FML 4.0.45, Netty 4.1.97.Final,
Log4j 2.22.1, LZ4 Java 1.8.0, Commons Lang 3.14.0, Plexus Utils 3.3.0,
and JLine Reader 3.20.0. This review covers the current repository/platform
baseline. It does not establish that these versions are patched or cover
every third-party modpack and custom deployment.

The scoped graph was refreshed successfully by
[Dependency Security Scope run 3](https://github.com/Thunderrock424242/Wilderness-Odyssey-API/actions/runs/38922584757).
Runtime presence establishes which packages need review. An advisory's
affected feature and its inputs determine whether it applies. A failed
Dependabot update establishes neither result.

## Evidence and boundaries

- Minecraft/NeoForge's generated 21.1.262 sources: `Connection` installs
  Minecraft packet codecs, `CompressionDecoder` uses JDK `Inflater`, and
  `setEncryptionKey` installs Minecraft cipher codecs. Client/server listeners
  use TCP sockets or in-memory local channels. They do not install the Netty
  TLS/SNI, subnet-filter, optional compression, or Unix descriptor handlers
  listed below. Minecraft's HTTP downloader uses `HttpURLConnection`.
- Aether's `AetherServer` uses JDK `HttpServer`; `OllamaAdapter`,
  `ManagedOllama`, the mod's `AetherBackendClient`, and
  `LocalVoiceServiceClient` use JDK `java.net.http`. The standalone gateway
  does not depend on Minecraft's Netty or Log4j. The voice service uses Python
  HTTP libraries. These do not exercise Netty's affected handlers.
- FML 4.0.45's shipped `log4j2.xml` and the generated client/server logging
  configurations use `PatternLayout` with console, GUI queue, and local file
  appenders. The affected XML/JSON/syslog formats and TLS network appenders
  are absent. Repository sources do not add them.
- Minecraft's `RegionFileVersion.VERSION_LZ4` constructs
  `LZ4BlockInputStream(InputStream)` and `LZ4BlockOutputStream`. The mod's
  `NbtCompressionUtils` also supports the block output stream. LZ4 1.8.0's
  input stream reuses its destination buffer, selects the fastest
  decompressor, and allocates its compressed buffer from the stream header.
  Its default constructor has `stopOnEmptyBlock=true`. Both block streams
  pass non-null, correctly bounded buffers to their XXHash checksums.
- NeoForge's `TerminalHandler` builds a JLine reader without a
  `HISTORY_IGNORE` variable. JLine 3.20.0's `DefaultHistory` uses an empty
  default. Neither the repository nor the checked platform console exposes
  a command/configuration path to set it.
- No call to `ClassUtils.getClass` or Plexus `Expand` was found in the mod,
  Aether, Minecraft/NeoForge, or FML sources. Inspection of available launch
  dependency class references found no external consumers of either class;
  Commons Lang's own sources do not call `ClassUtils.getClass` from another
  class. This supports the unused-feature decisions, without proving
  absence of reflection or behavior in arbitrary additional mods.

Revisit this record when platform/dependency versions, mods, codecs, console
extensions, or logging/network configurations change. Reopen an alert if its
preconditions become reachable. These decisions do not automatically suppress
new advisory IDs. Host permissions and custom configurations can change
applicability independently of repository code.

## Findings retained

These six alerts remain open because Minecraft can use the affected code.
Their environmental/input conditions still matter; this is not a claim that
every installation is exploitable. Alert numbers link to this repository's
Dependabot details; advisory IDs link to the published conditions.

| Alert | Advisory | Why it remains relevant |
| --- | --- | --- |
| [#10](https://github.com/Thunderrock424242/Wilderness-Odyssey-API/security/dependabot/10) | [GHSA-xq3w-v528-46rv](https://github.com/advisories/GHSA-xq3w-v528-46rv) | Minecraft initializes Netty on Windows. A local attacker who can create the probed OS-release files can exhaust the JVM heap. |
| [#11](https://github.com/Thunderrock424242/Wilderness-Odyssey-API/security/dependabot/11) | [GHSA-389x-839f-4rhx](https://github.com/advisories/GHSA-389x-839f-4rhx) | Follow-up to the same startup read, including null-byte input. Minecraft loads the affected Netty common version. |
| [#14](https://github.com/Thunderrock424242/Wilderness-Odyssey-API/security/dependabot/14) | [GHSA-vqf4-7m7x-wgfc](https://github.com/advisories/GHSA-vqf4-7m7x-wgfc) | LZ4 region reads select the fastest decompressor, including the affected native implementation where available. Crafted world data can reach this path. |
| [#15](https://github.com/Thunderrock424242/Wilderness-Odyssey-API/security/dependabot/15) | [GHSA-cmp6-m4wj-q63q](https://github.com/advisories/GHSA-cmp6-m4wj-q63q) | Block reads reuse destination buffers and can use the affected Java fallback. A disclosure path is not established; keep it visible for remediation/review. |
| [#35](https://github.com/Thunderrock424242/Wilderness-Odyssey-API/security/dependabot/35) | [GHSA-4v53-57pg-c464](https://github.com/advisories/GHSA-4v53-57pg-c464) | The actual region reader allocates from an unvalidated compressed-length header before reading payload. Outer input-size limits alone do not prevent this allocation. |
| [#38](https://github.com/Thunderrock424242/Wilderness-Odyssey-API/security/dependabot/38) | [GHSA-mcr4-qmvw-px4g](https://github.com/advisories/GHSA-mcr4-qmvw-px4g) | LZ4 can load bundled JNI libraries. Shared temp directories on server hosts can meet the local-user race conditions; private per-user temp directories avoid that condition. |

Use a compatible platform/dependency update for remediation. Several LZ4
fixes use the maintained `at.yawk.lz4` coordinates; no patch listed under the
old `org.lz4` coordinates does not mean no upstream fix exists. Substituting
libraries or changing world compression requires separate compatibility and
existing-world validation.

## Findings dismissed as vulnerable code not used

These are individual advisory decisions, not package-wide or severity-wide
ignore rules. The packages remain runtime-scoped in the submitted graph.

| Alert | Advisory | Unmet precondition in the reviewed baseline |
| --- | --- | --- |
| #9 | [GHSA-4g8c-wm8x-jfhw](https://github.com/advisories/GHSA-4g8c-wm8x-jfhw) | Requires Netty `SslHandler` with native SSLEngine. Minecraft uses its cipher codecs; Aether uses JDK HTTP. |
| #24 | [GHSA-3qp7-7mw8-wx86](https://github.com/advisories/GHSA-3qp7-7mw8-wx86) | Requires `IpSubnetFilterRule` IPv6 access controls. The checked listeners do not install that filter. |
| #32 | [GHSA-c4c3-7fpv-j4q5](https://github.com/advisories/GHSA-c4c3-7fpv-j4q5) | Requires Netty SNI selection with differing per-SNI/default TLS authentication. No such TLS termination exists here. |
| #28 | [GHSA-558v-64gr-wgg4](https://github.com/advisories/GHSA-558v-64gr-wgg4) | `Bzip2Decoder` is not installed. Minecraft network compression uses its own Inflater codec. |
| #12 | [GHSA-j288-q9x7-2f5v](https://github.com/advisories/GHSA-j288-q9x7-2f5v) | The affected `ClassUtils.getClass` name-parsing API has no identified caller in the checked baseline. Recheck new class-name parsing/reflective integrations. |
| #25 | [GHSA-x4gw-5cx5-pgmh](https://github.com/advisories/GHSA-x4gw-5cx5-pgmh) | No Netty SNI/ClientHello handler accepts connections. |
| #27 | [GHSA-c653-97m9-rcg9](https://github.com/advisories/GHSA-c653-97m9-rcg9) | No Netty TLS client with a wrapped plain trust manager is constructed. JDK HTTP clients do not use this wrapper. |
| #21 | [GHSA-445c-vh5m-36rj](https://github.com/advisories/GHSA-445c-vh5m-36rj) | `PatternLayout` is used, without direct `Rfc5424Layout` stream-based syslog output. |
| #19 | [GHSA-3pxv-7cmr-fjr4](https://github.com/advisories/GHSA-3pxv-7cmr-fjr4) | No `XmlLayout` output. An XML configuration file does not itself enable XML log output. |
| #23 | [GHSA-mj4r-2hfc-f8p6](https://github.com/advisories/GHSA-mj4r-2hfc-f8p6) | No Netty `Lz4FrameDecoder`. Minecraft LZ4 region streams are a different implementation, with findings retained above. |
| #18 | [GHSA-6fmv-xxpf-w3cw](https://github.com/advisories/GHSA-6fmv-xxpf-w3cw) | Plexus `Expand.extractFile` has no identified consumer. Other Plexus helpers do not exercise this archive extractor. |
| #13 | [GHSA-3p8m-j85q-pgmj](https://github.com/advisories/GHSA-3p8m-j85q-pgmj) | No Netty optional compression decoder from the affected path, including `BrotliDecoder`, is installed. Minecraft's Inflater codec is separate. |
| #31 | [GHSA-fccg-mwvh-qqg4](https://github.com/advisories/GHSA-fccg-mwvh-qqg4) | No Netty SNI/ClientHello aggregation path is used. |
| #30 | [GHSA-qv9r-c865-cp47](https://github.com/advisories/GHSA-qv9r-c865-cp47) | No `MapMessage` JSON serialization or dependent JSON layout. Pattern logs do not meet the two required conditions. |
| #16 | [GHSA-vc5p-v9hr-52mj](https://github.com/advisories/GHSA-vc5p-v9hr-52mj) | No TLS `SocketAppender` sends logs to a remote receiver. |
| #29 | [GHSA-xx22-p4ch-683r](https://github.com/advisories/GHSA-xx22-p4ch-683r) | Block-stream checksums use non-null arrays with ranges bounded by allocated buffers. The advisory excludes attacker-controlled contents of valid bounded arrays. |
| #20 | [GHSA-6hg6-v5c8-fphq](https://github.com/advisories/GHSA-6hg6-v5c8-fphq) | No SMTP/Socket/Syslog appender with nested TLS configuration. |
| #36 | [GHSA-gm45-99xc-r7wv](https://github.com/advisories/GHSA-gm45-99xc-r7wv) | No `LZ4FrameInputStream`. Region files use the separate legacy block stream. |
| #34 | [GHSA-6cx8-rjf8-pr8g](https://github.com/advisories/GHSA-6cx8-rjf8-pr8g) | No `LZ4DecompressorWithLength` convenience API is used. |
| #33 | [GHSA-5q95-hrpc-m3w3](https://github.com/advisories/GHSA-5q95-hrpc-m3w3) | JLine is used, but `HISTORY_IGNORE` remains empty with no exposed configuration path. Ordinary Minecraft console commands do not set JLine variables. |
| #26 | [GHSA-w573-9ffj-6ff9](https://github.com/advisories/GHSA-w573-9ffj-6ff9) | Requires a Unix domain socket opted into `FILE_DESCRIPTORS` mode. Minecraft uses TCP/local-memory channels; epoll TCP alone is insufficient. |
| #37 | [GHSA-343h-94h5-c4wr](https://github.com/advisories/GHSA-343h-94h5-c4wr) | The default block constructor uses `stopOnEmptyBlock=true`; the affected false/concatenation mode is not selected. |

## Future triage

Keep the development-only scope rule and native runtime coverage. Review new
alerts by advisory ID, selected version, owner/path, affected feature, and
attacker-controlled input. Dismiss a demonstrated unused path with an alert
comment and a review record. Keep applicable or unresolved findings visible.
Submit a fresh graph to refresh coverage; clearing alerts does not regenerate
their dismissal decisions. Dismissed findings can be reopened in GitHub's
closed alert view.
