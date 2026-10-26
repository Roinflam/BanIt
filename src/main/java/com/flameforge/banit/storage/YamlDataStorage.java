package com.flameforge.banit.storage;

import com.flameforge.banit.Banit;
import com.flameforge.banit.config.ConfigManager;
import com.flameforge.banit.utils.NBTPathUtil;
import de.tr7zw.nbtapi.NBTItem;
import de.tr7zw.nbtapi.NBTTileEntity;
import org.bukkit.block.Block;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Ageable;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import pers.tany.yukinoaapi.interfacepart.configuration.IConfig;
import pers.tany.yukinoaapi.interfacepart.item.IItem;
import pers.tany.yukinoaapi.interfacepart.serializer.ISerializer;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * YAML 数据存储实现
 * 使用 YAML 文件存储所有禁止项数据
 *
 * @author Tany天炎
 */
public class YamlDataStorage implements DataStorage {

    /**
     * 配置管理器
     */
    private final ConfigManager configManager;

    /**
     * 构造函数
     */
    public YamlDataStorage() {
        this.configManager = Banit.getConfigManager();
    }

    @Override
    public void initialize() {
        // YAML 存储不需要额外初始化
    }

    @Override
    public void shutdown() {
        // 保存数据
        IConfig.saveConfig(Banit.plugin, Banit.data, "", "data");
    }

    // ==================== 物品相关方法 ====================

    @Override
    public void addBannedItem(String world, String serialNumber, ItemStack item, String id, short durability, int distinguish, boolean clear, String mode, String reason, String key, String value, String fuzzyNBT) {
        String path = "BanInfo." + world + ".Item." + serialNumber;

        Banit.data.set(path + ".ItemStack", ISerializer.serializerItemStack(item, true));
        Banit.data.set(path + ".ID", id);
        Banit.data.set(path + ".Data", durability);
        Banit.data.set(path + ".Distinguish", distinguish);
        Banit.data.set(path + ".Clear", clear);
        Banit.data.set(path + ".Mode", mode);
        Banit.data.set(path + ".Reason", reason);

        if (key != null) {
            Banit.data.set(path + ".Key", key);
        }
        if (value != null) {
            Banit.data.set(path + ".Value", value);
        }
        if (fuzzyNBT != null) {
            Banit.data.set(path + ".FuzzyNBT", fuzzyNBT);
        }

        IConfig.saveConfig(Banit.plugin, Banit.data, "", "data");
    }

    @Override
    public void removeBannedItem(String world, String serialNumber) {
        Banit.data.set("BanInfo." + world + ".Item." + serialNumber, null);
        cleanupEmptyWorld(world);
        IConfig.saveConfig(Banit.plugin, Banit.data, "", "data");
    }

    @Override
    public String isBannedItemStack(Player player, String world, ItemStack itemStack, boolean ignorePermission) {
        if (IItem.isEmpty(itemStack)) {
            return null;
        }

        String basePath = "BanInfo." + world + ".Item";
        if (!Banit.data.contains(basePath)) {
            return null;
        }

        for (String serialNumber : Banit.data.getConfigurationSection(basePath).getKeys(false)) {
            String path = basePath + "." + serialNumber;

            // 权限检查
            if (!ignorePermission && player != null) {
                if (player.hasPermission("bi.ignore." + serialNumber) || player.hasPermission("bi.op")) {
                    continue;
                }
            }

            String id = Banit.data.getString(path + ".ID");
            short data = (short) Banit.data.getInt(path + ".Data");
            int distinguish = Banit.data.getInt(path + ".Distinguish", 0);
            String key = Banit.data.getString(path + ".Key");
            String value = Banit.data.getString(path + ".Value");
            String fuzzyNBT = Banit.data.getString(path + ".FuzzyNBT");

            // NBT 检查（支持路径）
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

            // 模糊 NBT 检查
            if (fuzzyNBT != null) {
                try {
                    NBTItem nbtItem = new NBTItem(itemStack);
                    if (nbtItem.toString().contains(fuzzyNBT)) {
                        return serialNumber;
                    }
                } catch (Exception ignored) {
                }
                continue;
            }

            // 物品匹配
            if (distinguish == 0) {
                // 精确匹配
                if (itemStack.getType().toString().equals(id) && itemStack.getDurability() == data) {
                    return serialNumber;
                }
            } else if (distinguish == 1) {
                // 仅匹配类型
                if (itemStack.getType().toString().equals(id)) {
                    return serialNumber;
                }
            } else if (distinguish == 2) {
                // 完全匹配（包括NBT）
                String storedItemData = Banit.data.getString(path + ".ItemStack");
                ItemStack storedItem = ISerializer.deserializeItemStack(storedItemData);
                if (storedItem != null && storedItem.isSimilar(itemStack)) {
                    return serialNumber;
                }
            }
        }

        return null;
    }

