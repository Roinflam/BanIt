package com.flameforge.banit.listenevent;

import com.flameforge.banit.Banit;
import com.flameforge.banit.config.DataManager;
import org.bukkit.Chunk;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.*;
import org.bukkit.inventory.CraftingInventory;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;
import com.flameforge.banit.config.ConfigManager;
import com.flameforge.banit.config.LanguageManager;
import com.flameforge.banit.utils.BanInfo;
import pers.tany.yukinoaapi.interfacepart.block.IBlock;
import pers.tany.yukinoaapi.interfacepart.inventory.IInventoryItem;
import pers.tany.yukinoaapi.interfacepart.item.IItem;
import pers.tany.yukinoaapi.interfacepart.other.IRandom;
import pers.tany.yukinoaapi.interfacepart.player.IPlayer;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 事件监听器类
 * 处理所有与物品禁用相关的游戏事件
 *
 * <p>重构说明：所有检查方法已改为通过 DataManager（缓存层）调用，
 * 不再直接访问 DataStorage（底层存储），避免 MySQL 模式下的高频查库问题。
 *
 * @author Tany天炎
 */
public class Events implements Listener {

    /** 数据管理器（缓存层），替代原来的 DataStorage 直接访问 */
    private final DataManager dataManager;

    /** 配置管理器 */
    private final ConfigManager configManager;

    /** 语言管理器 */
    private final LanguageManager languageManager;

    /** 区块检查冷却记录 */
    private final ConcurrentHashMap<String, Long> chunkCheckCooldown = new ConcurrentHashMap<>();

    /** 已永久检查过的区块集合 */
    private final ConcurrentHashMap<String, Boolean> checkedChunksOnce = new ConcurrentHashMap<>();

    /** 冷却清理任务 */
    private BukkitRunnable cooldownCleanupTask;

    /**
     * 构造函数
     */
    public Events() {
        this.dataManager = Banit.getDataManager();
        this.configManager = Banit.getConfigManager();
        this.languageManager = Banit.getLanguageManager();

        // 启动冷却记录清理任务
        startCooldownCleanupTask();
    }

    /**
     * 启动冷却记录清理任务
     */
    private void startCooldownCleanupTask() {
        cooldownCleanupTask = new BukkitRunnable() {
            @Override
            public void run() {
                long now = System.currentTimeMillis();
                long cooldownTime = configManager.getBlockCheckChunkCooldown();

                // 清理过期的冷却记录
                chunkCheckCooldown.entrySet().removeIf(entry ->
                        (now - entry.getValue()) > cooldownTime * 2
                );

                // 如果不是"只检查一次"模式，也清理永久记录
                if (!configManager.isBlockCheckCheckOncePerChunk()) {
                    checkedChunksOnce.clear();
                }
            }
        };

        // 每5分钟执行一次
        cooldownCleanupTask.runTaskTimerAsynchronously(Banit.plugin, 6000L, 6000L);
    }

    /**
     * 生成区块唯一标识
     *
     * @param world 世界
     * @param chunk 区块
     * @return 唯一标识字符串
     */
    private String getChunkKey(World world, Chunk chunk) {
        return world.getName() + "_" + chunk.getX() + "_" + chunk.getZ();
    }

    /**
     * 玩家登录事件处理
     * 检查玩家背包中是否有禁止物品，如果有且配置为清除则移除
     *
     * @param evt 玩家登录事件
     */
    @EventHandler
    public void onPlayerLogin(PlayerLoginEvent evt) {
        if (configManager.isCheckBackPack()) {
            Player player = evt.getPlayer();

            if (!player.hasPermission("bi.op")) {
                Inventory inventory = player.getInventory();
                World world = player.getWorld();

                for (ItemStack itemStack : inventory.getContents()) {
                    if (!IItem.isEmpty(itemStack)) {
                        String[] worlds = new String[]{world.getName(), configManager.getOverAll()};

                        for (String worldName : worlds) {
                            String serialNumber = dataManager.isBannedItemStack(player, worldName, itemStack);

                            if (serialNumber != null) {
                                boolean clear = dataManager.isBannedItemClear(worldName, serialNumber);
                                String reason = dataManager.getBannedItemReason(worldName, serialNumber);

                                if (clear) {
                                    IInventoryItem.deductItem(inventory, itemStack);
                                    player.sendMessage(languageManager.getMessageBracket("message.item-clear", "reason", reason));
                                }
                                break;
                            }
                        }
                    }
                }
            }
        }
    }

    /**
     * 玩家切换手持物品事件处理
     * 检查切换到的物品是否被禁止
     *
     * @param evt 切换手持物品事件
     */
    @EventHandler
    public void onPlayerItemHeld(PlayerItemHeldEvent evt) {
        Player player = evt.getPlayer();
        Inventory inventory = player.getInventory();
        ItemStack itemStack = inventory.getItem(evt.getNewSlot());
        World world = player.getWorld();

        if (!IItem.isEmpty(itemStack)) {
            String[] worlds = new String[]{world.getName(), configManager.getOverAll()};

            for (String worldName : worlds) {
                // 检查附魔
                String serialNumber = dataManager.isBannedEnchantment(player, worldName, itemStack);
                if (serialNumber != null) {
                    Map<String, Object> enchInfo = dataManager.getBannedInfo(worldName, serialNumber);
                    Enchantment ench = Enchantment.getByName((String) enchInfo.get("EnchName"));
                    boolean clear = (boolean) enchInfo.get("Clear");
                    String reason = (String) enchInfo.get("Reason");

                    if (clear) {
                        itemStack.removeEnchantment(ench);
                        player.sendMessage(languageManager.getMessageBracket("message.ench-clear", "reason", reason));
                    }
                    break;
                }

                // 检查物品
                serialNumber = dataManager.isBannedItemStack(player, worldName, itemStack);
                if (serialNumber != null) {
                    boolean clear = dataManager.isBannedItemClear(worldName, serialNumber);
                    String reason = dataManager.getBannedItemReason(worldName, serialNumber);

                    if (clear) {
                        inventory.setItem(evt.getNewSlot(), null);
                        player.sendMessage(languageManager.getMessageBracket("message.item-clear", "reason", reason));
                    }
                    break;
                }
            }
        }
    }

