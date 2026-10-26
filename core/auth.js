// Авторизація: офлайн-нік і Microsoft (офіційне вікно входу через бібліотеку msmc)
const { app, safeStorage } = require('electron');
const path = require('path');
const fs = require('fs');
const { Authenticator } = require('minecraft-launcher-core');

const tokenFile = () => path.join(app.getPath('userData'), 'ms-token.bin');
const infoFile = () => path.join(app.getPath('userData'), 'ms-profile.json');

function saveToken(refresh) {
  const buf = safeStorage.isEncryptionAvailable() ? safeStorage.encryptString(refresh) : Buffer.from(refresh);
  fs.writeFileSync(tokenFile(), buf);
}
function readToken() {
  if (!fs.existsSync(tokenFile())) return null;
  const buf = fs.readFileSync(tokenFile());
  return safeStorage.isEncryptionAvailable() ? safeStorage.decryptString(buf) : buf.toString();
}

// Відкриває вікно входу Microsoft. Пароль лаунчер не бачить.
async function microsoftLogin() {
  const { Auth } = require('msmc');
  const manager = new Auth('select_account');
  const xbox = await manager.launch('electron');
  const mc = await xbox.getMinecraft();
  saveToken(xbox.save());
  const profile = { name: mc.profile.name, id: mc.profile.id };
  fs.writeFileSync(infoFile(), JSON.stringify(profile));
  return profile;
}

function microsoftStatus() {
  try { return fs.existsSync(tokenFile()) ? JSON.parse(fs.readFileSync(infoFile(), 'utf8')) : null; } catch { return null; }
}

function microsoftLogout() {
  [tokenFile(), infoFile()].forEach(f => fs.rmSync(f, { force: true }));
}

// Свіжий токен для запуску гри
async function microsoftAuthorization() {
  const saved = readToken();
  if (!saved) throw new Error('Спочатку увійди через Microsoft на сторінці «Профіль».');
  const { Auth } = require('msmc');
  const xbox = await new Auth('none').refresh(saved);
  const mc = await xbox.getMinecraft();
  saveToken(xbox.save());
  return mc.mclc();
}

const offlineAuthorization = (nick) => Authenticator.getAuth(nick);

module.exports = { microsoftLogin, microsoftStatus, microsoftLogout, microsoftAuthorization, offlineAuthorization };
