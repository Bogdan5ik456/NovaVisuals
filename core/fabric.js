// Fabric: завантаження завантажувача, Fabric API, вбудований мод Nova Visuals, FPS-мод
const fs = require('fs');
const path = require('path');
const crypto = require('crypto');
const { app } = require('electron');
const paths = require('./paths');
const { download } = require('./util');
const lib = require('./library');

const UA = { 'User-Agent': 'NovaVisuals-Launcher/1.0' };
const NOVA_JAR = 'NovaVisuals-mod.jar';
const SYSTEM_RE = /^(novavisuals-mod\.jar|nova-fabricapi-|nova-fps-)/i;   // файли, якими керує лаунчер

const isSystem = (name) => SYSTEM_RE.test(String(name));

async function getJson(url) {
  const r = await fetch(url, { headers: UA });
  if (!r.ok) throw new Error(`Помилка ${r.status}: ${url}`);
  return r.json();
}

const mavenPath = (name) => {
  const [g, a, v, c] = name.split(':');
  return `${g.replace(/\./g, '/')}/${a}/${v}/${a}-${v}${c ? '-' + c : ''}.jar`;
};

function installedFabric(mc) {
  const d = path.join(paths.root, 'versions');
  if (!fs.existsSync(d)) return null;
  return fs.readdirSync(d)
    .filter(n => n.startsWith('fabric-loader-') && n.endsWith('-' + mc) && fs.existsSync(path.join(d, n, '.nova-ok')))
    .sort().pop() || null;
}

/** Ставить Fabric для версії mc, повертає назву версії для запуску. Працює офлайн, якщо вже встановлено. */
async function ensureFabric(mc, send) {
  let loaderVer;
  try {
    const meta = await getJson(`https://meta.fabricmc.net/v2/versions/loader/${mc}`);
    const pick = meta.find(x => x.loader && x.loader.stable) || meta[0];
    if (!pick) throw new Error('Fabric для Minecraft ' + mc + ' недоступний');
    loaderVer = pick.loader.version;
  } catch (e) {
    const have = installedFabric(mc);
    if (have) return have;
    throw e;
  }
  const id = `fabric-loader-${loaderVer}-${mc}`;
  const dir = path.join(paths.root, 'versions', id);
  const marker = path.join(dir, '.nova-ok');
  if (fs.existsSync(marker)) return id;

  send('progress', { text: 'Завантаження Fabric…', percent: 0 });
  const profile = await getJson(`https://meta.fabricmc.net/v2/versions/loader/${mc}/${loaderVer}/profile/json`);
  const libs = profile.libraries || [];
  let i = 0;
  for (const l of libs) {
    const rel = mavenPath(l.name);
    const base = l.url || 'https://libraries.minecraft.net/';
    const dest = path.join(paths.root, 'libraries', rel);
    if (!fs.existsSync(dest)) await download(base + rel, dest);
    const buf = fs.readFileSync(dest);
    // записуємо бібліотеку у форматі, який розуміє рушій запуску (шлях, адреса, хеш)
    l.downloads = { artifact: { path: rel, url: base + rel, sha1: crypto.createHash('sha1').update(buf).digest('hex'), size: buf.length } };
    ['url', 'sha1', 'sha256', 'sha512', 'md5', 'size'].forEach(k => delete l[k]);
    send('progress', { text: 'Завантаження Fabric…', percent: Math.round(++i / libs.length * 100) });
  }
  fs.mkdirSync(dir, { recursive: true });
  fs.writeFileSync(path.join(dir, id + '.json'), JSON.stringify(profile));
  fs.writeFileSync(marker, 'ok');
  return id;
}

// ---------- мод Nova Visuals (лежить усередині лаунчера) ----------
function novaSource(mc) {
  const name = `NovaVisuals-fabric-${mc}.jar`;
  return app.isPackaged ? path.join(process.resourcesPath, 'mod', name) : path.join(__dirname, '..', 'mod-dist', name);
}

function rmForce(f) {
  try { fs.chmodSync(f, 0o666); } catch {}
  fs.rmSync(f, { force: true });
}

function removeNovaCopies(mods, keepCurrent) {
  if (!fs.existsSync(mods)) return;
  fs.readdirSync(mods)
    .filter(f => /^novavisuals.*\.jar(\.disabled)?$/i.test(f) && !(keepCurrent && f === NOVA_JAR))
    .forEach(f => rmForce(path.join(mods, f)));
}

/** Кладе мод у папку mods. Файл лише для читання, а при кожному запуску лаунчер повертає його назад. */
function installNovaMod(mc) {
  const src = novaSource(mc);
  if (!fs.existsSync(src)) throw new Error(`Мод Nova Visuals для Minecraft ${mc} ще не готовий.`);
  const mods = paths.modsDir(mc);
  fs.mkdirSync(mods, { recursive: true });
  removeNovaCopies(mods, true);
  const dest = path.join(mods, NOVA_JAR);
  try { fs.chmodSync(dest, 0o666); } catch {}
  fs.copyFileSync(src, dest);
  try { fs.chmodSync(dest, 0o444); } catch {}
}

function removeNovaMod(mc) { removeNovaCopies(paths.modsDir(mc), false); }

function novaStatus(mc) {
  return { bundled: fs.existsSync(novaSource(mc)), installed: fs.existsSync(path.join(paths.modsDir(mc), NOVA_JAR)) };
}

// ---------- Fabric API (обов'язковий) і Sodium (FPS) ----------
async function ensureFabricApi(mc, send) {
  const mods = paths.modsDir(mc);
  fs.mkdirSync(mods, { recursive: true });
  if (fs.readdirSync(mods).some(f => /^(nova-fabricapi-|fabric-api)/i.test(f))) return;
  send('progress', { text: 'Завантаження Fabric API…', percent: 0 });
  await lib.installProject('mods', 'fabric-api', mc, mods, { prefix: 'nova-fabricapi-' });
}

async function ensureFpsMod(mc, send) {
  const mods = paths.modsDir(mc);
  fs.mkdirSync(mods, { recursive: true });
  if (fs.readdirSync(mods).some(f => f.startsWith('nova-fps-'))) return;
  try {
    send('progress', { text: 'Завантаження FPS-моду (Sodium)…', percent: 0 });
    await lib.installProject('mods', 'sodium', mc, mods, { prefix: 'nova-fps-' });
  } catch (e) { send('log', { text: 'FPS-мод не встановлено: ' + e.message }); }
}

function removeFpsMod(mc) {
  const mods = paths.modsDir(mc);
  if (fs.existsSync(mods)) fs.readdirSync(mods).filter(f => f.startsWith('nova-fps-')).forEach(f => rmForce(path.join(mods, f)));
}

module.exports = { isSystem, ensureFabric, installNovaMod, removeNovaMod, novaStatus, ensureFabricApi, ensureFpsMod, removeFpsMod };