    /**
     * 安全获取点击的背包
     * 处理异常的原始槽位索引
     *
     * @param evt 背包点击事件
     * @return 被点击的背包，异常时返回 null
     */
    private Inventory getClickedInventorySafe(InventoryClickEvent evt) {
        try {
            int rawSlot = evt.getRawSlot();
            if (rawSlot < 0) {
                return null;
            }
            return evt.getClickedInventory();
        } catch (IllegalArgumentException | NullPointerException e) {
            return null;
        }
    }

    /**
     * 物品栏点击事件处理
     * 检查点击的物品是否被禁止
     *
     * <p>InventoryCheck 开启、或者点的是玩家自己的背包时只匹配一次：命中需要清除的条目就取消这次点击并提示，
     * 点的是玩家背包的话再从背包里扣掉这件物品。两种情况合在一起判断，点自己背包时不会重复匹配，也不会收到两条提示。
     *
     * @param evt 背包点击事件
     */
    @EventHandler(ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent evt) {
        if (!(evt.getWhoClicked() instanceof Player)) {
            return;
        }

        Inventory clickedInventory = getClickedInventorySafe(evt);
        if (clickedInventory == null) {
            return;
        }

        Player player = (Player) evt.getWhoClicked();
        World world = player.getWorld();
        ItemStack itemStack = evt.getCurrentItem();
        boolean isPlayerInventory = clickedInventory.getType().equals(InventoryType.PLAYER);

        // 只要 InventoryCheck 开启，或者点击的是玩家自己的背包，就需要检查
        if ((configManager.isInventoryCheck() || isPlayerInventory) && !IItem.isEmpty(itemStack)) {
            String[] worlds = new String[]{world.getName(), configManager.getOverAll()};

            for (String worldName : worlds) {
                String serialNumber = dataManager.isBannedItemStack(player, worldName, itemStack);

                if (serialNumber != null) {
                    if (player.hasPermission("bi.ignore." + serialNumber) || player.hasPermission("bi.op")) {
                        continue;
                    }

                    boolean clear = dataManager.isBannedItemClear(worldName, serialNumber);
                    String reason = dataManager.getBannedItemReason(worldName, serialNumber);

                    if (clear) {
                        evt.setCancelled(true);
                        // 点的是玩家自己的背包时，顺带从背包里扣掉这件
                        if (isPlayerInventory) {
                            IInventoryItem.deductItem(player.getInventory(), itemStack);
                        }
                        player.sendMessage(languageManager.getMessageBracket("message.item-clear", "reason", reason));
                    }
                    break;
                }
            }
        }
    }

    /**
     * 玩家拾取物品事件处理
     * 检查拾取的物品是否被禁止
     *
     * @param evt 拾取物品事件
     */
    @EventHandler
    public void onPlayerPickupItem(PlayerPickupItemEvent evt) {
        Player player = evt.getPlayer();
        World world = player.getWorld();
        ItemStack itemStack = evt.getItem().getItemStack();

        if (!IItem.isEmpty(itemStack)) {
            String[] worlds = new String[]{world.getName(), configManager.getOverAll()};

            for (String worldName : worlds) {
                // 检查附魔
                String serialNumber = dataManager.isBannedEnchantment(player, worldName, itemStack);
                if (serialNumber != null) {
                    Map<String, Object> enchInfo = dataManager.getBannedInfo(worldName, serialNumber);
                    Enchantment ench = Enchantment.getByName((String) enchInfo.get("EnchName"));
                    boolean clear = (boolean) enchInfo.get("Clear");
                    String reason = (String) enchInfo.get("Reason");

                    if (clear) {
                        itemStack.removeEnchantment(ench);
                        player.sendMessage(languageManager.getMessageBracket("message.ench-clear", "reason", reason));
                    }
                    break;
                }

                // 检查物品
                serialNumber = dataManager.isBannedItemStack(player, worldName, itemStack);
                if (serialNumber != null) {
                    boolean clear = dataManager.isBannedItemClear(worldName, serialNumber);
                    String reason = dataManager.getBannedItemReason(worldName, serialNumber);

                    if (clear) {
                        evt.setCancelled(true);
                        evt.getItem().remove();
                        player.sendMessage(languageManager.getMessageBracket("message.item-clear", "reason", reason));
                    }
                    break;
                }
            }
        }
    }

