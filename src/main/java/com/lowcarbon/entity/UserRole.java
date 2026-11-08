package com.lowcarbon.entity;

import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * 用户角色枚举。
 * <p>
 * 对应数据库 {@code user.role} 字段，用于区分普通用户和系统管理员。
 *
 * @author lowcarbon-team
 * @since 2026-11-08
 */
public enum UserRole {

    /** 普通用户（默认角色）。 */
    USER("USER"),

    /** 系统管理员。 */
    ADMIN("ADMIN");

    private static final Logger LOGGER = Logger.getLogger(UserRole.class.getName());

    /** 数据库中存储的字符串编码。 */
    private final String code;

    UserRole(String code) {
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
     * @return 对应的枚举实例；若找不到则返回 {@link #USER}（默认角色）
     */
    public static UserRole fromCode(String code) {
        if (code == null) {
            return USER;
        }
        for (UserRole role : values()) {
            if (role.code.equalsIgnoreCase(code.trim())) {
                return role;
            }
        }
        LOGGER.log(Level.WARNING, "未知的用户角色编码: {0}，使用默认 USER", code);
        return USER;
    }
}
