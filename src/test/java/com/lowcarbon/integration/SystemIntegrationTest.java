package com.lowcarbon.integration;

import com.lowcarbon.config.DatabaseManager;
import com.lowcarbon.dao.CategoryDao;
import com.lowcarbon.dao.ItemDao;
import com.lowcarbon.dao.TransactionDao;
import com.lowcarbon.dao.UserDao;
import com.lowcarbon.entity.Category;
import com.lowcarbon.entity.Item;
import com.lowcarbon.entity.ItemStatus;
import com.lowcarbon.entity.Review;
import com.lowcarbon.entity.TradeType;
import com.lowcarbon.entity.Transaction;
import com.lowcarbon.entity.TransactionStatus;
import com.lowcarbon.entity.User;
import com.lowcarbon.entity.UserRole;
import com.lowcarbon.exception.BusinessException;
import com.lowcarbon.factory.DaoFactory;
import com.lowcarbon.factory.ServiceFactory;
import com.lowcarbon.observer.CreditAdjustObserver;
import com.lowcarbon.observer.ItemStatusObserver;
import com.lowcarbon.service.AdminService;
import com.lowcarbon.service.ItemService;
import com.lowcarbon.service.ReviewService;
import com.lowcarbon.service.StatisticsService;
import com.lowcarbon.service.TransactionService;
import com.lowcarbon.service.UserService;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.*;

/**
 * 系统集成测试——跨 Service / DAO / 观察者 / 策略模式的端到端验证。
 * <p>
 * 与单元测试（Fake DAO 隔离）不同，本测试使用：
 * <ul>
 *   <li>{@link DaoFactory} / {@link ServiceFactory} 获取真实实现</li>
 *   <li>SQLite 真实数据库（文件模式，每个测试前 resetForTest 隔离）</li>
 *   <li>验证跨模块协作的完整业务流程正确性</li>
 * </ul>
 * <p>
 * 覆盖设计模式落地验证：
 * <ul>
 *   <li>工厂模式：DaoFactory + ServiceFactory 装配链路</li>
 *   <li>观察者模式：CreditAdjustObserver ↔ ItemService 状态变更联动</li>
 *   <li>策略模式：ItemService.searchOnSale 组合策略在真实数据上的效果</li>
 * </ul>
 *
 * @author lowcarbon-team
 * @since 2026-11-09
 */
public class SystemIntegrationTest {

    // ==================== Service 实例 ====================
    private UserService userService;
    private ItemService itemService;
    private TransactionService transactionService;
    private ReviewService reviewService;
    private StatisticsService statisticsService;
    private AdminService adminService;

    // ==================== DAO 实例（辅助断言用） ====================
    private UserDao userDao;
    private ItemDao itemDao;
    private TransactionDao transactionDao;
    private CategoryDao categoryDao;

    // ==================== 预置数据 ====================
    // 每个测试用 setUp 重置后，以下字段需要在各测试内部构造

    @BeforeClass
    public static void setUpClass() {
        // 测试模式：使用文件数据库（非内存），DatabaseManager 是单例所以要提前设置
        System.setProperty("lowcarbon.db.path", "lowcarbon_unit_test.db");
    }

    @Before
    public void setUp() {
        // 1. 清空数据库 + 恢复预置分类（4 个）
        DatabaseManager.resetForTest();

        // 2. 通过 Factory 获取 Service/DAO 实例——真实装配链路
        userService = ServiceFactory.getUserService();
        itemService = ServiceFactory.getItemService();
        transactionService = ServiceFactory.getTransactionService();
        reviewService = ServiceFactory.getReviewService();
        statisticsService = ServiceFactory.getStatisticsService();
        adminService = ServiceFactory.getAdminService();

        userDao = DaoFactory.getUserDao();
        itemDao = DaoFactory.getItemDao();
        transactionDao = DaoFactory.getTransactionDao();
        categoryDao = DaoFactory.getCategoryDao();
    }

    // =========================================================================
    // 第一部分：完整端到端业务流程
    // =========================================================================