    @Override
    public boolean isBannedItemClear(String world, String serialNumber) {
        return Banit.data.getBoolean("BanInfo." + world + ".Item." + serialNumber + ".Clear", false);
    }

    @Override
    public String getBannedItemMode(String world, String serialNumber) {
        return Banit.data.getString("BanInfo." + world + ".Item." + serialNumber + ".Mode", "a");
    }

    @Override
    public String getBannedItemReason(String world, String serialNumber) {
        return Banit.data.getString("BanInfo." + world + ".Item." + serialNumber + ".Reason", configManager.getDefaultReason());
    }

    // ==================== 方块相关方法 ====================

    @Override
    public void addBannedBlock(String world, String serialNumber, Block block, int distinguish, boolean clear, String mode, String reason) {
        String path = "BanInfo." + world + ".Block." + serialNumber;

        Banit.data.set(path + ".ID", block.getType().toString());
        Banit.data.set(path + ".Data", block.getData());
        Banit.data.set(path + ".Distinguish", distinguish);
        Banit.data.set(path + ".Clear", clear);
        Banit.data.set(path + ".Mode", mode);
        Banit.data.set(path + ".Reason", reason);

        // 保存 TileEntity NBT（如果有）
        try {
            NBTTileEntity nbtTileEntity = new NBTTileEntity(block.getState());
            if (nbtTileEntity.hasKey("id")) {
                Banit.data.set(path + ".TileID", nbtTileEntity.getString("id"));
            }
            if (nbtTileEntity.hasKey("SubTileName")) {
                Banit.data.set(path + ".SubTileName", nbtTileEntity.getString("SubTileName"));
            }
        } catch (Exception ignored) {
        }

        IConfig.saveConfig(Banit.plugin, Banit.data, "", "data");
    }

    @Override
    public void addBannedBlockDirect(String world, String serialNumber, String id, byte data, int distinguish, boolean clear, String mode, String reason) {
        String path = "BanInfo." + world + ".Block." + serialNumber;

        Banit.data.set(path + ".ID", id);
        Banit.data.set(path + ".Data", data);
        Banit.data.set(path + ".Distinguish", distinguish);
        Banit.data.set(path + ".Clear", clear);
        Banit.data.set(path + ".Mode", mode);
        Banit.data.set(path + ".Reason", reason);

        IConfig.saveConfig(Banit.plugin, Banit.data, "", "data");
    }

    @Override
    public void removeBannedBlock(String world, String serialNumber) {
        Banit.data.set("BanInfo." + world + ".Block." + serialNumber, null);
        cleanupEmptyWorld(world);
        IConfig.saveConfig(Banit.plugin, Banit.data, "", "data");
    }

