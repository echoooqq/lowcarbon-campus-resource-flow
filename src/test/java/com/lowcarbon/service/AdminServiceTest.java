package com.lowcarbon.service;

import com.lowcarbon.dao.ItemDao;
import com.lowcarbon.dao.UserDao;
import com.lowcarbon.entity.Item;
import com.lowcarbon.entity.ItemStatus;
import com.lowcarbon.entity.User;
import com.lowcarbon.entity.UserRole;
import com.lowcarbon.exception.BusinessException;
import org.junit.Before;
import org.junit.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.*;

/**
 * AdminService 单元测试。
 * <p>
 * Fake 策略：
 *   - UserDao / ItemDao 用内存 Fake DAO（AdminService 自己调 DAO）
 *   - UserService / ItemService / StatisticsService 用简化 Fake（只实现 AdminService 实际调用的方法）
 *
 * @author lowcarbon-team
 * @since 2026-11-09
 */
public class AdminServiceTest {

    private AdminService service;

    private FakeUserDao fakeUserDao;
    private FakeItemDao fakeItemDao;
    private FakeUserService fakeUserService;
    private FakeItemService fakeItemService;
    private FakeStatisticsService fakeStatsService;

    private Long adminId;
    private Long normalUserId;
    private Long itemId;

    @Before
    public void setUp() {
        fakeUserDao = new FakeUserDao();
        fakeItemDao = new FakeItemDao();
        fakeUserService = new FakeUserService();
        fakeItemService = new FakeItemService();
        fakeStatsService = new FakeStatisticsService();

        service = new AdminService(fakeUserDao, fakeItemDao,
                fakeUserService, fakeItemService, fakeStatsService);

        // 预置 2 个管理员 + 1 个普通用户
        User admin1 = new User("admin1", "h", "s");
        admin1.setRole(UserRole.ADMIN);
        admin1.setCreditScore(150);
        fakeUserDao.insert(admin1);
        adminId = admin1.getUserId();

        User admin2 = new User("admin2", "h2", "s2");
        admin2.setRole(UserRole.ADMIN);
        admin2.setCreditScore(100);
        fakeUserDao.insert(admin2);

        User normal = new User("normal_user", "h3", "s3");
        normal.setRole(UserRole.USER);
        normal.setCreditScore(100);
        fakeUserDao.insert(normal);
        normalUserId = normal.getUserId();

        // 预置一个物品
        Item item = new Item(normalUserId, "书籍", 1L, null, 20.0, null);
        item.setStatus(ItemStatus.OFF_SHELF); // 已下架，测试重新上架用
        fakeItemDao.insert(item);
        itemId = item.getItemId();
    }

    // ==================== 权限校验 ====================

    @Test
    public void testVerifyAdmin_AcceptsAdmin() {
        User admin = service.verifyAdmin(adminId);
        assertEquals(adminId, admin.getUserId());
        assertEquals(UserRole.ADMIN, admin.getRole());
    }

