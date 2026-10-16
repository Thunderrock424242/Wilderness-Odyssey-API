import assert from 'node:assert/strict';
import test from 'node:test';
import { explainDependencies, renderDependencyReport } from './dependency-report.mjs';

function configuration(project, scope, dependencies) {
    return {
        rootOrigin: { id: project, path: project }, configurationName: 'runtimeClasspath', scope,
        allDependencies: dependencies.map(([id, children = [], direct = true]) => ({
            id, dependencies: children, direct, project: false, origin: { id: project, path: project },
        })),
    };
}

const files = {
    'build.gradle': "dependencies {\n    implementation 'example:parent:2.0'\n}",
    'aether-server/build.gradle': "implementation 'com.google.code.gson:gson:2.14.0'",
    'tools/structure-viewer/build.gradle': "implementation 'com.google.code.gson:gson:2.14.0'",
};

test('finds a transitive dependency path and the actual declaration line of its parent', () => {
    const entries = explainDependencies([configuration(':', 'Runtime', [
        ['example:parent:2.0', ['example:child:1.0']], ['example:child:1.0', [], false],
    ])], files);
    const child = entries.find(entry => entry.dependency === 'example:child:1.0');
    assert.equal(child.scope, 'runtime');
    assert.deepEqual(child.locations[0].chain, ['example:parent:2.0', 'example:child:1.0']);
    assert.equal(child.locations[0].file, 'build.gradle');
    assert.equal(child.locations[0].line, 2);
    assert.match(child.locations[0].guidance, /parent/);
});

test('keeps shared Aether and viewer ownership while runtime scope wins', () => {
    const entries = explainDependencies([
        configuration(':aether-server', 'Runtime', [['com.google.code.gson:gson:2.14.0']]),
        configuration(':tools:structure-viewer', 'Development', [['com.google.code.gson:gson:2.14.0']]),
    ], files);
    assert.equal(entries.length, 1);
    assert.equal(entries[0].scope, 'runtime');
    assert.deepEqual(entries[0].locations.map(location => location.file), [
        'aether-server/build.gradle', 'tools/structure-viewer/build.gradle',
    ]);
});

test('keeps different versions distinct and identifies Minecraft-managed runtime libraries', () => {
    const entries = explainDependencies([
        configuration(':', 'Runtime', [
            ['net.neoforged:neoforge:21.1.262', ['org.apache.commons:commons-compress:1.26.0']],
            ['org.apache.commons:commons-compress:1.26.0', [], false],
        ]),
        configuration(':', 'Development', [['org.apache.commons:commons-compress:1.18']]),
    ], files);
    const runtime = entries.find(entry => entry.dependency.endsWith(':1.26.0'));
    assert.match(runtime.locations[0].guidance, /Minecraft\/NeoForge/);
    assert.equal(runtime.locations[0].line, null);
    assert.equal(entries.find(entry => entry.dependency.endsWith(':1.18')).scope, 'development');
});

test('handles cycles without inventing a declaration for inherited libraries', () => {
    const entries = explainDependencies([configuration(':', 'Runtime', [
        ['example:a:1', ['example:b:1']], ['example:b:1', ['example:a:1'], false],
    ])], files);
    assert.equal(entries[1].locations[0].line, null);
    assert.deepEqual(entries[1].locations[0].chain, ['example:a:1', 'example:b:1']);
});

test('rejects missing scopes and unsafe project paths instead of producing misleading links', () => {
    assert.throws(() => explainDependencies([configuration(':', 'Unknown', [['example:a:1']])], files), /scope/i);
    assert.throws(() => explainDependencies([configuration(':..:outside', 'Runtime', [['example:a:1']])], files), /project/i);
    assert.throws(() => explainDependencies([], files), /empty/i);
});

test('renders commit-specific file links and labels links without a declaration line as project entry points', () => {
    const entries = explainDependencies([configuration(':', 'Runtime', [['example:a:1']])], files);
    const report = renderDependencyReport(entries, 'https://github.com/example/repo', 'abc123');
    assert.match(report, /https:\/\/github.com\/example\/repo\/blob\/abc123\/build.gradle/);
    assert.match(report, /project entry point/i);
    assert.match(report, /settings.gradle/);
    assert.doesNotMatch(report, /#L0/);
});

test('keeps the job summary compact when a dependency has many resolution paths', () => {
    const entries = explainDependencies(Array.from({ length: 100 }, () => configuration(':', 'Runtime', [['example:a:1']])), files);
    const summary = renderDependencyReport(entries, 'https://github.com/example/repo', 'abc123', false);
    assert.match(summary, /dependency-ownership-report/);
    assert.match(summary, /example:a:1/);
    assert.ok(summary.length < 2000);
    assert.doesNotMatch(summary, /```powershell/);
});