    /**
     * E2E-01：买家注册 → 卖家注册 → 卖家发布物品 → 浏览大厅看到物品 → 买家发起交易
     * → 卖家同意 → 完成交易 → 双方双向评价 → 信用分联动 + 统计数据正确。
     */
    @Test
    public void testFullBuySellFlow_EndToEnd() {
        // --- 1. 注册两个用户 ---
        User buyer = userService.register("buyer01", "pass1234", "pass1234");
        User seller = userService.register("seller01", "pass1234", "pass1234");
        assertNotNull(buyer.getUserId());
        assertNotNull(seller.getUserId());
        assertEquals(UserService.DEFAULT_CREDIT_SCORE, buyer.getCreditScore());
        assertEquals(UserService.DEFAULT_CREDIT_SCORE, seller.getCreditScore());

        // --- 2. 卖家发布物品（使用第一个分类——教材，category_id=1）---
        List<Category> cats = itemService.listAllCategories();
        assertTrue("预置分类应有 4 个", cats.size() >= 4);
        Long bookCatId = cats.get(0).getCategoryId();

        Item item = itemService.publish(seller.getUserId(), "高等数学第七版", bookCatId,
                "九成新，无笔记", 25.0, TradeType.SELL, "教材,考研");
        assertNotNull(item.getItemId());
        assertEquals(ItemStatus.ON_SALE, item.getStatus());

        // --- 3. 浏览大厅应能看到物品 ---
        List<Item> onSale = itemService.listOnSale();
        assertEquals(1, onSale.size());
        assertEquals("高等数学第七版", onSale.get(0).getName());

        // --- 4. 买家发起交易 ---
        Transaction tx = transactionService.create(item.getItemId(), buyer.getUserId(), "求购自取");
        assertNotNull(tx.getRecordId());
        assertEquals(TransactionStatus.PENDING, tx.getStatus());

        // 物品应自动变成 RESERVED
        Item afterCreate = itemService.findById(item.getItemId());
        assertEquals(ItemStatus.RESERVED, afterCreate.getStatus());

        // --- 5. 卖家同意 ---
        Transaction approved = transactionService.approve(tx.getRecordId(), seller.getUserId());
        assertEquals(TransactionStatus.APPROVED, approved.getStatus());

        // --- 6. 完成交易 ---
        Transaction completed = transactionService.complete(approved.getRecordId(), buyer.getUserId());
        assertEquals(TransactionStatus.COMPLETED, completed.getStatus());
        assertNotNull(completed.getCompleteTime());

        // 物品应变成 SOLD
        Item sold = itemService.findById(item.getItemId());
        assertEquals(ItemStatus.SOLD, sold.getStatus());

        // --- 7. 买家评卖家（5 星 +2）---
        Review buyerToSeller = reviewService.submit(completed.getRecordId(),
                buyer.getUserId(), 5, "书很新，交易愉快！");
        assertNotNull(buyerToSeller.getReviewId());

        // --- 8. 卖家评买家（4 星 +1）---
        Review sellerToBuyer = reviewService.submit(completed.getRecordId(),
                seller.getUserId(), 4, "沟通顺畅，推荐！");
        assertNotNull(sellerToBuyer.getReviewId());

        // --- 9. 信用分联动断言 ---
        // 卖家被 5 星评 → 100 + (5-3) = 102；卖家自己评价 +1 → 103
        // 买家被 4 星评 → 100 + (4-3) = 101；买家自己评价 +1 → 102
        User sellerAfter = userService.findById(seller.getUserId());
        User buyerAfter = userService.findById(buyer.getUserId());
        assertEquals("卖家信用分应为 102（5星+2，自己评价+1，共103）", 103, sellerAfter.getCreditScore());
        assertEquals("买家信用分应为 102（4星+1，自己评价+1，共102）", 102, buyerAfter.getCreditScore());

        // --- 10. StatisticsService 端到端验证 ---
        Map<String, Object> overview = statisticsService.overview();
        assertEquals(2, asInt(overview.get("用户总数")));
        assertEquals(1, asInt(overview.get("物品总数")));
        assertEquals(0L, asLong(overview.get("在售物品数")));
        assertEquals(1L, asLong(overview.get("成交物品数")));
        assertEquals(25.0, ((Number) overview.get("成交金额(元)")).doubleValue(), 0.01);
        assertEquals(1, asInt(overview.get("流转总数")));
        assertEquals(1, asInt(overview.get("已完成流转")));
        assertEquals(2, asInt(overview.get("评价总数")));
        assertEquals(4.5, ((Number) overview.get("平均评分")).doubleValue(), 0.01);

        // 低碳指标
        Map<String, Object> carbon = statisticsService.carbonImpact();
        assertEquals(1, asInt(carbon.get("成交物品数")));
        assertEquals(6.0, ((Number) carbon.get("累计节约碳排放(kg CO₂e)")).doubleValue(), 0.01);

        // 用户画像
        Map<String, Object> profile = statisticsService.userProfile();
        assertEquals(2, asInt(profile.get("用户总数")));
        assertEquals(102.5, ((Number) profile.get("平均信用分")).doubleValue(), 0.01);
        assertEquals(2L, asLong(profile.get("中信用(80~120)")));
    }

