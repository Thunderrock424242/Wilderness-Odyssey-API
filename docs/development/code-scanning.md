# Code scanning filters and review

The existing CodeQL Security Analysis workflow runs CodeQL for Java and GitHub
Actions, plus Qodana JVM Community. Qodana's report is uploaded with the existing
`qodana-jvm` category.

## Performance filter

After a scan using the updated workflow uploads its results, use:

```text
is:open branch:in-dev tag:performance
```

[Open performance alerts on GitHub](https://github.com/Thunderrock424242/Wilderness-Odyssey-API/security/code-scanning?query=is%3Aopen%20branch%3Ain-dev%20tag%3Aperformance)

[Open all code-scanning alerts](https://github.com/Thunderrock424242/Wilderness-Odyssey-API/security/code-scanning?query=is%3Aopen%20branch%3Ain-dev)

Replace `in-dev` in the query when reviewing a different branch. This uses GitHub's
existing search field; it does not add a custom dropdown to GitHub's interface.
The tagging step also adds the performance-alert link to its Actions job summary.

The tagging script reads Qodana's `codeQualityCategory` inspection metadata. Rules
classified as `Performance` receive the lowercase `performance` SARIF rule tag.
For reports without that category, a `Performance` segment in the inspection's
taxonomy path provides the fallback. Warning names and descriptions are not used
to guess categories. Qodana's classification can include threading inspections
such as busy waits, as well as minor suggestions such as unused assignments.

The script writes a separate GitHub upload report. The original Qodana artifact,
all findings, rule IDs, fingerprints, locations, severities, suppression state,
and upload category are preserved. Existing tags are retained, and running the
script again does not duplicate the new tag. Unknown inspection categories remain
in the full report without a performance tag.

This labels inspections already included by the Qodana profile. It does not enable
additional inspections or guarantee that every performance problem is detected.
Existing alerts gain the tag after a new upload; resetting or dismissing alerts
is not required. GitHub may retain an alert's original opened date when a scan
reports the same finding again.

## Reviewing findings

Review security, correctness, threading, and resource-lifecycle warnings against
the owning code and its callers. Minecraft and mixin lifecycle assumptions can
make generic Java suggestions inappropriate. Confirm false positives before
applying a narrow suppression with a reason.

Performance alerts identify candidates for investigation. Check execution
frequency, collection size, client/server side, and whether the code is a GameTest
fixture or a gameplay path. Measure tick, rendering, loading, or world-generation
impact before making invasive optimizations. A successful scan does not establish
a gameplay speedup, and clearing an alert does not establish that its code changed.

## Local validation

Node.js 24 and its standard library are sufficient; no package installation or
Gradle invocation is needed for this report-processing code.

```powershell
node --test .github/scripts/tag-qodana-performance.test.mjs
node .github/scripts/tag-qodana-performance.mjs original.sarif.json github.sarif.json
```

The input and output must be different files. The workflow runs the focused tests
before producing the GitHub report and then uses the existing SARIF upload step.

References:

- [GitHub SARIF rule tags](https://docs.github.com/en/code-security/reference/code-scanning/sarif-files/sarif-support)
- [Qodana report metadata](https://www.jetbrains.com/help/qodana/qodana-inspection-output.html)
- [GitHub alert filtering](https://docs.github.com/en/code-security/how-tos/manage-security-alerts/manage-code-scanning-alerts/assess-alerts)
