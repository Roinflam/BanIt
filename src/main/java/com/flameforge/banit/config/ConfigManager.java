// ConfigManager.java

package com.flameforge.banit.config;

import com.flameforge.banit.Banit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import pers.tany.yukinoaapi.interfacepart.configuration.IConfig;

/**
 * 配置管理器
 * 负责加载、缓存和提供配置数据
 *
 * @author Tany天炎
 */
public class ConfigManager {

    /** 插件实例 */
    private final Banit plugin;

    /** 配置文件对象 */
    private YamlConfiguration config;

    // ==================== MySQL 配置 ====================

    /** 是否启用 MySQL */
    private boolean mysqlEnabled;

    /** MySQL 主机地址 */
    private String mysqlHost;

    /** MySQL 端口 */
    private int mysqlPort;

    /** MySQL 用户名 */
    private String mysqlUsername;

    /** MySQL 密码 */
    private String mysqlPassword;

    /** MySQL 数据库名 */
    private String mysqlDatabase;

    /** MySQL 连接参数 */
    private String mysqlParameters;

    /** 连接池最大连接数 */
    private int mysqlPoolSize;

    /** 连接池最小空闲连接数 */
    private int mysqlPoolMinIdle;

    /** 连接超时时间 */
    private long mysqlConnectionTimeout;

    /** 空闲超时时间 */
    private long mysqlIdleTimeout;

    /** 连接最大生存时间 */
    private long mysqlMaxLifetime;

    // ==================== 通用配置 ====================

    /** 全局世界名 */
    private String overAll;

    /** NBT 显示模式 */
    private boolean canBeUsedNBT;

    /** 进服是否检测背包 */
    private boolean checkBackPack;

    /** 是否启用定时检测任务 */
    private boolean checkBackPackTask;

    /** 检测间隔时间（秒） */
    private int checkTime;

    /** 是否启用重复检测 */
    private boolean hasFlag;

    /** 是否启用优化模式 */
    private boolean optimization;

    /** 是否启用物品界面禁用检测 */
    private boolean inventoryCheck;

    /** 物品被禁用后是否扔出 */
    private boolean dropItem;

    // ==================== 区块检查配置 ====================

    /** 是否启用区块方块检查 */
    private boolean blockCheckEnable;

    /** 区块检查冷却时间 */
    private long blockCheckChunkCooldown;

    /** 是否每个区块只检测一次 */
    private boolean blockCheckCheckOncePerChunk;

    /** 世界最小高度 */
    private int blockCheckWorldMinHeight;

    /** 世界最大高度 */
    private int blockCheckWorldMaxHeight;

    /** 检查触发概率 */
    private int blockCheckCheckChance;

    /** 是否总是检查 */
    private boolean blockCheckAlwaysCheck;

    /** 是否输出调试信息 */
    private boolean blockCheckDebug;

    /**
     * 构造函数
     *
     * @param plugin 插件实例
     */
    public ConfigManager(Banit plugin) {
        this.plugin = plugin;
    }

    /**
     * 加载配置文件
     */
    public void loadConfig() {
        config = IConfig.loadConfig(plugin, "", "config");

        if (config == null) {
            plugin.sendError(Banit.getLanguageManager().getMessage("error.config-load-failed"));
            config = new YamlConfiguration();
            return;
        }

        // 加载 MySQL 配置
        loadMysqlConfig();

        // 加载通用配置
        loadGeneralConfig();

        // 加载区块检查配置
        loadBlockCheckConfig();
    }

