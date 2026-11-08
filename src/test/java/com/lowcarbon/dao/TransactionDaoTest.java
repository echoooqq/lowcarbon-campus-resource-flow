package com.lowcarbon.dao;

import com.lowcarbon.config.DatabaseManager;
import com.lowcarbon.dao.impl.CategoryDaoImpl;
import com.lowcarbon.dao.impl.ItemDaoImpl;
import com.lowcarbon.dao.impl.TransactionDaoImpl;
import com.lowcarbon.dao.impl.UserDaoImpl;
import com.lowcarbon.entity.*;
import com.lowcarbon.exception.DaoException;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.*;

/**
 * TransactionDao 单元测试。
 *
 * @author lowcarbon-team
 * @since 2026-11-09
 */
public class TransactionDaoTest {

    static {
        System.setProperty("lowcarbon.db.path", "lowcarbon_unit_test.db");
    }

    private static TransactionDao dao;
    private static ItemDao itemDao;
    private static UserDao userDao;
    private static Long buyerUserId;
    private static Long publisherId;
    private static Long testItemId;

    @BeforeClass
    public static void setUpClass() {
        dao = new TransactionDaoImpl();
        itemDao = new ItemDaoImpl();
        userDao = new UserDaoImpl();
        prepareFixtures();
    }

    @Before
    public void setUp() {
        prepareFixtures();
    }

    /** 重置数据库 + 准备 TransactionDaoTest 所需的发布者、买家、物品。 */
    private static void prepareFixtures() {
        DatabaseManager.resetForTest();

        // 准备发布者
        User publisher = new User("tx_publisher", "h_pub_tx", "s_pub_tx");
        publisher.setRole(UserRole.USER);
        publisher.setCreateTime(System.currentTimeMillis());
        publisherId = userDao.insert(publisher);

        // 准备买家
        User buyer = new User("transaction_buyer", "h_buyer", "s_buyer");
        buyer.setRole(UserRole.USER);
        buyer.setCreateTime(System.currentTimeMillis());
        buyerUserId = userDao.insert(buyer);

        // 找预置分类
        Category cat = new CategoryDaoImpl().findByName("数码配件");
        assertNotNull(cat);

        Item item = new Item(publisherId, "流转测试耳机", cat.getCategoryId(),
                "成色好", 50.0, TradeType.SELL);
        item.setPublishTime(System.currentTimeMillis());
        testItemId = itemDao.insert(item);
    }

    // ==================== 空库查询 ====================

    @Test
    public void testFindById_NotExist() {
        assertNull(dao.findById(null));
        assertNull(dao.findById(99999L));
    }

    @Test
    public void testFindAll_Empty() {
        assertTrue(dao.findAll().isEmpty());
    }

    @Test
    public void testFindByItemId_Empty() {
        assertTrue(dao.findByItemId(null).isEmpty());
        assertTrue(dao.findByItemId(99999L).isEmpty());
    }

    @Test
    public void testFindByInitiatorId_Empty() {
        assertTrue(dao.findByInitiatorId(null).isEmpty());
    }

    @Test
    public void testFindByStatus_Empty() {
        assertTrue(dao.findByStatus(null).isEmpty());
        assertTrue(dao.findByStatus(TransactionStatus.PENDING).isEmpty());
    }

    // ==================== 新增 ====================

    @Test
    public void testInsert() {
        Transaction t = new Transaction(testItemId, buyerUserId, "我想要，什么时候可以面交？");
        t.setCreateTime(System.currentTimeMillis());

        Long id = dao.insert(t);
        assertNotNull(id);
        assertEquals(id, t.getRecordId());
        assertEquals(TransactionStatus.PENDING, t.getStatus());
        assertNull("新插入记录 completeTime 应为 null", t.getCompleteTime());
        assertNotNull(t.getRemark());
    }

    @Test
    public void testInsert_NoRemark() {
        Transaction t = new Transaction(testItemId, buyerUserId, null);
        t.setCreateTime(System.currentTimeMillis());
        Long id = dao.insert(t);
        Transaction fetched = dao.findById(id);
        assertNotNull(fetched);
        assertNull(fetched.getRemark());
    }

    // ==================== 查询 ====================

    @Test
    public void testFindById_RoundTrip() {
        Transaction t = new Transaction(testItemId, buyerUserId, "往返验证");
        t.setCreateTime(1700000000000L);
        Long id = dao.insert(t);

        Transaction fetched = dao.findById(id);
        assertNotNull(fetched);
        assertEquals(testItemId, fetched.getItemId());
        assertEquals(buyerUserId, fetched.getInitiatorId());
        assertEquals(TransactionStatus.PENDING, fetched.getStatus());
        assertEquals(1700000000000L, fetched.getCreateTime());
        assertNull("未完成记录 completeTime 应为 null", fetched.getCompleteTime());
    }

