package io.shantek;

import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.Container;
import org.bukkit.block.DoubleChest;
import org.bukkit.block.ShulkerBox;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.inventory.*;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.BlockStateMeta;

import java.util.*;

public class ShulkerListener implements Listener {

    private final ShulkerPacksContinued plugin;
    private final Map<Player, ItemStack> soloSessions = new HashMap<>();
    private final Map<Player, String> lockedKeys = new HashMap<>(); // containerKey per player

    public ShulkerListener(ShulkerPacksContinued plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onRightClick(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        if (!player.hasPermission("shantek.shulkerpacks.use")) return;

        ItemStack item = event.getItem();
        if (item == null || item.getAmount() > 1 || !item.getType().toString().contains("SHULKER_BOX")) return;

        if (event.getAction() == Action.RIGHT_CLICK_AIR) {
            event.setCancelled(true);
            openShulker(player, item, null, -1);
        } else if (event.getAction() == Action.RIGHT_CLICK_BLOCK && event.getClickedBlock() != null) {
            if (!(event.getClickedBlock().getState() instanceof Container container)) return;
            Inventory containerInv = container.getInventory();
            int slot = getExactSlot(item, containerInv);
            if (slot != -1) {
                String key = generateSessionKey(container, slot);
                if (isLocked(key)) {
                    player.sendMessage(ChatColor.RED + "Someone is already editing this shulker.");
                    return;
                }
                event.setCancelled(true);
                lock(key, player);
                openShulker(player, item, container, slot);
            }
        }
    }

    private void openShulker(Player player, ItemStack item, Container container, int slot) {
        if (!(item.getItemMeta() instanceof BlockStateMeta meta)) return;
        if (!(meta.getBlockState() instanceof ShulkerBox shulker)) return;

        Inventory inv = Bukkit.createInventory(null, 27, getShulkerTitle(item));
        inv.setContents(shulker.getInventory().getContents());

        player.openInventory(inv);
        player.playSound(player.getLocation(), Sound.BLOCK_SHULKER_BOX_OPEN, plugin.volume, 1);
        soloSessions.put(player, item);

        // Store key only if it's in a container
        if (container != null && slot != -1) {
            lockedKeys.put(player, generateSessionKey(container, slot));
        }
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        Player player = (Player) event.getWhoClicked();
        if (!player.hasPermission("shantek.shulkerpacks.use")) return;

        ItemStack clicked = event.getCurrentItem();

        // Prevent moving the shulker being edited
        if (clicked != null && clicked.equals(soloSessions.get(player))) {
            event.setCancelled(true);
            player.sendMessage(ChatColor.RED + "You can't move a shulker you're editing.");
            return;
        }

        // Prevent moving a shulker that is locked by someone else
        if (clicked != null && clicked.getType().toString().contains("SHULKER_BOX")) {
            Inventory clickedInv = event.getClickedInventory();
            if (clickedInv != null && clickedInv.getHolder() instanceof Container container) {
                int slot = getExactSlot(clicked, clickedInv);
                if (slot != -1) {
                    String key = generateSessionKey(container, slot);
                    if (isLocked(key) && !key.equals(lockedKeys.get(player))) {
                        event.setCancelled(true);
                        player.sendMessage(ChatColor.RED + "That shulker is currently locked by another player.");
                        return;
                    }
                }
            }
        }

        // Open shulker in container via right-click
        if (event.getClick() == ClickType.RIGHT && clicked != null &&
                clicked.getType().toString().contains("SHULKER_BOX") &&
                clicked.getAmount() == 1 &&
                event.getClickedInventory() != null &&
                !(event.getClickedInventory().getHolder() instanceof Player)) {

            Inventory containerInv = event.getClickedInventory();
            if (containerInv.getHolder() instanceof Container container) {
                int slot = getExactSlot(clicked, containerInv);
                if (slot != -1) {
                    String key = generateSessionKey(container, slot);
                    if (isLocked(key)) {
                        event.setCancelled(true);
                        player.sendMessage(ChatColor.RED + "Someone is already editing this shulker.");
                        return;
                    }

                    event.setCancelled(true); // prevent normal right-click behavior
                    lock(key, player);
                    openShulker(player, clicked, container, slot);
                }
            }
        }
    }


    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        Player player = (Player) event.getWhoClicked();
        if (!player.hasPermission("shantek.shulkerpacks.use")) return;

        for (ItemStack item : event.getNewItems().values()) {
            if (item.equals(soloSessions.getOrDefault(player, null))) {
                event.setCancelled(true);
                player.sendMessage(ChatColor.RED + "You can't drag a shulker you're editing.");
            }
        }
    }

