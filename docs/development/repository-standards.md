# Repository standards baseline

This project follows the conventions in the root `AGENTS.md`. The initial standards refactor established these
boundaries:

- `WildernessOdysseyAPIMainModClass` performs startup wiring only.
- `ModRegistries` owns mod-level deferred registers and delegates feature registries.
- `ModConfigRegistration` owns config filenames, config types, and runtime cache refreshes.
- `ModPayloads` owns side-specific network codec and handler registration.
- `ModCommands` owns command dispatcher registration.
- `ServerLifecycleEvents` owns cross-feature server startup, tick, reload, unload, and shutdown coordination.

`RepositoryStructureTest` enforces lowercase package names, package-to-directory alignment, the base mod package,
PascalCase public types, and matching source filenames. This keeps the mechanical parts of the repository standard
from drifting while feature reviews focus on NeoForge behavior, side safety, performance, and documentation quality.

Large gameplay classes should be split only along stable feature boundaries. In particular, mixins should retain only
the injection or redirect and delegate reusable logic to the feature package when that logic can be tested without a
Minecraft runtime.

Release changelogs are generated from committed Git history through the `generateChangelog` Gradle task. See
`docs/development/changelog-generator.md` for the version-baseline and first-run behavior.
