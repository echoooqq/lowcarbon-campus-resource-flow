package com.lowcarbon.dao;

import com.lowcarbon.config.DatabaseManager;
import com.lowcarbon.dao.impl.UserDaoImpl;
import com.lowcarbon.entity.User;
import com.lowcarbon.entity.UserRole;
import com.lowcarbon.exception.DaoException;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.*;

/**
 * UserDao 单元测试。
 *
 * @author lowcarbon-team
 * @since 2026-11-09
 */
public class UserDaoTest {

    static {
        System.setProperty("lowcarbon.db.path", "lowcarbon_unit_test.db");
    }

    private static UserDao dao;

    @BeforeClass
    public static void setUpClass() {
        dao = new UserDaoImpl();
    }

    @Before
    public void setUp() {
        DatabaseManager.resetForTest();
    }

    // ==================== 基础查询 ====================

    @Test
    public void testFindById_NotExist() {
        assertNull("空库里不存在的 ID 应返回 null", dao.findById(null));
        assertNull(dao.findById(1L));
    }

    @Test
    public void testFindByUsername_NotExist() {
        assertNull(dao.findByUsername(null));
        assertNull(dao.findByUsername(""));
        assertNull(dao.findByUsername("   "));
        assertNull(dao.findByUsername("nobody_xxx"));
    }

    @Test
    public void testFindAll_Empty() {
        // 内存库初始是空的（schema.sql 只插分类，不插用户）
        List<User> list = dao.findAll();
        assertNotNull(list);
        assertTrue(list.isEmpty());
    }

    @Test
    public void testCount_Empty() {
        assertEquals(0, dao.count());
    }

    // ==================== 新增 ====================

    @Test
    public void testInsert() {
        User u = new User("alice_test", "hash_alice", "salt_alice");
        u.setCreateTime(System.currentTimeMillis());
        u.setRole(UserRole.USER);
        u.setCreditScore(100);

        Long id = dao.insert(u);
        assertNotNull("insert 应返回自增 ID", id);
        assertEquals(id, u.getUserId());
        assertEquals(1, dao.count());
    }

    @Test
    public void testInsert_DefaultValues() {
        User u = new User("bob_default", "hash_bob", "salt_bob");
        u.setCreateTime(System.currentTimeMillis());
        // 不显式设置 role / creditScore，验证实体默认值

        Long id = dao.insert(u);
        User fetched = dao.findById(id);
        assertNotNull(fetched);
        assertEquals("默认角色应为 USER", UserRole.USER, fetched.getRole());
        assertEquals("默认信用分应为 100", 100, fetched.getCreditScore());
    }

    @Test
    public void testInsert_DuplicateUsername() {
        User u1 = new User("dup_user", "h1", "s1");
        u1.setCreateTime(System.currentTimeMillis());
        dao.insert(u1);

        User u2 = new User("dup_user", "h2", "s2");
        u2.setCreateTime(System.currentTimeMillis());
        try {
            dao.insert(u2);
            fail("违反 UNIQUE 约束应抛 DaoException");
        } catch (DaoException expected) {
            assertTrue(expected.getMessage().toLowerCase().contains("唯一")
                    || expected.getMessage().contains("UNIQUE")
                    || expected.getMessage().length() > 0);
        }
    }

    // ==================== 查询回填 ====================

    @Test
    public void testFindById_RoundTrip() {
        User u = new User("charlie", "h_charlie", "s_charlie");
        u.setRole(UserRole.ADMIN);
        u.setCreditScore(85);
        u.setCreateTime(1700000000000L);

        Long id = dao.insert(u);

        User fetched = dao.findById(id);
        assertNotNull(fetched);
        assertEquals("charlie", fetched.getUsername());
        assertEquals("h_charlie", fetched.getPasswordHash());
        assertEquals("s_charlie", fetched.getSalt());
        assertEquals(UserRole.ADMIN, fetched.getRole());
        assertEquals(85, fetched.getCreditScore());
        assertEquals(1700000000000L, fetched.getCreateTime());
    }

    @Test
    public void testFindByUsername_RoundTrip() {
        User u = new User("diana", "h_diana", "s_diana");
        u.setCreateTime(System.currentTimeMillis());
        Long id = dao.insert(u);

        User fetched = dao.findByUsername("diana");
        assertNotNull(fetched);
        assertEquals(id, fetched.getUserId());
    }

    // ==================== 更新 ====================

    @Test
    public void testUpdate() {
        User u = new User("eve_old", "h_old", "s_old");
        u.setCreateTime(System.currentTimeMillis());
        u.setRole(UserRole.USER);
        u.setCreditScore(50);
        Long id = dao.insert(u);

        u.setUsername("eve_new");
        u.setPasswordHash("h_new");
        u.setSalt("s_new");
        u.setRole(UserRole.ADMIN);
        u.setCreditScore(200);
        dao.update(u);

        User fetched = dao.findById(id);
        assertEquals("eve_new", fetched.getUsername());
        assertEquals("h_new", fetched.getPasswordHash());
        assertEquals("s_new", fetched.getSalt());
        assertEquals(UserRole.ADMIN, fetched.getRole());
        assertEquals(200, fetched.getCreditScore());
    }

    @Test
    public void testUpdate_NoId() {
        User u = new User("no_id", "h", "s");
        try {
            dao.update(u);
            fail("userId 为 null 应抛 DaoException");
        } catch (DaoException expected) {
            assertTrue(expected.getMessage().contains("userId"));
        }
    }

    // ==================== 删除 ====================

    @Test
    public void testDelete() {
        User u = new User("frank", "h_f", "s_f");
        u.setCreateTime(System.currentTimeMillis());
        Long id = dao.insert(u);
        assertTrue(dao.count() > 0);

        dao.delete(id);
        assertNull("删除后应查不到", dao.findById(id));
    }

    @Test
    public void testDelete_Null() {
        // 不应抛异常
        dao.delete(null);
    }
}
