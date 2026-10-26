package com.flameforge.banit.utils;

import com.flameforge.banit.Banit;
import com.flameforge.banit.storage.DataStorage;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;
import com.flameforge.banit.config.LanguageManager;
import com.flameforge.banit.storage.MySqlDataStorage;
import pers.tany.yukinoaapi.interfacepart.serializer.ISerializer;

/**
 * 数据迁移工具类
 * 用于将数据从 YAML 格式迁移到 MySQL 数据库
 *
 * @author Tany天炎
 */
public class DataMigration {

    /**
     * 开始数据迁移流程
     *
     * @param sender 命令发送者
     */
    public static void migrateDataToMySQL(CommandSender sender) {
        LanguageManager languageManager = Banit.getLanguageManager();

        if (!(Banit.getDataStorage() instanceof MySqlDataStorage)) {
            sender.sendMessage(languageManager.getMessage("error.not-mysql-mode"));
            return;
        }

        sender.sendMessage(languageManager.getMessage("migration.start"));

        new BukkitRunnable() {
            @Override
            public void run() {
                try {
                    YamlConfiguration yamlData = Banit.data;
                    DataStorage mysqlStorage = Banit.getDataStorage();

                    // 迁移物品数据
                    sender.sendMessage(languageManager.getMessage("migration.migrating-items"));
                    migrateItems(yamlData, mysqlStorage);

                    // 迁移方块数据
                    sender.sendMessage(languageManager.getMessage("migration.migrating-blocks"));
                    migrateBlocks(yamlData, mysqlStorage);

                    // 迁移实体数据
                    sender.sendMessage(languageManager.getMessage("migration.migrating-entities"));
                    migrateEntities(yamlData, mysqlStorage);

                    // 迁移附魔数据
                    sender.sendMessage(languageManager.getMessage("migration.migrating-enchantments"));
                    migrateEnchantments(yamlData, mysqlStorage);

                    // 迁移世界展示物品
                    sender.sendMessage(languageManager.getMessage("migration.migrating-display-items"));
                    migrateWorldDisplayItems(yamlData, mysqlStorage);

                    sender.sendMessage(languageManager.getMessage("migration.success"));
                } catch (Exception e) {
                    sender.sendMessage(languageManager.getMessage("migration.error", "error", e.getMessage()));
                    Banit.plugin.getLogger().severe("Data migration failed: " + e.getMessage());
                    e.printStackTrace();
                }
            }
        }.runTaskAsynchronously(Banit.plugin);
    }

    /**
     * 迁移物品数据
     */
    private static void migrateItems(YamlConfiguration yamlData, DataStorage mysqlStorage) {
        ConfigurationSection itemSection = yamlData.getConfigurationSection("BanInfo");
        if (itemSection == null) {
            return;
        }

        int count = 0;
        for (String world : itemSection.getKeys(false)) {
            ConfigurationSection worldItems = itemSection.getConfigurationSection(world + ".Item");
            if (worldItems != null) {
                for (String serialNumber : worldItems.getKeys(false)) {
                    try {
                        String itemData = worldItems.getString(serialNumber + ".ItemStack");
                        String id = worldItems.getString(serialNumber + ".ID");
                        short durability = (short) worldItems.getInt(serialNumber + ".Data");
                        int distinguish = worldItems.getInt(serialNumber + ".Distinguish", 0);
                        boolean clear = worldItems.getBoolean(serialNumber + ".Clear");
                        String mode = worldItems.getString(serialNumber + ".Mode");
                        String reason = worldItems.getString(serialNumber + ".Reason");
                        String key = worldItems.getString(serialNumber + ".Key");
                        String value = worldItems.getString(serialNumber + ".Value");
                        String fuzzyNBT = worldItems.getString(serialNumber + ".FuzzyNBT");

                        ItemStack item = ISerializer.deserializeItemStack(itemData);
                        mysqlStorage.addBannedItem(world, serialNumber, item, id, durability, distinguish, clear, mode, reason, key, value, fuzzyNBT);
                        count++;
                    } catch (Exception e) {
                        Banit.plugin.getLogger().warning("Failed to migrate item " + serialNumber + ": " + e.getMessage());
                    }
                }
            }
        }
        Banit.plugin.getLogger().info("Successfully migrated " + count + " item records");
    }

