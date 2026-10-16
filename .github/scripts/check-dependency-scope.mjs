import { readdirSync, readFileSync } from 'node:fs';
import { join, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const runtimePackages = [
    'io.netty/netty-handler',
    'org.apache.logging.log4j/log4j-core',
    'org.lz4/lz4-java',
    'com.squareup.okhttp3/okhttp-jvm',
    'org.yaml/snakeyaml',
    'com.google.code.gson/gson',
    'com.nimbusds/nimbus-jose-jwt',
    'net.caffeinemc/sodium-neoforge-mod',
];
const developmentPackages = [
    'net.neoforged/neoform-runtime',
    'org.junit.jupiter/junit-jupiter',
    'com.formdev/flatlaf',
];

/** Refuse to publish a graph that would hide known Minecraft/Aether runtime dependencies. */
export function checkDependencyScope(snapshot) {
    const dependencies = Object.values(snapshot.manifests ?? {})
        .flatMap(manifest => Object.values(manifest.resolved ?? {}));
    if (!dependencies.length) throw new Error('Dependency graph is empty.');
    const counts = { runtime: 0, development: 0 };
    for (const dependency of dependencies) {
        if (!Object.hasOwn(counts, dependency.scope)) {
            throw new Error(`Missing or invalid dependency scope: ${dependency.package_url}`);
        }
        counts[dependency.scope]++;
    }
    for (const [scope, packages] of [['runtime', runtimePackages], ['development', developmentPackages]]) {
        for (const name of packages) {
            const matches = dependencies.filter(dependency => dependency.package_url?.startsWith(`pkg:maven/${name}@`));
            if (!matches.length || !matches.some(dependency => dependency.scope === scope)
                || (scope === 'development' && matches.some(dependency => dependency.scope !== scope))) {
                throw new Error(`Expected ${scope} dependency coverage for ${name}. Review the Gradle scope filters.`);
            }
        }
    }
    return counts;
}

if (process.argv[1] && resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
    const directory = process.argv[2];
    if (!directory) throw new Error('Usage: node check-dependency-scope.mjs <report-directory>');
    const reports = readdirSync(directory).filter(name => name.endsWith('.json'));
    if (!reports.length) throw new Error('No dependency graph reports were generated.');
    for (const report of reports) {
        const counts = checkDependencyScope(JSON.parse(readFileSync(join(directory, report), 'utf8')));
        console.log(`${report}: ${counts.runtime} runtime, ${counts.development} development dependencies`);
    }
}