    /**
     * 玩家丢弃物品事件处理
     * 检查丢弃的物品是否被禁止
     *
     * @param evt 丢弃物品事件
     */
    @EventHandler
    public void onPlayerDropItem(PlayerDropItemEvent evt) {
        if (configManager.isOptimization() && !IRandom.percentageChance(10)) {
            return;
        }

        Player player = evt.getPlayer();
        World world = player.getWorld();
        ItemStack itemStack = evt.getItemDrop().getItemStack();

        if (!IItem.isEmpty(itemStack)) {
            String[] worlds = new String[]{world.getName(), configManager.getOverAll()};

            for (String worldName : worlds) {
                // 检查附魔
                String serialNumber = dataManager.isBannedEnchantment(player, worldName, itemStack);
                if (serialNumber != null) {
                    Map<String, Object> enchInfo = dataManager.getBannedInfo(worldName, serialNumber);
                    Enchantment ench = Enchantment.getByName((String) enchInfo.get("EnchName"));
                    boolean clear = (boolean) enchInfo.get("Clear");
                    String mode = (String) enchInfo.get("Mode");
                    String reason = (String) enchInfo.get("Reason");

                    if (clear) {
                        itemStack.removeEnchantment(ench);
                        player.sendMessage(languageManager.getMessageBracket("message.ench-clear", "reason", reason));
                    } else if (mode.equals("d")) {
                        evt.setCancelled(true);
                        player.sendMessage(languageManager.getMessageBracket("message.ench-no-drop", "reason", reason));
                    }
                    break;
                }

                // 检查物品
                serialNumber = dataManager.isBannedItemStack(player, worldName, itemStack);
                if (serialNumber != null) {
                    boolean clear = dataManager.isBannedItemClear(worldName, serialNumber);
                    String mode = dataManager.getBannedItemMode(worldName, serialNumber);
                    String reason = dataManager.getBannedItemReason(worldName, serialNumber);

                    if (clear) {
                        evt.getItemDrop().remove();
                        player.sendMessage(languageManager.getMessageBracket("message.item-clear", "reason", reason));
                    } else if (mode.equals("d")) {
                        evt.setCancelled(true);
                        player.sendMessage(languageManager.getMessageBracket("message.item-no-drop", "reason", reason));
                    }
                    break;
                }
            }
        }
    }

    /**
     * 玩家交互事件处理
     * 检查交互的方块和使用的物品是否被禁止
     *
     * @param evt 玩家交互事件
     */
    @EventHandler
    public void onPlayerInteractEvent(PlayerInteractEvent evt) {
        Player player = evt.getPlayer();
        World world = player.getWorld();
        ItemStack itemStack = evt.getItem();
        String[] worlds = new String[]{world.getName(), configManager.getOverAll()};

        // 方块交互检查
        if (evt.getAction().equals(Action.LEFT_CLICK_BLOCK) || evt.getAction().equals(Action.RIGHT_CLICK_BLOCK)) {
            Block block = evt.getClickedBlock();

            for (String worldName : worlds) {
                String serialNumber = dataManager.isBannedBlock(worldName, block);

                if (serialNumber != null) {
                    Map<String, Object> blockInfo = dataManager.getBannedInfo(worldName, serialNumber);
                    boolean clear = (boolean) blockInfo.get("Clear");
                    String mode = (String) blockInfo.get("Mode");
                    String reason = (String) blockInfo.get("Reason");

                    if (clear) {
                        evt.setCancelled(true);
                        block.setType(Material.AIR);
                        player.sendMessage(languageManager.getMessageBracket("message.block-clear", "reason", reason));
                    } else if (mode.equals("a")) {
                        evt.setCancelled(true);
                        player.sendMessage(languageManager.getMessageBracket("message.block-no-all", "reason", reason));
                    } else if (mode.equals("i")) {
                        evt.setCancelled(true);
                        player.sendMessage(languageManager.getMessageBracket("message.block-no-interact", "reason", reason));
                    }
                    break;
                }
            }
        }

        // 物品交互检查
        if (!IItem.isEmpty(itemStack)) {
            for (String worldName : worlds) {
                // 检查附魔
                String serialNumber = dataManager.isBannedEnchantment(player, worldName, itemStack);
                if (serialNumber != null) {
                    Map<String, Object> enchInfo = dataManager.getBannedInfo(worldName, serialNumber);
                    Enchantment ench = Enchantment.getByName((String) enchInfo.get("EnchName"));
                    boolean clear = (boolean) enchInfo.get("Clear");
                    String mode = (String) enchInfo.get("Mode");
                    String reason = (String) enchInfo.get("Reason");

                    if (clear) {
                        itemStack.removeEnchantment(ench);
                        player.sendMessage(languageManager.getMessageBracket("message.ench-clear", "reason", reason));
                    } else if (mode.equals("a")) {
                        evt.setCancelled(true);
                        handleCantUse(player, itemStack);
                        player.sendMessage(languageManager.getMessageBracket("message.ench-no-all", "reason", reason));
                    } else if (mode.equals("r") && (evt.getAction().equals(Action.RIGHT_CLICK_AIR) || evt.getAction().equals(Action.RIGHT_CLICK_BLOCK))) {
                        evt.setCancelled(true);
                        handleCantUse(player, itemStack);
                        player.sendMessage(languageManager.getMessageBracket("message.ench-no-right", "reason", reason));
                    } else if (mode.equals("l") && (evt.getAction().equals(Action.LEFT_CLICK_AIR) || evt.getAction().equals(Action.LEFT_CLICK_BLOCK))) {
                        evt.setCancelled(true);
                        handleCantUse(player, itemStack);
                        player.sendMessage(languageManager.getMessageBracket("message.ench-no-left", "reason", reason));
                    } else if (mode.equals("sr") && player.isSneaking() && (evt.getAction().equals(Action.RIGHT_CLICK_AIR) || evt.getAction().equals(Action.RIGHT_CLICK_BLOCK))) {
                        evt.setCancelled(true);
                        handleCantUse(player, itemStack);
                        player.sendMessage(languageManager.getMessageBracket("message.ench-no-shift-right", "reason", reason));
                    } else if (mode.equals("sl") && player.isSneaking() && (evt.getAction().equals(Action.LEFT_CLICK_AIR) || evt.getAction().equals(Action.LEFT_CLICK_BLOCK))) {
                        evt.setCancelled(true);
                        handleCantUse(player, itemStack);
                        player.sendMessage(languageManager.getMessageBracket("message.ench-no-shift-left", "reason", reason));
                    }
                    break;
                }

                // 检查物品
                serialNumber = dataManager.isBannedItemStack(player, worldName, itemStack);
                if (serialNumber != null) {
                    boolean clear = dataManager.isBannedItemClear(worldName, serialNumber);
                    String mode = dataManager.getBannedItemMode(worldName, serialNumber);
                    String reason = dataManager.getBannedItemReason(worldName, serialNumber);

                    if (clear) {
                        evt.setCancelled(true);
                        itemStack.setAmount(0);
                        player.setItemInHand(null);
                        player.sendMessage(languageManager.getMessageBracket("message.item-clear", "reason", reason));
                        return;
                    } else if (mode.equals("a")) {
                        evt.setCancelled(true);
                        handleCantUse(player, itemStack);
                        player.sendMessage(languageManager.getMessageBracket("message.item-no-all", "reason", reason));
                    } else if (mode.equals("r") && (evt.getAction().equals(Action.RIGHT_CLICK_AIR) || evt.getAction().equals(Action.RIGHT_CLICK_BLOCK))) {
                        evt.setCancelled(true);
                        handleCantUse(player, itemStack);
                        player.sendMessage(languageManager.getMessageBracket("message.item-no-right", "reason", reason));
                    } else if (mode.equals("l") && (evt.getAction().equals(Action.LEFT_CLICK_AIR) || evt.getAction().equals(Action.LEFT_CLICK_BLOCK))) {
                        evt.setCancelled(true);
                        handleCantUse(player, itemStack);
                        player.sendMessage(languageManager.getMessageBracket("message.item-no-left", "reason", reason));
                    } else if (mode.equals("sr") && player.isSneaking() && (evt.getAction().equals(Action.RIGHT_CLICK_AIR) || evt.getAction().equals(Action.RIGHT_CLICK_BLOCK))) {
                        evt.setCancelled(true);
                        handleCantUse(player, itemStack);
                        player.sendMessage(languageManager.getMessageBracket("message.item-no-shift-right", "reason", reason));
                    } else if (mode.equals("sl") && player.isSneaking() && (evt.getAction().equals(Action.LEFT_CLICK_AIR) || evt.getAction().equals(Action.LEFT_CLICK_BLOCK))) {
                        evt.setCancelled(true);
                        handleCantUse(player, itemStack);
                        player.sendMessage(languageManager.getMessageBracket("message.item-no-shift-left", "reason", reason));
                    }
                    break;
                }
            }
        }
    }

