package com.flameforge.banit.task;

import com.flameforge.banit.Banit;
import com.flameforge.banit.config.DataManager;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.scheduler.BukkitRunnable;
import com.flameforge.banit.config.ConfigManager;
import com.flameforge.banit.config.LanguageManager;
import com.flameforge.banit.utils.BanInfo;
import com.flameforge.banit.utils.BanMatch;
import pers.tany.yukinoaapi.interfacepart.item.IItem;
import pers.tany.yukinoaapi.realizationpart.server.Version;

import java.util.Map;

/**
 * 手持物品检查任务
 * 定期检查玩家手中的物品是否被禁用
 *
 * @author Tany天炎
 */
public class CheckHandTask extends BukkitRunnable {

    /** 配置管理器 */
    private final ConfigManager configManager;

    /** 语言管理器 */
    private final LanguageManager languageManager;

    /** 数据管理器（缓存层）：增删查都走它，写库的同时刷新缓存 */
    private final DataManager dataManager;

    /**
     * 构造函数
     */
    public CheckHandTask() {
        this.configManager = Banit.getConfigManager();
        this.languageManager = Banit.getLanguageManager();
        this.dataManager = Banit.getDataManager();
    }

    @Override
    public void run() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            // 跳过有豁免权限的玩家
            if (player.hasPermission("bi.op")) {
                continue;
            }

            PlayerInventory inventory = player.getInventory();
            World world = player.getWorld();

            // 检查主手物品
            checkItem(player, inventory.getItemInHand(), world, "m");

            // 如果不是低版本，检查副手物品
            if (!Version.getType().isLOW()) {
                checkItem(player, inventory.getItemInOffHand(), world, "o");
            }
        }
    }

    /**
     * 检查物品是否被禁用
     *
     * @param player    玩家
     * @param itemStack 物品
     * @param world     世界
     * @param handType  手持类型（"m"为主手，"o"为副手）
     */
    private void checkItem(Player player, ItemStack itemStack, World world, String handType) {
        if (IItem.isEmpty(itemStack)) {
            return;
        }

        // matchBannedItemStack 内部已经依次查了当前世界和 OverAll，不要再在外面按世界循环。
        // 下面的世界名要用匹配结果里的：命中的是 OverAll 条目时，拿当前世界去 getAllBannedInfo 会取到 null。
        BanMatch match = BanInfo.matchBannedItemStack(player, world.getName(), itemStack);

        if (match == null) {
            return;
        }

        String serialNumber = match.getSerialNumber();
        String worldName = match.getWorld();

        // 从 dataManager 缓存读取，不穿透到底层存储
        Map<String, Object> bannedItemInfo = dataManager.getAllBannedInfo(worldName).get(serialNumber);

        if (bannedItemInfo == null) {
            return;
        }

        boolean clear = (boolean) bannedItemInfo.get("Clear");
        String mode = (String) bannedItemInfo.get("Mode");

        new BukkitRunnable() {
            @Override
            public void run() {
                // 处理放在下一 tick，这期间玩家可能已经切了快捷栏、把物品挪走，
                // 或者传送 / 换世界（客户端重建玩家时会把选中格临时报成第 1 格）。
                // 不重新核对的话，清掉的可能是另一件正常物品，丢地上的又是检查时那份拷贝，等于多出一件被禁物品。
                // 所以执行前重新取手上的物品，按同一条禁用规则再匹配一次，对不上就跳过，交给下一轮检查。
                if (!player.isOnline()) {
                    return;
                }
                PlayerInventory current = player.getInventory();
                ItemStack held = handType.equals("m") ? current.getItemInHand() : current.getItemInOffHand();
                if (IItem.isEmpty(held)) {
                    return;
                }
                BanMatch recheck = BanInfo.matchBannedItemStack(player, player.getWorld().getName(), held);
                if (recheck == null || !serialNumber.equals(recheck.getSerialNumber()) || !worldName.equals(recheck.getWorld())) {
                    return;
                }

                // 从 dataManager 缓存读取原因，不穿透到底层存储
                String reason = dataManager.getBannedItemReason(worldName, serialNumber);

                if (clear && (mode.equals("a") || mode.equals(handType))) {
                    if (handType.equals("m")) {
                        current.setItemInHand(null);
                    } else {
                        current.setItemInOffHand(null);
                    }
                    player.sendMessage(languageManager.getMessageBracket("message.item-clear", "reason", reason));
                } else if (mode.equals(handType)) {
                    // 掉落物可能没生成出来（别的插件 / 模组只是取消了生成），这时手上的照留着，等下一轮，不然这件东西就没了。
                    // 实体已经被移除的不算：那是被别处接走了（Forge 换成模组自己的掉落物实体、绑定插件收进失物招领、
                    // 掉落物过多时暂存起来稍后放出），东西还在，手上的要照常清掉，不然每轮多出一件
                    Item dropped = player.getWorld().dropItemNaturally(player.getLocation(), held.clone());
                    if (dropped == null || !dropped.isValid() && !dropped.isDead()) {
                        return;
                    }

                    if (handType.equals("m")) {
                        player.getInventory().setItemInHand(null);
                        player.sendMessage(languageManager.getMessageBracket("message.item-no-main", "reason", reason));
                    } else {
                        player.getInventory().setItemInOffHand(null);
                        player.sendMessage(languageManager.getMessageBracket("message.item-no-off", "reason", reason));
                    }
                }
            }
        }.runTask(Banit.plugin);
    }
}