    /**
     * E2E-02：交易拒绝后物品回退，再被另一个买家成功购买。
     */
    @Test
    public void testTransactionRejectThenBuyAgain() {
        User seller = userService.register("reject_seller", "pass1234", "pass1234");
        User buyer1 = userService.register("buyer_reject", "pass1234", "pass1234");
        User buyer2 = userService.register("buyer_success", "pass1234", "pass1234");

        List<Category> cats = itemService.listAllCategories();
        Long catId = cats.get(0).getCategoryId();

        Item item = itemService.publish(seller.getUserId(), "二手耳机", catId,
                "正常使用", 30.0, TradeType.SELL, null);

        // buyer1 发起 → seller 拒绝
        Transaction tx1 = transactionService.create(item.getItemId(), buyer1.getUserId(), null);
        assertEquals(ItemStatus.RESERVED, itemService.findById(item.getItemId()).getStatus());

        transactionService.reject(tx1.getRecordId(), seller.getUserId());
        assertEquals(TransactionStatus.REJECTED, transactionDao.findById(tx1.getRecordId()).getStatus());

        // 物品应回退 ON_SALE
        assertEquals(ItemStatus.ON_SALE, itemService.findById(item.getItemId()).getStatus());

        // buyer2 成功购买
        Transaction tx2 = transactionService.create(item.getItemId(), buyer2.getUserId(), null);
        transactionService.approve(tx2.getRecordId(), seller.getUserId());
        transactionService.complete(tx2.getRecordId(), seller.getUserId());

        assertEquals(ItemStatus.SOLD, itemService.findById(item.getItemId()).getStatus());
        assertEquals(TransactionStatus.COMPLETED, transactionDao.findById(tx2.getRecordId()).getStatus());

        // 统计：流转 2 条（一拒绝一完成），已完成 1 条
        Map<String, Object> overview = statisticsService.overview();
        assertEquals(2, asInt(overview.get("流转总数")));
        assertEquals(1, asInt(overview.get("已完成流转")));
    }

    /**
     * E2E-03：交易取消（买家主动）后物品回退。
     */
    @Test
    public void testTransactionBuyerCancel() {
        User seller = userService.register("cancel_seller", "pass1234", "pass1234");
        User buyer = userService.register("cancel_buyer", "pass1234", "pass1234");

        List<Category> cats = itemService.listAllCategories();
        Long catId = cats.get(1).getCategoryId(); // 数码配件

        Item item = itemService.publish(seller.getUserId(), "U盘 64G", catId,
                "全新闲置", 50.0, TradeType.SELL, null);

        Transaction tx = transactionService.create(item.getItemId(), buyer.getUserId(), null);
        assertEquals(ItemStatus.RESERVED, itemService.findById(item.getItemId()).getStatus());

        transactionService.cancel(tx.getRecordId(), buyer.getUserId());
        assertEquals(TransactionStatus.CANCELLED, transactionDao.findById(tx.getRecordId()).getStatus());

        // 物品应回退 ON_SALE
        assertEquals(ItemStatus.ON_SALE, itemService.findById(item.getItemId()).getStatus());
    }

