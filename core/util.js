// Допоміжні функції: завантаження файлів із прогресом
const fs = require('fs');
const path = require('path');

async function download(url, dest, onProgress) {
  const res = await fetch(url, { redirect: 'follow' });
  if (!res.ok) throw new Error(`Помилка завантаження (${res.status}): ${url}`);
  const total = Number(res.headers.get('content-length')) || 0;
  fs.mkdirSync(path.dirname(dest), { recursive: true });
  const tmp = dest + '.part';
  const out = fs.createWriteStream(tmp);
  let got = 0;
  const reader = res.body.getReader();
  for (;;) {
    const { done, value } = await reader.read();
    if (done) break;
    out.write(Buffer.from(value));
    got += value.length;
    if (onProgress && total) onProgress(got / total);
  }
  await new Promise((ok, fail) => out.end(e => (e ? fail(e) : ok())));
  fs.renameSync(tmp, dest);
}

// Безпечне ім'я файлу (без шляхів)
const safeName = (n) => path.basename(String(n));

// true, якщо є інтернет (швидка перевірка, 4 секунди)
async function online() {
  const c = new AbortController();
  const t = setTimeout(() => c.abort(), 4000);
  try { await fetch('https://launchermeta.mojang.com/mc/game/version_manifest.json', { method: 'HEAD', signal: c.signal }); return true; }
  catch { return false; }
  finally { clearTimeout(t); }
}

module.exports = { download, safeName, online };