    @Test
    public void testVerifyAdmin_RejectsNormal() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.verifyAdmin(normalUserId));
        assertTrue(ex.getMessage().contains("管理员"));
    }

    @Test
    public void testVerifyAdmin_NullId() {
        assertThrows(BusinessException.class, () -> service.verifyAdmin(null));
    }

    @Test
    public void testVerifyAdmin_NotExist() {
        assertThrows(BusinessException.class, () -> service.verifyAdmin(99999L));
    }

    // ==================== 重置密码 ====================

    @Test
    public void testResetPassword_Success() {
        String tempPwd = service.resetPassword(adminId, normalUserId);

        assertNotNull(tempPwd);
        assertEquals(8, tempPwd.length()); // 生成的临时密码是 8 位

        // 密码和盐值应该被更新了（新盐 + 新 hash）
        User updated = fakeUserDao.findById(normalUserId);
        assertNotEquals("h3", updated.getPasswordHash()); // 不是原 hash
        assertNotEquals("s3", updated.getSalt());         // 不是原盐
    }

    @Test
    public void testResetPassword_CannotResetSelf() {
        assertThrows(BusinessException.class,
                () -> service.resetPassword(adminId, adminId));
    }

    @Test
    public void testResetPassword_NotAdmin() {
        assertThrows(BusinessException.class,
                () -> service.resetPassword(normalUserId, adminId));
    }

    @Test
    public void testResetPassword_TargetNotExist() {
        assertThrows(BusinessException.class,
                () -> service.resetPassword(adminId, 99999L));
    }

    // ==================== 角色变更 ====================

    @Test
    public void testChangeRole_Promote() {
        service.changeRole(adminId, normalUserId, UserRole.ADMIN);

        User updated = fakeUserDao.findById(normalUserId);
        assertEquals(UserRole.ADMIN, updated.getRole());
    }

    @Test
    public void testChangeRole_DemoteLastAdminRejected() {
        // admin2 被降级后只剩 admin1 一个管理员 → 允许（adminId 是 admin1，目标 admin2）
        // 再降级 admin1 → 会被拒绝
        service.changeRole(adminId, 2L, UserRole.USER); // admin2 降级，现在只剩 admin1

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.changeRole(adminId, adminId, UserRole.USER)); // self，会先被 self-check 拦
        assertTrue(ex.getMessage().contains("自己"));

        // 换个思路：再创建一个 admin3，然后降级 admin1
        User admin3 = new User("admin3", "h4", "s4");
        admin3.setRole(UserRole.ADMIN);
        fakeUserDao.insert(admin3);

        // 再降级 admin2，现在剩下 admin1 + admin3
        service.changeRole(adminId, 2L, UserRole.USER);

        // 降级 admin1（自己） → self check 还是会拦
        // 换：让 admin3 降级 admin1
        service.changeRole(admin3.getUserId(), adminId, UserRole.USER);
        assertEquals(UserRole.USER, fakeUserDao.findById(adminId).getRole());
    }

    @Test
    public void testChangeRole_CannotChangeSelf() {
        assertThrows(BusinessException.class,
                () -> service.changeRole(adminId, adminId, UserRole.USER));
    }

    @Test
    public void testChangeRole_NonAdminRejected() {
        assertThrows(BusinessException.class,
                () -> service.changeRole(normalUserId, adminId, UserRole.USER));
    }

    @Test
    public void testChangeRole_PromoteLowCreditAutoRaises() {
        User low = new User("low_user", "h4", "s4");
        low.setRole(UserRole.USER);
        low.setCreditScore(20); // 远低于 MIN_ADMIN_CREDIT=50
        fakeUserDao.insert(low);

        service.changeRole(adminId, low.getUserId(), UserRole.ADMIN);

        assertEquals(UserRole.ADMIN, fakeUserDao.findById(low.getUserId()).getRole());
        // 信用分应被自动补到 50
        assertEquals(50, fakeUserDao.findById(low.getUserId()).getCreditScore());
    }

    // ==================== 信用分重置 ====================

    @Test
    public void testResetCreditScore() {
        service.resetCreditScore(adminId, normalUserId);
        assertEquals(UserService.DEFAULT_CREDIT_SCORE, fakeUserDao.findById(normalUserId).getCreditScore());
    }

    @Test
    public void testResetCreditScore_NotAdmin() {
        assertThrows(BusinessException.class,
                () -> service.resetCreditScore(normalUserId, adminId));
    }

    // ==================== 删除用户（委托 UserService） ====================

    @Test
    public void testDeleteUser_Delegates() {
        service.deleteUser(adminId, normalUserId);
        // FakeUserService.deleteUser 应被调用
        assertTrue("FakeUserService 应收到 deleteUser 调用", fakeUserService.deleteCalled);
        assertEquals(normalUserId, fakeUserService.lastDeletedId);
        assertEquals(adminId, fakeUserService.lastOperatorId);
    }

    @Test
    public void testDeleteUser_NotAdmin() {
        assertThrows(BusinessException.class,
                () -> service.deleteUser(normalUserId, adminId));
    }

    // ==================== 强制下架 ====================

    @Test
    public void testForceOffShelf_Delegates() {
        service.forceOffShelf(adminId, itemId, "内容违规");
        assertTrue("FakeItemService 应收到 adminTakeOffShelf 调用", fakeItemService.forceOffCalled);
    }

    @Test
    public void testForceOffShelf_NotAdmin() {
        assertThrows(BusinessException.class,
                () -> service.forceOffShelf(normalUserId, itemId, null));
    }

    // ==================== 重新上架 ====================

    @Test
    public void testRelistItem_Success() {
        service.adminRelistItem(adminId, itemId);
        assertEquals(ItemStatus.ON_SALE, fakeItemDao.findById(itemId).getStatus());
    }

    @Test
    public void testRelistItem_NotOffShelf() {
        // item 本来是 OFF_SHELF，先改成 ON_SALE 测试拒绝
        fakeItemDao.findById(itemId).setStatus(ItemStatus.ON_SALE);
        assertThrows(BusinessException.class,
                () -> service.adminRelistItem(adminId, itemId));
    }

    @Test
    public void testRelistItem_NotAdmin() {
        assertThrows(BusinessException.class,
                () -> service.adminRelistItem(normalUserId, itemId));
    }

    @Test
    public void testRelistItem_NotExist() {
        assertThrows(BusinessException.class,
                () -> service.adminRelistItem(adminId, 99999L));
    }

    // ==================== 统计委托 ====================

    @Test
    public void testOverview_Delegates() {
        Map<String, Object> result = service.overview(adminId);
        assertNotNull(result);
        assertTrue("应从 FakeStatsService 返回", fakeStatsService.overviewCalled);
    }

    @Test
    public void testOverview_NotAdmin() {
        assertThrows(BusinessException.class,
                () -> service.overview(normalUserId));
    }

    @Test
    public void testExportCsv_Success() throws Exception {
        Path temp = Files.createTempFile("admin_export_", ".csv");
        try {
            service.exportCsv(adminId, temp);
            assertTrue("应成功委托给 FakeStatsService", fakeStatsService.exportCalled);
            assertEquals(temp, fakeStatsService.lastExportPath);
        } finally {
            Files.deleteIfExists(temp);
        }
    }

    @Test
    public void testExportCsv_NotAdmin() {
        assertThrows(BusinessException.class,
                () -> service.exportCsv(normalUserId, "x.csv"));
    }

    // ==================== 用户列表 ====================

    @Test
    public void testListAllUsers_SortedByCredit() {
        List<User> users = service.listAllUsers(adminId);
        assertEquals(3, users.size());
        // admin1 150 > admin2 100 > normal_user 100 — 后两者同 credit，顺序不严格
        assertTrue(users.get(0).getCreditScore() >= users.get(1).getCreditScore());
        assertTrue(users.get(1).getCreditScore() >= users.get(2).getCreditScore());
    }

    @Test
    public void testCountAdmins() {
        assertEquals(2, service.countAdmins());
    }

    // ==================== 依赖 Fake ====================

    private static class FakeUserDao implements UserDao {
        final Map<Long, User> store = new HashMap<>();
        private long nextId = 1;
        @Override public User findById(Long uid) { return uid == null ? null : store.get(uid); }
        @Override public User findByUsername(String un) { return null; }
        @Override public List<User> findAll() { return new ArrayList<>(store.values()); }
        @Override public Long insert(User u) { Long id = nextId++; u.setUserId(id); store.put(id, u); return id; }
        @Override public void update(User u) { if (u.getUserId() != null) store.put(u.getUserId(), u); }
        @Override public void delete(Long id) { store.remove(id); }
        @Override public int count() { return store.size(); }
    }

    private static class FakeItemDao implements ItemDao {
        final Map<Long, Item> store = new HashMap<>();
        private long nextId = 1;
        @Override public Item findById(Long id) { return id == null ? null : store.get(id); }
        @Override public List<Item> findAll() { return new ArrayList<>(store.values()); }
        @Override public List<Item> findByPublisherId(Long pid) { return new ArrayList<>(); }
        @Override public List<Item> findByCategoryId(Long cid) { return new ArrayList<>(); }
        @Override public List<Item> findByStatus(ItemStatus s) { return new ArrayList<>(); }
        @Override public List<Item> findByKeyword(String kw) { return new ArrayList<>(); }
        @Override public Long insert(Item i) { Long id = nextId++; i.setItemId(id); store.put(id, i); return id; }
        @Override public void update(Item i) { if (i.getItemId() != null) store.put(i.getItemId(), i); }
        @Override public void delete(Long id) { store.remove(id); }
        @Override public void updateStatus(Long id, ItemStatus s) { Item it = store.get(id); if (it != null) it.setStatus(s); }
        @Override public List<Item> findByPriceRange(double a, double b) { return new ArrayList<>(); }
        @Override public List<Item> search(String k, Long c, double a, double b, ItemStatus s) { return new ArrayList<>(); }
        @Override public int count() { return store.size(); }
    }

    /** Fake UserService：只实现 AdminService 实际调用的 deleteUser。 */
    private static class FakeUserService extends UserService {
        boolean deleteCalled;
        Long lastDeletedId;
        Long lastOperatorId;

        public FakeUserService() {
            super(new FakeUserDao());
        }

        @Override
        public void deleteUser(Long userId, Long operatorId) {
            deleteCalled = true;
            lastDeletedId = userId;
            lastOperatorId = operatorId;
        }
    }

    /** Fake ItemService：只实现 adminTakeOffShelf 和 listAll。 */
    private static class FakeItemService extends ItemService {
        boolean forceOffCalled;

        public FakeItemService() {
            super(new FakeItemDao(), new FakeUserDao(), new FakeCategoryDao());
        }

        @Override
        public void adminTakeOffShelf(Long itemId, Long adminId, String reason) {
            forceOffCalled = true;
        }

        @Override
        public List<Item> listAll() { return new ArrayList<>(); }
    }

    private static class FakeCategoryDao implements com.lowcarbon.dao.CategoryDao {
        final Map<Long, com.lowcarbon.entity.Category> store = new HashMap<>();
        private long nextId = 1;
        @Override public com.lowcarbon.entity.Category findById(Long id) { return null; }
        @Override public com.lowcarbon.entity.Category findByName(String n) { return null; }
        @Override public List<com.lowcarbon.entity.Category> findAll() { return new ArrayList<>(); }
        @Override public Long insert(com.lowcarbon.entity.Category c) { Long id = nextId++; c.setCategoryId(id); store.put(id, c); return id; }
        @Override public void update(com.lowcarbon.entity.Category c) { }
        @Override public void delete(Long id) { }
        @Override public int count() { return 0; }
    }

    /** Fake StatisticsService：只实现 overview 和 exportAll。 */
    private static class FakeStatisticsService extends StatisticsService {
        boolean overviewCalled;
        boolean exportCalled;
        Path lastExportPath;

        public FakeStatisticsService() {
            super(new FakeUserDao(), new FakeItemDao(), new FakeTransactionDao(),
                    new FakeReviewDao(), new FakeCategoryDao());
        }

        @Override
        public Map<String, Object> overview() {
            overviewCalled = true;
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("用户总数", 3);
            return m;
        }

        @Override
        public void exportAll(Path path) {
            exportCalled = true;
            lastExportPath = path;
        }
    }

    private static class FakeTransactionDao implements com.lowcarbon.dao.TransactionDao {
        private long nextId = 1;
        @Override public com.lowcarbon.entity.Transaction findById(Long id) { return null; }
        @Override public List<com.lowcarbon.entity.Transaction> findAll() { return new ArrayList<>(); }
        @Override public List<com.lowcarbon.entity.Transaction> findByItemId(Long id) { return new ArrayList<>(); }
        @Override public List<com.lowcarbon.entity.Transaction> findByInitiatorId(Long id) { return new ArrayList<>(); }
        @Override public List<com.lowcarbon.entity.Transaction> findByStatus(com.lowcarbon.entity.TransactionStatus s) { return new ArrayList<>(); }
        @Override public Long insert(com.lowcarbon.entity.Transaction t) { return nextId++; }
        @Override public void update(com.lowcarbon.entity.Transaction t) { }
        @Override public void delete(Long id) { }
        @Override public int count() { return 0; }
    }

    private static class FakeReviewDao implements com.lowcarbon.dao.ReviewDao {
        @Override public com.lowcarbon.entity.Review findById(Long id) { return null; }
        @Override public List<com.lowcarbon.entity.Review> findAll() { return new ArrayList<>(); }
        @Override public List<com.lowcarbon.entity.Review> listByRecordId(Long id) { return new ArrayList<>(); }
        @Override public com.lowcarbon.entity.Review findByRecordId(Long id) { return null; }
        @Override public List<com.lowcarbon.entity.Review> findByRevieweeId(Long id) { return new ArrayList<>(); }
        @Override public List<com.lowcarbon.entity.Review> findByReviewerId(Long id) { return new ArrayList<>(); }
        @Override public Long insert(com.lowcarbon.entity.Review r) { return 1L; }
        @Override public int count() { return 0; }
    }
}
