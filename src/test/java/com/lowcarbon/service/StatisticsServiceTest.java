package com.lowcarbon.service;

import com.lowcarbon.dao.CategoryDao;
import com.lowcarbon.dao.ItemDao;
import com.lowcarbon.dao.ReviewDao;
import com.lowcarbon.dao.TransactionDao;
import com.lowcarbon.dao.UserDao;
import com.lowcarbon.entity.Category;
import com.lowcarbon.entity.Item;
import com.lowcarbon.entity.ItemStatus;
import com.lowcarbon.entity.Review;
import com.lowcarbon.entity.Transaction;
import com.lowcarbon.entity.TransactionStatus;
import com.lowcarbon.entity.User;
import com.lowcarbon.exception.DaoException;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.*;

/**
 * StatisticsService 单元测试。
 * <p>
 * 通过 Fake DAO 预先灌入测试数据，然后验证内存聚合统计结果。
 *
 * @author lowcarbon-team
 * @since 2026-11-09
 */
public class StatisticsServiceTest {

    private StatisticsService service;
    private FakeUserDao fakeUserDao;
    private FakeItemDao fakeItemDao;
    private FakeTransactionDao fakeTxDao;
    private FakeReviewDao fakeReviewDao;
    private FakeCategoryDao fakeCategoryDao;

    @Before
    public void setUp() {
        fakeUserDao = new FakeUserDao();
        fakeItemDao = new FakeItemDao();
        fakeTxDao = new FakeTransactionDao();
        fakeReviewDao = new FakeReviewDao();
        fakeCategoryDao = new FakeCategoryDao();

        service = new StatisticsService(fakeUserDao, fakeItemDao, fakeTxDao, fakeReviewDao, fakeCategoryDao);

        seedCategories();
        seedUsers();
        seedItems();
        seedTransactions();
        seedReviews();
    }

    // ==================== Seed 数据 ====================

    /** 4 个分类（与 schema.sql 预置一致）。 */
    private void seedCategories() {
        String[] names = {"教材", "数码配件", "文体用品", "生活物资"};
        for (String n : names) {
            Category c = new Category(n, null);
            fakeCategoryDao.insert(c);
        }
    }

    /** 3 个用户：seller1、seller2、buyer。 */
    private Long seller1Id;
    private Long seller2Id;
    private Long buyerId;

    private void seedUsers() {
        User u1 = new User("seller1", "h", "s");
        u1.setCreditScore(150);
        fakeUserDao.insert(u1);
        seller1Id = u1.getUserId();

        User u2 = new User("seller2", "h2", "s2");
        u2.setCreditScore(90);
        fakeUserDao.insert(u2);
        seller2Id = u2.getUserId();

        User u3 = new User("buyer", "h3", "s3");
        u3.setCreditScore(120);
        fakeUserDao.insert(u3);
        buyerId = u3.getUserId();
    }

    /** 6 个物品：seller1 4 件（2 SOLD, 1 ON_SALE, 1 OFF_SHELF），seller2 2 件（1 SOLD, 1 ON_SALE）。
     *  category 分布：教材 3 件 SOLD、数码配件 1 SOLD + 1 ON_SALE、文体用品 2 ON_SALE。 */
    private void seedItems() {
        Item[] data = {
                // seller1
                mkItem(seller1Id, "高等数学", 1L, 30.0, ItemStatus.SOLD),
                mkItem(seller1Id, "线性代数", 1L, 20.0, ItemStatus.SOLD),
                mkItem(seller1Id, "电路教材", 1L, 25.0, ItemStatus.SOLD),  // 教材 cat_id=1，3 件 SOLD
                mkItem(seller1Id, "华为耳机", 2L, 80.0, ItemStatus.ON_SALE), // 数码配件 cat_id=2
                mkItem(seller1Id, "坏键盘", 2L, 5.0, ItemStatus.OFF_SHELF),
                // seller2
                mkItem(seller2Id, "篮球", 3L, 35.0, ItemStatus.SOLD),      // 文体 cat_id=3
                mkItem(seller2Id, "羽毛球拍", 3L, 60.0, ItemStatus.ON_SALE),
        };
        for (Item it : data) fakeItemDao.insert(it);
    }

