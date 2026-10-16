package me.stackspawners;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class SpawnerGUI implements InventoryHolder {

    public static final int INFO = 4, TAKE = 11, EXP = 13, SELL = 15;

    private final SpawnerManager mgr;
    private final SpawnerData data;
    private Inventory inv;

    public SpawnerGUI(SpawnerManager mgr, SpawnerData data) {
        this.mgr = mgr;
        this.data = data;
    }

    public void open(Player p) {
        inv = Bukkit.createInventory(this, 27, ChatColor.DARK_GRAY + SpawnerManager.pretty(data.type) + " Spawner");
        render();
        p.openInventory(inv);
    }

    @Override
    public Inventory getInventory() {
        return inv;
    }

    private ItemStack icon(Material m, String name, List<String> lore) {
        ItemStack it = new ItemStack(m);
        ItemMeta meta = it.getItemMeta();
        meta.setDisplayName(name);
        meta.setLore(lore);
        it.setItemMeta(meta);
        return it;
    }

    private void render() {
        ItemStack filler = icon(Material.GRAY_STAINED_GLASS_PANE, " ", new ArrayList<>());
        for (int i = 0; i < inv.getSize(); i++) inv.setItem(i, filler);

        List<String> info = new ArrayList<>();
        info.add(ChatColor.GRAY + "Stack: " + ChatColor.WHITE + data.stack);
        info.add(ChatColor.GRAY + "Stored items: " + ChatColor.WHITE + data.totalItems());
        info.add(ChatColor.GRAY + "Stored exp: " + ChatColor.WHITE + (long) data.exp);
        inv.setItem(INFO, icon(Material.SPAWNER, ChatColor.YELLOW + SpawnerManager.pretty(data.type) + " Spawner", info));

        List<String> take = new ArrayList<>();
        take.add(ChatColor.GRAY + "Move all stored items into your inventory.");
        take.add("");
        int shown = 0;
        for (Map.Entry<Material, Long> e : data.items.entrySet()) {
            if (shown++ >= 8) {
                take.add(ChatColor.DARK_GRAY + "...and " + (data.items.size() - 8) + " more");
                break;
            }
            take.add(ChatColor.WHITE + "" + e.getValue() + "x " + ChatColor.GRAY + SpawnerManager.pretty(e.getKey()));
        }
        if (data.items.isEmpty()) take.add(ChatColor.DARK_GRAY + "Nothing stored yet.");
        inv.setItem(TAKE, icon(Material.CHEST, ChatColor.GREEN + "Take All Items", take));

        List<String> exp = new ArrayList<>();
        exp.add(ChatColor.GRAY + "Sell all stored exp.");
        exp.add("");
        exp.add(ChatColor.GRAY + "Exp: " + ChatColor.WHITE + (long) data.exp);
        exp.add(ChatColor.GRAY + "Value: " + ChatColor.GOLD + "$" + String.format("%.2f", mgr.expValue(data)));
        inv.setItem(EXP, icon(Material.EXPERIENCE_BOTTLE, ChatColor.AQUA + "Sell All Exp", exp));

        List<String> all = new ArrayList<>();
        all.add(ChatColor.GRAY + "Sell all items and exp.");
        all.add("");
        all.add(ChatColor.GRAY + "Items: " + ChatColor.GOLD + "$" + String.format("%.2f", mgr.itemsValue(data)));
        all.add(ChatColor.GRAY + "Exp: " + ChatColor.GOLD + "$" + String.format("%.2f", mgr.expValue(data)));
        all.add(ChatColor.GRAY + "Total: " + ChatColor.GOLD + "$" + String.format("%.2f", mgr.itemsValue(data) + mgr.expValue(data)));
        inv.setItem(SELL, icon(Material.EMERALD, ChatColor.GOLD + "Sell All", all));
    }

    public void click(Player p, int slot) {
        if (!mgr.isTracked(data)) {
            p.closeInventory();
            return;
        }
        switch (slot) {
            case TAKE -> mgr.takeItems(p, data);
            case EXP -> mgr.sellExp(p, data);
            case SELL -> mgr.sellAll(p, data);
            default -> {
                return;
            }
        }
        render();
    }
}
