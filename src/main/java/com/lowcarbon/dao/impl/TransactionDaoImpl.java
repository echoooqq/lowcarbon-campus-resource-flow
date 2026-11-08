package com.lowcarbon.dao.impl;

import com.lowcarbon.config.DatabaseManager;
import com.lowcarbon.dao.TransactionDao;
import com.lowcarbon.entity.Transaction;
import com.lowcarbon.entity.TransactionStatus;
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
 * 流转记录 DAO 的 JDBC 实现。
 *
 * @author lowcarbon-team
 * @since 2026-11-09
 */
public class TransactionDaoImpl implements TransactionDao {

    private static final Logger LOGGER = Logger.getLogger(TransactionDaoImpl.class.getName());

    // ==================== SQL 常量 ====================

    private static final String COLUMNS =
            "record_id, item_id, initiator_id, status, create_time, complete_time, remark";

    private static final String SQL_FIND_BY_ID =
            "SELECT " + COLUMNS + " FROM transaction_record WHERE record_id = ?";

    private static final String SQL_FIND_ALL =
            "SELECT " + COLUMNS + " FROM transaction_record ORDER BY create_time DESC";

    private static final String SQL_FIND_BY_ITEM =
            "SELECT " + COLUMNS + " FROM transaction_record WHERE item_id = ? ORDER BY create_time DESC";

    private static final String SQL_FIND_BY_INITIATOR =
            "SELECT " + COLUMNS + " FROM transaction_record WHERE initiator_id = ? ORDER BY create_time DESC";

    private static final String SQL_FIND_BY_STATUS =
            "SELECT " + COLUMNS + " FROM transaction_record WHERE status = ? ORDER BY create_time DESC";

    private static final String SQL_INSERT =
            "INSERT INTO transaction_record (item_id, initiator_id, status, create_time, complete_time, remark) "
            + "VALUES (?, ?, ?, ?, ?, ?)";

    private static final String SQL_UPDATE =
            "UPDATE transaction_record SET status = ?, complete_time = ?, remark = ? WHERE record_id = ?";

    private static final String SQL_DELETE =
            "DELETE FROM transaction_record WHERE record_id = ?";

    private static final String SQL_COUNT =
            "SELECT COUNT(*) FROM transaction_record";

    // ==================== CRUD ====================

    @Override
    public Transaction findById(Long recordId) {
        if (recordId == null) {
            return null;
        }
        try (Connection conn = DatabaseManager.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(SQL_FIND_BY_ID)) {
            ps.setLong(1, recordId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return resultSetToTransaction(rs);
                }
                return null;
            }
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "按 ID 查询流转记录失败: recordId=" + recordId, e);
            throw new DaoException("按 ID 查询流转记录失败: " + e.getMessage(), e);
        }
    }

    @Override
    public List<Transaction> findAll() {
        return queryList(SQL_FIND_ALL, ps -> { });
    }

    @Override
    public List<Transaction> findByItemId(Long itemId) {
        if (itemId == null) {
            return new ArrayList<>();
        }
        return queryList(SQL_FIND_BY_ITEM, ps -> ps.setLong(1, itemId));
    }

    @Override
    public List<Transaction> findByInitiatorId(Long initiatorId) {
        if (initiatorId == null) {
            return new ArrayList<>();
        }
        return queryList(SQL_FIND_BY_INITIATOR, ps -> ps.setLong(1, initiatorId));
    }

    @Override
    public List<Transaction> findByStatus(TransactionStatus status) {
        if (status == null) {
            return new ArrayList<>();
        }
        return queryList(SQL_FIND_BY_STATUS, ps -> ps.setString(1, status.getCode()));
    }

    @Override
    public Long insert(Transaction transaction) {
        try (Connection conn = DatabaseManager.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(SQL_INSERT, Statement.RETURN_GENERATED_KEYS)) {
            ps.setLong(1, transaction.getItemId());
            ps.setLong(2, transaction.getInitiatorId());
            ps.setString(3, transaction.getStatus() != null ? transaction.getStatus().getCode() : TransactionStatus.PENDING.getCode());
            ps.setLong(4, transaction.getCreateTime());
            ps.setObject(5, transaction.getCompleteTime());
            ps.setString(6, transaction.getRemark());
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    Long id = keys.getLong(1);
                    transaction.setRecordId(id);
                    return id;
                }
            }
            throw new DaoException("插入流转记录后未获取到自增 ID");
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "新增流转记录失败: itemId=" + transaction.getItemId(), e);
            throw new DaoException("新增流转记录失败: " + e.getMessage(), e);
        }
    }

    @Override
    public void update(Transaction transaction) {
        if (transaction.getRecordId() == null) {
            throw new DaoException("更新流转记录失败：recordId 为 null");
        }
        try (Connection conn = DatabaseManager.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(SQL_UPDATE)) {
            ps.setString(1, transaction.getStatus() != null ? transaction.getStatus().getCode() : TransactionStatus.PENDING.getCode());
            ps.setObject(2, transaction.getCompleteTime());
            ps.setString(3, transaction.getRemark());
            ps.setLong(4, transaction.getRecordId());
            int affected = ps.executeUpdate();
            if (affected == 0) {
                LOGGER.log(Level.WARNING, "更新流转记录影响行数为 0: recordId=" + transaction.getRecordId());
            }
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "更新流转记录失败: recordId=" + transaction.getRecordId(), e);
            throw new DaoException("更新流转记录失败: " + e.getMessage(), e);
        }
    }

    @Override
    public void delete(Long recordId) {
        if (recordId == null) {
            return;
        }
        try (Connection conn = DatabaseManager.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(SQL_DELETE)) {
            ps.setLong(1, recordId);
            int affected = ps.executeUpdate();
            if (affected == 0) {
                LOGGER.log(Level.WARNING, "删除流转记录影响行数为 0: recordId=" + recordId);
            }
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "删除流转记录失败: recordId=" + recordId, e);
            throw new DaoException("删除流转记录失败: " + e.getMessage(), e);
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
            LOGGER.log(Level.SEVERE, "统计流转记录数失败", e);
            throw new DaoException("统计流转记录数失败: " + e.getMessage(), e);
        }
    }

    // ==================== 通用查询辅助 ====================

    private List<Transaction> queryList(String sql, SqlBinder binder) {
        List<Transaction> list = new ArrayList<>();
        try (Connection conn = DatabaseManager.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            binder.bind(ps);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(resultSetToTransaction(rs));
                }
            }
            return list;
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "查询流转记录列表失败: sql=" + sql, e);
            throw new DaoException("查询流转记录列表失败: " + e.getMessage(), e);
        }
    }

    @FunctionalInterface
    private interface SqlBinder {
        void bind(PreparedStatement ps) throws SQLException;
    }

    // ==================== ResultSet → Entity ====================

    private Transaction resultSetToTransaction(ResultSet rs) throws SQLException {
        Transaction t = new Transaction();
        t.setRecordId(rs.getLong("record_id"));
        t.setItemId(rs.getLong("item_id"));
        t.setInitiatorId(rs.getLong("initiator_id"));
        t.setStatus(TransactionStatus.fromCode(rs.getString("status")));
        t.setCreateTime(rs.getLong("create_time"));
        // complete_time 是可空字段，用 wasNull 判断
        long completeLong = rs.getLong("complete_time");
        t.setCompleteTime(rs.wasNull() ? null : completeLong);
        t.setRemark(rs.getString("remark"));
        return t;
    }
}
