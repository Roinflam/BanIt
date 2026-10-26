package com.flameforge.banit.storage;

import com.flameforge.banit.Banit;
import com.flameforge.banit.config.ConfigManager;
import com.flameforge.banit.utils.NBTPathUtil;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import de.tr7zw.nbtapi.NBTItem;
import de.tr7zw.nbtapi.NBTTileEntity;
import org.bukkit.block.Block;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Ageable;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import pers.tany.yukinoaapi.interfacepart.item.IItem;
import pers.tany.yukinoaapi.interfacepart.nbt.INBT;
import pers.tany.yukinoaapi.interfacepart.serializer.ISerializer;

import java.sql.*;
import java.util.*;

/**
 * MySQL 数据存储实现
 * 使用 MySQL 数据库存储所有禁止项数据
 *
 * <p>v3.0.1 修复：
 * <ul>
 *   <li>修复在低版本 MySQL（5.6 及以下，或未启用 innodb_large_prefix 的环境）下
 *       因 utf8mb4 索引前缀超过 767 字节导致建表失败的问题</li>
 *   <li>将所有进入唯一索引的 world 列长度从 VARCHAR(255) 缩短为 VARCHAR(100)</li>
 *   <li>将所有进入唯一索引的 serial_number 列长度从 VARCHAR(50) 缩短为 VARCHAR(32)</li>
 *   <li>修改后索引前缀 = (100 + 32) * 4 = 528 字节，远低于 767 字节上限</li>
 *   <li>实际数据：MC 世界名一般不超过 50 字符；序列号由 IRandom.createRandomString(10)
 *       生成，只有 8-10 字符，新长度足够使用</li>
 * </ul>
 *
 * @author Tany天炎
 */
public class MySqlDataStorage implements DataStorage {

    /** 配置管理器 */
    private final ConfigManager configManager;

    /** HikariCP 数据源 */
    private HikariDataSource dataSource;

    /**
     * 构造函数
     */
    public MySqlDataStorage() {
        this.configManager = Banit.getConfigManager();
    }