    /** 3 条 COMPLETED 流转（对应 seller1 3 件 SOLD + seller2 1 件 SOLD）。 */
    private void seedTransactions() {
        // 找前 4 件 SOLD 物品
        int cnt = 0;
        for (Item it : fakeItemDao.store.values()) {
            if (it.getStatus() == ItemStatus.SOLD && cnt < 4) {
                Transaction tx = new Transaction(it.getItemId(), buyerId, null);
                tx.setStatus(TransactionStatus.COMPLETED);
                tx.setCreateTime(System.currentTimeMillis() - 86400000L);
                tx.setCompleteTime(System.currentTimeMillis() - 3600000L);
                fakeTxDao.insert(tx);
                cnt++;
            }
        }

        // 另加 1 条 PENDING + 1 条 CANCELLED
        int added = 0;
        for (Item it : fakeItemDao.store.values()) {
            if (it.getStatus() == ItemStatus.ON_SALE && added < 2) {
                Transaction t = new Transaction(it.getItemId(), buyerId, null);
                t.setStatus(added == 0 ? TransactionStatus.PENDING : TransactionStatus.CANCELLED);
                fakeTxDao.insert(t);
                added++;
            }
        }
    }

    /** 3 条评价（对 COMPLETED 流转的双向评价只实现一端，简化）。 */
    private void seedReviews() {
        // 只加一个 reviewer → seller1 的 3 条评价
        fakeReviewDao.insert(mkReview(1L, buyerId, seller1Id, 5)); // 高等数学
        fakeReviewDao.insert(mkReview(2L, buyerId, seller1Id, 4)); // 线性代数
        fakeReviewDao.insert(mkReview(3L, buyerId, seller1Id, 3)); // 电路教材
    }

    // ==================== 辅助 ====================

    /** 统一转 int（Long/Integer 都能处理，解决 Stream count 返回 Long 问题）。 */
    private static int intVal(Object o) {
        if (o instanceof Number) return ((Number) o).intValue();
        throw new IllegalArgumentException("not a number: " + o);
    }

    private Item mkItem(Long publisherId, String name, Long catId, double price, ItemStatus status) {
        Item it = new Item(publisherId, name, catId, null, price, null);
        it.setStatus(status);
        it.setPublishTime(System.currentTimeMillis());
        return it;
    }

    private Review mkReview(Long recordId, Long reviewerId, Long revieweeId, int rating) {
        Review r = new Review(recordId, reviewerId, revieweeId, rating, "好");
        r.setCreateTime(System.currentTimeMillis());
        return r;
    }

    // ==================== 总览 ====================

    @Test
    public void testOverview_CoreMetrics() {
        Map<String, Object> ov = service.overview();

        assertEquals(3, intVal(ov.get("用户总数")));
        assertEquals(7, intVal(ov.get("物品总数")));
        assertEquals(2, intVal(ov.get("在售物品数")));
        assertEquals(4, intVal(ov.get("成交物品数")));
        assertEquals(110.0, (Double) ov.get("成交金额(元)"), 0.01);
        assertEquals(6, intVal(ov.get("流转总数")));
        assertEquals(4, intVal(ov.get("已完成流转")));
        assertEquals(3, intVal(ov.get("评价总数")));
        assertEquals(4.0, (Double) ov.get("平均评分"), 0.01);
    }

