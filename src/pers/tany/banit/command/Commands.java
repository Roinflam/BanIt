package pers.tany.banit.command;


import de.tr7zw.nbtapi.NBTItem;
import de.tr7zw.nbtapi.NBTList;
import de.tr7zw.nbtapi.NBTListCompound;
import de.tr7zw.nbtapi.NBTTileEntity;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.ComponentBuilder;
import org.bukkit.Bukkit;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;
import pers.tany.banit.Main;
import pers.tany.banit.gui.AddInterface;
import pers.tany.banit.gui.ListInterface;
import pers.tany.banit.gui.WorldListInterface;
import pers.tany.banit.utils.BanInfo;
import pers.tany.yukinoaapi.interfacepart.configuration.IConfig;
import pers.tany.yukinoaapi.interfacepart.enchantment.IEnchantment;
import pers.tany.yukinoaapi.interfacepart.entity.IEntity;
import pers.tany.yukinoaapi.interfacepart.inventory.IInventory;
import pers.tany.yukinoaapi.interfacepart.item.IItem;
import pers.tany.yukinoaapi.interfacepart.jsonText.IBungee;
import pers.tany.yukinoaapi.interfacepart.nbt.INBT;
import pers.tany.yukinoaapi.interfacepart.other.IRandom;
import pers.tany.yukinoaapi.interfacepart.player.IAsk;
import pers.tany.yukinoaapi.interfacepart.player.ISelectBlock;
import pers.tany.yukinoaapi.interfacepart.player.ISelectEntity;
import pers.tany.yukinoaapi.interfacepart.serializer.ISerializer;
import pers.tany.yukinoaapi.realizationpart.builder.ItemBuilder;
import pers.tany.yukinoaapi.realizationpart.container.OrderlyMap;
import pers.tany.yukinoaapi.realizationpart.player.Ask;
import pers.tany.yukinoaapi.realizationpart.player.SelectBlock;
import pers.tany.yukinoaapi.realizationpart.player.SelectEntity;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static net.minecraft.server.v1_12_R1.SoundEffects.de;
import static org.bukkit.Bukkit.getLogger;

