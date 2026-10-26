package com.flameforge.banit.utils;

import com.flameforge.banit.Banit;
import com.flameforge.banit.config.ConfigManager;
import com.flameforge.banit.config.DataManager;
import com.flameforge.banit.config.LanguageManager;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import pers.tany.yukinoaapi.interfacepart.item.IItem;
import pers.tany.yukinoaapi.realizationpart.server.Version;

/**
 * 装备槽禁用辅助工具类
 *
 * <p>本类用于实现“禁止穿戴装备”功能：当玩家把被禁用的物品穿到护甲槽（头盔/胸甲/护腿/靴子）
 * 或副手时，物品会被立即从对应槽位移除——若 clear=false 则掉落到地面（实现“一穿上就掉下来”的效果），
 * 若 clear=true 则直接删除。
 *
 * <p>支持的装备模式（mode）：
 * <ul>
 *   <li>helmet —— 仅头盔槽</li>
 *   <li>chest  —— 仅胸甲槽</li>
 *   <li>legs   —— 仅护腿槽</li>
 *   <li>boots  —— 仅靴子槽</li>
 *   <li>armor  —— 四个护甲槽（头盔+胸甲+护腿+靴子）</li>
 *   <li>equip  —— 四个护甲槽 + 副手</li>
 *   <li>o      —— 仅副手（复用既有的副手模式，使其也支持即时掉落）</li>
 * </ul>
 *
 * <p>性能说明：所有公开入口都先通过 {@link DataManager#hasEquipBan()} 做零成本快速判断，
 * 当服务器没有任何装备类禁用时，本类的逻辑完全不会执行实际检查。
 * 单次 {@link #enforceArmorBan(Player)} 仅检查 5 个装备槽（多数为空槽，空槽立即跳过），
 * 不会在主线程产生明显开销。
 *
 * @author Roinflam
 */
public final class ArmorBanHelper {

    /**
     * 私有构造，禁止实例化（纯静态工具类）
     */
    private ArmorBanHelper() {
    }

    /**
     * 判断给定模式是否属于“装备类”模式（本系统会处理的全部模式）。
     * 用于 DataManager 的装备禁用标志（equipBanFlag）计算。
     *
     * @param mode 模式代码
     * @return 如果是装备类模式返回 true
     */
    public static boolean isEquipMode(String mode) {
        if (mode == null) {
            return false;
        }
        switch (mode) {
            case "helmet":
            case "chest":
            case "legs":
            case "boots":
            case "armor":
            case "equip":
            case "o":
                return true;
            default:
                return false;
        }
    }

    /**
     * 判断给定模式是否属于“护甲槽类”模式（不含纯副手 o）。
     * 用于：1) 添加禁用时 clear 的默认值判定（护甲类默认 clear=false，实现掉落效果）；
     * 2) 发射器装备护甲（BlockDispenseArmorEvent）的拦截判定（发射器只装备护甲，不涉及副手）。
     *
     * @param mode 模式代码
     * @return 如果是护甲槽类模式返回 true
     */
    public static boolean isArmorMode(String mode) {
        if (mode == null) {
            return false;
        }
        switch (mode) {
            case "helmet":
            case "chest":
            case "legs":
            case "boots":
            case "armor":
            case "equip":
                return true;
            default:
                return false;
        }
    }

    /**
     * 判断给定模式是否作用于指定的装备槽位。
     *
     * @param mode 模式代码
     * @param slot 装备槽位
     * @return 如果该模式作用于该槽位返回 true
     */
    public static boolean modeAppliesToSlot(String mode, EquipmentSlot slot) {
        if (mode == null || slot == null) {
            return false;
        }
        switch (slot) {
            case HEAD:
                return mode.equals("helmet") || mode.equals("armor") || mode.equals("equip");
            case CHEST:
                return mode.equals("chest") || mode.equals("armor") || mode.equals("equip");
            case LEGS:
                return mode.equals("legs") || mode.equals("armor") || mode.equals("equip");
            case FEET:
                return mode.equals("boots") || mode.equals("armor") || mode.equals("equip");
            case OFF_HAND:
                return mode.equals("o") || mode.equals("equip");
            default:
                return false;
        }
    }

    /**
     * 对指定玩家执行一次装备槽禁用检查与处置。
     *
     * <p>会依次检查头盔、胸甲、护腿、靴子、副手 5 个槽位（副手在低版本环境下跳过）。
     * 对每个非空槽位，检查其物品是否在当前世界或全局世界被禁用，且禁用模式作用于该槽位；
     * 命中则清空该槽位，clear=true 删除并提示清除消息，clear=false 掉落到地面并提示对应消息。
     *
     * @param player 目标玩家
     */
    public static void enforceArmorBan(Player player) {
        // 基础校验：玩家为空或不在线直接返回
        if (player == null || !player.isOnline()) {
            return;
        }

        DataManager dataManager = Banit.getDataManager();
        // 零成本快速判断：没有任何装备类禁用时直接返回
        if (dataManager == null || !dataManager.hasEquipBan()) {
            return;
        }

        // 拥有豁免权限的玩家不处理
        if (player.hasPermission("bi.op")) {
            return;
        }

        ConfigManager configManager = Banit.getConfigManager();
        String worldName = player.getWorld().getName();
        String overAll = configManager.getOverAll();
        PlayerInventory inventory = player.getInventory();

        boolean changed = false;

        // 头盔、胸甲、护腿、靴子
        changed |= checkAndHandleSlot(player, inventory, EquipmentSlot.HEAD, worldName, overAll);
        changed |= checkAndHandleSlot(player, inventory, EquipmentSlot.CHEST, worldName, overAll);
        changed |= checkAndHandleSlot(player, inventory, EquipmentSlot.LEGS, worldName, overAll);
        changed |= checkAndHandleSlot(player, inventory, EquipmentSlot.FEET, worldName, overAll);

        // 副手（低版本无副手，跳过）
        if (!Version.getType().isLOW()) {
            changed |= checkAndHandleSlot(player, inventory, EquipmentSlot.OFF_HAND, worldName, overAll);
        }

        // 有改动时刷新玩家背包视图，确保客户端立即同步
        if (changed) {
            player.updateInventory();
        }
    }

