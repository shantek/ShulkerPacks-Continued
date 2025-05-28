package io.shantek;

import org.bukkit.*;
import org.bukkit.block.ShulkerBox;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.inventory.*;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BlockStateMeta;

import java.util.ArrayList;
import java.util.List;

public class ShulkerListener implements Listener {

    public ShulkerPacksContinued main;
    public ShulkerListener(ShulkerPacksContinued plugin) {
        this.main = plugin; //set it equal to an instance of main
    }

    /*
    Saves the shulker on inventory drag if its open
     */
    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        if (event.getWhoClicked() instanceof Player player && ShulkerPacksContinued.openshulkers.containsKey(player)) {
            // Prevent saving to an item that no longer exists in inventory
            if (!isShulkerStillValid(player)) {
                event.setCancelled(true);
                player.closeInventory();
                player.sendMessage(ChatColor.RED + "Shulker was moved or taken. Changes not allowed.");
                return;
            }
        }

        // Delay save to next tick
        Bukkit.getServer().getScheduler().scheduleSyncDelayedTask(main, () ->
                saveShulker((Player) event.getWhoClicked(), event.getView().getTitle()), 1);
    }



    @EventHandler
    public void onInventoryMoveItem(InventoryMoveItemEvent event) {
        List<Player> closeInventories = new ArrayList<>();
        for (Player p : ShulkerPacksContinued.openshulkers.keySet()) {
            if (ShulkerPacksContinued.openshulkers.get(p).equals(event.getItem())) {
                closeInventories.add(p);
            }
        }
        for (Player p : closeInventories) {
            if (event.getInitiator().getLocation() != null && event.getInitiator().getLocation().getWorld() == p.getWorld()) {
                if (event.getInitiator().getLocation().distance(p.getLocation()) < 6) {
                    p.closeInventory();
                }
            }
        }
    }

    /*
    Opens the shulker if its not in a weird inventory, then saves it
     */
    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (event.isCancelled()) return;

        Player player = (Player) event.getWhoClicked();
        // Prevent saving to an item that no longer exists in inventory
        if (!isShulkerStillValid(player)) {
            player.sendMessage(ChatColor.RED + "Shulker was moved or taken. Closing to prevent item duplication.");
            event.setCancelled(true);
            player.closeInventory();
            return;
        }

        if (checkIfOpen(event.getCurrentItem())) { // Trying to move an open shulker
            if (event.getClick() != ClickType.RIGHT) {
                event.setCancelled(true);
                return;
            }
        }

        if (event.getClickedInventory() != null && event.getWhoClicked() instanceof Player) {

            if (event.getCurrentItem() != null && ShulkerPacksContinued.openshulkers.containsKey(player) &&
                    event.getCurrentItem().equals(ShulkerPacksContinued.openshulkers.get(player))) {
                event.setCancelled(true);
                return;
            }

            InventoryType type = event.getClickedInventory().getType();
            String typeStr = type.toString();

            if (type == InventoryType.CHEST && (!main.canopeninchests || !player.hasPermission("shantek.shulkerpacks.chests"))) {
                return;
            }

            if (typeStr.equals("WORKBENCH") || typeStr.equals("ANVIL") || typeStr.equals("BEACON") || typeStr.equals("MERCHANT") ||
                    typeStr.equals("ENCHANTING") || typeStr.equals("GRINDSTONE") || typeStr.equals("CARTOGRAPHY") ||
                    typeStr.equals("LOOM") || typeStr.equals("STONECUTTER")) {
                return;
            }

            if (type == InventoryType.CRAFTING && event.getRawSlot() >= 1 && event.getRawSlot() <= 4) {
                return;
            }

            if (event.getClickedInventory() == player.getInventory() &&
                    (!main.canopenininventory || !player.hasPermission("shantek.shulkerpacks.inventory"))) {
                return;
            }

            if (event.getSlotType() == InventoryType.SlotType.RESULT) {
                return;
            }

            if (event.getClickedInventory().getHolder() != null &&
                    event.getClickedInventory().getHolder().getClass().toString().endsWith(".CraftBarrel") &&
                    !main.canopeninbarrels) {
                return;
            }

            if (!main.canopeninenderchest && type == InventoryType.ENDER_CHEST) {
                return;
            }

            for (String str : main.blacklist) {
                if (ChatColor.stripColor(player.getOpenInventory().getTitle())
                        .contains(ChatColor.stripColor(ChatColor.translateAlternateColorCodes('&', str)))) {
                    return;
                }
            }

            if (!main.shiftclicktoopen || event.isShiftClick()) {
                boolean wasCancelled = event.isCancelled();
                event.setCancelled(true);
                if (event.isRightClick() && openInventoryIfShulker(event.getCurrentItem(), player)) {
                    main.fromhand.remove(player);
                    return;
                }
                event.setCancelled(wasCancelled);
            }

            // Schedule a save to ensure consistency on slot updates
            Bukkit.getServer().getScheduler().scheduleSyncDelayedTask(main, () -> {
                if (!saveShulker(player, event.getView().getTitle())) {
                    event.setCancelled(true);
                }
            }, 1);
        }
    }

    // Deals with multiple people opening the same shulker
    private static boolean checkIfOpen(ItemStack shulker) {
        for (ItemStack i : ShulkerPacksContinued.openshulkers.values()) {
            if (i.equals(shulker)) {
                return true;
            }
        }
        return false;
    }

    /*
    Saves the shulker if its open, then removes the current open shulker from the player data
     */
    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (event.getPlayer() instanceof Player) {
            Player player = (Player) event.getPlayer();
            if (saveShulker(player, player.getOpenInventory().getTitle())) {
                player.playSound(player.getLocation(), Sound.BLOCK_SHULKER_BOX_CLOSE, main.volume, 1);
                if (main.openpreviousinv) {
                    openPreviousInventory(player);
                }
            }
            ShulkerPacksContinued.openshulkers.remove(player);
        }
    }


    private void openPreviousInventory(Player player) {
        InventoryType type = main.opencontainer.get(player).getType();
        if (type != InventoryType.CRAFTING && type != InventoryType.SHULKER_BOX) {
            Bukkit.getServer().getScheduler().scheduleSyncDelayedTask(main, new Runnable() {
                @Override
                public void run() {
                    player.openInventory(main.opencontainer.get(player));
                    main.opencontainer.remove(player);
                }
            }, 1);
        }
    }


    /*
    Opens the shulker if the air was clicked with one
     */
    @EventHandler
    public void onClickAir(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        if (main.canopeninair && (event.getClickedBlock() == null || event.getClickedBlock().getType() == Material.AIR)) {
            if ((!main.shiftclicktoopen || player.isSneaking())) {
                if (event.getAction() == Action.RIGHT_CLICK_AIR) {
                     if (main.canopeninair && player.hasPermission("shantek.shulkerpacks.air")) {
                         ItemStack item = event.getItem();
                         openInventoryIfShulker(item, event.getPlayer());
                         main.fromhand.put(player, true);
                     }
                }
            }
        }
    }

    @EventHandler
    public void onShulkerPlace(BlockPlaceEvent event) {
        if (event.getBlockPlaced().getType().toString().contains("SHULKER_BOX")) {
            if (!main.canplaceshulker) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler
    public void onPlayerHit(EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof Player && event.getEntity() instanceof Player) {
            main.setPvpTimer((Player) event.getDamager());
            main.setPvpTimer((Player) event.getEntity());
        }
    }

    @EventHandler
    public void onPlayerShoot(ProjectileHitEvent event) {
        if (event.getHitEntity() instanceof Player && event.getEntity().getShooter() instanceof Player) {
            main.setPvpTimer((Player) event.getEntity().getShooter());
            main.setPvpTimer((Player) event.getHitEntity());
        }
    }

    /*
    Saves the shulker data in the itemmeta
     */
    public boolean saveShulker(Player player, String title) {
        try {
            if (!ShulkerPacksContinued.openshulkers.containsKey(player)) return false;

            if (!isShulkerStillValid(player)) {
                player.sendMessage(ChatColor.RED + "Shulker was moved or taken. Closing to prevent item duplication.");
                player.closeInventory();
                return false;
            }

            ItemStack original = ShulkerPacksContinued.openshulkers.get(player);
            if (!title.equals(main.defaultname) &&
                    (!original.hasItemMeta() || !original.getItemMeta().hasDisplayName() ||
                            !original.getItemMeta().getDisplayName().equals(title))) {
                return false;
            }

            BlockStateMeta meta = (BlockStateMeta) original.getItemMeta();
            ShulkerBox shulker = (ShulkerBox) meta.getBlockState();
            shulker.getInventory().setContents(main.openinventories.get(player).getContents());
            meta.setBlockState(shulker);
            original.setItemMeta(meta);
            updateAllInventories(original);
            return true;

        } catch (Exception e) {
            ShulkerPacksContinued.openshulkers.remove(player);
            player.closeInventory();
            return false;
        }
    }


    private void updateAllInventories(ItemStack item) {
        for (Player p : ShulkerPacksContinued.openshulkers.keySet()) {
            if (ShulkerPacksContinued.openshulkers.get(p).equals(item)) {
                BlockStateMeta meta = (BlockStateMeta) item.getItemMeta();
                ShulkerBox shulker = (ShulkerBox) meta.getBlockState();
                p.getOpenInventory().getTopInventory().setContents(shulker.getInventory().getContents());
                p.updateInventory();
            }
        }
    }

    /*
    Opens the shulker inventory with the contents of the shulker
     */
    public boolean openInventoryIfShulker(ItemStack item, Player player) {
        if (player.hasPermission("shantek.shulkerpacks.use")) {
            if (item != null) {
                if (item.getAmount() == 1 && item.getType().toString().contains("SHULKER")) {

                    if (main.getPvpTimer(player)) {
                        player.sendMessage(main.prefix + ChatColor.RED + "You cannot open shulkerboxes in combat!");
                        return false;
                    }

                    if (item.getItemMeta() instanceof BlockStateMeta) {
                        BlockStateMeta meta = (BlockStateMeta) item.getItemMeta();
                        if (meta != null && meta.getBlockState() instanceof ShulkerBox) {
                            ShulkerBox shulker = (ShulkerBox) meta.getBlockState();
                            Inventory inv;
                            if (meta.hasDisplayName()) {
                                inv = Bukkit.createInventory(new ShulkerHolder(), InventoryType.SHULKER_BOX, meta.getDisplayName());
                            } else {
                                inv = Bukkit.createInventory(new ShulkerHolder(), InventoryType.SHULKER_BOX, main.defaultname);
                            }
                            inv.setContents(shulker.getInventory().getContents());

                            main.opencontainer.put(player, player.getOpenInventory().getTopInventory());

                            Bukkit.getServer().getScheduler().scheduleSyncDelayedTask(main, new Runnable() {
                                @Override
                                public void run() {
                                    player.openInventory(inv);
                                    player.playSound(player.getLocation(), Sound.BLOCK_SHULKER_BOX_OPEN, main.volume, 1);
                                    ShulkerPacksContinued.openshulkers.put(player, item);
                                    main.openinventories.put(player, player.getOpenInventory().getTopInventory());
                                }
                            }, 1);
                            return true;
                        }
                    }
                }
            }
        }
        return false;
    }

    void checkIfValid() {
        Bukkit.getScheduler().scheduleSyncRepeatingTask(main, new Runnable() {
            @Override
            public void run() {
                for (Player p : ShulkerPacksContinued.openshulkers.keySet()) {
                    ItemStack openShulker = ShulkerPacksContinued.openshulkers.get(p);

                    // If the open shulker is now air, close the GUI
                    if (openShulker.getType() == Material.AIR) {
                        p.closeInventory();
                        continue;
                    }

                    // If player is too far from their original container, close the GUI
                    if (main.opencontainer.containsKey(p)) {
                        Inventory container = main.opencontainer.get(p);
                        if (container != null && container.getLocation() != null) {
                            Location containerLoc = container.getLocation();
                            if (containerLoc.getWorld() == p.getWorld() &&
                                    containerLoc.distance(p.getLocation()) > 6) {
                                p.closeInventory();
                            }
                        }
                    }
                }
            }
        }, 1L, 1L);
    }

    private boolean isShulkerStillValid(Player player) {
        if (!ShulkerPacksContinued.openshulkers.containsKey(player)) return false;

        ItemStack openShulker = ShulkerPacksContinued.openshulkers.get(player);
        if (openShulker == null || openShulker.getType() == Material.AIR) {
            return false;
        }

        for (ItemStack item : player.getInventory().getContents()) {
            if (item != null && item.equals(openShulker)) {
                return true;
            }
        }

        return false;
    }

}
