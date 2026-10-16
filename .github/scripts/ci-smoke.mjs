import { spawn, spawnSync } from 'node:child_process';
import { appendFileSync, closeSync, existsSync, mkdirSync, openSync, readFileSync, readSync, statSync, writeFileSync } from 'node:fs';
import { dirname, join, resolve } from 'node:path';
import { createInterface } from 'node:readline';
import { fileURLToPath } from 'node:url';

const clientReady = /\[WO-CI\] CLIENT_READY screen=(TitleScreen|AlphaNoticeScreen)\b/;
const serverReady = /Done \([\d.,]+s\)! For help, type "help"/;

/** Owns one launch, its deadlines and its process group. A marker alone never passes a run. */
export function monitorProcess({ kind, command, args, cwd, logFile, startupTimeoutMs,
    soakMs = 60000, shutdownTimeoutMs = 90000, onOutput = () => {}, onProgress = () => {},
    onTimeout = () => {} }) {
    mkdirSync(dirname(logFile), { recursive: true });
    writeFileSync(logFile, '');
    const started = Date.now();
    const result = { status: 'running', ready: false, stopRequested: false, timedOut: false, highlights: [] };
    return new Promise(resolveResult => {
        const child = spawn(command, args, { cwd, detached: process.platform !== 'win32', stdio: ['pipe', 'pipe', 'pipe'] });
        let timer;
        let killTimer;
        let finished = false;
        let failure;
        let stage = kind === 'build' ? 'build' : 'startup';
        const signalGroup = signal => {
            if (!child.pid) return;
            try {
                if (process.platform === 'win32') child.kill(signal);
                else process.kill(-child.pid, signal);
            } catch (error) { if (error.code !== 'ESRCH') appendFileSync(logFile, `\nCleanup: ${error.message}\n`); }
        };
        const finish = (code, signal) => {
            if (finished) return;
            finished = true;
            clearTimeout(timer);
            clearTimeout(killTimer);
            clearInterval(heartbeat);
            process.off('SIGTERM', cancelled);
            process.off('SIGINT', cancelled);
            // A launcher can exit while descendants with redirected stdio remain alive.
            if (failure) signalGroup('SIGKILL');
            result.exitCode = code;
            result.signal = signal;
            result.elapsedMs = Date.now() - started;
            result.status = 'failed';
            if (failure) result.reason = failure;
            else if (code !== 0) result.reason = `${kind} exited with ${signal ? `signal ${signal}` : `exit code ${code}`}`;
            else if (kind !== 'build' && !result.ready) result.reason = 'Process exited before readiness';
            else if (kind !== 'build' && !result.stopRequested) result.reason = 'Process exited before the planned stop';
            else { result.status = 'passed'; result.reason = 'Completed successfully'; }
            onProgress({ ...result });
            resolveResult(result);
        };
        const fail = reason => {
            if (finished || failure) return;
            failure = reason;
            clearTimeout(timer);
            signalGroup('SIGTERM');
            killTimer = setTimeout(() => signalGroup('SIGKILL'), 3000);
        };
        const deadline = (ms, label) => {
            clearTimeout(timer);
            timer = setTimeout(() => {
                result.timedOut = true;
                try { onTimeout(child.pid); }
                catch (error) { appendFileSync(logFile, `\nDiagnostics: ${error.message}\n`); }
                fail(`${label} timed out after ${Math.round(ms / 1000)} seconds`);
            }, ms);
        };
        const cancelled = () => fail('Workflow interrupted; launched process group terminated');
        process.once('SIGTERM', cancelled);
        process.once('SIGINT', cancelled);
        child.stdin.on('error', error => fail(`Console input failed: ${error.message}`));
        for (const stream of [child.stdout, child.stderr]) {
            stream.on('data', data => { appendFileSync(logFile, data); onOutput(data); });
            createInterface({ input: stream }).on('line', line => {
                if (/FAILED|Exception|Caused by:|Execution failed for task/.test(line)) {
                    result.highlights.push(line.slice(0, 1000));
                    result.highlights = result.highlights.slice(-30);
                }
                if (line.startsWith('> Task ')) result.lastTask = line;
                if (!result.ready && kind !== 'build' && (kind === 'client' ? clientReady : serverReady).test(line)) {
                    result.ready = true;
                    result.readyMs = Date.now() - started;
                    result.evidence = line;
                    stage = 'soak';
                    onProgress({ ...result });
                    if (kind === 'server') {
                        clearTimeout(timer);
                        timer = setTimeout(() => {
                            result.stopRequested = true;
                            stage = 'shutdown';
                            onProgress({ ...result });
                            child.stdin.write('stop\n');
                            deadline(shutdownTimeoutMs, 'shutdown');
                        }, soakMs);
                    } else deadline(soakMs + shutdownTimeoutMs, 'client soak/shutdown');
                }
                if (kind === 'client' && result.ready && line.includes('[WO-CI] CLIENT_STOP_REQUESTED')) {
                    result.stopRequested = true;
                    stage = 'shutdown';
                    onProgress({ ...result });
                    deadline(shutdownTimeoutMs, 'shutdown');
                }
            });
        }
        const heartbeat = setInterval(() => onOutput(Buffer.from(
            `[CI] ${kind}: ${stage}, ${Math.round((Date.now() - started) / 1000)}s elapsed${result.lastTask ? `; ${result.lastTask}` : ''}\n`
        )), 30000);
        deadline(startupTimeoutMs, stage);
        child.once('error', error => { failure = `Launcher failed: ${error.message}`; });
        child.once('exit', (code, signal) => {
            if (failure || finished) return;
            if (code !== 0) fail(`${kind} exited with ${signal ? `signal ${signal}` : `exit code ${code}`}`);
            // 'close' can be delayed indefinitely by descendants holding pipes.
            // Allow final output to drain, then clean up the owned launch group.
            else deadline(5000, 'output drain after launcher exit');
        });
        child.once('close', finish);
    });
}

