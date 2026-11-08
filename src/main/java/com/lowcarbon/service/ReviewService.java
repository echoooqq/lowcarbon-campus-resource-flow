package com.lowcarbon.service;

import com.lowcarbon.dao.ItemDao;
import com.lowcarbon.dao.ReviewDao;
import com.lowcarbon.dao.TransactionDao;
import com.lowcarbon.entity.Item;
import com.lowcarbon.entity.Review;
import com.lowcarbon.entity.Transaction;
import com.lowcarbon.entity.TransactionStatus;
import com.lowcarbon.exception.BusinessException;
import com.lowcarbon.exception.DaoException;
import com.lowcarbon.factory.DaoFactory;
import com.lowcarbon.factory.ServiceFactory;
import com.lowcarbon.util.Validator;

import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * 评价业务逻辑层。
 * <p>
 * 覆盖需求：R-01 双向评价、R-02 信用分更新、R-03 查看评价。
 * <p>
 * 评价规则：
 * <ol>
 *   <li>只有 COMPLETED 状态的流转可以评价</li>
 *   <li>评价人必须是交易双方（买家或卖家）之一</li>
 *   <li>评价对象必须是交易对方</li>
 *   <li>每个交易双方各自最多评一次（双向各一条 Review 记录）</li>
 *   <li>评分 1-5 星</li>
 * </ol>
 * 信用分更新：
 * <ul>
 *   <li>被评价方（reviewee）信用分 += (rating - 3)，即 5 星 +2、4 星 +1、3 星不变、2 星 -1、1 星 -2</li>
 *   <li>评价方（reviewer）信用分 +1（鼓励参与评价，防刷差评成本）</li>
 * </ul>
 *
 * @author lowcarbon-team
 * @since 2026-11-09
 */
public class ReviewService {

    private static final Logger LOGGER = Logger.getLogger(ReviewService.class.getName());

    /** 评分下限。 */
    public static final int MIN_RATING = 1;

    /** 评分上限。 */
    public static final int MAX_RATING = 5;

    /** reviewer 每次评价自己得的信用分。 */
    public static final int REVIEWER_CREDIT_BONUS = 1;

    private final ReviewDao reviewDao;
    private final TransactionDao transactionDao;
    private final ItemDao itemDao;
    private final UserService userService;

    public ReviewService() {
        this.reviewDao = DaoFactory.getReviewDao();
        this.transactionDao = DaoFactory.getTransactionDao();
        this.itemDao = DaoFactory.getItemDao();
        // UserService 由 ServiceFactory 负责装配完整依赖
        this.userService = ServiceFactory.getUserService();
    }

    /**
     * 测试用注入构造器（需传 Fake DAO + Fake UserService）。
     * <p>
     * UserService 也走注入路径时，通常测试里直接注入一个 FakeUserService 会更方便。
     */
    public ReviewService(ReviewDao reviewDao, TransactionDao transactionDao,
                         ItemDao itemDao, UserService userService) {
        this.reviewDao = reviewDao;
        this.transactionDao = transactionDao;
        this.itemDao = itemDao;
        this.userService = userService;
    }

    // ==================== 提交评价（R-01 / R-02） ====================

