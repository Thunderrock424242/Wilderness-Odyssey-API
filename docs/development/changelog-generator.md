# Automatic changelog generator

The changelog generator reads committed Git history and updates
`src/main/resources/config/wildernessodysseyapi/changelog.txt`, which is the default copied into the runtime config
folder and displayed by `/changelog`.

## Release workflow

Commit the intended release changes first, then run:

```powershell
$env:JAVA_HOME="$env:USERPROFILE\.jdks\corretto-21.0.10"
.\gradlew.bat generateChangelog -PchangelogVersion=0.1.0
```

`changelogVersion` defaults to `ModConstants.VERSION`, keeping the generated heading aligned with the version shown
by the in-game command. Passing the property explicitly is recommended in release automation.

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
.\gradlew.bat generateChangelog -PchangelogVersion=0.1.0 -PchangelogDryRun=true
```

Change the initial window or output path:

```powershell
.\gradlew.bat generateChangelog `
    -PchangelogVersion=0.1.0 `
    -PchangelogFirstRunDays=45 `
    -PchangelogOutput=build/reports/changelog-preview.txt
```

Only committed changes are included. The generator prints a warning when tracked working-tree changes exist so a
release changelog is not accidentally created from an incomplete commit range.
