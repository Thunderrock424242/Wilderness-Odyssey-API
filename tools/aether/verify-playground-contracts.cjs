// Validate actual Java HTTP fixtures against the verbatim pinned bot Zod contracts.
// Usage: node tools/aether/verify-playground-contracts.cjs <peer-roundtrip.json> <bot-node_modules>
const fs = require('node:fs');
const path = require('node:path');
const assert = require('node:assert/strict');
const dependencies = path.resolve(process.argv[3]);
const ts = require(path.join(dependencies, 'typescript'));
const zod = require(path.join(dependencies, 'zod'));
const root = path.resolve(__dirname, '../../docs/ai/contracts/60a9620/src');
const cache = new Map();
function load(file) {
  file = path.resolve(file);
  if (file===path.join(root,'connected/store.ts')) file=path.resolve(root,'../test/service-error.ts');
  if (cache.has(file)) return cache.get(file);
  const exports = {};
  cache.set(file, exports);
  const code = ts.transpileModule(fs.readFileSync(file, 'utf8'), {
    compilerOptions: { module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2022 },
  }).outputText;
  const localRequire = name => name === 'zod' ? zod : name.startsWith('node:') ? require(name) : load(path.resolve(path.dirname(file), name + '.ts'));
  new Function('require', 'exports', code)(localRequire, exports);
  return exports;
}
const peer = load(path.join(root, 'contracts/v1/aether-playground.ts'));
const admin = load(path.join(root, 'contracts/v1/admin.ts'));
const connected = load(path.join(root, 'connected/contracts.ts'));
const fixtures = JSON.parse(fs.readFileSync(process.argv[2], 'utf8'));
for (const [key, schema] of Object.entries({capabilities: peer.peerCapabilitiesSchema, sandbox: peer.sandboxOutput,
  cancellation: peer.peerCancellationSchema, cancelledJob: peer.peerJobSchema, health: connected.observationSchema, operation: admin.operationSchema})) {
  assert.deepEqual(schema.parse(fixtures[key]), fixtures[key], key + ' must round-trip without dropped fields');
}
admin.modelsSchema.parse(fixtures.serviceCapabilities.models);
assert.equal(fixtures.serviceCapabilities.server_id, fixtures.capabilities.serverId);
assert.deepEqual(fixtures.serviceCapabilities.actions, ['ai.pause']);
assert.equal(peer.peerCancellationSchema.safeParse({...fixtures.cancellation, unexpected: true}).success, false);
assert.equal(peer.peerCapabilitiesSchema.safeParse({...fixtures.capabilities, unexpected: true}).success, false);
assert.equal(peer.sandboxOutput.safeParse({...fixtures.sandbox, unexpected: true}).success, false);
const job = {schemaVersion:'1.0',serverId:fixtures.capabilities.serverId,requestId:fixtures.sandbox.requestId,
  jobId:'job-fixture',baseModelId:fixtures.sandbox.modelId,presetId:'lora-smoke',datasetHash:'f'.repeat(64),
  state:'queued',createdAt:new Date().toISOString(),updatedAt:new Date().toISOString(),progress:null,candidateModelId:null};
assert.deepEqual(peer.peerJobSchema.parse(job), job);
assert.equal(peer.peerJobSchema.safeParse({...job,state:'completed'}).success,false);
assert.equal(peer.peerJobSchema.safeParse({...job,state:'completed',candidateModelId:'candidate-fixture'}).success,true);
if (process.argv[4]) {
  const completed=JSON.parse(fs.readFileSync(process.argv[4],'utf8'));
  assert.deepEqual(peer.peerJobSchema.parse(completed),completed);
  assert.equal(completed.state,'completed');
}
console.log('Pinned 60a9620 peer, cancellation, observation, operation and models schemas: PASS');

async function verifyPinnedClient() {
  const {AetherGatewayClient}=load(path.join(root,'connected/aetherGatewayClient.ts'));
  let reads=0,writes=0;
  const client=new AetherGatewayClient({origin:'https://fixture.invalid',serverId:fixtures.capabilities.serverId,
    monitoringToken:'m'.repeat(32),administrationToken:'a'.repeat(32),environment:'test'},async(url,init)=>{
      const route=new URL(String(url)).pathname;
      assert.equal(init.redirect,'error');
      if(route.endsWith('/capabilities'))return Response.json(fixtures.capabilities);
      assert.equal(new Headers(init.headers).get('authorization'),'Bearer '+'a'.repeat(32));
      if(route.endsWith('/cancel')) { writes++;return Response.json(fixtures.cancellation); }
      reads++;return Response.json(fixtures.cancelledJob);
    });
  assert.deepEqual(await client.cancelTraining(fixtures.cancellation.requestId),fixtures.cancellation);
  assert.deepEqual(await client.trainingStatus(fixtures.cancelledJob.requestId),fixtures.cancelledJob);
  assert.equal(reads,1);assert.equal(writes,1);
  console.log('Pinned AetherGatewayClient durable cancellation receipt and original-ID reconciliation: PASS');
}
verifyPinnedClient().catch(error=>{console.error(error);process.exitCode=1;});
