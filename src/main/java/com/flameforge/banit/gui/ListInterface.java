package com.flameforge.banit.gui;

import com.flameforge.banit.Banit;
import com.flameforge.banit.config.DataManager;
import com.flameforge.banit.config.LanguageManager;
import org.bukkit.enchantments.Enchantment;
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
import pers.tany.yukinoaapi.interfacepart.builder.IItemBuilder;
import pers.tany.yukinoaapi.interfacepart.item.IItem;
import pers.tany.yukinoaapi.interfacepart.other.IList;
import pers.tany.yukinoaapi.interfacepart.other.IRandom;
import pers.tany.yukinoaapi.interfacepart.serializer.ISerializer;
import pers.tany.yukinoaapi.realizationpart.builder.ItemBuilder;
import pers.tany.yukinoaapi.realizationpart.item.GlassPaneUtil;

import java.util.*;

/**
 * 禁用列表界面
 * 显示被禁用的物品、方块、实体和附魔的列表
 *
 * <p>v3.0.0 改动：
 * <ul>
 *   <li>所有 Item 类型的展示物品强制堆叠数为 1</li>
 *   <li>排序规则：类型（物品→方块→实体→附魔）→ 展示名字母序</li>
 * </ul>
 *
 * <p>v3.0.1 改动：
 * <ul>
 *   <li>移除按模组命名空间排序的逻辑（依赖 1.13+ 的 Material.getKey() / Enchantment.getKey() / EntityType.getKey()
 *       API，在 1.12 等旧版本上会抛 NoSuchMethodError 导致 GUI 崩溃）</li>
 *   <li>排序规则简化为：类型 → 展示名字母序，无模组分组</li>
 * </ul>
 *
 * @author Tany天炎
 */
public class ListInterface implements InventoryHolder, Listener {

    /** GUI 库存对象 */
    private final Inventory inventory;

    /** 操作玩家 */
    private final Player player;

    /** 世界名称 */
    private final String worldName;

    /** 槽位与序列号映射 */
    private final HashMap<Integer, String> SLOT_ITEM = new HashMap<>();