function text(value) {
    return String(value ?? '').replaceAll('&', '&amp;').replaceAll('<', '&lt;').replaceAll('>', '&gt;').replaceAll('|', '&#124;').replaceAll('\n', ' ');
}

/** Reports observed phases separately, including a setup failure before the harness starts. */
export function renderReport(kind, result, setup = {}) {
    const build = result?.build;
    const runtime = result?.runtime;
    const status = phase => phase?.status === 'running' && ['failure', 'cancelled', 'skipped'].includes(setup.smoke)
        ? 'interrupted' : phase?.status;
    const startup = runtime ? (runtime.ready ? 'passed' : status(runtime) === 'passed' ? 'failed' : status(runtime)) : 'not run';
    const soak = runtime?.ready ? (runtime.stopRequested ? 'passed' : status(runtime) === 'passed' ? 'failed' : status(runtime)) : 'not run';
    const shutdown = runtime?.stopRequested ? status(runtime) : 'not run';
    const row = (label, status, detail = '') => `| ${label} | ${text(status)} | ${text(detail)} |`;
    const lines = [
        `## Minecraft ${kind} smoke test`, '',
        '| Phase | Result | Evidence |', '| --- | --- | --- |',
        ...Object.entries(setup).map(([name, status]) => row(`Setup: ${name}`, status)),
        row('Build', status(build) ?? 'not run', build?.reason),
        row('Startup', startup, runtime?.evidence ?? runtime?.reason),
        row('Soak', soak, runtime?.ready ? `Target: ${kind === 'client' ? 60 : 180} seconds` : ''),
        row('Shutdown', shutdown, runtime?.stopRequested ? runtime.reason : ''), '',
        `Commit: ${text(result?.commit ?? process.env.GITHUB_SHA ?? 'unavailable')}.`,
        `Runtime exit code: ${text(runtime?.exitCode ?? 'not available')}; signal: ${text(runtime?.signal ?? 'none')}.`,
        `Build time: ${build?.elapsedMs != null ? Math.round(build.elapsedMs / 1000) + 's' : 'not available'}; runtime: ${runtime?.elapsedMs != null ? Math.round(runtime.elapsedMs / 1000) + 's' : 'not available'}.`, '',
        kind === 'client'
            ? 'Scope: a rendered TitleScreen or the first-run AlphaNoticeScreen after the loading overlay closes, followed by 60 seconds of rendering and a clean client exit. This does not exercise world creation, multiplayer or real GPU performance.'
            : 'Scope: a disposable dedicated-server world reaches Done, runs for 180 seconds, accepts the console stop command and exits cleanly. This does not exercise player login or long-term gameplay.',
        '', 'Download the diagnostics artifact for console output, Minecraft logs, crash reports and available JUnit XML/HTML reports.',
    ];
    if (result?.error) lines.push('', `Harness error: ${text(result.error)}`);
    for (const [name, phase] of [['Build', build], ['Runtime', runtime]]) {
        if (phase?.highlights?.length) lines.push('', `### ${name} diagnostic excerpts`, '', '<pre>', phase.highlights.map(text).join('\n'), '</pre>');
    }
    return lines.join('\n') + '\n';
}

function tail(file) {
    if (!existsSync(file)) return '';
    const size = statSync(file).size;
    const buffer = Buffer.alloc(Math.min(size, 12000));
    const fd = openSync(file, 'r');
    try { readSync(fd, buffer, 0, buffer.length, Math.max(0, size - buffer.length)); }
    finally { closeSync(fd); }
    return buffer.toString('utf8').split('\n').slice(-70).map(text).join('\n');
}

