// Nova Visuals — головний процес Electron
const { app, BrowserWindow, ipcMain, shell } = require('electron');
const path = require('path');

if (!app.requestSingleInstanceLock()) app.quit();

const paths = require('./core/paths');
const files = require('./core/files');
const auth = require('./core/auth');
const { launchGame } = require('./core/game');
const { optifineJars } = require('./core/loaders');
const skin = require('./core/skin');
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

handle('auth:ms-login', () => auth.microsoftLogin());
handle('auth:ms-status', () => auth.microsoftStatus());
handle('auth:ms-logout', () => auth.microsoftLogout());
handle('files:list', (v, k) => files.list(v, k));
handle('files:add', (v, k) => files.add(win, v, k));
handle('files:remove', (v, k, n) => files.remove(v, k, n));
handle('files:toggle', (v, n) => files.toggle(v, n));
handle('optifine:add', () => files.addOptifine(win));
handle('optifine:list', (v) => optifineJars(v));
handle('folder:open', (v) => shell.openPath(paths.instance(/^[0-9.]+$/.test(v) ? v : '')));
handle('skin:set', (d) => skin.setSkin(d));
handle('skin:clear', () => skin.clearSkin());
handle('logs:open', () => { fs.mkdirSync(path.dirname(logFile), { recursive: true }); if (!fs.existsSync(logFile)) fs.writeFileSync(logFile, ''); shell.showItemInFolder(logFile); });
handle('optifine:site', () => shell.openExternal('https://optifine.net/downloads'));

app.whenReady().then(createWindow);
app.on('second-instance', () => { if (win) { if (win.isMinimized()) win.restore(); win.focus(); } });
app.on('window-all-closed', () => app.quit());