    @Override
    public void initialize() {
        try {
            HikariConfig config = new HikariConfig();

            // 构建 JDBC URL
            String jdbcUrl = "jdbc:mysql://" + configManager.getMysqlHost() + ":" + configManager.getMysqlPort() +
                    "/" + configManager.getMysqlDatabase() + configManager.getMysqlParameters();

            config.setJdbcUrl(jdbcUrl);
            config.setUsername(configManager.getMysqlUsername());
            config.setPassword(configManager.getMysqlPassword());
            config.setMaximumPoolSize(configManager.getMysqlPoolSize());
            config.setMinimumIdle(configManager.getMysqlPoolMinIdle());
            config.setConnectionTimeout(configManager.getMysqlConnectionTimeout());
            config.setIdleTimeout(configManager.getMysqlIdleTimeout());
            config.setMaxLifetime(configManager.getMysqlMaxLifetime());

            // 其他优化配置
            config.addDataSourceProperty("cachePrepStmts", "true");
            config.addDataSourceProperty("prepStmtCacheSize", "250");
            config.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");

            dataSource = new HikariDataSource(config);

            // 创建表
            createTables();

            Banit.plugin.getLogger().info("MySQL connection established successfully!");
        } catch (Exception e) {
            Banit.plugin.getLogger().severe("Failed to initialize MySQL connection: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * 创建数据库表
     *
     * <p>修复说明：
     * 唯一索引列（world、serial_number）使用受限长度，避免在 utf8mb4 字符集下
     * 索引前缀超过 InnoDB 默认 767 字节上限。
     * <ul>
     *   <li>world: VARCHAR(100) - MC 世界名实际很少超过 50 字符</li>
     *   <li>serial_number: VARCHAR(32) - 实际由 createRandomString(10) 生成，仅 8-10 字符</li>
     *   <li>索引前缀大小 = (100 + 32) * 4 = 528 字节 &lt; 767 字节</li>
     * </ul>
     */
    private void createTables() {
        try (Connection conn = dataSource.getConnection(); Statement stmt = conn.createStatement()) {
            // 禁止物品表（world 和 serial_number 是唯一索引列，需控制长度）
            stmt.executeUpdate(
                    "CREATE TABLE IF NOT EXISTS banit_items (" +
                            "id INT AUTO_INCREMENT PRIMARY KEY," +
                            "world VARCHAR(100) NOT NULL," +
                            "serial_number VARCHAR(32) NOT NULL," +
                            "item_stack TEXT," +
                            "item_id VARCHAR(255)," +
                            "item_data SMALLINT," +
                            "distinguish INT DEFAULT 0," +
                            "clear_item BOOLEAN DEFAULT TRUE," +
                            "mode VARCHAR(10) DEFAULT 'a'," +
                            "reason TEXT," +
                            "nbt_key VARCHAR(255)," +
                            "nbt_value TEXT," +
                            "fuzzy_nbt TEXT," +
                            "UNIQUE KEY unique_item (world, serial_number)" +
                            ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4"
            );

            // 禁止方块表（world 和 serial_number 是唯一索引列，需控制长度）
            stmt.executeUpdate(
                    "CREATE TABLE IF NOT EXISTS banit_blocks (" +
                            "id INT AUTO_INCREMENT PRIMARY KEY," +
                            "world VARCHAR(100) NOT NULL," +
                            "serial_number VARCHAR(32) NOT NULL," +
                            "block_id VARCHAR(255)," +
                            "block_data TINYINT," +
                            "distinguish INT DEFAULT 0," +
                            "clear_block BOOLEAN DEFAULT TRUE," +
                            "mode VARCHAR(10) DEFAULT 'a'," +
                            "reason TEXT," +
                            "tile_id VARCHAR(255)," +
                            "sub_tile_name VARCHAR(255)," +
                            "UNIQUE KEY unique_block (world, serial_number)" +
                            ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4"
            );

            // 禁止实体表（world 和 serial_number 是唯一索引列，需控制长度）
            stmt.executeUpdate(
                    "CREATE TABLE IF NOT EXISTS banit_entities (" +
                            "id INT AUTO_INCREMENT PRIMARY KEY," +
                            "world VARCHAR(100) NOT NULL," +
                            "serial_number VARCHAR(32) NOT NULL," +
                            "entity_type VARCHAR(255)," +
                            "distinguish INT DEFAULT 0," +
                            "reason TEXT," +
                            "species VARCHAR(50)," +
                            "entity_name VARCHAR(255)," +
                            "UNIQUE KEY unique_entity (world, serial_number)" +
                            ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4"
            );

            // 禁止附魔表（world 和 serial_number 是唯一索引列，需控制长度）
            stmt.executeUpdate(
                    "CREATE TABLE IF NOT EXISTS banit_enchantments (" +
                            "id INT AUTO_INCREMENT PRIMARY KEY," +
                            "world VARCHAR(100) NOT NULL," +
                            "serial_number VARCHAR(32) NOT NULL," +
                            "ench_name VARCHAR(255)," +
                            "clear_ench BOOLEAN DEFAULT TRUE," +
                            "mode VARCHAR(10) DEFAULT 'a'," +
                            "level_condition VARCHAR(50) DEFAULT '>0'," +
                            "reason TEXT," +
                            "UNIQUE KEY unique_ench (world, serial_number)" +
                            ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4"
            );

            // 世界展示物品表（world 是唯一索引列，需控制长度）
            stmt.executeUpdate(
                    "CREATE TABLE IF NOT EXISTS banit_world_display (" +
                            "id INT AUTO_INCREMENT PRIMARY KEY," +
                            "world VARCHAR(100) NOT NULL UNIQUE," +
                            "display_item TEXT" +
                            ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4"
            );

        } catch (SQLException e) {
            Banit.plugin.getLogger().severe("Failed to create tables: " + e.getMessage());
            e.printStackTrace();
        }
    }

    @Override
    public void shutdown() {
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
            Banit.plugin.getLogger().info("MySQL connection closed.");
        }
    }

    /**
     * 获取数据库连接
     *
     * @return 数据库连接
     * @throws SQLException 如果获取连接失败
     */
    private Connection getConnection() throws SQLException {
        return dataSource.getConnection();
    }

    // ==================== 物品相关方法 ====================

    @Override
    public void addBannedItem(String world, String serialNumber, ItemStack item, String id, short durability, int distinguish, boolean clear, String mode, String reason, String key, String value, String fuzzyNBT) {
        String sql = "INSERT INTO banit_items (world, serial_number, item_stack, item_id, item_data, distinguish, clear_item, mode, reason, nbt_key, nbt_value, fuzzy_nbt) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) " +
                "ON DUPLICATE KEY UPDATE item_stack=VALUES(item_stack), item_id=VALUES(item_id), item_data=VALUES(item_data), " +
                "distinguish=VALUES(distinguish), clear_item=VALUES(clear_item), mode=VALUES(mode), reason=VALUES(reason), " +
                "nbt_key=VALUES(nbt_key), nbt_value=VALUES(nbt_value), fuzzy_nbt=VALUES(fuzzy_nbt)";

        try (Connection conn = getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, world);
            ps.setString(2, serialNumber);
            ps.setString(3, ISerializer.serializerItemStack(item, true));
            ps.setString(4, id);
            ps.setShort(5, durability);
            ps.setInt(6, distinguish);
            ps.setBoolean(7, clear);
            ps.setString(8, mode);
            ps.setString(9, reason);
            ps.setString(10, key);
            ps.setString(11, value);
            ps.setString(12, fuzzyNBT);
            ps.executeUpdate();
        } catch (SQLException e) {
            Banit.plugin.getLogger().severe("Failed to add banned item: " + e.getMessage());
        }
    }

    @Override
    public void removeBannedItem(String world, String serialNumber) {
        String sql = "DELETE FROM banit_items WHERE world = ? AND serial_number = ?";

        try (Connection conn = getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, world);
            ps.setString(2, serialNumber);
            ps.executeUpdate();
        } catch (SQLException e) {
            Banit.plugin.getLogger().severe("Failed to remove banned item: " + e.getMessage());
        }
    }

