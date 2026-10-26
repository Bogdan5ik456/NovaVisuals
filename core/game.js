// Підготовка та запуск Minecraft
const { Client } = require('minecraft-launcher-core');
const paths = require('./paths');
const { ensureJava } = require('./java');
const loaders = require('./loaders');
const auth = require('./auth');

let running = false;

async function launchGame(o, send) {
  if (running) throw new Error('Гра вже запущена');
  running = true;
  try {
    const { version, loader, ram, res, authType, nick } = o;
    if (!['1.21.11', '1.18.2'].includes(version)) throw new Error('Непідтримувана версія');
    if (!['vanilla', 'forge', 'optifine', 'forgeoptifine'].includes(loader)) throw new Error('Невідома збірка');

    const java = await ensureJava(version === '1.18.2' ? 17 : 21, (text, percent) => send('progress', { text, percent }));

    let custom;
    if (loader === 'forge' || loader === 'forgeoptifine') custom = await loaders.ensureForge(version, java.java, send);
    if (loader === 'forgeoptifine') loaders.installOptifineAsMod(version);
    if (loader === 'optifine') custom = await loaders.ensureOptifine(version, java.java, send);

    send('progress', { text: 'Вхід в акаунт…', percent: 100 });
    let authorization;
    if (authType === 'microsoft') authorization = await auth.microsoftAuthorization();
    else {
      if (!/^[A-Za-z0-9_]{3,16}$/.test(nick || '')) throw new Error('Некоректний нік');
      authorization = auth.offlineAuthorization(nick);
    }

    const win = res === 'full' ? { fullscreen: true } : (() => { const [w, h] = String(res).split('x').map(Number); return { width: w || 1280, height: h || 720 }; })();
    const client = new Client();
    client.on('progress', e => e.total && send('progress', { text: `Завантаження: ${e.type}`, percent: Math.round(e.task / e.total * 100) }));
    client.on('download-status', e => send('progress', { text: `Завантаження: ${e.name}`, percent: e.total ? Math.round(e.current / e.total * 100) : 0 }));
    client.on('debug', t => send('log', { text: String(t) }));
    client.on('data', t => send('log', { text: String(t) }));

    send('progress', { text: 'Запуск Minecraft…', percent: 100 });
    const proc = await client.launch({
      authorization,
      root: paths.root,
      javaPath: java.javaw,
      version: { number: version, type: 'release', ...(custom ? { custom } : {}) },
      memory: { max: `${Math.max(1024, Math.min(+ram || 4096, 16384))}M`, min: '1024M' },
      window: win,
      overrides: { gameDirectory: paths.instance(version) }
    });
    if (!proc) throw new Error('Не вдалося запустити Minecraft. Дивись журнал нижче.');
    send('started', {});
    proc.on('close', code => { running = false; send('closed', { code }); });
  } catch (e) {
    running = false;
    throw e;
  }
}

module.exports = { launchGame };
