package com.lowcarbon.dao.impl;

import com.lowcarbon.config.DatabaseManager;
import com.lowcarbon.dao.UserDao;
import com.lowcarbon.entity.User;
import com.lowcarbon.entity.UserRole;
import com.lowcarbon.exception.DaoException;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * 用户 DAO 的 JDBC 实现。
 *
 * @author lowcarbon-team
 * @since 2026-11-09
 */
public class UserDaoImpl implements UserDao {

    private static final Logger LOGGER = Logger.getLogger(UserDaoImpl.class.getName());

    // ==================== SQL 常量 ====================

    private static final String SQL_FIND_BY_ID =
            "SELECT user_id, username, password_hash, salt, role, credit_score, create_time "
            + "FROM user WHERE user_id = ?";

    private static final String SQL_FIND_BY_USERNAME =
            "SELECT user_id, username, password_hash, salt, role, credit_score, create_time "
            + "FROM user WHERE username = ?";

    private static final String SQL_FIND_ALL =
            "SELECT user_id, username, password_hash, salt, role, credit_score, create_time "
            + "FROM user ORDER BY create_time DESC";

    private static final String SQL_INSERT =
            "INSERT INTO user (username, password_hash, salt, role, credit_score, create_time) "
            + "VALUES (?, ?, ?, ?, ?, ?)";

    private static final String SQL_UPDATE =
            "UPDATE user SET username = ?, password_hash = ?, salt = ?, role = ?, credit_score = ? "
            + "WHERE user_id = ?";

    private static final String SQL_DELETE =
            "DELETE FROM user WHERE user_id = ?";

    private static final String SQL_COUNT =
            "SELECT COUNT(*) FROM user";

    // ==================== CRUD ====================

    @Override
    public User findById(Long userId) {
        if (userId == null) {
            return null;
        }
        try (Connection conn = DatabaseManager.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(SQL_FIND_BY_ID)) {
            ps.setLong(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return resultSetToUser(rs);
                }
                return null;
            }
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "按 ID 查询用户失败: userId=" + userId, e);
            throw new DaoException("按 ID 查询用户失败: " + e.getMessage(), e);
        }
    }

    @Override
    public User findByUsername(String username) {
        if (username == null || username.isBlank()) {
            return null;
        }
        try (Connection conn = DatabaseManager.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(SQL_FIND_BY_USERNAME)) {
            ps.setString(1, username);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return resultSetToUser(rs);
                }
                return null;
            }
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "按用户名查询用户失败: username=" + username, e);
            throw new DaoException("按用户名查询用户失败: " + e.getMessage(), e);
        }
    }

    @Override
    public List<User> findAll() {
        List<User> list = new ArrayList<>();
        try (Connection conn = DatabaseManager.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(SQL_FIND_ALL);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                list.add(resultSetToUser(rs));
            }
            return list;
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "查询全部用户失败", e);
            throw new DaoException("查询全部用户失败: " + e.getMessage(), e);
        }
    }

    @Override
    public Long insert(User user) {
        try (Connection conn = DatabaseManager.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(SQL_INSERT, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, user.getUsername());
            ps.setString(2, user.getPasswordHash());
            ps.setString(3, user.getSalt());
            ps.setString(4, user.getRole() != null ? user.getRole().getCode() : UserRole.USER.getCode());
            ps.setInt(5, user.getCreditScore());
            ps.setLong(6, user.getCreateTime());
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    Long id = keys.getLong(1);
                    user.setUserId(id);
                    return id;
                }
            }
            throw new DaoException("插入用户后未获取到自增 ID");
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "新增用户失败: username=" + user.getUsername(), e);
            throw new DaoException("新增用户失败: " + e.getMessage(), e);
        }
    }

    @Override
    public void update(User user) {
        if (user.getUserId() == null) {
            throw new DaoException("更新用户失败：userId 为 null");
        }
        try (Connection conn = DatabaseManager.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(SQL_UPDATE)) {
            ps.setString(1, user.getUsername());
            ps.setString(2, user.getPasswordHash());
            ps.setString(3, user.getSalt());
            ps.setString(4, user.getRole() != null ? user.getRole().getCode() : UserRole.USER.getCode());
            ps.setInt(5, user.getCreditScore());
            ps.setLong(6, user.getUserId());
            int affected = ps.executeUpdate();
            if (affected == 0) {
                LOGGER.log(Level.WARNING, "更新用户影响行数为 0: userId=" + user.getUserId());
            }
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "更新用户失败: userId=" + user.getUserId(), e);
            throw new DaoException("更新用户失败: " + e.getMessage(), e);
        }
    }

    @Override
    public void delete(Long userId) {
        if (userId == null) {
            return;
        }
        try (Connection conn = DatabaseManager.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(SQL_DELETE)) {
            ps.setLong(1, userId);
            int affected = ps.executeUpdate();
            if (affected == 0) {
                LOGGER.log(Level.WARNING, "删除用户影响行数为 0: userId=" + userId);
            }
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "删除用户失败: userId=" + userId, e);
            throw new DaoException("删除用户失败: " + e.getMessage(), e);
        }
    }

    @Override
    public int count() {
        try (Connection conn = DatabaseManager.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(SQL_COUNT);
             ResultSet rs = ps.executeQuery()) {
            if (rs.next()) {
                return rs.getInt(1);
            }
            return 0;
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "统计用户数失败", e);
            throw new DaoException("统计用户数失败: " + e.getMessage(), e);
        }
    }

    // ==================== ResultSet → Entity ====================

    /**
     * 将 ResultSet 当前行转为 User 对象。
     *
     * @param rs 已定位到有效行的 ResultSet
     * @return User 实体
     * @throws SQLException 字段读取异常
     */
    private User resultSetToUser(ResultSet rs) throws SQLException {
        User user = new User();
        user.setUserId(rs.getLong("user_id"));
        user.setUsername(rs.getString("username"));
        user.setPasswordHash(rs.getString("password_hash"));
        user.setSalt(rs.getString("salt"));
        user.setRole(UserRole.fromCode(rs.getString("role")));
        user.setCreditScore(rs.getInt("credit_score"));
        user.setCreateTime(rs.getLong("create_time"));
        return user;
    }
}
