// Папки лаунчера: %APPDATA%\.nova-visuals
const { app } = require('electron');
const path = require('path');
const fs = require('fs');

const root = path.join(app.getPath('appData'), '.nova-visuals');
// кожна версія Minecraft має свою папку (з модами, світами й ресурс-паками); лаунчер працює лише з Fabric
const instance = (version) => path.join(root, 'instances', version + '-fabric');
const paths = {
  root,
  runtime: path.join(root, 'runtime'),   // завантажена Java
  cache: path.join(root, 'cache'),
  instance,
  modsDir: (version) => path.join(instance(version), 'mods'),
  packsDir: (version) => path.join(instance(version), 'resourcepacks')
};
[paths.runtime, paths.cache].forEach(d => fs.mkdirSync(d, { recursive: true }));
module.exports = paths;
