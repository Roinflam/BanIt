package pers.tany.banit.gui;

import org.bukkit.Bukkit;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;
import pers.tany.banit.Main;
import pers.tany.yukinoaapi.interfacepart.builder.IItemBuilder;
import pers.tany.yukinoaapi.interfacepart.configuration.IConfig;
import pers.tany.yukinoaapi.interfacepart.inventory.IInventory;
import pers.tany.yukinoaapi.interfacepart.item.IItem;
import pers.tany.yukinoaapi.interfacepart.other.IList;
import pers.tany.yukinoaapi.interfacepart.other.IString;
import pers.tany.yukinoaapi.interfacepart.serializer.ISerializer;
import pers.tany.yukinoaapi.realizationpart.builder.ItemBuilder;
import pers.tany.yukinoaapi.realizationpart.container.OrderlyMap;
import pers.tany.yukinoaapi.realizationpart.item.GlassPaneUtil;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class ListInterface implements InventoryHolder, Listener {
    private final Inventory inventory;
    private final Player player;
    private final String worldName;
    private final HashMap<Integer, String> SLOT_ITEM = new HashMap<>();
    private int nowPage;

    public ListInterface(Player player, int page, String worldName) {
        OrderlyMap<String, String> orderlyHashMap = new OrderlyMap<String, String>();
        if (Main.data.getConfigurationSection("BanInfo." + worldName + ".Item") != null) {
            for (String s : Main.data.getConfigurationSection("BanInfo." + worldName + ".Item").getKeys(false)) {
                orderlyHashMap.put(s, "Item");
            }
        }
        if (Main.data.getConfigurationSection("BanInfo." + worldName + ".Block") != null) {
            for (String s : Main.data.getConfigurationSection("BanInfo." + worldName + ".Block").getKeys(false)) {
                orderlyHashMap.put(s, "Block");
            }
        }
        if (Main.data.getConfigurationSection("BanInfo." + worldName + ".Entity") != null) {
            for (String s : Main.data.getConfigurationSection("BanInfo." + worldName + ".Entity").getKeys(false)) {
                orderlyHashMap.put(s, "Entity");
            }
        }
        if (Main.data.getConfigurationSection("BanInfo." + worldName + ".Ench") != null) {
            for (String s : Main.data.getConfigurationSection("BanInfo." + worldName + ".Ench").getKeys(false)) {
                orderlyHashMap.put(s, "Ench");
            }
        }

        this.inventory = Bukkit.createInventory(this, 54, IString.color(Main.message.getString("ListTitle").replace("[world]", worldName).replace(Main.config.getString("OverAll"), "全局")).replace("[number]", orderlyHashMap.size() + ""));
        this.player = player;
        this.nowPage = page;
        this.worldName = worldName;

        update(nowPage);

        Bukkit.getPluginManager().registerEvents(this, Main.plugin);
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    public void update() {
        update(nowPage);
    }

    private void update(int page) {
        inventory.clear();
        new BukkitRunnable() {

            @Override
            public void run() {
                OrderlyMap<String, String> orderlyHashMap = new OrderlyMap<String, String>();
                if (Main.data.getConfigurationSection("BanInfo." + worldName + ".Item") != null) {
                    for (String s : Main.data.getConfigurationSection("BanInfo." + worldName + ".Item").getKeys(false)) {
                        orderlyHashMap.put(s, "Item");
                    }
                }
                if (Main.data.getConfigurationSection("BanInfo." + worldName + ".Block") != null) {
                    for (String s : Main.data.getConfigurationSection("BanInfo." + worldName + ".Block").getKeys(false)) {
                        orderlyHashMap.put(s, "Block");
                    }
                }
                if (Main.data.getConfigurationSection("BanInfo." + worldName + ".Entity") != null) {
                    for (String s : Main.data.getConfigurationSection("BanInfo." + worldName + ".Entity").getKeys(false)) {
                        orderlyHashMap.put(s, "Entity");
                    }
                }
                if (Main.data.getConfigurationSection("BanInfo." + worldName + ".Ench") != null) {
                    for (String s : Main.data.getConfigurationSection("BanInfo." + worldName + ".Ench").getKeys(false)) {
                        orderlyHashMap.put(s, "Ench");
                    }
                }

                IItemBuilder frame = GlassPaneUtil.getStainedGlass(1);
                IItemBuilder last = GlassPaneUtil.getStainedGlass(11);
                IItemBuilder next = GlassPaneUtil.getStainedGlass(14);

                frame.setDisplayName("§2介绍");
                last.setDisplayName("§3上一页");
                next.setDisplayName("§6下一页");

                frame.addLoreAll(IList.listReplace(Main.message.getStringList("ListLore"), "&", "§"));
                last.addLore("§a翻到上一页");
                next.addLore("§a翻到下一页");

                if (page > 1) {
                    inventory.setItem(45, last.getItemStack());
                } else {
                    inventory.setItem(45, frame.getItemStack());
                }
                for (int i = 46; i <= 52; i++) {
                    inventory.setItem(i, frame.getItemStack());
                }
                if (orderlyHashMap.size() > 45 + (page - 1) * 45) {
                    inventory.setItem(53, next.getItemStack());
                } else {
                    inventory.setItem(53, frame.getItemStack());
                }

                int index = (page - 1) * 45;
                int location = 0;
                int size = orderlyHashMap.size() - 1;
                while (index <= size && index <= 44 + (page - 1) * 45) {
                    switch (orderlyHashMap.get(index)) {
                        case "Item": {
                            String serialNumber = orderlyHashMap.getKey(index);
                            try {
                                IItemBuilder itemBuilder = new ItemBuilder(ISerializer.deserializeItemStack(Main.data.getString("BanInfo." + worldName + ".Item." + serialNumber + ".ItemStack")));

                                String id = Main.data.getString("BanInfo." + worldName + ".Item." + serialNumber + ".ID");
                                short durability = (short) Main.data.getInt("BanInfo." + worldName + ".Item." + serialNumber + ".Durability");
                                int distinguish = Main.data.getInt("BanInfo." + worldName + ".Item." + serialNumber + ".Distinguish");
                                boolean clear = Main.data.getBoolean("BanInfo." + worldName + ".Item." + serialNumber + ".Clear");
                                String mode = Main.data.getString("BanInfo." + worldName + ".Item." + serialNumber + ".Mode");
                                String key = Main.data.getString("BanInfo." + worldName + ".Item." + serialNumber + ".Key");
                                String value = Main.data.getString("BanInfo." + worldName + ".Item." + serialNumber + ".Value");
                                String reason = Main.data.getString("BanInfo." + worldName + ".Item." + serialNumber + ".Reason");

                                SLOT_ITEM.put(location, serialNumber);

                                List<String> lore = new ArrayList<>();
                                if (distinguish == 0) {
                                    lore = Main.message.getStringList("ListItemLore");
                                } else if (distinguish == 1) {
                                    lore = Main.message.getStringList("ListItemAllLore");
                                } else if (distinguish == 2) {
                                    lore = Main.message.getStringList("ListItemExactLore");
                                }
                                if (player.isOp()) {
                                    lore.add("");
                                    lore.add("");
                                    lore.add("§e点击移除此设置");
                                }

                                lore = IList.listReplace(lore, "[number]", serialNumber);
                                lore = IList.listReplace(lore, "[id]", id);
                                lore = IList.listReplace(lore, "[durability]", durability + "");
                                lore = IList.listReplace(lore, "[reason]", reason);
                                if (clear) {
                                    lore = IList.listReplace(lore, "[clear]", "§c是");
                                } else {
                                    lore = IList.listReplace(lore, "[clear]", "§a否");
                                }
                                switch (mode) {
                                    case "a":
                                        lore = IList.listReplace(lore, "[mode]", "所有");
                                        break;
                                    case "r":
                                        lore = IList.listReplace(lore, "[mode]", "右键");
                                        break;
                                    case "l":
                                        lore = IList.listReplace(lore, "[mode]", "左键");
                                        break;
                                    case "p":
                                        lore = IList.listReplace(lore, "[mode]", "放置");
                                        break;
                                    case "c":
                                        lore = IList.listReplace(lore, "[mode]", "合成");
                                        break;
                                    case "d":
                                        lore = IList.listReplace(lore, "[mode]", "丢弃");
                                        break;
                                    case "m":
                                        lore = IList.listReplace(lore, "[mode]", "放在主手");
                                        break;
                                    case "o":
                                        lore = IList.listReplace(lore, "[mode]", "放在副手");
                                        break;
                                    default:
                                        break;
                                }
                                if (key != null) {
                                    lore = IList.listReplace(lore, "[key]", key);
                                } else {
                                    lore = IList.listReplace(lore, "[key]", "无");
                                }
                                if (value != null) {
                                    lore = IList.listReplace(lore, "[value]", value);
                                } else {
                                    lore = IList.listReplace(lore, "[value]", "无");
                                }
                                itemBuilder.setLore(lore);

                                inventory.setItem(location, itemBuilder.getItemStack());
                            } catch (Exception e) {
                                Main.data.set("BanInfo." + worldName + ".Item." + serialNumber, null);
                                Main.data.set("BanInfo." + worldName + ".Block." + serialNumber, null);
                                Main.data.set("BanInfo." + worldName + ".Entity." + serialNumber, null);
                                Main.data.set("BanInfo." + worldName + ".Ench." + serialNumber, null);
                                IConfig.saveConfig(Main.plugin, Main.data, "", "data");
                            }
                            break;
                        }
                        case "Block": {
                            String serialNumber = orderlyHashMap.getKey(index);
                            try {
                                IItemBuilder itemBuilder = new ItemBuilder(ISerializer.deserializeItemStack(Main.data.getString("BanInfo." + worldName + ".Block." + serialNumber + ".ItemStack")));
                                String id = Main.data.getString("BanInfo." + worldName + ".Block." + serialNumber + ".ID");
                                byte data = (byte) Main.data.getInt("BanInfo." + worldName + ".Block." + serialNumber + ".Data");
                                int distinguish = Main.data.getInt("BanInfo." + worldName + ".Block." + serialNumber + ".Distinguish");
                                boolean clear = Main.data.getBoolean("BanInfo." + worldName + ".Block." + serialNumber + ".Clear");
                                String mode = Main.data.getString("BanInfo." + worldName + ".Block." + serialNumber + ".Mode");
                                String tileID = Main.data.getString("BanInfo." + worldName + ".Block." + serialNumber + ".TileID");
                                String subTileName = Main.data.getString("BanInfo." + worldName + ".Block." + serialNumber + ".SubTileName");
                                String reason = Main.data.getString("BanInfo." + worldName + ".Block." + serialNumber + ".Reason");

                                SLOT_ITEM.put(location, serialNumber);

                                List<String> lore = new ArrayList<>();
                                if (distinguish == 0) {
                                    lore = Main.message.getStringList("ListBlockLore");
                                } else if (distinguish == 1) {
                                    lore = Main.message.getStringList("ListBlockAllLore");
                                } else if (distinguish == 2) {
                                    lore = Main.message.getStringList("ListBlockExactLore");
                                }
                                if (player.isOp()) {
                                    lore.add("");
                                    lore.add("");
                                    lore.add("§e点击移除此设置");
                                }

                                lore = IList.listReplace(lore, "[number]", serialNumber);
                                lore = IList.listReplace(lore, "[id]", id);
                                lore = IList.listReplace(lore, "[data]", data + "");
                                lore = IList.listReplace(lore, "[reason]", reason);
                                if (clear) {
                                    lore = IList.listReplace(lore, "[clear]", "§c是");
                                } else {
                                    lore = IList.listReplace(lore, "[clear]", "§a否");
                                }
                                switch (mode) {
                                    case "a":
                                        lore = IList.listReplace(lore, "[mode]", "所有");
                                        break;
                                    case "i":
                                        lore = IList.listReplace(lore, "[mode]", "交互");
                                        break;
                                    case "b":
                                        lore = IList.listReplace(lore, "[mode]", "破坏");
                                        break;
                                    case "p":
                                        lore = IList.listReplace(lore, "[mode]", "放置");
                                        break;
                                    default:
                                        break;
                                }

                                if (tileID == null) {
                                    lore = IList.listReplace(lore, "[tileID]", "无");
                                } else {
                                    lore = IList.listReplace(lore, "[tileID]", tileID);
                                }
                                if (subTileName == null) {
                                    lore = IList.listReplace(lore, "[subTileName]", "无");
                                } else {
                                    lore = IList.listReplace(lore, "[subTileName]", subTileName);
                                }
                                itemBuilder.setLore(lore);

                                inventory.setItem(location, itemBuilder.getItemStack());
                            } catch (Exception e) {
                                Main.data.set("BanInfo." + worldName + ".Item." + serialNumber, null);
                                Main.data.set("BanInfo." + worldName + ".Block." + serialNumber, null);
                                Main.data.set("BanInfo." + worldName + ".Entity." + serialNumber, null);
                                Main.data.set("BanInfo." + worldName + ".Ench." + serialNumber, null);
                                IConfig.saveConfig(Main.plugin, Main.data, "", "data");
                            }
                            break;
                        }
                        case "Entity": {
                            String serialNumber = orderlyHashMap.getKey(index);
                            try {
                                IItemBuilder itemBuilder = new ItemBuilder(ISerializer.deserializeItemStack(Main.data.getString("BanInfo." + worldName + ".Entity." + serialNumber + ".ItemStack")));
                                int distinguish = Main.data.getInt("BanInfo." + worldName + ".Entity." + serialNumber + ".Distinguish");
                                String species = "无";
                                String name = "无";
                                if (Main.data.getString("BanInfo." + worldName + ".Entity." + serialNumber + ".Species") != null) {
                                    species = Main.data.getString("BanInfo." + worldName + ".Entity." + serialNumber + ".Species");
                                }
                                if (Main.data.getString("BanInfo." + worldName + ".Entity." + serialNumber + ".Name") != null) {
                                    name = Main.data.getString("BanInfo." + worldName + ".Entity." + serialNumber + ".Name");
                                }
                                String reason = Main.data.getString("BanInfo." + worldName + ".Entity." + serialNumber + ".Reason");

                                SLOT_ITEM.put(location, serialNumber);

                                List<String> lore = new ArrayList<>();
                                if (distinguish == 0) {
                                    lore = Main.message.getStringList("ListEntityLore");
                                } else if (distinguish == 1) {
                                    lore = Main.message.getStringList("ListEntitySpeciesLore");
                                } else if (distinguish == 2) {
                                    lore = Main.message.getStringList("ListEntityNameLore");
                                }
                                if (player.isOp()) {
                                    lore.add("");
                                    lore.add("");
                                    lore.add("§e点击移除此设置");
                                }
                                lore = IList.listReplace(lore, "[number]", serialNumber);
                                lore = IList.listReplace(lore, "[species]", species);
                                lore = IList.listReplace(lore, "[name]", name);
                                lore = IList.listReplace(lore, "[reason]", reason);
                                itemBuilder.setLore(lore);

                                inventory.setItem(location, itemBuilder.getItemStack());
                            } catch (Exception e) {
                                Main.data.set("BanInfo." + worldName + ".Item." + serialNumber, null);
                                Main.data.set("BanInfo." + worldName + ".Block." + serialNumber, null);
                                Main.data.set("BanInfo." + worldName + ".Entity." + serialNumber, null);
                                Main.data.set("BanInfo." + worldName + ".Ench." + serialNumber, null);
                                IConfig.saveConfig(Main.plugin, Main.data, "", "data");
                            }
                            break;
                        }
                        default: {
                            String serialNumber = orderlyHashMap.getKey(index);
                            try {
                                IItemBuilder itemBuilder = new ItemBuilder("ENCHANTED_BOOK");
                                String enchName = Main.data.getString("BanInfo." + worldName + ".Ench." + serialNumber + ".EnchName");
                                boolean clear = Main.data.getBoolean("BanInfo." + worldName + ".Ench." + serialNumber + ".Clear");
                                String mode = Main.data.getString("BanInfo." + worldName + ".Ench." + serialNumber + ".Mode");
                                String level = Main.data.getString("BanInfo." + worldName + ".Ench." + serialNumber + ".Level");
                                String reason = Main.data.getString("BanInfo." + worldName + ".Ench." + serialNumber + ".Reason");

                                SLOT_ITEM.put(location, serialNumber);

                                List<String> lore = new ArrayList<>();
                                lore = Main.message.getStringList("ListEnchLore");
                                if (player.isOp()) {
                                    lore.add("");
                                    lore.add("");
                                    lore.add("§e点击移除此设置");
                                }

                                lore = IList.listReplace(lore, "[number]", serialNumber);
                                lore = IList.listReplace(lore, "[level]", level);
                                lore = IList.listReplace(lore, "[reason]", reason);
                                if (clear) {
                                    lore = IList.listReplace(lore, "[clear]", "§c是");
                                } else {
                                    lore = IList.listReplace(lore, "[clear]", "§a否");
                                }
                                switch (mode) {
                                    case "a":
                                        lore = IList.listReplace(lore, "[mode]", "所有");
                                        break;
                                    case "r":
                                        lore = IList.listReplace(lore, "[mode]", "右键");
                                        break;
                                    case "l":
                                        lore = IList.listReplace(lore, "[mode]", "左键");
                                        break;
                                    case "d":
                                        lore = IList.listReplace(lore, "[mode]", "丢弃");
                                        break;
                                    default:
                                        break;
                                }
                                itemBuilder.setLore(lore);

                                ItemStack itemStack = itemBuilder.getItemStack();
                                itemStack.addUnsafeEnchantment(Enchantment.getByName(enchName), 1);
                                inventory.setItem(location, itemStack);
                            } catch (Exception e) {
                                Main.data.set("BanInfo." + worldName + ".Item." + serialNumber, null);
                                Main.data.set("BanInfo." + worldName + ".Block." + serialNumber, null);
                                Main.data.set("BanInfo." + worldName + ".Entity." + serialNumber, null);
                                Main.data.set("BanInfo." + worldName + ".Ench." + serialNumber, null);
                                IConfig.saveConfig(Main.plugin, Main.data, "", "data");
                            }
                            break;
                        }
                    }
                    location++;
                    index++;
                }
            }

        }.runTask(Main.plugin);
    }

    @EventHandler
    private void onInventoryClick(InventoryClickEvent evt) {
        if (evt.getWhoClicked() instanceof Player && evt.getWhoClicked().equals(player)) {
            int rawSlot = evt.getRawSlot();
            if (rawSlot != -999) {
                if (evt.getInventory().getHolder() instanceof ListInterface) {
                    evt.setCancelled(true);
                    if (evt.getClickedInventory().getHolder() instanceof ListInterface) {
                        if (!IItem.isEmpty(evt.getCurrentItem())) {
                            if (evt.getCurrentItem().getItemMeta().hasDisplayName()) {
                                if (evt.getCurrentItem().getItemMeta().getDisplayName().equals("§2介绍")) {
                                    return;
                                }
                                if (evt.getCurrentItem().getItemMeta().getDisplayName().equals("§3上一页")) {
                                    update(--nowPage);
                                    return;
                                }
                                if (evt.getCurrentItem().getItemMeta().getDisplayName().equals("§6下一页")) {
                                    update(++nowPage);
                                    return;
                                }
                            }
                            if (player.isOp()) {
                                String serialNumber = SLOT_ITEM.get(evt.getRawSlot());
                                Main.data.set("BanInfo." + worldName + ".Item." + serialNumber, null);
                                Main.data.set("BanInfo." + worldName + ".Block." + serialNumber, null);
                                Main.data.set("BanInfo." + worldName + ".Entity." + serialNumber, null);
                                Main.data.set("BanInfo." + worldName + ".Ench." + serialNumber, null);

                                OrderlyMap<String, String> orderlyHashMap = new OrderlyMap<String, String>();
                                if (Main.data.getConfigurationSection("BanInfo." + worldName + ".Item") != null) {
                                    for (String s : Main.data.getConfigurationSection("BanInfo." + worldName + ".Item").getKeys(false)) {
                                        orderlyHashMap.put(s, "Item");
                                    }
                                }
                                if (Main.data.getConfigurationSection("BanInfo." + worldName + ".Block") != null) {
                                    for (String s : Main.data.getConfigurationSection("BanInfo." + worldName + ".Block").getKeys(false)) {
                                        orderlyHashMap.put(s, "Block");
                                    }
                                }
                                if (Main.data.getConfigurationSection("BanInfo." + worldName + ".Entity") != null) {
                                    for (String s : Main.data.getConfigurationSection("BanInfo." + worldName + ".Entity").getKeys(false)) {
                                        orderlyHashMap.put(s, "Entity");
                                    }
                                }
                                if (Main.data.getConfigurationSection("BanInfo." + worldName + ".Ench") != null) {
                                    for (String s : Main.data.getConfigurationSection("BanInfo." + worldName + ".Ench").getKeys(false)) {
                                        orderlyHashMap.put(s, "Ench");
                                    }
                                }
                                if (orderlyHashMap.size() < 1) {
                                    Main.data.set("BanInfo." + worldName, null);
                                }

                                IConfig.saveConfig(Main.plugin, Main.data, "", "data");

                                player.sendMessage("§a移除完毕");
                                new BukkitRunnable() {

                                    @Override
                                    public void run() {
                                        update(nowPage);
                                    }

                                }.runTask(Main.plugin);
                            }
                        }
                    }
                }
            }
        }
    }

    @EventHandler
    private void onInventoryClose(InventoryCloseEvent evt) {
        if (evt.getInventory().getHolder() instanceof ListInterface && evt.getPlayer() instanceof Player) {
            if (evt.getPlayer().equals(player)) {
                HandlerList.unregisterAll(this);
                if (Main.config.getBoolean("WorldListInterface")) {
                    IInventory.openInventory(new WorldListInterface(player, 1), player);
                }
            }
        }
    }
}
