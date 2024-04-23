package pers.tany.banit.gui;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import pers.tany.banit.Main;
import pers.tany.banit.utils.BanInfo;
import pers.tany.yukinoaapi.interfacepart.configuration.IConfig;
import pers.tany.yukinoaapi.interfacepart.item.IItem;
import pers.tany.yukinoaapi.interfacepart.other.IRandom;
import pers.tany.yukinoaapi.interfacepart.serializer.ISerializer;

import java.util.List;

public class AddInterface implements InventoryHolder, Listener {
    Inventory inventory;
    Player player;
    List<String> flag;

    public AddInterface(Player player, List<String> flag) {
        this.inventory = Bukkit.createInventory(this, 54, "§7快速添加物品禁用");
        this.player = player;
        this.flag = flag;

        Bukkit.getPluginManager().registerEvents(this, Main.plugin);
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    @EventHandler
    private void onInventoryClose(InventoryCloseEvent evt) {
        if (evt.getInventory().getHolder() instanceof AddInterface && evt.getPlayer() instanceof Player) {
            if (evt.getPlayer().equals(player)) {
                boolean has = false;
                for (ItemStack itemStack : inventory.getContents()) {
                    if (!IItem.isEmpty(itemStack)) {
                        String serialNumber = IRandom.createRandomString(10);
                        String id = itemStack.getType().toString();
                        short durability = itemStack.getDurability();
                        String worldName = Main.config.getString("OverAll");
                        boolean clear = true;
                        String mode = "a";
                        String reason = Main.config.getString("DefaultReason");

                        for (String f : flag) {
                            if (f.startsWith("w:") || f.startsWith("world:")) {
                                worldName = f.replace("world:", "").replace("w:", "");
                                break;
                            }
                        }
                        if (flag.contains("c:f") || flag.contains("clear:false")) {
                            clear = false;
                        }
                        if (flag.contains("u:r") || flag.contains("use:right")) {
                            mode = "r";
                        } else if (flag.contains("u:sr") || flag.contains("use:shiftright")) {
                            mode = "sr";
                        } else if (flag.contains("u:l") || flag.contains("use:left")) {
                            mode = "l";
                        } else if (flag.contains("u:sl") || flag.contains("use:shiftleft")) {
                            mode = "sl";
                        } else if (flag.contains("u:p") || flag.contains("use:place")) {
                            mode = "p";
                        } else if (flag.contains("u:c") || flag.contains("use:craft")) {
                            mode = "c";
                        } else if (flag.contains("u:d") || flag.contains("use:drop")) {
                            mode = "d";
                        } else if (flag.contains("u:m") || flag.contains("use:main")) {
                            mode = "m";
                        } else if (flag.contains("u:o") || flag.contains("use:off")) {
                            mode = "o";
                        }
                        for (String r : flag) {
                            if (r.startsWith("reason:")) {
                                reason = r.replace("reason:", "");
                                break;
                            }
                        }

                        if (!worldName.equals(Main.config.getString("OverAll")) && Bukkit.getWorld(worldName) == null) {
                            player.sendMessage("§7「§fBanIt§7」§c" + worldName + "世界不存在！");
                            continue;
                        }

                        Main.data.set("BanInfo." + worldName + ".Item." + serialNumber + ".ItemStack", ISerializer.serializerItemStack(itemStack, true));
                        Main.data.set("BanInfo." + worldName + ".Item." + serialNumber + ".ID", id);
                        Main.data.set("BanInfo." + worldName + ".Item." + serialNumber + ".Durability", durability);
                        if (flag.contains("r:a") || flag.contains("range:all")) {
                            Main.data.set("BanInfo." + worldName + ".Item." + serialNumber + ".Distinguish", 1);
                        } else if (flag.contains("r:e") || flag.contains("range:exactly")) {
                            Main.data.set("BanInfo." + worldName + ".Item." + serialNumber + ".Distinguish", 2);
                        } else {
                            Main.data.set("BanInfo." + worldName + ".Item." + serialNumber + ".Distinguish", 0);
                        }
                        Main.data.set("BanInfo." + worldName + ".Item." + serialNumber + ".Clear", clear);
                        Main.data.set("BanInfo." + worldName + ".Item." + serialNumber + ".Mode", mode);
                        Main.data.set("BanInfo." + worldName + ".Item." + serialNumber + ".Reason", reason);

                        for (String f : flag) {
                            if (f.startsWith("k:") || f.startsWith("key:")) {
                                Main.data.set("BanInfo." + worldName + ".Item." + serialNumber + ".Key", f.replace("key:", "").replace("k:", ""));
                                break;
                            }
                        }
                        for (String f : flag) {
                            if (f.startsWith("v:") || f.startsWith("value:")) {
                                Main.data.set("BanInfo." + worldName + ".Item." + serialNumber + ".Value", f.replace("value:", "").replace("v:", ""));
                                break;
                            }
                        }
                        for (String f : flag) {
                            if (f.startsWith("fn:") || f.startsWith("fuzzynbt:")) {
                                Main.data.set("BanInfo." + worldName + ".Item." + serialNumber + ".FuzzyNBT", f.replaceFirst("fuzzynbt:", "").replaceFirst("fn:", ""));
                                break;
                            }
                        }
                        if (Main.config.getBoolean("HasFlag") && BanInfo.hasFlag(serialNumber)) {
                            player.sendMessage("§7「§fBanIt§7」§c" + IItem.getName(itemStack) + "物品已被此方法禁用过了！");
                            Main.data.set("BanInfo." + worldName + ".Item." + serialNumber, null);
                        } else {
                            has = true;
                        }
                    }
                }
                IConfig.saveConfig(Main.plugin, Main.data, "", "data");
                player.sendMessage(has ? "§7「§fBanIt§7」§a成功添加到ban表！" : "§7「§fBanIt§7」§c没有任何物品成功添加到ban表！");
                HandlerList.unregisterAll(this);
            }
        }
    }
}