    // =========================================================================
    // 第二部分：观察者模式集成验证
    // =========================================================================

    /**
     * OBS-01：CreditAdjustObserver 注册到 ItemService 后，物品被 OFF_SHELF 时
     * 发布者信用分自动被扣 5 分。
     */
    @Test
    public void testObserver_CreditAdjustOnOffShelf() {
        // 注册观察者
        CreditAdjustObserver observer = new CreditAdjustObserver(
                userService,
                itemDao::findById
        );
        itemService.addObserver(observer);

        try {
            User seller = userService.register("observer_seller", "pass1234", "pass1234");
            Long catId = itemService.listAllCategories().get(0).getCategoryId();

            Item item = itemService.publish(seller.getUserId(), "待下架物品", catId,
                    "违规内容", 0.0, TradeType.SELL, null);

            // 初始信用分 100
            assertEquals(100, userService.findById(seller.getUserId()).getCreditScore());

            // 发布者主动下架 → 观察者应扣 5 分
            itemService.takeOffShelf(item.getItemId(), seller.getUserId());

            // 等待观察者回调同步执行（当前是同步通知）
            User afterShelfOff = userService.findById(seller.getUserId());
            assertEquals("主动下架应触发观察者扣 5 分", 95, afterShelfOff.getCreditScore());
        } finally {
            itemService.removeObserver(observer);
        }
    }

    /**
     * OBS-02：观察者异常不应中断主业务流程（观察者模式的松散耦合验证）。
     */
    @Test
    public void testObserver_ExceptionDoesNotBreakMainFlow() {
        // 注册一个会抛异常的观察者
        ItemStatusObserver badObserver = (itemId, oldStatus, newStatus) -> {
            throw new RuntimeException("模拟观察者故障");
        };
        itemService.addObserver(badObserver);

        try {
            User seller = userService.register("bad_obs_seller", "pass1234", "pass1234");
            Long catId = itemService.listAllCategories().get(0).getCategoryId();
            Item item = itemService.publish(seller.getUserId(), "测试物品", catId,
                    null, 10.0, TradeType.SELL, null);

            // 主业务流程应该继续成功，即使观察者抛异常
            assertDoesNotThrow(() -> {
                itemService.takeOffShelf(item.getItemId(), seller.getUserId());
            });

            // 物品状态确实变更了
            assertEquals(ItemStatus.OFF_SHELF, itemService.findById(item.getItemId()).getStatus());
        } finally {
            itemService.removeObserver(badObserver);
        }
    }

    /**
     * OBS-03：管理员强制下架同样触发信用分扣减。
     */
    @Test
    public void testObserver_AdminForceOffShelfAlsoTriggers() {
        // 先注册一个管理员用户
        User adminRaw = userService.register("admin_force_test", "pass1234", "pass1234");
        // 手动提升为管理员（绕过注册默认 USER 的限制）
        adminRaw.setRole(UserRole.ADMIN);
        userDao.update(adminRaw);
        Long adminId = adminRaw.getUserId();

        // 注册信用分观察者
        CreditAdjustObserver observer = new CreditAdjustObserver(
                userService, itemDao::findById
        );
        itemService.addObserver(observer);

        try {
            User seller = userService.register("force_victim", "pass1234", "pass1234");
            Long catId = itemService.listAllCategories().get(0).getCategoryId();
            Item item = itemService.publish(seller.getUserId(), "违规物品", catId,
                    "欺诈描述", 999.0, TradeType.SELL, null);

            assertEquals(100, userService.findById(seller.getUserId()).getCreditScore());

            // 管理员强制下架
            adminService.forceOffShelf(adminId, item.getItemId(), "违规宣传");

            // 观察者扣 5 分
            assertEquals("管理员下架也应扣 5 分", 95,
                    userService.findById(seller.getUserId()).getCreditScore());
        } finally {
            itemService.removeObserver(observer);
        }
    }

