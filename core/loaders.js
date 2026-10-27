// Forge та OptiFine
const fs = require('fs');
const path = require('path');
const { spawn } = require('child_process');
const { app } = require('electron');
const crypto = require('crypto');
const paths = require('./paths');
const { download } = require('./util');

const versionsDir = () => path.join(paths.root, 'versions');
const findVersion = (test) => {
  const d = versionsDir();
  if (!fs.existsSync(d)) return null;
  return fs.readdirSync(d).filter(test).sort().pop() || null;
};

function run(javaExe, args, onLog) {
  return new Promise((ok, fail) => {
    const p = spawn(javaExe, args, { windowsHide: true });
    const tail = [];
    const feed = d => { const t = String(d).trim(); if (!t) return; tail.push(t); if (tail.length > 12) tail.shift(); onLog && onLog(t); };
    p.stdout.on('data', feed);
    p.stderr.on('data', feed);
    p.on('error', fail);
    p.on('close', code => (code === 0 ? ok() : fail(new Error('Інсталятор завершився з кодом ' + code + '\n' + tail.join('\n')))));
  });
}

// Завантажує ванільну версію (json + jar) — Forge-інсталятору вона потрібна на диску
async function ensureVanilla(mc, send) {
  const dir = path.join(paths.root, 'versions', mc);
  const json = path.join(dir, mc + '.json'), jar = path.join(dir, mc + '.jar');
  if (fs.existsSync(json) && fs.existsSync(jar)) return;
  send('progress', { text: `Завантаження Minecraft ${mc}…`, percent: 0 });
  const mres = await fetch('https://piston-meta.mojang.com/mc/game/version_manifest_v2.json');
  if (!mres.ok) throw new Error('Не вдалося отримати список версій Minecraft');
  const entry = (await mres.json()).versions.find(v => v.id === mc);
  if (!entry) throw new Error('Версію Minecraft ' + mc + ' не знайдено');
  const meta = await (await fetch(entry.url)).json();
  fs.mkdirSync(dir, { recursive: true });
  fs.writeFileSync(json, JSON.stringify(meta));
  await download(meta.downloads.client.url, jar, p => send('progress', { text: `Завантаження Minecraft ${mc}…`, percent: Math.round(p * 100) }));
}

// Встановлює Forge для версії mc, повертає назву версії для запуску
async function ensureForge(mc, javaExe, send) {
  const okFile = (d) => path.join(versionsDir(), d, '.nova-ok');   // позначка «встановлено повністю»
  const have = findVersion(d => d.startsWith(mc + '-forge-') && fs.existsSync(okFile(d)));
  if (have) return have;
  await ensureVanilla(mc, send);

  send('progress', { text: `Пошук Forge для ${mc}…`, percent: 0 });
  const res = await fetch('https://files.minecraftforge.net/net/minecraftforge/forge/promotions_slim.json');
  if (!res.ok) throw new Error('Не вдалося отримати список версій Forge');
  const promos = (await res.json()).promos || {};
  const ver = promos[mc + '-recommended'] || promos[mc + '-latest'];
  if (!ver) throw new Error(`Forge для Minecraft ${mc} зараз недоступний`);

  const jar = path.join(paths.cache, `forge-${mc}-${ver}-installer.jar`);
  const url = `https://maven.minecraftforge.net/net/minecraftforge/forge/${mc}-${ver}/forge-${mc}-${ver}-installer.jar`;
  await download(url, jar, p => send('progress', { text: `Завантаження Forge ${ver}…`, percent: Math.round(p * 100) }));

  // інсталятор Forge вимагає цей файл у папці гри
  const prof = path.join(paths.root, 'launcher_profiles.json');
  if (!fs.existsSync(prof)) fs.writeFileSync(prof, JSON.stringify({ profiles: {} }));

  send('progress', { text: 'Встановлення Forge (це може тривати кілька хвилин)…', percent: 100 });
  await run(javaExe, ['-jar', jar, '--installClient', paths.root], t => send('log', { text: t }));
  const exact = `${mc}-forge-${ver}`;
  const name = fs.existsSync(path.join(versionsDir(), exact)) ? exact : findVersion(d => d.startsWith(mc + '-forge-'));
  if (!name) throw new Error('Forge встановлено, але версію не знайдено');
  fs.writeFileSync(okFile(name), 'ok');
  return name;
}

// OptiFine не має офіційного API, тому користувач сам додає .jar (кнопка в лаунчері)
const optifineJars = (mc) => fs.readdirSync(paths.optifine)
  .filter(f => /^optifine/i.test(f) && f.toLowerCase().endsWith('.jar') && f.includes(mc));

function needOptifine(mc) {
  const jars = optifineJars(mc);
  if (!jars.length) throw new Error(`Додай файл OptiFine для Minecraft ${mc} (кнопка «Додати OptiFine»).`);
  return path.join(paths.optifine, jars[0]);
}

