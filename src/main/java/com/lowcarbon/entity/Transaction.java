package com.lowcarbon.entity;

import java.util.Objects;

/**
 * 流转记录实体类，对应数据库 {@code transaction_record} 表。
 * <p>
 * 字段映射：
 * <ul>
 *   <li>{@code record_id}     → {@link #recordId}</li>
 *   <li>{@code item_id}       → {@link #itemId}</li>
 *   <li>{@code initiator_id}  → {@link #initiatorId}</li>
 *   <li>{@code status}        → {@link #status}（通过 {@link TransactionStatus} 枚举表达）</li>
 *   <li>{@code create_time}   → {@link #createTime}</li>
 *   <li>{@code complete_time} → {@link #completeTime}（可选，使用包装类表示可 null）</li>
 *   <li>{@code remark}        → {@link #remark}</li>
 * </ul>
 *
 * @author lowcarbon-team
 * @since 2026-11-08
 */
public class Transaction {

    /** 流转记录 ID（主键，自增）。 */
    private Long recordId;

    /** 关联物品 ID（外键 → item.item_id）。 */
    private Long itemId;

    /** 发起人用户 ID（通常是买家，外键 → user.user_id）。 */
    private Long initiatorId;

    /** 流转当前状态。 */
    private TransactionStatus status;

    /** 创建时间（Unix 毫秒时间戳）。 */
    private long createTime;

    /** 完成时间（Unix 毫秒时间戳，流转未完成时为 null）。 */
    private Long completeTime;

    /** 备注（可选，可填买家留言或卖家说明）。 */
    private String remark;

    // ==================== 构造器 ====================

    /**
     * 无参构造器。
     */
    public Transaction() {
    }

    /**
     * 发起流转申请时使用的构造器（recordId 由数据库自增，status 默认 PENDING，
     * createTime 默认由 Service 层填充，completeTime 初始为 null）。
     *
     * @param itemId      目标物品 ID
     * @param initiatorId 发起人用户 ID
     * @param remark      备注（可 null）
     */
    public Transaction(Long itemId, Long initiatorId, String remark) {
        this.itemId = itemId;
        this.initiatorId = initiatorId;
        this.status = TransactionStatus.PENDING;
        this.remark = remark;
    }

    /**
     * 全参构造器。
     */
    public Transaction(Long recordId, Long itemId, Long initiatorId,
                       TransactionStatus status, long createTime,
                       Long completeTime, String remark) {
        this.recordId = recordId;
        this.itemId = itemId;
        this.initiatorId = initiatorId;
        this.status = status;
        this.createTime = createTime;
        this.completeTime = completeTime;
        this.remark = remark;
    }

    // ==================== Getter / Setter ====================

    public Long getRecordId() {
        return recordId;
    }

    public void setRecordId(Long recordId) {
        this.recordId = recordId;
    }

    public Long getItemId() {
        return itemId;
    }

    public void setItemId(Long itemId) {
        this.itemId = itemId;
    }

    public Long getInitiatorId() {
        return initiatorId;
    }

    public void setInitiatorId(Long initiatorId) {
        this.initiatorId = initiatorId;
    }

    public TransactionStatus getStatus() {
        return status;
    }

    public void setStatus(TransactionStatus status) {
        this.status = status;
    }

    public long getCreateTime() {
        return createTime;
    }

    public void setCreateTime(long createTime) {
        this.createTime = createTime;
    }

    public Long getCompleteTime() {
        return completeTime;
    }

    public void setCompleteTime(Long completeTime) {
        this.completeTime = completeTime;
    }

    public String getRemark() {
        return remark;
    }

    public void setRemark(String remark) {
        this.remark = remark;
    }

    // ==================== 便捷方法 ====================

    /**
     * 判断该流转是否已结束（完成或取消）。
     *
     * @return true 表示已终结
     */
    public boolean isFinished() {
        return status == TransactionStatus.COMPLETED
                || status == TransactionStatus.CANCELLED
                || status == TransactionStatus.REJECTED;
    }

    /**
     * 判断该流转是否还在进行中。
     *
     * @return true 表示待处理或已同意
     */
    public boolean isActive() {
        return status == TransactionStatus.PENDING
                || status == TransactionStatus.APPROVED;
    }

    // ==================== equals / hashCode / toString ====================

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Transaction other)) {
            return false;
        }
        return Objects.equals(recordId, other.recordId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(recordId);
    }

    @Override
    public String toString() {
        return "Transaction{"
                + "recordId=" + recordId
                + ", itemId=" + itemId
                + ", initiatorId=" + initiatorId
                + ", status=" + status
                + ", createTime=" + createTime
                + ", completeTime=" + completeTime
                + '}';
    }
}
