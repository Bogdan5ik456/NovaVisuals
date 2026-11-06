// Бібліотека модів і ресурс-паків: Modrinth (відкритий каталог)
const fs = require('fs');
const path = require('path');
const crypto = require('crypto');
const { download, safeName } = require('./util');

const UA = { 'User-Agent': 'NovaVisuals-Launcher/1.0' };
const FABRIC_API_ID = 'P7dR8mSH';   // Fabric API ставиться окремо, повторно не тягнемо

async function mr(p) {
  const r = await fetch('https://api.modrinth.com/v2' + p, { headers: UA });
  if (!r.ok) throw new Error('Modrinth: помилка ' + r.status);
  return r.json();
}

/** kind: 'mods' або 'packs'; mc: версія гри. Повертає до 20 результатів. */
async function search(kind, query, mc, offset) {
  if (!/^[0-9.]+$/.test(mc)) throw new Error('Невірна версія');
  const facets = [[`project_type:${kind === 'packs' ? 'resourcepack' : 'mod'}`], [`versions:${mc}`]];
  if (kind !== 'packs') facets.push(['categories:fabric']);
  const q = String(query || '').slice(0, 60);
  const url = `/search?query=${encodeURIComponent(q)}&facets=${encodeURIComponent(JSON.stringify(facets))}&limit=20&offset=${Math.max(0, +offset || 0)}&index=${q ? 'relevance' : 'downloads'}`;
  const r = await mr(url);
  return r.hits.map(h => ({ id: h.project_id, slug: h.slug, title: h.title, desc: h.description, icon: h.icon_url || '', downloads: h.downloads, author: h.author }));
}

async function latest(kind, project, mc) {
  let url = `/project/${encodeURIComponent(project)}/version?game_versions=${encodeURIComponent(JSON.stringify([mc]))}`;
  if (kind !== 'packs') url += `&loaders=${encodeURIComponent(JSON.stringify(['fabric']))}`;
  const vs = await mr(url);
  return vs[0] || null;
}

/** Завантажує проєкт у папку dir разом з обов'язковими залежностями. Повертає назви файлів. */
async function installProject(kind, project, mc, dir, opts = {}, depth = 0, seen = new Set()) {
  if (seen.has(project) || depth > 2) return [];
  seen.add(project);
  const v = await latest(kind, project, mc);
  if (!v) throw new Error(`Немає сумісної версії для Minecraft ${mc}`);
  const f = v.files.find(x => x.primary) || v.files[0];
  const name = (opts.prefix || '') + safeName(f.filename);
  const dest = path.join(dir, name);
  fs.mkdirSync(dir, { recursive: true });
  if (!fs.existsSync(dest)) {
    await download(f.url, dest);
    const want = f.hashes && f.hashes.sha512;
    if (want && crypto.createHash('sha512').update(fs.readFileSync(dest)).digest('hex') !== want) {
      fs.rmSync(dest, { force: true });
      throw new Error('Файл не пройшов перевірку цілісності: ' + name);
    }
  }
  const got = [name];
  if (kind !== 'packs') {
    for (const d of (v.dependencies || [])) {
      if (d.dependency_type === 'required' && d.project_id && d.project_id !== FABRIC_API_ID) {
        try { got.push(...await installProject(kind, d.project_id, mc, dir, {}, depth + 1, seen)); } catch { /* залежність необов'язково критична */ }
      }
    }
  }
  return got;
}

module.exports = { search, installProject };
