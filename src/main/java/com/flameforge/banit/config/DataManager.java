package com.flameforge.banit.config;

import com.flameforge.banit.Banit;
import com.flameforge.banit.storage.DataStorage;
import com.flameforge.banit.utils.ArmorBanHelper;
import com.flameforge.banit.utils.NBTPathUtil;
import de.tr7zw.nbtapi.NBTCompound;
import de.tr7zw.nbtapi.NBTContainer;
import de.tr7zw.nbtapi.NBTItem;
import de.tr7zw.nbtapi.NBTTileEntity;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Ageable;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import pers.tany.yukinoaapi.interfacepart.item.IItem;
import pers.tany.yukinoaapi.interfacepart.serializer.ISerializer;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 数据管理器（缓存层）
 * 在 DataStorage 之上提供统一的内存缓存，所有匹配逻辑在缓存内完成，不穿透到底层存储
 *
 * <p>性能说明：
 * <ul>
 *   <li>所有 isBanned* 检查方法完全基于内存缓存，零数据库/文件IO</li>
 *   <li>add/remove 操作同时更新底层存储和缓存</li>
 *   <li>reload() 方法从底层存储重新加载全部数据到缓存</li>
 * </ul>
 *
 * <p>isBannedItemStack 性能优化：
 * <ul>
 *   <li>预先缓存 itemType 和 itemDurability，避免循环内重复调用</li>
 *   <li>fuzzyNBT 的 NBTItem.toString() 延迟初始化且只创建一次，多个 fuzzyNBT 条目复用同一字符串</li>
 * </ul>
 *
 * <p>v3.0.0 改动：
 * <ul>
 *   <li>addBannedItem 方法中，存储前强制将物品堆叠数设为 1，确保序列化数据统一</li>
 * </ul>
 *
 * <p>v3.0.1 改动：
 * <ul>
 *   <li>新增 purgeInvalidEntries()：缓存加载后自动检测并移除失效的模组条目（物品/方块/实体/附魔）</li>
 *   <li>模组被移除后，对应的禁用条目会在插件启动或 reload 时自动清理，并在控制台输出清理日志</li>
 * </ul>
 *
 * <p>装备槽禁用改动：
 * <ul>
 *   <li>新增 equipBanFlag 缓存与 hasEquipBan()，供 ArmorListener/CheckArmorTask 做零成本快速判断</li>
 * </ul>
 *
 * <p>物品禁用索引：
 * <ul>
 *   <li>isBannedItemStack 不再逐条扫描全部条目：按世界建索引，普通条目按材质分桶，
 *       NBT 键值 / 模糊 NBT 条目单独一组，检查时只看同材质的桶和 NBT 组</li>
 *   <li>bi.ignore.&lt;序列号&gt; 权限改为条目命中之后才查，仍然返回「第一条命中且玩家没有豁免权限」的条目</li>
 *   <li>一次检查内 NBTItem 至多构造一次，物品没有 NBT 时 NBT 键值条目直接判为不命中</li>
 *   <li>任何禁用数据变动后索引作废、下次检查时重建；读条目出异常时回到逐条扫描</li>
 * </ul>
 *
 * @author Tany天炎
 */
public class DataManager {

    /** 插件实例 */
    private final Banit plugin;

    /** 底层存储实现 */
    private final DataStorage storage;

    // ==================== 缓存结构 ====================

    /** 世界列表缓存 */
    private final Set<String> worldsCache = ConcurrentHashMap.newKeySet();

    /** 物品禁用缓存：世界 -> 序列号列表 */
    private final Map<String, Set<String>> bannedItemsCache = new ConcurrentHashMap<>();

    /** 方块禁用缓存：世界 -> 序列号列表 */
    private final Map<String, Set<String>> bannedBlocksCache = new ConcurrentHashMap<>();

    /** 实体禁用缓存：世界 -> 序列号列表 */
    private final Map<String, Set<String>> bannedEntitiesCache = new ConcurrentHashMap<>();

    /** 附魔禁用缓存：世界 -> 序列号列表 */
    private final Map<String, Set<String>> bannedEnchantmentsCache = new ConcurrentHashMap<>();

    /** 完整禁用信息缓存：世界 -> 序列号 -> 详细信息 */
    private final Map<String, Map<String, Map<String, Object>>> allBannedInfoCache = new ConcurrentHashMap<>();

    /** 世界展示物品缓存：世界 -> 物品 */
    private final Map<String, ItemStack> worldDisplayItemCache = new ConcurrentHashMap<>();

    /**
     * 序列化物品字符串 → 反序列化结果的缓存。
     *
     * <p>{@link #isBannedItemStack} 里 {@code distinguish == 2} 的条目每次检查都要把存的 {@code "ItemStack"}
     * 字符串反序列化成 {@link ItemStack} 再比对，走的是 ProtocolLib 的反射；这条字符串运行期不变，不缓存就全是重复计算。</p>
     *
     * <p>直接用序列化字符串作 key：条目改了字符串跟着变，旧值自然取不到，不需要失效逻辑；
     * 被删条目的字符串会留在缓存里，见 {@link #DESERIALIZE_CACHE_LIMIT}。</p>
     *
     * <p>返回的 ItemStack 是共享实例，目前只用于 {@code storedItem.isSimilar(itemStack)}；要改它先 {@code clone()}。</p>
     */
    private final Map<String, ItemStack> deserializeCache = new ConcurrentHashMap<>();

    /**
     * {@link #deserializeCache} 的条目上限，超出即整体清空。
     *
     * <p>正常情况下条目数等于「所有世界里 distinguish==2 的封禁条目数」，量级在几十到几百。
     * 设一个远高于此的上限只是防御性兜底：万一有异常路径导致条目只增不减，
     * 清空的代价也仅仅是下一轮重新反序列化一次。</p>
     */
    private static final int DESERIALIZE_CACHE_LIMIT = 4096;

    /**
     * 装备禁用标志缓存（volatile 保证多线程可见性）。
     * 当缓存中存在任意“装备类”模式（helmet/chest/legs/boots/armor/equip/o）的物品禁用时为 true。
     * 用于 ArmorListener / CheckArmorTask 在事件与定时任务中做零成本快速判断，
     * 没有装备类禁用时完全不执行装备槽检查逻辑。
     */
    private volatile boolean equipBanFlag = false;

    // ==================== 物品禁用索引 ====================

    /**
     * isBannedItemStack 是否走索引。改成 false 就回到逐条扫描全部条目的写法，两条路径的匹配结果相同。
     */
    private static final boolean ITEM_INDEX_ENABLED = true;

    /** 匹配方式：distinguish==0，材质相同且耐久相同 */
    private static final int MATCH_TYPE_AND_DATA = 0;

    /** 匹配方式：distinguish==1，材质相同即可 */
    private static final int MATCH_TYPE_ONLY = 1;

    /** 匹配方式：distinguish==2，与条目里存的物品 isSimilar */
    private static final int MATCH_SIMILAR = 2;

    /** 匹配方式：NBT 键值（Key + Value） */
    private static final int MATCH_NBT_KEY_VALUE = 3;

    /** 匹配方式：模糊 NBT（FuzzyNBT） */
    private static final int MATCH_NBT_FUZZY = 4;

    private static final ItemBanEntry[] NO_ENTRIES = new ItemBanEntry[0];

    /**
     * 索引代数。禁用数据每变动一次（增删条目、清空世界、重载）就在变动结束时加一，
     * 代数对不上的索引视为作废，下次检查时按当前缓存重建。
     *
     * <p>先读代数、再读缓存建索引：建索引期间如果有变动，变动结束时代数会再加一，
     * 这份索引存进去也会在下一次检查时被判作废，不会留下过期的索引。</p>
     */
    private final AtomicLong itemIndexGeneration = new AtomicLong();