    // =========================================================================
    // 第三部分：策略模式端到端验证
    // =========================================================================

    /**
     * STR-01：分类筛选——只返回指定分类的在售物品。
     */
    @Test
    public void testStrategy_CategoryFilter() {
        User seller = userService.register("cat_seller", "pass1234", "pass1234");
        List<Category> cats = itemService.listAllCategories();

        // 分类 0 发布 2 个，分类 1 发布 1 个
        itemService.publish(seller.getUserId(), "高等数学", cats.get(0).getCategoryId(),
                null, 20.0, TradeType.SELL, null);
        itemService.publish(seller.getUserId(), "线性代数", cats.get(0).getCategoryId(),
                null, 15.0, TradeType.SELL, null);
        itemService.publish(seller.getUserId(), "蓝牙耳机", cats.get(1).getCategoryId(),
                null, 80.0, TradeType.SELL, null);

        // 按分类 0 筛选
        List<Item> books = itemService.listByCategory(cats.get(0).getCategoryId());
        assertEquals(2, books.size());
        for (Item i : books) {
            assertEquals(cats.get(0).getCategoryId(), i.getCategoryId());
            assertEquals(ItemStatus.ON_SALE, i.getStatus());
        }

        // 按分类 1 筛选
        List<Item> digital = itemService.listByCategory(cats.get(1).getCategoryId());
        assertEquals(1, digital.size());
    }

    /**
     * STR-02：关键词搜索——匹配名称中包含关键词的在售物品。
     */
    @Test
    public void testStrategy_KeywordSearch() {
        User seller = userService.register("kw_seller", "pass1234", "pass1234");
        Long catId = itemService.listAllCategories().get(0).getCategoryId();

        itemService.publish(seller.getUserId(), "高等数学第七版", catId,
                null, 25.0, TradeType.SELL, null);
        itemService.publish(seller.getUserId(), "线性代数及其应用", catId,
                null, 20.0, TradeType.SELL, null);
        itemService.publish(seller.getUserId(), "概率论与数理统计", catId,
                null, 22.0, TradeType.SELL, null);

        List<Item> math = itemService.searchByKeyword("数学");
        assertEquals(1, math.size());
        assertTrue(math.get(0).getName().contains("数学"));

        List<Item> alg = itemService.searchByKeyword("代数");
        assertEquals(1, alg.size());
        assertTrue(alg.get(0).getName().contains("代数"));
    }

    /**
     * STR-03：价格区间筛选。
     */
    @Test
    public void testStrategy_PriceRangeFilter() {
        User seller = userService.register("price_seller", "pass1234", "pass1234");
        Long catId = itemService.listAllCategories().get(0).getCategoryId();

        itemService.publish(seller.getUserId(), "便宜书", catId, null, 5.0, TradeType.SELL, null);
        itemService.publish(seller.getUserId(), "中等书", catId, null, 25.0, TradeType.SELL, null);
        itemService.publish(seller.getUserId(), "贵书", catId, null, 100.0, TradeType.SELL, null);

        List<Item> mid = itemService.listByPriceRange(10, 50);
        assertEquals(1, mid.size());
        assertEquals("中等书", mid.get(0).getName());

        List<Item> cheap = itemService.listByPriceRange(0, 10);
        assertEquals(1, cheap.size());
    }

