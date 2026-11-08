package com.lowcarbon.entity;

import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * 物品交易类型枚举。
 * <p>
 * 对应数据库 {@code item.trade_type} 字段。
 *
 * @author lowcarbon-team
 * @since 2026-11-08
 */
public enum TradeType {

    /** 出售（默认类型）。 */
    SELL("SELL"),

    /** 捐赠（免费赠送）。 */
    DONATE("DONATE"),

    /** 借用 / 交换。 */
    BORROW("BORROW");

    private static final Logger LOGGER = Logger.getLogger(TradeType.class.getName());

    /** 数据库中存储的字符串编码。 */
    private final String code;

    TradeType(String code) {
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
     * @return 对应的枚举实例；若找不到则返回 {@link #SELL}（默认类型）
     */
    public static TradeType fromCode(String code) {
        if (code == null) {
            return SELL;
        }
        for (TradeType type : values()) {
            if (type.code.equalsIgnoreCase(code.trim())) {
                return type;
            }
        }
        LOGGER.log(Level.WARNING, "未知的交易类型编码: {0}，使用默认 SELL", code);
        return SELL;
    }
}
