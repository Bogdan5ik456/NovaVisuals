// Скін для офлайн-режиму: лаунчер створює ресурс-пак, який підміняє стандартні скіни Minecraft.
// Працює в одиночній грі та по локальній мережі. Для Microsoft-акаунта діє справжній скін акаунта.
const fs = require('fs');
const path = require('path');
const paths = require('./paths');

const skinFile = () => path.join(paths.root, 'skin.png');
const NAMES = ['alex', 'ari', 'efe', 'kai', 'makena', 'noor', 'steve', 'sunny', 'zuri'];

function setSkin(dataUrl) {
  const m = /^data:image\/png;base64,(.+)$/.exec(dataUrl || '');
  if (!m) throw new Error('Потрібен PNG-файл');
  const buf = Buffer.from(m[1], 'base64');
  if (buf.length < 33 || buf.length > 200000 || buf.readUInt32BE(1) !== 0x504e470d) throw new Error('Некоректний PNG');
  if (buf.readUInt32BE(16) !== 64 || buf.readUInt32BE(20) !== 64) throw new Error('Для гри потрібен скін 64×64');
  fs.writeFileSync(skinFile(), buf);
}
const clearSkin = () => fs.rmSync(skinFile(), { force: true });

// Додає/прибирає ресурс-пак NovaSkin в options.txt (якщо файл уже існує)
function updateOptions(inst, enable, log) {
  const f = path.join(inst, 'options.txt');
  if (!fs.existsSync(f)) { if (enable) log('Скін: увімкни ресурс-пак «NovaSkin» в Налаштування гри → Ресурс-паки (один раз).'); return; }
  const lines = fs.readFileSync(f, 'utf8').split(/\r?\n/).filter((l, i, a) => l !== '' || i < a.length - 1);
  const idx = lines.findIndex(l => l.startsWith('resourcePacks:'));
  let packs = [];
  if (idx >= 0) { try { packs = JSON.parse(lines[idx].slice('resourcePacks:'.length)); } catch { packs = []; } }
  packs = packs.filter(p => p !== 'file/NovaSkin');
  if (!packs.includes('vanilla')) packs.unshift('vanilla');
  if (enable) packs.push('file/NovaSkin');
  const line = 'resourcePacks:' + JSON.stringify(packs);
  if (idx >= 0) lines[idx] = line; else lines.push(line);
  fs.writeFileSync(f, lines.join('\n') + '\n');
}

function applySkin(version, enable, log) {
  const inst = paths.instance(version);
  const pack = path.join(inst, 'resourcepacks', 'NovaSkin');
  fs.rmSync(pack, { recursive: true, force: true });
  const use = enable && fs.existsSync(skinFile());
  if (use) {
    const png = fs.readFileSync(skinFile());
    const modern = version !== '1.18.2';
    const tex = path.join(pack, 'assets', 'minecraft', 'textures', 'entity');
    const targets = modern
      ? NAMES.flatMap(n => [path.join(tex, 'player', 'wide', n + '.png'), path.join(tex, 'player', 'slim', n + '.png')])
      : [path.join(tex, 'steve.png'), path.join(tex, 'alex.png')];
    for (const t of targets) { fs.mkdirSync(path.dirname(t), { recursive: true }); fs.writeFileSync(t, png); }
    const meta = modern
      ? { pack: { pack_format: 75, supported_formats: { min_inclusive: 8, max_inclusive: 99 }, description: 'Nova Visuals skin' } }
      : { pack: { pack_format: 8, description: 'Nova Visuals skin' } };
    fs.writeFileSync(path.join(pack, 'pack.mcmeta'), JSON.stringify(meta));
  }
  updateOptions(inst, use, log);
}

module.exports = { setSkin, clearSkin, applySkin };
