package com.lowcarbon.util;

import java.util.regex.Pattern;

/**
 * 输入合法性校验工具类。
 * <p>
 * 集中处理用户名、密码、价格、描述等字段的长度、格式校验，
 * 以及 SQL 注入风险字符过滤。Service 层和 GUI 层共用同一套校验逻辑，
 * 避免校验规则分散在各处导致不一致。
 *
 * @author lowcarbon-team
 * @since 2026-11-09
 */
public final class Validator {

    // ==================== 校验规则常量 ====================

    /** 用户名最小长度。 */
    public static final int MIN_USERNAME_LENGTH = 3;

    /** 用户名最大长度。 */
    public static final int MAX_USERNAME_LENGTH = 20;

    /** 密码最小长度。 */
    public static final int MIN_PASSWORD_LENGTH = 6;

    /** 密码最大长度。 */
    public static final int MAX_PASSWORD_LENGTH = 32;

    /** 物品名称最大长度。 */
    public static final int MAX_ITEM_NAME_LENGTH = 50;

    /** 物品描述最大长度。 */
    public static final int MAX_ITEM_DESC_LENGTH = 500;

    /** 评论内容最大长度。 */
    public static final int MAX_COMMENT_LENGTH = 300;

    /** 信用分最低值。 */
    public static final int MIN_CREDIT_SCORE = 0;

    /** 信用分最高值。 */
    public static final int MAX_CREDIT_SCORE = 200;

    /** 价格最低值（分）。 */
    public static final double MIN_PRICE = 0.0;

    /** 价格最高值。 */
    public static final double MAX_PRICE = 99999.99;

    // ==================== 正则表达式 ====================

    /** 用户名：字母/数字/下划线，3-20 位，首字母必须为字母。 */
    private static final Pattern USERNAME_PATTERN =
            Pattern.compile("^[a-zA-Z][a-zA-Z0-9_]{2,19}$");

    /** 禁止出现在文本中的危险字符（SQL 注入、HTML 注入风险）。 */
    private static final Pattern DANGEROUS_CHARS_PATTERN =
            Pattern.compile("[;\\-'\"\\\\\\x00\\x1a]");

    private Validator() {
        // 工具类禁止实例化
    }

    // ==================== 通用方法 ====================

    /**
     * 字符串是否为 null 或全空白。
     *
     * @param str 待检查字符串
     * @return 是否为空
     */
    public static boolean isBlank(String str) {
        return str == null || str.isBlank();
    }

    /**
     * 检查字符串长度是否在指定范围内。
     *
     * @param str      待检查字符串
     * @param minIncl  最小长度（含）
     * @param maxIncl  最大长度（含）
     * @return 是否在范围内；若 str 为 null 也返回 false
     */
    public static boolean isLengthInRange(String str, int minIncl, int maxIncl) {
        if (str == null) {
            return false;
        }
        int len = str.length();
        return len >= minIncl && len <= maxIncl;
    }

    // ==================== 用户名校验 ====================

    /**
     * 校验用户名格式。
     * <p>
     * 规则：3-20 位，首字母必须为字母，后续可含字母/数字/下划线。
     *
     * @param username 待校验用户名
     * @return 错误信息；校验通过返回 {@code null}
     */
    public static String validateUsername(String username) {
        if (isBlank(username)) {
            return "用户名不能为空";
        }
        if (username.length() < MIN_USERNAME_LENGTH) {
            return "用户名至少 " + MIN_USERNAME_LENGTH + " 个字符";
        }
        if (username.length() > MAX_USERNAME_LENGTH) {
            return "用户名最多 " + MAX_USERNAME_LENGTH + " 个字符";
        }
        if (!USERNAME_PATTERN.matcher(username).matches()) {
            return "用户名必须以字母开头，只能包含字母、数字和下划线";
        }
        if (containsDangerousChars(username)) {
            return "用户名包含非法字符";
        }
        return null;
    }

    // ==================== 密码校验 ====================