    /**
     * 玩家与实体交互事件处理
     *
     * @param evt 玩家与实体交互事件
     */
    @EventHandler
    public void onPlayerInteractEntity(PlayerInteractEntityEvent evt) {
        if (configManager.isOptimization() && !IRandom.percentageChance(10)) {
            return;
        }

        Player player = evt.getPlayer();
        World world = player.getWorld();
        ItemStack itemStack = player.getItemInHand();

        if (!IItem.isEmpty(itemStack)) {
            String[] worlds = new String[]{world.getName(), configManager.getOverAll()};

            for (String worldName : worlds) {
                // 检查附魔
                String serialNumber = dataManager.isBannedEnchantment(player, worldName, itemStack);
                if (serialNumber != null) {
                    Map<String, Object> enchInfo = dataManager.getBannedInfo(worldName, serialNumber);
                    Enchantment ench = Enchantment.getByName((String) enchInfo.get("EnchName"));
                    boolean clear = (boolean) enchInfo.get("Clear");
                    String mode = (String) enchInfo.get("Mode");
                    String reason = (String) enchInfo.get("Reason");

                    if (clear) {
                        itemStack.removeEnchantment(ench);
                        player.sendMessage(languageManager.getMessageBracket("message.ench-clear", "reason", reason));
                    } else if (mode.equals("a")) {
                        evt.setCancelled(true);
                        handleCantUse(player, itemStack);
                        player.sendMessage(languageManager.getMessageBracket("message.ench-no-all", "reason", reason));
                    }
                    break;
                }

                // 检查物品
                serialNumber = dataManager.isBannedItemStack(player, worldName, itemStack);
                if (serialNumber != null) {
                    boolean clear = dataManager.isBannedItemClear(worldName, serialNumber);
                    String mode = dataManager.getBannedItemMode(worldName, serialNumber);
                    String reason = dataManager.getBannedItemReason(worldName, serialNumber);

                    if (clear) {
                        evt.setCancelled(true);
                        player.setItemInHand(null);
                        player.sendMessage(languageManager.getMessageBracket("message.item-clear", "reason", reason));
                    } else if (mode.equals("a")) {
                        evt.setCancelled(true);
                        handleCantUse(player, itemStack);
                        player.sendMessage(languageManager.getMessageBracket("message.item-no-all", "reason", reason));
                    }
                    break;
                }
            }
        }
    }

