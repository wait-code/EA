import fs from 'node:fs';
import path from 'node:path';
const p = process.argv[2];
const txt = fs.readFileSync(p, 'utf8');
const lines = txt.split(/\r?\n/);
let cur = null, buf = [], count = 0;
const flush = () => {
  if (!cur) return;
  const full = path.join(process.argv[3] || '.', cur);
  fs.mkdirSync(path.dirname(full), { recursive: true });
  let body = buf.join('\n');
  fs.writeFileSync(full, body.endsWith('\n') ? body : body + '\n', 'utf8');
  count++;
  console.log('wrote ' + full + ' (' + body.length + ' chars)');
};
for (const line of lines) {
  const m = line.match(/^@@FILE (.+)$/);
  if (m) { flush(); cur = m[1].trim(); buf = []; }
  else if (cur) buf.push(line);
}
flush();
console.log('total files: ' + count);