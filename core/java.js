// Пошук і завантаження Java (Eclipse Temurin з api.adoptium.net)
const fs = require('fs');
const path = require('path');
const AdmZip = require('adm-zip');
const paths = require('./paths');
const { download } = require('./util');

function findBin(dir, name) {
  for (const e of fs.readdirSync(dir, { withFileTypes: true })) {
    const p = path.join(dir, e.name);
    if (e.isDirectory()) { const r = findBin(p, name); if (r) return r; }
    else if (e.name.toLowerCase() === name) return p;
  }
  return null;
}

// feature: 17 для Minecraft 1.18.2, 21 для 1.21.x
async function ensureJava(feature, onProgress) {
  const dir = path.join(paths.runtime, 'java' + feature);
  const done = path.join(dir, '.ok');
  if (!fs.existsSync(done)) {
    fs.rmSync(dir, { recursive: true, force: true });
    const zip = path.join(paths.cache, `java${feature}.zip`);
    const url = `https://api.adoptium.net/v3/binary/latest/${feature}/ga/windows/x64/jre/hotspot/normal/eclipse`;
    onProgress(`Завантаження Java ${feature}…`, 0);
    await download(url, zip, p => onProgress(`Завантаження Java ${feature}…`, Math.round(p * 100)));
    onProgress(`Розпакування Java ${feature}…`, 100);
    new AdmZip(zip).extractAllTo(dir, true);
    fs.unlinkSync(zip);
    fs.writeFileSync(done, 'ok');
  }
  const java = findBin(dir, 'java.exe');
  const javaw = findBin(dir, 'javaw.exe') || java;
  if (!java) throw new Error('Java не знайдено після розпакування');
  return { java, javaw };
}

module.exports = { ensureJava };
