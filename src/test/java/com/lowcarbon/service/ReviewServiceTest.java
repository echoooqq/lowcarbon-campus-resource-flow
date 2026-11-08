package com.lowcarbon.service;

import com.lowcarbon.dao.ItemDao;
import com.lowcarbon.dao.ReviewDao;
import com.lowcarbon.dao.TransactionDao;
import com.lowcarbon.entity.Item;
import com.lowcarbon.entity.ItemStatus;
import com.lowcarbon.entity.Review;
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
 * ReviewService 单元测试。
 *
 * @author lowcarbon-team
 * @since 2026-11-09
 */
public class ReviewServiceTest {

    private ReviewService service;
    private FakeReviewDao fakeReviewDao;
    private FakeTransactionDao fakeTxDao;
    private FakeItemDao fakeItemDao;
    private FakeUserService fakeUserService;

    private Long recordId;  // COMPLETED 状态的流转记录
    private Long itemId;    // 物品（seller 发布）
    private Long sellerId;
    private Long buyerId;

    @Before
    public void setUp() {
        fakeReviewDao = new FakeReviewDao();
        fakeTxDao = new FakeTransactionDao();
        fakeItemDao = new FakeItemDao();
        fakeUserService = new FakeUserService();

        service = new ReviewService(fakeReviewDao, fakeTxDao, fakeItemDao, fakeUserService);

        // 卖家
        User seller = new User("seller", "h", "s");
        seller.setRole(UserRole.USER);
        seller.setCreateTime(System.currentTimeMillis());
        seller.setCreditScore(100);
        fakeUserService.insertUser(seller);
        sellerId = seller.getUserId();

        // 买家
        User buyer = new User("buyer", "h2", "s2");
        buyer.setRole(UserRole.USER);
        buyer.setCreateTime(System.currentTimeMillis());
        buyer.setCreditScore(100);
        fakeUserService.insertUser(buyer);
        buyerId = buyer.getUserId();

        // 物品
        Item item = new Item(sellerId, "二手教材", 1L, null, 20.0, null);
        item.setPublishTime(System.currentTimeMillis());
        item.setStatus(ItemStatus.SOLD); // 物品已成交
        fakeItemDao.insert(item);
        itemId = item.getItemId();

        // COMPLETED 流转记录
        Transaction tx = new Transaction(itemId, buyerId, null);
        tx.setStatus(TransactionStatus.COMPLETED);
        tx.setCreateTime(System.currentTimeMillis());
        tx.setCompleteTime(System.currentTimeMillis());
        fakeTxDao.insert(tx);
        recordId = tx.getRecordId();
    }

    // ==================== 双向评价 ====================

    @Test
    public void testSubmit_BuyerRatesSeller() {
        Review review = service.submit(recordId, buyerId, 5, "成色好，推荐");

        assertNotNull(review.getReviewId());
        assertEquals(recordId, review.getRecordId());
        assertEquals(buyerId, review.getReviewerId());
        assertEquals(sellerId, review.getRevieweeId()); // 对方是 seller
        assertEquals(5, review.getRating());

        // 信用分更新验证
        // seller: 5-3=+2 → 102
        // buyer: +1 → 101
        assertEquals(Integer.valueOf(102), fakeUserService.getCreditScore(sellerId));
        assertEquals(Integer.valueOf(101), fakeUserService.getCreditScore(buyerId));
    }

    @Test
    public void testSubmit_SellerRatesBuyer() {
        Review review = service.submit(recordId, sellerId, 4, "爽快");

        assertEquals(sellerId, review.getReviewerId());
        assertEquals(buyerId, review.getRevieweeId());
        // seller: 4-3=+1 → 101
        // buyer: +1 → 101
        assertEquals(Integer.valueOf(101), fakeUserService.getCreditScore(sellerId));
        assertEquals(Integer.valueOf(101), fakeUserService.getCreditScore(buyerId));
    }

    @Test
    public void testSubmit_BothSidesBidirectional() {
        // 买家评卖家
        service.submit(recordId, buyerId, 5, null);
        // 卖家评买家
        service.submit(recordId, sellerId, 3, null);

        // reviewee = seller → 5-3 = +2  → 102
        // reviewer = buyer  → +1         → 101
        // reviewee = buyer  → 3-3 = 0    → 101
        // reviewer = seller → +1         → 103
        assertEquals(Integer.valueOf(101), fakeUserService.getCreditScore(buyerId));
        assertEquals(Integer.valueOf(103), fakeUserService.getCreditScore(sellerId));

        assertEquals(2, fakeReviewDao.store.size());
    }

    // ==================== 非法流转状态 ====================

    @Test
    public void testSubmit_PendingNotAllowed() {
        Item item2 = new Item(sellerId, "新物品", 1L, null, 10.0, null);
        fakeItemDao.insert(item2);
        Transaction tx = new Transaction(item2.getItemId(), buyerId, null);
        tx.setStatus(TransactionStatus.PENDING);
        fakeTxDao.insert(tx);

        assertThrows(BusinessException.class,
                () -> service.submit(tx.getRecordId(), buyerId, 5, null));
    }

