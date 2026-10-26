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
 * 性能优化：
 * 1. 移除末尾重复调度（runTaskTimer 已是重复任务，run() 末尾又 new CheckTask() 会导致任务无限叠加）
 * 2. 修复遍历范围 inventory.getSize() * 2 → inventory.getSize()（原来会访问越界索引，白白浪费循环）
 * 3. 移除冗余的世界数组循环（BanInfo.isBannedItemStack 内部已经同时检查当前世界和 OverAll 世界）
 * 4. 改用 DataManager（缓存层）读取 clear/reason，避免穿透到底层存储
 *
 * 性能优化（异步化改造，2024 补充）：
 * 5. 本任务由 runTaskTimer 调度，run() 本体仍运行在主线程；但主线程内现在只做“克隆物品快照”
 *    这一步开销很低的操作（逐槽位 clone，不做任何禁用匹配运算）。
 * 6. 真正耗时的禁用匹配运算（isBannedItemStack 内部的 NBT 比较、fuzzyNBT 字符串匹配等纯内存
 *    计算）被转移到异步线程执行，不再阻塞主线程 tick。在线人数越多、背包物品越多（尤其是
 *    Sophisticated Backpacks 这类带海量子物品/NBT 的背包），收益越明显。
 * 7. 异步线程只读取克隆出来的 ItemStack 快照，与主线程实时的 Inventory/ItemStack 完全隔离，
 *    不存在并发读写同一 Bukkit 对象的线程安全问题（Bukkit 的 Inventory/ItemStack 本身不是
 *    线程安全的，绝不能让异步线程直接读取仍在被主线程使用的实时背包对象）。
 * 8. 命中禁用后，实际的物品移除（deductItem）与消息发送仍统一切回主线程执行；执行前会重新
 *    校验玩家是否仍在线，避免异步等待期间玩家已下线导致的异常。
 *
 * 性能优化（2026-09-09）：
 * 9. 改用 BanInfo.matchBannedItemStack，一次匹配同时拿到「序列号 + 匹配到的世界」，
 *    并删除 findMatchedWorld。原实现只能拿到序列号，需要再跑一遍 dataManager.isBannedItemStack
 *    去反推世界，导致一次命中要执行三遍完整匹配（当前世界 + OverAll + 反推那一遍）。
 *    匹配规则与结果完全不变，只是不再重复计算。
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