    /**
     * STR-04：组合筛选——分类 + 关键词 + 价格区间。
     */
    @Test
    public void testStrategy_CompositeFilter() {
        User seller = userService.register("composite_seller", "pass1234", "pass1234");
        List<Category> cats = itemService.listAllCategories();

        // 分类 0 发布数学书（25 元），分类 1 发布耳机（80 元）
        itemService.publish(seller.getUserId(), "高等数学第七版", cats.get(0).getCategoryId(),
                null, 25.0, TradeType.SELL, null);
        itemService.publish(seller.getUserId(), "线性代数", cats.get(0).getCategoryId(),
                null, 15.0, TradeType.SELL, null);
        itemService.publish(seller.getUserId(), "蓝牙运动耳机", cats.get(1).getCategoryId(),
                null, 80.0, TradeType.SELL, null);

        // 组合：分类 0 + 关键词"数学" + 价格 20~30
        List<Item> result = itemService.searchOnSale("数学", cats.get(0).getCategoryId(), 20, 30);
        assertEquals(1, result.size());
        assertEquals("高等数学第七版", result.get(0).getName());
    }

    // =========================================================================
    // 第四部分：Factory 装配 + 管理员后台集成
    // =========================================================================

    /**
     * FAC-01：ServiceFactory 完整装配链路验证——AdminService 获取的依赖全部可用。
     */
    @Test
    public void testFactory_AdminServiceFullChain() {
        // 注册管理员
        User adminRaw = userService.register("factory_admin", "pass1234", "pass1234");
        adminRaw.setRole(UserRole.ADMIN);
        userDao.update(adminRaw);

        // 注册普通用户和其物品
        User normal = userService.register("factory_normal", "pass1234", "pass1234");
        Long catId = itemService.listAllCategories().get(0).getCategoryId();
        Item item = itemService.publish(normal.getUserId(), "Factory测试物品", catId,
                null, 10.0, TradeType.SELL, null);

        // 通过 adminService（完全由 ServiceFactory 装配）执行后台操作
        String tempPwd = adminService.resetPassword(adminRaw.getUserId(), normal.getUserId());
        assertNotNull(tempPwd);
        assertTrue(tempPwd.length() == 8);

        // 新密码能登录
        User loggedIn = userService.login("factory_normal", tempPwd);
        assertNotNull(loggedIn);

        // 强制下架
        adminService.forceOffShelf(adminRaw.getUserId(), item.getItemId(), "测试下架");
        assertEquals(ItemStatus.OFF_SHELF, itemService.findById(item.getItemId()).getStatus());

        // 重新上架
        adminService.adminRelistItem(adminRaw.getUserId(), item.getItemId());
        assertEquals(ItemStatus.ON_SALE, itemService.findById(item.getItemId()).getStatus());

        // 统计总览
        Map<String, Object> overview = adminService.overview(adminRaw.getUserId());
        assertNotNull(overview);
        assertEquals(2, asInt(overview.get("用户总数")));
    }

    /**
     * FAC-02：工厂模式装配的 Service 在真实数据库上的跨模块查询一致性。
     */
    @Test
    public void testFactory_CrossServiceDataConsistency() {
        User seller = userService.register("cross_seller", "pass1234", "pass1234");
        User buyer = userService.register("cross_buyer", "pass1234", "pass1234");
        Long catId = itemService.listAllCategories().get(0).getCategoryId();

        Item item = itemService.publish(seller.getUserId(), "跨服务测试", catId,
                null, 33.0, TradeType.SELL, null);

        // TransactionService 创建交易
        Transaction tx = transactionService.create(item.getItemId(), buyer.getUserId(), "求购");

        // ReviewService 尝试评价（应该失败，因为交易未完成）
        assertThrows(BusinessException.class, () ->
                reviewService.submit(tx.getRecordId(), buyer.getUserId(), 5, "太早了"));

        // 完成交易
        transactionService.approve(tx.getRecordId(), seller.getUserId());
        transactionService.complete(tx.getRecordId(), buyer.getUserId());

        // 现在 ReviewService 可以评价了
        Review r1 = reviewService.submit(tx.getRecordId(), buyer.getUserId(), 5, "不错");
        Review r2 = reviewService.submit(tx.getRecordId(), seller.getUserId(), 4, "好买家");
        assertNotNull(r1);
        assertNotNull(r2);

        // 双向查询验证
        List<Review> byRecord = reviewService.findByRecordId(tx.getRecordId());
        assertEquals(2, byRecord.size());

        List<Review> forSeller = reviewService.findByReviewee(seller.getUserId());
        assertEquals(1, forSeller.size());
        assertEquals(5, forSeller.get(0).getRating());

        List<Review> forBuyer = reviewService.findByReviewee(buyer.getUserId());
        assertEquals(1, forBuyer.size());
        assertEquals(4, forBuyer.get(0).getRating());
    }

