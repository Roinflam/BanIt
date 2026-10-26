package com.flameforge.banit.utils;

import de.tr7zw.nbtapi.*;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * NBT 路径工具类
 * 用于解析和导航 NBT 树形结构
 *
 * @author Tany天炎
 */
public class NBTPathUtil {

    /**
     * NBT 节点信息
     */
    public static class NBTNode {
        /** 键名 */
        public String key;
        /** 显示名称（包含类型标识） */
        public String displayName;
        /** 值（基础类型） */
        public Object value;
        /** NBT 类型 */
        public NBTType type;
        /** 是否可导航（Compound/List） */
        public boolean navigable;
        /** 完整路径 */
        public String fullPath;

        public NBTNode(String key, String displayName, Object value, NBTType type, boolean navigable, String fullPath) {
            this.key = key;
            this.displayName = displayName;
            this.value = value;
            this.type = type;
            this.navigable = navigable;
            this.fullPath = fullPath;
        }
    }

    /**
     * 获取指定路径的子节点列表
     *
     * @param itemStack 物品
     * @param path      路径（空字符串表示根路径）
     * @return 子节点列表
     */
    public static List<NBTNode> getChildren(ItemStack itemStack, String path) {
        List<NBTNode> children = new ArrayList<>();
        NBTItem nbtItem = new NBTItem(itemStack);

        if (path == null || path.isEmpty()) {
            // 根路径 - 获取所有顶层键
            for (String key : nbtItem.getKeys()) {
                NBTNode node = createNode(nbtItem, key, key);
                if (node != null) {
                    children.add(node);
                }
            }
        } else {
            // 导航到指定路径
            Object target = navigateToPath(nbtItem, path);
            if (target instanceof NBTCompound) {
                NBTCompound compound = (NBTCompound) target;
                for (String key : compound.getKeys()) {
                    String fullPath = path + "/" + key;
                    NBTNode node = createNodeFromCompound(compound, key, fullPath);
                    if (node != null) {
                        children.add(node);
                    }
                }
            } else if (target instanceof NBTList) {
                NBTList<?> list = (NBTList<?>) target;
                for (int i = 0; i < list.size(); i++) {
                    String fullPath = path + "/" + i;
                    NBTNode node = createNodeFromList(list, i, fullPath);
                    if (node != null) {
                        children.add(node);
                    }
                }
            }
        }

        return children;
    }

    /**
     * 导航到指定路径的 NBT 对象
     *
     * @param nbtItem 根 NBT
     * @param path    路径（用 / 分隔）
     * @return NBT 对象
     */
    private static Object navigateToPath(NBTCompound nbtItem, String path) {
        if (path == null || path.isEmpty()) {
            return nbtItem;
        }

        String[] parts = path.split("/");
        Object current = nbtItem;

        for (String part : parts) {
            if (part.isEmpty()) continue;

            if (current instanceof NBTCompound) {
                NBTCompound compound = (NBTCompound) current;
                if (!compound.hasKey(part)) {
                    return null;
                }

                NBTType type = compound.getType(part);
                switch (type) {
                    case NBTTagCompound:
                        current = compound.getCompound(part);
                        break;
                    case NBTTagList:
                        current = compound.getCompoundList(part);
                        break;
                    default:
                        // 基础类型，不能再导航
                        return null;
                }
            } else if (current instanceof NBTList) {
                NBTList<?> list = (NBTList<?>) current;
                try {
                    int index = Integer.parseInt(part);
                    if (index < 0 || index >= list.size()) {
                        return null;
                    }

                    NBTListCompound listItem = ((NBTList<NBTListCompound>) list).get(index);
                    current = listItem;
                } catch (NumberFormatException e) {
                    return null;
                }
            } else {
                return null;
            }
        }

        return current;
    }

    /**
     * 从 NBTItem 创建节点
     */
    private static NBTNode createNode(NBTCompound compound, String key, String fullPath) {
        NBTType type = compound.getType(key);

        switch (type) {
            case NBTTagCompound:
                return new NBTNode(
                        key,
                        "§b" + key + "/ §7(Compound)",
                        null,
                        type,
                        true,
                        fullPath
                );

            case NBTTagList:
                NBTList<?> list = compound.getCompoundList(key);
                return new NBTNode(
                        key,
                        "§a" + key + "/ §7(List[" + list.size() + "])",
                        null,
                        type,
                        true,
                        fullPath
                );

            case NBTTagString:
                String strValue = compound.getString(key);
                return new NBTNode(
                        key,
                        "§e" + key + ": §f\"" + truncate(strValue, 20) + "\"",
                        strValue,
                        type,
                        false,
                        fullPath
                );

            case NBTTagInt:
                int intValue = compound.getInteger(key);
                return new NBTNode(
                        key,
                        "§e" + key + ": §6" + intValue,
                        intValue,
                        type,
                        false,
                        fullPath
                );

            case NBTTagDouble:
                double doubleValue = compound.getDouble(key);
                return new NBTNode(
                        key,
                        "§e" + key + ": §6" + doubleValue,
                        doubleValue,
                        type,
                        false,
                        fullPath
                );

            case NBTTagFloat:
                float floatValue = compound.getFloat(key);
                return new NBTNode(
                        key,
                        "§e" + key + ": §6" + floatValue,
                        floatValue,
                        type,
                        false,
                        fullPath
                );

            case NBTTagLong:
                long longValue = compound.getLong(key);
                return new NBTNode(
                        key,
                        "§e" + key + ": §6" + longValue,
                        longValue,
                        type,
                        false,
                        fullPath
                );

            case NBTTagShort:
                short shortValue = compound.getShort(key);
                return new NBTNode(
                        key,
                        "§e" + key + ": §6" + shortValue,
                        shortValue,
                        type,
                        false,
                        fullPath
                );

            case NBTTagByte:
                byte byteValue = compound.getByte(key);
                boolean boolValue = byteValue != 0;
                return new NBTNode(
                        key,
                        "§e" + key + ": §6" + boolValue,
                        byteValue,
                        type,
                        false,
                        fullPath
                );

            default:
                return new NBTNode(
                        key,
                        "§7" + key + ": §c(Unsupported)",
                        null,
                        type,
                        false,
                        fullPath
                );
        }
    }

