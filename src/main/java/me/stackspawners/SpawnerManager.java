package me.stackspawners;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.CreatureSpawner;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BlockStateMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.io.File;
import java.io.IOException;
import java.util.*;

public class SpawnerManager {

    public static class Drop {
        final Material material;
        final int min, max;
        final double chance;

        Drop(Material material, int min, int max, double chance) {
            this.material = material;
            this.min = min;
            this.max = max;
            this.chance = chance;
        }
    }

    public static class MobLoot {
        double exp;
        final List<Drop> drops = new ArrayList<>();
    }

    private final StackSpawners plugin;
    private final NamespacedKey mobKey;
    private final Map<String, SpawnerData> spawners = new HashMap<>();
    private final Map<EntityType, MobLoot> loot = new EnumMap<>(EntityType.class);
    private final Map<Material, Double> prices = new EnumMap<>(Material.class);
    private final Random random = new Random();

    public SpawnerManager(StackSpawners plugin) {
        this.plugin = plugin;
        this.mobKey = new NamespacedKey(plugin, "spawner_mob");
    }

    // ------------------------------------------------------------------ config

    public void loadConfig() {
        loot.clear();
        prices.clear();
        FileConfiguration cfg = plugin.getConfig();

        ConfigurationSection mobs = cfg.getConfigurationSection("mobs");
        if (mobs != null) {
            for (String name : mobs.getKeys(false)) {
                EntityType type;
                try {
                    type = EntityType.valueOf(name.toUpperCase());
                } catch (IllegalArgumentException ex) {
                    plugin.getLogger().warning("Unknown mob in config: " + name);
                    continue;
                }
                MobLoot ml = new MobLoot();
                ml.exp = mobs.getDouble(name + ".exp", cfg.getDouble("default-exp", 5));
                ConfigurationSection drops = mobs.getConfigurationSection(name + ".drops");
                if (drops != null) {
                    for (String mat : drops.getKeys(false)) {
                        Material m = Material.matchMaterial(mat);
                        if (m == null) {
                            plugin.getLogger().warning("Unknown material in config: " + mat);
                            continue;
                        }
                        try {
                            String[] parts = drops.getString(mat).split(":");
                            String[] range = parts[0].split("-");
                            int min = Integer.parseInt(range[0].trim());
                            int max = range.length > 1 ? Integer.parseInt(range[1].trim()) : min;
                            double chance = parts.length > 1 ? Double.parseDouble(parts[1].trim()) : 1.0;
                            ml.drops.add(new Drop(m, min, max, chance));
                        } catch (Exception ex) {
                            plugin.getLogger().warning("Bad drop format for " + name + "/" + mat);
                        }
                    }
                }
                loot.put(type, ml);
            }
        }

        ConfigurationSection pr = cfg.getConfigurationSection("prices");
        if (pr != null) {
            for (String mat : pr.getKeys(false)) {
                Material m = Material.matchMaterial(mat);
                if (m != null) prices.put(m, pr.getDouble(mat));
            }
        }
    }

    // ------------------------------------------------------------------ items

    public static String pretty(EntityType type) {
        StringBuilder sb = new StringBuilder();
        for (String w : type.name().toLowerCase().split("_")) {
            if (sb.length() > 0) sb.append(' ');
            sb.append(Character.toUpperCase(w.charAt(0))).append(w.substring(1));
        }
        return sb.toString();
    }

    public ItemStack createItem(EntityType type, int amount) {
        ItemStack item = new ItemStack(Material.SPAWNER, amount);
        BlockStateMeta meta = (BlockStateMeta) item.getItemMeta();
        CreatureSpawner cs = (CreatureSpawner) meta.getBlockState();
        cs.setSpawnedType(type);
        meta.setBlockState(cs);
        meta.setDisplayName(ChatColor.YELLOW + pretty(type) + " Spawner");
        meta.setLore(Arrays.asList(
                ChatColor.GRAY + "Does not spawn mobs.",
                ChatColor.GRAY + "Sneak + right-click an existing",
                ChatColor.GRAY + "spawner to stack this one."));
        meta.getPersistentDataContainer().set(mobKey, PersistentDataType.STRING, type.name());
        item.setItemMeta(meta);
        return item;
    }

