package com.lowcarbon.service;

import com.lowcarbon.dao.UserDao;
import com.lowcarbon.entity.User;
import com.lowcarbon.entity.UserRole;
import com.lowcarbon.exception.BusinessException;
import com.lowcarbon.exception.DaoException;
import com.lowcarbon.util.PasswordUtil;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.*;

/**
 * UserService 单元测试。
 * <p>
 * 使用 {@link FakeUserDao} 替代真实 DAO，隔离数据库依赖，
 * 聚焦验证 Service 层的业务规则、校验逻辑和异常处理。
 *
 * @author lowcarbon-team
 * @since 2026-11-09
 */
public class UserServiceTest {

    private UserService service;
    private FakeUserDao fakeDao;

    @Before
    public void setUp() {
        fakeDao = new FakeUserDao();
        service = new UserService(fakeDao);
    }

    // ==================== 注册 ====================

    @Test
    public void testRegister_Success() {
        User user = service.register("alice", "pass1234", "pass1234");

        assertNotNull("应返回用户对象", user);
        assertNotNull("应生成 userId", user.getUserId());
        assertEquals("alice", user.getUsername());
        assertEquals(UserRole.USER, user.getRole());
        assertEquals("默认信用分应为 100", 100, user.getCreditScore());
        assertTrue("创建时间应大于 0", user.getCreateTime() > 0);
        // 密码不应为明文
        assertNotEquals("pass1234", user.getPasswordHash());
        assertNotNull(user.getSalt());
        // 验证加密正确
        assertTrue("加密后应能验证通过",
                PasswordUtil.verifyPassword("pass1234", user.getPasswordHash(), user.getSalt()));
    }