    /**
     * 方块放置事件处理
     * 检查放置的方块和使用的物品是否被禁止
     *
     * @param evt 方块放置事件
     */
    @EventHandler
    public void onBlockPlaceEvent(BlockPlaceEvent evt) {
        Player player = evt.getPlayer();
        World world = player.getWorld();
        ItemStack itemStack = evt.getItemInHand();
        Block block = evt.getBlock();
        String[] worlds = new String[]{world.getName(), configManager.getOverAll()};

        // 方块检查
        for (String worldName : worlds) {
            String serialNumber = dataManager.isBannedBlock(worldName, block);

            if (serialNumber != null) {
                Map<String, Object> blockInfo = dataManager.getBannedInfo(worldName, serialNumber);
                boolean clear = (boolean) blockInfo.get("Clear");
                String mode = (String) blockInfo.get("Mode");
                String reason = (String) blockInfo.get("Reason");

                if (clear) {
                    evt.setCancelled(true);
                    block.setType(Material.AIR);
                    player.sendMessage(languageManager.getMessageBracket("message.block-clear", "reason", reason));
                } else if (mode.equals("a")) {
                    evt.setCancelled(true);
                    player.sendMessage(languageManager.getMessageBracket("message.block-no-all", "reason", reason));
                } else if (mode.equals("p")) {
                    evt.setCancelled(true);
                    player.sendMessage(languageManager.getMessageBracket("message.block-no-place", "reason", reason));
                }
                break;
            }
        }

        // 物品检查
        if (!IItem.isEmpty(itemStack)) {
            for (String worldName : worlds) {
                // 检查附魔
                String serialNumber = dataManager.isBannedEnchantment(player, worldName, itemStack);
                if (serialNumber != null) {
                    Map<String, Object> enchInfo = dataManager.getBannedInfo(worldName, serialNumber);
                    Enchantment ench = Enchantment.getByName((String) enchInfo.get("EnchName"));
                    boolean clear = (boolean) enchInfo.get("Clear");
                    String mode = (String) enchInfo.get("Mode");
                    String reason = (String) enchInfo.get("Reason");

                    if (clear) {
                        evt.setCancelled(true);
                        itemStack.removeEnchantment(ench);
                        player.sendMessage(languageManager.getMessageBracket("message.ench-clear", "reason", reason));
                    } else if (mode.equals("a") || mode.equals("r") || (mode.equals("sr") && player.isSneaking())) {
                        evt.setCancelled(true);
                        player.sendMessage(languageManager.getMessageBracket("message.ench-no-all", "reason", reason));
                    }
                    break;
                }

                // 检查物品
                serialNumber = dataManager.isBannedItemStack(player, worldName, itemStack);
                if (serialNumber != null) {
                    boolean clear = dataManager.isBannedItemClear(worldName, serialNumber);
                    String mode = dataManager.getBannedItemMode(worldName, serialNumber);
                    String reason = dataManager.getBannedItemReason(worldName, serialNumber);

                    if (clear) {
                        evt.setCancelled(true);
                        player.setItemInHand(null);
                        player.sendMessage(languageManager.getMessageBracket("message.item-clear", "reason", reason));
                    } else if (mode.equals("a")) {
                        evt.setCancelled(true);
                        player.sendMessage(languageManager.getMessageBracket("message.item-no-all", "reason", reason));
                    } else if (mode.equals("p")) {
                        evt.setCancelled(true);
                        player.sendMessage(languageManager.getMessageBracket("message.item-no-place", "reason", reason));
                    }
                    break;
                }
            }
        }
    }

    /**
     * 方块破坏事件处理
     * 检查被破坏的方块和使用的工具是否被禁止
     *
     * @param evt 方块破坏事件
     */
    @EventHandler
    public void onBlockBreak(BlockBreakEvent evt) {
        if (configManager.isOptimization() && !IRandom.percentageChance(2.5)) {
            return;
        }

        Player player = evt.getPlayer();
        World world = player.getWorld();
        ItemStack itemStack = player.getItemInHand();
        Block block = evt.getBlock();
        String[] worlds = new String[]{world.getName(), configManager.getOverAll()};

        // 方块检查
        for (String worldName : worlds) {
            String serialNumber = dataManager.isBannedBlock(worldName, block);

            if (serialNumber != null) {
                Map<String, Object> blockInfo = dataManager.getBannedInfo(worldName, serialNumber);
                boolean clear = (boolean) blockInfo.get("Clear");
                String mode = (String) blockInfo.get("Mode");
                String reason = (String) blockInfo.get("Reason");

                if (clear) {
                    evt.setCancelled(true);
                    block.setType(Material.AIR);
                    player.sendMessage(languageManager.getMessageBracket("message.block-clear", "reason", reason));
                } else if (mode.equals("a")) {
                    evt.setCancelled(true);
                    player.sendMessage(languageManager.getMessageBracket("message.block-no-all", "reason", reason));
                } else if (mode.equals("b")) {
                    evt.setCancelled(true);
                    player.sendMessage(languageManager.getMessageBracket("message.block-no-break", "reason", reason));
                }
                break;
            }
        }

        // 物品检查
        if (!IItem.isEmpty(itemStack)) {
            for (String worldName : worlds) {
                // 检查附魔
                String serialNumber = dataManager.isBannedEnchantment(player, worldName, itemStack);
                if (serialNumber != null) {
                    Map<String, Object> enchInfo = dataManager.getBannedInfo(worldName, serialNumber);
                    Enchantment ench = Enchantment.getByName((String) enchInfo.get("EnchName"));
                    boolean clear = (boolean) enchInfo.get("Clear");
                    String mode = (String) enchInfo.get("Mode");
                    String reason = (String) enchInfo.get("Reason");

                    if (clear) {
                        evt.setCancelled(true);
                        itemStack.removeEnchantment(ench);
                        player.sendMessage(languageManager.getMessageBracket("message.ench-clear", "reason", reason));
                    } else if (mode.equals("a") || mode.equals("l") || (mode.equals("sl") && player.isSneaking())) {
                        evt.setCancelled(true);
                        player.sendMessage(languageManager.getMessageBracket("message.ench-no-all", "reason", reason));
                    }
                    break;
                }

                // 检查物品
                serialNumber = dataManager.isBannedItemStack(player, worldName, itemStack);
                if (serialNumber != null) {
                    boolean clear = dataManager.isBannedItemClear(worldName, serialNumber);
                    String mode = dataManager.getBannedItemMode(worldName, serialNumber);
                    String reason = dataManager.getBannedItemReason(worldName, serialNumber);

                    if (clear) {
                        evt.setCancelled(true);
                        player.setItemInHand(null);
                        player.sendMessage(languageManager.getMessageBracket("message.item-clear", "reason", reason));
                    } else if (mode.equals("a") || mode.equals("l") || (mode.equals("sl") && player.isSneaking())) {
                        evt.setCancelled(true);
                        player.sendMessage(languageManager.getMessageBracket("message.item-no-all", "reason", reason));
                    }
                    break;
                }
            }
        }
    }

