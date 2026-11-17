// Імпорт своєї косметики (крила й капелюхи) із ZIP у папку config/novavisuals/cosmetics потрібної версії гри.
// Мод підхоплює файли wings_*.png й hat_*.png сам при наступному запуску гри.
const fs = require('fs');
const path = require('path');
const { dialog, shell } = require('electron');
const AdmZip = require('adm-zip');
const paths = require('./paths');

const MAX_FILES = 40, MAX_ONE = 4 * 1024 * 1024, MAX_TOTAL = 60 * 1024 * 1024, MAX_SIDE = 2048;
const ver = (v) => (/^[0-9.]+$/.test(v) ? v : '1.18.2');
const folder = (v) => path.join(paths.instance(ver(v)), 'config', 'novavisuals', 'cosmetics');

// Із назви файлу й папки в архіві визначаємо тип: wings_Назва.png або hat_Назва.png
function targetName(entryName) {
  const parts = entryName.replace(/\\/g, '/').split('/').filter(Boolean);
  const base = parts[parts.length - 1];
  if (!/\.png$/i.test(base)) return null;
  const stem = base.slice(0, -4);
  const dir = (parts[parts.length - 2] || '').toLowerCase();
  let m = /^(wings?|крила)[_\- ](.+)$/i.exec(stem);
  if (m) return 'wings_' + clean(m[2]);
  m = /^(hats?|капелюх)[_\- ](.+)$/i.exec(stem);
  if (m) return 'hat_' + clean(m[2]);
  if (/^(wings?|крила)$/.test(dir)) return 'wings_' + clean(stem);
  if (/^(hats?|капелюхи|капелюх)$/.test(dir)) return 'hat_' + clean(stem);
  return null;
}
const clean = (s) => String(s).replace(/[^\p{L}\p{N}_ .-]/gu, '_').replace(/\s+/g, '_').replace(/^[._]+/, '').slice(0, 40) || 'my';

function pngOk(buf) {
  if (buf.length < 33 || buf.readUInt32BE(0) !== 0x89504e47 || buf.readUInt32BE(4) !== 0x0d0a1a0a) return false;
  const w = buf.readUInt32BE(16), h = buf.readUInt32BE(20);
  return w >= 8 && h >= 8 && w <= MAX_SIDE && h <= MAX_SIDE;
}

async function importZip(win, version) {
  const r = await dialog.showOpenDialog(win, { properties: ['openFile'], filters: [{ name: 'Косметика (.zip)', extensions: ['zip'] }] });
  if (r.canceled || !r.filePaths[0]) return { added: 0, skipped: 0, names: [], canceled: true };
  if (fs.statSync(r.filePaths[0]).size > 80 * 1024 * 1024) throw new Error('ZIP завеликий (понад 80 МБ).');
  let zip;
  try { zip = new AdmZip(r.filePaths[0]); } catch { throw new Error('Це не схоже на ZIP-архів, або він пошкоджений.'); }
  const dir = folder(version);
  fs.mkdirSync(dir, { recursive: true });
  const names = []; let skipped = 0, total = 0;
  for (const e of zip.getEntries()) {
    if (e.isDirectory) continue;
    const tn = targetName(e.entryName);
    if (!tn) { if (/\.png$/i.test(e.entryName)) skipped++; continue; }
    if (names.length >= MAX_FILES || e.header.size > MAX_ONE || total + e.header.size > MAX_TOTAL) { skipped++; continue; }
    const data = e.getData();
    if (!pngOk(data)) { skipped++; continue; }
    total += data.length;
    fs.writeFileSync(path.join(dir, tn + '.png'), data);   // лише ім'я файлу, без шляхів з архіву
    names.push(tn);
  }
  return { added: names.length, skipped, names };
}

function openFolder(version) {
  const d = folder(version);
  fs.mkdirSync(d, { recursive: true });
  return shell.openPath(d);
}

module.exports = { importZip, openFolder };
