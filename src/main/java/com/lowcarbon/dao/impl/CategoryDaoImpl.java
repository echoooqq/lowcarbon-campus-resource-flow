package com.lowcarbon.dao.impl;

import com.lowcarbon.config.DatabaseManager;
import com.lowcarbon.dao.CategoryDao;
import com.lowcarbon.entity.Category;
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
 * 分类 DAO 的 JDBC 实现。
 *
 * @author lowcarbon-team
 * @since 2026-11-09
 */
public class CategoryDaoImpl implements CategoryDao {

    private static final Logger LOGGER = Logger.getLogger(CategoryDaoImpl.class.getName());

    // ==================== SQL 常量 ====================

    private static final String SQL_FIND_BY_ID =
            "SELECT category_id, name, description FROM category WHERE category_id = ?";

    private static final String SQL_FIND_BY_NAME =
            "SELECT category_id, name, description FROM category WHERE name = ?";

    private static final String SQL_FIND_ALL =
            "SELECT category_id, name, description FROM category ORDER BY category_id ASC";

    private static final String SQL_INSERT =
            "INSERT INTO category (name, description) VALUES (?, ?)";

    private static final String SQL_UPDATE =
            "UPDATE category SET name = ?, description = ? WHERE category_id = ?";

    private static final String SQL_DELETE =
            "DELETE FROM category WHERE category_id = ?";

    private static final String SQL_COUNT =
            "SELECT COUNT(*) FROM category";

    // ==================== CRUD ====================

    @Override
    public Category findById(Long categoryId) {
        if (categoryId == null) {
            return null;
        }
        try (Connection conn = DatabaseManager.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(SQL_FIND_BY_ID)) {
            ps.setLong(1, categoryId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return resultSetToCategory(rs);
                }
                return null;
            }
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "按 ID 查询分类失败: categoryId=" + categoryId, e);
            throw new DaoException("按 ID 查询分类失败: " + e.getMessage(), e);
        }
    }

    @Override
    public Category findByName(String name) {
        if (name == null || name.isBlank()) {
            return null;
        }
        try (Connection conn = DatabaseManager.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(SQL_FIND_BY_NAME)) {
            ps.setString(1, name);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return resultSetToCategory(rs);
                }
                return null;
            }
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "按名称查询分类失败: name=" + name, e);
            throw new DaoException("按名称查询分类失败: " + e.getMessage(), e);
        }
    }

    @Override
    public List<Category> findAll() {
        List<Category> list = new ArrayList<>();
        try (Connection conn = DatabaseManager.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(SQL_FIND_ALL);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                list.add(resultSetToCategory(rs));
            }
            return list;
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "查询全部分类失败", e);
            throw new DaoException("查询全部分类失败: " + e.getMessage(), e);
        }
    }

    @Override
    public Long insert(Category category) {
        try (Connection conn = DatabaseManager.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(SQL_INSERT, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, category.getName());
            ps.setString(2, category.getDescription());
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    Long id = keys.getLong(1);
                    category.setCategoryId(id);
                    return id;
                }
            }
            throw new DaoException("插入分类后未获取到自增 ID");
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "新增分类失败: name=" + category.getName(), e);
            throw new DaoException("新增分类失败: " + e.getMessage(), e);
        }
    }

    @Override
    public void update(Category category) {
        if (category.getCategoryId() == null) {
            throw new DaoException("更新分类失败：categoryId 为 null");
        }
        try (Connection conn = DatabaseManager.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(SQL_UPDATE)) {
            ps.setString(1, category.getName());
            ps.setString(2, category.getDescription());
            ps.setLong(3, category.getCategoryId());
            int affected = ps.executeUpdate();
            if (affected == 0) {
                LOGGER.log(Level.WARNING, "更新分类影响行数为 0: categoryId=" + category.getCategoryId());
            }
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "更新分类失败: categoryId=" + category.getCategoryId(), e);
            throw new DaoException("更新分类失败: " + e.getMessage(), e);
        }
    }

    @Override
    public void delete(Long categoryId) {
        if (categoryId == null) {
            return;
        }
        try (Connection conn = DatabaseManager.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(SQL_DELETE)) {
            ps.setLong(1, categoryId);
            int affected = ps.executeUpdate();
            if (affected == 0) {
                LOGGER.log(Level.WARNING, "删除分类影响行数为 0: categoryId=" + categoryId);
            }
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "删除分类失败: categoryId=" + categoryId, e);
            throw new DaoException("删除分类失败: " + e.getMessage(), e);
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
            LOGGER.log(Level.SEVERE, "统计分类数失败", e);
            throw new DaoException("统计分类数失败: " + e.getMessage(), e);
        }
    }

    // ==================== ResultSet → Entity ====================

    private Category resultSetToCategory(ResultSet rs) throws SQLException {
        Category c = new Category();
        c.setCategoryId(rs.getLong("category_id"));
        c.setName(rs.getString("name"));
        c.setDescription(rs.getString("description"));
        return c;
    }
}
