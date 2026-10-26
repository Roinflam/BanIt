package com.flameforge.banit.command;

import com.flameforge.banit.Banit;
import com.flameforge.banit.config.DataManager;
import com.flameforge.banit.gui.NBTBrowserInterface;
import de.tr7zw.nbtapi.NBTItem;
import de.tr7zw.nbtapi.NBTList;
import de.tr7zw.nbtapi.NBTListCompound;
import org.bukkit.Bukkit;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;
import com.flameforge.banit.config.ConfigManager;
import com.flameforge.banit.config.LanguageManager;
import com.flameforge.banit.gui.AddInterface;
import com.flameforge.banit.gui.ListInterface;
import com.flameforge.banit.gui.WorldListInterface;
import com.flameforge.banit.storage.MySqlDataStorage;
import com.flameforge.banit.utils.ArmorBanHelper;
import com.flameforge.banit.utils.BanInfo;
import com.flameforge.banit.utils.DataMigration;
import pers.tany.yukinoaapi.interfacepart.configuration.IConfig;
import pers.tany.yukinoaapi.interfacepart.enchantment.IEnchantment;
import pers.tany.yukinoaapi.interfacepart.inventory.IInventory;
import pers.tany.yukinoaapi.interfacepart.item.IItem;
import pers.tany.yukinoaapi.interfacepart.other.IRandom;
import pers.tany.yukinoaapi.interfacepart.player.IAsk;
import pers.tany.yukinoaapi.interfacepart.player.ISelectBlock;
import pers.tany.yukinoaapi.interfacepart.player.ISelectEntity;
import pers.tany.yukinoaapi.realizationpart.builder.ItemBuilder;
import pers.tany.yukinoaapi.realizationpart.player.Ask;
import pers.tany.yukinoaapi.realizationpart.player.SelectBlock;
import pers.tany.yukinoaapi.realizationpart.player.SelectEntity;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * 命令处理器
 * 处理所有 BanIt 插件命令
 *
 * @author Tany天炎
 */
public class Commands implements CommandExecutor {

    /** 插件实例 */
    private final Banit plugin;

    /** 配置管理器 */
    private final ConfigManager configManager;

    /** 语言管理器 */
    private final LanguageManager languageManager;

    /** 数据管理器（缓存层）：增删查都走它，写库的同时刷新缓存 */
    private final DataManager dataManager;

    /**
     * 构造函数
     *
     * @param plugin 插件实例
     */
    public Commands(Banit plugin) {
        this.plugin = plugin;
        this.configManager = Banit.getConfigManager();
        this.languageManager = Banit.getLanguageManager();
        this.dataManager = Banit.getDataManager();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        // ==================== 单参数命令 ====================
        if (args.length == 1) {
            if (args[0].equalsIgnoreCase("reload")) {
                return handleReload(sender);
            }
            if (args[0].equalsIgnoreCase("migrateToMySQL")) {
                return handleMigrateToMySQL(sender);
            }
            if (args[0].equalsIgnoreCase("clear")) {
                return handleClearAll(sender);
            }
        }

        // ==================== 双参数命令 ====================
        if (args.length == 2) {
            if (args[0].equalsIgnoreCase("clear")) {
                return handleClearWorld(sender, args[1]);
            }
        }

        // ==================== 控制台命令帮助 ====================
        if (sender instanceof ConsoleCommandSender) {
            List<String> helpMessages = languageManager.getMessageList("command.console-help");
            for (String message : helpMessages) {
                sender.sendMessage(message);
            }
            return true;
        }

        // ==================== 玩家命令 ====================
        Player player = (Player) sender;

        if (args.length == 1) {
            if (args[0].equalsIgnoreCase("nbt")) {
                return handleNbt(player);
            }
            if (args[0].equalsIgnoreCase("ench")) {
                return handleEnch(player);
            }
            if (args[0].equalsIgnoreCase("list")) {
                return handleList(player);
            }
        }

        if (args.length == 2) {
            if (args[0].equalsIgnoreCase("list")) {
                return handleListWorld(player, args[1]);
            }
            if (args[0].equalsIgnoreCase("set")) {
                return handleSet(player, args[1]);
            }
        }

        // ==================== 多参数命令 ====================
        if (args.length >= 1) {
            if (args[0].equalsIgnoreCase("check")) {
                return handleCheck(player, args);
            }
            if (args[0].equalsIgnoreCase("add")) {
                return handleAdd(player, args);
            }
            if (args[0].equalsIgnoreCase("adds")) {
                return handleAdds(player, args);
            }
            if (args[0].equalsIgnoreCase("addblock")) {
                return handleAddBlock(player, args);
            }
            if (args[0].equalsIgnoreCase("addentity")) {
                return handleAddEntity(player, args);
            }
        }

        if (args.length >= 2) {
            if (args[0].equalsIgnoreCase("addench")) {
                return handleAddEnch(player, args);
            }
        }

        // ==================== 帮助信息 ====================
        if (player.isOp()) {
            List<String> helpMessages = languageManager.getMessageList("command.help");
            for (String message : helpMessages) {
                player.sendMessage(message);
            }
        } else {
            player.sendMessage(languageManager.getMessage("command.help-list"));
        }

        return true;
    }

