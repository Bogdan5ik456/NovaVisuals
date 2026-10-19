const { contextBridge } = require("electron");
contextBridge.exposeInMainWorld("novaApp", { version: "0.1.0", isPrototype: true });