    @Test
    public void testOverview_EmptySystem() {
        StatisticsService s = new StatisticsService(
                new FakeUserDao(), new FakeItemDao(), new FakeTransactionDao(),
                new FakeReviewDao(), new FakeCategoryDao());
        Map<String, Object> ov = s.overview();

        assertEquals(0, ov.get("用户总数"));
        assertEquals(0, ov.get("物品总数"));
        assertEquals(0, ov.get("成交物品数"));
        assertEquals(0.0, (Double) ov.get("成交金额(元)"), 0.01);
        assertEquals(0.0, (Double) ov.get("平均评分"), 0.01);
    }

    // ==================== 低碳指标 ====================

    @Test
    public void testCarbonImpact() {
        Map<String, Object> ci = service.carbonImpact();

        assertEquals(4, ci.get("成交物品数"));
        // 4 * 6kg = 24kg
        assertEquals(24.0, (Double) ci.get("累计节约碳排放(kg CO₂e)"), 0.01);
        // 24 / 21kg ≈ 1.14 棵树
        assertEquals(1.14, (Double) ci.get("相当于成年树木年吸碳(棵)"), 0.01);
    }

    @Test
    public void testCarbonImpact_NoSales() {
        FakeItemDao emptyItems = new FakeItemDao();
        // 分类不影响碳指标，不用 seed
        StatisticsService s = new StatisticsService(
                new FakeUserDao(), emptyItems, new FakeTransactionDao(),
                new FakeReviewDao(), new FakeCategoryDao());

        Map<String, Object> ci = s.carbonImpact();
        assertEquals(0, ci.get("成交物品数"));
        assertEquals(0.0, (Double) ci.get("累计节约碳排放(kg CO₂e)"), 0.01);
    }

    // ==================== 用户画像 ====================

    @Test
    public void testUserProfile() {
        Map<String, Object> up = service.userProfile();
        assertEquals(3, intVal(up.get("用户总数")));
        assertEquals(120.0, (Double) up.get("平均信用分"), 0.01);
        assertEquals(0, intVal(up.get("低信用(<80)")));
        assertEquals(2, intVal(up.get("中信用(80~120)")));
        assertEquals(1, intVal(up.get("高信用(>120)")));
    }

    // ==================== 物品状态分布 ====================

    @Test
    public void testItemStatusDistribution() {
        Map<String, Long> dist = service.itemStatusDistribution();
        // 固定顺序：在售/已预订/已成交/已下架
        assertEquals(2L, dist.get("在售").longValue());
        assertEquals(0L, dist.get("已预订").longValue()); // 我们的数据里没有 RESERVED
        assertEquals(4L, dist.get("已成交").longValue());
        assertEquals(1L, dist.get("已下架").longValue());
        assertEquals(4, dist.size());
    }

    // ==================== 流转状态分布 ====================

    @Test
    public void testTransactionStatusDistribution() {
        Map<String, Long> dist = service.transactionStatusDistribution();
        assertEquals(1L, dist.get("待卖家确认").longValue());
        assertEquals(0L, dist.get("已同意").longValue());
        assertEquals(0L, dist.get("已拒绝").longValue());
        assertEquals(4L, dist.get("已完成").longValue());
        assertEquals(1L, dist.get("已取消").longValue());
        assertEquals(5, dist.size());
    }

    // ==================== 分类物品数 ====================

    @Test
    public void testItemsByCategory() {
        Map<String, Long> dist = service.itemsByCategory();
        // 按数量降序：教材 3 件 > 文体用品 2 件 > 数码配件 2 件？不对，数码配件 2 件
        // seller1 华为耳机 ON_SALE + seller1 坏键盘 OFF_SHELF = 2 件数码
        // seller2 篮球 SOLD + 羽毛球拍 ON_SALE = 2 件文体
        // 教材 3 件 SOLD
        // 生活物资 0 件

        // 检查各 key 都存在
        assertTrue(dist.containsKey("教材"));
        assertTrue(dist.containsKey("数码配件"));
        assertTrue(dist.containsKey("文体用品"));
        assertTrue(dist.containsKey("生活物资"));

        assertEquals(3L, dist.get("教材").longValue());
        assertEquals(2L, dist.get("数码配件").longValue());
        assertEquals(2L, dist.get("文体用品").longValue());
        assertEquals(0L, dist.get("生活物资").longValue());
    }

