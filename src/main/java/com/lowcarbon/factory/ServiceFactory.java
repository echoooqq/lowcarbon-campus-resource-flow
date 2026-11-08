package com.lowcarbon.factory;

import com.lowcarbon.service.AdminService;
import com.lowcarbon.service.ItemService;
import com.lowcarbon.service.ReviewService;
import com.lowcarbon.service.StatisticsService;
import com.lowcarbon.service.TransactionService;
import com.lowcarbon.service.UserService;

/**
 * Service 工厂类——集中创建业务逻辑层实例（带单例缓存）。
 * <p>
 * 应用：工厂模式 + 单例模式。
 * <p>
 * 设计决策：Service 实例在 JVM 生命周期内只创建一次（静态 final 字段缓存）。
 * 原因：
 * <ol>
 *   <li>Service 是无状态的业务逻辑容器，内部可变状态仅限于 {@link ItemService} 的观察者列表——
 *       而观察者列表必须在多个调用方之间共享，因此 ItemService 实例必须全局唯一</li>
 *   <li>AdminService / ReviewService 等复合 Service 依赖基础 Service，
 *       缓存后确保所有调用方持有的是同一组依赖实例，避免观察者注册丢失、数据不一致等问题</li>
 *   <li>DAO 层每次 new 新实例（参考 {@link DaoFactory} 的注释），保持轻量</li>
 * </ol>
 * <p>
 * 依赖装配顺序（按拓扑排序——基础 Service 先造，复合 Service 后造）：
 * <pre>
 *   UserService          ← 仅 UserDao
 *   ItemService          ← ItemDao, UserDao, CategoryDao
 *   TransactionService   ← TransactionDao, ItemDao, UserDao
 *   StatisticsService    ← 5 个 DAO
 *   ReviewService        ← ReviewDao, TransactionDao, ItemDao, UserService
 *   AdminService         ← UserDao, ItemDao, UserService, ItemService, StatisticsService
 * </pre>
 *
 * @author lowcarbon-team
 * @since 2026-11-09
 */
public final class ServiceFactory {

    // ==================== 基础 Service（先造） ====================

    private static final UserService USER_SERVICE =
            new UserService(DaoFactory.getUserDao());

    private static final ItemService ITEM_SERVICE =
            new ItemService(
                    DaoFactory.getItemDao(),
                    DaoFactory.getUserDao(),
                    DaoFactory.getCategoryDao()
            );

    private static final TransactionService TRANSACTION_SERVICE =
            new TransactionService(
                    DaoFactory.getTransactionDao(),
                    DaoFactory.getItemDao(),
                    DaoFactory.getUserDao()
            );

    private static final StatisticsService STATISTICS_SERVICE =
            new StatisticsService(
                    DaoFactory.getUserDao(),
                    DaoFactory.getItemDao(),
                    DaoFactory.getTransactionDao(),
                    DaoFactory.getReviewDao(),
                    DaoFactory.getCategoryDao()
            );

    // ==================== 复合 Service（依赖基础 Service，后造） ====================

    private static final ReviewService REVIEW_SERVICE =
            new ReviewService(
                    DaoFactory.getReviewDao(),
                    DaoFactory.getTransactionDao(),
                    DaoFactory.getItemDao(),
                    USER_SERVICE
            );

    private static final AdminService ADMIN_SERVICE =
            new AdminService(
                    DaoFactory.getUserDao(),
                    DaoFactory.getItemDao(),
                    USER_SERVICE,
                    ITEM_SERVICE,
                    STATISTICS_SERVICE
            );

    // ==================== 工厂方法 ====================

    private ServiceFactory() {
        // 工具类禁止实例化
    }

    /** 获取用户 Service（全局单例）。 */
    public static UserService getUserService() {
        return USER_SERVICE;
    }

    /** 获取物品 Service（全局单例，观察者列表在此实例上维护）。 */
    public static ItemService getItemService() {
        return ITEM_SERVICE;
    }

    /** 获取流转 Service（全局单例）。 */
    public static TransactionService getTransactionService() {
        return TRANSACTION_SERVICE;
    }

    /** 获取评价 Service（全局单例）。 */
    public static ReviewService getReviewService() {
        return REVIEW_SERVICE;
    }

    /** 获取统计 Service（全局单例）。 */
    public static StatisticsService getStatisticsService() {
        return STATISTICS_SERVICE;
    }

    /** 获取管理员 Service（全局单例，内部持有的是上面同一组基础 Service）。 */
    public static AdminService getAdminService() {
        return ADMIN_SERVICE;
    }
}
