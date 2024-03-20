package pers.tany.banit.utils;

import de.tr7zw.nbtapi.NBTItem;
import de.tr7zw.nbtapi.NBTTileEntity;
import org.bukkit.Bukkit;
import org.bukkit.block.Block;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import pers.tany.banit.Main;
import pers.tany.yukinoaapi.interfacepart.enchantment.IEnchantment;
import pers.tany.yukinoaapi.interfacepart.entity.IEntity;
import pers.tany.yukinoaapi.interfacepart.inventory.IInventoryItem;
import pers.tany.yukinoaapi.interfacepart.nbt.INBT;
import pers.tany.yukinoaapi.interfacepart.other.IRandom;
import pers.tany.yukinoaapi.interfacepart.serializer.ISerializer;
import pers.tany.yukinoaapi.interfacepart.stream.IParallel;

import java.util.concurrent.atomic.AtomicReference;

public interface BanInfo {
    /**
     * 检测此物品是否在封禁名单
     *
     * @param player    涉及的玩家
     * @param world     当前世界
     * @param itemStack 物品
     * @return 返回物品封禁的编号，如果不存在则为null
     */
    static String isBannedItemStack(Player player, String world, ItemStack itemStack) {
        AtomicReference<String> str = new AtomicReference<>(null);
        if (Main.data.getConfigurationSection("BanInfo." + world + ".Item") != null) {
            IParallel.parallel(Main.data.getConfigurationSection("BanInfo." + world + ".Item").getKeys(false)).forEach(serialNumber -> {
                if (player.hasPermission("bi.ignore." + serialNumber) || player.hasPermission("bi.op")) {
                    return;
                }
                String id = Main.data.getString("BanInfo." + world + ".Item." + serialNumber + ".ID");
                short durability = (short) Main.data.getInt("BanInfo." + world + ".Item." + serialNumber + ".Durability");
                int distinguish = Main.data.getInt("BanInfo." + world + ".Item." + serialNumber + ".Distinguish");
                String key = Main.data.getString("BanInfo." + world + ".Item." + serialNumber + ".Key");
                String value = Main.data.getString("BanInfo." + world + ".Item." + serialNumber + ".Value");
                if (distinguish == 0) {
                    if (!(itemStack.getType().toString().equals(id) && itemStack.getDurability() == durability)) {
                        return;
                    }
                } else if (distinguish == 1) {
                    if (!itemStack.getType().toString().equals(id)) {
                        return;
                    }
                } else {
                    if (!itemStack.isSimilar(ISerializer.deserializeItemStack(Main.data.getString("BanInfo." + world + ".Item." + serialNumber + ".ItemStack")))) {
                        return;
                    }
                }
                if (key != null && value != null) {
                    NBTItem nbtItem = new NBTItem(itemStack);
                    if (nbtItem.hasKey(key)) {
                        Object v = INBT.getValue(nbtItem, key);
                        if (v instanceof String) {
                            String s = (String) v;
                            if (!s.equals(value)) {
                                return;
                            }
                        } else if (v instanceof Double) {
                            double d = (Double) v;
                            if (value.startsWith(">")) {
                                if (d <= Double.parseDouble(value.replace(">", ""))) {
                                    return;
                                }
                            } else if (value.startsWith("<")) {
                                if (d >= Double.parseDouble(value.replace("<", ""))) {
                                    return;
                                }
                            } else {
                                if (d != Double.parseDouble(value)) {
                                    return;
                                }
                            }
                        }
                    } else {
                        return;
                    }
                }
                str.set(serialNumber);
            });
        }
        return str.get();
    }

