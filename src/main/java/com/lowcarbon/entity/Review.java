package com.lowcarbon.entity;

import java.util.Objects;

/**
 * 评价实体类，对应数据库 {@code review} 表。
 * <p>
 * 字段映射：
 * <ul>
 *   <li>{@code review_id}   → {@link #reviewId}</li>
 *   <li>{@code record_id}   → {@link #recordId}（外键 → transaction_record.record_id）</li>
 *   <li>{@code reviewer_id} → {@link #reviewerId}（评价人，外键 → user.user_id）</li>
 *   <li>{@code reviewee_id} → {@link #revieweeId}（被评价人，外键 → user.user_id）</li>
 *   <li>{@code rating}      → {@link #rating}（1-5 星）</li>
 *   <li>{@code comment}     → {@link #comment}（可选）</li>
 *   <li>{@code create_time} → {@link #createTime}</li>
 * </ul>
 *
 * @author lowcarbon-team
 * @since 2026-11-08
 */
public class Review {

    /** 评价 ID（主键，自增）。 */
    private Long reviewId;

    /** 关联流转记录 ID（外键 → transaction_record.record_id）。 */
    private Long recordId;

    /** 评价人用户 ID（外键 → user.user_id）。 */
    private Long reviewerId;

    /** 被评价人用户 ID（外键 → user.user_id）。 */
    private Long revieweeId;

    /** 评分（1-5 星，数据库 CHECK 约束）。 */
    private int rating;

    /** 评价文字内容（可选）。 */
    private String comment;

    /** 评价时间（Unix 毫秒时间戳）。 */
    private long createTime;

    // ==================== 构造器 ====================

    /**
     * 无参构造器。
     */
    public Review() {
    }

    /**
     * 提交评价时使用的构造器（reviewId 由数据库自增，createTime 由 Service 层填充）。
     *
     * @param recordId   关联的流转记录 ID
     * @param reviewerId 评价人
     * @param revieweeId 被评价人
     * @param rating     评分（1-5）
     * @param comment    评价文字（可 null）
     */
    public Review(Long recordId, Long reviewerId, Long revieweeId, int rating, String comment) {
        this.recordId = recordId;
        this.reviewerId = reviewerId;
        this.revieweeId = revieweeId;
        this.rating = rating;
        this.comment = comment;
    }

    /**
     * 全参构造器。
     */
    public Review(Long reviewId, Long recordId, Long reviewerId, Long revieweeId,
                  int rating, String comment, long createTime) {
        this.reviewId = reviewId;
        this.recordId = recordId;
        this.reviewerId = reviewerId;
        this.revieweeId = revieweeId;
        this.rating = rating;
        this.comment = comment;
        this.createTime = createTime;
    }

    // ==================== Getter / Setter ====================

    public Long getReviewId() {
        return reviewId;
    }

    public void setReviewId(Long reviewId) {
        this.reviewId = reviewId;
    }

    public Long getRecordId() {
        return recordId;
    }

    public void setRecordId(Long recordId) {
        this.recordId = recordId;
    }

    public Long getReviewerId() {
        return reviewerId;
    }

    public void setReviewerId(Long reviewerId) {
        this.reviewerId = reviewerId;
    }

    public Long getRevieweeId() {
        return revieweeId;
    }

    public void setRevieweeId(Long revieweeId) {
        this.revieweeId = revieweeId;
    }

    public int getRating() {
        return rating;
    }

    public void setRating(int rating) {
        this.rating = rating;
    }

    public String getComment() {
        return comment;
    }

    public void setComment(String comment) {
        this.comment = comment;
    }

    public long getCreateTime() {
        return createTime;
    }

    public void setCreateTime(long createTime) {
        this.createTime = createTime;
    }

    // ==================== 便捷方法 ====================

    /**
     * 判断评分是否在有效范围内（1-5）。
     *
     * @return true 表示合法
     */
    public boolean isRatingValid() {
        return rating >= 1 && rating <= 5;
    }

    // ==================== equals / hashCode / toString ====================

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Review other)) {
            return false;
        }
        return Objects.equals(reviewId, other.reviewId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(reviewId);
    }

    @Override
    public String toString() {
        return "Review{"
                + "reviewId=" + reviewId
                + ", recordId=" + recordId
                + ", reviewerId=" + reviewerId
                + ", revieweeId=" + revieweeId
                + ", rating=" + rating
                + ", comment='" + comment + '\''
                + '}';
    }
}