    @Override
    public String isBannedItemStack(Player player, String world, ItemStack itemStack, boolean ignorePermission) {
        if (IItem.isEmpty(itemStack)) {
            return null;
        }

        String sql = "SELECT * FROM banit_items WHERE world = ?";

        try (Connection conn = getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, world);
            ResultSet rs = ps.executeQuery();

            while (rs.next()) {
                String serialNumber = rs.getString("serial_number");

                // 权限检查
                if (!ignorePermission && player != null) {
                    if (player.hasPermission("bi.ignore." + serialNumber) || player.hasPermission("bi.op")) {
                        continue;
                    }
                }

                String id = rs.getString("item_id");
                short data = rs.getShort("item_data");
                int distinguish = rs.getInt("distinguish");
                String key = rs.getString("nbt_key");
                String value = rs.getString("nbt_value");
                String fuzzyNBT = rs.getString("fuzzy_nbt");

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
                    String storedItemData = rs.getString("item_stack");
                    ItemStack storedItem = ISerializer.deserializeItemStack(storedItemData);
                    if (storedItem != null && storedItem.isSimilar(itemStack)) {
                        return serialNumber;
                    }
                }
            }
        } catch (SQLException e) {
            Banit.plugin.getLogger().severe("Failed to check banned item: " + e.getMessage());
        }

        return null;
    }

    @Override
    public boolean isBannedItemClear(String world, String serialNumber) {
        String sql = "SELECT clear_item FROM banit_items WHERE world = ? AND serial_number = ?";

        try (Connection conn = getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, world);
            ps.setString(2, serialNumber);
            ResultSet rs = ps.executeQuery();

            if (rs.next()) {
                return rs.getBoolean("clear_item");
            }
        } catch (SQLException e) {
            Banit.plugin.getLogger().severe("Failed to get item clear status: " + e.getMessage());
        }

        return false;
    }

    @Override
    public String getBannedItemMode(String world, String serialNumber) {
        String sql = "SELECT mode FROM banit_items WHERE world = ? AND serial_number = ?";

        try (Connection conn = getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, world);
            ps.setString(2, serialNumber);
            ResultSet rs = ps.executeQuery();

            if (rs.next()) {
                return rs.getString("mode");
            }
        } catch (SQLException e) {
            Banit.plugin.getLogger().severe("Failed to get item mode: " + e.getMessage());
        }

        return "a";
    }

    @Override
    public String getBannedItemReason(String world, String serialNumber) {
        String sql = "SELECT reason FROM banit_items WHERE world = ? AND serial_number = ?";

        try (Connection conn = getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, world);
            ps.setString(2, serialNumber);
            ResultSet rs = ps.executeQuery();

            if (rs.next()) {
                return rs.getString("reason");
            }
        } catch (SQLException e) {
            Banit.plugin.getLogger().severe("Failed to get item reason: " + e.getMessage());
        }

        return configManager.getDefaultReason();
    }

    // ==================== 方块相关方法 ====================

    @Override
    public void addBannedBlock(String world, String serialNumber, Block block, int distinguish, boolean clear, String mode, String reason) {
        String tileID = null;
        String subTileName = null;

        try {
            NBTTileEntity nbtTileEntity = new NBTTileEntity(block.getState());
            if (nbtTileEntity.hasKey("id")) {
                tileID = nbtTileEntity.getString("id");
            }
            if (nbtTileEntity.hasKey("SubTileName")) {
                subTileName = nbtTileEntity.getString("SubTileName");
            }
        } catch (Exception ignored) {
        }

        addBannedBlockWithTile(world, serialNumber, block.getType().toString(), block.getData(), distinguish, clear, mode, reason, tileID, subTileName);
    }

    /**
     * 添加带有TileEntity信息的被禁止方块
     *
     * @param world        世界名称
     * @param serialNumber 序列号
     * @param id           方块ID
     * @param data         方块数据值
     * @param distinguish  区分方式
     * @param clear        是否清除
     * @param mode         模式
     * @param reason       原因
     * @param tileID       TileEntity ID
     * @param subTileName  子TileEntity名称
     */
    public void addBannedBlockWithTile(String world, String serialNumber, String id, byte data, int distinguish, boolean clear, String mode, String reason, String tileID, String subTileName) {
        String sql = "INSERT INTO banit_blocks (world, serial_number, block_id, block_data, distinguish, clear_block, mode, reason, tile_id, sub_tile_name) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?) " +
                "ON DUPLICATE KEY UPDATE block_id=VALUES(block_id), block_data=VALUES(block_data), " +
                "distinguish=VALUES(distinguish), clear_block=VALUES(clear_block), mode=VALUES(mode), reason=VALUES(reason), " +
                "tile_id=VALUES(tile_id), sub_tile_name=VALUES(sub_tile_name)";

        try (Connection conn = getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, world);
            ps.setString(2, serialNumber);
            ps.setString(3, id);
            ps.setByte(4, data);
            ps.setInt(5, distinguish);
            ps.setBoolean(6, clear);
            ps.setString(7, mode);
            ps.setString(8, reason);
            ps.setString(9, tileID);
            ps.setString(10, subTileName);
            ps.executeUpdate();
        } catch (SQLException e) {
            Banit.plugin.getLogger().severe("Failed to add banned block: " + e.getMessage());
        }
    }

    @Override
    public void addBannedBlockDirect(String world, String serialNumber, String id, byte data, int distinguish, boolean clear, String mode, String reason) {
        addBannedBlockWithTile(world, serialNumber, id, data, distinguish, clear, mode, reason, null, null);
    }

    @Override
    public void removeBannedBlock(String world, String serialNumber) {
        String sql = "DELETE FROM banit_blocks WHERE world = ? AND serial_number = ?";

        try (Connection conn = getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, world);
            ps.setString(2, serialNumber);
            ps.executeUpdate();
        } catch (SQLException e) {
            Banit.plugin.getLogger().severe("Failed to remove banned block: " + e.getMessage());
        }
    }

    @Override
    public String isBannedBlock(String world, Block block) {
        if (block == null) {
            return null;
        }

        String sql = "SELECT * FROM banit_blocks WHERE world = ?";

        try (Connection conn = getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, world);
            ResultSet rs = ps.executeQuery();

            while (rs.next()) {
                String serialNumber = rs.getString("serial_number");
                String id = rs.getString("block_id");
                byte data = rs.getByte("block_data");
                int distinguish = rs.getInt("distinguish");
                String tileID = rs.getString("tile_id");
                String subTileName = rs.getString("sub_tile_name");

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
        } catch (SQLException e) {
            Banit.plugin.getLogger().severe("Failed to check banned block: " + e.getMessage());
        }

        return null;
    }

    // ==================== 实体相关方法 ====================

    @Override
    public void addBannedEntity(String world, String serialNumber, Entity entity, int distinguish, String reason) {
        String species = null;
        String name = null;

        if (entity instanceof LivingEntity) {
            LivingEntity livingEntity = (LivingEntity) entity;
            if (livingEntity.getCustomName() != null) {
                name = livingEntity.getCustomName();
            }
        }

        if (entity instanceof Ageable) {
            Ageable ageable = (Ageable) entity;
            species = ageable.isAdult() ? "adult" : "baby";
        }

        addBannedEntityWithDetails(world, serialNumber, entity.getType().toString(), distinguish, reason, species, name);
    }

    /**
     * 添加带有详细信息的被禁止实体
     *
     * @param world        世界名称
     * @param serialNumber 序列号
     * @param entityType   实体类型
     * @param distinguish  区分方式
     * @param reason       原因
     * @param species      物种
     * @param name         名称
     */
    public void addBannedEntityWithDetails(String world, String serialNumber, String entityType, int distinguish, String reason, String species, String name) {
        String sql = "INSERT INTO banit_entities (world, serial_number, entity_type, distinguish, reason, species, entity_name) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?) " +
                "ON DUPLICATE KEY UPDATE entity_type=VALUES(entity_type), distinguish=VALUES(distinguish), " +
                "reason=VALUES(reason), species=VALUES(species), entity_name=VALUES(entity_name)";

        try (Connection conn = getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, world);
            ps.setString(2, serialNumber);
            ps.setString(3, entityType);
            ps.setInt(4, distinguish);
            ps.setString(5, reason);
            ps.setString(6, species);
            ps.setString(7, name);
            ps.executeUpdate();
        } catch (SQLException e) {
            Banit.plugin.getLogger().severe("Failed to add banned entity: " + e.getMessage());
        }
    }

    @Override
    public void addBannedEntityDirect(String world, String serialNumber, String entityType, int distinguish, String reason) {
        addBannedEntityWithDetails(world, serialNumber, entityType, distinguish, reason, null, null);
    }

    @Override
    public void removeBannedEntity(String world, String serialNumber) {
        String sql = "DELETE FROM banit_entities WHERE world = ? AND serial_number = ?";

        try (Connection conn = getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, world);
            ps.setString(2, serialNumber);
            ps.executeUpdate();
        } catch (SQLException e) {
            Banit.plugin.getLogger().severe("Failed to remove banned entity: " + e.getMessage());
        }
    }

    @Override
    public String isBannedEntity(String world, Entity entity) {
        if (entity == null || entity instanceof Player) {
            return null;
        }

        String sql = "SELECT * FROM banit_entities WHERE world = ?";

        try (Connection conn = getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, world);
            ResultSet rs = ps.executeQuery();

            while (rs.next()) {
                String serialNumber = rs.getString("serial_number");
                String entityType = rs.getString("entity_type");
                int distinguish = rs.getInt("distinguish");
                String name = rs.getString("entity_name");
                String species = rs.getString("species");

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
        } catch (SQLException e) {
            Banit.plugin.getLogger().severe("Failed to check banned entity: " + e.getMessage());
        }

        return null;
    }

    // ==================== 附魔相关方法 ====================

    @Override
    public void addBannedEnchantment(String world, String serialNumber, String enchName, boolean clear, String mode, String level, String reason) {
        String sql = "INSERT INTO banit_enchantments (world, serial_number, ench_name, clear_ench, mode, level_condition, reason) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?) " +
                "ON DUPLICATE KEY UPDATE ench_name=VALUES(ench_name), clear_ench=VALUES(clear_ench), " +
                "mode=VALUES(mode), level_condition=VALUES(level_condition), reason=VALUES(reason)";

        try (Connection conn = getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, world);
            ps.setString(2, serialNumber);
            ps.setString(3, enchName);
            ps.setBoolean(4, clear);
            ps.setString(5, mode);
            ps.setString(6, level);
            ps.setString(7, reason);
            ps.executeUpdate();
        } catch (SQLException e) {
            Banit.plugin.getLogger().severe("Failed to add banned enchantment: " + e.getMessage());
        }
    }

    @Override
    public void removeBannedEnchantment(String world, String serialNumber) {
        String sql = "DELETE FROM banit_enchantments WHERE world = ? AND serial_number = ?";

        try (Connection conn = getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, world);
            ps.setString(2, serialNumber);
            ps.executeUpdate();
        } catch (SQLException e) {
            Banit.plugin.getLogger().severe("Failed to remove banned enchantment: " + e.getMessage());
        }
    }

    @Override
    public String isBannedEnchantment(Player player, String world, ItemStack itemStack) {
        if (IItem.isEmpty(itemStack) || itemStack.getEnchantments().isEmpty()) {
            return null;
        }

        String sql = "SELECT * FROM banit_enchantments WHERE world = ?";

        try (Connection conn = getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, world);
            ResultSet rs = ps.executeQuery();

            while (rs.next()) {
                String serialNumber = rs.getString("serial_number");

                // 权限检查
                if (player != null) {
                    if (player.hasPermission("bi.ignore." + serialNumber) || player.hasPermission("bi.op")) {
                        continue;
                    }
                }

                String enchName = rs.getString("ench_name");
                String levelStr = rs.getString("level_condition");

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
        } catch (SQLException e) {
            Banit.plugin.getLogger().severe("Failed to check banned enchantment: " + e.getMessage());
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
        Set<String> worlds = new HashSet<>();

        String[] tables = {"banit_items", "banit_blocks", "banit_entities", "banit_enchantments"};

        for (String table : tables) {
            String sql = "SELECT DISTINCT world FROM " + table;

            try (Connection conn = getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
                ResultSet rs = ps.executeQuery();

                while (rs.next()) {
                    worlds.add(rs.getString("world"));
                }
            } catch (SQLException e) {
                Banit.plugin.getLogger().severe("Failed to get banned worlds: " + e.getMessage());
            }
        }

        return new ArrayList<>(worlds);
    }

    @Override
    public List<String> getBannedItems(String world) {
        List<String> items = new ArrayList<>();
        String sql = "SELECT serial_number FROM banit_items WHERE world = ?";

        try (Connection conn = getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, world);
            ResultSet rs = ps.executeQuery();

            while (rs.next()) {
                items.add(rs.getString("serial_number"));
            }
        } catch (SQLException e) {
            Banit.plugin.getLogger().severe("Failed to get banned items: " + e.getMessage());
        }

        return items;
    }

    @Override
    public List<String> getBannedBlocks(String world) {
        List<String> blocks = new ArrayList<>();
        String sql = "SELECT serial_number FROM banit_blocks WHERE world = ?";

        try (Connection conn = getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, world);
            ResultSet rs = ps.executeQuery();

            while (rs.next()) {
                blocks.add(rs.getString("serial_number"));
            }
        } catch (SQLException e) {
            Banit.plugin.getLogger().severe("Failed to get banned blocks: " + e.getMessage());
        }

        return blocks;
    }

    @Override
    public List<String> getBannedEntities(String world) {
        List<String> entities = new ArrayList<>();
        String sql = "SELECT serial_number FROM banit_entities WHERE world = ?";

        try (Connection conn = getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, world);
            ResultSet rs = ps.executeQuery();

            while (rs.next()) {
                entities.add(rs.getString("serial_number"));
            }
        } catch (SQLException e) {
            Banit.plugin.getLogger().severe("Failed to get banned entities: " + e.getMessage());
        }

        return entities;
    }

    @Override
    public List<String> getBannedEnchantments(String world) {
        List<String> enchantments = new ArrayList<>();
        String sql = "SELECT serial_number FROM banit_enchantments WHERE world = ?";

        try (Connection conn = getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, world);
            ResultSet rs = ps.executeQuery();

            while (rs.next()) {
                enchantments.add(rs.getString("serial_number"));
            }
        } catch (SQLException e) {
            Banit.plugin.getLogger().severe("Failed to get banned enchantments: " + e.getMessage());
        }

        return enchantments;
    }

    // ==================== 清除方法 ====================

    @Override
    public void clearWorld(String world) {
        String[] tables = {"banit_items", "banit_blocks", "banit_entities", "banit_enchantments", "banit_world_display"};

        for (String table : tables) {
            String sql = "DELETE FROM " + table + " WHERE world = ?";

            try (Connection conn = getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, world);
                ps.executeUpdate();
            } catch (SQLException e) {
                Banit.plugin.getLogger().severe("Failed to clear world " + world + ": " + e.getMessage());
            }
        }
    }

    @Override
    public void clearAll() {
        String[] tables = {"banit_items", "banit_blocks", "banit_entities", "banit_enchantments", "banit_world_display"};

        for (String table : tables) {
            String sql = "TRUNCATE TABLE " + table;

            try (Connection conn = getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.executeUpdate();
            } catch (SQLException e) {
                Banit.plugin.getLogger().severe("Failed to clear all data: " + e.getMessage());
            }
        }
    }

    // ==================== 其他方法 ====================

    @Override
    public String getFlags(String serialNumber) {
        // 检查物品
        String sql = "SELECT * FROM banit_items WHERE serial_number = ?";
        try (Connection conn = getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, serialNumber);
            ResultSet rs = ps.executeQuery();

            if (rs.next()) {
                String world = rs.getString("world");
                String id = rs.getString("item_id");
                int data = rs.getInt("item_data");
                int distinguish = rs.getInt("distinguish");
                String key = rs.getString("nbt_key");
                String value = rs.getString("nbt_value");
                String fuzzyNBT = rs.getString("fuzzy_nbt");

                // 统一处理 null 值
                String keyStr = (key != null) ? key : "";
                String valueStr = (value != null) ? value : "";
                String fuzzyNBTStr = (fuzzyNBT != null) ? fuzzyNBT : "";

                return "item:" + id + ":" + data + ":" + distinguish + ":" + keyStr + ":" + valueStr + ":" + fuzzyNBTStr + ":" + world;
            }
        } catch (SQLException e) {
            Banit.plugin.getLogger().severe("Failed to get item flags: " + e.getMessage());
        }

        // 检查方块
        sql = "SELECT * FROM banit_blocks WHERE serial_number = ?";
        try (Connection conn = getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, serialNumber);
            ResultSet rs = ps.executeQuery();

            if (rs.next()) {
                String world = rs.getString("world");
                String id = rs.getString("block_id");
                int data = rs.getInt("block_data");
                int distinguish = rs.getInt("distinguish");
                String tileID = rs.getString("tile_id");
                String subTileName = rs.getString("sub_tile_name");

                // 统一处理 null 值
                String tileIDStr = (tileID != null) ? tileID : "";
                String subTileNameStr = (subTileName != null) ? subTileName : "";

                return "block:" + id + ":" + data + ":" + distinguish + ":" + tileIDStr + ":" + subTileNameStr + ":" + world;
            }
        } catch (SQLException e) {
            Banit.plugin.getLogger().severe("Failed to get block flags: " + e.getMessage());
        }

        // 检查实体
        sql = "SELECT * FROM banit_entities WHERE serial_number = ?";
        try (Connection conn = getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, serialNumber);
            ResultSet rs = ps.executeQuery();

            if (rs.next()) {
                String world = rs.getString("world");
                String entityType = rs.getString("entity_type");
                int distinguish = rs.getInt("distinguish");
                String name = rs.getString("entity_name");
                String species = rs.getString("species");

                // 统一处理 null 值
                String nameStr = (name != null) ? name : "";
                String speciesStr = (species != null) ? species : "";

                return "entity:" + entityType + ":" + distinguish + ":" + nameStr + ":" + speciesStr + ":" + world;
            }
        } catch (SQLException e) {
            Banit.plugin.getLogger().severe("Failed to get entity flags: " + e.getMessage());
        }

        // 检查附魔
        sql = "SELECT * FROM banit_enchantments WHERE serial_number = ?";
        try (Connection conn = getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, serialNumber);
            ResultSet rs = ps.executeQuery();

            if (rs.next()) {
                String world = rs.getString("world");
                String enchName = rs.getString("ench_name");
                String level = rs.getString("level_condition");

                return "ench:" + enchName + ":" + level + ":" + world;
            }
        } catch (SQLException e) {
            Banit.plugin.getLogger().severe("Failed to get enchantment flags: " + e.getMessage());
        }

        return null;
    }

    @Override
    public Map<String, Map<String, Object>> getAllBannedInfo(String world) {
        Map<String, Map<String, Object>> result = new HashMap<>();

        // 物品
        String sql = "SELECT * FROM banit_items WHERE world = ?";
        try (Connection conn = getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, world);
            ResultSet rs = ps.executeQuery();

            while (rs.next()) {
                Map<String, Object> info = new HashMap<>();
                String serialNumber = rs.getString("serial_number");

                info.put("Type", "Item");
                info.put("ID", rs.getString("item_id"));
                info.put("Data", rs.getInt("item_data"));
                info.put("Distinguish", rs.getInt("distinguish"));
                info.put("Clear", rs.getBoolean("clear_item"));
                info.put("Mode", rs.getString("mode"));
                info.put("Reason", rs.getString("reason"));
                info.put("ItemStack", rs.getString("item_stack"));

                result.put(serialNumber, info);
            }
        } catch (SQLException e) {
            Banit.plugin.getLogger().severe("Failed to get all item info: " + e.getMessage());
        }

        // 方块
        sql = "SELECT * FROM banit_blocks WHERE world = ?";
        try (Connection conn = getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, world);
            ResultSet rs = ps.executeQuery();

            while (rs.next()) {
                Map<String, Object> info = new HashMap<>();
                String serialNumber = rs.getString("serial_number");

                info.put("Type", "Block");
                info.put("ID", rs.getString("block_id"));
                info.put("Data", rs.getInt("block_data"));
                info.put("Distinguish", rs.getInt("distinguish"));
                info.put("Clear", rs.getBoolean("clear_block"));
                info.put("Mode", rs.getString("mode"));
                info.put("Reason", rs.getString("reason"));

                result.put(serialNumber, info);
            }
        } catch (SQLException e) {
            Banit.plugin.getLogger().severe("Failed to get all block info: " + e.getMessage());
        }

        // 实体
        sql = "SELECT * FROM banit_entities WHERE world = ?";
        try (Connection conn = getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, world);
            ResultSet rs = ps.executeQuery();

            while (rs.next()) {
                Map<String, Object> info = new HashMap<>();
                String serialNumber = rs.getString("serial_number");

                info.put("Type", "Entity");
                info.put("EntityType", rs.getString("entity_type"));
                info.put("Distinguish", rs.getInt("distinguish"));
                info.put("Reason", rs.getString("reason"));

                result.put(serialNumber, info);
            }
        } catch (SQLException e) {
            Banit.plugin.getLogger().severe("Failed to get all entity info: " + e.getMessage());
        }

        // 附魔
        sql = "SELECT * FROM banit_enchantments WHERE world = ?";
        try (Connection conn = getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, world);
            ResultSet rs = ps.executeQuery();

            while (rs.next()) {
                Map<String, Object> info = new HashMap<>();
                String serialNumber = rs.getString("serial_number");

                info.put("Type", "Ench");
                info.put("EnchName", rs.getString("ench_name"));
                info.put("Clear", rs.getBoolean("clear_ench"));
                info.put("Mode", rs.getString("mode"));
                info.put("Level", rs.getString("level_condition"));
                info.put("Reason", rs.getString("reason"));

                result.put(serialNumber, info);
            }
        } catch (SQLException e) {
            Banit.plugin.getLogger().severe("Failed to get all enchantment info: " + e.getMessage());
        }

        return result;
    }

    @Override
    public void setWorldDisplayItem(String world, ItemStack item) {
        String sql = "INSERT INTO banit_world_display (world, display_item) VALUES (?, ?) " +
                "ON DUPLICATE KEY UPDATE display_item = VALUES(display_item)";

        try (Connection conn = getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, world);
            ps.setString(2, ISerializer.serializerItemStack(item, true));
            ps.executeUpdate();
        } catch (SQLException e) {
            Banit.plugin.getLogger().severe("Failed to set world display item: " + e.getMessage());
        }
    }

    @Override
    public ItemStack getWorldDisplayItem(String world) {
        String sql = "SELECT display_item FROM banit_world_display WHERE world = ?";

        try (Connection conn = getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, world);
            ResultSet rs = ps.executeQuery();

            if (rs.next()) {
                String itemData = rs.getString("display_item");
                if (itemData != null) {
                    return ISerializer.deserializeItemStack(itemData);
                }
            }
        } catch (SQLException e) {
            Banit.plugin.getLogger().severe("Failed to get world display item: " + e.getMessage());
        }

        return null;
    }
}