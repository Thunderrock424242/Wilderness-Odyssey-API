# Publish the API JAR to GitHub Packages

The **Publish JAR to GitHub Packages** workflow
(`.github/workflows/publish-packages.yml`) runs only through `workflow_dispatch`.
Pushes, pull requests, tags and GitHub releases do not start package publication.

## Run it

1. Open the repository's **Actions** tab.
2. Select **Publish JAR to GitHub Packages**.
3. Click **Run workflow** and select the branch to publish.
4. Click **Run workflow** to start validation and publication.

GitHub exposes manual dispatch when the workflow file is on the default branch.
This repository's default branch is `in-dev`.

The selected revision supplies the version from the root `build.gradle` and the
existing `mavenJava` publication. Its current coordinates are:

```text
com.thunder:wildernessodysseyapi:5.0.0
```

Change the root project version before publishing a new release version. A
rejected existing-version upload is a publication failure; this workflow does
not delete or replace package versions as recovery.

## What it publishes

The workflow publishes the existing API mod JAR and its Maven/Gradle metadata to:

```text
https://maven.pkg.github.com/thunderrock424242/wilderness-odyssey-api
```

Minecraft clients and servers use the same API mod JAR. The standalone Aether
Gateway and Structure Viewer are separate applications and are not additional
Maven publications in this workflow.

The existing Java component owns the published artifact, including the mod's
normal NeoForge Jar-in-Jar packaging. The workflow invokes the exact root task
`:publishMavenJavaPublicationToGitHubPackagesRepository`, so adding another
repository or subproject publication does not silently expand its destination
or scope.

## Authentication

The workflow uses the repository's automatic `GITHUB_TOKEN` with `contents: read`
and `packages: write`. No personal access token or extra repository secret is
needed for publishing a package associated with this repository, subject to its
GitHub package access settings.

Gradle accepts `GITHUB_ACTOR` and `GITHUB_TOKEN`. Existing `gpr.user`/`gpr.key`
Gradle properties take precedence, and legacy `USERNAME`/`TOKEN` environment
variables remain supported. `GITHUB_TOKEN` is exported to Gradle only for the
upload step.

## Validation and reports

Before uploading, the workflow refreshes the StructureGen catalog, runs the
focused StructureGen suite and full Java test/build tasks, checks for changes to
tracked generated sources, and prepares publication metadata. All Gradle calls
use JDK 21, the checked-in wrapper, isolated `.codex-build` output and
`--no-parallel`. Failed validation prevents the upload step from running.

Publication runs are serialized across branches, and a new request does not
cancel an upload already in progress. A running workflow must be allowed to
finish or be cancelled explicitly.

The job summary distinguishes setup, validation, metadata preparation, package
upload and diagnostic-artifact outcomes. It includes the commit and Maven
coordinates when available. The `packages-build-<run>-<attempt>` artifact keeps
built API JARs, SHA-256 checksums, publication metadata, command logs and available
JUnit XML/HTML reports for 14 days. A downloadable Actions artifact alone does
not mean that GitHub Packages publication succeeded.

To check metadata locally without uploading a package:

```powershell
.\gradlew.bat :generatePomFileForMavenJavaPublication :generateMetadataFileForMavenJavaPublication '-PcodexBuildDir=.codex-build' --no-parallel --console=plain
```

Inspect `.codex-build/publications/mavenJava/` afterward. A successful local build
or publication dry run does not establish remote authentication, package access
or successful upload; the manually started workflow's publication result does.
