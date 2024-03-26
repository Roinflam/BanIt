package pers.tany.banit;

import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import pers.tany.banit.command.Commands;
import pers.tany.banit.listenevent.Events;
import pers.tany.banit.task.CheckHandTask;
import pers.tany.banit.task.CheckTask;
import pers.tany.yukinoaapi.interfacepart.configuration.IConfig;
import pers.tany.yukinoaapi.interfacepart.register.IRegister;


public class Main extends JavaPlugin {
    public static Plugin plugin = null;
    public static YamlConfiguration config;
    public static YamlConfiguration data;
    public static YamlConfiguration message;

    @Override
    public void onDisable() {
        Bukkit.getConsoleSender().sendMessage("§7「§fBanIt§7」§c已卸载");
    }

    @Override
    public void onEnable() {
        plugin = this;

        Bukkit.getConsoleSender().sendMessage("§7「§fBanIt§7」§a已启用");

        IConfig.createResource(this, "", "config.yml", false);
        IConfig.createResource(this, "", "data.yml", false);
        IConfig.createResource(this, "", "message.yml", false);

        config = IConfig.loadConfig(this, "", "config");
        data = IConfig.loadConfig(this, "", "data");
        message = IConfig.loadConfig(this, "", "message");

        IRegister.registerEvents(this, new Events());
        IRegister.registerCommands(this, "BanIt", new Commands());

        if (config.getBoolean("CheckBackPackTask")) {
            new CheckTask().runTaskLaterAsynchronously(plugin, config.getInt("CheckTime") * 20L);
        }
        new CheckHandTask().runTaskTimerAsynchronously(plugin, 10L, 10L);

    }
}