    // ==================== 命令处理方法 ====================

    /**
     * 处理 reload 命令
     */
    private boolean handleReload(CommandSender sender) {
        if (!sender.isOp()) {
            sender.sendMessage(languageManager.getMessage("error.no-permission"));
            return true;
        }

        // 显示统计信息
        int all = 0;
        for (String worldName : dataManager.getBannedWorlds()) {
            int itemCount = dataManager.getBannedItems(worldName).size();
            int blockCount = dataManager.getBannedBlocks(worldName).size();
            int entityCount = dataManager.getBannedEntities(worldName).size();
            int enchCount = dataManager.getBannedEnchantments(worldName).size();

            String displayWorld = worldName.equals(configManager.getOverAll()) ?
                    languageManager.getMessage("gui.global") : worldName;

            if (itemCount > 0) {
                sender.sendMessage(languageManager.getMessage("command.stats-world-item")
                        .replace("{0}", displayWorld)
                        .replace("{1}", String.valueOf(itemCount)));
            }
            if (blockCount > 0) {
                sender.sendMessage(languageManager.getMessage("command.stats-world-block")
                        .replace("{0}", displayWorld)
                        .replace("{1}", String.valueOf(blockCount)));
            }
            if (entityCount > 0) {
                sender.sendMessage(languageManager.getMessage("command.stats-world-entity")
                        .replace("{0}", displayWorld)
                        .replace("{1}", String.valueOf(entityCount)));
            }
            if (enchCount > 0) {
                sender.sendMessage(languageManager.getMessage("command.stats-world-enchantment")
                        .replace("{0}", displayWorld)
                        .replace("{1}", String.valueOf(enchCount)));
            }
            all += itemCount + blockCount + entityCount + enchCount;
        }

        if (all > 0) {
            sender.sendMessage("");
        }

        // 重新加载配置
        configManager.loadConfig();
        languageManager.loadLanguage();
        Banit.data = IConfig.loadConfig(plugin, "", "data");

        // 重新加载数据缓存
        dataManager.reload();

        sender.sendMessage(languageManager.getMessage("command.reload-success"));
        return true;
    }

    /**
     * 处理 migrateToMySQL 命令
     */
    private boolean handleMigrateToMySQL(CommandSender sender) {
        if (!sender.isOp()) {
            sender.sendMessage(languageManager.getMessage("error.no-permission"));
            return true;
        }

        // 迁移要看底层存储是不是 MySQL，所以这里取 DataStorage，不走 DataManager 缓存层
        if (!(Banit.getDataStorage() instanceof MySqlDataStorage)) {
            sender.sendMessage(languageManager.getMessage("error.not-mysql-mode"));
            return true;
        }

        DataMigration.migrateDataToMySQL(sender);
        return true;
    }

