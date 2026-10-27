import { readFile, readdir, access } from 'node:fs/promises';
import { createHash } from 'node:crypto';

const expectedContract = '800a25b92e7272a2e02ea1d08b29612ad58d28d0db85dea9743c25257537e22c';
const contract = await readFile(new URL('../../contracts/http/public-v0.1.yaml', import.meta.url));
if (createHash('sha256').update(contract).digest('hex') !== expectedContract) {
  throw new Error(
    'Approved public v0.1 changed; review contract separately before updating its baseline.',
  );
}
const dir = new URL('../dist/assets/', import.meta.url);
for (const file of await readdir(dir)) {
  if (!file.endsWith('.js')) continue;
  const source = await readFile(new URL(file, dir), 'utf8');
  for (const marker of [
    'lt:demo:v1',
    'hung.demo@example.com',
    'LearningDemo2026!',
    'MOCK / DEMO',
  ]) {
    if (source.includes(marker))
      throw new Error(`Production bundle contains demo marker: ${marker}`);
  }
}
let workerExists = false;
try {
  await access(new URL('../dist/mockServiceWorker.js', import.meta.url));
  workerExists = true;
} catch {
  /* expected */
}
if (workerExists) throw new Error('Production output must not contain the mock service worker.');
console.log(
  'PASS: approved contract unchanged; production output excludes demo fixtures, credentials and worker.',
);