    /**
     * 检测此方块是否在封禁名单
     *
     * @param world 当前世界
     * @param block 方块
     * @return 返回方块封禁的编号，如果不存在则为null
     */
    static String isBannedBlock(String world, Block block) {
        AtomicReference<String> str = new AtomicReference<>(null);
        if (Main.data.getConfigurationSection("BanInfo." + world + ".Block") != null) {
            for (String serialNumber : Main.data.getConfigurationSection("BanInfo." + world + ".Block").getKeys(false)) {
                String id = Main.data.getString("BanInfo." + world + ".Block." + serialNumber + ".ID");
                byte data = (byte) Main.data.getInt("BanInfo." + world + ".Block." + serialNumber + ".Data");
                int distinguish = Main.data.getInt("BanInfo." + world + ".Block." + serialNumber + ".Distinguish");
                String tileID = Main.data.getString("BanInfo." + world + ".Block." + serialNumber + ".TileID");
                String subTileName = Main.data.getString("BanInfo." + world + ".Block." + serialNumber + ".SubTileName");
                if (distinguish == 0) {
                    if (!(block.getType().toString().equals(id) && block.getData() == data)) {
                        continue;
                    }
                } else if (distinguish == 1) {
                    if (!block.getType().toString().equals(id)) {
                        continue;
                    }
                } else {
                    if (!(block.getType().toString().equals(id) && block.getData() == data)) {
                        continue;
                    }
                    if (tileID != null) {
                        try {
                            NBTTileEntity nbtTileEntity = new NBTTileEntity(block.getState());
                            if (nbtTileEntity.hasKey("id")) {
                                if (!tileID.equals(nbtTileEntity.getString("id"))) {
                                    continue;
                                }
                                if (subTileName != null) {
                                    if (nbtTileEntity.hasKey("subTileName")) {
                                        if (!subTileName.equals(nbtTileEntity.getString("subTileName"))) {
                                            continue;
                                        }
                                    } else {
                                        continue;
                                    }
                                }
                            } else {
                                continue;
                            }
                        } catch (Exception exception) {
                            continue;
                        }
                    } else {
                        try {
                            new NBTTileEntity(block.getState()).toString();
                            continue;
                        } catch (Exception ignored) {

                        }
                    }
                }
                str.set(serialNumber);
            }
        }
        return str.get();
    }

    /**
     * 检测物品附魔是否在封禁名单
     * 一次最多返回一个附魔的封禁信息
     *
     * @param player    涉及的玩家
     * @param world     当前世界
     * @param itemStack 物品
     * @return 返回附魔封禁的编号，如果不存在则为null
     */
    static String isBannedEnchantment(Player player, String world, ItemStack itemStack) {
        AtomicReference<String> str = new AtomicReference<>(null);
        if (Main.data.getConfigurationSection("BanInfo." + world + ".Ench") != null) {
            IParallel.parallel(Main.data.getConfigurationSection("BanInfo." + world + ".Ench").getKeys(false)).forEach(serialNumber -> {
                if (player.hasPermission("bi.ignore." + serialNumber) || player.hasPermission("bi.op")) {
                    return;
                }
                Enchantment ench = IEnchantment.getEnchantment(Main.data.getString("BanInfo." + world + ".Ench." + serialNumber + ".EnchName"));
                boolean clear = Main.data.getBoolean("BanInfo." + world + ".Ench." + serialNumber + ".Clear");
                String mode = Main.data.getString("BanInfo." + world + ".Ench." + serialNumber + ".Mode");
                String level = Main.data.getString("BanInfo." + world + ".Ench." + serialNumber + ".Level");
                if (itemStack.getEnchantments().containsKey(ench)) {
                    if (level.startsWith(">")) {
                        if (itemStack.getEnchantmentLevel(ench) <= Integer.parseInt(level.replace(">", ""))) {
                            return;
                        }
                    } else if (level.startsWith("<")) {
                        if (itemStack.getEnchantmentLevel(ench) >= Integer.parseInt(level.replace("<", ""))) {
                            return;
                        }
                    } else {
                        if (itemStack.getEnchantmentLevel(ench) != Integer.parseInt(level)) {
                            return;
                        }
                    }
                    str.set(serialNumber);
                }
            });
        }
        return str.get();
    }

