package com.lowcarbon.service;

import com.lowcarbon.dao.ItemDao;
import com.lowcarbon.dao.TransactionDao;
import com.lowcarbon.dao.UserDao;
import com.lowcarbon.entity.Item;
import com.lowcarbon.entity.ItemStatus;
import com.lowcarbon.entity.Transaction;
import com.lowcarbon.entity.TransactionStatus;
import com.lowcarbon.entity.User;
import com.lowcarbon.entity.UserRole;
import com.lowcarbon.exception.BusinessException;
import com.lowcarbon.exception.DaoException;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.*;

/**
 * TransactionService 单元测试。
 * <p>
 * Fake DAO 隔离：transactionDao / itemDao / userDao。
 *
 * @author lowcarbon-team
 * @since 2026-11-09
 */
public class TransactionServiceTest {

    private TransactionService service;
    private FakeTransactionDao fakeTxDao;
    private FakeItemDao fakeItemDao;
    private FakeUserDao fakeUserDao;

    // 预置数据
    private Long buyerId;    // 买家
    private Long sellerId;   // 卖家（item 发布者）
    private Long itemId;     // ON_SALE 状态的物品
    private Long reservedItemId; // RESERVED 状态的物品

    @Before
    public void setUp() {
        fakeTxDao = new FakeTransactionDao();
        fakeItemDao = new FakeItemDao();
        fakeUserDao = new FakeUserDao();

        service = new TransactionService(fakeTxDao, fakeItemDao, fakeUserDao);

        // 买家
        User buyer = new User("buyer", "h", "s");
        buyer.setRole(UserRole.USER);
        buyer.setCreateTime(System.currentTimeMillis());
        fakeUserDao.insert(buyer);
        buyerId = buyer.getUserId();

        // 卖家
        User seller = new User("seller", "h2", "s2");
        seller.setRole(UserRole.USER);
        seller.setCreateTime(System.currentTimeMillis());
        fakeUserDao.insert(seller);
        sellerId = seller.getUserId();

        // 在售物品（卖家发布的）
        Item item = new Item(sellerId, "高等数学", 1L, null, 25.0, null);
        item.setPublishTime(System.currentTimeMillis());
        fakeItemDao.insert(item);
        itemId = item.getItemId();

        // 已预定物品
        Item reserved = new Item(sellerId, "二手耳机", 1L, null, 30.0, null);
        reserved.setPublishTime(System.currentTimeMillis());
        reserved.setStatus(ItemStatus.RESERVED);
        fakeItemDao.insert(reserved);
        reservedItemId = reserved.getItemId();
    }

    // ==================== 发起意向 ====================

    @Test
    public void testCreate_Success() {
        Transaction tx = service.create(itemId, buyerId, "求购");

        assertNotNull(tx.getRecordId());
        assertEquals(itemId, tx.getItemId());
        assertEquals(buyerId, tx.getInitiatorId());
        assertEquals(TransactionStatus.PENDING, tx.getStatus());
        assertTrue(tx.getCreateTime() > 0);
        // 物品自动变 RESERVED
        assertEquals(ItemStatus.RESERVED, fakeItemDao.findById(itemId).getStatus());
    }

    @Test
    public void testCreate_ItemOnSaleOnly() {
        // 已预定物品不能再发起
        assertThrows(BusinessException.class,
                () -> service.create(reservedItemId, buyerId, null));
    }

    @Test
    public void testCreate_CannotBuyOwnItem() {
        // 卖家不能对自己的物品发起意向
        assertThrows(BusinessException.class,
                () -> service.create(itemId, sellerId, null));
    }

    @Test
    public void testCreate_NoActiveDuplicate() {
        // 已有 PENDING，不能再发
        service.create(itemId, buyerId, "第一次");
        assertThrows(BusinessException.class,
                () -> service.create(itemId, buyerId, "第二次"));
    }

    @Test
    public void testCreate_ItemNotExist() {
        assertThrows(BusinessException.class,
                () -> service.create(99999L, buyerId, null));
    }

    @Test
    public void testCreate_BuyerNotExist() {
        assertThrows(BusinessException.class,
                () -> service.create(itemId, 99999L, null));
    }

    @Test
    public void testCreate_NullIds() {
        assertThrows(BusinessException.class,
                () -> service.create(null, buyerId, null));
        assertThrows(BusinessException.class,
                () -> service.create(itemId, null, null));
    }

    // ==================== 卖家同意 ====================

    @Test
    public void testApprove_Success() {
        Transaction tx = service.create(itemId, buyerId, null);

        Transaction updated = service.approve(tx.getRecordId(), sellerId);
        assertEquals(TransactionStatus.APPROVED, updated.getStatus());
    }

    @Test
    public void testApprove_NotOwner() {
        Transaction tx = service.create(itemId, buyerId, null);

        assertThrows(BusinessException.class,
                () -> service.approve(tx.getRecordId(), buyerId)); // 买家不是发布者
    }

