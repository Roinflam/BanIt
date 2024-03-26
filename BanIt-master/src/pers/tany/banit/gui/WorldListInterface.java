package pers.tany.banit.gui;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.scheduler.BukkitRunnable;
import pers.tany.banit.Main;
import pers.tany.yukinoaapi.interfacepart.builder.IItemBuilder;
import pers.tany.yukinoaapi.interfacepart.inventory.IInventory;
import pers.tany.yukinoaapi.interfacepart.item.IItem;
import pers.tany.yukinoaapi.interfacepart.other.IList;
import pers.tany.yukinoaapi.interfacepart.other.IString;
import pers.tany.yukinoaapi.interfacepart.serializer.ISerializer;
import pers.tany.yukinoaapi.realizationpart.builder.ItemBuilder;
import pers.tany.yukinoaapi.realizationpart.item.GlassPaneUtil;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class WorldListInterface implements InventoryHolder, Listener {
    private final Inventory inventory;
    private final Player player;
    private int nowPage;
    private final HashMap<Integer, String> SLOT_ITEM = new HashMap<>();


    public WorldListInterface(Player player, int page) {
        ArrayList<String> worlds = new ArrayList<>(Main.data.getConfigurationSection("BanInfo").getKeys(false));

        this.inventory = Bukkit.createInventory(this, 54, IString.color(Main.message.getString("WorldListTitle").replace("[number]", worlds.size() + "")));
        this.player = player;
        this.nowPage = page;

        update(page);

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
                ArrayList<String> worlds = new ArrayList<>(Main.data.getConfigurationSection("BanInfo").getKeys(false));

                IItemBuilder frame = GlassPaneUtil.getStainedGlass(1);
                IItemBuilder last = GlassPaneUtil.getStainedGlass(11);
                IItemBuilder next = GlassPaneUtil.getStainedGlass(14);

                frame.setDisplayName("§2介绍");
                last.setDisplayName("§3上一页");
                next.setDisplayName("§6下一页");

                frame.addLoreAll(IList.listReplace(Main.message.getStringList("WorldListLore"), "&", "§"));
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
                if (worlds.size() > 45 + (page - 1) * 45) {
                    inventory.setItem(53, next.getItemStack());
                } else {
                    inventory.setItem(53, frame.getItemStack());
                }
                int index = (page - 1) * 45;
                int location = 0;
                int size = worlds.size() - 1;
                while (index <= size && index <= 44 + (page - 1) * 45) {
                    String worldName = worlds.get(index);
                    IItemBuilder itemBuilder;
                    if (Main.data.getString("BanInfo." + worldName + ".DisplayItem") != null) {
                        itemBuilder = new ItemBuilder(ISerializer.deserializeItemStack(Main.data.getString("BanInfo." + worldName + ".DisplayItem")));
                    } else {
                        itemBuilder = new ItemBuilder("GRASS");
                    }
                    int itemNumber = 0;
                    int blockNumber = 0;
                    int entityNumber = 0;
                    int enchNumber = 0;

                    itemBuilder.setDisplayName(IString.color(Main.message.getString("WorldListName").replace("[world]", worldName).replace(Main.config.getString("OverAll"), "全局")));

                    if (Main.data.getConfigurationSection("BanInfo." + worldName + ".Item") != null) {
                        itemNumber = Main.data.getConfigurationSection("BanInfo." + worldName + ".Item").getKeys(false).size();
                    }
                    if (Main.data.getConfigurationSection("BanInfo." + worldName + ".Block") != null) {
                        blockNumber = Main.data.getConfigurationSection("BanInfo." + worldName + ".Block").getKeys(false).size();
                    }
                    if (Main.data.getConfigurationSection("BanInfo." + worldName + ".Entity") != null) {
                        entityNumber = Main.data.getConfigurationSection("BanInfo." + worldName + ".Entity").getKeys(false).size();
                    }
                    if (Main.data.getConfigurationSection("BanInfo." + worldName + ".Ench") != null) {
                        enchNumber = Main.data.getConfigurationSection("BanInfo." + worldName + ".Ench").getKeys(false).size();
                    }
                    int allNumber = itemNumber + blockNumber + entityNumber + enchNumber;

                    List<String> lore = Main.message.getStringList("WorldListItemLore");
                    lore = IList.listReplace(lore, "[allNumber]", allNumber + "");
                    lore = IList.listReplace(lore, "[itemNumber]", itemNumber + "");
                    lore = IList.listReplace(lore, "[blockNumber]", blockNumber + "");
                    lore = IList.listReplace(lore, "[entityNumber]", entityNumber + "");
                    lore = IList.listReplace(lore, "[enchNumber]", enchNumber + "");
                    lore = IList.listReplace(lore, "&", "§");

                    itemBuilder.setLore(lore);
                    inventory.setItem(location, itemBuilder.getItemStack());
                    SLOT_ITEM.put(location, worldName);

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
                if (evt.getInventory().getHolder() instanceof WorldListInterface) {
                    evt.setCancelled(true);
                    if (evt.getClickedInventory().getHolder() instanceof WorldListInterface) {
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
                            player.closeInventory();
                            IInventory.openInventory(new ListInterface(player, 1, SLOT_ITEM.get(evt.getRawSlot())), player);
                        }
                    }
                }
            }
        }
    }

    @EventHandler
    private void onInventoryClose(InventoryCloseEvent evt) {
        if (evt.getInventory().getHolder() instanceof WorldListInterface && evt.getPlayer() instanceof Player) {
            if (evt.getPlayer().equals(player)) {
                HandlerList.unregisterAll(this);
            }
        }
    }
}