    /**
     * 加载 MySQL 配置
     */
    private void loadMysqlConfig() {
        ConfigurationSection mysqlSection = config.getConfigurationSection("MySQL");
        if (mysqlSection == null) {
            this.mysqlEnabled = false;
            return;
        }

        this.mysqlEnabled = mysqlSection.getBoolean("Enable", false);
        this.mysqlHost = mysqlSection.getString("Host", "localhost");
        this.mysqlPort = mysqlSection.getInt("Port", 3306);
        this.mysqlUsername = mysqlSection.getString("Username", "root");
        this.mysqlPassword = mysqlSection.getString("Password", "password");
        this.mysqlDatabase = mysqlSection.getString("Database", "banit");
        this.mysqlParameters = mysqlSection.getString("Parameters", "?useSSL=false&autoReconnect=true&useUnicode=true&characterEncoding=UTF-8");

        // 连接池配置
        ConfigurationSection poolSection = mysqlSection.getConfigurationSection("Pool");
        if (poolSection != null) {
            this.mysqlPoolSize = poolSection.getInt("Size", 10);
            this.mysqlPoolMinIdle = poolSection.getInt("MinIdle", 2);
            this.mysqlConnectionTimeout = poolSection.getLong("ConnectionTimeout", 30000);
            this.mysqlIdleTimeout = poolSection.getLong("IdleTimeout", 600000);
            this.mysqlMaxLifetime = poolSection.getLong("MaxLifetime", 1800000);
        } else {
            this.mysqlPoolSize = 10;
            this.mysqlPoolMinIdle = 2;
            this.mysqlConnectionTimeout = 30000;
            this.mysqlIdleTimeout = 600000;
            this.mysqlMaxLifetime = 1800000;
        }
    }

    /**
     * 加载通用配置
     */
    private void loadGeneralConfig() {
        this.overAll = config.getString("OverAll", "OverAll");
        this.canBeUsedNBT = config.getBoolean("CanBeUsedNBT", true);
        this.checkBackPack = config.getBoolean("CheckBackPack", true);
        this.checkBackPackTask = config.getBoolean("CheckBackPackTask", false);
        this.checkTime = config.getInt("CheckTime", 10);
        this.hasFlag = config.getBoolean("HasFlag", true);
        this.optimization = config.getBoolean("Optimization", true);
        this.inventoryCheck = config.getBoolean("InventoryCheck", true);
        this.dropItem = config.getBoolean("DropItem", false);
    }

    /**
     * 加载区块检查配置
     */
    private void loadBlockCheckConfig() {
        ConfigurationSection blockCheckSection = config.getConfigurationSection("BlockCheck");
        if (blockCheckSection == null) {
            this.blockCheckEnable = false;
            return;
        }

        this.blockCheckEnable = blockCheckSection.getBoolean("Enable", true);
        this.blockCheckChunkCooldown = blockCheckSection.getLong("ChunkCooldown", 30000);
        this.blockCheckCheckOncePerChunk = blockCheckSection.getBoolean("CheckOncePerChunk", false);
        this.blockCheckWorldMinHeight = blockCheckSection.getInt("WorldMinHeight", 0);
        this.blockCheckWorldMaxHeight = blockCheckSection.getInt("WorldMaxHeight", 256);
        this.blockCheckCheckChance = blockCheckSection.getInt("CheckChance", 10);
        this.blockCheckAlwaysCheck = blockCheckSection.getBoolean("AlwaysCheck", false);
        this.blockCheckDebug = blockCheckSection.getBoolean("Debug", false);
    }

    /**
     * 获取原始配置对象
     *
     * @return 配置对象
     */
    public YamlConfiguration getConfig() {
        return config;
    }

    // ==================== MySQL Getter 方法 ====================

    /**
     * 是否启用 MySQL
     *
     * @return 是否启用
     */
    public boolean isMysqlEnabled() {
        return mysqlEnabled;
    }

    /**
     * 获取 MySQL 主机地址
     *
     * @return 主机地址
     */
    public String getMysqlHost() {
        return mysqlHost;
    }

    /**
     * 获取 MySQL 端口
     *
     * @return 端口号
     */
    public int getMysqlPort() {
        return mysqlPort;
    }

    /**
     * 获取 MySQL 用户名
     *
     * @return 用户名
     */
    public String getMysqlUsername() {
        return mysqlUsername;
    }

    /**
     * 获取 MySQL 密码
     *
     * @return 密码
     */
    public String getMysqlPassword() {
        return mysqlPassword;
    }

    /**
     * 获取 MySQL 数据库名
     *
     * @return 数据库名
     */
    public String getMysqlDatabase() {
        return mysqlDatabase;
    }

    /**
     * 获取 MySQL 连接参数
     *
     * @return 连接参数
     */
    public String getMysqlParameters() {
        return mysqlParameters;
    }

