// Безпечний міст між вікном лаунчера та головним процесом
const { contextBridge, ipcRenderer } = require('electron');
const call = (ch, ...a) => ipcRenderer.invoke(ch, ...a);

contextBridge.exposeInMainWorld('nova', {
  minimize: () => ipcRenderer.send('win:minimize'),
  maximize: () => ipcRenderer.send('win:maximize'),
  close: () => ipcRenderer.send('win:close'),
  launch: (opts) => call('game:launch', opts),
  onGameEvent: (cb) => ipcRenderer.on('game:event', (_e, d) => cb(d)),
  msLogin: () => call('auth:ms-login'),
  msStatus: () => call('auth:ms-status'),
  msLogout: () => call('auth:ms-logout'),
  listFiles: (v, k) => call('files:list', v, k),
  addFiles: (v, k) => call('files:add', v, k),
  removeFile: (v, k, n) => call('files:remove', v, k, n),
  toggleMod: (v, n) => call('files:toggle', v, n),
  libSearch: (k, q, v, off) => call('lib:search', k, q, v, off),
  libInstall: (k, p, v) => call('lib:install', k, p, v),
  appInfo: () => call('app:info'),
  checkUpdates: (manual) => call('update:check', manual),
  onUpdate: (cb) => ipcRenderer.on('update:event', (_e, d) => cb(d)),
  modStatus: (v) => call('mod:status', v),
  installMod: (v) => call('mod:install', v),
  setSkin: (d) => call('skin:set', d),
  clearSkin: () => call('skin:clear'),
  openLog: () => call('logs:open'),
  openFolder: (v) => call('folder:open', v)
});
