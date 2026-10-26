package com.flameforge.banit.task;

import com.flameforge.banit.Banit;
import com.flameforge.banit.config.DataManager;
import com.flameforge.banit.utils.ArmorBanHelper;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

/**
 * 装备槽定时检查任务
 *
 * <p>作为事件监听（{@link com.flameforge.banit.listenevent.ArmorListener}）的兜底机制：
 * 对于不会触发常规穿戴事件的装备方式（例如其它插件通过 API 直接设置护甲、命令装备等），
 * 由本定时任务周期性扫描在线玩家的装备槽，确保被禁用装备最终会被移除。
 *
 * <p>性能说明：run() 入口先通过 {@link DataManager#hasEquipBan()} 做零成本判断，
 * 没有任何装备类禁用时直接返回；单个玩家仅检查 5 个装备槽，开销极小。
 *
 * @author Roinflam
 */
public class CheckArmorTask extends BukkitRunnable {

    /** 数据管理器（缓存层） */
    private final DataManager dataManager;

    /**
     * 构造函数
     */
    public CheckArmorTask() {
        this.dataManager = Banit.getDataManager();
    }

    @Override
    public void run() {
        // 零成本快速判断：没有任何装备类禁用时直接返回
        if (!dataManager.hasEquipBan()) {
            return;
        }

        for (Player player : Bukkit.getOnlinePlayers()) {
            // 跳过有豁免权限的玩家
            if (player.hasPermission("bi.op")) {
                continue;
            }
            ArmorBanHelper.enforceArmorBan(player);
        }
    }
}