    @Test
    public void testFindByItemId() {
        Transaction t = new Transaction(testItemId, buyerUserId, "按物品查");
        t.setCreateTime(System.currentTimeMillis());
        Long id = dao.insert(t);

        List<Transaction> list = dao.findByItemId(testItemId);
        assertTrue(list.stream().anyMatch(tx -> id.equals(tx.getRecordId())));
    }

    @Test
    public void testFindByInitiatorId() {
        Transaction t = new Transaction(testItemId, buyerUserId, "按发起人查");
        t.setCreateTime(System.currentTimeMillis());
        Long id = dao.insert(t);

        List<Transaction> list = dao.findByInitiatorId(buyerUserId);
        assertTrue(list.stream().anyMatch(tx -> id.equals(tx.getRecordId())));
    }

    @Test
    public void testFindByStatus() {
        Transaction t = new Transaction(testItemId, buyerUserId, "按状态查");
        t.setCreateTime(System.currentTimeMillis());
        Long id = dao.insert(t);

        List<Transaction> list = dao.findByStatus(TransactionStatus.PENDING);
        assertTrue(list.stream().anyMatch(tx -> id.equals(tx.getRecordId())));

        List<Transaction> noList = dao.findByStatus(TransactionStatus.COMPLETED);
        assertFalse(noList.stream().anyMatch(tx -> id.equals(tx.getRecordId())));
    }

    // ==================== 更新 ====================

    @Test
    public void testUpdate_StatusTransition() {
        Transaction t = new Transaction(testItemId, buyerUserId, "状态流转");
        t.setCreateTime(System.currentTimeMillis());
        Long id = dao.insert(t);

        // PENDING → APPROVED
        t.setStatus(TransactionStatus.APPROVED);
        dao.update(t);
        assertEquals(TransactionStatus.APPROVED, dao.findById(id).getStatus());

        // APPROVED → COMPLETED（填入 completeTime）
        long completeTime = System.currentTimeMillis();
        t.setStatus(TransactionStatus.COMPLETED);
        t.setCompleteTime(completeTime);
        dao.update(t);

        Transaction fetched = dao.findById(id);
        assertEquals(TransactionStatus.COMPLETED, fetched.getStatus());
        assertEquals(Long.valueOf(completeTime), fetched.getCompleteTime());
    }

    @Test
    public void testUpdate_Rejected() {
        Transaction t = new Transaction(testItemId, buyerUserId, "拒绝场景");
        t.setCreateTime(System.currentTimeMillis());
        Long id = dao.insert(t);

        t.setStatus(TransactionStatus.REJECTED);
        t.setCompleteTime(null);
        dao.update(t);

        Transaction fetched = dao.findById(id);
        assertEquals(TransactionStatus.REJECTED, fetched.getStatus());
        assertNull("被拒绝的记录 completeTime 应为 null", fetched.getCompleteTime());
    }

    @Test
    public void testUpdate_NoId() {
        Transaction t = new Transaction(testItemId, buyerUserId, "");
        try {
            dao.update(t);
            fail("recordId 为 null 应抛 DaoException");
        } catch (DaoException expected) {
            assertTrue(expected.getMessage().contains("recordId"));
        }
    }

    // ==================== 删除 ====================

    @Test
    public void testDelete() {
        Transaction t = new Transaction(testItemId, buyerUserId, "待删除");
        t.setCreateTime(System.currentTimeMillis());
        Long id = dao.insert(t);
        assertNotNull(dao.findById(id));

        dao.delete(id);
        assertNull(dao.findById(id));
    }

    @Test
    public void testDelete_Null() {
        dao.delete(null);
    }

    // ==================== 便捷方法 ====================

    @Test
    public void testIsFinishedAndIsActive() {
        Transaction pending = new Transaction(testItemId, buyerUserId, "pending");
        pending.setCreateTime(System.currentTimeMillis());
        dao.insert(pending);
        assertFalse(pending.isFinished());
        assertTrue(pending.isActive());

        Transaction completed = new Transaction(testItemId, buyerUserId, "completed");
        completed.setCreateTime(System.currentTimeMillis());
        Long cid = dao.insert(completed);
        completed.setStatus(TransactionStatus.COMPLETED);
        completed.setCompleteTime(System.currentTimeMillis());
        dao.update(completed);

        Transaction fetched = dao.findById(cid);
        assertTrue(fetched.isFinished());
        assertFalse(fetched.isActive());
    }
}
