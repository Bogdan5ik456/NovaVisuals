// Nova Visuals — головний процес Electron
const { app, BrowserWindow, ipcMain, shell } = require('electron');
const path = require('path');

if (!app.requestSingleInstanceLock()) app.quit();

const paths = require('./core/paths');
const files = require('./core/files');
const auth = require('./core/auth');
const { launchGame } = require('./core/game');
const fabric = require('./core/fabric');
const lib = require('./core/library');
const skin = require('./core/skin');
const site = require('./core/site');
const fs = require('fs');

// Журнал запуску: %APPDATA%\.nova-visuals\logs\launcher.log
const logFile = path.join(paths.root, 'logs', 'launcher.log');
const log = (t) => { try { fs.appendFileSync(logFile, String(t) + '\n'); } catch {} };

let win, gameRunning = false, manualCheck = false, updater = null;
function createWindow() {
  win = new BrowserWindow({
    width: 1180, height: 720, minWidth: 960, minHeight: 600,
    frame: false, backgroundColor: '#0B0B0F', show: false,
    icon: path.join(__dirname, 'assets', 'icon.ico'),
    webPreferences: { preload: path.join(__dirname, 'preload.js'), contextIsolation: true, nodeIntegration: false, sandbox: true }
  });
  win.loadFile(path.join(__dirname, 'src', 'index.html'));
  win.once('ready-to-show', () => win.show());
  win.webContents.setWindowOpenHandler(() => ({ action: 'deny' }));
  win.on('maximize', () => win.webContents.send('win:state', true));
  win.on('unmaximize', () => win.webContents.send('win:state', false));
}

// Обгортка: усі помилки повертаємо у вікно як текст
const handle = (name, fn) => ipcMain.handle(name, async (_e, ...a) => {
  try { return { ok: true, data: await fn(...a) }; }
  catch (e) { return { ok: false, error: e && e.message ? e.message : String(e) }; }
});

ipcMain.on('win:minimize', () => win && win.minimize());
ipcMain.on('win:maximize', () => win && (win.isMaximized() ? win.unmaximize() : win.maximize()));
ipcMain.on('win:close', () => win && win.close());

handle('app:info', () => ({ version: app.getVersion() }));
handle('game:launch', async (opts) => {
  const send = (type, data) => {
    if (type === 'log' || type === 'progress') log(data.text);
    if (type === 'started') gameRunning = true;
    if (type === 'closed') gameRunning = false;
    if (!win || win.isDestroyed()) return;
    win.webContents.send('game:event', { type, ...data });
    if (opts.closeOnLaunch && type === 'started') win.hide();
    if (opts.closeOnLaunch && type === 'closed') win.show();
  };
  try { fs.mkdirSync(path.dirname(logFile), { recursive: true }); fs.writeFileSync(logFile, `Nova Visuals ${app.getVersion()} ${new Date().toISOString()}\n${JSON.stringify({ ...opts, nick: undefined })}\n`); } catch {}
  try { await launchGame(opts, send); } catch (e) { log('ПОМИЛКА: ' + (e && e.stack || e)); throw e; }
});
// Автооновлення через GitHub Releases (працює лише в встановленій версії)
const sendUpd = (d) => { if (win && !win.isDestroyed()) win.webContents.send('update:event', d); };
function initUpdater() {
  if (updater) return updater;
  updater = require('electron-updater').autoUpdater;
  updater.autoDownload = true;
  updater.autoInstallOnAppQuit = true;
  updater.logger = null;
  updater.on('update-available', i => sendUpd({ state: 'downloading', version: i.version, percent: 0 }));
  updater.on('download-progress', p => sendUpd({ state: 'downloading', version: '', percent: Math.round(p.percent) }));
  updater.on('update-not-available', () => { if (manualCheck) sendUpd({ state: 'none' }); });
  updater.on('update-downloaded', i => {
    sendUpd({ state: 'ready', version: i.version });
    if (!gameRunning) setTimeout(() => updater.quitAndInstall(true, true), 3500);   // під час гри не перезапускаємо
  });
  return updater;
}
handle('update:check', async (manual) => {
  if (!app.isPackaged) return { dev: true };
  manualCheck = !!manual;
  await initUpdater().checkForUpdates();
});

// Сайт Nova Visuals: вхід, друзі
handle('site:status', () => site.status());
handle('site:start', () => site.start(d => { if (win && !win.isDestroyed()) win.webContents.send('site:event', d); }));
handle('site:cancel', () => site.stopPolling());
handle('site:logout', () => site.logout());
handle('site:friends', () => site.friends());
handle('site:friend', (a, n) => site.friendAction(a, n));
handle('site:get-url', () => site.siteUrl());
handle('site:set-url', (u) => site.setSiteUrl(u));

handle('auth:ms-login', () => auth.microsoftLogin());
handle('auth:ms-status', () => auth.microsoftStatus());
handle('auth:ms-logout', () => auth.microsoftLogout());
handle('files:list', (v, k) => files.list(v, k));
handle('files:add', (v, k) => files.add(win, v, k));
handle('files:remove', (v, k, n) => files.remove(v, k, n));
handle('files:toggle', (v, n) => files.toggle(v, n));
handle('lib:search', (k, q, v, off) => { if (!['mods', 'packs'].includes(k)) throw new Error('Невірний тип'); return lib.search(k, q, v, off); });
handle('lib:install', async (k, p, v) => {
  if (!['mods', 'packs'].includes(k) || !/^[0-9.]+$/.test(v)) throw new Error('Невірні дані');
  return lib.installProject(k, p, v, k === 'packs' ? paths.packsDir(v) : paths.modsDir(v));
});
handle('folder:open', (v) => shell.openPath(paths.instance(/^[0-9.]+$/.test(v) ? v : '')));
const modVer = (v) => (/^[0-9.]+$/.test(v) ? v : '1.18.2');
handle('mod:status', (v) => fabric.novaStatus(modVer(v)));
handle('mod:install', (v) => { fabric.installNovaMod(modVer(v)); return fabric.novaStatus(modVer(v)); });
handle('skin:set', (d) => skin.setSkin(d));
handle('skin:clear', () => skin.clearSkin());
handle('logs:open', () => { fs.mkdirSync(path.dirname(logFile), { recursive: true }); if (!fs.existsSync(logFile)) fs.writeFileSync(logFile, ''); shell.showItemInFolder(logFile); });

app.whenReady().then(() => {
  createWindow();
  if (site.status()) site.startPresence();
  for (const v of ['1.18.2', '1.21.11']) {   // вбудований мод -> папка mods
    try { if (fabric.novaStatus(v).bundled) fabric.installNovaMod(v); } catch (e) { log('Мод Nova Visuals ' + v + ': ' + e.message); }
  }
});
app.on('second-instance', () => { if (win) { if (win.isMinimized()) win.restore(); win.focus(); } });
app.on('window-all-closed', () => app.quit());
