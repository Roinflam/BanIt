package pers.tany.banit.listenevent;

import org.bukkit.Chunk;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.*;
import org.bukkit.inventory.CraftingInventory;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;
import pers.tany.banit.Main;
import pers.tany.banit.utils.BanInfo;
import pers.tany.yukinoaapi.interfacepart.block.IBlock;
import pers.tany.yukinoaapi.interfacepart.entity.IEntity;
import pers.tany.yukinoaapi.interfacepart.inventory.IInventoryItem;
import pers.tany.yukinoaapi.interfacepart.item.IItem;
import pers.tany.yukinoaapi.interfacepart.other.IRandom;
import pers.tany.yukinoaapi.interfacepart.other.IString;
import pers.tany.yukinoaapi.interfacepart.player.IPlayer;

public class Events implements Listener {

    @EventHandler
    public void onPlayerLogin(PlayerLoginEvent evt) {
        if (Main.config.getBoolean("CheckBackPack")) {
            Player player = evt.getPlayer();
            if (!player.hasPermission("bi.op")) {
                Inventory inventory = player.getInventory();
                World world = player.getWorld();
                for (ItemStack itemStack : inventory.getContents()) {
                    if (!IItem.isEmpty(itemStack)) {
                        String[] worlds = new String[]{world.getName(), Main.config.getString("OverAll")};
                        for (String worldName : worlds) {
                            String serialNumber = BanInfo.isBannedItemStack(player, worldName, itemStack);
                            if (serialNumber != null) {
                                boolean clear = Main.data.getBoolean("BanInfo." + worldName + ".Item." + serialNumber + ".Clear");
                                String mode = Main.data.getString("BanInfo." + worldName + ".Item." + serialNumber + ".Mode");
                                if (clear) {
                                    IInventoryItem.deductItem(inventory, itemStack);
                                    player.sendMessage(IString.color(Main.message.getString("ItemClearMessage")));
                                }
                                break;
                            }
                        }
                    }
                }
            }
        }
    }

