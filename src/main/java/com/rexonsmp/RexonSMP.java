package com.rexonsmp;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.Statistic;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scoreboard.Criteria;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class RexonSMP extends JavaPlugin implements Listener {

    private static final double START_BALANCE = 0.0;
    private static final int LINES = 4; // dashes, balance, deaths, kills
    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacyAmpersand();

    private final Map<UUID, Double> balances = new HashMap<>();
    private final Map<UUID, Scoreboard> boards = new HashMap<>();
    private File balanceFile;

    private static Component c(String text) {
        return LEGACY.deserialize(text);
    }

    @Override
    public void onEnable() {
        balanceFile = new File(getDataFolder(), "balances.yml");
        loadBalances();

        Bukkit.getPluginManager().registerEvents(this, this);

        // Refresh every second (20 ticks)
        Bukkit.getScheduler().runTaskTimer(this, () -> {
            for (Player p : Bukkit.getOnlinePlayers()) {
                updateBoard(p);
            }
        }, 20L, 20L);

        for (Player p : Bukkit.getOnlinePlayers()) {
            setupBoard(p);
        }
    }

    @Override
    public void onDisable() {
        saveBalances();
    }

    // ---------------- Events ----------------

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        balances.putIfAbsent(e.getPlayer().getUniqueId(), START_BALANCE);
        setupBoard(e.getPlayer());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        boards.remove(e.getPlayer().getUniqueId());
        saveBalances();
    }

    // ---------------- Scoreboard ----------------

    private void setupBoard(Player player) {
        Scoreboard board = Bukkit.getScoreboardManager().getNewScoreboard();
        Component title = Component.text("RexonSMP")
                .color(NamedTextColor.GOLD)
                .decorate(TextDecoration.BOLD);
        Objective obj = board.registerNewObjective("rexon", Criteria.DUMMY, title);
        obj.setDisplaySlot(DisplaySlot.SIDEBAR);

        // One team per line so we can change the text without flicker
        for (int i = 0; i < LINES; i++) {
            Team team = board.registerNewTeam("line" + i);
            String entry = "\u00a7" + i + "\u00a7r"; // unique invisible entry
            team.addEntry(entry);
            obj.getScore(entry).setScore(LINES - i); // higher score = higher on screen
        }

        boards.put(player.getUniqueId(), board);
        player.setScoreboard(board);
        updateBoard(player);
    }

    private void updateBoard(Player player) {
        Scoreboard board = boards.get(player.getUniqueId());
        if (board == null) return;

        setLine(board, 0, "&7-----------------");
        setLine(board, 1, "&6Balance: &f" + formatMoney(getBalance(player.getUniqueId())));
        setLine(board, 2, "&cDeaths: &f" + player.getStatistic(Statistic.DEATHS));
        setLine(board, 3, "&aKills: &f" + player.getStatistic(Statistic.PLAYER_KILLS));
    }

    private void setLine(Scoreboard board, int index, String text) {
        Team team = board.getTeam("line" + index);
        if (team != null) {
            team.prefix(c(text));
        }
    }

    // ---------------- Balance ----------------

    public double getBalance(UUID id) {
        return balances.getOrDefault(id, START_BALANCE);
    }

    public void setBalance(UUID id, double amount) {
        balances.put(id, amount);
    }

    private String formatMoney(double amount) {
        return "$" + String.format("%,.2f", amount);
    }

    private void loadBalances() {
        if (!balanceFile.exists()) return;
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(balanceFile);
        for (String key : yaml.getKeys(false)) {
            try {
                balances.put(UUID.fromString(key), yaml.getDouble(key));
            } catch (IllegalArgumentException ignored) {
            }
        }
    }

    private void saveBalances() {
        getDataFolder().mkdirs();
        YamlConfiguration yaml = new YamlConfiguration();
        for (Map.Entry<UUID, Double> entry : balances.entrySet()) {
            yaml.set(entry.getKey().toString(), entry.getValue());
        }
        try {
            yaml.save(balanceFile);
        } catch (IOException ex) {
            getLogger().warning("Could not save balances.yml: " + ex.getMessage());
        }
    }

    // ---------------- Commands ----------------

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (cmd.getName().equalsIgnoreCase("bal")) {
            if (args.length == 0) {
                if (!(sender instanceof Player player)) {
                    sender.sendMessage(c("Console must use /bal <player>"));
                    return true;
                }
                player.sendMessage(c("&6Your balance: &f" + formatMoney(getBalance(player.getUniqueId()))));
            } else {
                OfflinePlayer target = Bukkit.getOfflinePlayer(args[0]);
                sender.sendMessage(c("&6" + args[0] + "'s balance: &f"
                        + formatMoney(getBalance(target.getUniqueId()))));
            }
            return true;
        }

        if (cmd.getName().equalsIgnoreCase("setbal")) {
            if (args.length != 2) {
                sender.sendMessage(c("&cUsage: /setbal <player> <amount>"));
                return true;
            }
            double amount;
            try {
                amount = Double.parseDouble(args[1]);
            } catch (NumberFormatException ex) {
                sender.sendMessage(c("&cThat's not a valid number."));
                return true;
            }
            OfflinePlayer target = Bukkit.getOfflinePlayer(args[0]);
            setBalance(target.getUniqueId(), amount);
            saveBalances();
            sender.sendMessage(c("&aSet " + args[0] + "'s balance to " + formatMoney(amount)));
            return true;
        }

        return false;
    }
}