    /** 物品禁用索引缓存：世界 -> 索引（首次检查该世界时构建） */
    private final Map<String, ItemBanIndex> itemIndexCache = new ConcurrentHashMap<>();

    /**
     * 构造函数
     *
     * @param plugin  插件实例
     * @param storage 底层存储实现
     */
    public DataManager(Banit plugin, DataStorage storage) {
        this.plugin = plugin;
        this.storage = storage;
    }

    /**
     * 初始化数据管理器
     * 加载所有数据到缓存
     */
    public void initialize() {
        storage.initialize();
        loadDataToCache();
        plugin.getLogger().info("§a数据缓存加载完成");
    }

    /**
     * 关闭数据管理器
     */
    public void shutdown() {
        clearAllCache();
        storage.shutdown();
    }

    /**
     * 从底层存储加载所有数据到缓存
     */
    private void loadDataToCache() {
        try {
            clearAllCache();

            List<String> worlds = storage.getBannedWorlds();
            worldsCache.addAll(worlds);

            for (String world : worlds) {
                // 加载物品列表
                List<String> items = storage.getBannedItems(world);
                bannedItemsCache.put(world, ConcurrentHashMap.newKeySet());
                bannedItemsCache.get(world).addAll(items);

                // 加载方块列表
                List<String> blocks = storage.getBannedBlocks(world);
                bannedBlocksCache.put(world, ConcurrentHashMap.newKeySet());
                bannedBlocksCache.get(world).addAll(blocks);

                // 加载实体列表
                List<String> entities = storage.getBannedEntities(world);
                bannedEntitiesCache.put(world, ConcurrentHashMap.newKeySet());
                bannedEntitiesCache.get(world).addAll(entities);

                // 加载附魔列表
                List<String> enchantments = storage.getBannedEnchantments(world);
                bannedEnchantmentsCache.put(world, ConcurrentHashMap.newKeySet());
                bannedEnchantmentsCache.get(world).addAll(enchantments);

                // 加载完整信息（包含所有字段：ItemStack、TileID、SubTileName、Species、Name 等）
                Map<String, Map<String, Object>> allInfo = storage.getAllBannedInfo(world);
                allBannedInfoCache.put(world, new ConcurrentHashMap<>(allInfo));

                // 加载展示物品
                ItemStack displayItem = storage.getWorldDisplayItem(world);
                if (displayItem != null) {
                    worldDisplayItemCache.put(world, displayItem);
                }
            }

            // 清理失效的模组条目（模组被移除后对应的物品/方块/实体/附魔不再有效）
            purgeInvalidEntries();

            // 缓存加载完成后重算装备禁用标志
            recomputeEquipBanFlag();
        } finally {
            invalidateItemIndex();
        }
    }

    /**
     * 清理失效的禁用条目
     * 当模组被移除后，对应的物品 ID、方块 ID、实体类型、附魔名称会失效
     * 此方法在缓存加载后自动检测并移除这些无效条目，同时从底层存储中删除
     *
     * <p>检测规则：
     * <ul>
     *   <li>Item：如果条目不是 key/value 或 fuzzyNBT 匹配模式，则检查 Material.matchMaterial(id)；
     *       如果是 distinguish==2（精确NBT匹配），还会尝试反序列化 ItemStack</li>
     *   <li>Block：检查 Material.matchMaterial(id) 是否为 null</li>
     *   <li>Entity：检查 EntityType.valueOf(entityType) 是否有效</li>
     *   <li>Ench：检查 Enchantment.getByName(enchName) 是否为 null</li>
     * </ul>
     */
    private void purgeInvalidEntries() {
        int removedCount = 0;

        // 遍历所有世界的副本，避免 ConcurrentModification
        for (String world : new ArrayList<>(worldsCache)) {
            Map<String, Map<String, Object>> worldInfo = allBannedInfoCache.get(world);
            if (worldInfo == null) {
                continue;
            }

            // 收集需要移除的条目（先收集再批量移除，避免遍历中修改）
            List<String> toRemoveItems = new ArrayList<>();
            List<String> toRemoveBlocks = new ArrayList<>();
            List<String> toRemoveEntities = new ArrayList<>();
            List<String> toRemoveEnchs = new ArrayList<>();

            for (Map.Entry<String, Map<String, Object>> entry : worldInfo.entrySet()) {
                String serialNumber = entry.getKey();
                Map<String, Object> info = entry.getValue();
                String type = (String) info.get("Type");

                if (type == null) {
                    continue;
                }

                switch (type) {
                    case "Item":
                        if (isItemEntryInvalid(info)) {
                            toRemoveItems.add(serialNumber);
                            plugin.getLogger().warning(Banit.getLanguageManager().getMessage("purge.invalid-item")
                                    .replace("{serial}", serialNumber)
                                    .replace("{id}", String.valueOf(info.get("ID")))
                                    .replace("{world}", world));
                        }
                        break;
                    case "Block":
                        if (isBlockEntryInvalid(info)) {
                            toRemoveBlocks.add(serialNumber);
                            plugin.getLogger().warning(Banit.getLanguageManager().getMessage("purge.invalid-block")
                                    .replace("{serial}", serialNumber)
                                    .replace("{id}", String.valueOf(info.get("ID")))
                                    .replace("{world}", world));
                        }
                        break;
                    case "Entity":
                        if (isEntityEntryInvalid(info)) {
                            toRemoveEntities.add(serialNumber);
                            plugin.getLogger().warning(Banit.getLanguageManager().getMessage("purge.invalid-entity")
                                    .replace("{serial}", serialNumber)
                                    .replace("{entityType}", String.valueOf(info.get("EntityType")))
                                    .replace("{world}", world));
                        }
                        break;
                    case "Ench":
                        if (isEnchEntryInvalid(info)) {
                            toRemoveEnchs.add(serialNumber);
                            plugin.getLogger().warning(Banit.getLanguageManager().getMessage("purge.invalid-ench")
                                    .replace("{serial}", serialNumber)
                                    .replace("{enchName}", String.valueOf(info.get("EnchName")))
                                    .replace("{world}", world));
                        }
                        break;
                }
            }

            // 批量移除（同时删除底层存储和缓存）
            for (String sn : toRemoveItems) {
                removeBannedItem(world, sn);
                removedCount++;
            }
            for (String sn : toRemoveBlocks) {
                removeBannedBlock(world, sn);
                removedCount++;
            }
            for (String sn : toRemoveEntities) {
                removeBannedEntity(world, sn);
                removedCount++;
            }
            for (String sn : toRemoveEnchs) {
                removeBannedEnchantment(world, sn);
                removedCount++;
            }
        }

        if (removedCount > 0) {
            plugin.getLogger().info(Banit.getLanguageManager().getMessage("purge.summary")
                    .replace("{count}", String.valueOf(removedCount)));
        }
    }

    /**
     * 检查物品条目是否失效
     *
     * @param info 物品信息
     * @return 如果失效返回 true
     */
    private boolean isItemEntryInvalid(Map<String, Object> info) {
        String key = (String) info.get("Key");
        String value = (String) info.get("Value");
        String fuzzyNBT = (String) info.get("FuzzyNBT");

        // key/value 和 fuzzyNBT 匹配模式依赖 NBT 而非 Material，
        // 但如果模组都删了，这些物品也不会出现在玩家手中，清理同样合理
        // 通过检查 ID 对应的 Material 是否存在来判断
        String id = (String) info.get("ID");
        if (id == null) {
            return true;
        }

        try {
            Material material = Material.matchMaterial(id);
            if (material == null || material == Material.AIR) {
                return true;
            }
        } catch (Exception e) {
            return true;
        }

        // 对于 distinguish==2（精确NBT匹配），额外检查反序列化是否成功
        int distinguish = getIntSafe(info, "Distinguish", 0);
        if (distinguish == 2) {
            String itemData = (String) info.get("ItemStack");
            if (itemData != null) {
                try {
                    ItemStack deserialized = ISerializer.deserializeItemStack(itemData);
                    if (deserialized == null || deserialized.getType() == Material.AIR) {
                        return true;
                    }
                } catch (Exception e) {
                    return true;
                }
            }
        }

        return false;
    }

