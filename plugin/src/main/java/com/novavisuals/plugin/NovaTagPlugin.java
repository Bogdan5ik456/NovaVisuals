package com.novavisuals.plugin;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.plugin.messaging.PluginMessageListener;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Мод Nova Visuals при вході надсилає серверу повідомлення в каналі novavisuals:hello.
 * Плагін його ловить і додає "NV" після ніка гравця.
 */
public final class NovaTagPlugin extends JavaPlugin implements Listener, PluginMessageListener {
    public static final String CHANNEL = "novavisuals:hello";

    private final Set<UUID> nv = ConcurrentHashMap.newKeySet();
    private String suffix = " " + ChatColor.LIGHT_PURPLE + "NV";
    private boolean tab = true, chat = true, head = false;
    private String chatMode = "auto";
    private String chatSuffix = " ";

    @Override
    public void onEnable() {
        saveDefaultConfig();
        loadSettings();
        getServer().getMessenger().registerIncomingPluginChannel(this, CHANNEL, this);
        getServer().getPluginManager().registerEvents(this, this);
        getLogger().info("NovaVisualsTag увімкнено. Очікую гравців з модом Nova Visuals.");
    }

    @Override
    public void onDisable() {
        for (UUID id : new ArrayList<>(nv)) {
            Player p = Bukkit.getPlayer(id);
            if (p != null) removeTag(p);
        }
        nv.clear();
        getServer().getMessenger().unregisterIncomingPluginChannel(this);
    }

    private void loadSettings() {
        reloadConfig();
        suffix = ChatColor.translateAlternateColorCodes('&', getConfig().getString("suffix", " &dNV"));
        tab = getConfig().getBoolean("show-in-tab", true);
        chat = getConfig().getBoolean("show-in-chat", true);
        head = getConfig().getBoolean("show-above-head", false);
        chatMode = getConfig().getString("chat-mode", "auto").toLowerCase();
        chatSuffix = suffix + ChatColor.RESET;   // скидаємо колір, щоб решта повідомлення не стала фіолетовою
    }

    @Override
    public void onPluginMessageReceived(String channel, Player player, byte[] message) {
        if (!CHANNEL.equals(channel)) return;
        Bukkit.getScheduler().runTask(this, () -> mark(player, "повідомлення"));
    }

    /** Позначає гравця як такого, що грає з Nova Visuals (викликається і з повідомлення мода, і з команди /nvhello). */
    private void mark(Player p, String how) {
        if (p.isOnline() && nv.add(p.getUniqueId())) {
            applyTag(p);
            getLogger().info(p.getName() + " грає з Nova Visuals (" + how + ")");
        }
    }

    private Team team() {
        Scoreboard sb = Bukkit.getScoreboardManager().getMainScoreboard();
        Team t = sb.getTeam("nv_tag");
        if (t == null) t = sb.registerNewTeam("nv_tag");
        t.setSuffix(suffix);
        return t;
    }

    private void applyTag(Player p) {
        if (tab) p.setPlayerListName(p.getName() + suffix);
        if (chat && chatMode.equals("displayname")) p.setDisplayName(p.getName() + chatSuffix);
        if (head) team().addEntry(p.getName());
    }

    private void removeTag(Player p) {
        if (tab) p.setPlayerListName(null);
        if (chat && chatMode.equals("displayname")) p.setDisplayName(null);
        if (head) team().removeEntry(p.getName());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        if (nv.remove(e.getPlayer().getUniqueId()) && head) team().removeEntry(e.getPlayer().getName());
    }

    /**
     * Працює ПІСЛЯ чат-плагінів (MONITOR): шукаємо нік у готовому форматі й дописуємо NV одразу після нього.
     * Режими chat-mode: auto (стандартний), displayname, off.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onChat(AsyncPlayerChatEvent e) {
        if (!chat || chatMode.equals("displayname") || chatMode.equals("off")) return;
        Player pl = e.getPlayer();
        if (!nv.contains(pl.getUniqueId())) return;
        String f = e.getFormat();
        String esc = chatSuffix.replace("%", "%%");
        if (f.contains("%1$s")) {                       // стандартний формат: %1$s = ім'я гравця
            e.setFormat(f.replace("%1$s", "%1$s" + esc));
            return;
        }
        String name = pl.getName();                     // чат-плагін уже підставив нік текстом
        int i = f.indexOf(name);
        if (i < 0) {
            String dn = ChatColor.stripColor(pl.getDisplayName());
            if (dn != null && !dn.isEmpty()) { name = dn; i = f.indexOf(name); }
        }
        if (i >= 0) {
            int end = i + name.length();
            e.setFormat(f.substring(0, end) + esc + f.substring(end));
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (cmd.getName().equalsIgnoreCase("nvhello")) {   // мод викликає її сам при вході (працює і через Velocity/Bungee)
            if (sender instanceof Player p) mark(p, "команда");
            return true;
        }
        if (args.length > 0 && args[0].equalsIgnoreCase("reload")) {
            loadSettings();
            sender.sendMessage(ChatColor.LIGHT_PURPLE + "[NV] Налаштування перезавантажено.");
            return true;
        }
        List<String> names = new ArrayList<>();
        for (UUID id : nv) {
            Player p = Bukkit.getPlayer(id);
            if (p != null) names.add(p.getName());
        }
        sender.sendMessage(ChatColor.LIGHT_PURPLE + "[NV] З Nova Visuals (" + names.size() + "): " + String.join(", ", names));
        return true;
    }
}
