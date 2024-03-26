package pers.tany.banit.task;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;
import pers.tany.banit.utils.BanInfo;
import pers.tany.banit.Main;
import pers.tany.yukinoaapi.interfacepart.inventory.IInventoryItem;
import pers.tany.yukinoaapi.interfacepart.item.IItem;
import pers.tany.yukinoaapi.interfacepart.other.IString;

public class CheckTask extends BukkitRunnable {

    @Override
    public void run() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (!player.hasPermission("bi.op")) {
                Inventory inventory = player.getInventory();
                World world = player.getWorld();
                for (int i = 0; i < inventory.getSize() * 2; i++) {
                    ItemStack itemStack = inventory.getItem(i);
                    if (!IItem.isEmpty(itemStack)) {
                        String[] worlds = new String[]{world.getName(), Main.config.getString("OverAll")};
                        for (String worldName : worlds) {
                            String serialNumber = BanInfo.isBannedItemStack(player, worldName, itemStack);
                            if (serialNumber != null) {
                                boolean clear = Main.data.getBoolean("BanInfo." + worldName + ".Item." + serialNumber + ".Clear");
                                if (clear) {
                                    new BukkitRunnable() {

                                        @Override
                                        public void run() {
                                            IInventoryItem.deductItem(inventory, itemStack);
                                            player.sendMessage(IString.color(Main.message.getString("ItemClearMessage")));
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
        if (Main.config.getBoolean("CheckBackPackTask")) {
            new CheckTask().runTaskLaterAsynchronously(Main.plugin, Main.config.getInt("CheckTime") * 20L);
        }
    }

}