    // ==================== 分类成交金额 ====================

    @Test
    public void testAmountByCategory() {
        Map<String, Double> amt = service.amountByCategory();
        // 教材：30+20+25=75
        assertEquals(75.0, amt.get("教材"), 0.01);
        // 文体：35（篮球 SOLD）
        assertEquals(35.0, amt.get("文体用品"), 0.01);
        // 数码配件：0（全 SOLD 的是教材和文体）
        assertEquals(0.0, amt.get("数码配件"), 0.01);
        assertEquals(0.0, amt.get("生活物资"), 0.01);
    }

    // ==================== Top 卖家 ====================

    @Test
    public void testTopSellers() {
        List<String[]> top = service.topSellers(3);
        // seller1: 3 件 SOLD，金额 30+20+25=75
        // seller2: 1 件 SOLD，金额 35
        assertEquals(2, top.size());

        String[] row1 = top.get(0);
        assertEquals("seller1", row1[1]);
        assertEquals("3", row1[2]);
        assertEquals("75.0", row1[3]);

        String[] row2 = top.get(1);
        assertEquals("seller2", row2[1]);
        assertEquals("1", row2[2]);
        assertEquals("35.0", row2[3]);
    }

    @Test
    public void testTopSellers_NZero() {
        // n<=0 应回退到 10
        List<String[]> top = service.topSellers(0);
        assertEquals(2, top.size());
    }

    // ==================== Top 评价者 ====================

    @Test
    public void testTopReviewers() {
        List<String[]> top = service.topReviewers(3);
        assertEquals(1, top.size()); // 只有 buyer 一个评价者，3 条评价

        String[] row = top.get(0);
        assertEquals("buyer", row[1]);
        assertEquals("3", row[2]);
    }

    // ==================== CSV 导出 ====================

    @Test
    public void testExportAll_Success() throws IOException {
        Path temp = Files.createTempFile("stats_test_", ".csv");
        try {
            service.exportAll(temp);
            assertTrue("文件应被创建", Files.exists(temp));
            assertTrue("文件应有内容", Files.size(temp) > 100);

            // 读回验证前几行有表头关键字
            String content = Files.readString(temp);
            assertTrue("应包含总览表头", content.contains("系统总览"));
            assertTrue("应包含低碳指标", content.contains("低碳指标"));
            assertTrue("应包含用户画像", content.contains("用户画像"));
        } finally {
            Files.deleteIfExists(temp);
        }
    }

    @Test
    public void testExportAll_StringPath() throws IOException {
        Path temp = Files.createTempFile("stats_strpath_", ".csv");
        try {
            service.exportAll(temp.toString());
            assertTrue(Files.exists(temp));
        } finally {
            Files.deleteIfExists(temp);
        }
    }

    // ==================== DAO 异常包装 ====================

    @Test
    public void testOverview_DaoExceptionWrapped() {
        FakeUserDao bomb = new FakeUserDao();
        bomb.boomOnFindAll = true;

        StatisticsService s = new StatisticsService(
                bomb, fakeItemDao, fakeTxDao, fakeReviewDao, fakeCategoryDao);

        assertThrows(com.lowcarbon.exception.BusinessException.class, s::overview);
    }

    // ==================== Fake DAO ====================

    private static class FakeUserDao implements UserDao {
        final Map<Long, User> store = new LinkedHashMap<>();
        private long nextId = 1;
        boolean boomOnFindAll = false;