    // =========================================================================
    // 第五部分：复杂多场景与统计准确性
    // =========================================================================

    /**
     * MUL-01：多用户多物品多交易的统计准确性验证。
     */
    @Test
    public void testMultipleUsersItemsTransactions_StatisticsAccuracy() {
        // 3 个卖家
        User s1 = userService.register("multi_s1", "pass1234", "pass1234");
        User s2 = userService.register("multi_s2", "pass1234", "pass1234");
        User s3 = userService.register("multi_s3", "pass1234", "pass1234");

        // 2 个买家
        User b1 = userService.register("multi_b1", "pass1234", "pass1234");
        User b2 = userService.register("multi_b2", "pass1234", "pass1234");

        List<Category> cats = itemService.listAllCategories();

        // s1 发布 2 件
        Item i1 = itemService.publish(s1.getUserId(), "s1-item1", cats.get(0).getCategoryId(),
                null, 20.0, TradeType.SELL, null);
        Item i2 = itemService.publish(s1.getUserId(), "s1-item2", cats.get(1).getCategoryId(),
                null, 50.0, TradeType.SELL, null);
        // s2 发布 1 件
        Item i3 = itemService.publish(s2.getUserId(), "s2-item1", cats.get(0).getCategoryId(),
                null, 15.0, TradeType.SELL, null);
        // s3 发布 1 件（稍后下架）
        Item i4 = itemService.publish(s3.getUserId(), "s3-item1", cats.get(2).getCategoryId(),
                null, 30.0, TradeType.SELL, null);

        // b1 买 s1-item1（20 元）
        Transaction tx1 = transactionService.create(i1.getItemId(), b1.getUserId(), null);
        transactionService.approve(tx1.getRecordId(), s1.getUserId());
        transactionService.complete(tx1.getRecordId(), b1.getUserId());

        // b2 买 s1-item2（50 元）+ s2-item1（15 元）
        Transaction tx2 = transactionService.create(i2.getItemId(), b2.getUserId(), null);
        transactionService.approve(tx2.getRecordId(), s1.getUserId());
        transactionService.complete(tx2.getRecordId(), b2.getUserId());

        Transaction tx3 = transactionService.create(i3.getItemId(), b2.getUserId(), null);
        transactionService.approve(tx3.getRecordId(), s2.getUserId());
        transactionService.complete(tx3.getRecordId(), b2.getUserId());

        // s3 自己下架 s3-item1（观察者扣信用分）
        CreditAdjustObserver obs = new CreditAdjustObserver(userService, itemDao::findById);
        itemService.addObserver(obs);
        try {
            itemService.takeOffShelf(i4.getItemId(), s3.getUserId());
        } finally {
            itemService.removeObserver(obs);
        }

        // === 验证统计 ===
        Map<String, Object> overview = statisticsService.overview();
        assertEquals(5, asInt(overview.get("用户总数")));
        assertEquals(4, asInt(overview.get("物品总数")));
        // i4 下架了，i1/i2/i3 都 SOLD → 在售 0
        assertEquals(0L, asLong(overview.get("在售物品数")));
        assertEquals(3L, asLong(overview.get("成交物品数")));
        // 20 + 50 + 15 = 85
        assertEquals(85.0, ((Number) overview.get("成交金额(元)")).doubleValue(), 0.01);
        // 3 个交易（全部完成）
        assertEquals(3, asInt(overview.get("流转总数")));
        assertEquals(3, asInt(overview.get("已完成流转")));

        // 物品状态分布
        Map<String, Long> statusDist = statisticsService.itemStatusDistribution();
        assertEquals(Long.valueOf(3), statusDist.get("已成交"));
        assertEquals(Long.valueOf(1), statusDist.get("已下架"));
        assertEquals(Long.valueOf(0), statusDist.get("在售"));
        assertEquals(Long.valueOf(0), statusDist.get("已预订"));

        // 流转状态分布
        Map<String, Long> txDist = statisticsService.transactionStatusDistribution();
        assertEquals(Long.valueOf(3), txDist.get("已完成"));
        assertEquals(Long.valueOf(0), txDist.get("待卖家确认"));

        // 按分类物品数（预置 4 分类 + 0 物品的也要列出来）
        Map<String, Long> byCat = statisticsService.itemsByCategory();
        // 分类 0 有 2 件（i1, i3），分类 1 有 1 件（i2），分类 2 有 1 件（i4）
        assertTrue(byCat.containsKey(cats.get(0).getName()));
        assertTrue(byCat.containsKey(cats.get(1).getName()));
        assertTrue(byCat.containsKey(cats.get(2).getName()));
        assertTrue(byCat.containsKey(cats.get(3).getName())); // 分类 3 0 件也应该在

        // 低碳指标
        Map<String, Object> carbon = statisticsService.carbonImpact();
        assertEquals(3, asInt(carbon.get("成交物品数")));
        assertEquals(18.0, ((Number) carbon.get("累计节约碳排放(kg CO₂e)")).doubleValue(), 0.01);
    }

