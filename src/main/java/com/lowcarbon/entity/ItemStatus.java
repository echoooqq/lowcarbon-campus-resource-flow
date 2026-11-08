package com.lowcarbon.entity;

import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * 物品状态枚举。
 * <p>
 * 对应数据库 {@code item.status} 字段，标识物品当前的流转阶段。
 *
 * @author lowcarbon-team
 * @since 2026-11-08
 */
public enum ItemStatus {

    /** 在售中（默认状态）。 */
    ON_SALE("ON_SALE"),

    /** 已被预定（有买家发起流转但未完成）。 */
    RESERVED("RESERVED"),

    /** 已售 / 已完成流转。 */
    SOLD("SOLD"),

    /** 已下架（卖家主动撤回或管理员下架）。 */
    OFF_SHELF("OFF_SHELF");

    private static final Logger LOGGER = Logger.getLogger(ItemStatus.class.getName());

    /** 数据库中存储的字符串编码。 */
    private final String code;

    ItemStatus(String code) {
        this.code = code;
    }

    /**
     * 获取数据库编码。
     *
     * @return 数据库存储的字符串值
     */
    public String getCode() {
        return code;
    }

    /**
     * 根据数据库字符串编码查找枚举实例。
     *
     * @param code 数据库中存储的字符串值
     * @return 对应的枚举实例；若找不到则返回 {@link #ON_SALE}（默认状态）
     */
    public static ItemStatus fromCode(String code) {
        if (code == null) {
            return ON_SALE;
        }
        for (ItemStatus status : values()) {
            if (status.code.equalsIgnoreCase(code.trim())) {
                return status;
            }
        }
        LOGGER.log(Level.WARNING, "未知的物品状态编码: {0}，使用默认 ON_SALE", code);
        return ON_SALE;
    }
}