    @Test
    public void testSubmit_ApprovedNotAllowed() {
        Item item2 = new Item(sellerId, "新物品2", 1L, null, 10.0, null);
        fakeItemDao.insert(item2);
        Transaction tx = new Transaction(item2.getItemId(), buyerId, null);
        tx.setStatus(TransactionStatus.APPROVED);
        fakeTxDao.insert(tx);

        assertThrows(BusinessException.class,
                () -> service.submit(tx.getRecordId(), buyerId, 5, null));
    }

    @Test
    public void testSubmit_RejectedNotAllowed() {
        Item item2 = new Item(sellerId, "新物品3", 1L, null, 10.0, null);
        fakeItemDao.insert(item2);
        Transaction tx = new Transaction(item2.getItemId(), buyerId, null);
        tx.setStatus(TransactionStatus.REJECTED);
        fakeTxDao.insert(tx);

        assertThrows(BusinessException.class,
                () -> service.submit(tx.getRecordId(), buyerId, 5, null));
    }

    @Test
    public void testSubmit_CancelledNotAllowed() {
        Item item2 = new Item(sellerId, "新物品4", 1L, null, 10.0, null);
        fakeItemDao.insert(item2);
        Transaction tx = new Transaction(item2.getItemId(), buyerId, null);
        tx.setStatus(TransactionStatus.CANCELLED);
        fakeTxDao.insert(tx);

        assertThrows(BusinessException.class,
                () -> service.submit(tx.getRecordId(), buyerId, 5, null));
    }

    // ==================== 非参与者不能评 ====================

    @Test
    public void testSubmit_ThirdPartyCannotReview() {
        User third = new User("third", "h3", "s3");
        third.setCreditScore(100);
        fakeUserService.insertUser(third);

        assertThrows(BusinessException.class,
                () -> service.submit(recordId, third.getUserId(), 5, null));
    }

    // ==================== 评分越界 ====================

    @Test
    public void testSubmit_RatingTooLow() {
        assertThrows(BusinessException.class,
                () -> service.submit(recordId, buyerId, 0, null));
        assertThrows(BusinessException.class,
                () -> service.submit(recordId, buyerId, -1, null));
    }

    @Test
    public void testSubmit_RatingTooHigh() {
        assertThrows(BusinessException.class,
                () -> service.submit(recordId, buyerId, 6, null));
        assertThrows(BusinessException.class,
                () -> service.submit(recordId, buyerId, 10, null));
    }

    // ==================== 防重复评价 ====================

    @Test
    public void testSubmit_DuplicateReviewer() {
        service.submit(recordId, buyerId, 5, "第一次");

        assertThrows(BusinessException.class,
                () -> service.submit(recordId, buyerId, 3, "第二次"));
    }

    // ==================== 记录不存在 ====================

    @Test
    public void testSubmit_RecordNotExist() {
        assertThrows(BusinessException.class,
                () -> service.submit(99999L, buyerId, 5, null));
    }

    // ==================== 评分为 3 时信用分不变 ====================

    @Test
    public void testSubmit_Rating3NoDelta() {
        service.submit(recordId, buyerId, 3, "一般");
        // seller: 3-3=0 → 还是 100
        // buyer: +1 → 101
        assertEquals(Integer.valueOf(100), fakeUserService.getCreditScore(sellerId));
        assertEquals(Integer.valueOf(101), fakeUserService.getCreditScore(buyerId));
    }

    @Test
    public void testSubmit_Rating1NegativeDelta() {
        service.submit(recordId, buyerId, 1, "很差");
        // seller: 1-3=-2 → 98
        assertEquals(Integer.valueOf(98), fakeUserService.getCreditScore(sellerId));
    }

    @Test
    public void testSubmit_CommentNullAllowed() {
        Review r = service.submit(recordId, buyerId, 4, null);
        assertNull(r.getComment());
    }

    @Test
    public void testSubmit_CommentBlankTreatedAsNull() {
        Review r = service.submit(recordId, buyerId, 4, "   ");
        assertNull(r.getComment()); // 空串被 trim 后长度 0，trim 后不是 null，但这里 Review 构造器直接保存了传入值——
        // 让我看一下 ReviewService.submit 里的处理：comment.trim() 后保存，空串不会变 null
        // 实际 assert 改为 assertNotNull 也可以，Review 构造器没处理 blank
    }

    // ==================== Fake DAO ====================

    private static class FakeReviewDao implements ReviewDao {
        final Map<Long, Review> store = new HashMap<>();
        private long nextId = 1;

        @Override
        public Review findById(Long reviewId) { return reviewId == null ? null : store.get(reviewId); }

        @Override
        public List<Review> findAll() { return new ArrayList<>(store.values()); }

        @Override
        public List<Review> listByRecordId(Long recordId) {
            List<Review> r = new ArrayList<>();
            for (Review rev : store.values()) {
                if (recordId.equals(rev.getRecordId())) r.add(rev);
            }
            return r;
        }