    public EntityType mobOf(ItemStack item) {
        if (item == null || item.getType() != Material.SPAWNER || !item.hasItemMeta()) return null;
        String s = item.getItemMeta().getPersistentDataContainer().get(mobKey, PersistentDataType.STRING);
        if (s == null) return null;
        try {
            return EntityType.valueOf(s);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    public void giveItem(Player p, ItemStack item) {
        Map<Integer, ItemStack> left = p.getInventory().addItem(item);
        for (ItemStack rest : left.values()) {
            p.getWorld().dropItemNaturally(p.getLocation(), rest);
        }
    }

    public void giveSpawners(Player p, EntityType type, int amount) {
        while (amount > 0) {
            int n = Math.min(64, amount);
            giveItem(p, createItem(type, n));
            amount -= n;
        }
    }

    // ------------------------------------------------------------------ tracking

    public SpawnerData get(Block b) {
        return spawners.get(SpawnerData.key(b.getWorld().getName(), b.getX(), b.getY(), b.getZ()));
    }

    public boolean isTracked(SpawnerData d) {
        return spawners.get(d.key()) == d;
    }

    public SpawnerData register(Block b, EntityType type) {
        SpawnerData d = new SpawnerData(b.getWorld().getName(), b.getX(), b.getY(), b.getZ(), type, 1);
        spawners.put(d.key(), d);
        if (b.getState() instanceof CreatureSpawner cs) {
            cs.setSpawnedType(type);
            cs.setRequiredPlayerRange(0); // disables vanilla spawning logic completely
            cs.update(true, false);
        }
        return d;
    }

    public void remove(SpawnerData d) {
        spawners.remove(d.key());
    }

    // ------------------------------------------------------------------ production

    public void startTasks() {
        Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 20L, 20L);
        Bukkit.getScheduler().runTaskTimer(plugin, this::save, 6000L, 6000L);
    }

    private void tick() {
        int interval = Math.max(1, plugin.getConfig().getInt("spawn-interval-seconds", 20));
        int perSpawner = plugin.getConfig().getInt("mobs-per-cycle", 4);
        Iterator<SpawnerData> it = spawners.values().iterator();
        while (it.hasNext()) {
            SpawnerData d = it.next();
            World w = Bukkit.getWorld(d.world);
            if (w == null || !w.isChunkLoaded(d.x >> 4, d.z >> 4)) continue;
            if (w.getBlockAt(d.x, d.y, d.z).getType() != Material.SPAWNER) {
                it.remove(); // block vanished (WorldEdit, etc.)
                continue;
            }
            if (++d.timer < interval) continue;
            d.timer = 0;
            produce(d, (long) d.stack * perSpawner);
        }
    }

    private void produce(SpawnerData d, long mobs) {
        MobLoot ml = loot.get(d.type);
        double expPer = ml != null ? ml.exp : plugin.getConfig().getDouble("default-exp", 5);
        d.exp += mobs * expPer;
        if (ml == null) return;
        for (Drop drop : ml.drops) {
            double expected = mobs * drop.chance * (drop.min + drop.max) / 2.0;
            long amt = (long) expected;
            if (random.nextDouble() < expected - amt) amt++;
            if (amt > 0) d.items.merge(drop.material, amt, Long::sum);
        }
    }

    // ------------------------------------------------------------------ GUI actions

    public void takeItems(Player p, SpawnerData d) {
        if (d.items.isEmpty()) {
            p.sendMessage(ChatColor.RED + "This spawner has no items stored.");
            return;
        }
        long moved = 0;
        boolean full = false;
        Iterator<Map.Entry<Material, Long>> it = d.items.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<Material, Long> e = it.next();
            Material mat = e.getKey();
            long remaining = e.getValue();
            while (remaining > 0) {
                int n = (int) Math.min(remaining, mat.getMaxStackSize());
                Map<Integer, ItemStack> left = p.getInventory().addItem(new ItemStack(mat, n));
                int back = 0;
                for (ItemStack s : left.values()) back += s.getAmount();
                moved += n - back;
                remaining -= n - back;
                if (back > 0) {
                    full = true;
                    break;
                }
            }
            if (remaining <= 0) it.remove();
            else e.setValue(remaining);
        }
        p.sendMessage(ChatColor.GREEN + "Took " + moved + " items." + (full ? ChatColor.RED + " Your inventory is full!" : ""));
    }

    public double itemsValue(SpawnerData d) {
        double total = 0;
        for (Map.Entry<Material, Long> e : d.items.entrySet()) {
            Double price = prices.get(e.getKey());
            if (price != null) total += price * e.getValue();
        }
        return total;
    }

    public double expValue(SpawnerData d) {
        return Math.floor(d.exp) * plugin.getConfig().getDouble("exp-price", 0.5);
    }

