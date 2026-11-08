package com.lowcarbon.dao.impl;

import com.lowcarbon.config.DatabaseManager;
import com.lowcarbon.dao.ReviewDao;
import com.lowcarbon.entity.Review;
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
 * 评价 DAO 的 JDBC 实现。
 *
 * @author lowcarbon-team
 * @since 2026-11-09
 */
public class ReviewDaoImpl implements ReviewDao {

    private static final Logger LOGGER = Logger.getLogger(ReviewDaoImpl.class.getName());

    // ==================== SQL 常量 ====================

    private static final String COLUMNS =
            "review_id, record_id, reviewer_id, reviewee_id, rating, comment, create_time";

    private static final String SQL_FIND_BY_ID =
            "SELECT " + COLUMNS + " FROM review WHERE review_id = ?";

    private static final String SQL_FIND_BY_RECORD =
            "SELECT " + COLUMNS + " FROM review WHERE record_id = ? ORDER BY create_time DESC";

    private static final String SQL_FIND_BY_RECORD_SINGLE =
            "SELECT " + COLUMNS + " FROM review WHERE record_id = ? LIMIT 1";

    private static final String SQL_FIND_BY_REVIEWEE =
            "SELECT " + COLUMNS + " FROM review WHERE reviewee_id = ? ORDER BY create_time DESC";

    private static final String SQL_FIND_BY_REVIEWER =
            "SELECT " + COLUMNS + " FROM review WHERE reviewer_id = ? ORDER BY create_time DESC";

    private static final String SQL_INSERT =
            "INSERT INTO review (record_id, reviewer_id, reviewee_id, rating, comment, create_time) "
            + "VALUES (?, ?, ?, ?, ?, ?)";

    private static final String SQL_COUNT =
            "SELECT COUNT(*) FROM review";

    private static final String SQL_FIND_ALL =
            "SELECT " + COLUMNS + " FROM review ORDER BY create_time DESC";

    // ==================== CRUD ====================

    @Override
    public Review findById(Long reviewId) {
        if (reviewId == null) {
            return null;
        }
        try (Connection conn = DatabaseManager.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(SQL_FIND_BY_ID)) {
            ps.setLong(1, reviewId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return resultSetToReview(rs);
                }
                return null;
            }
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "按 ID 查询评价失败: reviewId=" + reviewId, e);
            throw new DaoException("按 ID 查询评价失败: " + e.getMessage(), e);
        }
    }

    @Override
    public List<Review> listByRecordId(Long recordId) {
        if (recordId == null) {
            return new ArrayList<>();
        }
        return queryList(SQL_FIND_BY_RECORD, ps -> ps.setLong(1, recordId));
    }

    @Override
    @Deprecated
    public Review findByRecordId(Long recordId) {
        if (recordId == null) {
            return null;
        }
        try (Connection conn = DatabaseManager.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(SQL_FIND_BY_RECORD_SINGLE)) {
            ps.setLong(1, recordId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return resultSetToReview(rs);
                }
                return null;
            }
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "按流转记录 ID 查询单条评价失败: recordId=" + recordId, e);
            throw new DaoException("按流转记录 ID 查询评价失败: " + e.getMessage(), e);
        }
    }

    @Override
    public List<Review> findByRevieweeId(Long revieweeId) {
        if (revieweeId == null) {
            return new ArrayList<>();
        }
        return queryList(SQL_FIND_BY_REVIEWEE, ps -> ps.setLong(1, revieweeId));
    }

    @Override
    public List<Review> findByReviewerId(Long reviewerId) {
        if (reviewerId == null) {
            return new ArrayList<>();
        }
        return queryList(SQL_FIND_BY_REVIEWER, ps -> ps.setLong(1, reviewerId));
    }

    @Override
    public Long insert(Review review) {
        try (Connection conn = DatabaseManager.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(SQL_INSERT, Statement.RETURN_GENERATED_KEYS)) {
            ps.setLong(1, review.getRecordId());
            ps.setLong(2, review.getReviewerId());
            ps.setLong(3, review.getRevieweeId());
            ps.setInt(4, review.getRating());
            ps.setString(5, review.getComment());
            ps.setLong(6, review.getCreateTime());
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    Long id = keys.getLong(1);
                    review.setReviewId(id);
                    return id;
                }
            }
            throw new DaoException("插入评价后未获取到自增 ID");
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "新增评价失败: recordId=" + review.getRecordId(), e);
            throw new DaoException("新增评价失败: " + e.getMessage(), e);
        }
    }

    @Override
    public List<Review> findAll() {
        return queryList(SQL_FIND_ALL, ps -> {});
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
            LOGGER.log(Level.SEVERE, "统计评价数失败", e);
            throw new DaoException("统计评价数失败: " + e.getMessage(), e);
        }
    }

    // ==================== 通用查询辅助 ====================

    private List<Review> queryList(String sql, SqlBinder binder) {
        List<Review> list = new ArrayList<>();
        try (Connection conn = DatabaseManager.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            binder.bind(ps);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(resultSetToReview(rs));
                }
            }
            return list;
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "查询评价列表失败: sql=" + sql, e);
            throw new DaoException("查询评价列表失败: " + e.getMessage(), e);
        }
    }

    @FunctionalInterface
    private interface SqlBinder {
        void bind(PreparedStatement ps) throws SQLException;
    }

    // ==================== ResultSet → Entity ====================

    private Review resultSetToReview(ResultSet rs) throws SQLException {
        Review r = new Review();
        r.setReviewId(rs.getLong("review_id"));
        r.setRecordId(rs.getLong("record_id"));
        r.setReviewerId(rs.getLong("reviewer_id"));
        r.setRevieweeId(rs.getLong("reviewee_id"));
        r.setRating(rs.getInt("rating"));
        r.setComment(rs.getString("comment"));
        r.setCreateTime(rs.getLong("create_time"));
        return r;
    }
}
