package com.lowcarbon.dao.impl;

import com.lowcarbon.config.DatabaseManager;
import com.lowcarbon.dao.ItemDao;
import com.lowcarbon.entity.Item;
import com.lowcarbon.entity.ItemStatus;
import com.lowcarbon.entity.TradeType;
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
 * 物品 DAO 的 JDBC 实现。
 *
 * @author lowcarbon-team
 * @since 2026-11-09
 */
public class ItemDaoImpl implements ItemDao {

    private static final Logger LOGGER = Logger.getLogger(ItemDaoImpl.class.getName());

    // ==================== SQL 常量 ====================

    /** SELECT 列表（避免重复书写）。 */
    private static final String COLUMNS =
            "item_id, publisher_id, name, category_id, description, price, "
            + "trade_type, status, tags, publish_time";

    private static final String SQL_FIND_BY_ID =
            "SELECT " + COLUMNS + " FROM item WHERE item_id = ?";

    private static final String SQL_FIND_ALL =
            "SELECT " + COLUMNS + " FROM item ORDER BY publish_time DESC";

    private static final String SQL_FIND_BY_PUBLISHER =
            "SELECT " + COLUMNS + " FROM item WHERE publisher_id = ? ORDER BY publish_time DESC";

    private static final String SQL_FIND_BY_CATEGORY =
            "SELECT " + COLUMNS + " FROM item WHERE category_id = ? ORDER BY publish_time DESC";

    private static final String SQL_FIND_BY_STATUS =
            "SELECT " + COLUMNS + " FROM item WHERE status = ? ORDER BY publish_time DESC";

    /** 关键词搜索：名称 LIKE 或 描述 LIKE（SQLite 的 LIKE 默认大小写不敏感）。 */
    private static final String SQL_FIND_BY_KEYWORD =
            "SELECT " + COLUMNS + " FROM item "
            + "WHERE name LIKE ? OR description LIKE ? ORDER BY publish_time DESC";

    private static final String SQL_INSERT =
            "INSERT INTO item (publisher_id, name, category_id, description, price, "
            + "trade_type, status, tags, publish_time) "
            + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";

    private static final String SQL_UPDATE =
            "UPDATE item SET name = ?, category_id = ?, description = ?, price = ?, "
            + "trade_type = ?, status = ?, tags = ? WHERE item_id = ?";

    private static final String SQL_DELETE =
            "DELETE FROM item WHERE item_id = ?";

    private static final String SQL_UPDATE_STATUS =
            "UPDATE item SET status = ? WHERE item_id = ?";

    private static final String SQL_COUNT =
            "SELECT COUNT(*) FROM item";

    private static final String SQL_FIND_BY_PRICE_RANGE =
            "SELECT " + COLUMNS + " FROM item "
            + "WHERE price >= ? AND price <= ? ORDER BY publish_time DESC";

    // ==================== CRUD ====================

