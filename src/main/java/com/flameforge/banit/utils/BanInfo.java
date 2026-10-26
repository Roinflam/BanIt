package com.flameforge.banit.utils;

import com.flameforge.banit.Banit;
import com.flameforge.banit.config.DataManager;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import com.flameforge.banit.config.ConfigManager;

import java.util.List;

/**
 * 禁用信息工具类
 * 提供检查物品、方块、实体和附魔是否被禁止的静态方法
 *
 * <p>重构说明：所有检查方法已改为通过 DataManager（缓存层）调用，
 * 不再直接访问 DataStorage（底层存储），避免 MySQL 模式下的高频查库问题。
 *
 * @author Tany天炎
 */
public class BanInfo {

    /**
     * 检查此物品是否在封禁名单
     *
     * @param player    涉及的玩家
     * @param world     当前世界
     * @param itemStack 物品
     * @return 返回物品封禁的编号，如果不存在则为 null
     */
    public static String isBannedItemStack(Player player, String world, ItemStack itemStack) {
        BanMatch match = matchBannedItemStack(player, world, itemStack);
        return match == null ? null : match.getSerialNumber();
    }

    /**
     * 检查物品是否被禁用，并<b>一并返回是在哪个世界匹配到的</b>。
     *
     * <p>与 {@link #isBannedItemStack(Player, String, ItemStack)} 的匹配逻辑完全相同：
     * 先查玩家所在世界，没命中再查 OverAll 全局世界。区别只是返回值多带了世界名。</p>
     *
     * <p>读 Clear / Mode / Reason 要按 (世界, 序列号) 查，带上世界后调用方不用再跑一遍匹配反推。</p>
     *
     * @param player    玩家，用于权限豁免判断，可为 null
     * @param world     玩家所在世界名
     * @param itemStack 待检查的物品
     * @return 命中时返回 {@link BanMatch}（含序列号与匹配世界）；未命中返回 null
     */
    public static BanMatch matchBannedItemStack(Player player, String world, ItemStack itemStack) {
        DataManager dataManager = Banit.getDataManager();
        ConfigManager configManager = Banit.getConfigManager();

        String serialNumber = dataManager.isBannedItemStack(player, world, itemStack);

        if (serialNumber != null) {
            return new BanMatch(serialNumber, world);
        }

        // 检查全局设置
        String overallWorld = configManager.getOverAll();
        serialNumber = dataManager.isBannedItemStack(player, overallWorld, itemStack);

        if (serialNumber != null) {
            return new BanMatch(serialNumber, overallWorld);
        }

        return null;
    }

    /**
     * 检查此方块是否在封禁名单
     *
     * @param world 当前世界
     * @param block 方块
     * @return 返回方块封禁的编号，如果不存在则为 null
     */
    public static String isBannedBlock(String world, Block block) {
        DataManager dataManager = Banit.getDataManager();
        ConfigManager configManager = Banit.getConfigManager();

        String serialNumber = dataManager.isBannedBlock(world, block);

        if (serialNumber != null) {
            return serialNumber;
        }

        // 检查全局设置
        String overallWorld = configManager.getOverAll();
        return dataManager.isBannedBlock(overallWorld, block);
    }

    /**
     * 检查此实体是否在封禁名单
     *
     * @param world  当前世界
     * @param entity 实体
     * @return 返回实体封禁的编号，如果不存在则为 null
     */
    public static String isBannedEntity(String world, Entity entity) {
        DataManager dataManager = Banit.getDataManager();
        ConfigManager configManager = Banit.getConfigManager();

        String serialNumber = dataManager.isBannedEntity(world, entity);

        if (serialNumber != null) {
            return serialNumber;
        }

        // 检查全局设置
        String overallWorld = configManager.getOverAll();
        return dataManager.isBannedEntity(overallWorld, entity);
    }

    /**
     * 检查物品附魔是否在封禁名单
     *
     * @param player    涉及的玩家
     * @param world     当前世界
     * @param itemStack 物品
     * @return 返回附魔封禁的编号，如果不存在则为 null
     */
    public static String isBannedEnchantment(Player player, String world, ItemStack itemStack) {
        DataManager dataManager = Banit.getDataManager();
        ConfigManager configManager = Banit.getConfigManager();

        String serialNumber = dataManager.isBannedEnchantment(player, world, itemStack);

        if (serialNumber != null) {
            return serialNumber;
        }

        // 检查全局设置
        String overallWorld = configManager.getOverAll();
        return dataManager.isBannedEnchantment(player, overallWorld, itemStack);
    }

    /**
     * 获取所有有禁止项的世界列表
     *
     * @return 世界列表
     */
    public static List<String> getBannedWorlds() {
        return Banit.getDataManager().getBannedWorlds();
    }

    /**
     * 清除指定世界的所有禁止项
     *
     * @param world 世界名称
     */
    public static void clearWorld(String world) {
        Banit.getDataManager().clearWorld(world);
    }

    /**
     * 清除所有世界的所有禁止项
     */
    public static void clearAll() {
        Banit.getDataManager().clearAll();
    }

    /**
     * 判断是否存在相同的禁止配置
     *
     * @param serialNumber 序列号
     * @return 如果存在相同的禁止配置返回 true，否则返回 false
     */
    public static boolean hasFlag(String serialNumber) {
        return Banit.getDataManager().hasFlag(serialNumber);
    }

    /**
     * 获取禁止项的标志
     *
     * @param serialNumber 序列号
     * @return 禁止项的标志字符串
     */
    public static String getFlags(String serialNumber) {
        return Banit.getDataManager().getFlags(serialNumber);
    }

    /**
     * 当玩家无法使用物品时的处理逻辑
     * 将物品丢出并从背包移除，然后随机切换快捷栏
     *
     * @param player    玩家
     * @param itemStack 物品
     */
    public static void cantUse(Player player, ItemStack itemStack) {
        player.getWorld().dropItem(player.getLocation(), itemStack);
        player.getInventory().remove(itemStack);
        int slot = (int) (Math.random() * 9);
        player.getInventory().setHeldItemSlot(slot == player.getInventory().getHeldItemSlot() ? 0 : slot);
    }
}

