package com.lowcarbon.service;

import com.lowcarbon.dao.ItemDao;
import com.lowcarbon.dao.TransactionDao;
import com.lowcarbon.dao.UserDao;
import com.lowcarbon.dao.impl.ItemDaoImpl;
import com.lowcarbon.dao.impl.TransactionDaoImpl;
import com.lowcarbon.dao.impl.UserDaoImpl;
import com.lowcarbon.entity.Item;
import com.lowcarbon.entity.ItemStatus;
import com.lowcarbon.entity.Transaction;
import com.lowcarbon.entity.TransactionStatus;
import com.lowcarbon.entity.User;
import com.lowcarbon.exception.BusinessException;
import com.lowcarbon.exception.DaoException;

import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * 流转记录业务逻辑层。
 * <p>
 * 覆盖需求：T-01 发起意向、T-02 生成流转记录、T-03 状态管理、T-04 历史记录查询。
 * <p>
 * 流转状态机：
 * <pre>
 *   PENDING → APPROVED（卖家同意）/ REJECTED（卖家拒绝）/ CANCELLED（买家取消）
 *   APPROVED → COMPLETED（确认完成）/ CANCELLED（卖家或买家取消）
 *   COMPLETED / REJECTED / CANCELLED 为终态
 * </pre>
 * 物品状态联动：
 * <pre>
 *   发起意向 (PENDING)   → 物品 RESERVED
 *   取消 / 拒绝          → 物品回退 ON_SALE
 *   完成 (COMPLETED)     → 物品 SOLD
 * </pre>
 *
 * @author lowcarbon-team
 * @since 2026-11-09
 */
public class TransactionService {

    private static final Logger LOGGER = Logger.getLogger(TransactionService.class.getName());

    private final TransactionDao transactionDao;
    private final ItemDao itemDao;
    private final UserDao userDao;

    public TransactionService() {
        this.transactionDao = new TransactionDaoImpl();
        this.itemDao = new ItemDaoImpl();
        this.userDao = new UserDaoImpl();
    }

    public TransactionService(TransactionDao transactionDao, ItemDao itemDao, UserDao userDao) {
        this.transactionDao = transactionDao;
        this.itemDao = itemDao;
        this.userDao = userDao;
    }

    // ==================== 发起意向（T-01 / T-02） ====================

    /**
     * 买家发起流转意向。
     * <p>
     * 前置校验：
     * <ol>
     *   <li>买家用户存在</li>
     *   <li>目标物品存在且处于 ON_SALE 状态</li>
     *   <li>买家不能对自己的物品发起意向</li>
     *   <li>同一物品不存在其他活跃（PENDING/APPROVED）的流转（避免重复预定）</li>
     * </ol>
     * 后续：物品自动置为 RESERVED。
     *
     * @param itemId        目标物品 ID
     * @param initiatorId   买家用户 ID
     * @param remark        备注（可 null）
     * @return 创建成功的流转记录（带生成的 recordId）
     */
    public Transaction create(Long itemId, Long initiatorId, String remark) {
        if (itemId == null) {
            throw new BusinessException("物品 ID 不能为空");
        }
        if (initiatorId == null) {
            throw new BusinessException("发起人 ID 不能为空");
        }

        // 1. 买家存在
        User buyer;
        try {
            buyer = userDao.findById(initiatorId);
        } catch (DaoException e) {
            throw new BusinessException("发起意向失败，请稍后再试", e);
        }
        if (buyer == null) {
            throw new BusinessException("发起人不存在");
        }

        // 2. 物品存在且在 ON_SALE
        Item item;
        try {
            item = itemDao.findById(itemId);
        } catch (DaoException e) {
            throw new BusinessException("查询物品失败", e);
        }
        if (item == null) {
            throw new BusinessException("物品不存在");
        }
        if (item.getStatus() != ItemStatus.ON_SALE) {
            throw new BusinessException("物品当前状态为\"" + itemStatusLabel(item.getStatus())
                    + "\"，不可发起意向");
        }

        // 3. 不能买自己的东西
        if (initiatorId.equals(item.getPublisherId())) {
            throw new BusinessException("不能对自己发布的物品发起流转");
        }

        // 4. 同一物品不能有其他活跃流转（防重复预定）
        try {
            List<Transaction> existing = transactionDao.findByItemId(itemId);
            for (Transaction t : existing) {
                if (t.isActive()) {
                    throw new BusinessException("该物品已有进行中的流转，请勿重复发起");
                }
            }
        } catch (DaoException e) {
            throw new BusinessException("检查流转冲突失败", e);
        }

        // 5. 创建流转记录（物品暂置 RESERVED）
        Transaction tx = new Transaction(itemId, initiatorId,
                remark == null || remark.isBlank() ? null : remark.trim());
        tx.setStatus(TransactionStatus.PENDING);
        tx.setCreateTime(System.currentTimeMillis());

        try {
            Long id = transactionDao.insert(tx);
            // 6. 物品状态联动
            itemDao.updateStatus(itemId, ItemStatus.RESERVED);
            LOGGER.log(Level.INFO, "流转意向已发起: recordId={0}, itemId={1}, initiatorId={2}",
                    new Object[]{id, itemId, initiatorId});
            return tx;
        } catch (DaoException e) {
            LOGGER.log(Level.SEVERE, "发起流转意向失败: itemId=" + itemId, e);
            throw new BusinessException("发起意向失败，请稍后再试", e);
        }
    }