    @Test
    public void testRegister_UsernameTooShort() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.register("ab", "pass1234", "pass1234"));
        assertTrue(ex.getMessage().contains("至少"));
    }

    @Test
    public void testRegister_UsernameInvalidFormat() {
        assertThrows(BusinessException.class,
                () -> service.register("123abc", "pass1234", "pass1234"));
        assertThrows(BusinessException.class,
                () -> service.register("张三", "pass1234", "pass1234"));
    }

    @Test
    public void testRegister_PasswordTooShort() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.register("alice", "12345", "12345"));
        assertTrue(ex.getMessage().contains("至少"));
    }

    @Test
    public void testRegister_PasswordMismatch() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.register("alice", "pass1234", "pass5678"));
        assertTrue(ex.getMessage().contains("不一致"));
    }

    @Test
    public void testRegister_DuplicateUsername() {
        fakeDao.insert(buildUser("exist_user", "hash", "salt"));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.register("exist_user", "pass1234", "pass1234"));
        assertTrue("应提示用户名已被占用", ex.getMessage().contains("已被占用"));
    }

    // ==================== 登录 ====================

    @Test
    public void testLogin_Success() {
        // 先注册
        User registered = service.register("bob", "secret99", "secret99");
        Long id = registered.getUserId();

        // 再登录
        User loggedIn = service.login("bob", "secret99");
        assertNotNull(loggedIn);
        assertEquals(id, loggedIn.getUserId());
        assertEquals("bob", loggedIn.getUsername());
    }

    @Test
    public void testLogin_WrongPassword() {
        service.register("carol", "myPass123", "myPass123");

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.login("carol", "wrongPass"));
        assertTrue("应提示用户名或密码错误", ex.getMessage().contains("密码错误"));
    }

    @Test
    public void testLogin_NonExistentUser() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.login("nobody_xxx", "anyPass123"));
        assertTrue(ex.getMessage().contains("密码错误"));
    }

    @Test
    public void testLogin_BlankInput() {
        assertThrows(BusinessException.class,
                () -> service.login("", "pass1234"));
        assertThrows(BusinessException.class,
                () -> service.login("user", ""));
        assertThrows(BusinessException.class,
                () -> service.login(null, "pass1234"));
    }

    // ==================== 查询 ====================

    @Test
    public void testFindById_Exists() {
        User user = buildUser("dave", "h", "s");
        fakeDao.insert(user);
        User found = service.findById(user.getUserId());
        assertNotNull(found);
        assertEquals("dave", found.getUsername());
    }

    @Test
    public void testFindById_Null() {
        assertNull(service.findById(null));
    }

    @Test
    public void testFindById_NotExist() {
        assertNull(service.findById(99999L));
    }

    @Test
    public void testFindByUsername() {
        fakeDao.insert(buildUser("eve", "h", "s"));
        User found = service.findByUsername("eve");
        assertNotNull(found);
        assertEquals("eve", found.getUsername());
    }

    @Test
    public void testListAll() {
        fakeDao.insert(buildUser("u1", "h1", "s1"));
        fakeDao.insert(buildUser("u2", "h2", "s2"));

        List<User> list = service.listAll();
        assertEquals(2, list.size());
    }

    @Test
    public void testCountUsers() {
        assertEquals(0, service.countUsers());
        fakeDao.insert(buildUser("u1", "h", "s"));
        assertEquals(1, service.countUsers());
    }

    // ==================== 密码修改 ====================

    @Test
    public void testChangePassword_Success() {
        User registered = service.register("frank", "oldPass123", "oldPass123");
        Long userId = registered.getUserId();

        service.changePassword(userId, "oldPass123", "newPass456");

        // 验证旧密码失效
        assertThrows(BusinessException.class,
                () -> service.login("frank", "oldPass123"));
        // 验证新密码生效
        User loggedIn = service.login("frank", "newPass456");
        assertEquals(userId, loggedIn.getUserId());
    }

    @Test
    public void testChangePassword_UserNotExist() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.changePassword(99999L, "old", "newPass1"));
        assertTrue(ex.getMessage().contains("不存在"));
    }

    @Test
    public void testChangePassword_WrongOldPassword() {
        User registered = service.register("grace", "pass1234", "pass1234");

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.changePassword(registered.getUserId(), "wrongPass", "newPass5678"));
        assertTrue(ex.getMessage().contains("旧密码错误"));
    }

    @Test
    public void testChangePassword_SameAsOld() {
        User registered = service.register("hank", "pass1234", "pass1234");

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.changePassword(registered.getUserId(), "pass1234", "pass1234"));
        assertTrue(ex.getMessage().contains("不能与旧密码相同"));
    }

    @Test
    public void testChangePassword_NewTooShort() {
        User registered = service.register("iris", "pass1234", "pass1234");

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.changePassword(registered.getUserId(), "pass1234", "abc"));
        assertTrue(ex.getMessage().contains("至少"));
    }

    // ==================== 信用分 ====================

    @Test
    public void testAdjustCreditScore() {
        User registered = service.register("jack", "pass1234", "pass1234");
        Long userId = registered.getUserId();

        // 加分
        service.adjustCreditScore(userId, 10);
        User after = fakeDao.findById(userId);
        assertEquals(110, after.getCreditScore());

        // 扣分
        service.adjustCreditScore(userId, -20);
        after = fakeDao.findById(userId);
        assertEquals(90, after.getCreditScore());
    }

    @Test
    public void testAdjustCreditScore_ClampUpper() {
        User registered = service.register("kate", "pass1234", "pass1234");

        service.adjustCreditScore(registered.getUserId(), 500);
        User after = fakeDao.findById(registered.getUserId());
        assertEquals("应被夹紧到 200", 200, after.getCreditScore());
    }

    @Test
    public void testAdjustCreditScore_ClampLower() {
        User registered = service.register("leo", "pass1234", "pass1234");

        service.adjustCreditScore(registered.getUserId(), -1000);
        User after = fakeDao.findById(registered.getUserId());
        assertEquals("应被夹紧到 0", 0, after.getCreditScore());
    }

    @Test
    public void testSetCreditScore() {
        User registered = service.register("mary", "pass1234", "pass1234");

        service.setCreditScore(registered.getUserId(), 50);
        User after = fakeDao.findById(registered.getUserId());
        assertEquals(50, after.getCreditScore());
    }

    @Test
    public void testSetCreditScore_OutOfRange() {
        User registered = service.register("nora", "pass1234", "pass1234");

        assertThrows(BusinessException.class,
                () -> service.setCreditScore(registered.getUserId(), -1));
        assertThrows(BusinessException.class,
                () -> service.setCreditScore(registered.getUserId(), 201));
    }

    // ==================== 删除用户 ====================

    @Test
    public void testDeleteUser_Success() {
        User admin = buildUser("admin", "h", "s", UserRole.ADMIN);
        fakeDao.insert(admin);
        User target = buildUser("victim", "h2", "s2");
        fakeDao.insert(target);

        service.deleteUser(target.getUserId(), admin.getUserId());

        assertNull("用户应已被删除", fakeDao.findById(target.getUserId()));
    }

    @Test
    public void testDeleteUser_NotAdmin() {
        User normal = service.register("normal", "pass1234", "pass1234");
        User target = buildUser("target", "h", "s");
        fakeDao.insert(target);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.deleteUser(target.getUserId(), normal.getUserId()));
        assertTrue(ex.getMessage().contains("管理员"));
    }

    @Test
    public void testDeleteUser_DeleteSelf() {
        User admin = buildUser("admin_self", "h", "s", UserRole.ADMIN);
        fakeDao.insert(admin);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.deleteUser(admin.getUserId(), admin.getUserId()));
        assertTrue(ex.getMessage().contains("不能删除自己"));
    }

    @Test
    public void testDeleteUser_TargetNotExist() {
        User admin = buildUser("admin2", "h", "s", UserRole.ADMIN);
        fakeDao.insert(admin);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.deleteUser(99999L, admin.getUserId()));
        assertTrue(ex.getMessage().contains("不存在"));
    }

    // ==================== DAO 异常传播 ====================

    @Test
    public void testRegister_DaoExceptionWrapped() {
        fakeDao.setNextInsertException(new DaoException("模拟 DB 故障"));

        assertThrows(BusinessException.class,
                () -> service.register("willfail", "pass1234", "pass1234"));
    }

    @Test
    public void testLogin_DaoExceptionWrapped() {
        fakeDao.setNextFindByUsernameException(new DaoException("模拟 DB 故障"));

        assertThrows(BusinessException.class,
                () -> service.login("anyuser", "anyPass1234"));
    }

    // ==================== 辅助方法 ====================

    private User buildUser(String username, String hash, String salt) {
        return buildUser(username, hash, salt, UserRole.USER);
    }

    private User buildUser(String username, String hash, String salt, UserRole role) {
        User u = new User(username, hash, salt);
        u.setRole(role);
        u.setCreditScore(100);
        u.setCreateTime(System.currentTimeMillis());
        return u;
    }

    // ==================== Fake DAO 实现 ====================

    /**
     * 内存版 UserDao 伪实现，用于隔离 Service 层测试。
     * <p>
     * 使用自增 ID + HashMap 模拟 DAO 行为，
     * 并支持注入异常以验证异常传播逻辑。
     */
    private static class FakeUserDao implements UserDao {

        private final Map<Long, User> store = new HashMap<>();
        private long nextId = 1;

        // 异常注入点
        private DaoException nextInsertException;
        private DaoException nextFindByUsernameException;

        void setNextInsertException(DaoException e) {
            this.nextInsertException = e;
        }

        void setNextFindByUsernameException(DaoException e) {
            this.nextFindByUsernameException = e;
        }

        @Override
        public User findById(Long userId) {
            if (userId == null) {
                return null;
            }
            return store.get(userId);
        }

        @Override
        public User findByUsername(String username) {
            if (nextFindByUsernameException != null) {
                DaoException ex = nextFindByUsernameException;
                nextFindByUsernameException = null;
                throw ex;
            }
            if (username == null || username.isBlank()) {
                return null;
            }
            for (User u : store.values()) {
                if (username.equals(u.getUsername())) {
                    return u;
                }
            }
            return null;
        }

        @Override
        public List<User> findAll() {
            return new ArrayList<>(store.values());
        }

        @Override
        public Long insert(User user) {
            if (nextInsertException != null) {
                DaoException ex = nextInsertException;
                nextInsertException = null;
                throw ex;
            }
            Long id = nextId++;
            user.setUserId(id);
            store.put(id, user);
            return id;
        }

        @Override
        public void update(User user) {
            if (user.getUserId() == null) {
                throw new DaoException("userId is null");
            }
            store.put(user.getUserId(), user);
        }

        @Override
        public void delete(Long userId) {
            store.remove(userId);
        }

        @Override
        public int count() {
            return store.size();
        }
    }
}
