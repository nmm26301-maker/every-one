import fs from 'node:fs';
import path from 'node:path';
const packagePath = process.env.CODEX_PRIMARY_RUNTIME_NODE_MODULES
  ? `${process.env.CODEX_PRIMARY_RUNTIME_NODE_MODULES}/marked/lib/marked.esm.js`
  : 'marked';
const { marked } = await import(packagePath);

const assets = path.resolve('app/src/main/assets');
const book = path.join(assets, 'book');
const names = ['README','chapter1','chapter2','chapter3','chapter4','chapter5','chapter6','chapter7','chapter8','end'];
const escape = s => s.replaceAll('&','&amp;').replaceAll('<','&lt;').replaceAll('>','&gt;');
const css = `<meta name="viewport" content="width=device-width,initial-scale=1"><meta charset="utf-8"><style>
body{margin:0;padding:22px 20px 70px;background:#fffbf7;color:#302b44;font:17px/1.85 system-ui,-apple-system,sans-serif;overflow-wrap:anywhere}
h1,h2,h3{line-height:1.35;color:#583c93;letter-spacing:.01em}h1{font-size:28px;margin:10px 0 30px}h2{font-size:23px;margin-top:2em}h3{font-size:20px}
p{margin:1.1em 0}a{color:#7255b5}blockquote{border-left:3px solid #d5c2ef;padding:2px 0 2px 14px;margin:20px 0;color:#55506b;background:#f8f1fc}
img{max-width:100%;height:auto;display:block;margin:14px auto;border-radius:10px}pre{white-space:pre-wrap;background:#f3eef8;padding:12px;overflow-wrap:anywhere}
table{display:block;overflow:auto}td,th{border:1px solid #e0d7e7;padding:7px}hr{border:0;border-top:1px solid #e7dbea;margin:2em 0}
</style>`;
for (const name of names) {
  let md = fs.readFileSync(path.join(book, `${name}.md`),'utf8');
  md = md.replace(/\]\((README|chapter[1-8]|end)\.md(#[^)]*)?\)/g, (_,n,hash='') => `](${n}.html${hash})`);
  md = md.replace(/\]\((files\/[^)]*)\)/g, (_,p) => `](https://github.com/ZuodaoTech/everyone-can-use-english/blob/main/book/${p})`);
  const html = `<!doctype html><html lang="zh-CN"><head>${css}</head><body>${marked.parse(md)}</body></html>`;
  fs.writeFileSync(path.join(book,`${name}.html`), html);
}
const license = fs.readFileSync(path.join(assets,'LICENSE'),'utf8');
fs.writeFileSync(path.join(assets,'license.html'), `<!doctype html><html><head>${css}</head><body><h1>GPL-3.0 License</h1><pre>${escape(license)}</pre></body></html>`);
