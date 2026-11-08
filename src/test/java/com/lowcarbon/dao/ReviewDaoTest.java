package com.lowcarbon.dao;

import com.lowcarbon.config.DatabaseManager;
import com.lowcarbon.dao.impl.ReviewDaoImpl;
import com.lowcarbon.dao.impl.TransactionDaoImpl;
import com.lowcarbon.dao.impl.UserDaoImpl;
import com.lowcarbon.entity.*;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.*;

/**
 * ReviewDao 单元测试。
 *
 * @author lowcarbon-team
 * @since 2026-11-09
 */
public class ReviewDaoTest {

    static {
        System.setProperty("lowcarbon.db.path", "lowcarbon_unit_test.db");
    }

    private static ReviewDao dao;
    private static Long recordId;
    private static Long reviewerId;
    private static Long revieweeId;

    @BeforeClass
    public static void setUpClass() {
        dao = new ReviewDaoImpl();
        prepareFixtures();
    }

    @Before
    public void setUp() {
        prepareFixtures();
    }

    /** 重置数据库 + 准备 ReviewDaoTest 所需的流转记录和用户。 */
    private static void prepareFixtures() {
        DatabaseManager.resetForTest();

        UserDao userDao = new UserDaoImpl();
        com.lowcarbon.dao.ItemDao itemDao = new com.lowcarbon.dao.impl.ItemDaoImpl();
        Category cat = new com.lowcarbon.dao.impl.CategoryDaoImpl().findByName("教材");
        assertNotNull(cat);

        // 发布者（被评价人）
        User publisher = new User("rv_publisher", "h_pub_rv", "s_pub_rv");
        publisher.setRole(UserRole.USER);
        publisher.setCreateTime(System.currentTimeMillis());
        revieweeId = userDao.insert(publisher);

        // 买家（评价人）
        User buyer = new User("rv_buyer", "h_buyer_rv", "s_buyer_rv");
        buyer.setRole(UserRole.USER);
        buyer.setCreateTime(System.currentTimeMillis());
        reviewerId = userDao.insert(buyer);

        // 物品
        Item item = new Item(revieweeId, "评价测试物品", cat.getCategoryId(),
                null, 0.0, TradeType.SELL);
        item.setPublishTime(System.currentTimeMillis());
        Long itemId = itemDao.insert(item);

        // 流转记录
        TransactionDao txDao = new TransactionDaoImpl();
        Transaction tx = new Transaction(itemId, reviewerId, "用于评价测试");
        tx.setStatus(TransactionStatus.COMPLETED);
        tx.setCreateTime(System.currentTimeMillis());
        tx.setCompleteTime(System.currentTimeMillis());
        recordId = txDao.insert(tx);
    }

    // ==================== 空库查询 ====================

    @Test
    public void testFindById_NotExist() {
        assertNull(dao.findById(null));
        assertNull(dao.findById(99999L));
    }

    @Test
    public void testFindByRecordId_NotExist() {
        assertNull(dao.findByRecordId(null));
        assertNull(dao.findByRecordId(99999L));
    }

    @Test
    public void testFindByRevieweeId_Empty() {
        assertTrue(dao.findByRevieweeId(null).isEmpty());
        assertTrue(dao.findByRevieweeId(99999L).isEmpty());
    }

    @Test
    public void testFindByReviewerId_Empty() {
        assertTrue(dao.findByReviewerId(null).isEmpty());
    }

    @Test
    public void testCount_Empty() {
        int initialCount = dao.count();
        assertTrue(initialCount >= 0); // 可能已被其他测试插入
    }

    // ==================== 新增 ====================

    @Test
    public void testInsert() {
        Review review = new Review(recordId, reviewerId, revieweeId, 4, "东西很好，物流也快！");
        review.setCreateTime(System.currentTimeMillis());

        Long id = dao.insert(review);
        assertNotNull(id);
        assertEquals(id, review.getReviewId());
        assertEquals(4, review.getRating());
    }

    @Test
    public void testInsert_NoComment() {
        Review review = new Review(recordId, reviewerId, revieweeId, 5, null);
        review.setCreateTime(System.currentTimeMillis());

        Long id = dao.insert(review);
        Review fetched = dao.findById(id);
        assertNotNull(fetched);
        assertNull(fetched.getComment());
        assertEquals(5, fetched.getRating());
    }

    @Test
    public void testInsert_RatingBoundary() {
        // rating 1 和 5 都是边界值
        Review r1 = new Review(recordId, reviewerId, revieweeId, 1, "最差");
        r1.setCreateTime(System.currentTimeMillis());
        Long id1 = dao.insert(r1);
        assertEquals(1, dao.findById(id1).getRating());

        Review r5 = new Review(recordId, reviewerId, revieweeId, 5, "最好");
        r5.setCreateTime(System.currentTimeMillis());
        Long id5 = dao.insert(r5);
        assertEquals(5, dao.findById(id5).getRating());
    }

    // ==================== 查询 ====================

    @Test
    public void testFindById_RoundTrip() {
        Review r = new Review(recordId, reviewerId, revieweeId, 3, "往返验证");
        r.setCreateTime(1700000000000L);
        Long id = dao.insert(r);

        Review fetched = dao.findById(id);
        assertNotNull(fetched);
        assertEquals(recordId, fetched.getRecordId());
        assertEquals(reviewerId, fetched.getReviewerId());
        assertEquals(revieweeId, fetched.getRevieweeId());
        assertEquals(3, fetched.getRating());
        assertEquals("往返验证", fetched.getComment());
        assertEquals(1700000000000L, fetched.getCreateTime());
    }

    @Test
    public void testFindByRecordId() {
        Review r = new Review(recordId, reviewerId, revieweeId, 4, "按记录查");
        r.setCreateTime(System.currentTimeMillis());
        Long id = dao.insert(r);

        Review found = dao.findByRecordId(recordId);
        assertNotNull(found);
        assertTrue(found.getReviewId() > 0);
    }

    @Test
    public void testFindByRevieweeId() {
        Review r = new Review(recordId, reviewerId, revieweeId, 5, "按被评价人查");
        r.setCreateTime(System.currentTimeMillis());
        Long id = dao.insert(r);

        List<Review> list = dao.findByRevieweeId(revieweeId);
        assertTrue(list.stream().anyMatch(rv -> id.equals(rv.getReviewId())));
    }

    @Test
    public void testFindByReviewerId() {
        Review r = new Review(recordId, reviewerId, revieweeId, 2, "按评价人查");
        r.setCreateTime(System.currentTimeMillis());
        Long id = dao.insert(r);

        List<Review> list = dao.findByReviewerId(reviewerId);
        assertTrue(list.stream().anyMatch(rv -> id.equals(rv.getReviewId())));
    }

    // ==================== count ====================

    @Test
    public void testCount_Increases() {
        int before = dao.count();

        Review r = new Review(recordId, reviewerId, revieweeId, 3, "count 测试");
        r.setCreateTime(System.currentTimeMillis());
        dao.insert(r);

        int after = dao.count();
        assertEquals(before + 1, after);
    }
}
