package me.stackspawners;

import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.CreatureSpawner;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockDamageEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.SpawnerSpawnEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

public class SpawnerListener implements Listener {

    private final StackSpawners plugin;
    private final SpawnerManager mgr;

    public SpawnerListener(StackSpawners plugin, SpawnerManager mgr) {
        this.plugin = plugin;
        this.mgr = mgr;
    }

    /** Right click: open GUI, or stack when sneaking with a matching spawner item. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent e) {
        if (e.getAction() != Action.RIGHT_CLICK_BLOCK || e.getHand() != EquipmentSlot.HAND) return;
        Block b = e.getClickedBlock();
        if (b == null || b.getType() != Material.SPAWNER) return;
        SpawnerData d = mgr.get(b);
        if (d == null) return;

        Player p = e.getPlayer();
        ItemStack hand = e.getItem();
        EntityType held = mgr.mobOf(hand);

        if (p.isSneaking()) {
            if (held != null) {
                if (held != d.type) return; // different mob: place it normally next to this one
                e.setCancelled(true);
                if (d.stack >= plugin.getConfig().getInt("max-stack", 100000)) {
                    p.sendMessage(ChatColor.RED + "This stack is full.");
                    return;
                }
                d.stack++;
                if (p.getGameMode() != GameMode.CREATIVE) hand.setAmount(hand.getAmount() - 1);
                p.sendMessage(ChatColor.GREEN + "Stacked! " + SpawnerManager.pretty(d.type) + " spawners: " + ChatColor.YELLOW + d.stack);
                return;
            }
            // sneaking with a normal block: let it be placed
            if (hand != null && hand.getType().isBlock()) return;
        }

        e.setCancelled(true);
        new SpawnerGUI(mgr, d).open(p);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent e) {
        EntityType type = mgr.mobOf(e.getItemInHand());
        if (type == null) return;
        mgr.register(e.getBlockPlaced(), type);
    }

    /** Make tracked spawners instantly breakable with any tool or bare hands. */
    @EventHandler(ignoreCancelled = true)
    public void onDamage(BlockDamageEvent e) {
        if (e.getBlock().getType() != Material.SPAWNER) return;
        if (!plugin.getConfig().getBoolean("instant-break", true)) return;
        if (mgr.get(e.getBlock()) != null) e.setInstaBreak(true);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent e) {
        Block b = e.getBlock();
        if (b.getType() != Material.SPAWNER) return;
        SpawnerData d = mgr.get(b);
        if (d == null) return;

        Player p = e.getPlayer();
        int give = p.isSneaking() ? d.stack : 1; // sneak = take the whole stack

        if (give < d.stack) {
            e.setCancelled(true);
            d.stack -= give;
            mgr.giveSpawners(p, d.type, give);
            p.sendMessage(ChatColor.YELLOW + "Removed 1 spawner. Remaining: " + d.stack);
            return;
        }

        e.setDropItems(false);
        e.setExpToDrop(0);
        mgr.remove(d);
        mgr.giveSpawners(p, d.type, give);
        mgr.refund(p, d);
    }

    @EventHandler(ignoreCancelled = true)
    public void onSpawn(SpawnerSpawnEvent e) {
        CreatureSpawner cs = e.getSpawner();
        if (cs != null && mgr.get(cs.getBlock()) != null) e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent e) {
        e.blockList().removeIf(b -> b.getType() == Material.SPAWNER && mgr.get(b) != null);
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent e) {
        e.blockList().removeIf(b -> b.getType() == Material.SPAWNER && mgr.get(b) != null);
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!(e.getView().getTopInventory().getHolder() instanceof SpawnerGUI gui)) return;
        e.setCancelled(true);
        if (e.getClickedInventory() == e.getView().getTopInventory() && e.getWhoClicked() instanceof Player p) {
            gui.click(p, e.getSlot());
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent e) {
        if (e.getView().getTopInventory().getHolder() instanceof SpawnerGUI) e.setCancelled(true);
    }
}
