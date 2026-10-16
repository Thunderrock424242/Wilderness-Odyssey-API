import assert from 'node:assert/strict';
import { mkdtempSync, rmSync, writeFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { spawnSync } from 'node:child_process';
import { fileURLToPath } from 'node:url';
import test from 'node:test';

const script = fileURLToPath(new URL('./check-qodana-health.mjs', import.meta.url));

function check(t, runs) {
    const directory = mkdtempSync(join(tmpdir(), 'qodana-health-'));
    t.after(() => rmSync(directory, { recursive: true, force: true }));
    const input = join(directory, 'report.sarif.json');
    writeFileSync(input, JSON.stringify({ version: '2.1.0', runs }));
    return spawnSync(process.execPath, [script, input], { encoding: 'utf8' });
}

test('rejects the unresolved libraries and suspended sanity analysis seen in the real report', t => {
    const result = check(t, [{ invocations: [{ executionSuccessful: true }] }, {
        invocations: [{
            executionSuccessful: true,
            toolExecutionNotifications: [{ message: {
                text: "Roots jar:///data/project/build/moddev/artifacts/neoforge-merged.jar!/ of the library 'Gradle: neoforge-merged.jar' from module 'project.main' were not resolved",
            } }, { message: {
                text: 'Analysis by sanity inspection "Java sanity" was suspended due to high number of problems.',
            } }],
        }],
    }]);
    assert.equal(result.status, 1);
    assert.match(result.stderr, /neoforge-merged\.jar/);
    assert.match(result.stderr, /Java sanity/);
});

test('accepts complete analysis with ordinary diagnostic warnings and code findings', t => {
    const result = check(t, [{
        invocations: [{
            executionSuccessful: true,
            toolExecutionNotifications: [{ message: { text: 'An optional integration is unavailable' } }],
        }],
        results: [{ ruleId: 'DataFlowIssue', level: 'error' }],
    }]);
    assert.equal(result.status, 0, result.stderr);
    assert.match(result.stdout, /No unresolved library roots or suspended sanity analysis/);
});

test('rejects unsuccessful analysis and reports with no invocation evidence', t => {
    for (const runs of [[], [{}], [{ invocations: [{ executionSuccessful: false }] }]]) {
        const result = check(t, runs);
        assert.equal(result.status, 1);
        assert.match(result.stderr, /Qodana analysis health check failed/);
    }
});
