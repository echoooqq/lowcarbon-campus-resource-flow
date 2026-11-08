package com.lowcarbon.dao;

import com.lowcarbon.entity.Review;

import java.util.List;

/**
 * 评价数据访问接口，定义对 {@code review} 表的查询 + 写入操作。
 * <p>
 * 评价只允许新增和查询，不允许修改和删除（防恶意操作）。
 *
 * @author lowcarbon-team
 * @since 2026-11-09
 */
public interface ReviewDao {

    /**
     * 按评价 ID 查询。
     *
     * @param reviewId 评价 ID
     * @return 评价对象，不存在返回 null
     */
    Review findById(Long reviewId);

    /**
     * 查询全部评价（管理员/统计用）。
     *
     * @return 评价列表
     */
    List<Review> findAll();

    /**
     * 按流转记录 ID 查询全部评价（双向评价会有多条，一笔交易双方各一条）。
     *
     * @param recordId 流转记录 ID
     * @return 评价列表；无则返回空列表
     */
    List<Review> listByRecordId(Long recordId);

    /**
     * 按流转记录 ID 查询（仅返回一条，见 {@link #listByRecordId}）。
     *
     * @deprecated 双向评价场景请改用 {@link #listByRecordId(Long)}
     */
    @Deprecated
    Review findByRecordId(Long recordId);

    /**
     * 按被评价人（卖家）查询（某人收到的所有评价）。
     *
     * @param revieweeId 被评价人用户 ID
     * @return 评价列表
     */
    List<Review> findByRevieweeId(Long revieweeId);

    /**
     * 按评价人查询（某人发出的所有评价）。
     *
     * @param reviewerId 评价人用户 ID
     * @return 评价列表
     */
    List<Review> findByReviewerId(Long reviewerId);

    /**
     * 新增评价。
     *
     * @param review 待新增评价（reviewId 由数据库自增）
     * @return 数据库生成的自增 ID
     */
    Long insert(Review review);

    /**
     * 统计评价总数。
     *
     * @return 评价数
     */
    int count();
}
