import { readFileSync, realpathSync, writeFileSync } from 'node:fs';
import { resolve } from 'node:path';

function isPerformanceRule(rule) {
    const category = rule.defaultConfiguration?.parameters?.codeQualityCategory;
    if (category) {
        return category === 'Performance';
    }

    // Older Qodana reports expose the inspection group through SARIF taxonomy
    // relationships instead of the code-quality category.
    return (rule.relationships ?? []).some(relationship =>
        relationship.target?.id?.split('/').includes('Performance'));
}

function tagPerformanceRules(report) {
    if (report.version !== '2.1.0' || !Array.isArray(report.runs)) {
        throw new Error('Expected a SARIF 2.1.0 report with a runs array');
    }

    let tagged = 0;
    for (const run of report.runs) {
        const components = [run.tool.driver, ...(run.tool.extensions ?? [])];
        for (const component of components) {
            for (const rule of component.rules ?? []) {
                if (!isPerformanceRule(rule)) {
                    continue;
                }
                const tags = rule.properties?.tags ?? [];
                if (!tags.includes('performance')) {
                    rule.properties ??= {};
                    rule.properties.tags = [...tags, 'performance'];
                    tagged++;
                }
            }
        }
    }
    return tagged;
}

try {
    const [input, output] = process.argv.slice(2);
    if (!input || !output || process.argv.length !== 4) {
        throw new Error('Usage: node tag-qodana-performance.mjs <input.sarif.json> <output.sarif.json>');
    }
    const inputPath = realpathSync(input);
    let outputPath = resolve(output);
    try {
        outputPath = realpathSync(outputPath);
    } catch (error) {
        if (error.code !== 'ENOENT') {
            throw error;
        }
    }
    if (inputPath === outputPath) {
        throw new Error('Input and output must use different paths to preserve the original report');
    }

    const report = JSON.parse(readFileSync(inputPath, 'utf8'));
    const tagged = tagPerformanceRules(report);
    writeFileSync(outputPath, `${JSON.stringify(report)}\n`, { encoding: 'utf8' });
    console.log(`Added performance tags to ${tagged} Qodana inspection rules.`);
} catch (error) {
    console.error(`Unable to tag Qodana report: ${error.message}`);
    process.exitCode = 1;
}
