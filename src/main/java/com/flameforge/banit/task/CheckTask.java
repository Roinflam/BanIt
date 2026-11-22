package com.flameforge.banit.task;

import com.flameforge.banit.Banit;
import com.flameforge.banit.config.DataManager;
import com.flameforge.banit.utils.BanInfo;
import com.flameforge.banit.utils.BanMatch;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;
import com.flameforge.banit.config.ConfigManager;
import com.flameforge.banit.config.LanguageManager;
import pers.tany.yukinoaapi.interfacepart.inventory.IInventoryItem;
import pers.tany.yukinoaapi.interfacepart.item.IItem;

/**
 * 背包检查任务
 * 定期检查玩家背包中的物品是否被禁用
 *
 * 线程分工：
 * - 主线程只逐槽位 clone 出物品快照，不做匹配；
 * - 禁用匹配（NBT 比较、模糊 NBT 字符串匹配）在异步线程跑，只读快照。Bukkit 的 Inventory / ItemStack
 *   不是线程安全的，异步线程不能碰实时背包。背包物品越多（如 Sophisticated Backpacks 这类带大量子物品的背包）越划算；
 * - 命中后移除物品（deductItem）和发消息切回主线程，执行前再确认玩家还在线。
 *
 * BanInfo.matchBannedItemStack 一次就拿到序列号和匹配到的世界（当前世界或 OverAll），不用再反推一遍。
 * run() 末尾不要再 new 一个 CheckTask：runTaskTimer 本身就是重复任务，再调度会无限叠加。
 *
 * @author Tany天炎
 */
public class CheckTask extends BukkitRunnable {

    /** 配置管理器 */
    private final ConfigManager configManager;

    /** 语言管理器 */
    private final LanguageManager languageManager;

    /** 数据管理器（缓存层） */
    private final DataManager dataManager;

    /**
     * 构造函数
     */
    public CheckTask() {
        this.configManager = Banit.getConfigManager();
        this.languageManager = Banit.getLanguageManager();
        this.dataManager = Banit.getDataManager();
    }

    @Override
    public void run() {
        // 本方法运行在主线程（由 runTaskTimer 调度）。
        // 主线程内只克隆背包物品快照，不做任何禁用匹配运算，单人开销约等于「遍历槽位 + clone」，
        // 与原来「遍历槽位 + 完整禁用匹配」相比大幅降低了主线程占用。
        for (Player player : Bukkit.getOnlinePlayers()) {
            // 跳过有豁免权限的玩家
            if (player.hasPermission("bi.op")) {
                continue;
            }

            Inventory inventory = player.getInventory();
            World world = player.getWorld();
            String worldName = world.getName();

            // 克隆背包快照：与主线程实时背包完全隔离，后续异步线程只读取该快照，
            // 不会与主线程产生并发访问同一 ItemStack/Inventory 对象的线程安全问题
            int size = inventory.getSize();
            ItemStack[] snapshot = new ItemStack[size];
            boolean hasAnyItem = false;
            for (int i = 0; i < size; i++) {
                ItemStack item = inventory.getItem(i);
                if (!IItem.isEmpty(item)) {
                    snapshot[i] = item.clone();
                    hasAnyItem = true;
                }
            }

            // 背包为空则无需再调度异步任务
            if (!hasAnyItem) {
                continue;
            }

            // 真正的禁用匹配运算放到异步线程执行，避免阻塞主线程
            new BukkitRunnable() {
                @Override
                public void run() {
                    for (ItemStack itemStack : snapshot) {
                        if (IItem.isEmpty(itemStack)) {
                            continue;
                        }

                        // 优化：BanInfo.matchBannedItemStack 内部已经同时检查当前世界和 OverAll 世界，
                        // 且直接返回「序列号 + 匹配到的世界」，无需再跑一遍匹配去反推世界。
                        // 原实现用 findMatchedWorld 反推，一次命中要执行三遍完整匹配。
                        BanMatch match = BanInfo.matchBannedItemStack(player, worldName, itemStack);

                        if (match != null) {
                            // 优化：使用 DataManager（缓存层）读取，不穿透到底层存储
                            String serialNumber = match.getSerialNumber();
                            String matchedWorld = match.getWorld();
                            boolean clear = dataManager.isBannedItemClear(matchedWorld, serialNumber);

                            if (clear) {
                                final String finalMatchedWorld = matchedWorld;
                                final ItemStack finalItemStack = itemStack;
                                // 物品移除属于状态变更操作，必须切回主线程执行
                                new BukkitRunnable() {
                                    @Override
                                    public void run() {
                                        // 异步等待期间玩家可能已下线，执行前重新校验，避免异常
                                        if (!player.isOnline()) {
                                            return;
                                        }
                                        IInventoryItem.deductItem(inventory, finalItemStack);
                                        String reason = dataManager.getBannedItemReason(finalMatchedWorld, serialNumber);
                                        player.sendMessage(languageManager.getMessageBracket("message.item-clear", "reason", reason));
                                    }
                                }.runTask(Banit.plugin);
                            }
                        }
                    }
                }
            }.runTaskAsynchronously(Banit.plugin);
        }
    }

}


