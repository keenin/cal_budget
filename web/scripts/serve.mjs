import { createServer } from 'node:http';
import { readFile } from 'node:fs/promises';
import { dirname, extname, join, sep } from 'node:path';
import { fileURLToPath } from 'node:url';

const root = join(dirname(fileURLToPath(import.meta.url)), '..', 'dist');
const port = Number(process.env.PORT || 4173);
const types = {
  '.html': 'text/html; charset=utf-8',
  '.js': 'text/javascript; charset=utf-8',
  '.css': 'text/css; charset=utf-8',
  '.json': 'application/json; charset=utf-8',
  '.webmanifest': 'application/manifest+json; charset=utf-8',
  '.png': 'image/png',
  '.svg': 'image/svg+xml',
  '.txt': 'text/plain; charset=utf-8',
};

createServer(async (request, response) => {
  const url = new URL(request.url || '/', `http://${request.headers.host || 'localhost'}`);
  let pathname = decodeURIComponent(url.pathname);
  if (pathname === '/') pathname = '/index.html';
  const rel = pathname.replace(/^\/+/, '');
  if (rel.includes('\0') || rel.split('/').includes('..')) {
    response.writeHead(403);
    response.end();
    return;
  }
  const file = join(root, rel);
  if (!file.startsWith(root + sep)) {
    response.writeHead(403);
    response.end();
    return;
  }
  try {
    const body = await readFile(file);
    const type = types[extname(file)] || 'application/octet-stream';
    const headers = { 'Content-Type': type, 'Cache-Control': 'no-cache' };
    response.writeHead(200, headers);
    response.end(body);
  } catch {
    response.writeHead(404, { 'Content-Type': 'text/plain; charset=utf-8' });
    response.end('Not found');
  }
}).listen(port, () => {
  console.log(`Cal Budget web at http://localhost:${port}`);
});