    /**
     * 检查方块条目是否失效
     *
     * @param info 方块信息
     * @return 如果失效返回 true
     */
    private boolean isBlockEntryInvalid(Map<String, Object> info) {
        // 如果有 TileID，说明是通过 TileEntity 匹配的模组方块
        // TileID 对应的模组不存在时，Material 也会失效，统一用 Material 检查即可
        String id = (String) info.get("ID");
        if (id == null) {
            return true;
        }

        try {
            Material material = Material.matchMaterial(id);
            return material == null || material == Material.AIR;
        } catch (Exception e) {
            return true;
        }
    }

    /**
     * 检查实体条目是否失效
     *
     * @param info 实体信息
     * @return 如果失效返回 true
     */
    private boolean isEntityEntryInvalid(Map<String, Object> info) {
        String entityType = (String) info.get("EntityType");
        if (entityType == null) {
            return true;
        }

        try {
            org.bukkit.entity.EntityType.valueOf(entityType);
            return false;
        } catch (IllegalArgumentException e) {
            return true;
        }
    }

    /**
     * 检查附魔条目是否失效
     *
     * @param info 附魔信息
     * @return 如果失效返回 true
     */
    private boolean isEnchEntryInvalid(Map<String, Object> info) {
        String enchName = (String) info.get("EnchName");
        if (enchName == null) {
            return true;
        }

        try {
            Enchantment enchantment = Enchantment.getByName(enchName);
            return enchantment == null;
        } catch (Exception e) {
            return true;
        }
    }

    /**
     * 清空所有缓存
     */
    private void clearAllCache() {
        worldsCache.clear();
        bannedItemsCache.clear();
        bannedBlocksCache.clear();
        bannedEntitiesCache.clear();
        bannedEnchantmentsCache.clear();
        allBannedInfoCache.clear();
        worldDisplayItemCache.clear();
        deserializeCache.clear();
        // 清空缓存后装备禁用标志一并复位
        equipBanFlag = false;
        invalidateItemIndex();
    }

    /**
     * 让所有世界的物品禁用索引作废。每个改动禁用数据的方法都在 finally 里调用，
     * 保证变动（哪怕中途抛异常）结束之后不会再用到变动前建的索引。
     */
    private void invalidateItemIndex() {
        itemIndexGeneration.incrementAndGet();
        itemIndexCache.clear();
    }

    /**
     * 重新加载数据
     */
    public void reload() {
        loadDataToCache();
    }

    // ==================== 物品相关 ====================

    /**
     * 添加被禁止的物品
     * 存储时会自动将物品堆叠数设为 1，确保序列化数据统一
     *
     * @param world        世界名称
     * @param serialNumber 序列号
     * @param item         物品（原始物品不会被修改，内部会克隆）
     * @param id           物品ID
     * @param durability   耐久度
     * @param distinguish  区分方式（0=精确匹配, 1=仅类型, 2=完全NBT匹配）
     * @param clear        是否清除
     * @param mode         模式
     * @param reason       原因
     * @param key          NBT键
     * @param value        NBT值
     * @param fuzzyNBT     模糊NBT
     */
    public void addBannedItem(String world, String serialNumber, ItemStack item, String id,
                              short durability, int distinguish, boolean clear, String mode,
                              String reason, String key, String value, String fuzzyNBT) {
        try {
            // 克隆物品并强制设置堆叠数为 1，确保存储数据统一
            // 不修改原始物品，避免影响调用者手中的物品
            ItemStack storedItem = item.clone();
            storedItem.setAmount(1);

            // 先写入底层存储（使用堆叠数为 1 的克隆物品）
            storage.addBannedItem(world, serialNumber, storedItem, id, durability, distinguish, clear, mode, reason, key, value, fuzzyNBT);

            // 更新缓存
            worldsCache.add(world);
            bannedItemsCache.computeIfAbsent(world, k -> ConcurrentHashMap.newKeySet()).add(serialNumber);

            // 构建完整的信息缓存（与 getAllBannedInfo 返回的结构一致）
            Map<String, Object> info = new HashMap<>();
            info.put("Type", "Item");
            info.put("ID", id);
            info.put("Data", (int) durability);
            info.put("Distinguish", distinguish);
            info.put("Clear", clear);
            info.put("Mode", mode);
            info.put("Reason", reason);
            // 序列化堆叠数为 1 的物品数据（用于 distinguish==2 的完全匹配和 GUI 展示）
            info.put("ItemStack", ISerializer.serializerItemStack(storedItem, true));
            if (key != null) {
                info.put("Key", key);
            }
            if (value != null) {
                info.put("Value", value);
            }
            if (fuzzyNBT != null) {
                info.put("FuzzyNBT", fuzzyNBT);
            }

            allBannedInfoCache.computeIfAbsent(world, k -> new ConcurrentHashMap<>()).put(serialNumber, info);

            // 若为装备类模式，置位装备禁用标志（供 ArmorListener/CheckArmorTask 快速判断）
            if (ArmorBanHelper.isEquipMode(mode)) {
                this.equipBanFlag = true;
            }
        } finally {
            invalidateItemIndex();
        }
    }

    /**
     * 移除被禁止的物品
     *
     * @param world        世界名称
     * @param serialNumber 序列号
     */
    public void removeBannedItem(String world, String serialNumber) {
        try {
            storage.removeBannedItem(world, serialNumber);

            // 更新缓存
            Set<String> items = bannedItemsCache.get(world);
            if (items != null) {
                items.remove(serialNumber);
                if (items.isEmpty()) {
                    bannedItemsCache.remove(world);
                    checkAndRemoveWorld(world);
                }
            }

            // 更新详细信息缓存
            Map<String, Map<String, Object>> worldInfo = allBannedInfoCache.get(world);
            if (worldInfo != null) {
                worldInfo.remove(serialNumber);
            }

            // 移除后重算装备禁用标志（可能移除的是最后一个装备类禁用）
            recomputeEquipBanFlag();
        } finally {
            invalidateItemIndex();
        }
    }

    /**
     * 取序列化字符串对应的 ItemStack，命中缓存则直接复用（v2 新增）。
     *
     * <p>反序列化失败时返回 null，且<b>不写入缓存</b>——失败通常意味着数据损坏或
     * ProtocolLib 版本不匹配，缓存一个 null 会让问题更难排查，且下次仍需重试。</p>
     *
     * @param storedItemData 封禁条目里存的序列化物品字符串
     * @return 反序列化后的 ItemStack（共享实例，调用方只读）；失败时返回 null
     */
    private ItemStack getCachedDeserializedItem(String storedItemData) {
        ItemStack cached = deserializeCache.get(storedItemData);
        if (cached != null) {
            return cached;
        }

        ItemStack deserialized = ISerializer.deserializeItemStack(storedItemData);
        if (deserialized == null) {
            return null;
        }

        if (deserializeCache.size() >= DESERIALIZE_CACHE_LIMIT) {
            deserializeCache.clear();
        }
        deserializeCache.put(storedItemData, deserialized);
        return deserialized;
    }

