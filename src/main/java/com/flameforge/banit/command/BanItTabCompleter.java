package com.flameforge.banit.command;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.enchantments.Enchantment;
import com.flameforge.banit.Banit;
import pers.tany.yukinoaapi.interfacepart.enchantment.IEnchantment;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * BanIt 插件的 Tab 补全器
 * 提供命令的智能补全功能
 *
 * @author Tany天炎
 */
public class BanItTabCompleter implements TabCompleter {

    /**
     * 处理 Tab 补全请求
     */
    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            // 主命令补全 (bi <tab>)
            completions.addAll(Arrays.asList(
                    "reload", "clear", "nbt", "ench", "list", "set",
                    "add", "adds", "addblock", "addentity", "addench",
                    "check", "migrateToMySQL"
            ));
        } else if (args.length == 2) {
            // 第二个参数补全 (bi add <tab>)
            switch (args[0].toLowerCase()) {
                case "clear":
                case "list":
                case "set":
                    // 这些命令需要世界名
                    completions.addAll(getWorldNames());
                    break;
                case "addench":
                    // 附魔命令需要附魔名
                    completions.addAll(getEnchantmentNames());
                    break;
                case "add":
                case "adds":
                case "check":
                case "addblock":
                case "addentity":
                    // 这些命令从第二个参数就开始接受参数
                    completions.addAll(getParameterNames(args[0].toLowerCase()));
                    break;
            }
        } else if (args.length > 2) {
            // 第三个及以后的参数补全
            String lastArg = args[args.length - 1];

            if (lastArg.contains(":")) {
                // 已经输入冒号，补全值
                String paramName = lastArg.split(":")[0].toLowerCase();
                completions.addAll(getParameterValueCompletions(args[0].toLowerCase(), paramName, lastArg));
            } else {
                // 还没输入冒号，补全参数名
                completions.addAll(getParameterNames(args[0].toLowerCase()));
            }
        }

        // 过滤已输入的部分
        String lastArg = args[args.length - 1].toLowerCase();
        return completions.stream()
                .filter(completion -> completion.toLowerCase().startsWith(lastArg))
                .collect(Collectors.toList());
    }

    /**
     * 获取所有世界名称
     */
    private List<String> getWorldNames() {
        List<String> worldNames = new ArrayList<>();
        for (World world : Bukkit.getWorlds()) {
            worldNames.add(world.getName());
        }
        worldNames.add(Banit.getConfigManager().getOverAll());
        return worldNames;
    }

    /**
     * 获取所有附魔名称（本地化名称）
     */
    private List<String> getEnchantmentNames() {
        List<String> enchantments = new ArrayList<>();
        for (Enchantment ench : Enchantment.values()) {
            // 添加本地化名称
            enchantments.add(IEnchantment.getName(ench));
        }
        return enchantments;
    }

    /**
     * 根据命令类型获取参数名列表
     */
    private List<String> getParameterNames(String commandType) {
        switch (commandType) {
            case "add":
            case "adds":
            case "check":
                return getItemParameterNames();
            case "addblock":
                return getBlockParameterNames();
            case "addentity":
                return getEntityParameterNames();
            case "addench":
                return getEnchantmentParameterNames();
            default:
                return new ArrayList<>();
        }
    }

    /**
     * 获取物品参数名列表（不含值）
     */
    private List<String> getItemParameterNames() {
        return Arrays.asList(
                "world:",
                "clear:",
                "use:",
                "reason:",
                "range:",
                "key:",
                "value:",
                "fuzzynbt:"
        );
    }

    /**
     * 获取方块参数名列表（不含值）
     */
    private List<String> getBlockParameterNames() {
        return Arrays.asList(
                "world:",
                "clear:",
                "use:",
                "reason:",
                "range:"
        );
    }

    /**
     * 获取实体参数名列表（不含值）
     */
    private List<String> getEntityParameterNames() {
        return Arrays.asList(
                "world:",
                "reason:",
                "range:"
        );
    }

    /**
     * 获取附魔参数名列表（不含值）
     */
    private List<String> getEnchantmentParameterNames() {
        return Arrays.asList(
                "world:",
                "clear:",
                "use:",
                "reason:",
                "level:"
        );
    }

    /**
     * 获取参数值的补全建议
     */
    private List<String> getParameterValueCompletions(String commandType, String paramName, String currentInput) {
        List<String> completions = new ArrayList<>();

        switch (paramName) {
            case "world":
                // 世界名称补全
                for (String worldName : getWorldNames()) {
                    completions.add(paramName + ":" + worldName);
                }
                break;

            case "clear":
                // 布尔值补全
                completions.add(paramName + ":true");
                completions.add(paramName + ":false");
                break;

            case "use":
                // 根据命令类型补全使用模式
                if (commandType.equals("addblock")) {
                    completions.addAll(Arrays.asList(
                            paramName + ":all",
                            paramName + ":interact",
                            paramName + ":break",
                            paramName + ":place"
                    ));
                } else if (commandType.equals("addench")) {
                    completions.addAll(Arrays.asList(
                            paramName + ":all",
                            paramName + ":right",
                            paramName + ":left",
                            paramName + ":shiftright",
                            paramName + ":shiftleft",
                            paramName + ":drop"
                    ));
                } else {
                    // add, adds, check
                    completions.addAll(Arrays.asList(
                            paramName + ":all",
                            paramName + ":right",
                            paramName + ":left",
                            paramName + ":shiftright",
                            paramName + ":shiftleft",
                            paramName + ":place",
                            paramName + ":craft",
                            paramName + ":drop",
                            paramName + ":main",
                            paramName + ":off",
                            // 装备槽禁穿模式
                            paramName + ":helmet",
                            paramName + ":chestplate",
                            paramName + ":leggings",
                            paramName + ":boots",
                            paramName + ":armor",
                            paramName + ":equip"
                    ));
                }
                break;

            case "range":
                // 根据命令类型补全范围
                if (commandType.equals("addentity")) {
                    completions.addAll(Arrays.asList(
                            paramName + ":sort",
                            paramName + ":name"
                    ));
                } else {
                    completions.addAll(Arrays.asList(
                            paramName + ":all",
                            paramName + ":exactly"
                    ));
                }
                break;

            case "level":
                // 等级补全
                for (int i = 1; i <= 5; i++) {
                    completions.add(paramName + ":" + i);
                    completions.add(paramName + ":>" + i);
                    completions.add(paramName + ":>=" + i);
                    completions.add(paramName + ":<" + i);
                    completions.add(paramName + ":<=" + i);
                }
                break;

            case "reason":
            case "key":
            case "value":
            case "fuzzynbt":
                // 这些参数需要用户自己输入，只提供参数名作为提示
                String currentValue = currentInput.contains(":") ? currentInput.split(":", 2)[1] : "";
                if (currentValue.isEmpty()) {
                    completions.add(paramName + ":");
                }
                break;
        }

        return completions;
    }
}
