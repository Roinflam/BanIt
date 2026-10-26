package com.flameforge.banit.listenevent;

import com.flameforge.banit.Banit;
import com.flameforge.banit.config.DataManager;
import com.flameforge.banit.utils.ArmorBanHelper;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockDispenseArmorEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.scheduler.BukkitRunnable;
import pers.tany.yukinoaapi.interfacepart.item.IItem;

/**
 * 装备槽禁用事件监听器
 *
 * <p>用于即时拦截“穿戴被禁用装备”的行为，实现“一穿上就自动掉落/删除”的效果。
 * 监听以下事件：
 * <ul>
 *   <li>{@link InventoryClickEvent} —— 在背包界面把护甲/副手物品点击、Shift 移动、数字键交换到装备槽</li>
 *   <li>{@link InventoryDragEvent}  —— 拖动物品到装备槽</li>
 *   <li>{@link PlayerInteractEvent} —— 右键手持护甲直接穿戴</li>
 *   <li>{@link BlockDispenseArmorEvent} —— 发射器为玩家装备护甲</li>
 * </ul>
 *
 * <p>处理策略：除发射器事件可直接取消外，其余事件统一延后 1 tick 重新读取玩家真实装备槽状态后再处置。
 * 这样无论物品通过何种方式（含模组自定义穿戴逻辑）进入装备槽，都能在穿上后的下一刻被正确检测并移除，
 * 健壮且不依赖具体穿戴路径。
 *
 * <p>性能说明：所有处理器入口都先通过 {@link DataManager#hasEquipBan()} 做零成本判断，
 * 服务器没有任何装备类禁用时不会产生任何额外开销。
 *
 * @author Roinflam
 */
public class ArmorListener implements Listener {

    /** 数据管理器（缓存层），用于零成本快速判断是否存在装备类禁用 */
    private final DataManager dataManager;

    /**
     * 构造函数
     */
    public ArmorListener() {
        this.dataManager = Banit.getDataManager();
    }

    /**
     * 背包点击事件：把物品放入护甲槽/副手槽时触发检查
     *
     * @param evt 背包点击事件
     */
    @EventHandler(ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent evt) {
        if (!dataManager.hasEquipBan()) {
            return;
        }
        if (!(evt.getWhoClicked() instanceof Player)) {
            return;
        }
        Player player = (Player) evt.getWhoClicked();
        if (player.hasPermission("bi.op")) {
            return;
        }
        scheduleEnforce(player);
    }

    /**
     * 背包拖动事件：拖动物品到护甲槽/副手槽时触发检查
     *
     * @param evt 背包拖动事件
     */
    @EventHandler(ignoreCancelled = true)
    public void onInventoryDrag(InventoryDragEvent evt) {
        if (!dataManager.hasEquipBan()) {
            return;
        }
        if (!(evt.getWhoClicked() instanceof Player)) {
            return;
        }
        Player player = (Player) evt.getWhoClicked();
        if (player.hasPermission("bi.op")) {
            return;
        }
        scheduleEnforce(player);
    }

    /**
     * 玩家交互事件：右键手持护甲直接穿戴时触发检查
     *
     * @param evt 玩家交互事件
     */
    @EventHandler(ignoreCancelled = true)
    public void onPlayerInteract(PlayerInteractEvent evt) {
        if (!dataManager.hasEquipBan()) {
            return;
        }
        // 仅右键动作才可能触发穿戴护甲
        Action action = evt.getAction();
        if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        Player player = evt.getPlayer();
        if (player.hasPermission("bi.op")) {
            return;
        }
        // 手中无物品则不可能穿戴
        if (IItem.isEmpty(evt.getItem())) {
            return;
        }
        scheduleEnforce(player);
    }

    /**
     * 发射器装备护甲事件：发射器为玩家装备被禁用护甲时直接取消
     *
     * @param evt 发射器装备护甲事件
     */
    @EventHandler(ignoreCancelled = true)
    public void onBlockDispenseArmor(BlockDispenseArmorEvent evt) {
        if (!dataManager.hasEquipBan()) {
            return;
        }
        if (!(evt.getTargetEntity() instanceof Player)) {
            return;
        }
        Player player = (Player) evt.getTargetEntity();
        if (ArmorBanHelper.shouldBlockArmorDispense(player, evt.getItem())) {
            evt.setCancelled(true);
        }
    }

    /**
     * 延后 1 tick 执行装备槽检查
     * 这样可以等待穿戴动作完成后再读取真实的装备槽状态
     *
     * @param player 目标玩家
     */
    private void scheduleEnforce(Player player) {
        new BukkitRunnable() {
            @Override
            public void run() {
                ArmorBanHelper.enforceArmorBan(player);
            }
        }.runTask(Banit.plugin);
    }
}