    /**
     * 检查物品是否被禁止（完全基于缓存，不穿透到底层存储）
     *
     * <p>返回值的含义：按该世界序列号集合的遍历顺序，第一条「匹配这个物品、且玩家没有
     * bi.ignore.&lt;序列号&gt; 权限」的条目。默认走索引（见 {@link #matchIndexed}），
     * 结果与逐条扫描（{@link #matchLinear}）相同。</p>
     *
     * @param player           玩家
     * @param world            世界名称
     * @param itemStack        物品
     * @param ignorePermission 是否忽略权限检查
     * @return 如果物品被禁止返回序列号，否则返回 null
     */
    public String isBannedItemStack(Player player, String world, ItemStack itemStack, boolean ignorePermission) {
        if (IItem.isEmpty(itemStack)) {
            return null;
        }

        Set<String> items = bannedItemsCache.get(world);
        if (items == null || items.isEmpty()) {
            return null;
        }

        Map<String, Map<String, Object>> worldInfo = allBannedInfoCache.get(world);
        if (worldInfo == null) {
            return null;
        }

        // 优化：预先缓存物品的类型和耐久，避免循环内重复调用
        Material itemMaterial = itemStack.getType();
        String itemType = itemMaterial.toString();
        short itemDurability = itemStack.getDurability();

        // v2：bi.op 与循环内的条目无关，属于循环不变式，提到循环外只判一次。
        // 原实现每条封禁条目都要判一次，条目多时是纯粹的重复权限查询。
        // 持有 bi.op 时所有条目都会被跳过，等价于直接返回 null。
        boolean checkPermission = !ignorePermission && player != null;
        if (checkPermission && player.hasPermission("bi.op")) {
            return null;
        }

        // 旧版材质（LEGACY_*）的物品，isSimilar 会先把材质换算成新版再比较，
        // 「条目材质 == 物品材质」不再成立，按材质分桶会漏，这种物品走逐条扫描
        if (ITEM_INDEX_ENABLED && !itemMaterial.isLegacy()) {
            ItemBanIndex index = getItemBanIndex(world);
            if (index.usable) {
                return matchIndexed(index, player, checkPermission, itemStack, itemType, itemDurability);
            }
        }
        return matchLinear(player, checkPermission, items, worldInfo, itemStack, itemType, itemDurability);
    }

    /**
     * 逐条扫描全部物品条目（索引关闭、旧版材质物品、或建索引时读条目出异常时使用）。
     * 这是索引之前的原始写法，索引路径的结果以它为准。
     */
    private String matchLinear(Player player, boolean checkPermission, Set<String> items,
                               Map<String, Map<String, Object>> worldInfo, ItemStack itemStack,
                               String itemType, short itemDurability) {
        // 优化：延迟初始化，fuzzyNBT 的 NBTItem 只在需要时创建一次，多个条目复用
        String nbtString = null;

        for (String serialNumber : items) {
            Map<String, Object> info = worldInfo.get(serialNumber);
            if (info == null || !"Item".equals(info.get("Type"))) {
                continue;
            }

            // v2：bi.op 已在循环外判过，这里只判条目级的 bi.ignore.<序列号>
            if (checkPermission && player.hasPermission("bi.ignore." + serialNumber)) {
                continue;
            }

            String id = (String) info.get("ID");
            int data = getIntSafe(info, "Data", 0);
            int distinguish = getIntSafe(info, "Distinguish", 0);
            String key = (String) info.get("Key");
            String value = (String) info.get("Value");
            String fuzzyNBT = (String) info.get("FuzzyNBT");

            // NBT 键值匹配
            if (key != null && value != null) {
                try {
                    Object nbtValue = NBTPathUtil.getValueAtPath(itemStack, key);
                    if (nbtValue != null && nbtValue.toString().equals(value)) {
                        return serialNumber;
                    }
                } catch (Exception ignored) {
                }
                continue;
            }

            // 模糊 NBT 匹配
            if (fuzzyNBT != null) {
                try {
                    if (nbtString == null) {
                        NBTItem nbtItem = new NBTItem(itemStack);
                        nbtString = nbtItem.toString();
                    }
                    if (nbtString.contains(fuzzyNBT)) {
                        return serialNumber;
                    }
                } catch (Exception ignored) {
                }
                continue;
            }

            // 基于 distinguish 的物品匹配
            if (distinguish == 0) {
                if (itemType.equals(id) && itemDurability == (short) data) {
                    return serialNumber;
                }
            } else if (distinguish == 1) {
                if (itemType.equals(id)) {
                    return serialNumber;
                }
            } else if (distinguish == 2) {
                String storedItemData = (String) info.get("ItemStack");
                if (storedItemData != null) {
                    // v2：走缓存，避免每次都做一遍 ProtocolLib 反射反序列化（见 deserializeCache 字段说明）
                    ItemStack storedItem = getCachedDeserializedItem(storedItemData);
                    if (storedItem != null && storedItem.isSimilar(itemStack)) {
                        return serialNumber;
                    }
                }
            }
        }

        return null;
    }

    /**
     * 按索引检查物品。
     *
     * <p>候选只有三组：物品材质对应的桶、NBT 条目组、没能预先反序列化的 distinguish==2 条目组。
     * 不在这三组里的条目，按 {@link #matchLinear} 的规则本来就不可能命中这个物品。
     * 三组各自按遍历位置升序，这里按位置归并，检查顺序与逐条扫描一致。</p>
     *
     * <p>bi.ignore 权限挪到命中之后再查：逐条扫描是「先跳过有豁免权限的条目、再匹配」，
     * 返回的是第一条「没有豁免且命中」的条目；先匹配后查权限、豁免就接着往后找，
     * 返回的还是同一条。匹配本身只读，不会因为多匹配了几条豁免条目而改变结果。</p>
     */
    private String matchIndexed(ItemBanIndex index, Player player, boolean checkPermission,
                                ItemStack itemStack, String itemType, short itemDurability) {
        ItemBanEntry[] typed = index.byType.get(itemType);
        if (typed == null) {
            typed = NO_ENTRIES;
        }
        ItemBanEntry[] nbt = index.nbtEntries;
        ItemBanEntry[] unresolved = index.unresolvedEntries;
        int typedPos = 0;
        int nbtPos = 0;
        int unresolvedPos = 0;
        ItemNbtView nbtView = null;

        while (true) {
            ItemBanEntry entry = null;
            int source = -1;
            if (typedPos < typed.length) {
                entry = typed[typedPos];
                source = 0;
            }
            if (nbtPos < nbt.length && (entry == null || nbt[nbtPos].order < entry.order)) {
                entry = nbt[nbtPos];
                source = 1;
            }
            if (unresolvedPos < unresolved.length && (entry == null || unresolved[unresolvedPos].order < entry.order)) {
                entry = unresolved[unresolvedPos];
                source = 2;
            }
            if (entry == null) {
                return null;
            }
            if (source == 0) {
                typedPos++;
            } else if (source == 1) {
                nbtPos++;
            } else {
                unresolvedPos++;
            }

            if (nbtView == null && entry.matchType >= MATCH_NBT_KEY_VALUE) {
                nbtView = new ItemNbtView(itemStack);
            }

            boolean matched;
            try {
                matched = matchesEntry(entry, itemStack, itemDurability, nbtView);
            } catch (RuntimeException | Error e) {
                // 逐条扫描时有豁免权限的条目根本不会走到匹配，这里的异常也就不该抛出去
                if (checkPermission && player.hasPermission(entry.ignorePermission)) {
                    continue;
                }
                throw e;
            }
            if (!matched) {
                continue;
            }
            if (checkPermission && player.hasPermission(entry.ignorePermission)) {
                continue;
            }
            return entry.serialNumber;
        }
    }

    /**
     * 判断一条索引条目是否命中物品，规则与 {@link #matchLinear} 里对应分支相同。
     * 进了材质桶的条目，材质相同已经由分桶保证。
     */
    private boolean matchesEntry(ItemBanEntry entry, ItemStack itemStack, short itemDurability, ItemNbtView nbtView) {
        switch (entry.matchType) {
            case MATCH_TYPE_AND_DATA:
                return itemDurability == entry.data;
            case MATCH_TYPE_ONLY:
                return true;
            case MATCH_SIMILAR: {
                ItemStack storedItem = entry.storedItem != null
                        ? entry.storedItem
                        : getCachedDeserializedItem(entry.storedItemData);
                return storedItem != null && storedItem.isSimilar(itemStack);
            }
            case MATCH_NBT_KEY_VALUE:
                try {
                    Object nbtValue = nbtView.valueAt(entry.key);
                    return nbtValue != null && nbtValue.toString().equals(entry.value);
                } catch (Exception ignored) {
                    return false;
                }
            case MATCH_NBT_FUZZY:
                try {
                    String nbtString = nbtView.text();
                    return nbtString != null && nbtString.contains(entry.fuzzyNBT);
                } catch (Exception ignored) {
                    return false;
                }
            default:
                return false;
        }
    }

