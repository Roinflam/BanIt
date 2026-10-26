package com.flameforge.banit.gui;

import com.flameforge.banit.Banit;
import com.flameforge.banit.config.LanguageManager;
import com.flameforge.banit.utils.NBTPathUtil;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.ComponentBuilder;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.Material;
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
import pers.tany.yukinoaapi.interfacepart.builder.IItemBuilder;
import pers.tany.yukinoaapi.interfacepart.item.IItem;
import pers.tany.yukinoaapi.interfacepart.other.IRandom;
import pers.tany.yukinoaapi.realizationpart.builder.ItemBuilder;
import pers.tany.yukinoaapi.realizationpart.item.GlassPaneUtil;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * NBT 浏览器界面
 * 交互式浏览物品的 NBT 树形结构
 *
 * @author Tany天炎
 */
public class NBTBrowserInterface implements InventoryHolder, Listener {

    /** GUI 库存对象 */
    private final Inventory inventory;

    /** 操作玩家 */
    private final Player player;

    /** 被浏览的物品 */
    private final ItemStack targetItem;

    /** 当前路径 */
    private String currentPath;

    /** 当前页码 */
    private int currentPage;

    /** 槽位与节点映射 */
    private final Map<Integer, NBTPathUtil.NBTNode> slotNodeMap = new HashMap<>();

    /** 语言管理器 */
    private final LanguageManager languageManager;

    /** 唯一标识符 */
    private final String serial;

    /** 每页显示的节点数量 */
    private static final int ITEMS_PER_PAGE = 45;

    /** 上一页槽位 */
    private static final int PREV_PAGE_SLOT = 45;

    /** 返回按钮槽位 */
    private static final int BACK_SLOT = 48;

    /** 路径显示槽位 */
    private static final int PATH_DISPLAY_SLOT = 49;

    /** 下一页槽位 */
    private static final int NEXT_PAGE_SLOT = 53;

    /**
     * 构造函数
     *
     * @param player     玩家
     * @param itemStack  被浏览的物品
     */
    public NBTBrowserInterface(Player player, ItemStack itemStack) {
        this(player, itemStack, "", 1);
    }

    /**
     * 构造函数（带路径和页码）
     *
     * @param player     玩家
     * @param itemStack  被浏览的物品
     * @param path       当前路径
     * @param page       当前页码
     */
    public NBTBrowserInterface(Player player, ItemStack itemStack, String path, int page) {
        this.languageManager = Banit.getLanguageManager();
        this.player = player;
        this.targetItem = itemStack.clone();
        this.currentPath = path == null ? "" : path;
        this.currentPage = page;
        this.serial = IRandom.createRandomString(8);

        // 创建固定长度标题
        this.inventory = Bukkit.createInventory(this, 54, languageManager.getMessage("nbt-browser.title"));

        update();
        Bukkit.getPluginManager().registerEvents(this, Banit.plugin);
    }

