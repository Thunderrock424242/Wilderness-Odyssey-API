import assert from 'node:assert/strict';
import { mkdtempSync, readFileSync, rmSync, writeFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { spawnSync } from 'node:child_process';
import { fileURLToPath } from 'node:url';
import test from 'node:test';

const script = fileURLToPath(new URL('./tag-qodana-performance.mjs', import.meta.url));

function runTagger(t, report) {
    const directory = mkdtempSync(join(tmpdir(), 'qodana-performance-'));
    t.after(() => rmSync(directory, { recursive: true, force: true }));
    const input = join(directory, 'original.sarif.json');
    const output = join(directory, 'github.sarif.json');
    const original = JSON.stringify(report);
    writeFileSync(input, original);
    const result = spawnSync(process.execPath, [script, input, output], { encoding: 'utf8' });
    assert.equal(result.status, 0, result.stderr);
    assert.equal(readFileSync(input, 'utf8'), original, 'The original Qodana report must remain untouched');
    return { report: JSON.parse(readFileSync(output, 'utf8')), input, output };
}

test('tags Qodana performance categories without changing findings or alert identity', t => {
    const report = {
        version: '2.1.0',
        runs: [{
            tool: {
                driver: { name: 'QDJVM', rules: [] },
                extensions: [{
                    name: 'com.intellij.java',
                    rules: [{
                        id: 'BusyWait',
                        defaultConfiguration: {
                            level: 'warning',
                            parameters: { codeQualityCategory: 'Performance' },
                        },
                        properties: { tags: ['JAVA'], precision: 'high' },
                        relationships: [{ target: { id: 'Java/Threading issues' } }],
                    }],
                }],
            },
            automationDetails: { id: 'project/qodana' },
            results: [{
                ruleId: 'BusyWait',
                level: 'warning',
                message: { text: 'Call to Thread.sleep() in a loop' },
                locations: [{ physicalLocation: { artifactLocation: { uri: 'src/Example.java' } } }],
                partialFingerprints: { 'equalIndicator/v2': 'existing-fingerprint' },
                suppressions: [{ kind: 'external' }],
            }],
        }],
    };
    const { report: tagged } = runTagger(t, report);
    const rule = tagged.runs[0].tool.extensions[0].rules[0];
    assert.deepEqual(rule.properties.tags, ['JAVA', 'performance']);
    rule.properties.tags.pop();
    assert.deepEqual(tagged, report, 'Only the rule tag may change');
});

test('recognizes the performance taxonomy when quality-category metadata is absent', t => {
    const { report } = runTagger(t, {
        version: '2.1.0',
        runs: [{
            tool: {
                driver: {
                    name: 'QDJVM',
                    rules: [{
                        id: 'SlowListContainsAll',
                        relationships: [{ target: { id: 'Java/Performance' } }],
                    }, {
                        id: 'NestedPerformanceInspection',
                        relationships: [{ target: { id: 'Java/Performance/Embedded' } }],
                    }],
                },
            },
            results: [],
        }],
    });
    for (const rule of report.runs[0].tool.driver.rules) {
        assert.deepEqual(rule.properties.tags, ['performance']);
    }
});

test('leaves non-performance rules untouched even when their descriptions mention performance', t => {
    const original = {
        version: '2.1.0',
        runs: [{
            tool: {
                driver: {
                    name: 'QDJVM',
                    rules: [{
                        id: 'ThreadingWarning',
                        fullDescription: { text: 'Investigate performance and correctness' },
                        relationships: [{ target: { id: 'Java/Threading issues' } }],
                    }, { id: 'UnknownInspection' }],
                },
            },
            results: [{ ruleId: 'UnknownInspection', level: 'error' }],
        }],
    };
    assert.deepEqual(runTagger(t, original).report, original);
});

test('processes every run and remains idempotent with existing performance tags', t => {
    const original = {
        version: '2.1.0',
        runs: ['QDJVM', 'QDJVM-second-run'].map(name => ({
            tool: {
                driver: {
                    name,
                    rules: [{
                        id: 'AlreadyTagged',
                        defaultConfiguration: { parameters: { codeQualityCategory: 'Performance' } },
                        properties: { tags: ['security', 'performance'] },
                    }],
                },
            },
            results: [],
        })),
    };
    const first = runTagger(t, original).report;
    const second = runTagger(t, first).report;
    assert.deepEqual(second, original);
});

test('rejects malformed reports without replacing an existing output', t => {
    const directory = mkdtempSync(join(tmpdir(), 'qodana-invalid-'));
    t.after(() => rmSync(directory, { recursive: true, force: true }));
    const input = join(directory, 'invalid.json');
    const output = join(directory, 'previous.json');
    writeFileSync(input, '{broken JSON');
    writeFileSync(output, 'previous report');
    const result = spawnSync(process.execPath, [script, input, output], { encoding: 'utf8' });
    assert.equal(result.status, 1);
    assert.match(result.stderr, /Unable to tag Qodana report/);
    assert.equal(readFileSync(output, 'utf8'), 'previous report');
});

test('refuses to overwrite the original report', t => {
    const { input } = runTagger(t, { version: '2.1.0', runs: [] });
    const original = readFileSync(input, 'utf8');
    const result = spawnSync(process.execPath, [script, input, input], { encoding: 'utf8' });
    assert.equal(result.status, 1);
    assert.match(result.stderr, /different paths/);
    assert.equal(readFileSync(input, 'utf8'), original);
});