    /**
     * 取某个世界当前代数的物品禁用索引，没有或已作废就按当前缓存重建。
     */
    private ItemBanIndex getItemBanIndex(String world) {
        long generation = itemIndexGeneration.get();
        ItemBanIndex index = itemIndexCache.get(world);
        if (index != null && index.generation == generation) {
            return index;
        }
        index = buildItemBanIndex(world, generation);
        itemIndexCache.put(world, index);
        return index;
    }

    /**
     * 按当前缓存给一个世界建物品禁用索引。
     *
     * <p>条目字段的读法（包括强转）与 {@link #matchLinear} 一致；读的过程中出了异常，
     * 返回一份标记为不可用的索引，这一代改走逐条扫描，异常照旧在那边抛出。</p>
     */
    private ItemBanIndex buildItemBanIndex(String world, long generation) {
        try {
            Set<String> items = bannedItemsCache.get(world);
            Map<String, Map<String, Object>> worldInfo = allBannedInfoCache.get(world);
            Map<String, List<ItemBanEntry>> byType = new HashMap<>();
            List<ItemBanEntry> nbtEntries = new ArrayList<>();
            List<ItemBanEntry> unresolvedEntries = new ArrayList<>();

            if (items != null && worldInfo != null) {
                int order = 0;
                for (String serialNumber : items) {
                    int position = order++;
                    Map<String, Object> info = worldInfo.get(serialNumber);
                    if (info == null || !"Item".equals(info.get("Type"))) {
                        continue;
                    }

                    String id = (String) info.get("ID");
                    int data = getIntSafe(info, "Data", 0);
                    int distinguish = getIntSafe(info, "Distinguish", 0);
                    String key = (String) info.get("Key");
                    String value = (String) info.get("Value");
                    String fuzzyNBT = (String) info.get("FuzzyNBT");

                    if (key != null && value != null) {
                        nbtEntries.add(new ItemBanEntry(position, serialNumber, MATCH_NBT_KEY_VALUE,
                                (short) 0, key, value, null, null, null));
                    } else if (fuzzyNBT != null) {
                        nbtEntries.add(new ItemBanEntry(position, serialNumber, MATCH_NBT_FUZZY,
                                (short) 0, null, null, fuzzyNBT, null, null));
                    } else if (distinguish == 0 || distinguish == 1) {
                        // id 为 null 时 itemType.equals(id) 恒为 false，这条永远不会命中，不进索引
                        if (id != null) {
                            int matchType = distinguish == 0 ? MATCH_TYPE_AND_DATA : MATCH_TYPE_ONLY;
                            byType.computeIfAbsent(id, k -> new ArrayList<>()).add(new ItemBanEntry(position,
                                    serialNumber, matchType, (short) data, null, null, null, null, null));
                        }
                    } else if (distinguish == 2) {
                        String storedItemData = (String) info.get("ItemStack");
                        if (storedItemData == null) {
                            // 没存物品数据，永远不会命中
                            continue;
                        }
                        ItemStack storedItem = null;
                        try {
                            storedItem = getCachedDeserializedItem(storedItemData);
                        } catch (RuntimeException | LinkageError ignored) {
                            // 留到匹配时再反序列化，异常在那边按原逻辑处理（缺 ProtocolLib 之类的 LinkageError 也一样）
                        }
                        if (storedItem != null && !storedItem.getType().isLegacy()) {
                            // isSimilar 要求两边材质相同（新版材质不做换算），按存储物品的材质分桶
                            byType.computeIfAbsent(storedItem.getType().toString(), k -> new ArrayList<>())
                                    .add(new ItemBanEntry(position, serialNumber, MATCH_SIMILAR,
                                            (short) 0, null, null, null, storedItemData, storedItem));
                        } else {
                            unresolvedEntries.add(new ItemBanEntry(position, serialNumber, MATCH_SIMILAR,
                                    (short) 0, null, null, null, storedItemData, null));
                        }
                    }
                    // 其余 distinguish 取值在逐条扫描里也不会命中任何物品
                }
            }

            Map<String, ItemBanEntry[]> byTypeArrays = new HashMap<>(byType.size() * 2);
            for (Map.Entry<String, List<ItemBanEntry>> bucket : byType.entrySet()) {
                byTypeArrays.put(bucket.getKey(), bucket.getValue().toArray(NO_ENTRIES));
            }
            return new ItemBanIndex(generation, true, byTypeArrays,
                    nbtEntries.toArray(NO_ENTRIES), unresolvedEntries.toArray(NO_ENTRIES));
        } catch (RuntimeException e) {
            return new ItemBanIndex(generation, false, Collections.<String, ItemBanEntry[]>emptyMap(),
                    NO_ENTRIES, NO_ENTRIES);
        }
    }

    /**
     * 索引里的一条物品禁用，匹配要用的字段在建索引时就从 info 里取好。
     */
    private static final class ItemBanEntry {
        /** 在该世界序列号集合里的遍历位置，用来保证「先遍历到的条目先命中」 */
        final int order;
        final String serialNumber;
        /** 预先拼好的 bi.ignore.&lt;序列号&gt; 权限节点 */
        final String ignorePermission;
        final int matchType;
        /** distinguish==0 时要比的耐久，已经按原逻辑转成 short */
        final short data;
        final String key;
        final String value;
        final String fuzzyNBT;
        final String storedItemData;
        /** 建索引时反序列化出的存储物品；为 null 时匹配时再走反序列化缓存 */
        final ItemStack storedItem;

        ItemBanEntry(int order, String serialNumber, int matchType, short data, String key, String value,
                     String fuzzyNBT, String storedItemData, ItemStack storedItem) {
            this.order = order;
            this.serialNumber = serialNumber;
            this.ignorePermission = "bi.ignore." + serialNumber;
            this.matchType = matchType;
            this.data = data;
            this.key = key;
            this.value = value;
            this.fuzzyNBT = fuzzyNBT;
            this.storedItemData = storedItemData;
            this.storedItem = storedItem;
        }
    }

    /**
     * 一个世界的物品禁用索引，建好后只读，可以被异步检查线程同时使用。
     */
    private static final class ItemBanIndex {
        final long generation;
        /** 为 false 表示建索引时读条目出了异常，这一代改走逐条扫描 */
        final boolean usable;
        /** 材质名 -> 该材质可能命中的条目（distinguish 0/1/2），按遍历位置升序 */
        final Map<String, ItemBanEntry[]> byType;
        /** NBT 键值、模糊 NBT 条目，按遍历位置升序 */
        final ItemBanEntry[] nbtEntries;
        /** distinguish==2 但建索引时没能反序列化出物品的条目，任何材质都要检查，按遍历位置升序 */
        final ItemBanEntry[] unresolvedEntries;

        ItemBanIndex(long generation, boolean usable, Map<String, ItemBanEntry[]> byType,
                     ItemBanEntry[] nbtEntries, ItemBanEntry[] unresolvedEntries) {
            this.generation = generation;
            this.usable = usable;
            this.byType = byType;
            this.nbtEntries = nbtEntries;
            this.unresolvedEntries = unresolvedEntries;
        }
    }