    /**
     * 校验密码强度。
     * <p>
     * 规则：6-32 位，不能为纯空白。
     *
     * @param password 待校验密码
     * @return 错误信息；校验通过返回 {@code null}
     */
    public static String validatePassword(String password) {
        if (isBlank(password)) {
            return "密码不能为空";
        }
        if (password.length() < MIN_PASSWORD_LENGTH) {
            return "密码至少 " + MIN_PASSWORD_LENGTH + " 个字符";
        }
        if (password.length() > MAX_PASSWORD_LENGTH) {
            return "密码最多 " + MAX_PASSWORD_LENGTH + " 个字符";
        }
        return null;
    }

    /**
     * 校验两次密码是否一致。
     *
     * @param password  密码
     * @param confirm   确认密码
     * @return 错误信息；一致返回 {@code null}
     */
    public static String validatePasswordConfirm(String password, String confirm) {
        if (password == null || !password.equals(confirm)) {
            return "两次输入的密码不一致";
        }
        return null;
    }

    // ==================== 价格校验 ====================

    /**
     * 校验价格范围。
     *
     * @param price 价格（元）
     * @return 错误信息；校验通过返回 {@code null}
     */
    public static String validatePrice(double price) {
        if (price < MIN_PRICE) {
            return "价格不能为负数";
        }
        if (price > MAX_PRICE) {
            return "价格不能超过 " + MAX_PRICE;
        }
        return null;
    }

    // ==================== 信用分校验 ====================

    /**
     * 校验信用分范围。
     *
     * @param creditScore 信用分
     * @return 错误信息；校验通过返回 {@code null}
     */
    public static String validateCreditScore(int creditScore) {
        if (creditScore < MIN_CREDIT_SCORE || creditScore > MAX_CREDIT_SCORE) {
            return "信用分必须在 " + MIN_CREDIT_SCORE + " ~ " + MAX_CREDIT_SCORE + " 之间";
        }
        return null;
    }

    // ==================== 物品名称 / 描述校验 ====================

    /**
     * 校验物品名称。
     *
     * @param name 物品名称
     * @return 错误信息；校验通过返回 {@code null}
     */
    public static String validateItemName(String name) {
        if (isBlank(name)) {
            return "物品名称不能为空";
        }
        if (name.length() > MAX_ITEM_NAME_LENGTH) {
            return "物品名称最多 " + MAX_ITEM_NAME_LENGTH + " 个字符";
        }
        return null;
    }

    /**
     * 校验物品描述。
     *
     * @param description 物品描述
     * @return 错误信息；校验通过返回 {@code null}
     */
    public static String validateItemDescription(String description) {
        if (description != null && description.length() > MAX_ITEM_DESC_LENGTH) {
            return "物品描述最多 " + MAX_ITEM_DESC_LENGTH + " 个字符";
        }
        return null;
    }

    /**
     * 校验评论内容。
     *
     * @param comment 评论文本
     * @return 错误信息；校验通过返回 {@code null}
     */
    public static String validateComment(String comment) {
        if (isBlank(comment)) {
            return "评论内容不能为空";
        }
        if (comment.length() > MAX_COMMENT_LENGTH) {
            return "评论最多 " + MAX_COMMENT_LENGTH + " 个字符";
        }
        return null;
    }

    // ==================== SQL 注入防护 ====================

    /**
     * 检查字符串是否含有危险字符（SQL 注入、命令注入常见载体）。
     *
     * @param input 待检查字符串
     * @return 是否含有危险字符
     */
    public static boolean containsDangerousChars(String input) {
        if (input == null) {
            return false;
        }
        return DANGEROUS_CHARS_PATTERN.matcher(input).find();
    }

    /**
     * 安全截取字符串长度。
     *
     * @param input  原始字符串
     * @param maxLen 最大允许长度
     * @return 截取后的字符串；若 input 为 null 返回 null
     */
    public static String truncate(String input, int maxLen) {
        if (input == null) {
            return null;
        }
        if (input.length() <= maxLen) {
            return input;
        }
        return input.substring(0, maxLen);
    }
}
