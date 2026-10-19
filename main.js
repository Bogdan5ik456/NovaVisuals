const { app, BrowserWindow } = require("electron");
const path = require("node:path");
function createWindow() {
  const win = new BrowserWindow({ width: 1180, height: 760, minWidth: 900, minHeight: 620,
    backgroundColor: "#090914", title: "Nova Visuals", autoHideMenuBar: true,
    webPreferences: { preload: path.join(__dirname, "preload.js"), contextIsolation: true, nodeIntegration: false } });
  win.loadFile(path.join(__dirname, "index.html"));
}
app.whenReady().then(() => { createWindow(); app.on("activate", () => { if (!BrowserWindow.getAllWindows().length) createWindow(); }); });
app.on("window-all-closed", () => { if (process.platform !== "darwin") app.quit(); });
