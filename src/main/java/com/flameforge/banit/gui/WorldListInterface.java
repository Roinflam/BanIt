package com.flameforge.banit.gui;

import com.flameforge.banit.Banit;
import com.flameforge.banit.config.DataManager;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;
import com.flameforge.banit.config.ConfigManager;
import com.flameforge.banit.config.LanguageManager;
import pers.tany.yukinoaapi.interfacepart.builder.IItemBuilder;
import pers.tany.yukinoaapi.interfacepart.inventory.IInventory;
import pers.tany.yukinoaapi.interfacepart.item.IItem;
import pers.tany.yukinoaapi.interfacepart.other.IList;
import pers.tany.yukinoaapi.interfacepart.other.IRandom;
import pers.tany.yukinoaapi.realizationpart.builder.ItemBuilder;
import pers.tany.yukinoaapi.realizationpart.item.GlassPaneUtil;

import java.util.HashMap;
import java.util.List;

/**
 * 世界列表界面
 * 显示所有有禁用配置的世界
 *
 * <p>v3.0.0 改动：
 * <ul>
 *   <li>全局世界（OverAll）永远排在列表最前面，其余世界按字母序排列</li>
 * </ul>
 *
 * @author Tany天炎
 */
public class WorldListInterface implements InventoryHolder, Listener {

    /** GUI 库存对象 */
    private final Inventory inventory;

    /** 操作玩家 */
    private final Player player;

    /** 当前页码 */
    private int nowPage;

    /** 槽位与世界名映射 */
    private final HashMap<Integer, String> SLOT_ITEM = new HashMap<>();

