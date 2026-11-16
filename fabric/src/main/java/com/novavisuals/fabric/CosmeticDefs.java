package com.novavisuals.fabric;

import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Каталог косметики: вбудована (з аркуша Nova Visuals) + своя (з меню або з папки config/novavisuals/cosmetics). */
public final class CosmeticDefs {
    public static final int WINGS = 0, HATS = 1, WEAPONS = 2;
    public static final String[] TYPE_NAMES = {"Крила", "Капелюх", "Зброя"};

    public static final String[] WING_IDS = {"shadow", "angel", "dragon", "neon", "flame", "astral"};
    public static final String[] WING_NAMES = {"Крила тіні", "Ангельські крила", "Драконячі крила", "Неонові крила", "Полум'яні крила", "Астральні крила"};
    public static final int[] WING_PAL = {0, 1, 0, 5, 2, 3};

    public static final String[] HAT_IDS = {"chinese", "amethyst", "neon", "mage", "samurai", "horns"};
    public static final String[] HAT_NAMES = {"Китайська шляпа", "Корона аметисту", "Неонова корона", "Капелюх мага", "Самурайський шолом", "Диявольські роги"};
    public static final int[] HAT_PAL = {2, 0, 0, 0, 2, 0};

    public static final String[] WPN_IDS = {"shadow", "flame", "ice", "neon", "katana", "axe", "staff", "bow"};
    public static final String[] WPN_NAMES = {"Меч тіні", "Полум'яний меч", "Крижаний меч", "Неоновий меч", "Катана", "Сокира дракона", "Жезл аркани", "Лук душ"};
    public static final int[] WPN_PAL = {0, 2, 6, 5, 1, 0, 5, 7};

    public static final String[] ANIM_IDS = {"dance", "meditate", "fly", "trail", "afk", "hit"};
    public static final String[] ANIM_NAMES = {"Емоція танцю", "Медитація", "Летюче сяйво", "Сліди при ходьбі", "AFK анімація", "Ефект удару"};

    public static final String[] KILL_IDS = {"blood", "souls", "burst"};
    public static final String[] KILL_NAMES = {"Кривавий сплеск", "Душі ворога", "Вибух частинок"};

    /** Палітри частинок: від них залежить колір ефектів, анімацій і своєї косметики. */
    public static final String[] PAL_NAMES = {"Фіолет", "Білий", "Вогонь", "Синій", "Зелений", "Електрика", "Крижаний", "Душі"};

    public static int builtin(int type) { return type == WINGS ? WING_IDS.length : type == HATS ? HAT_IDS.length : WPN_IDS.length; }

    public static List<Settings.Custom> customs(int type) {
        List<Settings.Custom> l = new ArrayList<>();
        for (Settings.Custom c : Settings.d.customCos) if (c.type == type) l.add(c);
        return l;
    }

    /** Скільки варіантів, не рахуючи «Немає». */
    public static int count(int type) { return builtin(type) + customs(type).size(); }

    public static int next(int type, int sel) { return (Math.max(0, sel) + 1) % (count(type) + 1); }

    public static Settings.Custom custom(int type, int sel) {
        int i = sel - builtin(type) - 1;
        List<Settings.Custom> l = customs(type);
        return sel > builtin(type) && i >= 0 && i < l.size() ? l.get(i) : null;
    }

    public static String name(int type, int sel) {
        if (sel <= 0) return "Немає";
        if (sel <= builtin(type)) return (type == WINGS ? WING_NAMES : type == HATS ? HAT_NAMES : WPN_NAMES)[sel - 1];
        Settings.Custom c = custom(type, sel);
        return c != null ? "★ " + c.name : "Немає";
    }

    /** Ідентифікатор вбудованого варіанта (для текстур), або null. */
    public static String id(int type, int sel) {
        return sel >= 1 && sel <= builtin(type) ? (type == WINGS ? WING_IDS : type == HATS ? HAT_IDS : WPN_IDS)[sel - 1] : null;
    }

    public static int pal(int type, int sel) {
        if (sel >= 1 && sel <= builtin(type)) return (type == WINGS ? WING_PAL : type == HATS ? HAT_PAL : WPN_PAL)[sel - 1];
        Settings.Custom c = custom(type, sel);
        return c != null ? Math.max(0, Math.min(PAL_NAMES.length - 1, c.palette)) : 0;
    }

    public static Path folder() { return FabricLoader.getInstance().getConfigDir().resolve("novavisuals").resolve("cosmetics"); }

    /** Нова косметика користувача. Повертає її номер у списку свого типу (для вибору), або -1. */
    public static int addCustom(String name, int type, int palette, String png) {
        Settings.Custom c = new Settings.Custom();
        String safe = name.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_]", "");
        c.id = (safe.isEmpty() ? "c" : safe) + "_" + (System.currentTimeMillis() % 1_000_000L);
        c.name = name.length() > 24 ? name.substring(0, 24) : name;
        c.type = type;
        c.palette = palette;
        c.png = png == null ? "" : png;
        Settings.d.customCos.add(c);
        Settings.save();
        return builtin(type) + customs(type).size();
    }

    public static void remove(Settings.Custom c) {
        Settings.d.customCos.remove(c);
        if (Settings.d.cosWings > count(WINGS)) Settings.d.cosWings = 0;
        if (Settings.d.cosHat > count(HATS)) Settings.d.cosHat = 0;
        if (Settings.d.cosWeapon > count(WEAPONS)) Settings.d.cosWeapon = 0;
        Settings.save();
    }

    /** Підхоплює файли wings_*.png й hat_*.png з папки косметики як нові варіанти. Повертає, скільки додано. */
    public static int scan() {
        int added = 0;
        Path dir = folder();
        try {
            Files.createDirectories(dir);
            try (DirectoryStream<Path> ds = Files.newDirectoryStream(dir, "*.png")) {
                for (Path p : ds) {
                    String fn = p.getFileName().toString();
                    String low = fn.toLowerCase(Locale.ROOT);
                    int type = low.startsWith("wings_") ? WINGS : low.startsWith("hat_") ? HATS : -1;
                    if (type < 0) continue;
                    boolean known = false;
                    for (Settings.Custom c : Settings.d.customCos) if (c.png != null && Path.of(c.png).getFileName().toString().equalsIgnoreCase(fn)) known = true;
                    if (known) continue;
                    String nm = fn.substring(low.indexOf('_') + 1, fn.length() - 4);
                    addCustom(nm.isEmpty() ? "Моя" : nm, type, 0, p.toString());
                    added++;
                }
            }
        } catch (IOException ignored) { }
        return added;
    }

    private CosmeticDefs() { }
}
