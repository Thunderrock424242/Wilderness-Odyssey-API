import assert from 'node:assert/strict';
import test from 'node:test';
import { checkDependencyScope } from './check-dependency-scope.mjs';

function snapshot() {
    const resolved = {};
    for (const [name, scope] of [
        ['io.netty/netty-handler@4.1.97.Final', 'runtime'],
        ['org.apache.logging.log4j/log4j-core@2.22.1', 'runtime'],
        ['org.lz4/lz4-java@1.8.0', 'runtime'],
        ['com.squareup.okhttp3/okhttp-jvm@5.5.0', 'runtime'],
        ['org.yaml/snakeyaml@2.7', 'runtime'],
        ['com.google.code.gson/gson@2.10.1', 'runtime'],
        ['com.google.code.gson/gson@2.14.0', 'runtime'],
        ['com.nimbusds/nimbus-jose-jwt@10.10', 'runtime'],
        ['net.caffeinemc/sodium-neoforge-mod@0.8.12', 'runtime'],
        ['net.neoforged/neoform-runtime@2.0.31', 'development'],
        ['org.junit.jupiter/junit-jupiter@5.14.4', 'development'],
        ['com.formdev/flatlaf@3.7.2', 'development'],
        ['org.apache.commons/commons-compress@1.18', 'development'],
        ['org.apache.commons/commons-compress@1.26.0', 'runtime'],
    ]) resolved[name] = { package_url: `pkg:maven/${name}`, scope };
    return { manifests: { gradle: { resolved } } };
}

test('preserves Minecraft, Aether and client runtime coverage while separating development tools', () => {
    assert.deepEqual(checkDependencyScope(snapshot()), { runtime: 10, development: 4 });
});

test('rejects unscoped snapshots produced without the scope configuration', () => {
    const input = snapshot();
    for (const dependency of Object.values(input.manifests.gradle.resolved)) delete dependency.scope;
    assert.throws(() => checkDependencyScope(input), /scope/i);
});

for (const name of ['io.netty/netty-handler@4.1.97.Final', 'com.nimbusds/nimbus-jose-jwt@10.10',
    'net.caffeinemc/sodium-neoforge-mod@0.8.12']) {
    test(`rejects accidental suppression of ${name}`, () => {
        const input = snapshot();
        input.manifests.gradle.resolved[name].scope = 'development';
        assert.throws(() => checkDependencyScope(input), /runtime/i);
    });
}

test('rejects a viewer dependency accidentally classified as runtime', () => {
    const input = snapshot();
    input.manifests.gradle.resolved['com.formdev/flatlaf@3.7.2'].scope = 'runtime';
    assert.throws(() => checkDependencyScope(input), /development/i);
});

test('rejects empty reports instead of submitting an empty replacement graph', () => {
    assert.throws(() => checkDependencyScope({ manifests: {} }), /empty/i);
});
