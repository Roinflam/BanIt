package com.flameforge.banit.listenevent;

import com.flameforge.banit.Banit;
import com.flameforge.banit.utils.ArmorBanHelper;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockDispenseArmorEvent;

/**
 * 发射器给玩家穿被禁用的护甲时直接取消。
 * BlockDispenseArmorEvent 是 1.13 才有的事件，单独放一个类，低版本不注册，免得连累 ArmorListener。
 *
 * @author Roinflam
 */
public class ArmorDispenseListener implements Listener {

    public static boolean isSupported() {
        try {
            Class.forName("org.bukkit.event.block.BlockDispenseArmorEvent");
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockDispenseArmor(BlockDispenseArmorEvent evt) {
        if (!Banit.getDataManager().hasEquipBan()) {
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
}