    /**
     * 一次检查内共享的物品 NBT 读取。
     *
     * <p>原写法每条 NBT 键值条目都 new 一个 NBTItem（每次都要复制物品），条目一多开销就上来了。
     * 这里 NBTItem 至多构造一次；NBT 键值条目读的是同一份标签，物品没有标签时直接判为不命中
     * （没有标签时 hasKey 恒为 false，原写法同样读不到值）；模糊 NBT 用的字符串也只取一次。</p>
     *
     * <p>出错的处理与原写法一致：构造或读取抛出的 Exception 视为不命中；
     * Error 不吞，也不记成失败，下一条用到时会再试一次（原写法每条都会重新构造）。</p>
     */
    private static final class ItemNbtView {
        private final ItemStack itemStack;
        private NBTItem nbtItem;
        private boolean nbtItemFailed;
        /** 物品的标签；为 null 表示没有标签或读取失败 */
        private NBTCompound root;
        private boolean rootResolved;
        private String text;
        private boolean textResolved;

        ItemNbtView(ItemStack itemStack) {
            this.itemStack = itemStack;
        }

        private NBTItem nbtItem() {
            if (nbtItem == null && !nbtItemFailed) {
                try {
                    nbtItem = new NBTItem(itemStack);
                } catch (Exception e) {
                    nbtItemFailed = true;
                }
            }
            return nbtItem;
        }

        /**
         * 读取路径上的基础类型值，规则同 {@link NBTPathUtil#getValueAtPath(ItemStack, String)}。
         */
        Object valueAt(String path) {
            if (!rootResolved) {
                NBTItem item = nbtItem();
                if (item == null) {
                    if (nbtItemFailed) {
                        rootResolved = true;
                    }
                    return null;
                }
                Object tag;
                try {
                    tag = item.getCompound();
                } catch (Exception e) {
                    tag = null;
                }
                NBTCompound compound = null;
                if (tag != null) {
                    try {
                        // 把这一份标签包起来反复读，不用每读一个键都让 NBTItem 再复制一次物品
                        compound = new NBTContainer(tag);
                    } catch (Exception | LinkageError e) {
                        compound = item;
                    }
                }
                root = compound;
                rootResolved = true;
            }
            if (root == null) {
                return null;
            }
            return NBTPathUtil.getValueAtPath(root, path);
        }

        /**
         * 模糊 NBT 用的整份 NBT 字符串，取法同原写法（{@code new NBTItem(item).toString()}）。
         */
        String text() {
            if (!textResolved) {
                NBTItem item = nbtItem();
                if (item == null) {
                    if (nbtItemFailed) {
                        textResolved = true;
                    }
                    return null;
                }
                try {
                    text = item.toString();
                } catch (Exception e) {
                    text = null;
                }
                textResolved = true;
            }
            return text;
        }
    }

    /**
     * 检查物品是否被禁止（默认不忽略权限）
     *
     * @param player    玩家
     * @param world     世界名称
     * @param itemStack 物品
     * @return 如果物品被禁止返回序列号，否则返回 null
     */
    public String isBannedItemStack(Player player, String world, ItemStack itemStack) {
        return isBannedItemStack(player, world, itemStack, false);
    }

    /**
     * 检查被禁止的物品是否需要清除（从缓存读取）
     *
     * @param world        世界名称
     * @param serialNumber 序列号
     * @return 如果需要清除返回 true
     */
    public boolean isBannedItemClear(String world, String serialNumber) {
        Map<String, Map<String, Object>> worldInfo = allBannedInfoCache.get(world);
        if (worldInfo != null) {
            Map<String, Object> info = worldInfo.get(serialNumber);
            if (info != null && info.containsKey("Clear")) {
                Object clearObj = info.get("Clear");
                if (clearObj instanceof Boolean) {
                    return (Boolean) clearObj;
                }
            }
        }
        return false;
    }

    /**
     * 获取被禁止物品的模式（从缓存读取）
     *
     * @param world        世界名称
     * @param serialNumber 序列号
     * @return 物品的禁止模式，默认 "a"
     */
    public String getBannedItemMode(String world, String serialNumber) {
        Map<String, Map<String, Object>> worldInfo = allBannedInfoCache.get(world);
        if (worldInfo != null) {
            Map<String, Object> info = worldInfo.get(serialNumber);
            if (info != null && info.containsKey("Mode")) {
                return (String) info.get("Mode");
            }
        }
        return "a";
    }

    /**
     * 获取被禁止物品的原因（从缓存读取）
     *
     * @param world        世界名称
     * @param serialNumber 序列号
     * @return 物品被禁止的原因
     */
    public String getBannedItemReason(String world, String serialNumber) {
        Map<String, Map<String, Object>> worldInfo = allBannedInfoCache.get(world);
        if (worldInfo != null) {
            Map<String, Object> info = worldInfo.get(serialNumber);
            if (info != null && info.containsKey("Reason")) {
                return (String) info.get("Reason");
            }
        }
        return Banit.getConfigManager().getDefaultReason();
    }

    /**
     * 获取被禁止物品列表（从缓存）
     *
     * @param world 世界名称
     * @return 被禁止的物品序列号列表
     */
    public List<String> getBannedItems(String world) {
        Set<String> items = bannedItemsCache.get(world);
        return items != null ? new ArrayList<>(items) : new ArrayList<>();
    }

    /**
     * 是否存在任意装备类禁用（零成本判断）。
     * 供 ArmorListener / CheckArmorTask 在事件与定时任务中快速跳过无关逻辑。
     *
     * @return 如果存在 helmet/chest/legs/boots/armor/equip/o 模式的物品禁用返回 true
     */
    public boolean hasEquipBan() {
        return equipBanFlag;
    }

    /**
     * 重新计算装备禁用标志。
     * 遍历所有世界的物品禁用条目，只要存在任意装备类模式即置为 true。
     * 在移除禁用、清除世界、加载缓存等可能改变装备禁用集合的操作后调用。
     */
    private void recomputeEquipBanFlag() {
        boolean found = false;
        for (Map<String, Map<String, Object>> worldInfo : allBannedInfoCache.values()) {
            for (Map<String, Object> info : worldInfo.values()) {
                if (!"Item".equals(info.get("Type"))) {
                    continue;
                }
                if (ArmorBanHelper.isEquipMode((String) info.get("Mode"))) {
                    found = true;
                    break;
                }
            }
            if (found) {
                break;
            }
        }
        this.equipBanFlag = found;
    }

    // ==================== 方块相关 ====================

    public void addBannedBlock(String world, String serialNumber, Block block, int distinguish,
                               boolean clear, String mode, String reason) {
        try {
            storage.addBannedBlock(world, serialNumber, block, distinguish, clear, mode, reason);
            worldsCache.add(world);
            bannedBlocksCache.computeIfAbsent(world, k -> ConcurrentHashMap.newKeySet()).add(serialNumber);
            Map<String, Object> info = new HashMap<>();
            info.put("Type", "Block");
            info.put("ID", block.getType().toString());
            info.put("Data", (int) block.getData());
            info.put("Distinguish", distinguish);
            info.put("Clear", clear);
            info.put("Mode", mode);
            info.put("Reason", reason);
            try {
                NBTTileEntity nbtTileEntity = new NBTTileEntity(block.getState());
                if (nbtTileEntity.hasKey("id")) {
                    info.put("TileID", nbtTileEntity.getString("id"));
                }
                if (nbtTileEntity.hasKey("SubTileName")) {
                    info.put("SubTileName", nbtTileEntity.getString("SubTileName"));
                }
            } catch (Exception ignored) {
            }
            allBannedInfoCache.computeIfAbsent(world, k -> new ConcurrentHashMap<>()).put(serialNumber, info);
        } finally {
            invalidateItemIndex();
        }
    }

    public void removeBannedBlock(String world, String serialNumber) {
        try {
            storage.removeBannedBlock(world, serialNumber);
            Set<String> blocks = bannedBlocksCache.get(world);
            if (blocks != null) {
                blocks.remove(serialNumber);
                if (blocks.isEmpty()) {
                    bannedBlocksCache.remove(world);
                    checkAndRemoveWorld(world);
                }
            }
            Map<String, Map<String, Object>> worldInfo = allBannedInfoCache.get(world);
            if (worldInfo != null) {
                worldInfo.remove(serialNumber);
            }
        } finally {
            invalidateItemIndex();
        }
    }