    /**
     * 实体受伤事件处理
     * 检查攻击者和被攻击实体是否被禁止，以及玩家的武器/护甲是否被禁止
     *
     * @param evt 实体受伤事件
     */
    @EventHandler
    public void onEntityDamageByEntity(EntityDamageByEntityEvent evt) {
        if (configManager.isOptimization() && !IRandom.percentageChance(2.5)) {
            return;
        }

        // 检查被攻击的实体
        if (evt.getEntity() instanceof LivingEntity && !(evt.getEntity() instanceof Player)) {
            LivingEntity livingEntity = (LivingEntity) evt.getEntity();
            World world = livingEntity.getWorld();
            String[] worlds = new String[]{world.getName(), configManager.getOverAll()};

            for (String worldName : worlds) {
                String serialNumber = dataManager.isBannedEntity(worldName, livingEntity);
                if (serialNumber != null) {
                    evt.setCancelled(true);
                    livingEntity.remove();
                    break;
                }
            }
        }

        // 检查攻击者
        if (evt.getDamager() instanceof LivingEntity && !(evt.getDamager() instanceof Player)) {
            LivingEntity livingEntity = (LivingEntity) evt.getDamager();
            World world = livingEntity.getWorld();
            String[] worlds = new String[]{world.getName(), configManager.getOverAll()};

            for (String worldName : worlds) {
                String serialNumber = dataManager.isBannedEntity(worldName, livingEntity);
                if (serialNumber != null) {
                    evt.setCancelled(true);
                    livingEntity.remove();
                    break;
                }
            }
        }

        // 检查抛射物发射者
        if (evt.getDamager() instanceof Projectile) {
            Projectile projectile = (Projectile) evt.getDamager();
            if (projectile.getShooter() instanceof LivingEntity && !(projectile.getShooter() instanceof Player)) {
                LivingEntity livingEntity = (LivingEntity) projectile.getShooter();
                World world = livingEntity.getWorld();
                String[] worlds = new String[]{world.getName(), configManager.getOverAll()};

                for (String worldName : worlds) {
                    String serialNumber = dataManager.isBannedEntity(worldName, livingEntity);
                    if (serialNumber != null) {
                        evt.setCancelled(true);
                        livingEntity.remove();
                        break;
                    }
                }
            }
        }

        // 检查被攻击的玩家护甲
        if (evt.getEntity() instanceof Player) {
            Player player = (Player) evt.getEntity();
            World world = player.getWorld();

            for (ItemStack itemStack : player.getInventory().getArmorContents()) {
                if (!IItem.isEmpty(itemStack)) {
                    String[] worlds = new String[]{world.getName(), configManager.getOverAll()};

                    for (String worldName : worlds) {
                        String serialNumber = dataManager.isBannedEnchantment(player, worldName, itemStack);
                        if (serialNumber != null) {
                            Map<String, Object> enchInfo = dataManager.getBannedInfo(worldName, serialNumber);
                            Enchantment ench = Enchantment.getByName((String) enchInfo.get("EnchName"));
                            boolean clear = (boolean) enchInfo.get("Clear");
                            String mode = (String) enchInfo.get("Mode");
                            String reason = (String) enchInfo.get("Reason");

                            if (clear) {
                                itemStack.removeEnchantment(ench);
                                player.sendMessage(languageManager.getMessageBracket("message.ench-clear", "reason", reason));
                            } else if (mode.equals("a")) {
                                evt.setCancelled(true);
                                IInventoryItem.deductItem(player.getInventory(), itemStack);
                                IPlayer.giveItem(player, itemStack);
                                player.sendMessage(languageManager.getMessageBracket("message.ench-no-all", "reason", reason));
                            }
                            break;
                        }

                        serialNumber = dataManager.isBannedItemStack(player, worldName, itemStack);
                        if (serialNumber != null) {
                            boolean clear = dataManager.isBannedItemClear(worldName, serialNumber);
                            String mode = dataManager.getBannedItemMode(worldName, serialNumber);
                            String reason = dataManager.getBannedItemReason(worldName, serialNumber);

                            if (clear) {
                                evt.setCancelled(true);
                                IInventoryItem.deductItem(player.getInventory(), itemStack);
                                player.sendMessage(languageManager.getMessageBracket("message.item-clear", "reason", reason));
                            } else if (mode.equals("a")) {
                                evt.setCancelled(true);
                                IInventoryItem.deductItem(player.getInventory(), itemStack);
                                IPlayer.giveItem(player, itemStack);
                                player.sendMessage(languageManager.getMessageBracket("message.item-no-all", "reason", reason));
                            }
                            break;
                        }
                    }
                }
            }
        }

        // 检查攻击者玩家手持物品
        if (evt.getDamager() instanceof Player) {
            Player player = (Player) evt.getDamager();
            World world = player.getWorld();
            ItemStack itemStack = player.getItemInHand();

            if (!IItem.isEmpty(itemStack)) {
                String[] worlds = new String[]{world.getName(), configManager.getOverAll()};

                for (String worldName : worlds) {
                    String serialNumber = dataManager.isBannedEnchantment(player, worldName, itemStack);
                    if (serialNumber != null) {
                        Map<String, Object> enchInfo = dataManager.getBannedInfo(worldName, serialNumber);
                        Enchantment ench = Enchantment.getByName((String) enchInfo.get("EnchName"));
                        boolean clear = (boolean) enchInfo.get("Clear");
                        String mode = (String) enchInfo.get("Mode");
                        String reason = (String) enchInfo.get("Reason");

                        if (clear) {
                            evt.setCancelled(true);
                            itemStack.removeEnchantment(ench);
                            player.sendMessage(languageManager.getMessageBracket("message.ench-clear", "reason", reason));
                        } else if (mode.equals("a") || mode.equals("l") || (mode.equals("sl") && player.isSneaking())) {
                            evt.setCancelled(true);
                            handleCantUse(player, itemStack);
                            player.sendMessage(languageManager.getMessageBracket("message.ench-no-all", "reason", reason));
                        }
                        break;
                    }

                    serialNumber = dataManager.isBannedItemStack(player, worldName, itemStack);
                    if (serialNumber != null) {
                        boolean clear = dataManager.isBannedItemClear(worldName, serialNumber);
                        String mode = dataManager.getBannedItemMode(worldName, serialNumber);
                        String reason = dataManager.getBannedItemReason(worldName, serialNumber);

                        if (clear) {
                            evt.setCancelled(true);
                            player.setItemInHand(null);
                            handleCantUse(player, itemStack);
                            player.sendMessage(languageManager.getMessageBracket("message.item-clear", "reason", reason));
                        } else if (mode.equals("a")) {
                            evt.setCancelled(true);
                            handleCantUse(player, itemStack);
                            player.sendMessage(languageManager.getMessageBracket("message.item-no-all", "reason", reason));
                        }
                        break;
                    }
                }
            }
        }

        // 检查抛射物发射者为玩家
        if (!(evt.getDamager() instanceof Player) && evt.getDamager() instanceof Projectile) {
            Projectile projectile = (Projectile) evt.getDamager();

            if (projectile.getShooter() instanceof Player) {
                Player player = (Player) projectile.getShooter();
                World world = player.getWorld();
                ItemStack itemStack = player.getItemInHand();

                if (!IItem.isEmpty(itemStack)) {
                    String[] worlds = new String[]{world.getName(), configManager.getOverAll()};

                    for (String worldName : worlds) {
                        String serialNumber = dataManager.isBannedEnchantment(player, worldName, itemStack);
                        if (serialNumber != null) {
                            Map<String, Object> enchInfo = dataManager.getBannedInfo(worldName, serialNumber);
                            Enchantment ench = Enchantment.getByName((String) enchInfo.get("EnchName"));
                            boolean clear = (boolean) enchInfo.get("Clear");
                            String mode = (String) enchInfo.get("Mode");
                            String reason = (String) enchInfo.get("Reason");

                            if (clear) {
                                evt.setCancelled(true);
                                itemStack.removeEnchantment(ench);
                                player.sendMessage(languageManager.getMessageBracket("message.ench-clear", "reason", reason));
                            } else if (mode.equals("a") || mode.equals("r")) {
                                // 不看 sr：这里是抛射物命中的时候，射手此刻蹲没蹲和射出去时无关；潜行时拉弓 / 扔出已经在右键那一步拦下
                                evt.setCancelled(true);
                                handleCantUse(player, itemStack);
                                player.sendMessage(languageManager.getMessageBracket("message.ench-no-all", "reason", reason));
                            }
                            break;
                        }

                        serialNumber = dataManager.isBannedItemStack(player, worldName, itemStack);
                        if (serialNumber != null) {
                            boolean clear = dataManager.isBannedItemClear(worldName, serialNumber);
                            String mode = dataManager.getBannedItemMode(worldName, serialNumber);
                            String reason = dataManager.getBannedItemReason(worldName, serialNumber);

                            if (clear) {
                                evt.setCancelled(true);
                                player.setItemInHand(null);
                                handleCantUse(player, itemStack);
                                player.sendMessage(languageManager.getMessageBracket("message.item-clear", "reason", reason));
                            } else if (mode.equals("a")) {
                                evt.setCancelled(true);
                                handleCantUse(player, itemStack);
                                player.sendMessage(languageManager.getMessageBracket("message.item-no-all", "reason", reason));
                            }
                            break;
                        }
                    }
                }
            }
        }
    }