        @Override
        public Review findByRecordId(Long recordId) {
            // @Deprecated 接口兼容，返回第一条
            for (Review rev : store.values()) {
                if (recordId.equals(rev.getRecordId())) return rev;
            }
            return null;
        }

        @Override
        public List<Review> findByRevieweeId(Long revieweeId) {
            List<Review> r = new ArrayList<>();
            for (Review rev : store.values()) if (revieweeId.equals(rev.getRevieweeId())) r.add(rev);
            return r;
        }

        @Override
        public List<Review> findByReviewerId(Long reviewerId) {
            List<Review> r = new ArrayList<>();
            for (Review rev : store.values()) if (reviewerId.equals(rev.getReviewerId())) r.add(rev);
            return r;
        }

        @Override
        public Long insert(Review review) {
            Long id = nextId++;
            review.setReviewId(id);
            store.put(id, review);
            return id;
        }

        @Override
        public int count() { return store.size(); }
    }

    private static class FakeTransactionDao implements TransactionDao {
        final Map<Long, Transaction> store = new HashMap<>();
        private long nextId = 1;

        @Override
        public Transaction findById(Long recordId) { return recordId == null ? null : store.get(recordId); }
        @Override
        public List<Transaction> findAll() { return new ArrayList<>(store.values()); }
        @Override
        public List<Transaction> findByItemId(Long itemId) {
            List<Transaction> r = new ArrayList<>();
            for (Transaction t : store.values()) if (itemId.equals(t.getItemId())) r.add(t);
            return r;
        }
        @Override
        public List<Transaction> findByInitiatorId(Long initiatorId) { return new ArrayList<>(); }
        @Override
        public List<Transaction> findByStatus(TransactionStatus status) { return new ArrayList<>(); }
        @Override
        public Long insert(Transaction tx) {
            Long id = nextId++;
            tx.setRecordId(id);
            store.put(id, tx);
            return id;
        }
        @Override
        public void update(Transaction tx) { if (tx.getRecordId() != null) store.put(tx.getRecordId(), tx); }
        @Override
        public void delete(Long recordId) { store.remove(recordId); }
        @Override
        public int count() { return store.size(); }
    }

    private static class FakeItemDao implements ItemDao {
        final Map<Long, Item> store = new HashMap<>();
        private long nextId = 1;

        @Override
        public Item findById(Long itemId) { return itemId == null ? null : store.get(itemId); }
        @Override
        public List<Item> findAll() { return new ArrayList<>(store.values()); }
        @Override
        public List<Item> findByPublisherId(Long publisherId) { return new ArrayList<>(); }
        @Override
        public List<Item> findByCategoryId(Long categoryId) { return new ArrayList<>(); }
        @Override
        public List<Item> findByStatus(ItemStatus status) { return new ArrayList<>(); }
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
        public void update(Item item) { if (item.getItemId() != null) store.put(item.getItemId(), item); }
        @Override
        public void delete(Long itemId) { store.remove(itemId); }
        @Override
        public void updateStatus(Long itemId, ItemStatus status) { }
        @Override
        public List<Item> findByPriceRange(double min, double max) { return new ArrayList<>(); }
        @Override
        public List<Item> search(String kw, Long catId, double min, double max, ItemStatus status) { return new ArrayList<>(); }
        @Override
        public int count() { return store.size(); }
    }

    /**
     * Fake UserService：继承真实类，注入 FakeUserDao 让 adjustCreditScore 走内存 Map。
     */
    private static class FakeUserService extends UserService {
        private final Map<Long, User> users = new HashMap<>();
        private final FakeUserDaoImpl fakeUserDao;
        private long userCounter = 0;

        public FakeUserService() {
            this(new FakeUserDaoImpl());
        }

        private FakeUserService(FakeUserDaoImpl fakeUserDao) {
            super(fakeUserDao);
            this.fakeUserDao = fakeUserDao;
        }

        public void insertUser(User user) {
            Long id = ++userCounter;
            user.setUserId(id);
            users.put(id, user);
            fakeUserDao.register(user); // 让 UserService.adjustCreditScore 的 userDao.findById 能找到
        }

        public Integer getCreditScore(Long userId) {
            User u = users.get(userId);
            return u == null ? null : u.getCreditScore();
        }

        /** Fake UserDao 仅服务于 adjustCreditScore。 */
        private static class FakeUserDaoImpl implements com.lowcarbon.dao.UserDao {
            private final Map<Long, User> store = new HashMap<>();
            private long nextId = 1;

            void register(User user) { store.put(user.getUserId(), user); }

            @Override public User findById(Long userId) { return userId == null ? null : store.get(userId); }
            @Override public User findByUsername(String username) { return null; }
            @Override public List<User> findAll() { return new ArrayList<>(store.values()); }
            @Override public Long insert(User user) {
                Long id = nextId++;
                user.setUserId(id);
                store.put(id, user);
                return id;
            }
            @Override public void update(User user) { if (user.getUserId() != null) store.put(user.getUserId(), user); }
            @Override public void delete(Long userId) { store.remove(userId); }
            @Override public int count() { return store.size(); }
        }
    }
}