    @Override
    public String isBannedBlock(String world, Block block) {
        if (block == null) {
            return null;
        }

        String basePath = "BanInfo." + world + ".Block";
        if (!Banit.data.contains(basePath)) {
            return null;
        }

        for (String serialNumber : Banit.data.getConfigurationSection(basePath).getKeys(false)) {
            String path = basePath + "." + serialNumber;

            String id = Banit.data.getString(path + ".ID");
            byte data = (byte) Banit.data.getInt(path + ".Data");
            int distinguish = Banit.data.getInt(path + ".Distinguish", 0);
            String tileID = Banit.data.getString(path + ".TileID");
            String subTileName = Banit.data.getString(path + ".SubTileName");

            // TileEntity 检查
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

            // 方块匹配
            if (distinguish == 0) {
                // 精确匹配
                if (block.getType().toString().equals(id) && block.getData() == data) {
                    return serialNumber;
                }
            } else if (distinguish == 1) {
                // 仅匹配类型
                if (block.getType().toString().equals(id)) {
                    return serialNumber;
                }
            } else if (distinguish == 2) {
                // 完全匹配（包括数据值）
                if (block.getType().toString().equals(id) && block.getData() == data) {
                    return serialNumber;
                }
            }
        }

        return null;
    }

    // ==================== 实体相关方法 ====================

    @Override
    public void addBannedEntity(String world, String serialNumber, Entity entity, int distinguish, String reason) {
        String path = "BanInfo." + world + ".Entity." + serialNumber;

        Banit.data.set(path + ".EntityType", entity.getType().toString());
        Banit.data.set(path + ".Distinguish", distinguish);
        Banit.data.set(path + ".Reason", reason);

        // 保存实体额外信息
        if (entity instanceof LivingEntity) {
            LivingEntity livingEntity = (LivingEntity) entity;
            if (livingEntity.getCustomName() != null) {
                Banit.data.set(path + ".Name", livingEntity.getCustomName());
            }
        }

        if (entity instanceof Ageable) {
            Ageable ageable = (Ageable) entity;
            Banit.data.set(path + ".Species", ageable.isAdult() ? "adult" : "baby");
        }

        IConfig.saveConfig(Banit.plugin, Banit.data, "", "data");
    }

    @Override
    public void addBannedEntityDirect(String world, String serialNumber, String entityType, int distinguish, String reason) {
        String path = "BanInfo." + world + ".Entity." + serialNumber;

        Banit.data.set(path + ".EntityType", entityType);
        Banit.data.set(path + ".Distinguish", distinguish);
        Banit.data.set(path + ".Reason", reason);

        IConfig.saveConfig(Banit.plugin, Banit.data, "", "data");
    }

    @Override
    public void removeBannedEntity(String world, String serialNumber) {
        Banit.data.set("BanInfo." + world + ".Entity." + serialNumber, null);
        cleanupEmptyWorld(world);
        IConfig.saveConfig(Banit.plugin, Banit.data, "", "data");
    }

