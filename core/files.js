// Керування модами та ресурс-паками (кожна версія Minecraft має свої папки)
const fs = require('fs');
const path = require('path');
const { dialog } = require('electron');
const paths = require('./paths');
const { safeName } = require('./util');

const dirOf = (version, kind) => {
  if (!/^[0-9.]+$/.test(version)) throw new Error('Невірна версія');
  const d = kind === 'packs' ? paths.packsDir(version) : paths.modsDir(version);
  fs.mkdirSync(d, { recursive: true });
  return d;
};

function list(version, kind) {
  const d = dirOf(version, kind);
  return fs.readdirSync(d, { withFileTypes: true })
    .filter(e => (kind === 'packs' ? e.isDirectory() || /\.zip$/i.test(e.name) : /\.jar(\.disabled)?$/i.test(e.name)))
    .map(e => ({ name: e.name, enabled: !e.name.endsWith('.disabled') }));
}

async function add(win, version, kind) {
  const d = dirOf(version, kind);
  const r = await dialog.showOpenDialog(win, {
    properties: ['openFile', 'multiSelections'],
    filters: [kind === 'packs' ? { name: 'Ресурс-пак (.zip)', extensions: ['zip'] } : { name: 'Мод (.jar)', extensions: ['jar'] }]
  });
  if (r.canceled) return 0;
  r.filePaths.forEach(f => fs.copyFileSync(f, path.join(d, safeName(f))));
  return r.filePaths.length;
}

function remove(version, kind, name) {
  fs.rmSync(path.join(dirOf(version, kind), safeName(name)), { recursive: true, force: true });
}

function toggle(version, name) {   // лише для модів: .jar <-> .jar.disabled
  const d = dirOf(version, 'mods'), f = path.join(d, safeName(name));
  fs.renameSync(f, f.endsWith('.disabled') ? f.slice(0, -9) : f + '.disabled');
}

async function addOptifine(win) {
  const r = await dialog.showOpenDialog(win, { properties: ['openFile'], filters: [{ name: 'OptiFine (.jar)', extensions: ['jar'] }] });
  if (r.canceled) return null;
  const name = safeName(r.filePaths[0]);
  fs.copyFileSync(r.filePaths[0], path.join(paths.optifine, name));
  return name;
}

module.exports = { list, add, remove, toggle, addOptifine };