// OptiFine як окрема версія: запускаємо його власний інсталятор один раз
async function ensureOptifine(mc, javaExe, send) {
  const test = d => d.startsWith(mc) && /optifine/i.test(d);
  const have = findVersion(test);
  if (have) return have;
  const jar = needOptifine(mc);
  send('progress', { text: 'Відкрито інсталятор OptiFine — натисни в ньому «Install»', percent: 100 });
  await run(javaExe, ['-cp', jar, 'optifine.Installer'], t => send('log', { text: t }));
  const name = findVersion(test);
  if (!name) throw new Error('OptiFine не було встановлено. Запусти ще раз і натисни «Install».');
  return name;
}

// OptiFine разом із Forge — як звичайний мод
function installOptifineAsMod(mc) {
  const jar = needOptifine(mc);
  const mods = paths.modsDir(mc);
  fs.mkdirSync(mods, { recursive: true });
  fs.copyFileSync(jar, path.join(mods, path.basename(jar)));
}

// Мод Nova Visuals (меню на правий Shift): лежить усередині лаунчера, копіюється в папку модів
const NOVA_JAR = 'NovaVisuals-mod.jar';
function novaSource(mc) {
  const name = `NovaVisuals-mod-${mc}.jar`;
  return app.isPackaged ? path.join(process.resourcesPath, 'mod', name) : path.join(__dirname, '..', 'mod-dist', name);
}
function installNovaMod(mc) {
  const src = novaSource(mc);
  if (!fs.existsSync(src)) throw new Error(`Мод Nova Visuals для Minecraft ${mc} ще не готовий. Обери іншу збірку.`);
  const mods = paths.modsDir(mc);
  fs.mkdirSync(mods, { recursive: true });
  fs.copyFileSync(src, path.join(mods, NOVA_JAR));
}
function removeNovaMod(mc) {
  fs.rmSync(path.join(paths.modsDir(mc), NOVA_JAR), { force: true });
}

// Аргументи Java, які Forge вимагає в своєму json (модульна система: -p, --add-opens, --add-modules ...).
// MCLC їх не передає, тому читаємо й підставляємо самі.
function forgeJvmArgs(versionName) {
  const f = path.join(paths.root, 'versions', versionName, versionName + '.json');
  if (!fs.existsSync(f)) return [];
  const raw = ((JSON.parse(fs.readFileSync(f, 'utf8')).arguments || {}).jvm || []).filter(x => typeof x === 'string');
  const lib = path.join(paths.root, 'libraries');
  const sub = s => s.replace(/\$\{library_directory\}/g, lib).replace(/\$\{classpath_separator\}/g, ';').replace(/\$\{version_name\}/g, versionName);
  const withValue = ['-p', '--module-path', '--add-modules', '--add-opens', '--add-exports', '--add-reads'];
  const a = raw.map(sub), out = [];
  for (let i = 0; i < a.length; i++) {
    if (a[i] === '-cp' || a[i] === '-classpath') { i++; continue; }      // classpath формує MCLC
    if (withValue.includes(a[i])) { const v = a[i + 1]; i++; if (v && !v.includes('${')) out.push(a[i - 1], v); continue; }
    if (a[i].startsWith('-D') && !a[i].includes('${') && !a[i].includes('java.library.path')) out.push(a[i]);
  }
  return out;
}

// FPS-мод (Rubidium) з Modrinth: прискорює малювання світу на Forge 1.18.2
async function ensureFpsMod(mc, send) {
  const mods = paths.modsDir(mc);
  fs.mkdirSync(mods, { recursive: true });
  if (fs.readdirSync(mods).some(f => f.startsWith('nova-fps-'))) return;
  try {
    const url = 'https://api.modrinth.com/v2/project/rubidium/version?loaders=' + encodeURIComponent('["forge"]') +
      '&game_versions=' + encodeURIComponent(JSON.stringify([mc]));
    const r = await fetch(url, { headers: { 'User-Agent': 'NovaVisuals-Launcher/0.5' } });
    if (!r.ok) return;
    const v = (await r.json())[0];
    if (!v) { send('log', { text: 'FPS-мод для ' + mc + ' не знайдено' }); return; }
    const f = v.files.find(x => x.primary) || v.files[0];
    send('progress', { text: 'Завантаження FPS-моду…', percent: 0 });
    const dest = path.join(mods, 'nova-fps-' + path.basename(f.filename));
    await download(f.url, dest, p => send('progress', { text: 'Завантаження FPS-моду…', percent: Math.round(p * 100) }));
    const want = f.hashes && f.hashes.sha512;
    if (want && crypto.createHash('sha512').update(fs.readFileSync(dest)).digest('hex') !== want) {
      fs.rmSync(dest, { force: true });
      send('log', { text: 'FPS-мод: перевірка файлу не пройшла, пропущено' });
    }
  } catch (e) { send('log', { text: 'FPS-мод не встановлено: ' + e.message }); }
}
function removeFpsMod(mc) {
  const mods = paths.modsDir(mc);
  if (fs.existsSync(mods)) fs.readdirSync(mods).filter(f => f.startsWith('nova-fps-')).forEach(f => fs.rmSync(path.join(mods, f), { force: true }));
}

module.exports = { forgeJvmArgs, ensureFpsMod, removeFpsMod, installNovaMod, removeNovaMod, ensureForge, ensureOptifine, installOptifineAsMod, optifineJars };