    /**
     * 检查并处置单个装备槽位。
     *
     * @param player    玩家
     * @param inventory 玩家背包
     * @param slot      装备槽位
     * @param worldName 当前世界名
     * @param overAll   全局世界名
     * @return 如果对该槽位执行了移除操作返回 true，否则返回 false
     */
    private static boolean checkAndHandleSlot(Player player, PlayerInventory inventory, EquipmentSlot slot,
                                              String worldName, String overAll) {
        ItemStack item = getSlotItem(inventory, slot);
        if (IItem.isEmpty(item)) {
            return false;
        }

        DataManager dataManager = Banit.getDataManager();
        LanguageManager languageManager = Banit.getLanguageManager();

        // 依次检查当前世界与全局世界
        String[] worlds = new String[]{worldName, overAll};
        for (String world : worlds) {
            String serialNumber = dataManager.isBannedItemStack(player, world, item);
            if (serialNumber == null) {
                continue;
            }

            String mode = dataManager.getBannedItemMode(world, serialNumber);
            // 该世界的禁用模式不作用于当前槽位，继续检查下一个世界
            if (!modeAppliesToSlot(mode, slot)) {
                continue;
            }

            boolean clear = dataManager.isBannedItemClear(world, serialNumber);
            String reason = dataManager.getBannedItemReason(world, serialNumber);

            // 先克隆用于掉落，再清空槽位
            ItemStack dropClone = item.clone();
            setSlotItem(inventory, slot, null);

            if (clear) {
                // clear=true：直接删除，提示清除消息
                player.sendMessage(languageManager.getMessageBracket("message.item-clear", "reason", reason));
            } else {
                // clear=false：掉落到地面，实现“穿上即掉落”的效果
                player.getWorld().dropItemNaturally(player.getLocation(), dropClone);
                if (slot == EquipmentSlot.OFF_HAND) {
                    player.sendMessage(languageManager.getMessageBracket("message.item-no-off", "reason", reason));
                } else {
                    player.sendMessage(languageManager.getMessageBracket("message.item-no-wear", "reason", reason));
                }
            }
            return true;
        }

        return false;
    }

    /**
     * 判断发射器装备护甲（BlockDispenseArmorEvent）是否应被拦截。
     *
     * <p>发射器只能为实体装备护甲（头盔/胸甲/护腿/靴子），不涉及副手，
     * 因此仅当物品被以护甲类模式禁用时才拦截。
     *
     * @param player 被装备的玩家
     * @param item   将被装备的物品
     * @return 如果应拦截返回 true
     */
    public static boolean shouldBlockArmorDispense(Player player, ItemStack item) {
        if (player == null || IItem.isEmpty(item)) {
            return false;
        }

        DataManager dataManager = Banit.getDataManager();
        if (dataManager == null || !dataManager.hasEquipBan()) {
            return false;
        }

        if (player.hasPermission("bi.op")) {
            return false;
        }

        ConfigManager configManager = Banit.getConfigManager();
        String[] worlds = new String[]{player.getWorld().getName(), configManager.getOverAll()};
        for (String world : worlds) {
            String serialNumber = dataManager.isBannedItemStack(player, world, item);
            if (serialNumber == null) {
                continue;
            }
            String mode = dataManager.getBannedItemMode(world, serialNumber);
            if (isArmorMode(mode)) {
                return true;
            }
        }

        return false;
    }

    /**
     * 读取指定装备槽位的物品。
     *
     * @param inventory 玩家背包
     * @param slot      装备槽位
     * @return 槽位物品，未知槽位返回 null
     */
    private static ItemStack getSlotItem(PlayerInventory inventory, EquipmentSlot slot) {
        switch (slot) {
            case HEAD:
                return inventory.getHelmet();
            case CHEST:
                return inventory.getChestplate();
            case LEGS:
                return inventory.getLeggings();
            case FEET:
                return inventory.getBoots();
            case OFF_HAND:
                return inventory.getItemInOffHand();
            default:
                return null;
        }
    }

    /**
     * 设置指定装备槽位的物品。
     *
     * @param inventory 玩家背包
     * @param slot      装备槽位
     * @param item      要设置的物品（null 表示清空）
     */
    private static void setSlotItem(PlayerInventory inventory, EquipmentSlot slot, ItemStack item) {
        switch (slot) {
            case HEAD:
                inventory.setHelmet(item);
                break;
            case CHEST:
                inventory.setChestplate(item);
                break;
            case LEGS:
                inventory.setLeggings(item);
                break;
            case FEET:
                inventory.setBoots(item);
                break;
            case OFF_HAND:
                inventory.setItemInOffHand(item);
                break;
            default:
                break;
        }
    }
}
