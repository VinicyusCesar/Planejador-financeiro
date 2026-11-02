import http from 'node:http';
import { readFile, stat } from 'node:fs/promises';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '../dist/frontend/browser');
const port = Number(process.env.PORT || 4200);
const target = new URL(process.argv[2] || process.env.API_TARGET || 'http://127.0.0.1:8080');
if (target.protocol !== 'http:' || !['localhost', '127.0.0.1'].includes(target.hostname))
  throw new Error('A prévia aceita apenas uma API HTTP local.');
const mime = {
  '.html': 'text/html; charset=utf-8',
  '.js': 'text/javascript; charset=utf-8',
  '.css': 'text/css; charset=utf-8',
  '.svg': 'image/svg+xml',
  '.woff2': 'font/woff2',
  '.ico': 'image/x-icon',
  '.png': 'image/png',
};
const server = http.createServer(async (request, response) => {
  const url = new URL(request.url || '/', 'http://127.0.0.1');
  if (url.pathname.startsWith('/api/')) {
    const headers = { ...request.headers, host: target.host };
    // O proxy representa requisições da própria prévia; outras origens permanecem
    // intactas para que a política CORS do backend continue sendo aplicada.
    if (request.headers.origin === 'http://' + request.headers.host) headers.origin = target.origin;
    const proxy = http.request(
      new URL(url.pathname + url.search, target),
      {
        method: request.method,
        headers,
        timeout: 10000,
      },
      (result) => {
        response.writeHead(result.statusCode || 502, result.headers);
        result.pipe(response);
      },
    );
    proxy.on('timeout', () => proxy.destroy());
    proxy.on('error', () => {
      if (!response.headersSent) response.writeHead(502, { 'content-type': 'application/json' });
      response.end(JSON.stringify({ mensagem: 'O backend local não está disponível.' }));
    });
    request.pipe(proxy);
    return;
  }
  if (!['GET', 'HEAD'].includes(request.method || '')) {
    response.writeHead(405).end();
    return;
  }
  try {
    const filename = path.resolve(root, '.' + decodeURIComponent(url.pathname));
    if (!filename.startsWith(root + path.sep) && filename !== root) {
      response.writeHead(403).end();
      return;
    }
    let file = filename;
    try {
      if (!(await stat(file)).isFile()) file = path.join(root, 'index.html');
    } catch {
      file = path.join(root, 'index.html');
    }
    const body = await readFile(file);
    response.writeHead(200, {
      'content-type': mime[path.extname(file)] || 'application/octet-stream',
      'cache-control': 'no-store',
    });
    response.end(request.method === 'HEAD' ? undefined : body);
  } catch {
    response.writeHead(500).end('Não foi possível servir o build. Execute npm run build.');
  }
});
server.listen(port, '127.0.0.1', () =>
  process.stdout.write('Prévia local: http://127.0.0.1:' + port + '\n'),
);
for (const signal of ['SIGINT', 'SIGTERM'])
  process.on(signal, () => server.close(() => process.exit(0)));