    /** 当前页码 */
    private int nowPage;

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
     * @param player    玩家对象
     * @param page      当前页码
     * @param worldName 世界名称
     */
    public ListInterface(Player player, int page, String worldName) {
        this.dataManager = Banit.getDataManager();
        this.configManager = Banit.getConfigManager();
        this.languageManager = Banit.getLanguageManager();
        this.player = player;
        this.nowPage = page;
        this.worldName = worldName;
        this.serial = IRandom.createRandomString(8);

        Map<String, Map<String, Object>> allBannedInfo = dataManager.getAllBannedInfo(worldName);

        // 创建标题
        String displayWorld = worldName.equals(configManager.getOverAll()) ?
                languageManager.getMessage("gui.global") : worldName;
        String title = languageManager.getMessage("gui.list-title")
                .replace("{world}", displayWorld)
                .replace("{number}", String.valueOf(allBannedInfo.size()));

        this.inventory = org.bukkit.Bukkit.createInventory(this, 54, title);

        update(nowPage);
        org.bukkit.Bukkit.getPluginManager().registerEvents(this, Banit.plugin);
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
                Map<String, Map<String, Object>> allBannedInfo = dataManager.getAllBannedInfo(worldName);

                // 创建装饰物品
                IItemBuilder frame = GlassPaneUtil.getStainedGlass(1);
                IItemBuilder last = GlassPaneUtil.getStainedGlass(11);
                IItemBuilder next = GlassPaneUtil.getStainedGlass(14);

                frame.setDisplayName(languageManager.getMessage("gui.introduction"));
                last.setDisplayName(languageManager.getMessage("gui.previous-page"));
                next.setDisplayName(languageManager.getMessage("gui.next-page"));

                frame.addLoreAll(languageManager.getMessageList("gui.list-lore"));
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

                if (allBannedInfo.size() > 45 + (page - 1) * 45) {
                    inventory.setItem(53, next.getItemStack());
                } else {
                    inventory.setItem(53, frame.getItemStack());
                }

                // ==================== 排序逻辑 ====================
                // 排序规则：类型（物品→方块→实体→附魔）→ 展示名字母序
                // 预计算展示名，避免比较器中重复反序列化 ItemStack
                List<Map.Entry<String, Map<String, Object>>> sortedEntries = new ArrayList<>(allBannedInfo.entrySet());

                // 预计算每个条目的排序键：展示名
                Map<String, String> sortKeyCache = new HashMap<>();
                for (Map.Entry<String, Map<String, Object>> entry : sortedEntries) {
                    String displayName = getDisplayNameForSort(entry.getValue());
                    sortKeyCache.put(entry.getKey(), displayName);
                }

                sortedEntries.sort((e1, e2) -> {
                    // 第一优先级：类型排序
                    String type1 = (String) e1.getValue().get("Type");
                    String type2 = (String) e2.getValue().get("Type");
                    int typeCompare = getTypeOrder(type1) - getTypeOrder(type2);
                    if (typeCompare != 0) {
                        return typeCompare;
                    }

                    // 第二优先级：展示名字母序
                    String name1 = sortKeyCache.get(e1.getKey());
                    String name2 = sortKeyCache.get(e2.getKey());
                    return name1.compareToIgnoreCase(name2);
                });

                // ==================== 填充物品 ====================
                int index = (page - 1) * 45;
                int location = 0;
                int size = sortedEntries.size() - 1;

                while (index <= size && index <= 44 + (page - 1) * 45) {
                    Map.Entry<String, Map<String, Object>> entry = sortedEntries.get(index);
                    String serialNumber = entry.getKey();
                    Map<String, Object> info = entry.getValue();
                    String type = (String) info.get("Type");

                    IItemBuilder itemBuilder;
                    List<String> lore;

                    switch (type) {
                        case "Item":
                            // 反序列化物品并强制堆叠数为 1（适配旧数据）
                            ItemStack displayItem = ISerializer.deserializeItemStack((String) info.get("ItemStack"));
                            if (displayItem != null) {
                                displayItem.setAmount(1);
                                itemBuilder = new ItemBuilder(displayItem);
                            } else {
                                itemBuilder = new ItemBuilder("BARRIER");
                            }
                            lore = createItemLore(serialNumber, info);
                            break;
                        case "Block":
                            itemBuilder = new ItemBuilder((String) info.get("ID"));
                            lore = createBlockLore(serialNumber, info);
                            break;
                        case "Entity":
                            String species = (String) info.get("Species");
                            if (species != null) {
                                itemBuilder = new ItemBuilder(species + "_SPAWN_EGG");
                            } else {
                                itemBuilder = new ItemBuilder("MONSTER_EGG");
                            }
                            lore = createEntityLore(serialNumber, info);
                            break;
                        case "Ench":
                            itemBuilder = new ItemBuilder("ENCHANTED_BOOK");
                            // 添加附魔效果到附魔书
                            String enchName = (String) info.get("EnchName");
                            if (enchName != null) {
                                Enchantment enchantment = Enchantment.getByName(enchName);
                                if (enchantment != null) {
                                    String levelStr = (String) info.get("Level");
                                    int level = 1;
                                    try {
                                        if (levelStr != null && !levelStr.isEmpty()) {
                                            String numStr = levelStr.replaceAll("[^0-9]", "");
                                            if (!numStr.isEmpty()) {
                                                level = Math.max(1, Integer.parseInt(numStr));
                                            }
                                        }
                                    } catch (NumberFormatException ignored) {
                                    }
                                    level = Math.min(level, enchantment.getMaxLevel() * 2);
                                    itemBuilder.addEnchant(enchantment, level, true);
                                }
                            }
                            lore = createEnchLore(serialNumber, info);
                            break;
                        default:
                            itemBuilder = new ItemBuilder("BARRIER");
                            lore = new ArrayList<>();
                            lore.add(languageManager.getMessage("gui.unknown-type"));
                            break;
                    }

                    if (player.isOp()) {
                        lore.add("");
                        lore.add("");
                        lore.add(languageManager.getMessage("gui.click-to-remove"));
                    }

                    itemBuilder.setLore(lore);
                    inventory.setItem(location, itemBuilder.getItemStack());
                    SLOT_ITEM.put(location, serialNumber);

                    location++;
                    index++;
                }
            }
        }.runTask(Banit.plugin);
    }

    // ==================== 排序辅助方法 ====================

    /**
     * 获取类型的显示顺序
     *
     * @param type 类型字符串
     * @return 顺序值
     */
    private int getTypeOrder(String type) {
        switch (type) {
            case "Item":
                return 0;
            case "Block":
                return 1;
            case "Entity":
                return 2;
            case "Ench":
                return 3;
            default:
                return 4;
        }
    }

    /**
     * 从禁用信息中提取用于排序的展示名
     *
     * <p>v3.0.2 改动：物品类型不再反序列化 ItemStack。
     * 1.12 的 PacketBuffer 对 NBT 字符串字段有硬性长度限制（最大约 1381/151 字节），
     * 大型模组物品（带超长 lore、复杂自定义 NBT）反序列化时会抛 EOFException，
     * 该异常可能以 Throwable 形式逃逸 catch (Exception)。
     * 直接使用 ID 排序既稳定又能让同模组物品自然聚合（ID 前缀相同）。
     *
     * @param info 禁用信息
     * @return 展示名字符串
     */
    private String getDisplayNameForSort(Map<String, Object> info) {
        String type = (String) info.get("Type");

        if ("Item".equals(type)) {
            // 直接使用 ID 排序，不反序列化 ItemStack（避免 1.12 PacketBuffer 长度限制崩溃）
            String id = (String) info.get("ID");
            return id != null ? id : "";
        }

        if ("Block".equals(type)) {
            String id = (String) info.get("ID");
            return id != null ? id : "";
        }

        if ("Entity".equals(type)) {
            String entityType = (String) info.get("EntityType");
            return entityType != null ? entityType : "";
        }

        if ("Ench".equals(type)) {
            String enchName = (String) info.get("EnchName");
            return enchName != null ? enchName : "";
        }

        return "";
    }

    // ==================== Lore 创建方法 ====================

    /**
     * 创建物品的 lore 列表
     *
     * @param serialNumber 序列号
     * @param info         物品信息映射
     * @return lore 列表
     */
    private List<String> createItemLore(String serialNumber, Map<String, Object> info) {
        List<String> lore;
        int distinguish = getIntSafe(info, "Distinguish");

        if (distinguish == 0) {
            lore = new ArrayList<>(languageManager.getMessageList("gui.list-item-lore"));
        } else if (distinguish == 1) {
            lore = new ArrayList<>(languageManager.getMessageList("gui.list-item-all-lore"));
        } else {
            lore = new ArrayList<>(languageManager.getMessageList("gui.list-item-exact-lore"));
        }

        lore = IList.listReplace(lore, "{number}", serialNumber);
        lore = IList.listReplace(lore, "{id}", (String) info.get("ID"));

        Object dataValue = info.get("Data");
        if (dataValue == null) {
            dataValue = 0;
        }
        lore = IList.listReplace(lore, "{data}", dataValue.toString());
        lore = IList.listReplace(lore, "{reason}", (String) info.get("Reason"));
        lore = IList.listReplace(lore, "{clear}", (boolean) info.get("Clear") ?
                languageManager.getMessage("gui.true") : languageManager.getMessage("gui.false"));
        lore = IList.listReplace(lore, "{mode}", getModeString((String) info.get("Mode")));
        lore = IList.listReplace(lore, "{key}", info.get("Key") != null ? (String) info.get("Key") :
                languageManager.getMessage("gui.none"));
        lore = IList.listReplace(lore, "{value}", info.get("Value") != null ? (String) info.get("Value") :
                languageManager.getMessage("gui.none"));

        return lore;
    }

    /**
     * 创建方块的 lore 列表
     *
     * @param serialNumber 序列号
     * @param info         方块信息映射
     * @return lore 列表
     */
    private List<String> createBlockLore(String serialNumber, Map<String, Object> info) {
        List<String> lore;
        int distinguish = getIntSafe(info, "Distinguish");

        if (distinguish == 0) {
            lore = new ArrayList<>(languageManager.getMessageList("gui.list-block-lore"));
        } else if (distinguish == 1) {
            lore = new ArrayList<>(languageManager.getMessageList("gui.list-block-all-lore"));
        } else {
            lore = new ArrayList<>(languageManager.getMessageList("gui.list-block-exact-lore"));
        }

        lore = IList.listReplace(lore, "{number}", serialNumber);
        lore = IList.listReplace(lore, "{id}", (String) info.get("ID"));

        Object dataValue = info.get("Data");
        if (dataValue == null) {
            dataValue = 0;
        }
        lore = IList.listReplace(lore, "{data}", dataValue.toString());
        lore = IList.listReplace(lore, "{tileID}", info.get("TileID") != null ? (String) info.get("TileID") :
                languageManager.getMessage("gui.none"));
        lore = IList.listReplace(lore, "{subTileName}", info.get("SubTileName") != null ? (String) info.get("SubTileName") :
                languageManager.getMessage("gui.none"));
        lore = IList.listReplace(lore, "{reason}", (String) info.get("Reason"));
        lore = IList.listReplace(lore, "{clear}", (boolean) info.get("Clear") ?
                languageManager.getMessage("gui.true") : languageManager.getMessage("gui.false"));
        lore = IList.listReplace(lore, "{mode}", getModeString((String) info.get("Mode")));

        return lore;
    }

    /**
     * 创建实体的 lore 列表
     *
     * @param serialNumber 序列号
     * @param info         实体信息映射
     * @return lore 列表
     */
    private List<String> createEntityLore(String serialNumber, Map<String, Object> info) {
        List<String> lore;
        int distinguish = getIntSafe(info, "Distinguish");

        if (distinguish == 1) {
            lore = new ArrayList<>(languageManager.getMessageList("gui.list-entity-species-lore"));
        } else if (distinguish == 2) {
            lore = new ArrayList<>(languageManager.getMessageList("gui.list-entity-name-lore"));
        } else {
            lore = new ArrayList<>(languageManager.getMessageList("gui.list-entity-lore"));
        }

        lore = IList.listReplace(lore, "{number}", serialNumber);
        lore = IList.listReplace(lore, "{species}", info.get("Species") != null ? (String) info.get("Species") :
                languageManager.getMessage("gui.none"));
        lore = IList.listReplace(lore, "{name}", info.get("Name") != null ? (String) info.get("Name") :
                languageManager.getMessage("gui.none"));
        lore = IList.listReplace(lore, "{reason}", (String) info.get("Reason"));

        return lore;
    }

    /**
     * 创建附魔的 lore 列表
     *
     * @param serialNumber 序列号
     * @param info         附魔信息映射
     * @return lore 列表
     */
    private List<String> createEnchLore(String serialNumber, Map<String, Object> info) {
        List<String> lore = new ArrayList<>(languageManager.getMessageList("gui.list-ench-lore"));

        lore = IList.listReplace(lore, "{number}", serialNumber);
        lore = IList.listReplace(lore, "{enchName}", info.get("EnchName") != null ?
                (String) info.get("EnchName") : languageManager.getMessage("gui.none"));
        lore = IList.listReplace(lore, "{level}", (String) info.get("Level"));
        lore = IList.listReplace(lore, "{reason}", (String) info.get("Reason"));
        lore = IList.listReplace(lore, "{clear}", (boolean) info.get("Clear") ?
                languageManager.getMessage("gui.true") : languageManager.getMessage("gui.false"));
        lore = IList.listReplace(lore, "{mode}", getModeString((String) info.get("Mode")));

        return lore;
    }

    /**
     * 获取模式的本地化描述
     *
     * @param mode 模式代码
     * @return 模式描述
     */
    private String getModeString(String mode) {
        switch (mode) {
            case "a":
                return languageManager.getMessage("mode.all");
            case "r":
                return languageManager.getMessage("mode.right");
            case "l":
                return languageManager.getMessage("mode.left");
            case "sr":
                return languageManager.getMessage("mode.shift-right");
            case "sl":
                return languageManager.getMessage("mode.shift-left");
            case "p":
                return languageManager.getMessage("mode.place");
            case "c":
                return languageManager.getMessage("mode.craft");
            case "d":
                return languageManager.getMessage("mode.drop");
            case "m":
                return languageManager.getMessage("mode.main");
            case "o":
                return languageManager.getMessage("mode.off");
            case "i":
                return languageManager.getMessage("mode.interact");
            case "b":
                return languageManager.getMessage("mode.break");
            case "helmet":
                return languageManager.getMessage("mode.helmet");
            case "chest":
                return languageManager.getMessage("mode.chest");
            case "legs":
                return languageManager.getMessage("mode.legs");
            case "boots":
                return languageManager.getMessage("mode.boots");
            case "armor":
                return languageManager.getMessage("mode.armor");
            case "equip":
                return languageManager.getMessage("mode.equip");
            default:
                return languageManager.getMessage("mode.unknown");
        }
    }

    /**
     * 安全地从 Map 中获取 int 值，兼容 Number 子类
     *
     * @param info 信息映射
     * @param key  键名
     * @return int 值，默认 0
     */
    private int getIntSafe(Map<String, Object> info, String key) {
        Object value = info.get(key);
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        return 0;
    }

    // ==================== 事件处理 ====================

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

        if (!(evt.getInventory().getHolder() instanceof ListInterface)) {
            return;
        }

        evt.setCancelled(true);

        if (evt.getClickedInventory() == null || !(evt.getClickedInventory().getHolder() instanceof ListInterface)) {
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

        if (player.isOp()) {
            String serialNumber = SLOT_ITEM.get(evt.getRawSlot());
            if (serialNumber != null) {
                Map<String, Object> info = dataManager.getAllBannedInfo(worldName).get(serialNumber);
                if (info != null) {
                    String type = (String) info.get("Type");
                    switch (type) {
                        case "Item":
                            dataManager.removeBannedItem(worldName, serialNumber);
                            break;
                        case "Block":
                            dataManager.removeBannedBlock(worldName, serialNumber);
                            break;
                        case "Entity":
                            dataManager.removeBannedEntity(worldName, serialNumber);
                            break;
                        case "Ench":
                            dataManager.removeBannedEnchantment(worldName, serialNumber);
                            break;
                    }
                    player.sendMessage(languageManager.getMessage("command.remove-success"));
                    update(nowPage);
                }
            }
        }
    }

    /**
     * 处理物品栏关闭事件
     *
     * @param evt 物品栏关闭事件
     */
    @EventHandler
    private void onInventoryClose(InventoryCloseEvent evt) {
        if (!(evt.getInventory().getHolder() instanceof ListInterface)) {
            return;
        }
        if (!(evt.getPlayer() instanceof Player)) {
            return;
        }
        if (!evt.getPlayer().equals(player)) {
            return;
        }

        ListInterface gui = (ListInterface) evt.getInventory().getHolder();
        if (!gui.getSerial().equals(this.serial)) {
            return;
        }

        HandlerList.unregisterAll(this);
    }
}
