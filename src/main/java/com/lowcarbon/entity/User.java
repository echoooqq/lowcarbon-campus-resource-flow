package com.lowcarbon.entity;

import java.util.Objects;

/**
 * 用户实体类，对应数据库 {@code user} 表。
 * <p>
 * 字段映射：
 * <ul>
 *   <li>{@code user_id}       → {@link #userId}</li>
 *   <li>{@code username}      → {@link #username}</li>
 *   <li>{@code password_hash} → {@link #passwordHash}</li>
 *   <li>{@code salt}          → {@link #salt}</li>
 *   <li>{@code role}          → {@link #role}（通过 {@link UserRole} 枚举表达）</li>
 *   <li>{@code credit_score}  → {@link #creditScore}</li>
 *   <li>{@code create_time}   → {@link #createTime}</li>
 * </ul>
 *
 * @author lowcarbon-team
 * @since 2026-11-08
 */
public class User {

    /** 用户 ID（主键，自增）。 */
    private Long userId;

    /** 登录用户名（唯一）。 */
    private String username;

    /** 密码哈希值（SHA-256 加盐后）。 */
    private String passwordHash;

    /** 密码盐值。 */
    private String salt;

    /** 用户角色。 */
    private UserRole role;

    /** 信用分（默认 100）。 */
    private int creditScore;

    /** 账号创建时间（Unix 毫秒时间戳）。 */
    private long createTime;

    // ==================== 构造器 ====================

    /**
     * 无参构造器（DAO 层反射创建对象时使用）。
     */
    public User() {
    }

    /**
     * 注册时使用的构造器（userId 由数据库自增，createTime 由 Service 层填充）。
     *
     * @param username     登录用户名
     * @param passwordHash 密码哈希值
     * @param salt         密码盐值
     */
    public User(String username, String passwordHash, String salt) {
        this.username = username;
        this.passwordHash = passwordHash;
        this.salt = salt;
        this.role = UserRole.USER;
        this.creditScore = 100;
    }

    /**
     * 全参构造器。
     */
    public User(Long userId, String username, String passwordHash, String salt,
                UserRole role, int creditScore, long createTime) {
        this.userId = userId;
        this.username = username;
        this.passwordHash = passwordHash;
        this.salt = salt;
        this.role = role;
        this.creditScore = creditScore;
        this.createTime = createTime;
    }

    // ==================== Getter / Setter ====================

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public void setPasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    public String getSalt() {
        return salt;
    }

    public void setSalt(String salt) {
        this.salt = salt;
    }

    public UserRole getRole() {
        return role;
    }

    public void setRole(UserRole role) {
        this.role = role;
    }

    public int getCreditScore() {
        return creditScore;
    }

    public void setCreditScore(int creditScore) {
        this.creditScore = creditScore;
    }

    public long getCreateTime() {
        return createTime;
    }

    public void setCreateTime(long createTime) {
        this.createTime = createTime;
    }

    // ==================== 便捷方法 ====================

    /**
     * 判断是否为管理员。
     *
     * @return true 表示管理员
     */
    public boolean isAdmin() {
        return role == UserRole.ADMIN;
    }

    // ==================== equals / hashCode / toString ====================

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof User other)) {
            return false;
        }
        return Objects.equals(userId, other.userId)
                && Objects.equals(username, other.username);
    }

    @Override
    public int hashCode() {
        return Objects.hash(userId, username);
    }

    @Override
    public String toString() {
        return "User{"
                + "userId=" + userId
                + ", username='" + username + '\''
                + ", role=" + role
                + ", creditScore=" + creditScore
                + '}';
    }
}