function captureDiagnostics(pid, output) {
    if (process.platform !== 'linux' || !pid) return;
    const listing = spawnSync('ps', ['-eo', 'pid=,pgid=,args='], { encoding: 'utf8', timeout: 5000 });
    const owned = (listing.stdout ?? '').split('\n').filter(line => Number(line.trim().split(/\s+/)[1]) === pid);
    writeFileSync(join(output, 'processes.txt'), owned.join('\n'));
    for (const line of owned.filter(line => /\bjava\b/.test(line)).slice(0, 4)) {
        const javaPid = line.trim().split(/\s+/)[0];
        const dump = spawnSync('jcmd', [javaPid, 'Thread.print', '-l'], { encoding: 'utf8', timeout: 5000, maxBuffer: 4 * 1024 * 1024 });
        writeFileSync(join(output, `threads-${javaPid}.txt`), (dump.stdout ?? '') + (dump.stderr ?? '') + (dump.error?.message ?? ''));
    }
}

async function main() {
    const [action, requestedKind] = process.argv.slice(2);
    const kind = action === 'report' ? requestedKind : action;
    if (!['client', 'server'].includes(kind)) throw new Error('Usage: node .github/scripts/ci-smoke.mjs [report] client|server');
    const root = resolve(fileURLToPath(new URL('../..', import.meta.url)));
    const output = join(root, '.codex-build', 'reports', `ci-${kind}`);
    const resultFile = join(output, 'result.json');
    mkdirSync(output, { recursive: true });
    if (action === 'report') {
        const result = existsSync(resultFile) ? JSON.parse(readFileSync(resultFile, 'utf8')) : null;
        let report = renderReport(kind, result, JSON.parse(process.env.CI_STEP_OUTCOMES ?? '{}'));
        for (const phase of ['build', 'runtime']) {
            const excerpt = tail(join(output, `${phase}.log`));
            if (excerpt) report += `\n<details><summary>${phase} console tail</summary>\n\n<pre>\n${excerpt}\n</pre>\n</details>\n`;
        }
        writeFileSync(join(output, 'summary.md'), report);
        if (process.env.GITHUB_STEP_SUMMARY) appendFileSync(process.env.GITHUB_STEP_SUMMARY, report);
        else process.stdout.write(report);
        return;
    }
    if (process.platform !== 'linux') throw new Error('The Minecraft CI launcher targets Linux GitHub runners; use the Gradle wrapper for local development.');
    const result = { kind, commit: process.env.GITHUB_SHA, startedAt: new Date().toISOString() };
    const save = () => writeFileSync(resultFile, JSON.stringify(result, null, 2) + '\n');
    const common = ['-PcodexBuildDir=.codex-build', '--no-daemon', '--no-parallel', '--console=plain', '--stacktrace'];
    save();
    try {
        result.build = { status: 'running' };
        save();
        result.build = await monitorProcess({
            kind: 'build', command: './gradlew', args: ['build', ...common], cwd: root,
            logFile: join(output, 'build.log'), startupTimeoutMs: 25 * 60000,
            onOutput: data => process.stdout.write(data),
            onTimeout: pid => captureDiagnostics(pid, output),
            onProgress: phase => { result.build = phase; save(); },
        });
        save();
        if (result.build.status !== 'passed') { process.exitCode = 1; return; }
        const game = join(root, '.codex-build', `run-ci-${kind}`);
        mkdirSync(game, { recursive: true });
        if (kind === 'server') writeFileSync(join(game, 'eula.txt'), 'eula=true\n');
        const gradleArgs = [kind === 'client' ? 'runClient' : 'runServer', ...common, '-PciSmokeTest=true'];
        result.runtime = { status: 'running', ready: false, stopRequested: false };
        save();
        result.runtime = await monitorProcess({
            kind, command: kind === 'client' ? 'xvfb-run' : './gradlew',
            args: kind === 'client' ? ['--auto-servernum', '--server-args=-screen 0 1280x720x24', './gradlew', ...gradleArgs] : gradleArgs,
            cwd: root, logFile: join(output, 'runtime.log'),
            startupTimeoutMs: (kind === 'client' ? 20 : 15) * 60000,
            soakMs: (kind === 'client' ? 60 : 180) * 1000,
            onOutput: data => process.stdout.write(data),
            onTimeout: pid => captureDiagnostics(pid, output),
            onProgress: phase => { result.runtime = phase; save(); },
        });
        if (result.runtime.status !== 'passed') process.exitCode = 1;
    } catch (error) { result.error = error.message; process.exitCode = 1; }
    finally { save(); }
}

if (process.argv[1] && resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
    main().catch(error => { console.error(error.message); process.exitCode = 1; });
}
