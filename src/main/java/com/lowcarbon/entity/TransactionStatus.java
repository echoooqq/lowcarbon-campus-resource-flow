package com.lowcarbon.entity;

import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * 流转记录状态枚举。
 * <p>
 * 对应数据库 {@code transaction_record.status} 字段。
 *
 * @author lowcarbon-team
 * @since 2026-11-08
 */
public enum TransactionStatus {

    /** 待处理（默认状态，买家发起申请后等待卖家确认）。 */
    PENDING("PENDING"),

    /** 卖家已同意 / 申请已通过。 */
    APPROVED("APPROVED"),

    /** 卖家已拒绝申请。 */
    REJECTED("REJECTED"),

    /** 流转已完成（双方确认完成交易）。 */
    COMPLETED("COMPLETED"),

    /** 流转已取消（买家或卖家主动取消）。 */
    CANCELLED("CANCELLED");

    private static final Logger LOGGER = Logger.getLogger(TransactionStatus.class.getName());

    /** 数据库中存储的字符串编码。 */
    private final String code;

    TransactionStatus(String code) {
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
     * @return 对应的枚举实例；若找不到则返回 {@link #PENDING}（默认状态）
     */
    public static TransactionStatus fromCode(String code) {
        if (code == null) {
            return PENDING;
        }
        for (TransactionStatus status : values()) {
            if (status.code.equalsIgnoreCase(code.trim())) {
                return status;
            }
        }
        LOGGER.log(Level.WARNING, "未知的流转状态编码: {0}，使用默认 PENDING", code);
        return PENDING;
    }
}
