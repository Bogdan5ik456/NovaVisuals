// Папки лаунчера: %APPDATA%\.nova-visuals
const { app } = require('electron');
const path = require('path');
const fs = require('fs');

const root = path.join(app.getPath('appData'), '.nova-visuals');
const paths = {
  root,
  runtime: path.join(root, 'runtime'),     // завантажена Java
  cache: path.join(root, 'cache'),         // інсталятори Forge тощо
  optifine: path.join(root, 'optifine'),   // .jar файли OptiFine, додані користувачем
  // кожна версія Minecraft має свою папку з модами, світами та ресурс-паками
  instance: (version) => path.join(root, 'instances', version),
  modsDir: (version) => path.join(root, 'instances', version, 'mods'),
  packsDir: (version) => path.join(root, 'instances', version, 'resourcepacks')
};
[paths.runtime, paths.cache, paths.optifine].forEach(d => fs.mkdirSync(d, { recursive: true }));
module.exports = paths;
