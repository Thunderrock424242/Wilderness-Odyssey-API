import { readFileSync } from 'node:fs';

try {
    const [input] = process.argv.slice(2);
    if (!input || process.argv.length !== 3) {
        throw new Error('Usage: node check-qodana-health.mjs <report.sarif.json>');
    }
    const report = JSON.parse(readFileSync(input, 'utf8'));
    if (report.version !== '2.1.0' || !Array.isArray(report.runs) || report.runs.length === 0) {
        throw new Error('Expected a nonempty SARIF 2.1.0 report');
    }

    const problems = [];
    for (const run of report.runs) {
        if (!Array.isArray(run.invocations) || run.invocations.length === 0) {
            problems.push('The report contains no invocation evidence for an analysis run.');
        }
        for (const invocation of run.invocations ?? []) {
            if (invocation.executionSuccessful !== true) {
                problems.push('A Qodana invocation did not report successful execution.');
            }
            for (const notification of invocation.toolExecutionNotifications ?? []) {
                const message = notification.message?.text ?? '';
                // Qodana can return success while its imported project is missing
                // generated libraries or its Java sanity inspection has stopped.
                if (/Roots (?:jar|file):.*were not resolved/i.test(message)
                        || /Analysis by sanity inspection .*was suspended/i.test(message)) {
                    problems.push(message);
                }
            }
        }
    }
    if (problems.length > 0) {
        throw new Error(problems.join('\n'));
    }
    console.log('No unresolved library roots or suspended sanity analysis reported by Qodana.');
} catch (error) {
    console.error(`Qodana analysis health check failed:\n${error.message}`);
    process.exitCode = 1;
}
