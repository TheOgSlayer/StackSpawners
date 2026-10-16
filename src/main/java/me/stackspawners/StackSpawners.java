package me.stackspawners;

import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class StackSpawners extends JavaPlugin implements CommandExecutor, TabCompleter {

    private SpawnerManager manager;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        manager = new SpawnerManager(this);
        manager.loadConfig();
        manager.load();
        manager.startTasks();
        Bukkit.getPluginManager().registerEvents(new SpawnerListener(this, manager), this);
        getCommand("givespawner").setExecutor(this);
        getCommand("givespawner").setTabCompleter(this);
        if (economy() == null) getLogger().warning("No Vault economy found yet - selling will fall back to exp/disabled.");
    }

    @Override
    public void onDisable() {
        if (manager != null) manager.save();
    }

    /** Looked up lazily so economy plugins that load after us still work. */
    public Economy economy() {
        if (Bukkit.getPluginManager().getPlugin("Vault") == null) return null;
        RegisteredServiceProvider<Economy> rsp = getServer().getServicesManager().getRegistration(Economy.class);
        return rsp == null ? null : rsp.getProvider();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (args.length != 3) {
            sender.sendMessage(ChatColor.RED + "Usage: /givespawner <player> <mob> <amount>");
            return true;
        }
        Player target = Bukkit.getPlayerExact(args[0]);
        if (target == null) {
            sender.sendMessage(ChatColor.RED + "Player not found.");
            return true;
        }
        EntityType type;
        try {
            type = EntityType.valueOf(args[1].toUpperCase());
            if (!type.isAlive() || !type.isSpawnable()) throw new IllegalArgumentException();
        } catch (IllegalArgumentException ex) {
            sender.sendMessage(ChatColor.RED + "Unknown mob: " + args[1]);
            return true;
        }
        int amount;
        try {
            amount = Integer.parseInt(args[2]);
        } catch (NumberFormatException ex) {
            amount = 0;
        }
        if (amount < 1) {
            sender.sendMessage(ChatColor.RED + "Amount must be a positive number.");
            return true;
        }
        manager.giveSpawners(target, type, amount);
        sender.sendMessage(ChatColor.GREEN + "Gave " + target.getName() + " " + amount + "x " + SpawnerManager.pretty(type) + " spawner.");
        if (sender != target) {
            target.sendMessage(ChatColor.GREEN + "You received " + amount + "x " + SpawnerManager.pretty(type) + " spawner.");
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String alias, String[] args) {
        List<String> out = new ArrayList<>();
        if (args.length == 1) {
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (p.getName().toLowerCase().startsWith(args[0].toLowerCase())) out.add(p.getName());
            }
        } else if (args.length == 2) {
            for (EntityType t : EntityType.values()) {
                if (t.isAlive() && t.isSpawnable() && t.name().toLowerCase().startsWith(args[1].toLowerCase())) {
                    out.add(t.name().toLowerCase());
                }
            }
        } else if (args.length == 3) {
            out.addAll(Arrays.asList("1", "16", "64"));
        }
        return out;
    }
}