    /**
     * 从 NBTCompound 创建节点
     */
    private static NBTNode createNodeFromCompound(NBTCompound compound, String key, String fullPath) {
        return createNode(compound, key, fullPath);
    }

    /**
     * 从 NBTList 创建节点
     */
    private static NBTNode createNodeFromList(NBTList<?> list, int index, String fullPath) {
        NBTListCompound listItem = ((NBTList<NBTListCompound>) list).get(index);

        // 尝试显示列表项的简要信息
        String preview = getListItemPreview(listItem);
        return new NBTNode(
                String.valueOf(index),
                "§b[" + index + "] §7" + preview,
                null,
                NBTType.NBTTagCompound,
                true,
                fullPath
        );
    }

    /**
     * 获取列表项的预览信息
     */
    private static String getListItemPreview(NBTListCompound item) {
        if (item.getKeys().isEmpty()) {
            return "(Empty)";
        }

        // 尝试找一些有意义的键
        String[] meaningfulKeys = {"id", "name", "Name", "type", "Type"};
        for (String key : meaningfulKeys) {
            if (item.hasKey(key)) {
                String value = item.getString(key);
                return "(" + truncate(value, 15) + ")";
            }
        }

        // 显示第一个键
        String firstKey = item.getKeys().iterator().next();
        return "(" + firstKey + "=...)";
    }

    /**
     * 截断字符串
     */
    private static String truncate(String str, int maxLen) {
        if (str == null) return "";
        if (str.length() <= maxLen) return str;
        return str.substring(0, maxLen) + "...";
    }

    /**
     * 获取指定路径的值（用于禁用）
     *
     * @param itemStack 物品
     * @param path      路径
     * @return 值（仅支持基础类型）
     */
    public static Object getValueAtPath(ItemStack itemStack, String path) {
        NBTItem nbtItem = new NBTItem(itemStack);
        return getValueAtPath(nbtItem, path);
    }

    /**
     * 获取指定路径的值（从已经拿到的 NBT 根节点开始找）。
     * 同一个物品要按多条路径取值时，复用同一个根节点，不必每条路径都重新构造 NBTItem。
     *
     * @param root 根节点（NBTItem，或包着物品标签的 NBTContainer）
     * @param path 路径
     * @return 值（仅支持基础类型）
     */
    public static Object getValueAtPath(NBTCompound root, String path) {
        if (path == null || path.isEmpty()) {
            return null;
        }

        String[] parts = path.split("/");
        NBTCompound current = root;
        String lastKey = parts[parts.length - 1];

        // 导航到父级
        for (int i = 0; i < parts.length - 1; i++) {
            String part = parts[i];
            if (part.isEmpty()) continue;

            if (!current.hasKey(part)) {
                return null;
            }

            NBTType type = current.getType(part);
            if (type == NBTType.NBTTagCompound) {
                current = current.getCompound(part);
            } else {
                return null;
            }
        }

        // 获取最终值
        if (!current.hasKey(lastKey)) {
            return null;
        }

        NBTType type = current.getType(lastKey);
        switch (type) {
            case NBTTagString:
                return current.getString(lastKey);
            case NBTTagInt:
                return current.getInteger(lastKey);
            case NBTTagDouble:
                return current.getDouble(lastKey);
            case NBTTagFloat:
                return current.getFloat(lastKey);
            case NBTTagLong:
                return current.getLong(lastKey);
            case NBTTagShort:
                return current.getShort(lastKey);
            case NBTTagByte:
                return current.getByte(lastKey);
            default:
                return null;
        }
    }

    /**
     * 获取父路径
     *
     * @param path 当前路径
     * @return 父路径
     */
    public static String getParentPath(String path) {
        if (path == null || path.isEmpty()) {
            return "";
        }

        int lastSlash = path.lastIndexOf('/');
        if (lastSlash <= 0) {
            return "";
        }

        return path.substring(0, lastSlash);
    }

    /**
     * 格式化路径显示
     *
     * @param path 路径
     * @return 格式化后的显示文本
     */
    public static String formatPathDisplay(String path) {
        if (path == null || path.isEmpty()) {
            return "§b/ §7(Root)";
        }

        String[] parts = path.split("/");
        StringBuilder display = new StringBuilder("§b/");

        for (String part : parts) {
            if (!part.isEmpty()) {
                display.append(part).append("/");
            }
        }

        return display.toString();
    }
}