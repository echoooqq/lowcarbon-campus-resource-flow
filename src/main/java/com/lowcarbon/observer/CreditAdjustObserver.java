package com.lowcarbon.observer;

import com.lowcarbon.entity.Item;
import com.lowcarbon.entity.ItemStatus;
import com.lowcarbon.exception.DaoException;
import com.lowcarbon.service.UserService;

import java.util.function.LongFunction;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * 信用分联动观察者——当物品被 OFF_SHELF 时自动扣发布者信用分。
 * <p>
 * 应用场景：
 * <ul>
 *   <li>管理员强制下架违规物品（A-02）：扣 5 分</li>
 *   <li>发布者主动下架正常物品（I-03）：不扣分</li>
 * </ul>
 * <p>
 * 本观察者只在 transitionStatus() 被调用时触发，不区分是主动下架还是管理员强撤——
 * 实际业务中管理员强撤走的是 adminTakeOffShelf，其中也会调 transitionStatus，所以这里统一扣。
 * 未来如果不想让发布者主动下架扣分，可以在 Subject 通知时带一个"是否管理员操作"标记。
 * <p>
 * 依赖注入：本观察者需要 UserService 来执行扣信用分，以及一个 Item 查询函数来获取发布者 ID。
 * 采用构造器注入 + {@code LongFunction<Item>} 接口，方便测试。
 *
 * @author lowcarbon-team
 * @since 2026-11-09
 */
public class CreditAdjustObserver implements ItemStatusObserver {

    private static final Logger LOGGER = Logger.getLogger(CreditAdjustObserver.class.getName());

    /** 物品下架时扣除的信用分。 */
    private static final int SHELF_OFF_PENALTY = 5;

    private final UserService userService;
    /** itemId → Item 的查询函数（避免观察者依赖完整的 ItemDao 接口）。 */
    private final LongFunction<Item> itemLookup;

    /**
     * 构造信用分观察者。
     *
     * @param userService 信用分操作服务
     * @param itemLookup  物品查询函数（如 itemDao::findById）
     */
    public CreditAdjustObserver(UserService userService, LongFunction<Item> itemLookup) {
        this.userService = userService;
        this.itemLookup = itemLookup;
    }

    @Override
    public void onStatusChanged(Long itemId, ItemStatus oldStatus, ItemStatus newStatus) {
        // 只在 → OFF_SHELF 的变更时触发
        if (newStatus != ItemStatus.OFF_SHELF) {
            return;
        }
        try {
            Item item = itemLookup.apply(itemId);
            if (item == null || item.getPublisherId() == null) {
                LOGGER.warning("信用分观察者：物品或发布者不存在，跳过扣分。itemId=" + itemId);
                return;
            }
            userService.adjustCreditScore(item.getPublisherId(), -SHELF_OFF_PENALTY);
            LOGGER.log(Level.INFO, "信用分观察者：物品 {0} 下架，发布者 userId={1} 信用分扣 {2} 分",
                    new Object[]{itemId, item.getPublisherId(), SHELF_OFF_PENALTY});
        } catch (Exception e) {
            // 观察者异常不应中断主业务流程，只记录日志
            LOGGER.log(Level.WARNING, "信用分观察者执行失败（不影响主流程）: itemId=" + itemId, e);
        }
    }
}