    /**
     * 物品合成事件处理
     * 检查合成产物是否被禁止
     *
     * @param evt 合成事件
     */
    @EventHandler
    public void onCraftItem(CraftItemEvent evt) {
        if (!(evt.getWhoClicked() instanceof Player)) {
            return;
        }

        Player player = (Player) evt.getWhoClicked();
        World world = player.getWorld();
        ItemStack itemStack = evt.getCurrentItem();
        CraftingInventory craftingInventory = evt.getInventory();

        if (!IItem.isEmpty(itemStack)) {
            String[] worlds = new String[]{world.getName(), configManager.getOverAll()};

            for (String worldName : worlds) {
                String serialNumber = dataManager.isBannedItemStack(player, worldName, itemStack);

                if (serialNumber != null) {
                    boolean clear = dataManager.isBannedItemClear(worldName, serialNumber);
                    String mode = dataManager.getBannedItemMode(worldName, serialNumber);
                    String reason = dataManager.getBannedItemReason(worldName, serialNumber);

                    if (clear) {
                        evt.setCancelled(true);
                        IInventoryItem.deductItem(craftingInventory, itemStack);
                        player.sendMessage(languageManager.getMessageBracket("message.item-no-craft", "reason", reason));

                        new BukkitRunnable() {
                            @Override
                            public void run() {
                                if (player.getItemOnCursor().equals(itemStack)) {
                                    player.setItemOnCursor(null);
                                }
                            }
                        }.runTask(Banit.plugin);
                    } else if (mode.equals("c")) {
                        evt.setCancelled(true);
                        player.sendMessage(languageManager.getMessageBracket("message.item-no-craft", "reason", reason));

                        new BukkitRunnable() {
                            @Override
                            public void run() {
                                if (player.getItemOnCursor().equals(itemStack)) {
                                    player.setItemOnCursor(null);
                                }
                            }
                        }.runTask(Banit.plugin);
                    }
                    break;
                }
            }
        }
    }

