package com.flameforge.banit;

import com.flameforge.banit.bstats.Metrics;
import com.flameforge.banit.command.BanItTabCompleter;
import com.flameforge.banit.command.Commands;
import com.flameforge.banit.config.DataManager;
import com.flameforge.banit.listenevent.ArmorListener;
import com.flameforge.banit.listenevent.Events;
import com.flameforge.banit.storage.DataStorage;
import com.flameforge.banit.storage.YamlDataStorage;
import com.flameforge.banit.task.CheckArmorTask;
import com.flameforge.banit.task.CheckHandTask;
import com.flameforge.banit.task.CheckTask;
import com.flameforge.banit.config.ConfigManager;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import com.flameforge.banit.config.LanguageManager;
import com.flameforge.banit.storage.MySqlDataStorage;
import pers.tany.yukinoaapi.interfacepart.configuration.IConfig;
import pers.tany.yukinoaapi.interfacepart.register.IRegister;

/**
 * BanIt 插件主类
 * 功能：多方面禁用物品、方块、实体、附魔
 *
 * @author Tany天炎
 * @version 2.0.0
 */
public class Banit extends JavaPlugin {

    /** 插件实例 */
    public static Plugin plugin = null;

    /** 数据配置文件（YAML模式使用） */
    public static YamlConfiguration data;

    /** 数据管理器（缓存层） */
    private static DataManager dataManager;

    /** 配置管理器 */
    private static ConfigManager configManager;

    /** 语言管理器 */
    private static LanguageManager languageManager;

    /** 数据 */
    private static DataStorage dataStorage;

    /** bStats 统计对象 */
    private Metrics metrics;

    /** bStats 服务ID */
    private static final int BSTATS_PLUGIN_ID = 29139;

    /**
     * 插件启用时执行
     */
    @Override
    public void onEnable() {
        plugin = this;

        // 初始化语言管理器（最先初始化，以便其他组件使用）
        languageManager = new LanguageManager(this);
        languageManager.loadLanguage();

        // 初始化配置管理器
        configManager = new ConfigManager(this);
        configManager.loadConfig();

        // 加载数据文件
        IConfig.createResource(this, "", "data.yml", false);
        data = IConfig.loadConfig(this, "", "data");

        // 初始化数据存储（先创建底层存储，再用 DataManager 包装）

        if (configManager.isMysqlEnabled()) {
            dataStorage = new MySqlDataStorage();
            sendMessage(languageManager.getMessage("plugin.mysql-enabled"));
        } else {
            dataStorage = new YamlDataStorage();
            sendMessage(languageManager.getMessage("plugin.yaml-mode"));
        }

        // 使用 DataManager 作为缓存层
        dataManager = new DataManager(this, dataStorage);
        dataManager.initialize();

        // 注册事件和命令
        IRegister.registerEvents(this, new Events());
        // 注册装备槽禁用监听器（实现“穿上即掉落”的即时拦截）
        IRegister.registerEvents(this, new ArmorListener());
        IRegister.registerCommands(this, "BanIt", new Commands(this));
        getCommand("BanIt").setTabCompleter(new BanItTabCompleter());

        // 启动定时任务
        if (configManager.isCheckBackPackTask()) {
            long checkTime = configManager.getCheckTime() * 20L;
            new CheckTask().runTaskTimer(plugin, checkTime, checkTime);
            new CheckHandTask().runTaskTimer(plugin, checkTime, checkTime);
            // 装备槽定时检查任务（作为事件监听的兜底机制）
            new CheckArmorTask().runTaskTimer(plugin, checkTime, checkTime);
        }

        // 初始化 bStats 统计
        metrics = new Metrics(this, BSTATS_PLUGIN_ID);

        // 发送启用消息
        sendMessage(languageManager.getMessage("plugin.enabled"));
    }

    /**
     * 插件禁用时执行
     */
    @Override
    public void onDisable() {
        // 关闭数据管理器
        if (dataManager != null) {
            dataManager.shutdown();
        }

        // 关闭 bStats 统计
        if (metrics != null) {
            metrics.shutdown();
        }

        // 发送禁用消息
        if (languageManager != null) {
            sendMessage(languageManager.getMessage("plugin.disabled"));
        }
    }

    /**
     * 发送控制台消息
     *
     * @param message 消息内容
     */
    public void sendMessage(String message) {
        Bukkit.getConsoleSender().sendMessage(message);
    }

    /**
     * 发送警告消息
     *
     * @param message 消息内容
     */
    public void sendWarning(String message) {
        if (languageManager != null) {
            String prefix = languageManager.getMessage("console.warning-prefix");
            Bukkit.getConsoleSender().sendMessage(prefix + message);
        } else {
            Bukkit.getConsoleSender().sendMessage("§e[WARNING] " + message);
        }
    }

    /**
     * 发送错误消息
     *
     * @param message 消息内容
     */
    public void sendError(String message) {
        if (languageManager != null) {
            String prefix = languageManager.getMessage("console.error-prefix");
            Bukkit.getConsoleSender().sendMessage(prefix + message);
        } else {
            Bukkit.getConsoleSender().sendMessage("§c[ERROR] " + message);
        }
    }

    // ==================== Getter 方法 ====================

    /**
     * 获取数据管理器（推荐使用，带缓存）
     *
     * @return 数据管理器
     */
    public static DataManager getDataManager() {
        return dataManager;
    }

    /**
     * 获取数据存储实例（向后兼容，内部使用 DataManager）
     *
     * @return 数据存储实例
     */
    public static DataStorage getDataStorage() {
        return dataStorage;
    }

    /**
     * 获取配置管理器
     *
     * @return 配置管理器
     */
    public static ConfigManager getConfigManager() {
        return configManager;
    }

    /**
     * 获取语言管理器
     *
     * @return 语言管理器
     */
    public static LanguageManager getLanguageManager() {
        return languageManager;
    }

    /**
     * 获取 bStats 统计对象
     *
     * @return bStats 统计对象
     */
    public Metrics getMetrics() {
        return metrics;
    }
}
