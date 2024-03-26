package pers.tany.banit.task;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.scheduler.BukkitRunnable;
import pers.tany.banit.Main;
import pers.tany.banit.utils.BanInfo;
import pers.tany.yukinoaapi.interfacepart.item.IItem;
import pers.tany.yukinoaapi.interfacepart.other.IString;
import pers.tany.yukinoaapi.realizationpart.server.Version;

public class CheckHandTask extends BukkitRunnable {

    @Override
    public void run() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (!player.hasPermission("bi.op")) {
                PlayerInventory inventory = player.getInventory();
                World world = player.getWorld();
                {
                    ItemStack itemStack = inventory.getItemInHand();
                    if (!IItem.isEmpty(itemStack)) {
                        String[] worlds = new String[]{world.getName(), Main.config.getString("OverAll")};
                        for (String worldName : worlds) {
                            String serialNumber = BanInfo.isBannedItemStack(player, worldName, itemStack);
                            if (serialNumber != null) {
                                boolean clear = Main.data.getBoolean("BanInfo." + worldName + ".Item." + serialNumber + ".Clear");
                                String mode = Main.data.getString("BanInfo." + worldName + ".Item." + serialNumber + ".Mode");
                                new BukkitRunnable() {

                                    @Override
                                    public void run() {
                                        if (clear) {
                                            inventory.setItemInHand(null);
                                            player.sendMessage(IString.color(Main.message.getString("ItemClearMessage")));
                                        } else if (mode.equals("m")) {
                                            player.getWorld().dropItemNaturally(player.getLocation(), itemStack.clone());
                                            inventory.setItemInHand(null);
                                            player.sendMessage(IString.color(Main.message.getString("ItemNoMainMessage")));
                                        }
                                    }

                                }.runTask(Main.plugin);
                                break;
                            }
                        }
                    }
                }
                if (!Version.getType().isLOW()) {
                    ItemStack itemStack = inventory.getItemInOffHand();
                    if (!IItem.isEmpty(itemStack)) {
                        String[] worlds = new String[]{world.getName(), Main.config.getString("OverAll")};
                        for (String worldName : worlds) {
                            String serialNumber = BanInfo.isBannedItemStack(player, worldName, itemStack);
                            if (serialNumber != null) {
                                boolean clear = Main.data.getBoolean("BanInfo." + worldName + ".Item." + serialNumber + ".Clear");
                                String mode = Main.data.getString("BanInfo." + worldName + ".Item." + serialNumber + ".Mode");
                                new BukkitRunnable() {

                                    @Override
                                    public void run() {
                                        if (clear) {
                                            inventory.setItemInOffHand(null);
                                            player.sendMessage(IString.color(Main.message.getString("ItemClearMessage")));
                                        } else if (mode.equals("o")) {
                                            player.getWorld().dropItemNaturally(player.getLocation(), itemStack.clone());
                                            inventory.setItemInOffHand(null);
                                            player.sendMessage(IString.color(Main.message.getString("ItemNoOffMessage")));
                                        }
                                    }

                                }.runTask(Main.plugin);
                                break;
                            }
                        }
                    }
                }
            }
        }
    }

}