    @Override
    public String isBannedEntity(String world, Entity entity) {
        if (entity == null || entity instanceof Player) {
            return null;
        }

        String basePath = "BanInfo." + world + ".Entity";
        if (!Banit.data.contains(basePath)) {
            return null;
        }

        for (String serialNumber : Banit.data.getConfigurationSection(basePath).getKeys(false)) {
            String path = basePath + "." + serialNumber;

            String entityType = Banit.data.getString(path + ".EntityType");
            int distinguish = Banit.data.getInt(path + ".Distinguish", 0);
            String name = Banit.data.getString(path + ".Name");
            String species = Banit.data.getString(path + ".Species");

            if (distinguish == 0) {
                // 精确匹配
                if (entity.getType().toString().equals(entityType)) {
                    return serialNumber;
                }
            } else if (distinguish == 1) {
                // 匹配类型和种类
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
                // 匹配类型和名称
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

    // ==================== 附魔相关方法 ====================

    @Override
    public void addBannedEnchantment(String world, String serialNumber, String enchName, boolean clear, String mode, String level, String reason) {
        String path = "BanInfo." + world + ".Ench." + serialNumber;

        Banit.data.set(path + ".EnchName", enchName);
        Banit.data.set(path + ".Clear", clear);
        Banit.data.set(path + ".Mode", mode);
        Banit.data.set(path + ".Level", level);
        Banit.data.set(path + ".Reason", reason);

        IConfig.saveConfig(Banit.plugin, Banit.data, "", "data");
    }

    @Override
    public void removeBannedEnchantment(String world, String serialNumber) {
        Banit.data.set("BanInfo." + world + ".Ench." + serialNumber, null);
        cleanupEmptyWorld(world);
        IConfig.saveConfig(Banit.plugin, Banit.data, "", "data");
    }

    @Override
    public String isBannedEnchantment(Player player, String world, ItemStack itemStack) {
        if (IItem.isEmpty(itemStack) || itemStack.getEnchantments().isEmpty()) {
            return null;
        }

        String basePath = "BanInfo." + world + ".Ench";
        if (!Banit.data.contains(basePath)) {
            return null;
        }

        for (String serialNumber : Banit.data.getConfigurationSection(basePath).getKeys(false)) {
            String path = basePath + "." + serialNumber;

            // 权限检查
            if (player != null) {
                if (player.hasPermission("bi.ignore." + serialNumber) || player.hasPermission("bi.op")) {
                    continue;
                }
            }

            String enchName = Banit.data.getString(path + ".EnchName");
            String levelStr = Banit.data.getString(path + ".Level", ">0");

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

    /**
     * 匹配等级条件
     *
     * @param level    实际等级
     * @param levelStr 等级条件字符串
     * @return 是否匹配
     */
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

    // ==================== 列表获取方法 ====================

    @Override
    public List<String> getBannedWorlds() {
        List<String> worlds = new ArrayList<>();

        if (Banit.data.contains("BanInfo")) {
            worlds.addAll(Banit.data.getConfigurationSection("BanInfo").getKeys(false));
        }

        return worlds;
    }

    @Override
    public List<String> getBannedItems(String world) {
        List<String> items = new ArrayList<>();

        String path = "BanInfo." + world + ".Item";
        if (Banit.data.contains(path)) {
            items.addAll(Banit.data.getConfigurationSection(path).getKeys(false));
        }

        return items;
    }

    @Override
    public List<String> getBannedBlocks(String world) {
        List<String> blocks = new ArrayList<>();

        String path = "BanInfo." + world + ".Block";
        if (Banit.data.contains(path)) {
            blocks.addAll(Banit.data.getConfigurationSection(path).getKeys(false));
        }

        return blocks;
    }

    @Override
    public List<String> getBannedEntities(String world) {
        List<String> entities = new ArrayList<>();

        String path = "BanInfo." + world + ".Entity";
        if (Banit.data.contains(path)) {
            entities.addAll(Banit.data.getConfigurationSection(path).getKeys(false));
        }

        return entities;
    }

    @Override
    public List<String> getBannedEnchantments(String world) {
        List<String> enchantments = new ArrayList<>();

        String path = "BanInfo." + world + ".Ench";
        if (Banit.data.contains(path)) {
            enchantments.addAll(Banit.data.getConfigurationSection(path).getKeys(false));
        }

        return enchantments;
    }

    // ==================== 清除方法 ====================

    @Override
    public void clearWorld(String world) {
        Banit.data.set("BanInfo." + world, null);
        IConfig.saveConfig(Banit.plugin, Banit.data, "", "data");
    }

    @Override
    public void clearAll() {
        Banit.data.set("BanInfo", null);
        IConfig.saveConfig(Banit.plugin, Banit.data, "", "data");
    }

    // ==================== 其他方法 ====================

    @Override
    public String getFlags(String serialNumber) {
        for (String world : getBannedWorlds()) {
            // 检查物品
            String itemPath = "BanInfo." + world + ".Item." + serialNumber;
            if (Banit.data.contains(itemPath)) {
                String id = Banit.data.getString(itemPath + ".ID");
                int data = Banit.data.getInt(itemPath + ".Data");
                int distinguish = Banit.data.getInt(itemPath + ".Distinguish");
                String key = Banit.data.getString(itemPath + ".Key");
                String value = Banit.data.getString(itemPath + ".Value");
                String fuzzyNBT = Banit.data.getString(itemPath + ".FuzzyNBT");

                // 统一处理 null 值，避免写成 "null" 字符串
                String keyStr = (key != null) ? key : "";
                String valueStr = (value != null) ? value : "";
                String fuzzyNBTStr = (fuzzyNBT != null) ? fuzzyNBT : "";

                return "item:" + id + ":" + data + ":" + distinguish + ":" + keyStr + ":" + valueStr + ":" + fuzzyNBTStr + ":" + world;
            }

            // 检查方块
            String blockPath = "BanInfo." + world + ".Block." + serialNumber;
            if (Banit.data.contains(blockPath)) {
                String id = Banit.data.getString(blockPath + ".ID");
                int data = Banit.data.getInt(blockPath + ".Data");
                int distinguish = Banit.data.getInt(blockPath + ".Distinguish");
                String tileID = Banit.data.getString(blockPath + ".TileID");
                String subTileName = Banit.data.getString(blockPath + ".SubTileName");

                // 统一处理 null 值
                String tileIDStr = (tileID != null) ? tileID : "";
                String subTileNameStr = (subTileName != null) ? subTileName : "";

                return "block:" + id + ":" + data + ":" + distinguish + ":" + tileIDStr + ":" + subTileNameStr + ":" + world;
            }

            // 检查实体
            String entityPath = "BanInfo." + world + ".Entity." + serialNumber;
            if (Banit.data.contains(entityPath)) {
                String entityType = Banit.data.getString(entityPath + ".EntityType");
                int distinguish = Banit.data.getInt(entityPath + ".Distinguish");
                String name = Banit.data.getString(entityPath + ".Name");
                String species = Banit.data.getString(entityPath + ".Species");

                // 统一处理 null 值
                String nameStr = (name != null) ? name : "";
                String speciesStr = (species != null) ? species : "";

                return "entity:" + entityType + ":" + distinguish + ":" + nameStr + ":" + speciesStr + ":" + world;
            }

            // 检查附魔
            String enchPath = "BanInfo." + world + ".Ench." + serialNumber;
            if (Banit.data.contains(enchPath)) {
                String enchName = Banit.data.getString(enchPath + ".EnchName");
                String level = Banit.data.getString(enchPath + ".Level");

                return "ench:" + enchName + ":" + level + ":" + world;
            }
        }

        return null;
    }

    @Override
    public Map<String, Map<String, Object>> getAllBannedInfo(String world) {
        Map<String, Map<String, Object>> result = new HashMap<>();

        // 物品
        for (String serialNumber : getBannedItems(world)) {
            Map<String, Object> info = new HashMap<>();
            String path = "BanInfo." + world + ".Item." + serialNumber;

            info.put("Type", "Item");
            info.put("ID", Banit.data.getString(path + ".ID"));
            info.put("Data", Banit.data.getInt(path + ".Data"));
            info.put("Distinguish", Banit.data.getInt(path + ".Distinguish"));
            info.put("Clear", Banit.data.getBoolean(path + ".Clear"));
            info.put("Mode", Banit.data.getString(path + ".Mode"));
            info.put("Reason", Banit.data.getString(path + ".Reason"));
            info.put("ItemStack", Banit.data.getString(path + ".ItemStack"));

            // 添加 NBT 相关字段
            String key = Banit.data.getString(path + ".Key");
            String value = Banit.data.getString(path + ".Value");
            String fuzzyNBT = Banit.data.getString(path + ".FuzzyNBT");
            if (key != null) {
                info.put("Key", key);
            }
            if (value != null) {
                info.put("Value", value);
            }
            if (fuzzyNBT != null) {
                info.put("FuzzyNBT", fuzzyNBT);
            }

            result.put(serialNumber, info);
        }

        // 方块
        for (String serialNumber : getBannedBlocks(world)) {
            Map<String, Object> info = new HashMap<>();
            String path = "BanInfo." + world + ".Block." + serialNumber;

            info.put("Type", "Block");
            info.put("ID", Banit.data.getString(path + ".ID"));
            info.put("Data", Banit.data.getInt(path + ".Data"));
            info.put("Distinguish", Banit.data.getInt(path + ".Distinguish"));
            info.put("Clear", Banit.data.getBoolean(path + ".Clear"));
            info.put("Mode", Banit.data.getString(path + ".Mode"));
            info.put("Reason", Banit.data.getString(path + ".Reason"));

            // 添加 TileEntity 相关字段
            String tileID = Banit.data.getString(path + ".TileID");
            String subTileName = Banit.data.getString(path + ".SubTileName");
            if (tileID != null) {
                info.put("TileID", tileID);
            }
            if (subTileName != null) {
                info.put("SubTileName", subTileName);
            }

            result.put(serialNumber, info);
        }

        // 实体
        for (String serialNumber : getBannedEntities(world)) {
            Map<String, Object> info = new HashMap<>();
            String path = "BanInfo." + world + ".Entity." + serialNumber;

            info.put("Type", "Entity");
            info.put("EntityType", Banit.data.getString(path + ".EntityType"));
            info.put("Distinguish", Banit.data.getInt(path + ".Distinguish"));
            info.put("Reason", Banit.data.getString(path + ".Reason"));

            // 添加实体额外信息
            String name = Banit.data.getString(path + ".Name");
            String species = Banit.data.getString(path + ".Species");
            if (name != null) {
                info.put("Name", name);
            }
            if (species != null) {
                info.put("Species", species);
            }

            result.put(serialNumber, info);
        }

        // 附魔
        for (String serialNumber : getBannedEnchantments(world)) {
            Map<String, Object> info = new HashMap<>();
            String path = "BanInfo." + world + ".Ench." + serialNumber;

            info.put("Type", "Ench");
            info.put("EnchName", Banit.data.getString(path + ".EnchName"));
            info.put("Clear", Banit.data.getBoolean(path + ".Clear"));
            info.put("Mode", Banit.data.getString(path + ".Mode"));
            info.put("Level", Banit.data.getString(path + ".Level"));
            info.put("Reason", Banit.data.getString(path + ".Reason"));

            result.put(serialNumber, info);
        }

        return result;
    }

    @Override
    public void setWorldDisplayItem(String world, ItemStack item) {
        Banit.data.set("BanInfo." + world + ".DisplayItem", ISerializer.serializerItemStack(item, true));
        IConfig.saveConfig(Banit.plugin, Banit.data, "", "data");
    }

    @Override
    public ItemStack getWorldDisplayItem(String world) {
        String itemData = Banit.data.getString("BanInfo." + world + ".DisplayItem");
        if (itemData != null) {
            return ISerializer.deserializeItemStack(itemData);
        }
        return null;
    }

    /**
     * 清理空的世界节点
     *
     * @param world 世界名称
     */
    private void cleanupEmptyWorld(String world) {
        String path = "BanInfo." + world;

        if (Banit.data.contains(path)) {
            boolean hasContent = Banit.data.contains(path + ".Item") ||
                    Banit.data.contains(path + ".Block") ||
                    Banit.data.contains(path + ".Entity") ||
                    Banit.data.contains(path + ".Ench") ||
                    Banit.data.contains(path + ".DisplayItem");

            if (!hasContent) {
                Banit.data.set(path, null);
            }
        }
    }
}