    /**
     * 生物生成事件处理
     * 检查生成的实体是否被禁止
     *
     * @param evt 生物生成事件
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onCreatureSpawn(CreatureSpawnEvent evt) {
        LivingEntity livingEntity = evt.getEntity();
        World world = livingEntity.getWorld();
        String[] worlds = new String[]{world.getName(), configManager.getOverAll()};

        for (String worldName : worlds) {
            String serialNumber = dataManager.isBannedEntity(worldName, livingEntity);
            if (serialNumber != null) {
                evt.setCancelled(true);
                break;
            }
        }
    }

    /**
     * 玩家移动事件处理
     * 当玩家进入新区块时，异步检查该区块中的方块是否被禁止
     *
     * @param evt 玩家移动事件
     */
    @EventHandler(ignoreCancelled = true)
    public void onPlayerMove(PlayerMoveEvent evt) {
        if (!configManager.isBlockCheckEnable()) {
            return;
        }

        if (evt.getFrom().getChunk() == evt.getTo().getChunk()) {
            return;
        }

        Player player = evt.getPlayer();

        if (player.hasPermission("bi.op")) {
            return;
        }

        World world = player.getWorld();
        Chunk targetChunk = evt.getTo().getChunk();
        String chunkKey = getChunkKey(world, targetChunk);
        String overallWorld = configManager.getOverAll();

        // 提前检查是否有禁用方块配置（从缓存读取）
        if (dataManager.getBannedBlocks(overallWorld).isEmpty() &&
                dataManager.getBannedBlocks(world.getName()).isEmpty()) {
            return;
        }

        // 检查是否为"永久只检查一次"模式
        if (configManager.isBlockCheckCheckOncePerChunk()) {
            if (checkedChunksOnce.containsKey(chunkKey)) {
                return;
            }
        }

        // 检查区块冷却时间
        long now = System.currentTimeMillis();
        Long lastCheck = chunkCheckCooldown.get(chunkKey);
        long cooldownTime = configManager.getBlockCheckChunkCooldown();

        if (lastCheck != null && (now - lastCheck) < cooldownTime) {
            return;
        }

        // 优化：概率检查
        if (configManager.isOptimization() && !configManager.isBlockCheckAlwaysCheck()) {
            int checkChance = configManager.getBlockCheckCheckChance();
            if (!IRandom.percentageChance(checkChance)) {
                return;
            }
        }

        if (!targetChunk.isLoaded()) {
            return;
        }

        // 更新冷却时间
        chunkCheckCooldown.put(chunkKey, now);

        if (configManager.isBlockCheckCheckOncePerChunk()) {
            checkedChunksOnce.put(chunkKey, true);
        }

        // 异步检查区块方块
        new BukkitRunnable() {
            @Override
            public void run() {
                checkChunkBlocksSafely(world, targetChunk, overallWorld, chunkKey);
            }
        }.runTaskAsynchronously(Banit.plugin);
    }

    /**
     * 安全的异步方块检查
     * 遍历区块内所有方块，检查是否在禁止列表中
     *
     * @param world        世界
     * @param chunk        区块
     * @param overallWorld 全局世界名称
     * @param chunkKey     区块唯一标识
     */
    private void checkChunkBlocksSafely(World world, Chunk chunk, String overallWorld, String chunkKey) {
        if (!chunk.isLoaded()) {
            return;
        }

        try {
            int minHeight = configManager.getBlockCheckWorldMinHeight();
            int maxHeight = configManager.getBlockCheckWorldMaxHeight();

            int checkedCount = 0;
            int foundCount = 0;

            for (int x = 0; x < 16; x++) {
                for (int z = 0; z < 16; z++) {
                    for (int y = minHeight; y < maxHeight; y++) {
                        checkedCount++;

                        try {
                            Block block = chunk.getBlock(x, y, z);

                            if (IBlock.isEmpty(block)) {
                                continue;
                            }

                            String[] worlds = new String[]{world.getName(), overallWorld};

                            for (String worldName : worlds) {
                                String serialNumber = dataManager.isBannedBlock(worldName, block);

                                if (serialNumber != null) {
                                    Map<String, Object> blockInfo = dataManager.getBannedInfo(worldName, serialNumber);

                                    if (blockInfo != null) {
                                        boolean clear = (boolean) blockInfo.get("Clear");

                                        if (clear) {
                                            foundCount++;
                                            final Block finalBlock = block;

                                            new BukkitRunnable() {
                                                @Override
                                                public void run() {
                                                    try {
                                                        if (finalBlock.getChunk().isLoaded() && !IBlock.isEmpty(finalBlock)) {
                                                            finalBlock.setType(Material.AIR);
                                                        }
                                                    } catch (Exception e) {
                                                        // 静默处理
                                                    }
                                                }
                                            }.runTask(Banit.plugin);
                                        }
                                    }
                                    break;
                                }
                            }
                        } catch (Exception e) {
                            continue;
                        }
                    }
                }
            }

            if (configManager.isBlockCheckDebug() && foundCount > 0) {
                Banit.plugin.getLogger().info(String.format(
                        "[BanIt] Chunk %s check complete: checked %d blocks, cleared %d banned blocks",
                        chunkKey, checkedCount, foundCount
                ));
            }

        } catch (Exception e) {
            if (configManager.isBlockCheckDebug()) {
                Banit.plugin.getLogger().warning("[BanIt] Error checking chunk: " + e.getMessage());
            }
        }
    }

    /**
     * 处理无法使用物品
     * 根据配置决定是丢弃还是切换快捷栏
     *
     * @param player    玩家
     * @param itemStack 物品
     */
    private void handleCantUse(Player player, ItemStack itemStack) {
        if (configManager.isDropItem()) {
            BanInfo.cantUse(player, itemStack);
        } else {
            int slot = IRandom.randomNumber(0, 8);
            player.getInventory().setHeldItemSlot(slot == player.getInventory().getHeldItemSlot() ? 0 : slot);
        }
    }
}