    /**
     * 迁移方块数据
     */
    private static void migrateBlocks(YamlConfiguration yamlData, DataStorage mysqlStorage) {
        ConfigurationSection itemSection = yamlData.getConfigurationSection("BanInfo");
        if (itemSection == null) {
            return;
        }

        int count = 0;
        for (String world : itemSection.getKeys(false)) {
            ConfigurationSection worldBlocks = itemSection.getConfigurationSection(world + ".Block");
            if (worldBlocks != null) {
                for (String serialNumber : worldBlocks.getKeys(false)) {
                    try {
                        String id = worldBlocks.getString(serialNumber + ".ID");
                        byte data = (byte) worldBlocks.getInt(serialNumber + ".Data");
                        int distinguish = worldBlocks.getInt(serialNumber + ".Distinguish", 0);
                        boolean clear = worldBlocks.getBoolean(serialNumber + ".Clear");
                        String mode = worldBlocks.getString(serialNumber + ".Mode");
                        String reason = worldBlocks.getString(serialNumber + ".Reason");
                        String tileID = worldBlocks.getString(serialNumber + ".TileID");
                        String subTileName = worldBlocks.getString(serialNumber + ".SubTileName");

                        if (mysqlStorage instanceof MySqlDataStorage) {
                            ((MySqlDataStorage) mysqlStorage).addBannedBlockWithTile(
                                    world, serialNumber, id, data, distinguish, clear, mode, reason, tileID, subTileName
                            );
                        } else {
                            mysqlStorage.addBannedBlockDirect(world, serialNumber, id, data, distinguish, clear, mode, reason);
                        }
                        count++;
                    } catch (Exception e) {
                        Banit.plugin.getLogger().warning("Failed to migrate block " + serialNumber + ": " + e.getMessage());
                    }
                }
            }
        }
        Banit.plugin.getLogger().info("Successfully migrated " + count + " block records");
    }

    /**
     * 迁移实体数据
     */
    private static void migrateEntities(YamlConfiguration yamlData, DataStorage mysqlStorage) {
        ConfigurationSection itemSection = yamlData.getConfigurationSection("BanInfo");
        if (itemSection == null) {
            return;
        }

        int count = 0;
        for (String world : itemSection.getKeys(false)) {
            ConfigurationSection worldEntities = itemSection.getConfigurationSection(world + ".Entity");
            if (worldEntities != null) {
                for (String serialNumber : worldEntities.getKeys(false)) {
                    try {
                        String entityType = worldEntities.getString(serialNumber + ".EntityType");
                        int distinguish = worldEntities.getInt(serialNumber + ".Distinguish", 0);
                        String reason = worldEntities.getString(serialNumber + ".Reason");
                        String species = worldEntities.getString(serialNumber + ".Species");
                        String name = worldEntities.getString(serialNumber + ".Name");

                        if (mysqlStorage instanceof MySqlDataStorage) {
                            ((MySqlDataStorage) mysqlStorage).addBannedEntityWithDetails(
                                    world, serialNumber, entityType, distinguish, reason, species, name
                            );
                        } else {
                            mysqlStorage.addBannedEntityDirect(world, serialNumber, entityType, distinguish, reason);
                        }
                        count++;
                    } catch (Exception e) {
                        Banit.plugin.getLogger().warning("Failed to migrate entity " + serialNumber + ": " + e.getMessage());
                    }
                }
            }
        }
        Banit.plugin.getLogger().info("Successfully migrated " + count + " entity records");
    }

    /**
     * 迁移附魔数据
     */
    private static void migrateEnchantments(YamlConfiguration yamlData, DataStorage mysqlStorage) {
        ConfigurationSection itemSection = yamlData.getConfigurationSection("BanInfo");
        if (itemSection == null) {
            return;
        }

        int count = 0;
        for (String world : itemSection.getKeys(false)) {
            ConfigurationSection worldEnchants = itemSection.getConfigurationSection(world + ".Ench");
            if (worldEnchants != null) {
                for (String serialNumber : worldEnchants.getKeys(false)) {
                    try {
                        String enchName = worldEnchants.getString(serialNumber + ".EnchName");
                        boolean clear = worldEnchants.getBoolean(serialNumber + ".Clear");
                        String mode = worldEnchants.getString(serialNumber + ".Mode");
                        String level = worldEnchants.getString(serialNumber + ".Level");
                        String reason = worldEnchants.getString(serialNumber + ".Reason");

                        mysqlStorage.addBannedEnchantment(world, serialNumber, enchName, clear, mode, level, reason);
                        count++;
                    } catch (Exception e) {
                        Banit.plugin.getLogger().warning("Failed to migrate enchantment " + serialNumber + ": " + e.getMessage());
                    }
                }
            }
        }
        Banit.plugin.getLogger().info("Successfully migrated " + count + " enchantment records");
    }

    /**
     * 迁移世界展示物品
     */
    private static void migrateWorldDisplayItems(YamlConfiguration yamlData, DataStorage mysqlStorage) {
        ConfigurationSection itemSection = yamlData.getConfigurationSection("BanInfo");
        if (itemSection == null) {
            return;
        }

        int count = 0;
        for (String world : itemSection.getKeys(false)) {
            try {
                String itemData = itemSection.getString(world + ".DisplayItem");
                if (itemData != null) {
                    ItemStack item = ISerializer.deserializeItemStack(itemData);
                    mysqlStorage.setWorldDisplayItem(world, item);
                    count++;
                }
            } catch (Exception e) {
                Banit.plugin.getLogger().warning("Failed to migrate display item for world " + world + ": " + e.getMessage());
            }
        }
        Banit.plugin.getLogger().info("Successfully migrated " + count + " world display items");
    }
}