    @EventHandler
    public void onInventoryMoveItem(InventoryMoveItemEvent event) {
        Inventory source = event.getSource();
        for (Map.Entry<Player, String> entry : lockedKeys.entrySet()) {
            Player locker = entry.getKey();
            String[] parts = entry.getValue().split(":");
            if (parts.length != 5) continue;
            World world = Bukkit.getWorld(parts[0]);
            int x = Integer.parseInt(parts[1]);
            int y = Integer.parseInt(parts[2]);
            int z = Integer.parseInt(parts[3]);

            if (source.getLocation() != null && source.getLocation().getBlockX() == x &&
                    source.getLocation().getBlockY() == y &&
                    source.getLocation().getBlockZ() == z &&
                    source.getLocation().getWorld().equals(world)) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        Block block = event.getBlock();

        // Only care about containers
        if (!(block.getState() instanceof Container container)) return;

        Location loc = container.getLocation();

        // Normalize to left side if it's a double chest
        if (container instanceof DoubleChest) {
            DoubleChest doubleChest = (DoubleChest) container;
            InventoryHolder left = doubleChest.getLeftSide();
            if (left instanceof Container) {
                loc = ((Container) left).getLocation();
            }
        }

        String prefix = loc.getWorld().getName() + ":" + loc.getBlockX() + ":" + loc.getBlockY() + ":" + loc.getBlockZ();

        // Check for any locked key that matches this location
        for (String locked : lockedKeys.values()) {
            if (locked.startsWith(prefix)) {
                event.setCancelled(true);
                event.getPlayer().sendMessage(ChatColor.RED + "This container contains an open shulker and cannot be broken.");
                return;
            }
        }
    }



    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        Player player = (Player) event.getPlayer();

        if (!soloSessions.containsKey(player)) return;

        ItemStack item = soloSessions.remove(player);
        if (!(item.getItemMeta() instanceof BlockStateMeta meta)) return;
        if (!(meta.getBlockState() instanceof ShulkerBox shulker)) return;

        Inventory inv = player.getOpenInventory().getTopInventory();
        shulker.getInventory().setContents(inv.getContents());
        meta.setBlockState(shulker);
        item.setItemMeta(meta);

        if (lockedKeys.containsKey(player)) {
            String key = lockedKeys.remove(player);
            String[] parts = key.split(":");
            World world = Bukkit.getWorld(parts[0]);
            int x = Integer.parseInt(parts[1]);
            int y = Integer.parseInt(parts[2]);
            int z = Integer.parseInt(parts[3]);
            int slot = Integer.parseInt(parts[4]);

            if (world != null) {
                Location loc = new Location(world, x, y, z);
                if (loc.getBlock().getState() instanceof Container container) {
                    Inventory containerInv = container.getInventory();
                    ItemStack currentItem = containerInv.getItem(slot);

                    // ✅ Confirm the shulker is still there
                    if (currentItem != null && currentItem.isSimilar(item)) {
                        containerInv.setItem(slot, item);
                        container.update();
                    } else {
                        player.sendMessage(ChatColor.RED + "The shulker was moved during editing. Changes were not saved.");
                    }
                }
            }
        }

        player.playSound(player.getLocation(), Sound.BLOCK_SHULKER_BOX_CLOSE, plugin.volume, 1);
    }


    private boolean isLocked(String key) {
        return lockedKeys.containsValue(key);
    }

    private void lock(String key, Player player) {
        lockedKeys.put(player, key);
    }

    private String generateSessionKey(Container container, int slot) {
        Location loc = container.getLocation();

        // Normalize for double chests
        if (container instanceof DoubleChest) {
            DoubleChest doubleChest = (DoubleChest) container;
            InventoryHolder left = doubleChest.getLeftSide();
            if (left instanceof Container) {
                loc = ((Container) left).getLocation(); // Always use the left side for locking
            }
        }

        return loc.getWorld().getName() + ":" + loc.getBlockX() + ":" + loc.getBlockY() + ":" + loc.getBlockZ() + ":" + slot;
    }


    private int getExactSlot(ItemStack target, Inventory inventory) {
        for (int i = 0; i < inventory.getSize(); i++) {
            ItemStack current = inventory.getItem(i);
            if (current != null && current.equals(target)) return i;
        }
        return -1;
    }

    private String getShulkerTitle(ItemStack item) {
        if (item.getItemMeta() != null && item.getItemMeta().hasDisplayName()) {
            return item.getItemMeta().getDisplayName();
        }
        return ChatColor.DARK_PURPLE + "Shulker Box";
    }
}