    public String isBannedBlock(String world, Block block) {
        if (block == null) {
            return null;
        }
        Set<String> blocks = bannedBlocksCache.get(world);
        if (blocks == null || blocks.isEmpty()) {
            return null;
        }
        Map<String, Map<String, Object>> worldInfo = allBannedInfoCache.get(world);
        if (worldInfo == null) {
            return null;
        }
        for (String serialNumber : blocks) {
            Map<String, Object> info = worldInfo.get(serialNumber);
            if (info == null || !"Block".equals(info.get("Type"))) {
                continue;
            }
            String id = (String) info.get("ID");
            int data = getIntSafe(info, "Data", 0);
            int distinguish = getIntSafe(info, "Distinguish", 0);
            String tileID = (String) info.get("TileID");
            String subTileName = (String) info.get("SubTileName");
            if (tileID != null || subTileName != null) {
                try {
                    NBTTileEntity nbtTileEntity = new NBTTileEntity(block.getState());
                    if (tileID != null && nbtTileEntity.hasKey("id")) {
                        if (nbtTileEntity.getString("id").equals(tileID)) {
                            if (subTileName != null && nbtTileEntity.hasKey("SubTileName")) {
                                if (nbtTileEntity.getString("SubTileName").equals(subTileName)) {
                                    return serialNumber;
                                }
                            } else if (subTileName == null) {
                                return serialNumber;
                            }
                        }
                    }
                } catch (Exception ignored) {
                }
                continue;
            }
            if (distinguish == 0) {
                if (block.getType().toString().equals(id) && block.getData() == (byte) data) {
                    return serialNumber;
                }
            } else if (distinguish == 1) {
                if (block.getType().toString().equals(id)) {
                    return serialNumber;
                }
            } else if (distinguish == 2) {
                if (block.getType().toString().equals(id) && block.getData() == (byte) data) {
                    return serialNumber;
                }
            }
        }
        return null;
    }

    public List<String> getBannedBlocks(String world) {
        Set<String> blocks = bannedBlocksCache.get(world);
        return blocks != null ? new ArrayList<>(blocks) : new ArrayList<>();
    }

    // ==================== 实体相关 ====================

    public void addBannedEntity(String world, String serialNumber, Entity entity, int distinguish, String reason) {
        try {
            storage.addBannedEntity(world, serialNumber, entity, distinguish, reason);
            worldsCache.add(world);
            bannedEntitiesCache.computeIfAbsent(world, k -> ConcurrentHashMap.newKeySet()).add(serialNumber);
            Map<String, Object> info = new HashMap<>();
            info.put("Type", "Entity");
            info.put("EntityType", entity.getType().name());
            info.put("Distinguish", distinguish);
            info.put("Reason", reason);
            if (entity instanceof LivingEntity) {
                LivingEntity livingEntity = (LivingEntity) entity;
                if (livingEntity.getCustomName() != null) {
                    info.put("Name", livingEntity.getCustomName());
                }
            }
            if (entity instanceof Ageable) {
                Ageable ageable = (Ageable) entity;
                info.put("Species", ageable.isAdult() ? "adult" : "baby");
            }
            allBannedInfoCache.computeIfAbsent(world, k -> new ConcurrentHashMap<>()).put(serialNumber, info);
        } finally {
            invalidateItemIndex();
        }
    }

    public void removeBannedEntity(String world, String serialNumber) {
        try {
            storage.removeBannedEntity(world, serialNumber);
            Set<String> entities = bannedEntitiesCache.get(world);
            if (entities != null) {
                entities.remove(serialNumber);
                if (entities.isEmpty()) {
                    bannedEntitiesCache.remove(world);
                    checkAndRemoveWorld(world);
                }
            }
            Map<String, Map<String, Object>> worldInfo = allBannedInfoCache.get(world);
            if (worldInfo != null) {
                worldInfo.remove(serialNumber);
            }
        } finally {
            invalidateItemIndex();
        }
    }

    public String isBannedEntity(String world, Entity entity) {
        if (entity == null || entity instanceof Player) {
            return null;
        }
        Set<String> entities = bannedEntitiesCache.get(world);
        if (entities == null || entities.isEmpty()) {
            return null;
        }
        Map<String, Map<String, Object>> worldInfo = allBannedInfoCache.get(world);
        if (worldInfo == null) {
            return null;
        }
        for (String serialNumber : entities) {
            Map<String, Object> info = worldInfo.get(serialNumber);
            if (info == null || !"Entity".equals(info.get("Type"))) {
                continue;
            }
            String entityType = (String) info.get("EntityType");
            int distinguish = getIntSafe(info, "Distinguish", 0);
            String name = (String) info.get("Name");
            String species = (String) info.get("Species");
            if (distinguish == 0) {
                if (entity.getType().toString().equals(entityType)) {
                    return serialNumber;
                }
            } else if (distinguish == 1) {
                if (entity.getType().toString().equals(entityType)) {
                    if (species != null && entity instanceof Ageable) {
                        Ageable ageable = (Ageable) entity;
                        String entitySpecies = ageable.isAdult() ? "adult" : "baby";
                        if (entitySpecies.equals(species)) {
                            return serialNumber;
                        }
                    } else if (species == null) {
                        return serialNumber;
                    }
                }
            } else if (distinguish == 2) {
                if (entity.getType().toString().equals(entityType)) {
                    if (name != null && entity instanceof LivingEntity) {
                        LivingEntity livingEntity = (LivingEntity) entity;
                        if (name.equals(livingEntity.getCustomName())) {
                            return serialNumber;
                        }
                    } else if (name == null) {
                        return serialNumber;
                    }
                }
            }
        }
        return null;
    }

    public List<String> getBannedEntities(String world) {
        Set<String> entities = bannedEntitiesCache.get(world);
        return entities != null ? new ArrayList<>(entities) : new ArrayList<>();
    }

    // ==================== 附魔相关 ====================

    public void addBannedEnchantment(String world, String serialNumber, String enchName,
                                     boolean clear, String mode, String level, String reason) {
        try {
            storage.addBannedEnchantment(world, serialNumber, enchName, clear, mode, level, reason);
            worldsCache.add(world);
            bannedEnchantmentsCache.computeIfAbsent(world, k -> ConcurrentHashMap.newKeySet()).add(serialNumber);
            Map<String, Object> info = new HashMap<>();
            info.put("Type", "Ench");
            info.put("EnchName", enchName);
            info.put("Clear", clear);
            info.put("Mode", mode);
            info.put("Level", level);
            info.put("Reason", reason);
            allBannedInfoCache.computeIfAbsent(world, k -> new ConcurrentHashMap<>()).put(serialNumber, info);
        } finally {
            invalidateItemIndex();
        }
    }

    public void removeBannedEnchantment(String world, String serialNumber) {
        try {
            storage.removeBannedEnchantment(world, serialNumber);
            Set<String> enchantments = bannedEnchantmentsCache.get(world);
            if (enchantments != null) {
                enchantments.remove(serialNumber);
                if (enchantments.isEmpty()) {
                    bannedEnchantmentsCache.remove(world);
                    checkAndRemoveWorld(world);
                }
            }
            Map<String, Map<String, Object>> worldInfo = allBannedInfoCache.get(world);
            if (worldInfo != null) {
                worldInfo.remove(serialNumber);
            }
        } finally {
            invalidateItemIndex();
        }
    }