    /**
     * 获取连接池最大连接数
     *
     * @return 最大连接数
     */
    public int getMysqlPoolSize() {
        return mysqlPoolSize;
    }

    /**
     * 获取连接池最小空闲连接数
     *
     * @return 最小空闲连接数
     */
    public int getMysqlPoolMinIdle() {
        return mysqlPoolMinIdle;
    }

    /**
     * 获取连接超时时间
     *
     * @return 连接超时时间（毫秒）
     */
    public long getMysqlConnectionTimeout() {
        return mysqlConnectionTimeout;
    }

    /**
     * 获取空闲超时时间
     *
     * @return 空闲超时时间（毫秒）
     */
    public long getMysqlIdleTimeout() {
        return mysqlIdleTimeout;
    }

    /**
     * 获取连接最大生存时间
     *
     * @return 最大生存时间（毫秒）
     */
    public long getMysqlMaxLifetime() {
        return mysqlMaxLifetime;
    }

    // ==================== 通用配置 Getter 方法 ====================

    /**
     * 获取全局世界名
     *
     * @return 全局世界名
     */
    public String getOverAll() {
        return overAll;
    }

    /**
     * 获取默认禁用理由（从语言文件）
     *
     * @return 默认禁用理由
     */
    public String getDefaultReason() {
        return Banit.getLanguageManager().getMessage("config.default-reason");
    }

    /**
     * 是否允许使用不支持的NBT类型
     *
     * @return 是否允许
     */
    public boolean isCanBeUsedNBT() {
        return canBeUsedNBT;
    }

    /**
     * 是否启用登录时背包检查
     *
     * @return 是否启用
     */
    public boolean isCheckBackPack() {
        return checkBackPack;
    }

    /**
     * 是否启用定时背包检查任务
     *
     * @return 是否启用
     */
    public boolean isCheckBackPackTask() {
        return checkBackPackTask;
    }

    /**
     * 获取检测间隔时间
     *
     * @return 检测间隔时间（秒）
     */
    public int getCheckTime() {
        return checkTime;
    }

    /**
     * 是否启用重复检测
     *
     * @return 是否启用
     */
    public boolean isHasFlag() {
        return hasFlag;
    }

    /**
     * 是否启用优化模式
     *
     * @return 是否启用
     */
    public boolean isOptimization() {
        return optimization;
    }

    /**
     * 是否启用物品界面禁用检测
     *
     * @return 是否启用
     */
    public boolean isInventoryCheck() {
        return inventoryCheck;
    }

    /**
     * 物品被禁用后是否扔出
     *
     * @return 是否扔出
     */
    public boolean isDropItem() {
        return dropItem;
    }

    // ==================== 区块检查配置 Getter 方法 ====================

    /**
     * 是否启用区块方块检查
     *
     * @return 是否启用
     */
    public boolean isBlockCheckEnable() {
        return blockCheckEnable;
    }

    /**
     * 获取区块检查冷却时间
     *
     * @return 冷却时间（毫秒）
     */
    public long getBlockCheckChunkCooldown() {
        return blockCheckChunkCooldown;
    }

    /**
     * 是否每个区块只检测一次
     *
     * @return 是否只检测一次
     */
    public boolean isBlockCheckCheckOncePerChunk() {
        return blockCheckCheckOncePerChunk;
    }

    /**
     * 获取世界最小高度
     *
     * @return 最小高度
     */
    public int getBlockCheckWorldMinHeight() {
        return blockCheckWorldMinHeight;
    }

    /**
     * 获取世界最大高度
     *
     * @return 最大高度
     */
    public int getBlockCheckWorldMaxHeight() {
        return blockCheckWorldMaxHeight;
    }

    /**
     * 获取检查触发概率
     *
     * @return 触发概率（百分比）
     */
    public int getBlockCheckCheckChance() {
        return blockCheckCheckChance;
    }

    /**
     * 是否总是检查
     *
     * @return 是否总是检查
     */
    public boolean isBlockCheckAlwaysCheck() {
        return blockCheckAlwaysCheck;
    }

    /**
     * 是否输出调试信息
     *
     * @return 是否输出调试
     */
    public boolean isBlockCheckDebug() {
        return blockCheckDebug;
    }
}