    /**
     * 获取唯一标识符
     */
    public String getSerial() {
        return serial;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    /**
     * 更新界面
     */
    private void update() {
        inventory.clear();
        slotNodeMap.clear();

        new BukkitRunnable() {
            @Override
            public void run() {
                // 获取当前路径的子节点
                List<NBTPathUtil.NBTNode> nodes = NBTPathUtil.getChildren(targetItem, currentPath);

                // 计算分页
                int totalPages = (int) Math.ceil((double) nodes.size() / ITEMS_PER_PAGE);
                if (totalPages == 0) totalPages = 1;
                int startIndex = (currentPage - 1) * ITEMS_PER_PAGE;
                int endIndex = Math.min(startIndex + ITEMS_PER_PAGE, nodes.size());

                // 创建装饰玻璃板
                IItemBuilder frameGray = GlassPaneUtil.getStainedGlass(7); // 灰色玻璃板
                IItemBuilder frameCyan = GlassPaneUtil.getStainedGlass(9); // 青色玻璃板(路径显示)
                IItemBuilder frameBlue = GlassPaneUtil.getStainedGlass(11); // 蓝色玻璃板(上一页)
                IItemBuilder frameOrange = GlassPaneUtil.getStainedGlass(1); // 橙色玻璃板(返回)
                IItemBuilder frameRed = GlassPaneUtil.getStainedGlass(14); // 红色玻璃板(下一页)

                frameGray.setDisplayName(languageManager.getMessage("nbt-browser.decoration"));
                frameGray.addLoreAll(languageManager.getMessageList("nbt-browser.decoration-lore"));

                // 填充节点（槽位 0-44）
                int slot = 0;
                for (int i = startIndex; i < endIndex; i++) {
                    NBTPathUtil.NBTNode node = nodes.get(i);
                    IItemBuilder itemBuilder = createNodeItem(node);
                    inventory.setItem(slot, itemBuilder.getItemStack());
                    slotNodeMap.put(slot, node);
                    slot++;
                }

                // ==================== 底部导航栏（槽位 45-53）====================

                // 槽位 45: 上一页按钮或灰色玻璃板
                if (currentPage > 1) {
                    IItemBuilder prevButton = new ItemBuilder(frameBlue.getItemStack());
                    prevButton.setDisplayName(languageManager.getMessage("gui.previous-page"));
                    prevButton.addLore(languageManager.getMessage("gui.previous-page-lore"));
                    inventory.setItem(PREV_PAGE_SLOT, prevButton.getItemStack());
                } else {
                    inventory.setItem(PREV_PAGE_SLOT, frameGray.getItemStack());
                }

                // 槽位 46-47: 灰色玻璃板
                inventory.setItem(46, frameGray.getItemStack());
                inventory.setItem(47, frameGray.getItemStack());

                // 槽位 48: 返回按钮或灰色玻璃板
                if (!currentPath.isEmpty()) {
                    IItemBuilder backButton = new ItemBuilder(frameOrange.getItemStack());
                    backButton.setDisplayName(languageManager.getMessage("nbt-browser.back"));
                    String parentPath = NBTPathUtil.getParentPath(currentPath);
                    String parentDisplay = parentPath.isEmpty()
                            ? languageManager.getMessage("nbt-browser.root")
                            : parentPath;
                    backButton.addLore(languageManager.getMessage("nbt-browser.back-to")
                            .replace("{path}", "§b" + parentDisplay));
                    inventory.setItem(BACK_SLOT, backButton.getItemStack());
                } else {
                    inventory.setItem(BACK_SLOT, frameGray.getItemStack());
                }

                // 槽位 49: 路径显示（青色玻璃板）
                IItemBuilder pathDisplay = new ItemBuilder(frameCyan.getItemStack());
                pathDisplay.setDisplayName(languageManager.getMessage("nbt-browser.current-path"));

                String pathDisplayText = currentPath.isEmpty()
                        ? languageManager.getMessage("nbt-browser.root")
                        : "§b/" + currentPath.replace("/", "§7/§b");
                pathDisplay.addLore(pathDisplayText);
                pathDisplay.addLore("");
                pathDisplay.addLore(languageManager.getMessage("nbt-browser.total-nodes")
                        .replace("{count}", String.valueOf(nodes.size())));

                if (totalPages > 1) {
                    pathDisplay.addLore(languageManager.getMessage("nbt-browser.page-info")
                            .replace("{current}", String.valueOf(currentPage))
                            .replace("{total}", String.valueOf(totalPages)));
                }

                inventory.setItem(PATH_DISPLAY_SLOT, pathDisplay.getItemStack());

                // 槽位 50-52: 灰色玻璃板
                inventory.setItem(50, frameGray.getItemStack());
                inventory.setItem(51, frameGray.getItemStack());
                inventory.setItem(52, frameGray.getItemStack());

                // 槽位 53: 下一页按钮或灰色玻璃板
                if (currentPage < totalPages) {
                    IItemBuilder nextButton = new ItemBuilder(frameRed.getItemStack());
                    nextButton.setDisplayName(languageManager.getMessage("gui.next-page"));
                    nextButton.addLore(languageManager.getMessage("gui.next-page-lore"));
                    inventory.setItem(NEXT_PAGE_SLOT, nextButton.getItemStack());
                } else {
                    inventory.setItem(NEXT_PAGE_SLOT, frameGray.getItemStack());
                }
            }
        }.runTask(Banit.plugin);
    }

    /**
     * 创建节点的显示物品
     */
    private IItemBuilder createNodeItem(NBTPathUtil.NBTNode node) {
        IItemBuilder builder;

        if (node.navigable) {
            // 可导航的节点（Compound/List）
            builder = new ItemBuilder(Material.CHEST);
            builder.setDisplayName(node.displayName);
            builder.addLore("");
            builder.addLore(languageManager.getMessage("nbt-browser.navigate-hint"));
            builder.addLore("§8" + node.fullPath);
        } else {
            // 基础类型节点
            builder = new ItemBuilder(Material.PAPER);
            builder.setDisplayName(node.displayName);
            builder.addLore("");
            builder.addLore(languageManager.getMessage("nbt-browser.type") + " §e" + node.type);
            builder.addLore("§8" + node.fullPath);

            if (node.value != null) {
                builder.addLore("");
                builder.addLore(languageManager.getMessage("nbt-browser.select-hint"));
                builder.addLore("");
                builder.addLore(languageManager.getMessage("nbt-browser.command-preview"));
                builder.addLore("§f/bi add key:" + node.fullPath + " value:" + truncateValue(node.value.toString(), 30));
            }
        }

        return builder;
    }

    /**
     * 截断值显示
     */
    private String truncateValue(String value, int maxLen) {
        if (value == null) return "";
        if (value.length() <= maxLen) return value;
        return value.substring(0, maxLen) + "...";
    }

    /**
     * 处理点击事件
     */
    @EventHandler
    private void onInventoryClick(InventoryClickEvent evt) {
        if (!(evt.getWhoClicked() instanceof Player)) {
            return;
        }
        if (!evt.getWhoClicked().equals(player)) {
            return;
        }

        if (!(evt.getInventory().getHolder() instanceof NBTBrowserInterface)) {
            return;
        }

        evt.setCancelled(true);

        if (evt.getClickedInventory() == null ||
                !(evt.getClickedInventory().getHolder() instanceof NBTBrowserInterface)) {
            return;
        }

        if (IItem.isEmpty(evt.getCurrentItem())) {
            return;
        }

        int slot = evt.getRawSlot();

        // 返回按钮
        if (slot == BACK_SLOT && !currentPath.isEmpty()) {
            String parentPath = NBTPathUtil.getParentPath(currentPath);
            player.closeInventory();
            new BukkitRunnable() {
                @Override
                public void run() {
                    NBTBrowserInterface newGui = new NBTBrowserInterface(player, targetItem, parentPath, 1);
                    player.openInventory(newGui.getInventory());
                }
            }.runTaskLater(Banit.plugin, 1L);
            return;
        }

        // 上一页
        if (slot == PREV_PAGE_SLOT && currentPage > 1) {
            currentPage--;
            update();
            return;
        }

        // 下一页
        if (slot == NEXT_PAGE_SLOT) {
            currentPage++;
            update();
            return;
        }

        // 节点点击
        NBTPathUtil.NBTNode node = slotNodeMap.get(slot);
        if (node != null) {
            if (node.navigable) {
                // 进入下一层
                player.closeInventory();
                new BukkitRunnable() {
                    @Override
                    public void run() {
                        NBTBrowserInterface newGui = new NBTBrowserInterface(player, targetItem, node.fullPath, 1);
                        player.openInventory(newGui.getInventory());
                    }
                }.runTaskLater(Banit.plugin, 1L);
            } else if (node.value != null) {
                // 选择用于禁用 - 发送可点击复制的消息
                player.closeInventory();

                String command = "/bi add key:" + node.fullPath + " value:" + node.value;

                // 创建可点击复制的文本组件
                TextComponent message = new TextComponent(languageManager.getMessage("nbt-browser.click-to-copy"));
                message.setClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, command));
                message.setHoverEvent(new HoverEvent(
                        HoverEvent.Action.SHOW_TEXT,
                        new ComponentBuilder(languageManager.getMessage("nbt-browser.click-hint")).create()
                ));

                // 发送消息
                player.sendMessage(languageManager.getMessage("nbt-browser.selected-node")
                        .replace("{path}", node.fullPath)
                        .replace("{value}", truncateValue(node.value.toString(), 50)));
                player.spigot().sendMessage(message);
            }
        }
    }

    /**
     * 处理关闭事件
     */
    @EventHandler
    private void onInventoryClose(InventoryCloseEvent evt) {
        if (!(evt.getInventory().getHolder() instanceof NBTBrowserInterface)) {
            return;
        }
        if (!(evt.getPlayer() instanceof Player)) {
            return;
        }
        if (!evt.getPlayer().equals(player)) {
            return;
        }

        NBTBrowserInterface gui = (NBTBrowserInterface) evt.getInventory().getHolder();
        if (!gui.getSerial().equals(this.serial)) {
            return;
        }

        HandlerList.unregisterAll(this);
    }
}