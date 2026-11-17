@echo off
chcp 65001 >nul
title Nova Visuals
cd /d "%~dp0"

where node >nul 2>nul
if errorlevel 1 (
  echo Потрібен Node.js. Зараз відкриється сторінка завантаження: встанови версію LTS, потім запусти цей файл ще раз.
  start https://nodejs.org
  pause
  exit /b 1
)

if not exist node_modules (
  echo Перший запуск: завантажую компоненти, це займе кілька хвилин...
  call npm install
  if errorlevel 1 ( echo Не вдалося встановити компоненти. Перевір інтернет. & pause & exit /b 1 )
)

echo Запускаю Nova Visuals...
call npm start
