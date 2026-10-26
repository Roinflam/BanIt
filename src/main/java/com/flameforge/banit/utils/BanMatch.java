package com.flameforge.banit.utils;

/**
 * 封禁匹配结果：序列号 + 匹配到的世界。
 *
 * <p>Clear / Mode / Reason 都按 (世界, 序列号) 读。以前只返回序列号，CheckTask 还得再跑一遍匹配
 * （findMatchedWorld）才知道命中的是当前世界还是 OverAll，一次命中要匹配三遍；现在匹配时把世界一起带回来。</p>
 *
 * <p>只是内存里的返回值，不参与存储。</p>
 *
 * @author roinflam
 * @version 1.0.0
 */
public final class BanMatch {

    /** 匹配到的封禁条目序列号 */
    private final String serialNumber;

    /** 该序列号所属的世界名（可能是玩家所在世界，也可能是 OverAll 全局世界） */
    private final String world;

    /**
     * @param serialNumber 封禁条目序列号，不为 null
     * @param world        匹配到该条目的世界名，不为 null
     */
    public BanMatch(String serialNumber, String world) {
        this.serialNumber = serialNumber;
        this.world = world;
    }

    /**
     * @return 封禁条目序列号
     */
    public String getSerialNumber() {
        return this.serialNumber;
    }

    /**
     * @return 匹配到该条目的世界名，可直接用于
     *         {@code dataManager.getAllBannedInfo(world)} /
     *         {@code isBannedItemClear(world, ...)} /
     *         {@code getBannedItemReason(world, ...)}
     */
    public String getWorld() {
        return this.world;
    }

    @Override
    public String toString() {
        return "BanMatch{serialNumber=" + this.serialNumber + ", world=" + this.world + "}";
    }
}
