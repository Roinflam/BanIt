package com.flameforge.banit.gui;

import com.flameforge.banit.Banit;
import com.flameforge.banit.config.DataManager;
import com.flameforge.banit.utils.ArmorBanHelper;
import com.flameforge.banit.utils.BanInfo;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import com.flameforge.banit.config.ConfigManager;
import com.flameforge.banit.config.LanguageManager;
import pers.tany.yukinoaapi.interfacepart.item.IItem;
import pers.tany.yukinoaapi.interfacepart.other.IRandom;

import java.util.List;

/**
 * 快速添加物品禁用界面
 * 允许批量添加禁用物品
 *
 * @author Tany天炎
 */
public class AddInterface implements InventoryHolder, Listener {

    /** GUI 库存对象 */
    private final Inventory inventory;

    /** 操作玩家 */
    private final Player player;

    /** 命令参数标志 */
    private final List<String> flag;

    /** 数据管理器（缓存层）：增删查都走它，写库的同时刷新缓存 */
    private final DataManager dataManager;

    /** 配置管理器 */
    private final ConfigManager configManager;

    /** 语言管理器 */
    private final LanguageManager languageManager;

    /** 唯一标识符 */
    private final String serial;

    /**
     * 构造函数
     *
     * @param player 玩家对象
     * @param flag   命令参数列表
     */
    public AddInterface(Player player, List<String> flag) {
        this.languageManager = Banit.getLanguageManager();
        this.configManager = Banit.getConfigManager();
        this.dataManager = Banit.getDataManager();
        this.player = player;
        this.flag = flag;
        this.serial = IRandom.createRandomString(8);

        // 创建 GUI
        String title = languageManager.getMessage("gui.add-interface-title");
        this.inventory = Bukkit.createInventory(this, 54, title);

        // 注册事件监听器
        Bukkit.getPluginManager().registerEvents(this, Banit.plugin);
    }

    /**
     * 获取唯一标识符
     *
     * @return 唯一标识符
     */
    public String getSerial() {
        return serial;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    /**
     * 处理库存关闭事件
     *
     * @param evt 关闭事件
     */
    @EventHandler
    private void onInventoryClose(InventoryCloseEvent evt) {
        if (!(evt.getInventory().getHolder() instanceof AddInterface)) {
            return;
        }
        if (!(evt.getPlayer() instanceof Player)) {
            return;
        }
        if (!evt.getPlayer().equals(player)) {
            return;
        }

        AddInterface gui = (AddInterface) evt.getInventory().getHolder();
        if (!gui.getSerial().equals(this.serial)) {
            return;
        }

        boolean hasAdded = false;

        for (ItemStack itemStack : inventory.getContents()) {
            if (IItem.isEmpty(itemStack)) {
                continue;
            }

            String serialNumber = IRandom.createRandomString(10);
            String id = itemStack.getType().toString();
            short durability = itemStack.getDurability();
            String worldName = configManager.getOverAll();
            boolean clear = true;
            String mode = "a";
            String reason = configManager.getDefaultReason();

            // 解析世界参数
            for (String f : flag) {
                if (f.startsWith("world:")) {
                    worldName = f.replace("world:", "");
                    break;
                }
            }

            // 解析清除参数
            if (flag.contains("clear:false")) {
                clear = false;
            }

            // 解析使用模式参数
            if (flag.contains("use:right")) {
                mode = "r";
            } else if (flag.contains("use:left")) {
                mode = "l";
            } else if (flag.contains("use:shiftright")) {
                // 只在潜行时禁止右键
                mode = "sr";
            } else if (flag.contains("use:shiftleft")) {
                // 只在潜行时禁止左键
                mode = "sl";
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

            // 护甲类模式（helmet/chest/legs/boots/armor/equip）：若未显式指定 clear，
            // 默认 clear=false，使被禁用装备"一穿上就掉落"而非删除
            if (!flag.contains("clear:true") && !flag.contains("clear:false") && ArmorBanHelper.isArmorMode(mode)) {
                clear = false;
            }

            // 解析理由参数
            for (String r : flag) {
                if (r.startsWith("reason:")) {
                    reason = r.replace("reason:", "");
                    break;
                }
            }

            // 检查世界是否存在
            if (!worldName.equals(configManager.getOverAll()) && Bukkit.getWorld(worldName) == null) {
                player.sendMessage(languageManager.getMessage("error.world-not-exist", "world", worldName));
                continue;
            }

            // 解析范围参数
            int distinguish = 0;
            if (flag.contains("range:all")) {
                distinguish = 1;
            } else if (flag.contains("range:exactly")) {
                distinguish = 2;
            }

            // 解析 NBT 参数
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

            // 检查重复
            if (configManager.isHasFlag() && BanInfo.hasFlag(serialNumber)) {
                player.sendMessage(languageManager.getMessage("error.item-already-banned-method", "item", IItem.getName(itemStack)));
                dataManager.removeBannedItem(worldName, serialNumber);
            } else {
                hasAdded = true;
            }
        }

        // 发送结果消息
        if (hasAdded) {
            player.sendMessage(languageManager.getMessage("command.add-success"));
        } else {
            player.sendMessage(languageManager.getMessage("command.add-failure"));
        }

        // 注销监听器
        HandlerList.unregisterAll(this);
    }
}