    /**
     * 检测实体附魔是否在封禁名单
     * 一次最多返回一个附魔的封禁信息
     *
     * @param entity 涉及的实体
     * @param world  当前世界
     * @return 返回实体封禁的编号，如果不存在则为null
     */
    static String isBannedEntity(String world, Entity entity) {
        AtomicReference<String> str = new AtomicReference<>(null);
        if (Main.data.getConfigurationSection("BanInfo." + world + ".Entity") != null) {
            IParallel.parallel(Main.data.getConfigurationSection("BanInfo." + world + ".Entity").getKeys(false)).forEach(serialNumber -> {
                int distinguish = Main.data.getInt("BanInfo." + world + ".Entity." + serialNumber + ".Distinguish");
                String species = "";
                String name = "";
                if (Main.data.getString("BanInfo." + world + ".Entity." + serialNumber + ".Species") != null) {
                    species = Main.data.getString("BanInfo." + world + ".Entity." + serialNumber + ".Species");
                }
                if (Main.data.getString("BanInfo." + world + ".Entity." + serialNumber + ".Name") != null) {
                    name = Main.data.getString("BanInfo." + world + ".Entity." + serialNumber + ".Name");
                }
                String saveID = IEntity.getSaveName(entity);
                if (saveID.contains(":")) {
                    if (distinguish == 0) {
                        if (!species.equals("")) {
                            if (!species.equals(saveID.split(":")[0])) {
                                return;
                            }
                        }
                        if (!name.equals("")) {
                            if (!name.equals(saveID.split(":")[1])) {
                                return;
                            }
                        }
                    } else if (distinguish == 1) {
                        if (!species.equals("")) {
                            if (!species.equals(saveID.split(":")[0])) {
                                return;
                            }
                        }
                    } else {
                        if (!name.equals("")) {
                            if (!name.equals(saveID.split(":")[1])) {
                                return;
                            }
                        }
                    }
                } else {
                    if (!name.equals(saveID)) {
                        return;
                    }
                }
                str.set(serialNumber);
            });
        }
        return str.get();
    }

    static void cantUse(Player player, ItemStack itemStack) {
        player.getWorld().dropItem(player.getLocation(), itemStack);
        IInventoryItem.deductItem(player.getInventory(), itemStack);
        int slot = IRandom.randomNumber(0, 8);
        player.getInventory().setHeldItemSlot(slot == player.getInventory().getHeldItemSlot() ? 0 : slot);
    }

    static String getFlags(String serialNumber) {
        for (String worldName : Main.data.getConfigurationSection("BanInfo").getKeys(false)) {
            for (String type : Main.data.getConfigurationSection("BanInfo." + worldName).getKeys(false)) {
                for (String s : Main.data.getConfigurationSection("BanInfo." + worldName + "." + type).getKeys(false)) {
                    if (s.equals(serialNumber)) {
                        String flag = worldName + "," + type;
                        for (String f : Main.data.getConfigurationSection("BanInfo." + worldName + "." + type + "." + serialNumber).getKeys(false)) {
                            if (!f.equals("ItemStack") && !f.equals("Reason")) {
                                flag += "," + Main.data.getString("BanInfo." + worldName + "." + type + "." + serialNumber + "." + f);
                            }
                        }
                        return flag;
                    }
                }
            }
        }
        return null;
    }

    static boolean hasFlag(String serialNumber) {
        for (String worldName : Main.data.getConfigurationSection("BanInfo").getKeys(false)) {
            for (String type : Main.data.getConfigurationSection("BanInfo." + worldName).getKeys(false)) {
                for (String s : Main.data.getConfigurationSection("BanInfo." + worldName + "." + type).getKeys(false)) {
                    if (!s.equals(serialNumber)) {
                        if (getFlags(s).equals(getFlags(serialNumber))) {
                            return true;
                        }
                    }
                }
            }
        }
        return false;
    }
}