    @EventHandler
    public void onPlayerItemHeld(PlayerItemHeldEvent evt) {
        Player player = evt.getPlayer();
        Inventory inventory = player.getInventory();
        ItemStack itemStack = inventory.getItem(evt.getNewSlot());
        World world = player.getWorld();
        if (!IItem.isEmpty(itemStack)) {
            String[] worlds = new String[]{world.getName(), Main.config.getString("OverAll")};
            for (String worldName : worlds) {
                String serialNumber = BanInfo.isBannedEnchantment(player, worldName, itemStack);
                if (serialNumber != null) {
                    Enchantment ench = Enchantment.getByName(Main.data.getString("BanInfo." + worldName + ".Ench." + serialNumber + ".EnchName"));
                    boolean clear = Main.data.getBoolean("BanInfo." + worldName + ".Ench." + serialNumber + ".Clear");
                    String mode = Main.data.getString("BanInfo." + worldName + ".Ench." + serialNumber + ".Mode");
                    if (clear) {
                        itemStack.removeEnchantment(ench);
                        player.sendMessage(IString.color(Main.message.getString("EnchClearMessage")));
                    }
                    break;
                }
                serialNumber = BanInfo.isBannedItemStack(player, worldName, itemStack);
                if (serialNumber != null) {
                    boolean clear = Main.data.getBoolean("BanInfo." + worldName + ".Item." + serialNumber + ".Clear");
                    String mode = Main.data.getString("BanInfo." + worldName + ".Item." + serialNumber + ".Mode");
                    if (clear) {
                        inventory.setItem(evt.getNewSlot(), null);
                        player.sendMessage(IString.color(Main.message.getString("ItemClearMessage")));
                    }
                    break;
                }
            }
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent evt) {
        if (Main.config.getBoolean("InventoryCheck")) {
            Player player = (Player) evt.getWhoClicked();
            World world = player.getWorld();
            ItemStack itemStack = evt.getCurrentItem();
            if (!IItem.isEmpty(itemStack)) {
                String[] worlds = new String[]{world.getName(), Main.config.getString("OverAll")};
                for (String worldName : worlds) {
                    String serialNumber = BanInfo.isBannedItemStack(player, worldName, itemStack);
                    if (serialNumber != null) {
                        if (player.hasPermission("bi.ignore." + serialNumber) || player.hasPermission("bi.op")) {
                            continue;
                        }
                        boolean clear = Main.data.getBoolean("BanInfo." + worldName + ".Item." + serialNumber + ".Clear");
                        String mode = Main.data.getString("BanInfo." + worldName + ".Item." + serialNumber + ".Mode");
                        if (clear) {
                            evt.setCancelled(true);
                            player.sendMessage(IString.color(Main.message.getString("ItemClearMessage")));
                        }
                        break;
                    }
                }
            }
        }
        if (evt.getWhoClicked() instanceof Player && evt.getClickedInventory() != null) {
            if (evt.getClickedInventory().getType().equals(InventoryType.PLAYER)) {
                Player player = (Player) evt.getWhoClicked();
                World world = player.getWorld();
                ItemStack itemStack = evt.getCurrentItem();
                if (!IItem.isEmpty(itemStack)) {
                    String[] worlds = new String[]{world.getName(), Main.config.getString("OverAll")};
                    for (String worldName : worlds) {
                        String serialNumber = BanInfo.isBannedItemStack(player, worldName, itemStack);
                        if (serialNumber != null) {
                            if (player.hasPermission("bi.ignore." + serialNumber) || player.hasPermission("bi.op")) {
                                continue;
                            }
                            boolean clear = Main.data.getBoolean("BanInfo." + worldName + ".Item." + serialNumber + ".Clear");
                            String mode = Main.data.getString("BanInfo." + worldName + ".Item." + serialNumber + ".Mode");
                            if (clear) {
                                evt.setCancelled(true);
                                IInventoryItem.deductItem(player.getInventory(), itemStack);
                                player.sendMessage(IString.color(Main.message.getString("ItemClearMessage")));
                            }
                            break;
                        }
                    }

                }
            }
        }
    }

    @EventHandler
    public void onPlayerPickupItem(PlayerPickupItemEvent evt) {
        Player player = evt.getPlayer();
        World world = player.getWorld();
        ItemStack itemStack = evt.getItem().getItemStack();
        if (!IItem.isEmpty(itemStack)) {
            String[] worlds = new String[]{world.getName(), Main.config.getString("OverAll")};
            for (String worldName : worlds) {
                String serialNumber = BanInfo.isBannedEnchantment(player, worldName, itemStack);
                if (serialNumber != null) {
                    Enchantment ench = Enchantment.getByName(Main.data.getString("BanInfo." + worldName + ".Ench." + serialNumber + ".EnchName"));
                    boolean clear = Main.data.getBoolean("BanInfo." + worldName + ".Ench." + serialNumber + ".Clear");
                    String mode = Main.data.getString("BanInfo." + worldName + ".Ench." + serialNumber + ".Mode");
                    if (clear) {
                        itemStack.removeEnchantment(ench);
                        player.sendMessage(IString.color(Main.message.getString("EnchClearMessage")));
                    }
                    break;
                }
                serialNumber = BanInfo.isBannedItemStack(player, worldName, itemStack);
                if (serialNumber != null) {
                    boolean clear = Main.data.getBoolean("BanInfo." + worldName + ".Item." + serialNumber + ".Clear");
                    String mode = Main.data.getString("BanInfo." + worldName + ".Item." + serialNumber + ".Mode");
                    if (clear) {
                        evt.setCancelled(true);
                        evt.getItem().remove();
                        player.sendMessage(IString.color(Main.message.getString("ItemClearMessage")));
                    }
                    break;
                }
            }
        }
    }

    @EventHandler
    public void onPlayerDropItem(PlayerDropItemEvent evt) {
        if (!Main.config.getBoolean("Optimization") || IRandom.percentageChance(10)) {
            Player player = evt.getPlayer();
            World world = player.getWorld();
            ItemStack itemStack = evt.getItemDrop().getItemStack();
            if (!IItem.isEmpty(itemStack)) {
                String[] worlds = new String[]{world.getName(), Main.config.getString("OverAll")};
                for (String worldName : worlds) {
                    String serialNumber = BanInfo.isBannedEnchantment(player, worldName, itemStack);
                    if (serialNumber != null) {
                        Enchantment ench = Enchantment.getByName(Main.data.getString("BanInfo." + worldName + ".Ench." + serialNumber + ".EnchName"));
                        boolean clear = Main.data.getBoolean("BanInfo." + worldName + ".Ench." + serialNumber + ".Clear");
                        String mode = Main.data.getString("BanInfo." + worldName + ".Ench." + serialNumber + ".Mode");
                        if (clear) {
                            itemStack.removeEnchantment(ench);
                            player.sendMessage(IString.color(Main.message.getString("EnchClearMessage")));
                        } else if (mode.equals("d")) {
                            evt.setCancelled(true);
                            player.sendMessage(IString.color(Main.message.getString("EnchNoDropMessage")));
                        }
                        break;
                    }
                    serialNumber = BanInfo.isBannedItemStack(player, worldName, itemStack);
                    if (serialNumber != null) {
                        boolean clear = Main.data.getBoolean("BanInfo." + worldName + ".Item." + serialNumber + ".Clear");
                        String mode = Main.data.getString("BanInfo." + worldName + ".Item." + serialNumber + ".Mode");
                        if (clear) {
                            evt.getItemDrop().remove();
                            player.sendMessage(IString.color(Main.message.getString("ItemClearMessage")));
                        } else if (mode.equals("d")) {
                            evt.setCancelled(true);
                            player.sendMessage(IString.color(Main.message.getString("ItemNoDropMessage")));
                        }
                        break;
                    }
                }
            }
        }
    }

    @EventHandler
    public void onPlayerInteractEvent(PlayerInteractEvent evt) {
        Player player = evt.getPlayer();
        World world = player.getWorld();
        ItemStack itemStack = evt.getItem();
        String[] worlds = new String[]{world.getName(), Main.config.getString("OverAll")};
        if (evt.getAction().equals(Action.LEFT_CLICK_BLOCK) || evt.getAction().equals(Action.RIGHT_CLICK_BLOCK)) {
            Block block = evt.getClickedBlock();
            for (String worldName : worlds) {
                String serialNumber = BanInfo.isBannedBlock(worldName, block);
                if (serialNumber != null) {
                    boolean clear = Main.data.getBoolean("BanInfo." + worldName + ".Block." + serialNumber + ".Clear");
                    String mode = Main.data.getString("BanInfo." + worldName + ".Block." + serialNumber + ".Mode");
                    if (clear) {
                        evt.setCancelled(true);
                        block.setType(Material.AIR);
                        player.sendMessage(IString.color(Main.message.getString("BlockClearMessage")));
                    } else if (mode.equals("a")) {
                        evt.setCancelled(true);
                        player.sendMessage(IString.color(Main.message.getString("BlockNoAllMessage")));
                    } else if (mode.equals("i")) {
                        evt.setCancelled(true);
                        player.sendMessage(IString.color(Main.message.getString("BlockNoInteractMessage")));
                    }
                    break;
                }
            }
        }
        if (!IItem.isEmpty(itemStack)) {
            for (String worldName : worlds) {
                String serialNumber = BanInfo.isBannedEnchantment(player, worldName, itemStack);
                if (serialNumber != null) {
                    Enchantment ench = Enchantment.getByName(Main.data.getString("BanInfo." + worldName + ".Ench." + serialNumber + ".EnchName"));
                    boolean clear = Main.data.getBoolean("BanInfo." + worldName + ".Ench." + serialNumber + ".Clear");
                    String mode = Main.data.getString("BanInfo." + worldName + ".Ench." + serialNumber + ".Mode");
                    if (clear) {
                        itemStack.removeEnchantment(ench);
                        player.sendMessage(IString.color(Main.message.getString("EnchClearMessage")));
                    } else if (mode.equals("a")) {
                        evt.setCancelled(true);
                        if(Main.config.getBoolean("DropItem")){
                            BanInfo.cantUse(player, itemStack);
                        } else {
                            int slot = IRandom.randomNumber(0, 8);
                            player.getInventory().setHeldItemSlot(slot == player.getInventory().getHeldItemSlot() ? 0 : slot);
                        }
                        player.sendMessage(IString.color(Main.message.getString("EnchNoAllMessage")));
                    } else if (mode.equals("r") && (evt.getAction().equals(Action.RIGHT_CLICK_AIR) || evt.getAction().equals(Action.RIGHT_CLICK_BLOCK))) {
                        evt.setCancelled(true);
                        if(Main.config.getBoolean("DropItem")){
                            BanInfo.cantUse(player, itemStack);
                        } else {
                            int slot = IRandom.randomNumber(0, 8);
                            player.getInventory().setHeldItemSlot(slot == player.getInventory().getHeldItemSlot() ? 0 : slot);
                        }
                        player.sendMessage(IString.color(Main.message.getString("EnchNoRightMessage")));
                    } else if (mode.equals("l") && (evt.getAction().equals(Action.LEFT_CLICK_AIR) || evt.getAction().equals(Action.LEFT_CLICK_BLOCK))) {
                        evt.setCancelled(true);
                        if(Main.config.getBoolean("DropItem")){
                            BanInfo.cantUse(player, itemStack);
                        } else {
                            int slot = IRandom.randomNumber(0, 8);
                            player.getInventory().setHeldItemSlot(slot == player.getInventory().getHeldItemSlot() ? 0 : slot);
                        }
                        player.sendMessage(IString.color(Main.message.getString("EnchNoLeftMessage")));
                    }
                    break;
                }
                serialNumber = BanInfo.isBannedItemStack(player, worldName, itemStack);
                if (serialNumber != null) {
                    boolean clear = Main.data.getBoolean("BanInfo." + worldName + ".Item." + serialNumber + ".Clear");
                    String mode = Main.data.getString("BanInfo." + worldName + ".Item." + serialNumber + ".Mode");
                    if (clear) {
                        evt.setCancelled(true);
                        itemStack.setAmount(0);
                        player.setItemInHand(null);
                        player.sendMessage(IString.color(Main.message.getString("ItemClearMessage")));
                        return;
                    } else if (mode.equals("a")) {
                        evt.setCancelled(true);
                        if(Main.config.getBoolean("DropItem")){
                            BanInfo.cantUse(player, itemStack);
                        } else {
                            int slot = IRandom.randomNumber(0, 8);
                            player.getInventory().setHeldItemSlot(slot == player.getInventory().getHeldItemSlot() ? 0 : slot);
                        }
                        player.sendMessage(IString.color(Main.message.getString("ItemNoAllMessage")));
                    } else if (mode.equals("r") && (evt.getAction().equals(Action.RIGHT_CLICK_AIR) || evt.getAction().equals(Action.RIGHT_CLICK_BLOCK))) {
                        evt.setCancelled(true);
                        if(Main.config.getBoolean("DropItem")){
                            BanInfo.cantUse(player, itemStack);
                        } else {
                            int slot = IRandom.randomNumber(0, 8);
                            player.getInventory().setHeldItemSlot(slot == player.getInventory().getHeldItemSlot() ? 0 : slot);
                        }
                        player.sendMessage(IString.color(Main.message.getString("ItemNoRightMessage")));
                    } else if (mode.equals("l") && (evt.getAction().equals(Action.LEFT_CLICK_AIR) || evt.getAction().equals(Action.LEFT_CLICK_BLOCK))) {
                        evt.setCancelled(true);
                        if(Main.config.getBoolean("DropItem")){
                            BanInfo.cantUse(player, itemStack);
                        } else {
                            int slot = IRandom.randomNumber(0, 8);
                            player.getInventory().setHeldItemSlot(slot == player.getInventory().getHeldItemSlot() ? 0 : slot);
                        }
                        player.sendMessage(IString.color(Main.message.getString("ItemNoLeftMessage")));
                    } else if (mode.equals("sl") && player.isSneaking() && (evt.getAction().equals(Action.LEFT_CLICK_AIR) || evt.getAction().equals(Action.LEFT_CLICK_BLOCK))) {
                        evt.setCancelled(true);
                        if(Main.config.getBoolean("DropItem")){
                            BanInfo.cantUse(player, itemStack);
                        } else {
                            int slot = IRandom.randomNumber(0, 8);
                            player.getInventory().setHeldItemSlot(slot == player.getInventory().getHeldItemSlot() ? 0 : slot);
                        }
                        player.sendMessage(IString.color(Main.message.getString("ItemNoShiftLeftMessage")));
                    } else if (mode.equals("sr") && player.isSneaking() && (evt.getAction().equals(Action.RIGHT_CLICK_AIR) || evt.getAction().equals(Action.RIGHT_CLICK_BLOCK))) {
                        evt.setCancelled(true);
                        if(Main.config.getBoolean("DropItem")){
                            BanInfo.cantUse(player, itemStack);
                        } else {
                            int slot = IRandom.randomNumber(0, 8);
                            player.getInventory().setHeldItemSlot(slot == player.getInventory().getHeldItemSlot() ? 0 : slot);
                        }
                        player.sendMessage(IString.color(Main.message.getString("ItemNoShiftLeftMessage")));
                    }
                    break;
                }
            }
        }
    }

    @EventHandler
    public void onPlayerInteractEntity(PlayerInteractEntityEvent evt) {
        if (!Main.config.getBoolean("Optimization") || IRandom.percentageChance(10)) {
            Player player = evt.getPlayer();
            World world = player.getWorld();
            ItemStack itemStack = player.getItemInHand();
            if (!IItem.isEmpty(itemStack)) {
                String[] worlds = new String[]{world.getName(), Main.config.getString("OverAll")};
                for (String worldName : worlds) {
                    String serialNumber = BanInfo.isBannedEnchantment(player, worldName, itemStack);
                    if (serialNumber != null) {
                        Enchantment ench = Enchantment.getByName(Main.data.getString("BanInfo." + worldName + ".Ench." + serialNumber + ".EnchName"));
                        boolean clear = Main.data.getBoolean("BanInfo." + worldName + ".Ench." + serialNumber + ".Clear");
                        String mode = Main.data.getString("BanInfo." + worldName + ".Ench." + serialNumber + ".Mode");
                        if (clear) {
                            itemStack.removeEnchantment(ench);
                            player.sendMessage(IString.color(Main.message.getString("EnchClearMessage")));
                        } else if (mode.equals("a")) {
                            evt.setCancelled(true);
                            if(Main.config.getBoolean("DropItem")){
                                BanInfo.cantUse(player, itemStack);
                            } else {
                                int slot = IRandom.randomNumber(0, 8);
                                player.getInventory().setHeldItemSlot(slot == player.getInventory().getHeldItemSlot() ? 0 : slot);
                            }
                            player.sendMessage(IString.color(Main.message.getString("EnchNoAllMessage")));
                        }
                        break;
                    }
                    serialNumber = BanInfo.isBannedItemStack(player, worldName, itemStack);
                    if (serialNumber != null) {
                        boolean clear = Main.data.getBoolean("BanInfo." + worldName + ".Item." + serialNumber + ".Clear");
                        String mode = Main.data.getString("BanInfo." + worldName + ".Item." + serialNumber + ".Mode");
                        if (clear) {
                            evt.setCancelled(true);
                            player.setItemInHand(null);
                            player.sendMessage(IString.color(Main.message.getString("ItemClearMessage")));
                        } else if (mode.equals("a")) {
                            evt.setCancelled(true);
                            if(Main.config.getBoolean("DropItem")){
                                BanInfo.cantUse(player, itemStack);
                            } else {
                                int slot = IRandom.randomNumber(0, 8);
                                player.getInventory().setHeldItemSlot(slot == player.getInventory().getHeldItemSlot() ? 0 : slot);
                            }
                            player.sendMessage(IString.color(Main.message.getString("ItemNoAllMessage")));
                        }
                        break;
                    }
                }
            }
        }
    }

    @EventHandler
    public void onBlockPlaceEvent(BlockPlaceEvent evt) {
        Player player = evt.getPlayer();
        World world = player.getWorld();
        ItemStack itemStack = evt.getItemInHand();
        Block block = evt.getBlock();
        String[] worlds = new String[]{world.getName(), Main.config.getString("OverAll")};
        for (String worldName : worlds) {
            String serialNumber = BanInfo.isBannedBlock(worldName, block);
            if (serialNumber != null) {
                boolean clear = Main.data.getBoolean("BanInfo." + worldName + ".Block." + serialNumber + ".Clear");
                String mode = Main.data.getString("BanInfo." + worldName + ".Block." + serialNumber + ".Mode");
                if (clear) {
                    evt.setCancelled(true);
                    block.setType(Material.AIR);
                    player.sendMessage(IString.color(Main.message.getString("BlockClearMessage")));
                } else if (mode.equals("a")) {
                    evt.setCancelled(true);
                    player.sendMessage(IString.color(Main.message.getString("BlockNoAllMessage")));
                } else if (mode.equals("p")) {
                    evt.setCancelled(true);
                    player.sendMessage(IString.color(Main.message.getString("BlockNoPlaceMessage")));
                }
                break;
            }
        }
        if (!IItem.isEmpty(itemStack)) {
            for (String worldName : worlds) {
                String serialNumber = BanInfo.isBannedEnchantment(player, worldName, itemStack);
                if (serialNumber != null) {
                    Enchantment ench = Enchantment.getByName(Main.data.getString("BanInfo." + worldName + ".Ench." + serialNumber + ".EnchName"));
                    boolean clear = Main.data.getBoolean("BanInfo." + worldName + ".Ench." + serialNumber + ".Clear");
                    String mode = Main.data.getString("BanInfo." + worldName + ".Ench." + serialNumber + ".Mode");
                    if (clear) {
                        evt.setCancelled(true);
                        itemStack.removeEnchantment(ench);
                        player.sendMessage(IString.color(Main.message.getString("EnchClearMessage")));
                    } else if (mode.equals("a")) {
                        evt.setCancelled(true);
                        player.sendMessage(IString.color(Main.message.getString("EnchNoAllMessage")));
                    } else if (mode.equals("r")) {
                        evt.setCancelled(true);
                        player.sendMessage(IString.color(Main.message.getString("EnchNoRightMessage")));
                    }
                    break;
                }
                serialNumber = BanInfo.isBannedItemStack(player, worldName, itemStack);
                if (serialNumber != null) {
                    boolean clear = Main.data.getBoolean("BanInfo." + worldName + ".Item." + serialNumber + ".Clear");
                    String mode = Main.data.getString("BanInfo." + worldName + ".Item." + serialNumber + ".Mode");
                    if (clear) {
                        evt.setCancelled(true);
                        player.setItemInHand(null);
                        player.sendMessage(IString.color(Main.message.getString("ItemClearMessage")));
                    } else if (mode.equals("a")) {
                        evt.setCancelled(true);
                        player.sendMessage(IString.color(Main.message.getString("ItemNoAllMessage")));
                    } else if (mode.equals("p")) {
                        evt.setCancelled(true);
                        player.sendMessage(IString.color(Main.message.getString("ItemNoPlaceMessage")));
                    }
                    break;
                }
            }
        }
    }

    @EventHandler
    public void onBlockBreak(BlockBreakEvent evt) {
        if (!Main.config.getBoolean("Optimization") || IRandom.percentageChance(2.5)) {
            Player player = evt.getPlayer();
            World world = player.getWorld();
            ItemStack itemStack = evt.getPlayer().getItemInHand();
            Block block = evt.getBlock();
            String[] worlds = new String[]{world.getName(), Main.config.getString("OverAll")};
            for (String worldName : worlds) {
                String serialNumber = BanInfo.isBannedBlock(worldName, block);
                if (serialNumber != null) {
                    boolean clear = Main.data.getBoolean("BanInfo." + worldName + ".Block." + serialNumber + ".Clear");
                    String mode = Main.data.getString("BanInfo." + worldName + ".Block." + serialNumber + ".Mode");
                    if (clear) {
                        evt.setCancelled(true);
                        block.setType(Material.AIR);
                        player.sendMessage(IString.color(Main.message.getString("BlockClearMessage")));
                    } else if (mode.equals("a")) {
                        evt.setCancelled(true);
                        player.sendMessage(IString.color(Main.message.getString("BlockNoAllMessage")));
                    } else if (mode.equals("b")) {
                        evt.setCancelled(true);
                        player.sendMessage(IString.color(Main.message.getString("BlockNoBreakMessage")));
                    }
                    break;
                }
            }
            if (!IItem.isEmpty(itemStack)) {
                for (String worldName : worlds) {
                    String serialNumber = BanInfo.isBannedEnchantment(player, worldName, itemStack);
                    if (serialNumber != null) {
                        Enchantment ench = Enchantment.getByName(Main.data.getString("BanInfo." + worldName + ".Ench." + serialNumber + ".EnchName"));
                        boolean clear = Main.data.getBoolean("BanInfo." + worldName + ".Ench." + serialNumber + ".Clear");
                        String mode = Main.data.getString("BanInfo." + worldName + ".Ench." + serialNumber + ".Mode");
                        if (clear) {
                            evt.setCancelled(true);
                            itemStack.removeEnchantment(ench);
                            player.sendMessage(IString.color(Main.message.getString("EnchClearMessage")));
                        } else if (mode.equals("a")) {
                            evt.setCancelled(true);
                            player.sendMessage(IString.color(Main.message.getString("EnchNoAllMessage")));
                        } else if (mode.equals("l")) {
                            evt.setCancelled(true);
                            player.sendMessage(IString.color(Main.message.getString("EnchNoLeftMessage")));
                        }
                        break;
                    }
                    serialNumber = BanInfo.isBannedItemStack(player, worldName, itemStack);
                    if (serialNumber != null) {
                        boolean clear = Main.data.getBoolean("BanInfo." + worldName + ".Item." + serialNumber + ".Clear");
                        String mode = Main.data.getString("BanInfo." + worldName + ".Item." + serialNumber + ".Mode");
                        if (clear) {
                            evt.setCancelled(true);
                            player.setItemInHand(null);
                            player.sendMessage(IString.color(Main.message.getString("ItemClearMessage")));
                        } else if (mode.equals("a")) {
                            evt.setCancelled(true);
                            player.sendMessage(IString.color(Main.message.getString("ItemNoAllMessage")));
                        } else if (mode.equals("l")) {
                            evt.setCancelled(true);
                            player.sendMessage(IString.color(Main.message.getString("ItemNoLeftMessage")));
                        }
                        break;
                    }
                }
            }
        }
    }

    @EventHandler
    public void onEntityDamageByEntity(EntityDamageByEntityEvent evt) {
        if (!Main.config.getBoolean("Optimization") || IRandom.percentageChance(2.5)) {
            if (evt.getEntity() instanceof LivingEntity && !(evt.getEntity() instanceof Player)) {
                LivingEntity livingEntity = (LivingEntity) evt.getEntity();
                World world = livingEntity.getWorld();
                String[] worlds = new String[]{world.getName(), Main.config.getString("OverAll")};
                String saveID = IEntity.getSaveName(livingEntity);
                for (String worldName : worlds) {
                    String serialNumber = BanInfo.isBannedEntity(worldName, livingEntity);
                    if (serialNumber != null) {
                        evt.setCancelled(true);
                        livingEntity.remove();
                        break;
                    }
                }
            }
            if (evt.getDamager() instanceof LivingEntity && !(evt.getDamager() instanceof Player)) {
                LivingEntity livingEntity = (LivingEntity) evt.getDamager();
                World world = livingEntity.getWorld();
                String[] worlds = new String[]{world.getName(), Main.config.getString("OverAll")};
                for (String worldName : worlds) {
                    String serialNumber = BanInfo.isBannedEntity(worldName, livingEntity);
                    if (serialNumber != null) {
                        evt.setCancelled(true);
                        livingEntity.remove();
                        break;
                    }
                }
            }
            if (evt.getDamager() instanceof Projectile) {
                Projectile projectile = (Projectile) evt.getDamager();
                if (projectile.getShooter() instanceof LivingEntity && !(projectile.getShooter() instanceof Player)) {
                    LivingEntity livingEntity = (LivingEntity) projectile.getShooter();
                    World world = livingEntity.getWorld();
                    String[] worlds = new String[]{world.getName(), Main.config.getString("OverAll")};
                    String saveID = IEntity.getSaveName(livingEntity);
                    for (String worldName : worlds) {
                        String serialNumber = BanInfo.isBannedEntity(worldName, livingEntity);
                        if (serialNumber != null) {
                            evt.setCancelled(true);
                            livingEntity.remove();
                            break;
                        }
                    }
                }
            }
            if (evt.getEntity() instanceof Player) {
                Player player = (Player) evt.getEntity();
                World world = player.getWorld();
                for (ItemStack itemStack : player.getInventory().getArmorContents()) {
                    if (!IItem.isEmpty(itemStack)) {
                        String[] worlds = new String[]{world.getName(), Main.config.getString("OverAll")};
                        for (String worldName : worlds) {
                            String serialNumber = BanInfo.isBannedEnchantment(player, worldName, itemStack);
                            if (serialNumber != null) {
                                Enchantment ench = Enchantment.getByName(Main.data.getString("BanInfo." + worldName + ".Ench." + serialNumber + ".EnchName"));
                                boolean clear = Main.data.getBoolean("BanInfo." + worldName + ".Ench." + serialNumber + ".Clear");
                                String mode = Main.data.getString("BanInfo." + worldName + ".Ench." + serialNumber + ".Mode");
                                if (clear) {
                                    itemStack.removeEnchantment(ench);
                                    player.sendMessage(IString.color(Main.message.getString("EnchClearMessage")));
                                } else if (mode.equals("a")) {
                                    evt.setCancelled(true);
                                    IInventoryItem.deductItem(player.getInventory(), itemStack);
                                    IPlayer.giveItem(player, itemStack);
                                    player.sendMessage(IString.color(Main.message.getString("EnchNoAllMessage")));
                                }
                                break;
                            }
                            serialNumber = BanInfo.isBannedItemStack(player, worldName, itemStack);
                            if (serialNumber != null) {
                                boolean clear = Main.data.getBoolean("BanInfo." + worldName + ".Item." + serialNumber + ".Clear");
                                String mode = Main.data.getString("BanInfo." + worldName + ".Item." + serialNumber + ".Mode");
                                if (clear) {
                                    evt.setCancelled(true);
                                    IInventoryItem.deductItem(player.getInventory(), itemStack);
                                    player.sendMessage(IString.color(Main.message.getString("ItemClearMessage")));
                                } else if (mode.equals("a")) {
                                    evt.setCancelled(true);
                                    IInventoryItem.deductItem(player.getInventory(), itemStack);
                                    IPlayer.giveItem(player, itemStack);
                                    player.sendMessage(IString.color(Main.message.getString("ItemNoAllMessage")));
                                }
                                break;
                            }
                        }
                    }
                }
            }
            if (evt.getDamager() instanceof Player) {
                Player player = (Player) evt.getDamager();
                World world = player.getWorld();
                ItemStack itemStack = player.getItemInHand();
                if (!IItem.isEmpty(itemStack)) {
                    String[] worlds = new String[]{world.getName(), Main.config.getString("OverAll")};
                    for (String worldName : worlds) {
                        String serialNumber = BanInfo.isBannedEnchantment(player, worldName, itemStack);
                        if (serialNumber != null) {
                            Enchantment ench = Enchantment.getByName(Main.data.getString("BanInfo." + worldName + ".Ench." + serialNumber + ".EnchName"));
                            boolean clear = Main.data.getBoolean("BanInfo." + worldName + ".Ench." + serialNumber + ".Clear");
                            String mode = Main.data.getString("BanInfo." + worldName + ".Ench." + serialNumber + ".Mode");
                            if (clear) {
                                evt.setCancelled(true);
                                itemStack.removeEnchantment(ench);
                                player.sendMessage(IString.color(Main.message.getString("EnchClearMessage")));
                            } else if (mode.equals("a")) {
                                evt.setCancelled(true);
                                if(Main.config.getBoolean("DropItem")){
                                    BanInfo.cantUse(player, itemStack);
                                } else {
                                    int slot = IRandom.randomNumber(0, 8);
                                    player.getInventory().setHeldItemSlot(slot == player.getInventory().getHeldItemSlot() ? 0 : slot);
                                }
                                player.sendMessage(IString.color(Main.message.getString("EnchNoAllMessage")));
                            } else if (mode.equals("l")) {
                                evt.setCancelled(true);
                                if(Main.config.getBoolean("DropItem")){
                                    BanInfo.cantUse(player, itemStack);
                                } else {
                                    int slot = IRandom.randomNumber(0, 8);
                                    player.getInventory().setHeldItemSlot(slot == player.getInventory().getHeldItemSlot() ? 0 : slot);
                                }
                                player.sendMessage(IString.color(Main.message.getString("EnchNoLeftMessage")));
                            }
                            break;
                        }
                        serialNumber = BanInfo.isBannedItemStack(player, worldName, itemStack);
                        if (serialNumber != null) {
                            boolean clear = Main.data.getBoolean("BanInfo." + worldName + ".Item." + serialNumber + ".Clear");
                            String mode = Main.data.getString("BanInfo." + worldName + ".Item." + serialNumber + ".Mode");
                            if (clear) {
                                evt.setCancelled(true);
                                player.setItemInHand(null);
                                if(Main.config.getBoolean("DropItem")){
                                    BanInfo.cantUse(player, itemStack);
                                } else {
                                    int slot = IRandom.randomNumber(0, 8);
                                    player.getInventory().setHeldItemSlot(slot == player.getInventory().getHeldItemSlot() ? 0 : slot);
                                }
                                player.sendMessage(IString.color(Main.message.getString("ItemClearMessage")));
                            } else if (mode.equals("a")) {
                                evt.setCancelled(true);
                                if(Main.config.getBoolean("DropItem")){
                                    BanInfo.cantUse(player, itemStack);
                                } else {
                                    int slot = IRandom.randomNumber(0, 8);
                                    player.getInventory().setHeldItemSlot(slot == player.getInventory().getHeldItemSlot() ? 0 : slot);
                                }
                                player.sendMessage(IString.color(Main.message.getString("ItemNoAllMessage")));
                            }
                            break;
                        }
                    }
                }
            }
            if (!(evt.getDamager() instanceof Player) && evt.getDamager() instanceof Projectile) {
                Projectile projectile = (Projectile) evt.getDamager();
                if (projectile.getShooter() instanceof Player) {
                    Player player = (Player) projectile.getShooter();
                    World world = player.getWorld();
                    ItemStack itemStack = player.getItemInHand();
                    if (!IItem.isEmpty(itemStack)) {
                        String[] worlds = new String[]{world.getName(), Main.config.getString("OverAll")};
                        for (String worldName : worlds) {
                            String serialNumber = BanInfo.isBannedEnchantment(player, worldName, itemStack);
                            if (serialNumber != null) {
                                Enchantment ench = Enchantment.getByName(Main.data.getString("BanInfo." + worldName + ".Ench." + serialNumber + ".EnchName"));
                                boolean clear = Main.data.getBoolean("BanInfo." + worldName + ".Ench." + serialNumber + ".Clear");
                                String mode = Main.data.getString("BanInfo." + worldName + ".Ench." + serialNumber + ".Mode");
                                if (clear) {
                                    evt.setCancelled(true);
                                    itemStack.removeEnchantment(ench);
                                    player.sendMessage(IString.color(Main.message.getString("EnchClearMessage")));
                                } else if (mode.equals("a")) {
                                    evt.setCancelled(true);
                                    if(Main.config.getBoolean("DropItem")){
                                        BanInfo.cantUse(player, itemStack);
                                    } else {
                                        int slot = IRandom.randomNumber(0, 8);
                                        player.getInventory().setHeldItemSlot(slot == player.getInventory().getHeldItemSlot() ? 0 : slot);
                                    }
                                    player.sendMessage(IString.color(Main.message.getString("EnchNoAllMessage")));
                                } else if (mode.equals("r")) {
                                    evt.setCancelled(true);
                                    if(Main.config.getBoolean("DropItem")){
                                        BanInfo.cantUse(player, itemStack);
                                    } else {
                                        int slot = IRandom.randomNumber(0, 8);
                                        player.getInventory().setHeldItemSlot(slot == player.getInventory().getHeldItemSlot() ? 0 : slot);
                                    }
                                    player.sendMessage(IString.color(Main.message.getString("EnchNoRightMessage")));
                                }
                                break;
                            }
                            serialNumber = BanInfo.isBannedItemStack(player, worldName, itemStack);
                            if (serialNumber != null) {
                                boolean clear = Main.data.getBoolean("BanInfo." + worldName + ".Item." + serialNumber + ".Clear");
                                String mode = Main.data.getString("BanInfo." + worldName + ".Item." + serialNumber + ".Mode");
                                if (clear) {
                                    evt.setCancelled(true);
                                    player.setItemInHand(null);
                                    if(Main.config.getBoolean("DropItem")){
                                        BanInfo.cantUse(player, itemStack);
                                    } else {
                                        int slot = IRandom.randomNumber(0, 8);
                                        player.getInventory().setHeldItemSlot(slot == player.getInventory().getHeldItemSlot() ? 0 : slot);
                                    }
                                    player.sendMessage(IString.color(Main.message.getString("ItemClearMessage")));
                                } else if (mode.equals("a")) {
                                    evt.setCancelled(true);
                                    if(Main.config.getBoolean("DropItem")){
                                        BanInfo.cantUse(player, itemStack);
                                    } else {
                                        int slot = IRandom.randomNumber(0, 8);
                                        player.getInventory().setHeldItemSlot(slot == player.getInventory().getHeldItemSlot() ? 0 : slot);
                                    }
                                    player.sendMessage(IString.color(Main.message.getString("ItemNoAllMessage")));
                                }
                                break;
                            }
                        }
                    }
                }
            }
        }
    }

    @EventHandler
    public void onCraftItem(CraftItemEvent evt) {
        if (evt.getWhoClicked() instanceof Player) {
            Player player = (Player) evt.getWhoClicked();
            World world = player.getWorld();
            ItemStack itemStack = evt.getCurrentItem();
            CraftingInventory craftingInventory = evt.getInventory();
            if (!IItem.isEmpty(itemStack)) {
                String[] worlds = new String[]{world.getName(), Main.config.getString("OverAll")};
                for (String worldName : worlds) {
                    String serialNumber = BanInfo.isBannedItemStack(player, worldName, itemStack);
                    if (serialNumber != null) {
                        boolean clear = Main.data.getBoolean("BanInfo." + worldName + ".Item." + serialNumber + ".Clear");
                        String mode = Main.data.getString("BanInfo." + worldName + ".Item." + serialNumber + ".Mode");
                        if (clear) {
                            evt.setCancelled(true);
                            IInventoryItem.deductItem(craftingInventory, itemStack);
                            player.sendMessage(IString.color(Main.message.getString("ItemNoCraftMessage")));
                            new BukkitRunnable() {

                                @Override
                                public void run() {
                                    if (player.getItemOnCursor().equals(itemStack)) {
                                        player.setItemOnCursor(null);
                                    }
                                }

                            }.runTask(Main.plugin);
                        } else if (mode.equals("c")) {
                            evt.setCancelled(true);
                            player.sendMessage(IString.color(Main.message.getString("ItemNoCraftMessage")));
                            new BukkitRunnable() {

                                @Override
                                public void run() {
                                    if (player.getItemOnCursor().equals(itemStack)) {
                                        player.setItemOnCursor(null);
                                    }
                                }

                            }.runTask(Main.plugin);
                        }
                        break;
                    }
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onCreatureSpawn(CreatureSpawnEvent evt) {
        LivingEntity livingEntity = evt.getEntity();
        World world = livingEntity.getWorld();
        String[] worlds = new String[]{world.getName(), Main.config.getString("OverAll")};
        String saveID = IEntity.getSaveName(livingEntity);
        for (String worldName : worlds) {
            String serialNumber = BanInfo.isBannedEntity(worldName, livingEntity);
            if (serialNumber != null) {
                evt.setCancelled(true);
                break;
            }
        }
    }


    @EventHandler(ignoreCancelled = true)
    public void onPlayerMove(PlayerMoveEvent evt) {
        if (evt.getFrom().getChunk() != evt.getTo().getChunk()) {
            if (!Main.config.getBoolean("Optimization") || IRandom.percentageChance(10)) {
                new BukkitRunnable() {

                    @Override
                    public void run() {
                        Player player = evt.getPlayer();
                        World world = player.getWorld();
                        Chunk chunk = evt.getTo().getChunk();
                        int xRange = 16;
                        int zRange = 16;
                        int yRange = 256;
                        if (Main.config.getBoolean("OptimizationClearBlock")) {
                            xRange = IRandom.randomNumber(0, 8) + IRandom.randomNumber(0, 8);
                            zRange = IRandom.randomNumber(0, 8) + IRandom.randomNumber(0, 8);
                            yRange = IRandom.randomNumber(32, 156) + IRandom.randomNumber(0, 100);
                        }
                        if (Main.data.getConfigurationSection("BanInfo." + Main.config.getString("OverAll") + ".Block") != null || Main.data.getConfigurationSection("BanInfo." + world.getName() + ".Block") != null) {
                            for (int x = 0; x < xRange; x++) {
                                for (int z = 0; z < zRange; z++) {
                                    for (int y = 0; y < yRange; y++) {
                                        Block block = chunk.getBlock(x, y, z);
                                        if (!IBlock.isEmpty(block)) {
                                            String[] worlds = new String[]{world.getName(), Main.config.getString("OverAll")};
                                            for (String worldName : worlds) {
                                                String serialNumber = BanInfo.isBannedBlock(worldName, block);
                                                if (serialNumber != null) {
                                                    boolean clear = Main.data.getBoolean("BanInfo." + worldName + ".Block." + serialNumber + ".Clear");
                                                    if (clear) {
                                                        new BukkitRunnable() {

                                                            @Override
                                                            public void run() {
                                                                block.setType(Material.AIR);
                                                            }

                                                        }.runTask(Main.plugin);
                                                    }
                                                    break;
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                }.runTaskAsynchronously(Main.plugin);
            }
        }
    }
}
