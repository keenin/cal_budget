import { createHash } from 'node:crypto';
import { cp, mkdir, readdir, readFile, rm, stat, writeFile } from 'node:fs/promises';
import { dirname, join, relative } from 'node:path';
import { fileURLToPath } from 'node:url';

const root = join(dirname(fileURLToPath(import.meta.url)), '..');
const src = join(root, 'src');
const dist = join(root, 'dist');

await rm(dist, { recursive: true, force: true });
await mkdir(dist, { recursive: true });
await cp(src, dist, { recursive: true });

const files = await collect(dist);
files.sort();
const hash = createHash('sha256');
for (const file of files) {
  hash.update(relative(dist, file));
  hash.update(await readFile(file));
}
const version = hash.digest('hex').slice(0, 12);
const swPath = join(dist, 'sw.js');
const sw = await readFile(swPath, 'utf8');
if (!sw.includes("'calbudget-v4'") && !sw.includes('"calbudget-v4"')) {
  throw new Error('sw.js is missing the calbudget-v4 cache name');
}
await writeFile(swPath, sw.replaceAll('calbudget-v4', `calbudget-${version}`));
console.log(`Built web/dist (cache calbudget-${version})`);

async function collect(dir) {
  const entries = await readdir(dir);
  const files = [];
  for (const entry of entries) {
    const path = join(dir, entry);
    const info = await stat(path);
    if (info.isDirectory()) files.push(...await collect(path));
    else files.push(path);
  }
  return files;
}