    @Test
    public void testApprove_NotPending() {
        Transaction tx = service.create(itemId, buyerId, null);
        service.approve(tx.getRecordId(), sellerId); // 先变 APPROVED

        assertThrows(BusinessException.class,
                () -> service.approve(tx.getRecordId(), sellerId)); // 再 approve 就不对了
    }

    // ==================== 卖家拒绝 ====================

    @Test
    public void testReject_Success() {
        Transaction tx = service.create(itemId, buyerId, null);

        Transaction updated = service.reject(tx.getRecordId(), sellerId);
        assertEquals(TransactionStatus.REJECTED, updated.getStatus());
        // 物品回退 ON_SALE
        assertEquals(ItemStatus.ON_SALE, fakeItemDao.findById(itemId).getStatus());
    }

    @Test
    public void testReject_NotOwner() {
        Transaction tx = service.create(itemId, buyerId, null);
        assertThrows(BusinessException.class,
                () -> service.reject(tx.getRecordId(), buyerId));
    }

    @Test
    public void testReject_FromApprovedNotAllowed() {
        Transaction tx = service.create(itemId, buyerId, null);
        service.approve(tx.getRecordId(), sellerId); // 先变 APPROVED

        // APPROVED 状态不能 reject，只能 complete / cancel
        assertThrows(BusinessException.class,
                () -> service.reject(tx.getRecordId(), sellerId));
    }

    // ==================== 完成 ====================

    @Test
    public void testComplete_Success() {
        Transaction tx = service.create(itemId, buyerId, null);
        service.approve(tx.getRecordId(), sellerId);

        Transaction updated = service.complete(tx.getRecordId(), buyerId);
        assertEquals(TransactionStatus.COMPLETED, updated.getStatus());
        assertNotNull("完成时间应自动填充", updated.getCompleteTime());
        // 物品变 SOLD
        assertEquals(ItemStatus.SOLD, fakeItemDao.findById(itemId).getStatus());
    }

    @Test
    public void testComplete_BySeller() {
        Transaction tx = service.create(itemId, buyerId, null);
        service.approve(tx.getRecordId(), sellerId);

        Transaction updated = service.complete(tx.getRecordId(), sellerId);
        assertEquals(TransactionStatus.COMPLETED, updated.getStatus());
    }

    @Test
    public void testComplete_ByThirdParty() {
        Transaction tx = service.create(itemId, buyerId, null);
        service.approve(tx.getRecordId(), sellerId);

        // 不是交易双方
        User stranger = new User("stranger", "h3", "s3");
        stranger.setRole(UserRole.USER);
        fakeUserDao.insert(stranger);

        assertThrows(BusinessException.class,
                () -> service.complete(tx.getRecordId(), stranger.getUserId()));
    }

    @Test
    public void testComplete_FromPendingNotAllowed() {
        Transaction tx = service.create(itemId, buyerId, null);
        // 跳过 approve，直接 complete（非法）
        assertThrows(BusinessException.class,
                () -> service.complete(tx.getRecordId(), buyerId));
    }

    @Test
    public void testComplete_RejectRejectRejectNotAllowed() {
        // REJECTED 是终态
        Transaction tx = service.create(itemId, buyerId, null);
        service.reject(tx.getRecordId(), sellerId);

        assertThrows(BusinessException.class,
                () -> service.complete(tx.getRecordId(), buyerId));
    }

    // ==================== 取消 ====================

    @Test
    public void testCancel_FromPending() {
        Transaction tx = service.create(itemId, buyerId, null);

        Transaction updated = service.cancel(tx.getRecordId(), buyerId);
        assertEquals(TransactionStatus.CANCELLED, updated.getStatus());
        // 物品回退 ON_SALE
        assertEquals(ItemStatus.ON_SALE, fakeItemDao.findById(itemId).getStatus());
    }

    @Test
    public void testCancel_FromApproved() {
        Transaction tx = service.create(itemId, buyerId, null);
        service.approve(tx.getRecordId(), sellerId);

        Transaction updated = service.cancel(tx.getRecordId(), sellerId);
        assertEquals(TransactionStatus.CANCELLED, updated.getStatus());
        assertEquals(ItemStatus.ON_SALE, fakeItemDao.findById(itemId).getStatus());
    }

    @Test
    public void testCancel_FromCompletedNotAllowed() {
        Transaction tx = service.create(itemId, buyerId, null);
        service.approve(tx.getRecordId(), sellerId);
        service.complete(tx.getRecordId(), buyerId);

        assertThrows(BusinessException.class,
                () -> service.cancel(tx.getRecordId(), buyerId));
    }

    @Test
    public void testCancel_NotInvolved() {
        Transaction tx = service.create(itemId, buyerId, null);

        User stranger = new User("stranger", "h3", "s3");
        fakeUserDao.insert(stranger);

        assertThrows(BusinessException.class,
                () -> service.cancel(tx.getRecordId(), stranger.getUserId()));
    }

    // ==================== 交易全流程串联 ====================