        @Override public List<User> findAll() {
            if (boomOnFindAll) throw new DaoException("boom!");
            return new ArrayList<>(store.values());
        }
        @Override public User findById(Long userId) { return store.get(userId); }
        @Override public User findByUsername(String username) { return null; }
        @Override public Long insert(User u) { Long id = nextId++; u.setUserId(id); store.put(id, u); return id; }
        @Override public void update(User u) { if (u.getUserId() != null) store.put(u.getUserId(), u); }
        @Override public void delete(Long id) { store.remove(id); }
        @Override public int count() { return store.size(); }
    }

    private static class FakeItemDao implements ItemDao {
        final Map<Long, Item> store = new LinkedHashMap<>();
        private long nextId = 1;
        @Override public List<Item> findAll() { return new ArrayList<>(store.values()); }
        @Override public Item findById(Long id) { return store.get(id); }
        @Override public List<Item> findByPublisherId(Long pid) { return new ArrayList<>(); }
        @Override public List<Item> findByCategoryId(Long cid) { return new ArrayList<>(); }
        @Override public List<Item> findByStatus(ItemStatus s) { return new ArrayList<>(); }
        @Override public List<Item> findByKeyword(String kw) { return new ArrayList<>(); }
        @Override public Long insert(Item i) { Long id = nextId++; i.setItemId(id); store.put(id, i); return id; }
        @Override public void update(Item i) { store.put(i.getItemId(), i); }
        @Override public void delete(Long id) { store.remove(id); }
        @Override public void updateStatus(Long id, ItemStatus s) { }
        @Override public List<Item> findByPriceRange(double a, double b) { return new ArrayList<>(); }
        @Override public List<Item> search(String k, Long c, double a, double b, ItemStatus s) { return new ArrayList<>(); }
        @Override public int count() { return store.size(); }
    }

    private static class FakeTransactionDao implements TransactionDao {
        final Map<Long, Transaction> store = new LinkedHashMap<>();
        private long nextId = 1;
        @Override public List<Transaction> findAll() { return new ArrayList<>(store.values()); }
        @Override public Transaction findById(Long id) { return store.get(id); }
        @Override public List<Transaction> findByItemId(Long id) { return new ArrayList<>(); }
        @Override public List<Transaction> findByInitiatorId(Long id) { return new ArrayList<>(); }
        @Override public List<Transaction> findByStatus(TransactionStatus s) { return new ArrayList<>(); }
        @Override public Long insert(Transaction t) { Long id = nextId++; t.setRecordId(id); store.put(id, t); return id; }
        @Override public void update(Transaction t) { store.put(t.getRecordId(), t); }
        @Override public void delete(Long id) { store.remove(id); }
        @Override public int count() { return store.size(); }
    }

    private static class FakeReviewDao implements ReviewDao {
        final Map<Long, Review> store = new LinkedHashMap<>();
        private long nextId = 1;
        @Override public List<Review> findAll() { return new ArrayList<>(store.values()); }
        @Override public Review findById(Long id) { return store.get(id); }
        @Override public List<Review> listByRecordId(Long id) { return new ArrayList<>(); }
        @Override public Review findByRecordId(Long id) { return null; }
        @Override public List<Review> findByRevieweeId(Long id) { return new ArrayList<>(); }
        @Override public List<Review> findByReviewerId(Long id) { return new ArrayList<>(); }
        @Override public Long insert(Review r) { Long id = nextId++; r.setReviewId(id); store.put(id, r); return id; }
        @Override public int count() { return store.size(); }
    }

    private static class FakeCategoryDao implements CategoryDao {
        final Map<Long, Category> store = new LinkedHashMap<>();
        private long nextId = 1;
        @Override public List<Category> findAll() { return new ArrayList<>(store.values()); }
        @Override public Category findById(Long id) { return store.get(id); }
        @Override public Category findByName(String n) { return null; }
        @Override public Long insert(Category c) { Long id = nextId++; c.setCategoryId(id); store.put(id, c); return id; }
        @Override public void update(Category c) { store.put(c.getCategoryId(), c); }
        @Override public void delete(Long id) { store.remove(id); }
        @Override public int count() { return store.size(); }
    }
}