    /**
     * MUL-02：CSV 导出集成验证——在真实 DB 上生成 CSV 文件。
     */
    @Test
    public void testCsvExport_Integration() throws Exception {
        // 造一些数据
        User s = userService.register("csv_seller", "pass1234", "pass1234");
        User b = userService.register("csv_buyer", "pass1234", "pass1234");
        Long catId = itemService.listAllCategories().get(0).getCategoryId();

        Item item = itemService.publish(s.getUserId(), "CSV测试物品", catId,
                null, 88.0, TradeType.SELL, null);
        Transaction tx = transactionService.create(item.getItemId(), b.getUserId(), null);
        transactionService.approve(tx.getRecordId(), s.getUserId());
        transactionService.complete(tx.getRecordId(), b.getUserId());
        reviewService.submit(tx.getRecordId(), b.getUserId(), 5, "好");
        reviewService.submit(tx.getRecordId(), s.getUserId(), 4, "好买家");

        // 导出到临时文件
        Path tempFile = Paths.get(System.getProperty("java.io.tmpdir"),
                "lowcarbon_integration_test.csv");
        Files.deleteIfExists(tempFile);

        statisticsService.exportAll(tempFile);

        assertTrue("CSV 文件应已生成", Files.exists(tempFile));
        assertTrue("CSV 文件应有内容", Files.size(tempFile) > 0);

        // 清理
        Files.deleteIfExists(tempFile);
    }

    // =========================================================================
    // 辅助方法
    // =========================================================================

    /**
     * JUnit 4 没有内建 assertDoesNotThrow，自己写一个。
     */
    private static void assertDoesNotThrow(ThrowingRunnable action) {
        try {
            action.run();
        } catch (Throwable t) {
            throw new AssertionError("不应抛出异常，但实际抛出了：" + t.getClass().getName()
                    + " - " + t.getMessage(), t);
        }
    }

    /**
     * 从 Map<String, Object> 安全提取 long 值。
     * <p>
     * StatisticsService 的 overview() 返回的 Map 里混合了 Integer（来自 List.size()）
     * 和 Long（来自 Stream.count()），统一强转避免类型不匹配。
     */
    private static long asLong(Object o) {
        return ((Number) o).longValue();
    }

    /**
     * 从 Map<String, Object> 安全提取 int 值。
     */
    private static int asInt(Object o) {
        return ((Number) o).intValue();
    }

    @FunctionalInterface
    private interface ThrowingRunnable {
        void run() throws Throwable;
    }
}
