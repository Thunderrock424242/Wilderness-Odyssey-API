import { appendFileSync, existsSync, readFileSync, writeFileSync } from 'node:fs';
import { join, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

function projectFile(project, files) {
    if (!/^:(?:[\w.-]+(?::[\w.-]+)*)?$/.test(project)
        || project.split(':').some(segment => segment === '.' || segment === '..')) {
        throw new Error(`Unsafe or unsupported Gradle project path: ${project}`);
    }
    const directory = project.slice(1).replaceAll(':', '/');
    return ['build.gradle', 'build.gradle.kts'].map(name => directory ? `${directory}/${name}` : name)
        .find(name => Object.hasOwn(files, name)) ?? null;
}

function dependencyPaths(configuration) {
    const nodes = new Map(configuration.allDependencies.map(dependency => [dependency.id, dependency]));
    const queue = configuration.allDependencies
        .filter(dependency => dependency.direct && dependency.origin.id === configuration.rootOrigin.id)
        .map(dependency => [dependency.id]);
    const paths = new Map();
    for (let index = 0; index < queue.length; index++) {
        const path = queue[index];
        const id = path.at(-1);
        if (paths.has(id)) continue;
        paths.set(id, path);
        for (const child of nodes.get(id)?.dependencies ?? []) {
            if (nodes.has(child) && !paths.has(child)) queue.push([...path, child]);
        }
    }
    return paths;
}

/** Explains captured resolution origins without changing GitHub's alert metadata. */
export function explainDependencies(configurations, files) {
    if (!Array.isArray(configurations) || !configurations.length) throw new Error('Dependency origins report is empty.');
    const entries = new Map();
    for (const configuration of configurations) {
        if (!['Runtime', 'Development'].includes(configuration.scope)) {
            throw new Error(`Missing dependency scope for ${configuration.configurationName}`);
        }
        const paths = dependencyPaths(configuration);
        for (const dependency of configuration.allDependencies) {
            if (dependency.project) continue;
            const scope = configuration.scope.toLowerCase();
            const entry = entries.get(dependency.id) ?? { dependency: dependency.id, scope, locations: [] };
            if (scope === 'runtime') entry.scope = scope;
            const project = dependency.origin.path;
            const file = projectFile(project, files);
            const chain = paths.get(dependency.id) ?? [];
            const source = file ? files[file].split(/\r?\n/) : [];
            let declaration;
            for (const parent of chain) {
                const coordinate = parent.split(':').slice(0, 2).join(':') + ':';
                const line = source.findIndex(text => !text.trimStart().startsWith('//')
                    && (text.includes(`'${coordinate}`) || text.includes(`"${coordinate}`)));
                if (line >= 0) { declaration = { parent, line: line + 1 }; break; }
            }
            let guidance = 'Inherited dependency: use dependencyInsight to identify the version owner before editing.';
            if (scope === 'development') guidance = 'Development tooling: review the owning build plugin or tool; excluded from runtime notifications.';
            else if (chain.some(id => /^net\.(neoforged|minecraft):/.test(id))) {
                guidance = 'Minecraft/NeoForge managed: review advisory preconditions and a compatible platform update; avoid arbitrary library overrides.';
            } else if (declaration) guidance = `Review the declared parent ${declaration.parent} and its compatible security update.`;
            const task = `${configuration.rootOrigin.path === ':' ? ':' : configuration.rootOrigin.path + ':'}dependencyInsight`;
            entry.locations.push({
                project, file, line: declaration?.line ?? null, scope,
                configuration: `${configuration.rootOrigin.path} ${configuration.configurationName}`, chain, guidance,
                command: `.\\gradlew.bat ${task} --dependency ${dependency.id} --configuration ${configuration.configurationName} '-PcodexBuildDir=.codex-build' --no-parallel`,
            });
            entries.set(dependency.id, entry);
        }
    }
    if (!entries.size) throw new Error('Dependency origins report contains no packages.');
    return [...entries.values()].sort((left, right) => left.dependency.localeCompare(right.dependency));
}

const escape = value => String(value).replaceAll('&', '&amp;').replaceAll('<', '&lt;')
    .replaceAll('>', '&gt;').replaceAll('|', '&#124;').replaceAll('\n', ' ');

export function renderDependencyReport(entries, repositoryUrl, ref, details = true) {
    const runtime = entries.filter(entry => entry.scope === 'runtime').length;
    const lines = [
        '# Dependency ownership and fix guidance', '',
        `Revision: ${escape(ref)}. ${runtime} runtime packages; ${entries.length - runtime} development packages.`, '',
        'Native alerts retain the upstream settings.gradle build-wide label. The links below identify project entry points; a line number is included only when a literal parent declaration was found. Inherited packages may be controlled by Minecraft, NeoForge, or a plugin.', '',
        'This report explains resolution and ownership. Advisory-specific exploitability still needs review.', '',
    ];
    if (!details) {
        lines.push('Download the **dependency-ownership-report** artifact for full paths, declaration lines, commands and fix guidance.', '',
            '| Package version | Scope | Owning project entry points |', '| --- | --- | --- |');
        for (const entry of entries) {
            const files = [...new Set(entry.locations.map(location => location.file).filter(Boolean))];
            const owners = files.map(file => `[${escape(file)}](${repositoryUrl}/blob/${encodeURIComponent(ref)}/${file.split('/').map(encodeURIComponent).join('/')})`);
            lines.push(`| <code>${escape(entry.dependency)}</code> | ${entry.scope} | ${owners.join(', ')} |`);
        }
        return lines.join('\n');
    }
    for (const scope of ['runtime', 'development']) {
        lines.push(`## ${scope === 'runtime' ? 'Runtime' : 'Development'} dependencies`, '');
        for (const entry of entries.filter(item => item.scope === scope)) {
            lines.push(`<details><summary>${escape(entry.dependency)}</summary>`, '');
            for (const location of entry.locations) {
                const url = location.file ? `${repositoryUrl}/blob/${encodeURIComponent(ref)}/${location.file.split('/').map(encodeURIComponent).join('/')}${location.line ? '#L' + location.line : ''}` : null;
                lines.push(`- **Owner:** ${url ? `[${escape(location.file)}${location.line ? ':' + location.line : ' (project entry point)'}](${url})` : escape(location.project)}`,
                    `- **Resolution:** ${escape(location.configuration)} (${location.scope})`,
                    `- **Path:** ${location.chain.length ? location.chain.map(id => `<code>${escape(id)}</code>`).join(' → ') : 'No complete declaration chain was captured.'}`,
                    `- **Fix route:** ${escape(location.guidance)}`, '', '```powershell', location.command, '```', '');
            }
            lines.push('</details>', '');
        }
    }
    return lines.join('\n');
}

if (process.argv[1] && resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
    const directory = process.argv[2];
    if (!directory) throw new Error('Usage: node dependency-report.mjs <origin-report-directory>');
    const graph = JSON.parse(readFileSync(join(directory, 'dependency-graph.json'), 'utf8'));
    const files = {};
    for (const configuration of graph) {
        for (const dependency of configuration.allDependencies) {
            projectFile(dependency.origin.path, files); // Validate before using any project path as a filesystem path.
            const prefix = dependency.origin.path.slice(1).replaceAll(':', '/');
            for (const name of ['build.gradle', 'build.gradle.kts']) {
                const file = prefix ? `${prefix}/${name}` : name;
                if (existsSync(file) && !Object.hasOwn(files, file)) files[file] = readFileSync(file, 'utf8');
            }
        }
    }
    const entries = explainDependencies(graph, files);
    const repository = `https://github.com/${process.env.GITHUB_REPOSITORY ?? 'Thunderrock424242/Wilderness-Odyssey-API'}`;
    const revision = process.env.GITHUB_SHA ?? 'in-dev';
    const report = renderDependencyReport(entries, repository, revision);
    const summary = renderDependencyReport(entries, repository, revision, false);
    writeFileSync(join(directory, 'dependency-report.json'), JSON.stringify(entries, null, 2) + '\n');
    writeFileSync(join(directory, 'dependency-report.md'), report);
    writeFileSync(join(directory, 'dependency-summary.md'), summary);
    if (process.env.GITHUB_STEP_SUMMARY) appendFileSync(process.env.GITHUB_STEP_SUMMARY, summary);
    console.log(`Explained ${entries.length} dependency versions in ${directory}/dependency-report.md`);
}