    /**
     * 提交一条评价。
     *
     * @param recordId   流转记录 ID
     * @param reviewerId 评价人用户 ID（必须是买家或卖家）
     * @param rating     评分 1-5
     * @param comment    评价文字（可 null）
     * @return 创建成功的评价对象
     */
    public Review submit(Long recordId, Long reviewerId, int rating, String comment) {
        // 1. 参数非空
        if (recordId == null) throw new BusinessException("流转记录 ID 不能为空");
        if (reviewerId == null) throw new BusinessException("评价人 ID 不能为空");

        // 2. 评分范围
        if (rating < MIN_RATING || rating > MAX_RATING) {
            throw new BusinessException("评分必须在 " + MIN_RATING + " ~ " + MAX_RATING + " 之间");
        }

        // 3. 评论长度校验（可选字段，不强制非空）
        if (comment != null) {
            String err = Validator.validateComment(comment);
            // validateComment 只在 blank 时报错，允许评论非空所以只检查长度
            if (err != null && err.contains("最多")) {
                throw new BusinessException("评价内容超过长度限制");
            }
        }

        // 4. 流转记录必须存在且为 COMPLETED
        Transaction tx;
        try {
            tx = transactionDao.findById(recordId);
        } catch (DaoException e) {
            throw new BusinessException("查询流转记录失败", e);
        }
        if (tx == null) {
            throw new BusinessException("流转记录不存在");
        }
        if (tx.getStatus() != TransactionStatus.COMPLETED) {
            throw new BusinessException("只有\"已完成\"的流转可以评价，当前为：" + txStatusLabel(tx.getStatus()));
        }

        // 5. 评价人必须是交易双方之一
        //    买家 = initiatorId；卖家 = item.publisherId
        Item item;
        try {
            item = itemDao.findById(tx.getItemId());
        } catch (DaoException e) {
            throw new BusinessException("查询物品失败", e);
        }
        if (item == null) {
            throw new BusinessException("关联物品不存在");
        }

        Long revieweeId;
        if (reviewerId.equals(tx.getInitiatorId())) {
            // 买家评卖家
            revieweeId = item.getPublisherId();
        } else if (reviewerId.equals(item.getPublisherId())) {
            // 卖家评买家
            revieweeId = tx.getInitiatorId();
        } else {
            throw new BusinessException("您不是该交易的参与者，无权评价");
        }

        // 6. 防重复评价：同一个 reviewer 对同一条 record 只能评一次
        try {
            boolean alreadyReviewed = reviewDao.findByReviewerId(reviewerId).stream()
                    .anyMatch(r -> recordId.equals(r.getRecordId()));
            if (alreadyReviewed) {
                throw new BusinessException("您已对该交易评价过，不能重复评价");
            }
        } catch (DaoException e) {
            throw new BusinessException("防重复检查失败", e);
        }

        // 7. 创建评价
        String finalComment = comment == null ? null : comment.trim();
        if (finalComment != null && finalComment.isBlank()) {
            finalComment = null; // 全空白评论当作没写
        }

        Review review = new Review(recordId, reviewerId, revieweeId,
                rating, finalComment);
        review.setCreateTime(System.currentTimeMillis());

        try {
            Long id = reviewDao.insert(review);

            // 8. 更新信用分（双向）
            //    reviewee: 信用分 += (rating - 3)
            int revieweeDelta = rating - 3;
            userService.adjustCreditScore(revieweeId, revieweeDelta);
            //    reviewer: 信用分 +1
            userService.adjustCreditScore(reviewerId, REVIEWER_CREDIT_BONUS);

            LOGGER.log(Level.INFO, "评价已提交: reviewId={0}, recordId={1}, reviewerId={2}, rating={3}",
                    new Object[]{id, recordId, reviewerId, rating});
            return review;
        } catch (DaoException e) {
            LOGGER.log(Level.SEVERE, "提交评价失败: recordId=" + recordId, e);
            throw new BusinessException("评价提交失败，请稍后再试", e);
        } catch (BusinessException e) {
            // adjustCreditScore 内部可能抛 BusinessException（用户不存在）
            LOGGER.log(Level.WARNING, "评价已入库但信用分更新失败: " + e.getMessage());
            throw e;
        }
    }

    // ==================== 查询（R-03） ====================

    /** 按评价 ID 查询。 */
    public Review findById(Long reviewId) {
        if (reviewId == null) return null;
        try {
            return reviewDao.findById(reviewId);
        } catch (DaoException e) {
            throw new BusinessException("查询评价失败", e);
        }
    }

    /**
     * 按流转记录查询评价列表（双向评价会有多条）。
     */
    public List<Review> findByRecordId(Long recordId) {
        if (recordId == null) return List.of();
        try {
            return reviewDao.listByRecordId(recordId);
        } catch (DaoException e) {
            throw new BusinessException("查询评价失败", e);
        }
    }

    /** 某人收到的全部评价（被评价人视角）。 */
    public List<Review> findByReviewee(Long revieweeId) {
        if (revieweeId == null) return List.of();
        try {
            return reviewDao.findByRevieweeId(revieweeId);
        } catch (DaoException e) {
            throw new BusinessException("查询评价失败", e);
        }
    }

    /** 某人发出的全部评价（评价人视角）。 */
    public List<Review> findByReviewer(Long reviewerId) {
        if (reviewerId == null) return List.of();
        try {
            return reviewDao.findByReviewerId(reviewerId);
        } catch (DaoException e) {
            throw new BusinessException("查询评价失败", e);
        }
    }

    /** 计算某用户的平均评价分数（收到的评价）。 */
    public double averageRatingFor(Long userId) {
        List<Review> reviews = findByReviewee(userId);
        if (reviews.isEmpty()) return 0.0;
        double sum = 0;
        for (Review r : reviews) sum += r.getRating();
        return Math.round(sum / reviews.size() * 100.0) / 100.0;
    }

    /** 全部评价数（管理员 / 统计用）。 */
    public int countReviews() {
        try {
            return reviewDao.count();
        } catch (DaoException e) {
            throw new BusinessException("统计失败", e);
        }
    }

    // ==================== 辅助 ====================

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
}