    public String isBannedEnchantment(Player player, String world, ItemStack itemStack) {
        if (IItem.isEmpty(itemStack) || itemStack.getEnchantments().isEmpty()) {
            return null;
        }
        Set<String> enchantments = bannedEnchantmentsCache.get(world);
        if (enchantments == null || enchantments.isEmpty()) {
            return null;
        }
        Map<String, Map<String, Object>> worldInfo = allBannedInfoCache.get(world);
        if (worldInfo == null) {
            return null;
        }
        for (String serialNumber : enchantments) {
            Map<String, Object> info = worldInfo.get(serialNumber);
            if (info == null || !"Ench".equals(info.get("Type"))) {
                continue;
            }
            if (player != null) {
                if (player.hasPermission("bi.ignore." + serialNumber) || player.hasPermission("bi.op")) {
                    continue;
                }
            }
            String enchName = (String) info.get("EnchName");
            String levelStr = (String) info.get("Level");
            if (levelStr == null) {
                levelStr = ">0";
            }
            Enchantment enchantment = Enchantment.getByName(enchName);
            if (enchantment == null) {
                continue;
            }
            if (itemStack.containsEnchantment(enchantment)) {
                int itemLevel = itemStack.getEnchantmentLevel(enchantment);
                if (matchLevel(itemLevel, levelStr)) {
                    return serialNumber;
                }
            }
        }
        return null;
    }

    public List<String> getBannedEnchantments(String world) {
        Set<String> enchantments = bannedEnchantmentsCache.get(world);
        return enchantments != null ? new ArrayList<>(enchantments) : new ArrayList<>();
    }

    // ==================== 通用方法 ====================

    public List<String> getBannedWorlds() {
        return new ArrayList<>(worldsCache);
    }

    public Map<String, Map<String, Object>> getAllBannedInfo(String world) {
        Map<String, Map<String, Object>> worldInfo = allBannedInfoCache.get(world);
        if (worldInfo == null) {
            return new HashMap<>();
        }
        return new HashMap<>(worldInfo);
    }

    /**
     * 获取单条禁用信息（从缓存直接读取，不拷贝整张世界禁用表）
     *
     * <p>性能说明：{@link #getAllBannedInfo(String)} 每次调用都会对整个世界的禁用信息表
     * 做一次浅拷贝（new HashMap&lt;&gt;(worldInfo)），如果调用方只是想按 serialNumber 取
     * 单条记录（例如 {@code getAllBannedInfo(world).get(serialNumber)} 这种写法），
     * 这份拷贝就是纯浪费——世界内禁用条目越多、命中越频繁（例如某个附魔/装备被大量玩家
     * 持有触发事件），这个开销就越明显。本方法直接在缓存内做一次 Map.get，不做任何拷贝，
     * 语义与 {@code getAllBannedInfo(world).get(serialNumber)} 完全一致（找不到都返回 null）。
     *
     * @param world        世界名称
     * @param serialNumber 序列号
     * @return 禁用信息，不存在时返回 null
     */
    public Map<String, Object> getBannedInfo(String world, String serialNumber) {
        Map<String, Map<String, Object>> worldInfo = allBannedInfoCache.get(world);
        if (worldInfo == null) {
            return null;
        }
        return worldInfo.get(serialNumber);
    }

    public void clearWorld(String world) {
        try {
            storage.clearWorld(world);
            bannedItemsCache.remove(world);
            bannedBlocksCache.remove(world);
            bannedEntitiesCache.remove(world);
            bannedEnchantmentsCache.remove(world);
            allBannedInfoCache.remove(world);
            worldDisplayItemCache.remove(world);
            worldsCache.remove(world);
            // 清除世界后重算装备禁用标志
            recomputeEquipBanFlag();
        } finally {
            invalidateItemIndex();
        }
    }

    public void clearAll() {
        storage.clearAll();
        clearAllCache();
    }

    public void setWorldDisplayItem(String world, ItemStack item) {
        storage.setWorldDisplayItem(world, item);
        worldDisplayItemCache.put(world, item);
    }

    public ItemStack getWorldDisplayItem(String world) {
        return worldDisplayItemCache.get(world);
    }

    public String getFlags(String serialNumber) {
        return storage.getFlags(serialNumber);
    }

    public boolean hasFlag(String serialNumber) {
        String flags = getFlags(serialNumber);
        if (flags == null) {
            return false;
        }
        for (String world : worldsCache) {
            for (String otherSerial : getBannedItems(world)) {
                if (!otherSerial.equals(serialNumber) && flags.equals(getFlags(otherSerial))) {
                    return true;
                }
            }
            for (String otherSerial : getBannedBlocks(world)) {
                if (!otherSerial.equals(serialNumber) && flags.equals(getFlags(otherSerial))) {
                    return true;
                }
            }
            for (String otherSerial : getBannedEntities(world)) {
                if (!otherSerial.equals(serialNumber) && flags.equals(getFlags(otherSerial))) {
                    return true;
                }
            }
            for (String otherSerial : getBannedEnchantments(world)) {
                if (!otherSerial.equals(serialNumber) && flags.equals(getFlags(otherSerial))) {
                    return true;
                }
            }
        }
        return false;
    }

    private void checkAndRemoveWorld(String world) {
        if (getBannedItems(world).isEmpty() &&
                getBannedBlocks(world).isEmpty() &&
                getBannedEntities(world).isEmpty() &&
                getBannedEnchantments(world).isEmpty()) {
            worldsCache.remove(world);
        }
    }

    public void addBannedBlockDirect(String world, String serialNumber, String id, byte data,
                                     int distinguish, boolean clear, String mode, String reason) {
        try {
            storage.addBannedBlockDirect(world, serialNumber, id, data, distinguish, clear, mode, reason);
            worldsCache.add(world);
            bannedBlocksCache.computeIfAbsent(world, k -> ConcurrentHashMap.newKeySet()).add(serialNumber);
            Map<String, Object> info = new HashMap<>();
            info.put("Type", "Block");
            info.put("ID", id);
            info.put("Data", (int) data);
            info.put("Distinguish", distinguish);
            info.put("Clear", clear);
            info.put("Mode", mode);
            info.put("Reason", reason);
            allBannedInfoCache.computeIfAbsent(world, k -> new ConcurrentHashMap<>()).put(serialNumber, info);
        } finally {
            invalidateItemIndex();
        }
    }

    public void addBannedEntityDirect(String world, String serialNumber, String entityType,
                                      int distinguish, String reason) {
        try {
            storage.addBannedEntityDirect(world, serialNumber, entityType, distinguish, reason);
            worldsCache.add(world);
            bannedEntitiesCache.computeIfAbsent(world, k -> ConcurrentHashMap.newKeySet()).add(serialNumber);
            Map<String, Object> info = new HashMap<>();
            info.put("Type", "Entity");
            info.put("EntityType", entityType);
            info.put("Distinguish", distinguish);
            info.put("Reason", reason);
            allBannedInfoCache.computeIfAbsent(world, k -> new ConcurrentHashMap<>()).put(serialNumber, info);
        } finally {
            invalidateItemIndex();
        }
    }

    public DataStorage getStorage() {
        return storage;
    }

    // ==================== 工具方法 ====================

    private int getIntSafe(Map<String, Object> map, String key, int defaultValue) {
        Object value = map.get(key);
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        return defaultValue;
    }

    private boolean matchLevel(int level, String levelStr) {
        if (levelStr == null || levelStr.isEmpty()) {
            return true;
        }
        try {
            if (levelStr.startsWith(">=")) {
                return level >= Integer.parseInt(levelStr.substring(2));
            } else if (levelStr.startsWith("<=")) {
                return level <= Integer.parseInt(levelStr.substring(2));
            } else if (levelStr.startsWith(">")) {
                return level > Integer.parseInt(levelStr.substring(1));
            } else if (levelStr.startsWith("<")) {
                return level < Integer.parseInt(levelStr.substring(1));
            } else if (levelStr.startsWith("=")) {
                return level == Integer.parseInt(levelStr.substring(1));
            } else {
                return level == Integer.parseInt(levelStr);
            }
        } catch (NumberFormatException e) {
            return true;
        }
    }
}