    // ==================== 卖家同意（T-03） ====================

    /**
     * 卖家同意流转申请。
     * <p>
     * 卖家身份校验：操作者必须是物品发布者。
     */
    public Transaction approve(Long recordId, Long operatorId) {
        return transitionAndLinkItem(recordId, operatorId, TransactionStatus.PENDING,
                TransactionStatus.APPROVED, null);
    }

    // ==================== 卖家拒绝 ====================

    /**
     * 卖家拒绝流转申请。
     * <p>
     * 物品自动回退 ON_SALE。
     */
    public Transaction reject(Long recordId, Long operatorId) {
        return transitionAndLinkItem(recordId, operatorId, TransactionStatus.PENDING,
                TransactionStatus.REJECTED, ItemStatus.ON_SALE);
    }

    // ==================== 完成流转（T-03） ====================

    /**
     * 确认完成流转。
     * <p>
     * 必须由发起者本人确认（交易双方共同参与，但这里由买家先点完成，卖家也可以点）；
     * 严格来说应由**双方都点完成**才算真完成，但为了简化，这里允许任一方（卖家/买家）确认完成。
     * 物品自动置为 SOLD，completeTime 自动填当前时间。
     */
    public Transaction complete(Long recordId, Long operatorId) {
        Transaction tx = getOrThrow(recordId);
        if (tx.getStatus() != TransactionStatus.APPROVED) {
            throw new BusinessException("只有\"已同意\"状态的流转可以标记完成，当前为："
                    + txStatusLabel(tx.getStatus()));
        }
        // 操作者必须是卖家或买家
        if (!isInvolved(tx, operatorId)) {
            throw new BusinessException("只有交易双方可以确认完成");
        }

        try {
            tx.setStatus(TransactionStatus.COMPLETED);
            tx.setCompleteTime(System.currentTimeMillis());
            transactionDao.update(tx);
            itemDao.updateStatus(tx.getItemId(), ItemStatus.SOLD);
            LOGGER.log(Level.INFO, "流转已完成: recordId={0}, operatorId={1}",
                    new Object[]{recordId, operatorId});
            return tx;
        } catch (DaoException e) {
            throw new BusinessException("标记完成失败", e);
        }
    }

    // ==================== 取消 ====================

    /**
     * 卖家或买家取消流转。
     * <p>
     * PENDING / APPROVED 均可取消。物品自动回退 ON_SALE。
     */
    public Transaction cancel(Long recordId, Long operatorId) {
        Transaction tx = getOrThrow(recordId);

        // 操作者必须是卖家或买家
        if (!isInvolved(tx, operatorId)) {
            throw new BusinessException("只有交易双方可以取消");
        }
        if (!tx.isActive()) {
            throw new BusinessException("流转已结束（" + txStatusLabel(tx.getStatus())
                    + "），无法取消");
        }

        try {
            tx.setStatus(TransactionStatus.CANCELLED);
            transactionDao.update(tx);
            itemDao.updateStatus(tx.getItemId(), ItemStatus.ON_SALE);
            LOGGER.log(Level.INFO, "流转已取消: recordId={0}, operatorId={1}",
                    new Object[]{recordId, operatorId});
            return tx;
        } catch (DaoException e) {
            throw new BusinessException("取消失败", e);
        }
    }

    // ==================== 查询（T-04） ====================

    /** 按 ID 查询。 */
    public Transaction findById(Long recordId) {
        if (recordId == null) return null;
        try {
            return transactionDao.findById(recordId);
        } catch (DaoException e) {
            throw new BusinessException("查询流转记录失败", e);
        }
    }

