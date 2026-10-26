package com.flameforge.banit.storage;

import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.Map;

/**
 * 数据存储接口
 * 定义了所有数据存储操作的方法
 *
 * @author Tany天炎
 */
public interface DataStorage {

    /**
     * 初始化数据存储
     */
    void initialize();

    /**
     * 关闭数据存储连接
     */
    void shutdown();

    /**
     * 添加被禁止的物品
     *
     * @param world        世界名称
     * @param serialNumber 序列号
     * @param item         物品
     * @param id           物品ID
     * @param durability   耐久度
     * @param distinguish  区分方式
     * @param clear        是否清除
     * @param mode         模式
     * @param reason       原因
     * @param key          NBT键
     * @param value        NBT值
     * @param fuzzyNBT     模糊NBT
     */
    void addBannedItem(String world, String serialNumber, ItemStack item, String id, short durability, int distinguish, boolean clear, String mode, String reason, String key, String value, String fuzzyNBT);

    /**
     * 移除被禁止的物品
     *
     * @param world        世界名称
     * @param serialNumber 序列号
     */
    void removeBannedItem(String world, String serialNumber);

    /**
     * 检查物品是否被禁止
     *
     * @param player           玩家
     * @param world            世界名称
     * @param itemStack        物品
     * @param ignorePermission 是否忽略权限检查
     * @return 如果物品被禁止，返回序列号；否则返回null
     */
    String isBannedItemStack(Player player, String world, ItemStack itemStack, boolean ignorePermission);

    /**
     * 检查物品是否被禁止
     *
     * @param player    玩家
     * @param world     世界名称
     * @param itemStack 物品
     * @return 如果物品被禁止，返回序列号；否则返回null
     */
    default String isBannedItemStack(Player player, String world, ItemStack itemStack) {
        return isBannedItemStack(player, world, itemStack, false);
    }

    /**
     * 检查被禁止的物品是否需要清除
     *
     * @param world        世界名称
     * @param serialNumber 序列号
     * @return 如果需要清除返回true，否则返回false
     */
    boolean isBannedItemClear(String world, String serialNumber);

    /**
     * 获取被禁止物品的模式
     *
     * @param world        世界名称
     * @param serialNumber 序列号
     * @return 物品的禁止模式
     */
    String getBannedItemMode(String world, String serialNumber);

    /**
     * 获取被禁止物品的原因
     *
     * @param world        世界名称
     * @param serialNumber 序列号
     * @return 物品被禁止的原因
     */
    String getBannedItemReason(String world, String serialNumber);

    /**
     * 添加被禁止的方块
     *
     * @param world        世界名称
     * @param serialNumber 序列号
     * @param block        方块
     * @param distinguish  区分方式
     * @param clear        是否清除
     * @param mode         模式
     * @param reason       原因
     */
    void addBannedBlock(String world, String serialNumber, Block block, int distinguish, boolean clear, String mode, String reason);

    /**
     * 移除被禁止的方块
     *
     * @param world        世界名称
     * @param serialNumber 序列号
     */
    void removeBannedBlock(String world, String serialNumber);

    /**
     * 检查方块是否被禁止
     *
     * @param world 世界名称
     * @param block 方块
     * @return 如果方块被禁止，返回序列号；否则返回null
     */
    String isBannedBlock(String world, Block block);

    /**
     * 添加被禁止的实体
     *
     * @param world        世界名称
     * @param serialNumber 序列号
     * @param entity       实体
     * @param distinguish  区分方式
     * @param reason       原因
     */
    void addBannedEntity(String world, String serialNumber, Entity entity, int distinguish, String reason);

    /**
     * 移除被禁止的实体
     *
     * @param world        世界名称
     * @param serialNumber 序列号
     */
    void removeBannedEntity(String world, String serialNumber);

    /**
     * 检查实体是否被禁止
     *
     * @param world  世界名称
     * @param entity 实体
     * @return 如果实体被禁止，返回序列号；否则返回null
     */
    String isBannedEntity(String world, Entity entity);

    /**
     * 添加被禁止的附魔
     *
     * @param world        世界名称
     * @param serialNumber 序列号
     * @param enchName     附魔名称
     * @param clear        是否清除
     * @param mode         模式
     * @param level        等级
     * @param reason       原因
     */
    void addBannedEnchantment(String world, String serialNumber, String enchName, boolean clear, String mode, String level, String reason);

    /**
     * 移除被禁止的附魔
     *
     * @param world        世界名称
     * @param serialNumber 序列号
     */
    void removeBannedEnchantment(String world, String serialNumber);

    /**
     * 检查附魔是否被禁止
     *
     * @param player    玩家
     * @param world     世界名称
     * @param itemStack 物品
     * @return 如果附魔被禁止，返回序列号；否则返回null
     */
    String isBannedEnchantment(Player player, String world, ItemStack itemStack);

    /**
     * 获取所有有禁止项的世界列表
     *
     * @return 世界列表
     */
    List<String> getBannedWorlds();

    /**
     * 获取指定世界的所有被禁止的物品列表
     *
     * @param world 世界名称
     * @return 被禁止的物品序列号列表
     */
    List<String> getBannedItems(String world);

    /**
     * 获取指定世界的所有被禁止的方块列表
     *
     * @param world 世界名称
     * @return 被禁止的方块序列号列表
     */
    List<String> getBannedBlocks(String world);

    /**
     * 获取指定世界的所有被禁止的实体列表
     *
     * @param world 世界名称
     * @return 被禁止的实体序列号列表
     */
    List<String> getBannedEntities(String world);

    /**
     * 获取指定世界的所有被禁止的附魔列表
     *
     * @param world 世界名称
     * @return 被禁止的附魔序列号列表
     */
    List<String> getBannedEnchantments(String world);

    /**
     * 清除指定世界的所有禁止项
     *
     * @param world 世界名称
     */
    void clearWorld(String world);

    /**
     * 清除所有世界的所有禁止项
     */
    void clearAll();

    /**
     * 获取禁止项的标志
     *
     * @param serialNumber 序列号
     * @return 禁止项的标志字符串
     */
    String getFlags(String serialNumber);

    /**
     * 获取指定世界的所有被禁止项的信息
     *
     * @param world 世界名称
     * @return 包含所有被禁止项信息的映射
     */
    Map<String, Map<String, Object>> getAllBannedInfo(String world);

    /**
     * 设置世界的展示物品
     *
     * @param world 世界名称
     * @param item  展示物品
     */
    void setWorldDisplayItem(String world, ItemStack item);

    /**
     * 获取世界的展示物品
     *
     * @param world 世界名称
     * @return 展示物品
     */
    ItemStack getWorldDisplayItem(String world);

    /**
     * 直接添加被禁止的方块到数据库（不使用 Block 对象）
     *
     * @param world        世界名称
     * @param serialNumber 序列号
     * @param id           方块ID
     * @param data         方块数据值
     * @param distinguish  区分方式
     * @param clear        是否清除
     * @param mode         模式
     * @param reason       原因
     */
    void addBannedBlockDirect(String world, String serialNumber, String id, byte data, int distinguish, boolean clear, String mode, String reason);

    /**
     * 直接添加被禁止的实体到数据库（不使用 Entity 对象）
     *
     * @param world        世界名称
     * @param serialNumber 序列号
     * @param entityType   实体类型
     * @param distinguish  区分方式
     * @param reason       原因
     */
    void addBannedEntityDirect(String world, String serialNumber, String entityType, int distinguish, String reason);
}