public class Commands implements CommandExecutor {

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (args.length == 1) {
            if (args[0].equalsIgnoreCase("reload")) {
                if (!sender.isOp()) {
                    sender.sendMessage("§c你没有权限执行此命令");
                    return true;
                }
                int all = 0;
                for (String worldName : Main.data.getConfigurationSection("BanInfo").getKeys(false)) {
                    if (Main.data.getConfigurationSection("BanInfo." + worldName + ".Item") != null) {
                        sender.sendMessage("§7「§f禁用系统§7」§a" + worldName.replace(Main.config.getString("OverAll"), "全局") + "世界已禁用§c " + Main.data.getConfigurationSection("BanInfo." + worldName + ".Item").getKeys(false).size() + " §a个物品");
                        all += Main.data.getConfigurationSection("BanInfo." + worldName + ".Item").getKeys(false).size();
                    }
                    if (Main.data.getConfigurationSection("BanInfo." + worldName + ".Block") != null) {
                        sender.sendMessage("§7「§f禁用系统§7」§a" + worldName.replace(Main.config.getString("OverAll"), "全局") + "世界已禁用§c " + Main.data.getConfigurationSection("BanInfo." + worldName + ".Block").getKeys(false).size() + " §a个方块");
                        all += Main.data.getConfigurationSection("BanInfo." + worldName + ".Block").getKeys(false).size();
                    }
                    if (Main.data.getConfigurationSection("BanInfo." + worldName + ".Entity") != null) {
                        sender.sendMessage("§7「§f禁用系统§7」§a" + worldName.replace(Main.config.getString("OverAll"), "全局") + "世界已禁用§c " + Main.data.getConfigurationSection("BanInfo." + worldName + ".Entity").getKeys(false).size() + " §a个生物");
                        all += Main.data.getConfigurationSection("BanInfo." + worldName + ".Entity").getKeys(false).size();
                    }
                    if (Main.data.getConfigurationSection("BanInfo." + worldName + ".Ench") != null) {
                        sender.sendMessage("§7「§f禁用系统§7」§a" + worldName.replace(Main.config.getString("OverAll"), "全局") + "世界已禁用§c " + Main.data.getConfigurationSection("BanInfo." + worldName + ".Ench").getKeys(false).size() + " §a个物品");
                        all += Main.data.getConfigurationSection("BanInfo." + worldName + ".Ench").getKeys(false).size();
                    }
                }
                sender.sendMessage("§7「§f禁用系统§7」§a共有§c " + all + " §a条禁用配置");
                if (all > 0) {
                    sender.sendMessage("");
                }
                Main.config = IConfig.loadConfig(Main.plugin, "", "config");
                Main.data = IConfig.loadConfig(Main.plugin, "", "data");
                Main.message = IConfig.loadConfig(Main.plugin, "", "message");
                sender.sendMessage("§7「§f禁用系统§7」§a重载成功");
                return true;
            }
            if (args[0].equalsIgnoreCase("clear")) {
                if (!sender.isOp()) {
                    sender.sendMessage("§c你没有权限执行此命令");
                    return true;
                }
                if (Main.data.getConfigurationSection("BanInfo").getKeys(false).size() < 1) {
                    sender.sendMessage("§7「§fBanIt§7」§c所有世界没有任何被禁用的记录");
                    return true;
                }
                for (String worldName : Main.data.getConfigurationSection("BanInfo").getKeys(false)) {
                    Main.data.set("BanInfo." + worldName, null);
                }
                IConfig.saveConfig(Main.plugin, Main.data, "", "data");

                for (Player p : Bukkit.getOnlinePlayers()) {
                    if (p.getOpenInventory().getTopInventory().getHolder() instanceof ListInterface) {
                        p.closeInventory();
                    }
                    if (p.getOpenInventory().getTopInventory().getHolder() instanceof WorldListInterface) {
                        p.closeInventory();
                    }
                }

                sender.sendMessage("§7「§fBanIt§7」§a清空所有世界世界禁用记录成功！");
                return true;
            }
        }
        if (args.length == 2) {
            if (args[0].equalsIgnoreCase("clear")) {
                if (!sender.isOp()) {
                    sender.sendMessage("§c你没有权限执行此命令");
                    return true;
                }
                if (Main.data.getConfigurationSection("BanInfo." + args[1]) == null) {
                    sender.sendMessage("§7「§fBanIt§7」§c" + args[1] + "世界没有任何被禁用的记录");
                    return true;
                }
                Main.data.set("BanInfo." + args[1], null);
                IConfig.saveConfig(Main.plugin, Main.data, "", "data");

                sender.sendMessage("§7「§fBanIt§7」§a清空" + args[1] + "世界禁用记录成功！");
                return true;
            }
        }
        if (sender instanceof ConsoleCommandSender) {
            sender.sendMessage("§b/bi clear [世界]  §3清空这个世界的所有禁用记录");
            sender.sendMessage("§b/bi reload  §3重载插件");
            return true;
        }
        Player player = (Player) sender;
        if (args.length == 1) {
            if (args[0].equalsIgnoreCase("nbt")) {
                if (!sender.isOp()) {
                    sender.sendMessage("§c你没有权限执行此命令");
                    return true;
                }
                if (IItem.isEmptyHand(player)) {
                    player.sendMessage("§7「§fBanIt§7」§c不能为空手！");
                    return true;
                }
                ItemStack itemStack = player.getItemInHand();
                NBTItem nbtItem = new NBTItem(itemStack);
                if (nbtItem.getKeys().size() < 1) {
                    player.sendMessage("§7「§fBanIt§7」§c这个物品没有NBT！");
                    return true;
                }
                for (String key : nbtItem.getKeys()) {
                    try {
                        Object value = INBT.getValue(nbtItem, key);
                        String type = "其他";
                        if (value instanceof String) {
                            type = "字符串";
                        } else if (value instanceof Double) {
                            type = "数字";
                        }
                        if (!type.equals("其他")) {
                            String message = "§7「§fNBT信息§7」§3键： §b" + key + " §3值： §b" + value + "  §2值类型： §6" + type + "   复制到输入框";
                            String command = "/bi add key:" + key + " value:" + value;
                            IBungee.sendPartShowCommandMessage(player, message, "复制到输入框", "§f§l『复制到输入框』", "§a复制命令信息到聊天框", command, false);
                        } else {
                            if (!Main.config.getBoolean("CanBeUsedNBT")) {
                                String message = "§7「§fNBT信息§7」§3键： §b" + key + " §3值： §b ...  §2值类型： §6其他   复制到输入框";
                                IBungee.sendPartShowMessage(player, message, "复制到输入框", "§8§l§m『复制到输入框』", "§7§m复制命令信息到聊天框");
                            }
                        }
                    } catch (Exception exception) {
                        if (!Main.config.getBoolean("CanBeUsedNBT")) {
                            String message = "§7「§fNBT信息§7」§3键： §b" + key + " §3值： §b......  §2值类型： §6其他   复制到输入框";
                            IBungee.sendPartShowMessage(player, message, "复制到输入框", "§8§l§m『复制到输入框』", "§7§m复制命令信息到聊天框");
                        }
                    }
                }
                String message = "§7「§fNBT信息§7」§3完整NBT数据："+ "   复制到输入框";
                String NBTData = nbtItem.toString().replaceAll("\"", "");
                if (NBTData.length() <= 256){
                    IBungee.sendPartShowCommandMessage(player, message, "复制到输入框", "§f§l『复制到输入框』", "§a点击复制到聊天框：\n§f" + NBTData, NBTData, false);
                }else{
                    IBungee.sendPartShowCommandMessage(player, message, "复制到输入框", "§f§l『输出到控制台』", "§a点击输出到控制台：\n§f" + NBTData,"/banit nbt outputToConsole", true);
                }

                return true;
            }
            if (args[0].equalsIgnoreCase("ench")) {
                if (!sender.isOp()) {
                    sender.sendMessage("§c你没有权限执行此命令");
                    return true;
                }
                if (IItem.isEmptyHand(player)) {
                    player.sendMessage("§7「§fBanIt§7」§c不能为空手！");
                    return true;
                }
                ItemStack itemStack = player.getItemInHand().clone();
                if (itemStack.getType().equals(new ItemBuilder("ENCHANTED_BOOK").getType())) {
                    try {
                        NBTItem nbtItem = new NBTItem(itemStack);
                        if (!nbtItem.hasKey("ench")) {
                            NBTList<NBTListCompound> storedEnchantmentsList = nbtItem.getCompoundList("StoredEnchantments");
                            nbtItem.addCompound("ench");
                            NBTList<NBTListCompound> enchList = nbtItem.getCompoundList("ench");

                            enchList.addAll(storedEnchantmentsList);
                            nbtItem.removeKey("StoredEnchantments");

                            nbtItem.applyNBT(itemStack);
                        }
                    } catch (Exception e) {
                        player.sendMessage("§7「§fBanIt§7」§c当前服务器核心可能不支持NBTAPI！请不要使用附魔书查看附魔！");
                        return true;
                    }
                }
                if (itemStack.getEnchantments().size() < 1) {
                    player.sendMessage("§7「§fBanIt§7」§c这个物品没有附魔！");
                    return true;
                }
                for (Enchantment enchantment : itemStack.getEnchantments().keySet()) {
                    player.sendMessage("§7「§f附魔信息§7」§d附魔： " + IEnchantment.getName(enchantment) + " §5等级： " + itemStack.getEnchantments().get(enchantment));
                }
                return true;
            }
            if (args[0].equalsIgnoreCase("list")) {
                if (!player.hasPermission("bi.list")) {
                    player.sendMessage("§c你没有权限使用此指令！");
                    return true;
                }
                if (Main.data.getConfigurationSection("BanInfo").getKeys(false).size() < 1) {
                    player.sendMessage("§c没有任何内容！");
                    return true;
                }
                Inventory inventory = new WorldListInterface(player, 1).getInventory();
                player.openInventory(inventory);
                return true;
            }
        }
        if (args.length == 2) {
            //输出手中物品nbt信息到控制台
            if (args[0].equalsIgnoreCase("nbt")) {
                if (args[1].equalsIgnoreCase("outputToConsole")) {
                    if (!sender.isOp()) {
                        sender.sendMessage("§c你没有权限执行此命令");
                        return true;
                    }
                    ItemStack itemStack = player.getItemInHand();
                    NBTItem nbtItem = new NBTItem(itemStack);
                    String NBTData = nbtItem.toString().replaceAll("\"", "");
                    getLogger().info("§3玩家 §b"+player.getName()+" §3手中物品的完整NBT数据:\n§f"+NBTData);
                    player.sendMessage("§a输出成功!");
                    return true;
                }
            }
            if (args[0].equalsIgnoreCase("list")) {
                if (!player.hasPermission("bi.list")) {
                    player.sendMessage("§c你没有权限使用此指令！");
                    return true;
                }
                OrderlyMap<String, String> orderlyHashMap = new OrderlyMap<String, String>();
                if (Main.data.getConfigurationSection("BanInfo." + args[1] + ".Item") != null) {
                    for (String s : Main.data.getConfigurationSection("BanInfo." + args[1] + ".Item").getKeys(false)) {
                        orderlyHashMap.put(s, "Item");
                    }
                }
                if (Main.data.getConfigurationSection("BanInfo." + args[1] + ".Block") != null) {
                    for (String s : Main.data.getConfigurationSection("BanInfo." + args[1] + ".Block").getKeys(false)) {
                        orderlyHashMap.put(s, "Block");
                    }
                }
                if (Main.data.getConfigurationSection("BanInfo." + args[1] + ".Ench") != null) {
                    for (String s : Main.data.getConfigurationSection("BanInfo." + args[1] + ".Ench").getKeys(false)) {
                        orderlyHashMap.put(s, "Ench");
                    }
                }
                if (orderlyHashMap.size() < 1) {
                    player.sendMessage("§c" + args[1] + "世界禁用名单没有任何内容！");
                    return true;
                }
                Inventory inventory = new ListInterface(player, 1, args[1]).getInventory();
                player.openInventory(inventory);
                return true;
            }
            if (args[0].equalsIgnoreCase("set")) {
                if (!player.isOp()) {
                    player.sendMessage("§c你没有权限使用此指令！");
                    return true;
                }
                if (IItem.isEmptyHand(player)) {
                    player.sendMessage("§7「§fBanIt§7」§c不能为空手！");
                    return true;
                }
                OrderlyMap<String, String> orderlyHashMap = new OrderlyMap<String, String>();
                if (Main.data.getConfigurationSection("BanInfo." + args[1] + ".Item") != null) {
                    for (String s : Main.data.getConfigurationSection("BanInfo." + args[1] + ".Item").getKeys(false)) {
                        orderlyHashMap.put(s, "Item");
                    }
                }
                if (Main.data.getConfigurationSection("BanInfo." + args[1] + ".Block") != null) {
                    for (String s : Main.data.getConfigurationSection("BanInfo." + args[1] + ".Block").getKeys(false)) {
                        orderlyHashMap.put(s, "Block");
                    }
                }
                if (Main.data.getConfigurationSection("BanInfo." + args[1] + ".Ench") != null) {
                    for (String s : Main.data.getConfigurationSection("BanInfo." + args[1] + ".Ench").getKeys(false)) {
                        orderlyHashMap.put(s, "Ench");
                    }
                }
                if (orderlyHashMap.size() < 1) {
                    player.sendMessage("§c" + args[1] + "世界禁用名单没有任何内容！");
                    return true;
                }
                ItemStack itemStack = player.getItemInHand();
                Main.data.set("BanInfo." + args[1] + ".DisplayItem", ISerializer.serializerItemStack(itemStack, true));
                IConfig.saveConfig(Main.plugin, Main.data, "", "data");
                player.sendMessage("§7「§fBanIt§7」§a设置材质成功！");
                return true;
            }
        }
        if (args.length >= 1) {
            if (args[0].equalsIgnoreCase("check")) {
                if (!sender.isOp()) {
                    sender.sendMessage("§c你没有权限执行此命令");
                    return true;
                }
                if (IItem.isEmptyHand(player)) {
                    player.sendMessage("§7「§fBanIt§7」§c不能为空手！");
                    return true;
                }
                List<String> flag = Arrays.asList(args);
                ItemStack itemStack = player.getItemInHand();
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
                } else if (flag.contains("u:l") || flag.contains("use:left")) {
                    mode = "l";
                } else if (flag.contains("u:p") || flag.contains("use:place")) {
                    mode = "p";
                } else if (flag.contains("u:c") || flag.contains("use:craft")) {
                    mode = "c";
                } else if (flag.contains("u:d") || flag.contains("use:drop")) {
                    mode = "d";
                }
                for (String r : flag) {
                    if (r.startsWith("reason:")) {
                        reason = r.replace("reason:", "");
                        break;
                    }
                }

                if (!worldName.equals(Main.config.getString("OverAll")) && Bukkit.getWorld(worldName) == null) {
                    player.sendMessage("§7「§fBanIt§7」§c世界不存在！");
                    return true;
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
                if (Main.config.getBoolean("HasFlag") && BanInfo.hasFlag(serialNumber)) {
                    player.sendMessage("§7「§fBanIt§7」§c此物品已被此方法禁用过了！");
                } else {
                    player.sendMessage("§7「§fBanIt§7」§a此物品未被此方法禁用！");
                }
                Main.data.set("BanInfo." + worldName + ".Item." + serialNumber, null);
                return true;
            }
            if (args[0].equalsIgnoreCase("add")) {
                if (!sender.isOp()) {
                    sender.sendMessage("§c你没有权限执行此命令");
                    return true;
                }
                if (IItem.isEmptyHand(player)) {
                    player.sendMessage("§7「§fBanIt§7」§c不能为空手！");
                    return true;
                }
                List<String> flag = Arrays.asList(args);
                ItemStack itemStack = player.getItemInHand();
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
                    player.sendMessage("§7「§fBanIt§7」§c世界不存在！");
                    return true;
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
                    player.sendMessage("§7「§fBanIt§7」§c此物品已被此方法禁用过了！");
                    Main.data.set("BanInfo." + worldName + ".Item." + serialNumber, null);
                } else {
                    IConfig.saveConfig(Main.plugin, Main.data, "", "data");
                    player.sendMessage("§7「§fBanIt§7」§a成功添加到ban表！");
                }
                return true;
            }
            if (args[0].equalsIgnoreCase("adds")) {
                if (!sender.isOp()) {
                    sender.sendMessage("§c你没有权限执行此命令");
                    return true;
                }
                IInventory.openInventory(new AddInterface(player, Arrays.asList(args)), player);
                player.sendMessage("§7「§fBanIt§7」§a往里面放入需要以这个参数禁用的物品，关闭界面即可批量禁用！");
                return true;
            }
            if (args[0].equalsIgnoreCase("addblock")) {
                if (!sender.isOp()) {
                    sender.sendMessage("§7「§fBanIt§7」§c你没有权限执行此命令");
                    return true;
                }
                List<String> flag = Arrays.asList(args);
                new BukkitRunnable() {

                    @Override
                    public void run() {
                        Block block;
                        player.sendMessage("§7「§fBanIt§7」§a请拿着需要展示的物品点击需要禁用的方块！");
                        while (true) {
                            ISelectBlock selectBlock = new SelectBlock(player);
                            selectBlock.setQuitClose(true);
                            SelectBlock.ClickSource clickSource = selectBlock.getClickSource();
                            if (clickSource.isClick()) {
                                block = selectBlock.getSelect();
                                break;
                            } else if (clickSource.equals(SelectBlock.ClickSource.quit)) {
                                return;
                            }
                            player.sendMessage("§7「§fBanIt§7」§a请拿着需要展示的物品点击需要禁用的方块！");
                        }
                        player.sendMessage("§7「§fBanIt§7」§a请输入true确认或者false取消！");
                        while (true) {
                            IAsk ask = new Ask(player);
                            ask.setQuitClose(true);
                            Ask.Reason reason = ask.getReason();
                            if (reason.isAnswer()) {
                                String answer = ask.getAnswer();
                                if (answer.equalsIgnoreCase("true")) {
                                    if (IItem.isEmptyHand(player)) {
                                        player.sendMessage("§7「§fBanIt§7」§c不能为空手！");
                                    } else {
                                        break;
                                    }
                                } else if (answer.equalsIgnoreCase("false")) {
                                    player.sendMessage("§7「§fBanIt§7」§a取消成功！");
                                    return;
                                }
                            } else if (reason.equals(Ask.Reason.quit)) {
                                return;
                            }
                            player.sendMessage("§7「§fBanIt§7」§a请输入true确认或者false取消！");
                        }
                        String serialNumber = IRandom.createRandomString(10);
                        ItemStack itemStack = player.getItemInHand();
                        String id = block.getType().toString();
                        byte data = block.getData();
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
                        if (flag.contains("u:i") || flag.contains("use:interact")) {
                            mode = "i";
                        } else if (flag.contains("u:b") || flag.contains("use:break")) {
                            mode = "b";
                        } else if (flag.contains("u:p") || flag.contains("use:place")) {
                            mode = "p";
                        }
                        if (flag.contains("r:o") || flag.contains("range:occupation")) {
                            Main.data.set("BanInfo." + worldName + ".Block." + serialNumber + ".Distinguish", 0);
                        } else if (flag.contains("r:a") || flag.contains("range:all")) {
                            Main.data.set("BanInfo." + worldName + ".Block." + serialNumber + ".Distinguish", 1);
                        } else {
                            Main.data.set("BanInfo." + worldName + ".Block." + serialNumber + ".Distinguish", 2);
                        }
                        for (String r : flag) {
                            if (r.startsWith("reason:")) {
                                reason = r.replace("reason:", "");
                            }
                        }

                        if (!worldName.equals(Main.config.getString("OverAll")) && Bukkit.getWorld(worldName) == null) {
                            player.sendMessage("§c世界不存在！");
                            return;
                        }

                        Main.data.set("BanInfo." + worldName + ".Block." + serialNumber + ".ItemStack", ISerializer.serializerItemStack(itemStack, true));
                        Main.data.set("BanInfo." + worldName + ".Block." + serialNumber + ".ID", id);
                        Main.data.set("BanInfo." + worldName + ".Block." + serialNumber + ".Data", data);
                        Main.data.set("BanInfo." + worldName + ".Block." + serialNumber + ".Clear", clear);
                        Main.data.set("BanInfo." + worldName + ".Block." + serialNumber + ".Mode", mode);
                        Main.data.set("BanInfo." + worldName + ".Block." + serialNumber + ".Reason", reason);
                        try {
                            NBTTileEntity nbtTileEntity = new NBTTileEntity(block.getState());
                            if (nbtTileEntity.hasKey("id")) {
                                Main.data.set("BanInfo." + worldName + ".Block." + serialNumber + ".TileID", nbtTileEntity.getString("id"));
                                if (nbtTileEntity.hasKey("subTileName")) {
                                    Main.data.set("BanInfo." + worldName + ".Block." + serialNumber + ".SubTileName", nbtTileEntity.getString("subTileName"));
                                }
                            }
                        } catch (Exception exception) {

                        }
                        if (Main.config.getBoolean("HasFlag") && BanInfo.hasFlag(serialNumber)) {
                            player.sendMessage("§7「§fBanIt§7」§c此方块已被此方法禁用过了！");
                            Main.data.set("BanInfo." + worldName + ".Block." + serialNumber, null);
                        } else {
                            IConfig.saveConfig(Main.plugin, Main.data, "", "data");
                            player.sendMessage("§7「§fBanIt§7」§a成功添加到ban表！");
                        }
                    }

                }.runTaskAsynchronously(Main.plugin);

                return true;
            }
            if (args[0].equalsIgnoreCase("addentity")) {
                if (!sender.isOp()) {
                    sender.sendMessage("§7「§fBanIt§7」§c你没有权限执行此命令");
                    return true;
                }
                List<String> flag = Arrays.asList(args);
                new BukkitRunnable() {

                    @Override
                    public void run() {
                        Entity entity;
                        player.sendMessage("§7「§fBanIt§7」§a请拿着需要展示的物品右键需要禁用的生物！");
                        while (true) {
                            ISelectEntity selectEntity = new SelectEntity(player);
                            selectEntity.setQuitClose(true);
                            SelectEntity.InteractSource interactSource = selectEntity.getInteractSource();
                            if (interactSource.isClick()) {
                                entity = selectEntity.getSelect();
                                break;
                            } else if (interactSource.equals(SelectEntity.InteractSource.quit)) {
                                return;
                            }
                            player.sendMessage("§7「§fBanIt§7」§a请拿着需要展示的物品右键需要禁用的生物！");
                        }
                        player.sendMessage("§7「§fBanIt§7」§a请输入true确认或者false取消！");
                        while (true) {
                            IAsk ask = new Ask(player);
                            ask.setQuitClose(true);
                            Ask.Reason reason = ask.getReason();
                            if (reason.isAnswer()) {
                                String answer = ask.getAnswer();
                                if (answer.equalsIgnoreCase("true")) {
                                    if (IItem.isEmptyHand(player)) {
                                        player.sendMessage("§7「§fBanIt§7」§c不能为空手！");
                                    } else {
                                        break;
                                    }
                                } else if (answer.equalsIgnoreCase("false")) {
                                    player.sendMessage("§7「§fBanIt§7」§a取消成功！");
                                    return;
                                }
                            } else if (reason.equals(Ask.Reason.quit)) {
                                return;
                            }
                            player.sendMessage("§7「§fBanIt§7」§a请输入true确认或者false取消！");
                        }
                        String serialNumber = IRandom.createRandomString(10);
                        ItemStack itemStack = player.getItemInHand();
                        String worldName = Main.config.getString("OverAll");
                        String reason = Main.config.getString("DefaultReason");
                        String saveID = IEntity.getSaveName(entity);
                        if (saveID.contains(":")) {
                            Main.data.set("BanInfo." + worldName + ".Entity." + serialNumber + ".Species", saveID.split(":")[0]);
                            Main.data.set("BanInfo." + worldName + ".Entity." + serialNumber + ".Name", saveID.split(":")[1]);
                        } else {
                            Main.data.set("BanInfo." + worldName + ".Entity." + serialNumber + ".Name", saveID);
                        }

                        for (String f : flag) {
                            if (f.startsWith("w:") || f.startsWith("world:")) {
                                worldName = f.replace("world:", "").replace("w:", "");
                                break;
                            }
                        }
                        if (flag.contains("r:s") || flag.contains("range:sort")) {
                            Main.data.set("BanInfo." + worldName + ".Entity." + serialNumber + ".Distinguish", 1);
                        } else if (flag.contains("r:n") || flag.contains("range:name")) {
                            Main.data.set("BanInfo." + worldName + ".Entity." + serialNumber + ".Distinguish", 2);
                        } else {
                            Main.data.set("BanInfo." + worldName + ".Entity." + serialNumber + ".Distinguish", 0);
                        }
                        for (String r : flag) {
                            if (r.startsWith("reason:")) {
                                reason = r.replace("reason:", "");
                            }
                        }

                        if (!worldName.equals(Main.config.getString("OverAll")) && Bukkit.getWorld(worldName) == null) {
                            player.sendMessage("§c世界不存在！");
                            return;
                        }
                        Main.data.set("BanInfo." + worldName + ".Entity." + serialNumber + ".ItemStack", ISerializer.serializerItemStack(itemStack, true));
                        Main.data.set("BanInfo." + worldName + ".Entity." + serialNumber + ".Reason", reason);
                        if (Main.config.getBoolean("HasFlag") && BanInfo.hasFlag(serialNumber)) {
                            player.sendMessage("§7「§fBanIt§7」§c此实体已被此方法禁用过了！");
                            Main.data.set("BanInfo." + worldName + ".Entity." + serialNumber, null);
                        } else {
                            IConfig.saveConfig(Main.plugin, Main.data, "", "data");
                            player.sendMessage("§7「§fBanIt§7」§a成功添加到ban表！");
                        }
                    }

                }.runTaskAsynchronously(Main.plugin);

                return true;
            }
        }
        if (args.length >= 2) {
            if (args[0].equalsIgnoreCase("addench")) {
                if (!sender.isOp()) {
                    sender.sendMessage("§7「§fBanIt§7」§c你没有权限执行此命令");
                    return true;
                }
                List<String> enchantmentNames = new ArrayList<>();
                for (Enchantment enchantment : Enchantment.values()) {
                    enchantmentNames.add(IEnchantment.getName(enchantment));
                }
                if (enchantmentNames.contains(args[1])) {
                    player.sendMessage("§7「§fBanIt§7」§c没有这个附魔！");
                    return true;
                }
                List<String> flag = Arrays.asList(args);
                String serialNumber = IRandom.createRandomString(10);
                String worldName = Main.config.getString("OverAll");
                boolean clear = true;
                String mode = "a";
                String level = ">0";
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
                } else if (flag.contains("u:l") || flag.contains("use:left")) {
                    mode = "l";
                } else if (flag.contains("u:d") || flag.contains("use:drop")) {
                    mode = "d";
                }
                for (String r : flag) {
                    if (r.startsWith("reason:")) {
                        reason = r.replace("reason:", "");
                        break;
                    }
                    if (r.startsWith("l:") || r.startsWith("level:")) {
                        level = r.replace("level:", "").replace("l:", "");
                        break;
                    }
                }

                if (!worldName.equals(Main.config.getString("OverAll")) && Bukkit.getWorld(worldName) == null) {
                    player.sendMessage("§7「§fBanIt§7」§c世界不存在！");
                    return true;
                }

                Main.data.set("BanInfo." + worldName + ".Ench." + serialNumber + ".EnchName", args[1].toUpperCase());
                Main.data.set("BanInfo." + worldName + ".Ench." + serialNumber + ".Clear", clear);
                Main.data.set("BanInfo." + worldName + ".Ench." + serialNumber + ".Mode", mode);
                Main.data.set("BanInfo." + worldName + ".Ench." + serialNumber + ".Level", level);
                Main.data.set("BanInfo." + worldName + ".Ench." + serialNumber + ".Reason", reason);

                if (Main.config.getBoolean("HasFlag") && BanInfo.hasFlag(serialNumber)) {
                    player.sendMessage("§7「§fBanIt§7」§c此附魔已被此方法禁用过了！");
                    Main.data.set("BanInfo." + worldName + ".Ench." + serialNumber, null);
                } else {
                    IConfig.saveConfig(Main.plugin, Main.data, "", "data");
                    player.sendMessage("§7「§fBanIt§7」§a成功添加到ban表！");
                }
                return true;
            }
        }
        if (player.isOp()) {
            player.sendMessage("§f/bi nbt  §7显示这个物品的NBT");
            player.sendMessage("§f/bi ench  §7显示这个物品的附魔");
            player.sendMessage("§f/bi clear [世界]  §7清空这个世界的所有禁用记录");
            player.sendMessage("§f/bi set [世界]  §7设置这个世界在list里展示的物品材质");
            player.sendMessage("§f/bi add [参数] [参数]...  §7禁用此物品");
            player.sendMessage("§f/bi adds [参数] [参数]...  §7打开快捷禁用物品界面");
            player.sendMessage("§f/bi addblock [参数] [参数]...  §7禁用这个方块");
            player.sendMessage("§f/bi addentity [参数] [参数]...  §7禁用这个生物");
            player.sendMessage("§f/bi addench 附魔名称 [参数] [参数]...  §7禁用这个附魔");
            player.sendMessage("§f/bi check [参数] [参数]...  §7查看此物品是否已被封禁");
            player.sendMessage("§f/bi list [世界]  §7查看被禁用的名单");
            player.sendMessage("§f/bi reload  §7重载插件");
        } else {
            player.sendMessage("§f/bi list [世界]  §8查看被禁用的名单");
        }
        return true;
    }
}