    @Override
    public Item findById(Long itemId) {
        if (itemId == null) {
            return null;
        }
        try (Connection conn = DatabaseManager.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(SQL_FIND_BY_ID)) {
            ps.setLong(1, itemId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return resultSetToItem(rs);
                }
                return null;
            }
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "按 ID 查询物品失败: itemId=" + itemId, e);
            throw new DaoException("按 ID 查询物品失败: " + e.getMessage(), e);
        }
    }

    @Override
    public List<Item> findAll() {
        return queryList(SQL_FIND_ALL, ps -> { /* 无参数 */ });
    }

    @Override
    public List<Item> findByPublisherId(Long publisherId) {
        if (publisherId == null) {
            return new ArrayList<>();
        }
        return queryList(SQL_FIND_BY_PUBLISHER, ps -> ps.setLong(1, publisherId));
    }

    @Override
    public List<Item> findByCategoryId(Long categoryId) {
        if (categoryId == null) {
            return new ArrayList<>();
        }
        return queryList(SQL_FIND_BY_CATEGORY, ps -> ps.setLong(1, categoryId));
    }

    @Override
    public List<Item> findByStatus(ItemStatus status) {
        if (status == null) {
            return new ArrayList<>();
        }
        return queryList(SQL_FIND_BY_STATUS, ps -> ps.setString(1, status.getCode()));
    }

    @Override
    public List<Item> findByKeyword(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return new ArrayList<>();
        }
        // 用 %keyword% 实现模糊匹配
        String likePattern = "%" + keyword.trim() + "%";
        return queryList(SQL_FIND_BY_KEYWORD, ps -> {
            ps.setString(1, likePattern);
            ps.setString(2, likePattern);
        });
    }

    @Override
    public Long insert(Item item) {
        try (Connection conn = DatabaseManager.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(SQL_INSERT, Statement.RETURN_GENERATED_KEYS)) {
            ps.setLong(1, item.getPublisherId());
            ps.setString(2, item.getName());
            ps.setLong(3, item.getCategoryId());
            ps.setString(4, item.getDescription());
            ps.setDouble(5, item.getPrice());
            ps.setString(6, item.getTradeType() != null ? item.getTradeType().getCode() : TradeType.SELL.getCode());
            ps.setString(7, item.getStatus() != null ? item.getStatus().getCode() : ItemStatus.ON_SALE.getCode());
            ps.setString(8, item.getTagsStr());
            ps.setLong(9, item.getPublishTime());
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    Long id = keys.getLong(1);
                    item.setItemId(id);
                    return id;
                }
            }
            throw new DaoException("插入物品后未获取到自增 ID");
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "新增物品失败: name=" + item.getName(), e);
            throw new DaoException("新增物品失败: " + e.getMessage(), e);
        }
    }

    @Override
    public void update(Item item) {
        if (item.getItemId() == null) {
            throw new DaoException("更新物品失败：itemId 为 null");
        }
        try (Connection conn = DatabaseManager.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(SQL_UPDATE)) {
            ps.setString(1, item.getName());
            ps.setLong(2, item.getCategoryId());
            ps.setString(3, item.getDescription());
            ps.setDouble(4, item.getPrice());
            ps.setString(5, item.getTradeType() != null ? item.getTradeType().getCode() : TradeType.SELL.getCode());
            ps.setString(6, item.getStatus() != null ? item.getStatus().getCode() : ItemStatus.ON_SALE.getCode());
            ps.setString(7, item.getTagsStr());
            ps.setLong(8, item.getItemId());
            int affected = ps.executeUpdate();
            if (affected == 0) {
                LOGGER.log(Level.WARNING, "更新物品影响行数为 0: itemId=" + item.getItemId());
            }
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "更新物品失败: itemId=" + item.getItemId(), e);
            throw new DaoException("更新物品失败: " + e.getMessage(), e);
        }
    }

    @Override
    public void delete(Long itemId) {
        if (itemId == null) {
            return;
        }
        try (Connection conn = DatabaseManager.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(SQL_DELETE)) {
            ps.setLong(1, itemId);
            int affected = ps.executeUpdate();
            if (affected == 0) {
                LOGGER.log(Level.WARNING, "删除物品影响行数为 0: itemId=" + itemId);
            }
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "删除物品失败: itemId=" + itemId, e);
            throw new DaoException("删除物品失败: " + e.getMessage(), e);
        }
    }

    @Override
    public List<Item> findByPriceRange(double minPrice, double maxPrice) {
        if (maxPrice < minPrice) {
            LOGGER.log(Level.WARNING, "价格区间无效: minPrice={0}, maxPrice={1}",
                    new Object[]{minPrice, maxPrice});
            return new ArrayList<>();
        }
        return queryList(SQL_FIND_BY_PRICE_RANGE, ps -> {
            ps.setDouble(1, minPrice);
            ps.setDouble(2, maxPrice);
        });
    }

    @Override
    public List<Item> search(String keyword, Long categoryId, double minPrice, double maxPrice, ItemStatus status) {
        // 动态拼接 WHERE 条件（比写 2^5=32 个 SQL 更可维护）
        StringBuilder sql = new StringBuilder("SELECT ").append(COLUMNS).append(" FROM item WHERE 1=1");
        List<Object> params = new ArrayList<>();

        boolean hasKeyword = keyword != null && !keyword.isBlank();
        boolean hasCategory = categoryId != null;
        boolean hasMinPrice = minPrice >= 0;
        boolean hasMaxPrice = maxPrice >= 0;
        boolean hasStatus = status != null;

        if (hasKeyword) {
            sql.append(" AND (name LIKE ? OR description LIKE ?)");
            String like = "%" + keyword.trim() + "%";
            params.add(like);
            params.add(like);
        }
        if (hasCategory) {
            sql.append(" AND category_id = ?");
            params.add(categoryId);
        }
        if (hasMinPrice) {
            sql.append(" AND price >= ?");
            params.add(minPrice);
        }
        if (hasMaxPrice) {
            sql.append(" AND price <= ?");
            params.add(maxPrice);
        }
        if (hasStatus) {
            sql.append(" AND status = ?");
            params.add(status.getCode());
        }
        sql.append(" ORDER BY publish_time DESC");

        final List<Object> finalParams = params;
        return queryList(sql.toString(), ps -> {
            for (int i = 0; i < finalParams.size(); i++) {
                ps.setObject(i + 1, finalParams.get(i));
            }
        });
    }

    @Override
    public void updateStatus(Long itemId, ItemStatus status) {
        if (itemId == null || status == null) {
            return;
        }
        try (Connection conn = DatabaseManager.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(SQL_UPDATE_STATUS)) {
            ps.setString(1, status.getCode());
            ps.setLong(2, itemId);
            int affected = ps.executeUpdate();
            if (affected == 0) {
                LOGGER.log(Level.WARNING, "更新物品状态影响行数为 0: itemId=" + itemId);
            }
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "更新物品状态失败: itemId=" + itemId + ", status=" + status, e);
            throw new DaoException("更新物品状态失败: " + e.getMessage(), e);
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
            LOGGER.log(Level.SEVERE, "统计物品数失败", e);
            throw new DaoException("统计物品数失败: " + e.getMessage(), e);
        }
    }

    // ==================== 通用查询辅助 ====================

    /**
     * 统一的列表查询模板，避免每个 findXxx 方法重复写 try-with-resources + 遍历。
     *
     * @param sql    带 ? 占位符的 SQL
     * @param binder 绑定参数的 lambda（Consumer<PreparedStatement>）
     * @return 实体列表
     */
    private List<Item> queryList(String sql, SqlBinder binder) {
        List<Item> list = new ArrayList<>();
        try (Connection conn = DatabaseManager.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            binder.bind(ps);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(resultSetToItem(rs));
                }
            }
            return list;
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "查询物品列表失败: sql=" + sql, e);
            throw new DaoException("查询物品列表失败: " + e.getMessage(), e);
        }
    }

    /**
     * 单参数 PreparedStatement 绑定器。
     */
    @FunctionalInterface
    private interface SqlBinder {
        void bind(PreparedStatement ps) throws SQLException;
    }

    // ==================== ResultSet → Entity ====================

    private Item resultSetToItem(ResultSet rs) throws SQLException {
        Item item = new Item();
        item.setItemId(rs.getLong("item_id"));
        item.setPublisherId(rs.getLong("publisher_id"));
        item.setName(rs.getString("name"));
        item.setCategoryId(rs.getLong("category_id"));
        item.setDescription(rs.getString("description"));
        item.setPrice(rs.getDouble("price"));
        item.setTradeType(TradeType.fromCode(rs.getString("trade_type")));
        item.setStatus(ItemStatus.fromCode(rs.getString("status")));
        item.setTagsStr(rs.getString("tags"));
        item.setPublishTime(rs.getLong("publish_time"));
        return item;
    }
}