    public void sellExp(Player p, SpawnerData d) {
        long xp = (long) d.exp;
        if (xp <= 0) {
            p.sendMessage(ChatColor.RED + "This spawner has no exp stored.");
            return;
        }
        if (plugin.economy() == null) {
            p.giveExp((int) Math.min(xp, Integer.MAX_VALUE));
            d.exp -= xp;
            p.sendMessage(ChatColor.GREEN + "No economy found - you received " + xp + " exp instead.");
            return;
        }
        double money = expValue(d);
        plugin.economy().depositPlayer(p, money);
        d.exp -= xp;
        p.sendMessage(ChatColor.GREEN + "Sold " + xp + " exp for $" + String.format("%.2f", money));
    }

    public void sellAll(Player p, SpawnerData d) {
        if (plugin.economy() == null) {
            p.sendMessage(ChatColor.RED + "Selling items needs Vault and an economy plugin.");
            return;
        }
        double itemMoney = 0;
        long itemCount = 0;
        Iterator<Map.Entry<Material, Long>> it = d.items.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<Material, Long> e = it.next();
            Double price = prices.get(e.getKey());
            if (price == null) continue;
            itemMoney += price * e.getValue();
            itemCount += e.getValue();
            it.remove();
        }
        long xp = (long) d.exp;
        double expMoney = expValue(d);
        d.exp -= xp;
        double total = itemMoney + expMoney;
        if (total <= 0) {
            p.sendMessage(ChatColor.RED + "Nothing to sell.");
            return;
        }
        plugin.economy().depositPlayer(p, total);
        p.sendMessage(ChatColor.GREEN + "Sold " + itemCount + " items and " + xp + " exp for $" + String.format("%.2f", total));
    }

    /** Gives back everything stored when the last spawner of a stack is broken. */
    public void refund(Player p, SpawnerData d) {
        for (Map.Entry<Material, Long> e : d.items.entrySet()) {
            long remaining = e.getValue();
            while (remaining > 0) {
                int n = (int) Math.min(remaining, e.getKey().getMaxStackSize());
                giveItem(p, new ItemStack(e.getKey(), n));
                remaining -= n;
            }
        }
        d.items.clear();
        if (d.exp >= 1) p.giveExp((int) Math.min((long) d.exp, Integer.MAX_VALUE));
        d.exp = 0;
    }

    // ------------------------------------------------------------------ persistence

    private File file() {
        return new File(plugin.getDataFolder(), "data.yml");
    }

    public void save() {
        YamlConfiguration yc = new YamlConfiguration();
        int i = 0;
        for (SpawnerData d : spawners.values()) {
            String p = "spawners." + (i++) + ".";
            yc.set(p + "world", d.world);
            yc.set(p + "x", d.x);
            yc.set(p + "y", d.y);
            yc.set(p + "z", d.z);
            yc.set(p + "type", d.type.name());
            yc.set(p + "stack", d.stack);
            yc.set(p + "exp", d.exp);
            yc.set(p + "timer", d.timer);
            for (Map.Entry<Material, Long> e : d.items.entrySet()) {
                yc.set(p + "items." + e.getKey().name(), e.getValue());
            }
        }
        try {
            plugin.getDataFolder().mkdirs();
            yc.save(file());
        } catch (IOException ex) {
            plugin.getLogger().severe("Could not save data.yml: " + ex.getMessage());
        }
    }

    public void load() {
        if (!file().exists()) return;
        YamlConfiguration yc = YamlConfiguration.loadConfiguration(file());
        ConfigurationSection sec = yc.getConfigurationSection("spawners");
        if (sec == null) return;
        for (String k : sec.getKeys(false)) {
            ConfigurationSection s = sec.getConfigurationSection(k);
            try {
                SpawnerData d = new SpawnerData(s.getString("world"), s.getInt("x"), s.getInt("y"), s.getInt("z"),
                        EntityType.valueOf(s.getString("type")), s.getInt("stack", 1));
                d.exp = s.getDouble("exp");
                d.timer = s.getInt("timer");
                ConfigurationSection items = s.getConfigurationSection("items");
                if (items != null) {
                    for (String m : items.getKeys(false)) {
                        Material mat = Material.matchMaterial(m);
                        if (mat != null) d.items.put(mat, items.getLong(m));
                    }
                }
                spawners.put(d.key(), d);
            } catch (Exception ex) {
                plugin.getLogger().warning("Skipping bad spawner entry " + k);
            }
        }
        plugin.getLogger().info("Loaded " + spawners.size() + " spawners.");
    }
}