    @Test
    public void testFullFlow_BuyerBuySellerItem() {
        Transaction tx = service.create(itemId, buyerId, "求购，自取");
        assertEquals(TransactionStatus.PENDING, tx.getStatus());
        assertEquals(ItemStatus.RESERVED, fakeItemDao.findById(itemId).getStatus());

        tx = service.approve(tx.getRecordId(), sellerId);
        assertEquals(TransactionStatus.APPROVED, tx.getStatus());

        tx = service.complete(tx.getRecordId(), buyerId);
        assertEquals(TransactionStatus.COMPLETED, tx.getStatus());
        assertEquals(ItemStatus.SOLD, fakeItemDao.findById(itemId).getStatus());
        assertNotNull(tx.getCompleteTime());
    }

    // ==================== Fake DAO ====================

    private static class FakeTransactionDao implements TransactionDao {
        private final Map<Long, Transaction> store = new HashMap<>();
        private long nextId = 1;

        @Override
        public Transaction findById(Long recordId) {
            return recordId == null ? null : store.get(recordId);
        }

        @Override
        public List<Transaction> findAll() {
            return new ArrayList<>(store.values());
        }

        @Override
        public List<Transaction> findByItemId(Long itemId) {
            List<Transaction> r = new ArrayList<>();
            for (Transaction t : store.values()) {
                if (itemId.equals(t.getItemId())) r.add(t);
            }
            return r;
        }

        @Override
        public List<Transaction> findByInitiatorId(Long initiatorId) {
            List<Transaction> r = new ArrayList<>();
            for (Transaction t : store.values()) {
                if (initiatorId.equals(t.getInitiatorId())) r.add(t);
            }
            return r;
        }

        @Override
        public List<Transaction> findByStatus(TransactionStatus status) {
            List<Transaction> r = new ArrayList<>();
            for (Transaction t : store.values()) {
                if (status.equals(t.getStatus())) r.add(t);
            }
            return r;
        }

        @Override
        public Long insert(Transaction tx) {
            Long id = nextId++;
            tx.setRecordId(id);
            store.put(id, tx);
            return id;
        }

        @Override
        public void update(Transaction tx) {
            if (tx.getRecordId() == null) throw new DaoException("recordId is null");
            store.put(tx.getRecordId(), tx);
        }

        @Override
        public void delete(Long recordId) { store.remove(recordId); }

        @Override
        public int count() { return store.size(); }
    }

    private static class FakeItemDao implements ItemDao {
        private final Map<Long, Item> store = new HashMap<>();
        private long nextId = 1;

        @Override
        public Item findById(Long itemId) {
            return itemId == null ? null : store.get(itemId);
        }

        @Override
        public List<Item> findAll() { return new ArrayList<>(store.values()); }

        @Override
        public List<Item> findByPublisherId(Long publisherId) {
            List<Item> r = new ArrayList<>();
            for (Item i : store.values()) if (publisherId.equals(i.getPublisherId())) r.add(i);
            return r;
        }

        @Override
        public List<Item> findByCategoryId(Long categoryId) {
            List<Item> r = new ArrayList<>();
            for (Item i : store.values()) if (categoryId.equals(i.getCategoryId())) r.add(i);
            return r;
        }

        @Override
        public List<Item> findByStatus(ItemStatus status) {
            List<Item> r = new ArrayList<>();
            for (Item i : store.values()) if (status.equals(i.getStatus())) r.add(i);
            return r;
        }

        @Override
        public List<Item> findByKeyword(String keyword) { return new ArrayList<>(); }

        @Override
        public Long insert(Item item) {
            Long id = nextId++;
            item.setItemId(id);
            store.put(id, item);
            return id;
        }

        @Override
        public void update(Item item) {
            if (item.getItemId() == null) throw new DaoException("itemId is null");
            store.put(item.getItemId(), item);
        }

        @Override
        public void delete(Long itemId) { store.remove(itemId); }

        @Override
        public void updateStatus(Long itemId, ItemStatus status) {
            Item item = store.get(itemId);
            if (item != null) item.setStatus(status);
        }

        @Override
        public List<Item> findByPriceRange(double min, double max) { return new ArrayList<>(); }

        @Override
        public List<Item> search(String kw, Long catId, double min, double max, ItemStatus status) {
            return new ArrayList<>();
        }

        @Override
        public int count() { return store.size(); }
    }

    private static class FakeUserDao implements UserDao {
        private final Map<Long, User> store = new HashMap<>();
        private long nextId = 1;

        @Override
        public User findById(Long userId) { return userId == null ? null : store.get(userId); }
        @Override
        public User findByUsername(String username) {
            for (User u : store.values()) if (username.equals(u.getUsername())) return u;
            return null;
        }
        @Override
        public List<User> findAll() { return new ArrayList<>(store.values()); }
        @Override
        public Long insert(User user) {
            Long id = nextId++;
            user.setUserId(id);
            store.put(id, user);
            return id;
        }
        @Override
        public void update(User user) { if (user.getUserId() != null) store.put(user.getUserId(), user); }
        @Override
        public void delete(Long userId) { store.remove(userId); }
        @Override
        public int count() { return store.size(); }
    }
}