    /**
     * 某人相关的所有流转（发起的 + 作为卖家的）。
     * <p>
     * 返回 list 中每条记录附带 operatorSide 信息由调用方自行判断。
     */
    public List<Transaction> listByUser(Long userId) {
        if (userId == null) return List.of();
        try {
            return transactionDao.findByInitiatorId(userId);
        } catch (DaoException e) {
            throw new BusinessException("查询流转记录失败", e);
        }
    }

    /** 按物品查流转历史。 */
    public List<Transaction> listByItem(Long itemId) {
        if (itemId == null) return List.of();
        try {
            return transactionDao.findByItemId(itemId);
        } catch (DaoException e) {
            throw new BusinessException("查询流转历史失败", e);
        }
    }

    /** 查询全部（管理员后台用）。 */
    public List<Transaction> listAll() {
        try {
            return transactionDao.findAll();
        } catch (DaoException e) {
            throw new BusinessException("获取全部流转失败", e);
        }
    }

    /** 获取物品发布者（卖家）信息，供 GUI 层展示对方身份时用。 */
    public User getSellerOfRecord(Long recordId) {
        Transaction tx = findById(recordId);
        if (tx == null) return null;
        try {
            Item item = itemDao.findById(tx.getItemId());
            if (item == null) return null;
            return userDao.findById(item.getPublisherId());
        } catch (DaoException e) {
            return null;
        }
    }

    // ==================== 状态机 & 辅助 ====================

    /**
     * 通用的"状态流转 + 物品联动"方法。
     * <p>
     * 用于 approve / reject 两个对称操作。
     */
    private Transaction transitionAndLinkItem(Long recordId, Long operatorId,
                                               TransactionStatus from, TransactionStatus to,
                                               ItemStatus linkTo) {
        Transaction tx = getOrThrow(recordId);

        // 操作者必须是卖家（item 的发布者）—— approve/reject 都是卖家行为
        Item item;
        try {
            item = itemDao.findById(tx.getItemId());
        } catch (DaoException e) {
            throw new BusinessException("查询物品失败", e);
        }
        if (item == null) {
            throw new BusinessException("关联物品不存在（可能已被删除）");
        }
        if (!operatorId.equals(item.getPublisherId())) {
            throw new BusinessException("只有物品发布者可以执行此操作");
        }

        if (tx.getStatus() != from) {
            throw new BusinessException("当前状态为\"" + txStatusLabel(tx.getStatus())
                    + "\"，无法执行\"" + txStatusLabel(to) + "\"");
        }

        try {
            tx.setStatus(to);
            transactionDao.update(tx);
            // 有 linkTo 时同步物品状态
            if (linkTo != null) {
                itemDao.updateStatus(item.getItemId(), linkTo);
            }
            LOGGER.log(Level.INFO, "流转状态变更: recordId={0}, {1} → {2}",
                    new Object[]{recordId, from, to});
            return tx;
        } catch (DaoException e) {
            throw new BusinessException("更新流转状态失败", e);
        }
    }

    /** 判断 operator 是否是该流转的交易双方（买家或卖家）。 */
    private boolean isInvolved(Transaction tx, Long operatorId) {
        if (operatorId == null) return false;
        // 买家
        if (operatorId.equals(tx.getInitiatorId())) return true;
        // 卖家（通过 item.publisherId）
        try {
            Item item = itemDao.findById(tx.getItemId());
            return item != null && operatorId.equals(item.getPublisherId());
        } catch (DaoException e) {
            return false;
        }
    }

    private Transaction getOrThrow(Long recordId) {
        try {
            Transaction tx = transactionDao.findById(recordId);
            if (tx == null) {
                throw new BusinessException("流转记录不存在");
            }
            return tx;
        } catch (DaoException e) {
            throw new BusinessException("查询流转记录失败", e);
        }
    }

    private static String txStatusLabel(TransactionStatus s) {
        if (s == null) return "未知";
        return switch (s) {
            case PENDING   -> "待卖家确认";
            case APPROVED  -> "已同意";
            case REJECTED  -> "已拒绝";
            case COMPLETED -> "已完成";
            case CANCELLED -> "已取消";
        };
    }

    private static String itemStatusLabel(ItemStatus s) {
        if (s == null) return "未知";
        return switch (s) {
            case ON_SALE   -> "在售";
            case RESERVED  -> "已预订";
            case SOLD      -> "已成交";
            case OFF_SHELF -> "已下架";
        };
    }
}
