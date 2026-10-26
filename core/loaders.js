// Forge та OptiFine
const fs = require('fs');
const path = require('path');
const { spawn } = require('child_process');
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
    p.stdout.on('data', d => onLog && onLog(String(d).trim()));
    p.stderr.on('data', d => onLog && onLog(String(d).trim()));
    p.on('error', fail);
    p.on('close', code => (code === 0 ? ok() : fail(new Error('Інсталятор завершився з кодом ' + code))));
  });
}

// Встановлює Forge для версії mc, повертає назву версії для запуску
async function ensureForge(mc, javaExe, send) {
  const have = findVersion(d => d.startsWith(mc + '-forge-'));
  if (have) return have;

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
  const name = findVersion(d => d.startsWith(mc + '-forge-'));
  if (!name) throw new Error('Forge встановлено, але версію не знайдено');
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

module.exports = { ensureForge, ensureOptifine, installOptifineAsMod, optifineJars };
