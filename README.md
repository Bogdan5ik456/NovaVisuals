# Nova Visuals Launcher

Власний Minecraft Java Launcher на Electron (HTML + CSS + JavaScript) для Windows 10/11.

## Можливості (v0.3.0)
- Інтерфейс Nova Visuals, налаштування (RAM, роздільна здатність, мова)
- Завантаження Java 17/21, Minecraft 1.21.11 і 1.18.2, Forge, запуск гри
- Офлайн-нік та вхід через офіційне вікно Microsoft
- Керування модами й ресурс-паками (окремо для кожної версії)
- Мод Nova Visuals для 1.18.2 (папка `mod/`): меню на правий Shift, приціл, зум (C), яскравість, час доби, колір неба, капелюхи, друзі, авто-/tpa, маркери
- Збірка Windows-інсталятора через GitHub Actions (`.github/workflows/build.yml`)

Дані лаунчера: `%APPDATA%\.nova-visuals`

## Інсталятор
Після `git push` у гілку `main` GitHub збирає `NovaVisuals-Setup-<версія>.exe`
і кладе його у розділ **Releases** репозиторію.

## Запуск на ПК з Windows
```
npm install
npm start
```

## Структура
- `main.js`, `preload.js` — процес Electron і безпечний міст
- `core/` — Java, Forge/OptiFine, авторизація, моди, запуск гри
- `src/` — інтерфейс (`index.html`, `css/style.css`, `js/app.js`, `js/i18n.js`, `js/icons.js`)
- `assets/` — логотип, іконка (SVG/PNG/ICO), банери, фон, іконки меню
- `assets/minecraft/` — приціли, серця, голод, XP-бар, інвентар
- `design-reference/` — початкові референс-картинки

Мова інтерфейсу, RAM, роздільна здатність і нік зберігаються локально (`localStorage`).
