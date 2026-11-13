import { createHash } from 'node:crypto';
import { cp, mkdir, readdir, readFile, rm, stat, writeFile } from 'node:fs/promises';
import { dirname, join, relative, sep } from 'node:path';
import { fileURLToPath } from 'node:url';

const root = join(dirname(fileURLToPath(import.meta.url)), '..');
const src = join(root, 'src');
const dist = join(root, 'dist');

await rm(dist, { recursive: true, force: true });
await mkdir(dist, { recursive: true });
await cp(src, dist, { recursive: true });

const hashes = await fingerprintModules(dist);
await rewriteIndex(dist, hashes);
await rewriteServiceWorker(dist, hashes);

const version = await cacheVersion(dist);
const swPath = join(dist, 'sw.js');
const sw = await readFile(swPath, 'utf8');
if (!sw.includes("'calbudget-v6'") && !sw.includes('"calbudget-v6"')) {
  throw new Error('sw.js is missing the calbudget-v6 cache name');
}
await writeFile(swPath, sw.replaceAll('calbudget-v6', `calbudget-${version}`));
console.log(`Built web/dist (cache calbudget-${version})`);

async function fingerprintModules(distDir) {
  const paths = (await collect(distDir))
    .map((file) => relative(distDir, file).split(sep).join('/'))
    .filter((file) => file !== 'sw.js' && (file.endsWith('.js') || file.endsWith('.css')));
  const original = new Map();
  const importsOf = new Map();
  for (const file of paths) {
    const text = await readFile(join(distDir, file), 'utf8');
    original.set(file, text);
    importsOf.set(file, localImports(file, text));
  }

  const hashes = new Map();
  const pending = new Set(paths);
  while (pending.size > 0) {
    let progressed = false;
    for (const file of pending) {
      const deps = importsOf.get(file);
      if (!deps.every((dep) => hashes.has(dep))) continue;
      const body = rewriteImports(file, original.get(file), hashes);
      await writeFile(join(distDir, file), body);
      hashes.set(file, digest(body));
      pending.delete(file);
      progressed = true;
    }
    if (!progressed) {
      throw new Error(`Could not fingerprint ${[...pending].join(', ')}`);
    }
  }
  return hashes;
}

function localImports(file, text) {
  const dir = file.includes('/') ? file.slice(0, file.lastIndexOf('/') + 1) : '';
  return [...text.matchAll(/from\s+['"]\.\/([^'"]+\.js)['"]/g)].map((match) => dir + match[1]);
}

function rewriteImports(file, text, hashes) {
  const dir = file.includes('/') ? file.slice(0, file.lastIndexOf('/') + 1) : '';
  return text.replace(/from\s+(['"])\.\/([^'"]+\.js)\1/g, (full, quote, name) => {
    const hash = hashes.get(dir + name);
    if (!hash) throw new Error(`${file} imports ./${name}, which has no fingerprint`);
    return `from ${quote}./${name}?v=${hash}${quote}`;
  });
}

async function rewriteIndex(distDir, hashes) {
  const indexPath = join(distDir, 'index.html');
  let html = await readFile(indexPath, 'utf8');
  html = html.replaceAll('/css/app.css', `/css/app.css?v=${hashes.get('css/app.css')}`);
  html = html.replaceAll('/js/app.js', `/js/app.js?v=${hashes.get('js/app.js')}`);
  if (!html.includes(`/css/app.css?v=${hashes.get('css/app.css')}`)) {
    throw new Error('index.html stylesheet was not fingerprinted');
  }
  if (!html.includes(`/js/app.js?v=${hashes.get('js/app.js')}`)) {
    throw new Error('index.html module was not fingerprinted');
  }
  await writeFile(indexPath, html);
}

async function rewriteServiceWorker(distDir, hashes) {
  const swPath = join(distDir, 'sw.js');
  let sw = await readFile(swPath, 'utf8');
  if (!sw.includes("cache: 'reload'") && !sw.includes('cache: "reload"')) {
    throw new Error('sw.js install must precache with cache: reload');
  }
  for (const [file, hash] of hashes) {
    const plain = `'/${file}'`;
    const fingerprinted = `'/${file}?v=${hash}'`;
    if (!sw.includes(plain)) throw new Error(`sw.js is missing ${plain}`);
    sw = sw.replaceAll(plain, fingerprinted);
  }
  await writeFile(swPath, sw);
}

async function cacheVersion(distDir) {
  const files = await collect(distDir);
  files.sort();
  const hash = createHash('sha256');
  for (const file of files) {
    hash.update(relative(distDir, file).split(sep).join('/'));
    hash.update(await readFile(file));
  }
  return hash.digest('hex').slice(0, 12);
}

function digest(text) {
  return createHash('sha256').update(text).digest('hex').slice(0, 12);
}

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