    /**
     * 处理清除所有命令
     */
    private boolean handleClearAll(CommandSender sender) {
        if (!sender.isOp()) {
            sender.sendMessage(languageManager.getMessage("error.no-permission"));
            return true;
        }

        if (dataManager.getBannedWorlds().isEmpty()) {
            sender.sendMessage(languageManager.getMessage("error.no-world-bans"));
            return true;
        }

        dataManager.clearAll();

        // 关闭所有打开的列表界面
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (p.getOpenInventory().getTopInventory().getHolder() instanceof ListInterface ||
                    p.getOpenInventory().getTopInventory().getHolder() instanceof WorldListInterface) {
                p.closeInventory();
            }
        }

        sender.sendMessage(languageManager.getMessage("command.clear-all-success"));
        return true;
    }

    /**
     * 处理清除世界命令
     */
    private boolean handleClearWorld(CommandSender sender, String worldName) {
        if (!sender.isOp()) {
            sender.sendMessage(languageManager.getMessage("error.no-permission"));
            return true;
        }

        if (dataManager.getBannedItems(worldName).isEmpty() &&
                dataManager.getBannedBlocks(worldName).isEmpty() &&
                dataManager.getBannedEntities(worldName).isEmpty() &&
                dataManager.getBannedEnchantments(worldName).isEmpty()) {
            sender.sendMessage(languageManager.getMessage("error.no-world-list-content", "world", worldName));
            return true;
        }

        dataManager.clearWorld(worldName);
        sender.sendMessage(languageManager.getMessage("command.clear-world-success", "world", worldName));
        return true;
    }

    /**
     * 处理 nbt 命令
     */
    private boolean handleNbt(Player player) {
        if (!player.isOp()) {
            player.sendMessage(languageManager.getMessage("error.no-permission"));
            return true;
        }

        if (IItem.isEmptyHand(player)) {
            player.sendMessage(languageManager.getMessage("error.empty-hand"));
            return true;
        }

        ItemStack itemStack = player.getItemInHand();
        NBTItem nbtItem = new NBTItem(itemStack);

        if (nbtItem.getKeys().isEmpty()) {
            player.sendMessage(languageManager.getMessage("nbt.no-nbt"));
            return true;
        }

        NBTBrowserInterface browser = new NBTBrowserInterface(player, itemStack);
        player.openInventory(browser.getInventory());

        player.sendMessage(languageManager.getMessage("nbt-browser.opened"));
        player.sendMessage(languageManager.getMessage("nbt-browser.navigate-tip"));
        player.sendMessage(languageManager.getMessage("nbt-browser.select-tip"));

        return true;
    }

    /**
     * 处理 ench 命令
     */
    private boolean handleEnch(Player player) {
        if (!player.isOp()) {
            player.sendMessage(languageManager.getMessage("error.no-permission"));
            return true;
        }

        if (IItem.isEmptyHand(player)) {
            player.sendMessage(languageManager.getMessage("error.empty-hand"));
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
                player.sendMessage(languageManager.getMessage("enchantment.nbtapi-error"));
                return true;
            }
        }

        if (itemStack.getEnchantments().isEmpty()) {
            player.sendMessage(languageManager.getMessage("enchantment.no-enchantment"));
            return true;
        }

        for (Enchantment enchantment : itemStack.getEnchantments().keySet()) {
            player.sendMessage(languageManager.getMessage("enchantment.info")
                    .replace("{0}", IEnchantment.getName(enchantment))
                    .replace("{1}", itemStack.getEnchantments().get(enchantment).toString()));
        }

        return true;
    }

    /**
     * 处理 list 命令
     */
    private boolean handleList(Player player) {
        if (!player.hasPermission("bi.list")) {
            player.sendMessage(languageManager.getMessage("error.no-permission"));
            return true;
        }

        if (dataManager.getBannedWorlds().isEmpty()) {
            player.sendMessage(languageManager.getMessage("error.no-world-content"));
            return true;
        }

        IInventory.openInventory(new WorldListInterface(player, 1), player);
        return true;
    }

    /**
     * 处理 list [world] 命令
     */
    private boolean handleListWorld(Player player, String worldName) {
        if (!player.hasPermission("bi.list")) {
            player.sendMessage(languageManager.getMessage("error.no-permission"));
            return true;
        }

        if (dataManager.getBannedItems(worldName).isEmpty() &&
                dataManager.getBannedBlocks(worldName).isEmpty() &&
                dataManager.getBannedEntities(worldName).isEmpty() &&
                dataManager.getBannedEnchantments(worldName).isEmpty()) {
            player.sendMessage(languageManager.getMessage("error.no-world-list-content", "world", worldName));
            return true;
        }

        IInventory.openInventory(new ListInterface(player, 1, worldName), player);
        return true;
    }

    /**
     * 处理 set 命令
     */
    private boolean handleSet(Player player, String worldName) {
        if (!player.isOp()) {
            player.sendMessage(languageManager.getMessage("error.no-permission"));
            return true;
        }

        if (IItem.isEmptyHand(player)) {
            player.sendMessage(languageManager.getMessage("error.empty-hand"));
            return true;
        }

        if (dataManager.getBannedItems(worldName).isEmpty() &&
                dataManager.getBannedBlocks(worldName).isEmpty() &&
                dataManager.getBannedEntities(worldName).isEmpty() &&
                dataManager.getBannedEnchantments(worldName).isEmpty()) {
            player.sendMessage(languageManager.getMessage("error.no-world-list-content", "world", worldName));
            return true;
        }

        ItemStack itemStack = player.getItemInHand();
        dataManager.setWorldDisplayItem(worldName, itemStack);
        player.sendMessage(languageManager.getMessage("command.set-material-success"));
        return true;
    }

    /**
     * 处理 check 命令
     */
    private boolean handleCheck(Player player, String[] args) {
        if (!player.isOp()) {
            player.sendMessage(languageManager.getMessage("error.no-permission"));
            return true;
        }

        if (IItem.isEmptyHand(player)) {
            player.sendMessage(languageManager.getMessage("error.empty-hand"));
            return true;
        }

        List<String> flag = Arrays.asList(args);
        ItemStack itemStack = player.getItemInHand();
        String worldName = configManager.getOverAll();

        for (String f : flag) {
            if (f.startsWith("world:")) {
                worldName = f.replace("world:", "");
                break;
            }
        }

        if (!worldName.equals(configManager.getOverAll()) && Bukkit.getWorld(worldName) == null) {
            player.sendMessage(languageManager.getMessage("error.world-not-exist"));
            return true;
        }

        if (dataManager.isBannedItemStack(player, worldName, itemStack, true) != null) {
            player.sendMessage(languageManager.getMessage("command.item-already-banned"));
        } else {
            player.sendMessage(languageManager.getMessage("command.item-not-banned"));
        }

        return true;
    }

    /**
     * 处理 add 命令
     */
    private boolean handleAdd(Player player, String[] args) {
        if (!player.isOp()) {
            player.sendMessage(languageManager.getMessage("error.no-permission"));
            return true;
        }

        if (IItem.isEmptyHand(player)) {
            player.sendMessage(languageManager.getMessage("error.empty-hand"));
            return true;
        }

        List<String> flag = Arrays.asList(args);
        ItemStack itemStack = player.getItemInHand();
        String serialNumber = IRandom.createRandomString(10);
        String id = itemStack.getType().toString();
        short durability = itemStack.getDurability();
        String worldName = configManager.getOverAll();
        boolean clear = true;
        String mode = "a";
        String reason = configManager.getDefaultReason();

        for (String f : flag) {
            if (f.startsWith("world:")) {
                worldName = f.replace("world:", "");
                break;
            }
        }

        if (flag.contains("clear:false")) {
            clear = false;
        }

        if (flag.contains("use:right")) {
            mode = "r";
        } else if (flag.contains("use:left")) {
            mode = "l";
        } else if (flag.contains("use:place")) {
            mode = "p";
        } else if (flag.contains("use:craft")) {
            mode = "c";
        } else if (flag.contains("use:drop")) {
            mode = "d";
        } else if (flag.contains("use:main")) {
            mode = "m";
        } else if (flag.contains("use:off")) {
            mode = "o";
        } else if (flag.contains("use:helmet")) {
            // 仅头盔槽禁止穿戴
            mode = "helmet";
        } else if (flag.contains("use:chestplate")) {
            // 仅胸甲槽禁止穿戴
            mode = "chest";
        } else if (flag.contains("use:leggings")) {
            // 仅护腿槽禁止穿戴
            mode = "legs";
        } else if (flag.contains("use:boots")) {
            // 仅靴子槽禁止穿戴
            mode = "boots";
        } else if (flag.contains("use:armor")) {
            // 四个护甲槽（头盔+胸甲+护腿+靴子）禁止穿戴
            mode = "armor";
        } else if (flag.contains("use:equip")) {
            // 四个护甲槽 + 副手禁止穿戴
            mode = "equip";
        }

        // 护甲类模式（helmet/chest/legs/boots/armor/equip）：若玩家未显式指定 clear，
        // 默认 clear=false，使被禁用装备"一穿上就掉落"而非删除
        if (!flag.contains("clear:true") && !flag.contains("clear:false") && ArmorBanHelper.isArmorMode(mode)) {
            clear = false;
        }

        for (String r : flag) {
            if (r.startsWith("reason:")) {
                reason = r.replace("reason:", "");
                break;
            }
        }

        if (!worldName.equals(configManager.getOverAll()) && Bukkit.getWorld(worldName) == null) {
            player.sendMessage(languageManager.getMessage("error.world-not-exist"));
            return true;
        }

        int distinguish = 0;
        if (flag.contains("range:all")) {
            distinguish = 1;
        } else if (flag.contains("range:exactly")) {
            distinguish = 2;
        }

        String key = null;
        String value = null;
        String fuzzyNBT = null;
        for (String f : flag) {
            if (f.startsWith("key:")) {
                key = f.replace("key:", "");
            } else if (f.startsWith("value:")) {
                value = f.replace("value:", "");
            } else if (f.startsWith("fuzzynbt:")) {
                fuzzyNBT = f.replaceFirst("fuzzynbt:", "");
            }
        }

        // 经缓存层写入，缓存和存储一起更新
        dataManager.addBannedItem(worldName, serialNumber, itemStack, id, durability, distinguish, clear, mode, reason, key, value, fuzzyNBT);

        if (configManager.isHasFlag() && BanInfo.hasFlag(serialNumber)) {
            player.sendMessage(languageManager.getMessage("error.item-already-banned"));
            dataManager.removeBannedItem(worldName, serialNumber);
        } else {
            player.sendMessage(languageManager.getMessage("command.add-success"));
        }

        return true;
    }

    /**
     * 处理 adds 命令
     */
    private boolean handleAdds(Player player, String[] args) {
        if (!player.isOp()) {
            player.sendMessage(languageManager.getMessage("error.no-permission"));
            return true;
        }

        IInventory.openInventory(new AddInterface(player, Arrays.asList(args)), player);
        player.sendMessage(languageManager.getMessage("gui.add-interface-hint"));
        return true;
    }

    /**
     * 处理 addblock 命令
     */
    private boolean handleAddBlock(Player player, String[] args) {
        if (!player.isOp()) {
            player.sendMessage(languageManager.getMessage("error.no-permission"));
            return true;
        }

        List<String> flag = Arrays.asList(args);

        new BukkitRunnable() {
            @Override
            public void run() {
                Block block;
                player.sendMessage(languageManager.getMessage("block.select-hint"));

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

                    player.sendMessage(languageManager.getMessage("block.select-hint"));
                }

                player.sendMessage(languageManager.getMessage("block.confirm-hint"));

                while (true) {
                    IAsk ask = new Ask(player);
                    ask.setQuitClose(true);
                    Ask.Reason reason = ask.getReason();

                    if (reason.isAnswer()) {
                        String answer = ask.getAnswer();
                        if (answer.equalsIgnoreCase("true")) {
                            if (IItem.isEmptyHand(player)) {
                                player.sendMessage(languageManager.getMessage("error.empty-hand"));
                            } else {
                                break;
                            }
                        } else if (answer.equalsIgnoreCase("false")) {
                            player.sendMessage(languageManager.getMessage("block.cancel-success"));
                            return;
                        }
                    } else if (reason.equals(Ask.Reason.quit)) {
                        return;
                    }

                    player.sendMessage(languageManager.getMessage("block.confirm-hint"));
                }

                String serialNumber = IRandom.createRandomString(10);
                String worldName = configManager.getOverAll();
                boolean clear = true;
                String mode = "a";
                String reason = configManager.getDefaultReason();

                for (String f : flag) {
                    if (f.startsWith("world:")) {
                        worldName = f.replace("world:", "");
                        break;
                    }
                }

                if (flag.contains("clear:false")) {
                    clear = false;
                }

                if (flag.contains("use:interact")) {
                    mode = "i";
                } else if (flag.contains("use:break")) {
                    mode = "b";
                } else if (flag.contains("use:place")) {
                    mode = "p";
                }

                for (String r : flag) {
                    if (r.startsWith("reason:")) {
                        reason = r.replace("reason:", "");
                    }
                }

                if (!worldName.equals(configManager.getOverAll()) && Bukkit.getWorld(worldName) == null) {
                    player.sendMessage(languageManager.getMessage("error.world-not-exist"));
                    return;
                }

                int distinguish = 0;
                if (flag.contains("range:all")) {
                    distinguish = 1;
                } else if (flag.contains("range:exactly")) {
                    distinguish = 2;
                }

                dataManager.addBannedBlock(worldName, serialNumber, block, distinguish, clear, mode, reason);

                if (configManager.isHasFlag() && BanInfo.hasFlag(serialNumber)) {
                    player.sendMessage(languageManager.getMessage("error.block-already-banned"));
                    dataManager.removeBannedBlock(worldName, serialNumber);
                } else {
                    player.sendMessage(languageManager.getMessage("command.add-success"));
                }
            }
        }.runTaskAsynchronously(plugin);

        return true;
    }

    /**
     * 处理 addentity 命令
     */
    private boolean handleAddEntity(Player player, String[] args) {
        if (!player.isOp()) {
            player.sendMessage(languageManager.getMessage("error.no-permission"));
            return true;
        }

        List<String> flag = Arrays.asList(args);

        new BukkitRunnable() {
            @Override
            public void run() {
                Entity entity;
                player.sendMessage(languageManager.getMessage("entity.select-hint"));

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

                    player.sendMessage(languageManager.getMessage("entity.select-hint"));
                }

                player.sendMessage(languageManager.getMessage("block.confirm-hint"));

                while (true) {
                    IAsk ask = new Ask(player);
                    ask.setQuitClose(true);
                    Ask.Reason reason = ask.getReason();

                    if (reason.isAnswer()) {
                        String answer = ask.getAnswer();
                        if (answer.equalsIgnoreCase("true")) {
                            if (IItem.isEmptyHand(player)) {
                                player.sendMessage(languageManager.getMessage("error.empty-hand"));
                            } else {
                                break;
                            }
                        } else if (answer.equalsIgnoreCase("false")) {
                            player.sendMessage(languageManager.getMessage("block.cancel-success"));
                            return;
                        }
                    } else if (reason.equals(Ask.Reason.quit)) {
                        return;
                    }

                    player.sendMessage(languageManager.getMessage("block.confirm-hint"));
                }

                String serialNumber = IRandom.createRandomString(10);
                String worldName = configManager.getOverAll();
                String reason = configManager.getDefaultReason();

                for (String f : flag) {
                    if (f.startsWith("world:")) {
                        worldName = f.replace("world:", "");
                        break;
                    }
                }

                for (String r : flag) {
                    if (r.startsWith("reason:")) {
                        reason = r.replace("reason:", "");
                    }
                }

                if (!worldName.equals(configManager.getOverAll()) && Bukkit.getWorld(worldName) == null) {
                    player.sendMessage(languageManager.getMessage("error.world-not-exist"));
                    return;
                }

                int distinguish = 0;
                if (flag.contains("range:sort")) {
                    distinguish = 1;
                } else if (flag.contains("range:name")) {
                    distinguish = 2;
                }

                dataManager.addBannedEntity(worldName, serialNumber, entity, distinguish, reason);

                if (configManager.isHasFlag() && BanInfo.hasFlag(serialNumber)) {
                    player.sendMessage(languageManager.getMessage("error.entity-already-banned"));
                    dataManager.removeBannedEntity(worldName, serialNumber);
                } else {
                    player.sendMessage(languageManager.getMessage("command.add-success"));
                }
            }
        }.runTaskAsynchronously(plugin);

        return true;
    }

    /**
     * 处理 addench 命令
     */
    private boolean handleAddEnch(Player player, String[] args) {
        if (!player.isOp()) {
            player.sendMessage(languageManager.getMessage("error.no-permission"));
            return true;
        }

        Enchantment targetEnchantment = null;

        for (Enchantment ench : Enchantment.values()) {
            String displayName = IEnchantment.getName(ench);
            String internalName = ench.getName();

            if (displayName.equalsIgnoreCase(args[1]) || internalName.equalsIgnoreCase(args[1])) {
                targetEnchantment = ench;
                break;
            }
        }

        if (targetEnchantment == null) {
            targetEnchantment = Enchantment.getByName(args[1].toUpperCase());
        }

        if (targetEnchantment == null) {
            player.sendMessage(languageManager.getMessage("error.enchantment-not-exist"));
            return true;
        }

        List<String> flag = Arrays.asList(args);
        String serialNumber = IRandom.createRandomString(10);
        String worldName = configManager.getOverAll();
        boolean clear = true;
        String mode = "a";
        String level = ">0";
        String reason = configManager.getDefaultReason();

        for (String f : flag) {
            if (f.startsWith("world:")) {
                worldName = f.replace("world:", "");
                break;
            }
        }

        if (flag.contains("clear:false")) {
            clear = false;
        }

        if (flag.contains("use:right")) {
            mode = "r";
        } else if (flag.contains("use:left")) {
            mode = "l";
        } else if (flag.contains("use:drop")) {
            mode = "d";
        }

        for (String r : flag) {
            if (r.startsWith("reason:")) {
                reason = r.replace("reason:", "");
                break;
            }
            if (r.startsWith("level:")) {
                level = r.replace("level:", "");
                break;
            }
        }

        if (!worldName.equals(configManager.getOverAll()) && Bukkit.getWorld(worldName) == null) {
            player.sendMessage(languageManager.getMessage("error.world-not-exist"));
            return true;
        }

        String enchName = targetEnchantment.getName();
        dataManager.addBannedEnchantment(worldName, serialNumber, enchName, clear, mode, level, reason);

        if (configManager.isHasFlag() && BanInfo.hasFlag(serialNumber)) {
            player.sendMessage(languageManager.getMessage("error.enchantment-already-banned"));
            dataManager.removeBannedEnchantment(worldName, serialNumber);
        } else {
            player.sendMessage(languageManager.getMessage("command.add-success"));
        }

        return true;
    }
}
