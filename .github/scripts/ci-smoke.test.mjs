import assert from 'node:assert/strict';
import { spawnSync } from 'node:child_process';
import { existsSync, mkdtempSync, readFileSync, rmSync, writeFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import test from 'node:test';
import { monitorProcess, prepareClientOptions, renderReport } from './ci-smoke.mjs';

function launch(t, kind, source, options = {}) {
    const directory = mkdtempSync(join(tmpdir(), 'wo-ci-smoke-'));
    t.after(() => rmSync(directory, { recursive: true, force: true }));
    return monitorProcess({
        kind,
        command: process.execPath,
        args: ['-e', source],
        cwd: directory,
        logFile: join(directory, 'console.log'),
        startupTimeoutMs: 3000,
        soakMs: 100,
        shutdownTimeoutMs: 1500,
        ...options,
    });
}

test('prepares a fresh CI client profile with accessibility onboarding already completed', t => {
    const directory = mkdtempSync(join(tmpdir(), 'wo-ci-options-'));
    t.after(() => rmSync(directory, { recursive: true, force: true }));
    prepareClientOptions(directory);
    const options = readFileSync(join(directory, 'options.txt'), 'utf8');
    assert.match(options, /^onboardAccessibility:false$/m);
});

test('CI onboarding preparation preserves unrelated options and is repeatable', t => {
    const directory = mkdtempSync(join(tmpdir(), 'wo-ci-options-'));
    t.after(() => rmSync(directory, { recursive: true, force: true }));
    writeFileSync(join(directory, 'options.txt'), 'lang:en_us\nonboardAccessibility:true\nrenderDistance:8\n');
    prepareClientOptions(directory);
    prepareClientOptions(directory);
    const options = readFileSync(join(directory, 'options.txt'), 'utf8');
    assert.match(options, /^lang:en_us$/m);
    assert.match(options, /^renderDistance:8$/m);
    assert.match(options, /^onboardAccessibility:false$/m);
    assert.equal(options.match(/^onboardAccessibility:/gm).length, 1);
});

test('captures the blocking screen without counting onboarding as readiness', async t => {
    const result = await launch(t, 'client', `
        console.log('[WO-CI] CLIENT_SCREEN class=net.minecraft.client.gui.screens.AccessibilityOnboardingScreen overlay=none');
    `);
    assert.equal(result.status, 'failed');
    assert.equal(result.ready, false);
    assert.match(result.lastScreen, /AccessibilityOnboardingScreen/);
    const report = renderReport('client', { runtime: result });
    assert.match(report, /AccessibilityOnboardingScreen/);
    assert.match(report, /before readiness/);
});

test('a texture atlas and a successful early exit do not prove client readiness', async t => {
    const result = await launch(t, 'client', `
        console.log('Created: 512x512x0 minecraft:textures/atlas/particles.png-atlas');
    `);
    assert.equal(result.status, 'failed');
    assert.equal(result.ready, false);
    assert.equal(result.exitCode, 0);
    assert.match(result.reason, /before readiness/);
});

test('reports a failed build with its real exit code', async t => {
    const result = await launch(t, 'build', "console.error('Execution failed for task :aether-server:test'); process.exit(7)");
    assert.equal(result.status, 'failed');
    assert.equal(result.exitCode, 7);
    assert.match(result.reason, /exit code 7/);
});

test('a successful build does not require Minecraft readiness markers', async t => {
    const result = await launch(t, 'build', "console.log('BUILD SUCCESSFUL')");
    assert.equal(result.status, 'passed');
    assert.equal(result.exitCode, 0);
});

test('requires readiness and the client stop signal followed by a clean exit', async t => {
    const result = await launch(t, 'client', `
        process.stdout.write('[WO-CI] CLIENT_');
        setTimeout(() => console.log('READY screen=AlphaNoticeScreen'), 10);
        setTimeout(() => console.log('[WO-CI] CLIENT_STOP_REQUESTED'), 150);
    `);
    assert.equal(result.status, 'passed');
    assert.equal(result.ready, true);
    assert.equal(result.stopRequested, true);
    assert.equal(result.exitCode, 0);
});

test('rejects a client that exits after readiness without completing the soak', async t => {
    const result = await launch(t, 'client', "console.log('[WO-CI] CLIENT_READY screen=TitleScreen')");
    assert.equal(result.status, 'failed');
    assert.equal(result.ready, true);
    assert.match(result.reason, /before.*stop/);
});

test('does not hide a crash after reaching the startup screen', async t => {
    const result = await launch(t, 'client', `
        console.log('[WO-CI] CLIENT_READY screen=TitleScreen');
        console.error('Crash after startup');
        process.exit(9);
    `);
    assert.equal(result.status, 'failed');
    assert.equal(result.ready, true);
    assert.equal(result.exitCode, 9);
});

test('stops a ready server through its console and waits for a clean exit', async t => {
    const result = await launch(t, 'server', `
        console.log('[Server thread/INFO] [minecraft/DedicatedServer]: Done (12.345s)! For help, type "help"');
        process.stdin.on('data', data => {
            if (data.toString().trim() === 'stop') {
                console.log('Saving chunks');
                process.exit(0);
            } else { process.exit(4); }
        });
    `);
    assert.equal(result.status, 'passed');
    assert.equal(result.ready, true);
    assert.equal(result.stopRequested, true);
    assert.ok(result.elapsedMs >= 100);
});

test('times out a live process that never becomes ready', async t => {
    const result = await launch(t, 'client', 'setInterval(() => {}, 1000)', { startupTimeoutMs: 150 });
    assert.equal(result.status, 'failed');
    assert.equal(result.timedOut, true);
    assert.match(result.reason, /startup timed out/);
});

test('times out shutdown rather than passing a hung server', async t => {
    const result = await launch(t, 'server', `
        console.log('Done (1.234s)! For help, type "help"');
        process.stdin.resume();
        setInterval(() => {}, 1000);
    `, { shutdownTimeoutMs: 150 });
    assert.equal(result.status, 'failed');
    assert.equal(result.ready, true);
    assert.equal(result.stopRequested, true);
    assert.equal(result.timedOut, true);
    assert.match(result.reason, /shutdown timed out/);
});

test('reports launcher errors promptly', async t => {
    const result = await launch(t, 'client', '', { command: 'missing-wo-ci-command' });
    assert.equal(result.status, 'failed');
    assert.match(result.reason, /ENOENT/);
});

for (const failure of ['timeout', 'early exit']) test(`${failure} cleanup reaches descendants in the launched Linux process group`, {
    skip: process.platform === 'win32',
}, async t => {
    const directory = mkdtempSync(join(tmpdir(), 'wo-ci-child-'));
    t.after(() => rmSync(directory, { recursive: true, force: true }));
    const pidFile = join(directory, 'child.pid');
    const result = await launch(t, 'client', `
        const { spawn } = require('node:child_process');
        const { writeFileSync } = require('node:fs');
        const child = spawn(process.execPath, ['-e', 'setInterval(() => {}, 1000)'], { stdio: 'inherit' });
        writeFileSync(${JSON.stringify(pidFile)}, String(child.pid));
        ${failure === 'early exit' ? 'process.exit(7);' : 'setInterval(() => {}, 1000);'}
    `, { startupTimeoutMs: 1000 });
    assert.equal(result.status, 'failed');
    assert.equal(result.timedOut, failure === 'timeout');
    if (failure === 'early exit') assert.equal(result.exitCode, 7);
    assert.equal(existsSync(pidFile), true);
    const pid = Number(readFileSync(pidFile, 'utf8'));
    const state = spawnSync('ps', ['-o', 'stat=', '-p', String(pid)], { encoding: 'utf8' }).stdout.trim();
    assert.ok(!state || state.startsWith('Z'), `Child ${pid} is still running: ${state}`);
});

test('reports setup failures as startup not run without inventing game evidence', () => {
    const report = renderReport('client', null, { java: 'failure', smoke: 'skipped' });
    assert.match(report, /java.*failure/);
    assert.match(report, /not run/i);
    assert.doesNotMatch(report, /Startup.*passed/);
});

test('keeps build failures separate from unexecuted Minecraft startup', () => {
    const report = renderReport('server', {
        build: { status: 'failed', reason: 'exit code 7', exitCode: 7, elapsedMs: 1000 },
    });
    assert.match(report, /Build.*failed/);
    assert.match(report, /Startup.*not run/);
    assert.match(report, /exit code 7/);
});

test('an interrupted phase does not remain running or acquire a fictitious duration', () => {
    const report = renderReport('client', { build: { status: 'running' } }, { smoke: 'cancelled' });
    assert.match(report, /Build.*interrupted/);
    assert.doesNotMatch(report, /NaN|\| running \|/);
});