    /** 数据管理器（缓存层） */
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
     * @param page   当前页码
     */
    public WorldListInterface(Player player, int page) {
        this.dataManager = Banit.getDataManager();
        this.configManager = Banit.getConfigManager();
        this.languageManager = Banit.getLanguageManager();
        this.player = player;
        this.nowPage = page;
        this.serial = IRandom.createRandomString(8);

        // 获取并排序世界列表（全局世界在最前）
        List<String> worlds = dataManager.getBannedWorlds();
        sortWorldList(worlds);

        // 创建标题
        String title = languageManager.getMessage("gui.world-list-title")
                .replace("{number}", String.valueOf(worlds.size()));

        this.inventory = Bukkit.createInventory(this, 54, title);

        update(page);
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
     * 更新当前页面
     */
    public void update() {
        update(nowPage);
    }

    /**
     * 对世界列表进行排序
     * 全局世界（OverAll）永远排在最前面，其余世界按字母序排列
     *
     * @param worlds 世界列表（就地排序）
     */
    private void sortWorldList(List<String> worlds) {
        String overAll = configManager.getOverAll();
        worlds.sort((w1, w2) -> {
            // 全局世界永远在最前
            if (w1.equals(overAll)) {
                return -1;
            }
            if (w2.equals(overAll)) {
                return 1;
            }
            // 其余按字母序
            return w1.compareToIgnoreCase(w2);
        });
    }

    /**
     * 更新指定页码的界面
     *
     * @param page 页码
     */
    private void update(int page) {
        inventory.clear();
        SLOT_ITEM.clear();

        new BukkitRunnable() {
            @Override
            public void run() {
                // 获取并排序世界列表（全局世界在最前）
                List<String> worlds = dataManager.getBannedWorlds();
                sortWorldList(worlds);

                // 创建装饰物品
                IItemBuilder frame = GlassPaneUtil.getStainedGlass(1);
                IItemBuilder last = GlassPaneUtil.getStainedGlass(11);
                IItemBuilder next = GlassPaneUtil.getStainedGlass(14);

                frame.setDisplayName(languageManager.getMessage("gui.introduction"));
                last.setDisplayName(languageManager.getMessage("gui.previous-page"));
                next.setDisplayName(languageManager.getMessage("gui.next-page"));

                frame.addLoreAll(languageManager.getMessageList("gui.world-list-lore"));
                last.addLore(languageManager.getMessage("gui.previous-page-lore"));
                next.addLore(languageManager.getMessage("gui.next-page-lore"));

                // 设置翻页按钮
                if (page > 1) {
                    inventory.setItem(45, last.getItemStack());
                } else {
                    inventory.setItem(45, frame.getItemStack());
                }

                for (int i = 46; i <= 52; i++) {
                    inventory.setItem(i, frame.getItemStack());
                }

                if (worlds.size() > 45 + (page - 1) * 45) {
                    inventory.setItem(53, next.getItemStack());
                } else {
                    inventory.setItem(53, frame.getItemStack());
                }

                // 填充世界列表
                int index = (page - 1) * 45;
                int location = 0;
                int size = worlds.size() - 1;

                while (index <= size && index <= 44 + (page - 1) * 45) {
                    String worldName = worlds.get(index);
                    IItemBuilder itemBuilder;

                    ItemStack displayItem = dataManager.getWorldDisplayItem(worldName);
                    if (displayItem != null) {
                        itemBuilder = new ItemBuilder(displayItem);
                    } else {
                        itemBuilder = new ItemBuilder("GRASS");
                    }

                    int itemNumber = dataManager.getBannedItems(worldName).size();
                    int blockNumber = dataManager.getBannedBlocks(worldName).size();
                    int entityNumber = dataManager.getBannedEntities(worldName).size();
                    int enchNumber = dataManager.getBannedEnchantments(worldName).size();
                    int allNumber = itemNumber + blockNumber + entityNumber + enchNumber;

                    // 设置显示名称
                    String displayWorld = worldName.equals(configManager.getOverAll()) ?
                            languageManager.getMessage("gui.global") : worldName;
                    String displayName = languageManager.getMessage("gui.world-list-name")
                            .replace("{world}", displayWorld);
                    itemBuilder.setDisplayName(displayName);

                    // 设置描述
                    List<String> lore = languageManager.getMessageList("gui.world-list-item-lore");
                    lore = IList.listReplace(lore, "{allNumber}", String.valueOf(allNumber));
                    lore = IList.listReplace(lore, "{itemNumber}", String.valueOf(itemNumber));
                    lore = IList.listReplace(lore, "{blockNumber}", String.valueOf(blockNumber));
                    lore = IList.listReplace(lore, "{entityNumber}", String.valueOf(entityNumber));
                    lore = IList.listReplace(lore, "{enchNumber}", String.valueOf(enchNumber));

                    itemBuilder.setLore(lore);
                    inventory.setItem(location, itemBuilder.getItemStack());
                    SLOT_ITEM.put(location, worldName);

                    location++;
                    index++;
                }
            }
        }.runTask(Banit.plugin);
    }

    /**
     * 处理物品栏点击事件
     *
     * @param evt 物品栏点击事件
     */
    @EventHandler
    private void onInventoryClick(InventoryClickEvent evt) {
        if (!(evt.getWhoClicked() instanceof Player)) {
            return;
        }
        if (!evt.getWhoClicked().equals(player)) {
            return;
        }

        int rawSlot = evt.getRawSlot();
        if (rawSlot == -999) {
            return;
        }

        if (!(evt.getInventory().getHolder() instanceof WorldListInterface)) {
            return;
        }

        evt.setCancelled(true);

        if (evt.getClickedInventory() == null || !(evt.getClickedInventory().getHolder() instanceof WorldListInterface)) {
            return;
        }

        if (IItem.isEmpty(evt.getCurrentItem())) {
            return;
        }

        if (evt.getCurrentItem().getItemMeta() != null && evt.getCurrentItem().getItemMeta().hasDisplayName()) {
            String displayName = evt.getCurrentItem().getItemMeta().getDisplayName();

            if (displayName.equals(languageManager.getMessage("gui.introduction"))) {
                return;
            }
            if (displayName.equals(languageManager.getMessage("gui.previous-page"))) {
                update(--nowPage);
                return;
            }
            if (displayName.equals(languageManager.getMessage("gui.next-page"))) {
                update(++nowPage);
                return;
            }
        }

        String worldName = SLOT_ITEM.get(evt.getRawSlot());
        if (worldName != null) {
            player.closeInventory();
            IInventory.openInventory(new ListInterface(player, 1, worldName), player);
        }
    }

    /**
     * 处理物品栏关闭事件
     *
     * @param evt 物品栏关闭事件
     */
    @EventHandler
    private void onInventoryClose(InventoryCloseEvent evt) {
        if (!(evt.getInventory().getHolder() instanceof WorldListInterface)) {
            return;
        }
        if (!(evt.getPlayer() instanceof Player)) {
            return;
        }
        if (!evt.getPlayer().equals(player)) {
            return;
        }

        WorldListInterface gui = (WorldListInterface) evt.getInventory().getHolder();
        if (!gui.getSerial().equals(this.serial)) {
            return;
        }

        HandlerList.unregisterAll(this);
    }
}