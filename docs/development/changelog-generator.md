# Automatic changelog generator

The changelog generator reads committed Git history and updates
`src/main/resources/config/wildernessodysseyapi/changelog.txt`, which is the default copied into the runtime config
folder and displayed by `/changelog`.

## Release workflow

Set the release once through the top-level project version in `build.gradle`:

```groovy
version = "4.2.0"
```

That value also supplies the generated NeoForge metadata and Maven publication version. Commit the intended release
changes, then run:

```powershell
.\gradlew.bat generateChangelog
```

The Gradle task passes the project version to the generator. If it differs from the version stored in the changelog
marker, the generator creates a new `## <version>` section. If it is unchanged, the existing section is refreshed from
its original Git baseline instead of creating a duplicate. `-PchangelogVersion=<version>` remains available as an
explicit recovery or release-automation override.

The first automatic run has no stored Git baseline, so it includes committed changes from the previous 30 days. The
generated changelog embeds a metadata comment containing its base and HEAD commits. When a different version is
generated later, that previous HEAD becomes the new base and the heading reads, for example, `Changes from 1.0 to
1.1:`. Rerunning the same version retains its original base and refreshes that version's section instead of duplicating
it.

Merge commits are omitted. Individual commits are grouped under `Added`, `Changed`, `Fixed`, or `Removed`, and affected
areas such as water, world generation, cloak, Rift systems, telemetry, or build tooling are inferred from changed file
paths. Commits that only update the generated changelog are ignored.

## Preview and options

Preview without writing:

```powershell
.\gradlew.bat generateChangelog -PchangelogDryRun=true
```

Change the initial window or output path:

```powershell
.\gradlew.bat generateChangelog `
    -PchangelogFirstRunDays=45 `
    -PchangelogOutput=build/reports/changelog-preview.txt
```

Only committed changes are included. The generator prints a warning when tracked working-tree changes exist so a
release changelog is not accidentally created from an incomplete commit range.
