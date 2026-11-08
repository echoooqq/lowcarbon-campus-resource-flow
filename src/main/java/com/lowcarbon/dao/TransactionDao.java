package com.lowcarbon.dao;

import com.lowcarbon.entity.Transaction;
import com.lowcarbon.entity.TransactionStatus;

import java.util.List;

/**
 * 流转记录数据访问接口，定义对 {@code transaction_record} 表的 CRUD + 查询操作。
 *
 * @author lowcarbon-team
 * @since 2026-11-09
 */
public interface TransactionDao {

    /**
     * 按流转记录 ID 查询。
     *
     * @param recordId 记录 ID
     * @return 流转记录对象，不存在返回 null
     */
    Transaction findById(Long recordId);

    /**
     * 查询全部流转记录（按创建时间倒序）。
     *
     * @return 流转记录列表
     */
    List<Transaction> findAll();

    /**
     * 按物品 ID 查询（某物品的所有流转历史）。
     *
     * @param itemId 物品 ID
     * @return 流转记录列表
     */
    List<Transaction> findByItemId(Long itemId);

    /**
     * 按发起人（买家）用户 ID 查询（某人发起的所有流转）。
     *
     * @param initiatorId 发起人用户 ID
     * @return 流转记录列表
     */
    List<Transaction> findByInitiatorId(Long initiatorId);

    /**
     * 按状态查询。
     *
     * @param status 流转状态
     * @return 流转记录列表
     */
    List<Transaction> findByStatus(TransactionStatus status);

    /**
     * 新增流转记录。
     *
     * @param transaction 待新增记录（recordId 由数据库自增）
     * @return 数据库生成的自增 ID
     */
    Long insert(Transaction transaction);

    /**
     * 更新流转记录（状态变更、完成时间填写等）。
     *
     * @param transaction 带更新内容的记录对象（必须已带 recordId）
     */
    void update(Transaction transaction);

    /**
     * 按记录 ID 删除。
     *
     * @param recordId 记录 ID
     */
    void delete(Long recordId);

    /**
     * 统计流转记录总数。
     *
     * @return 记录数
     */
    int count();
}
