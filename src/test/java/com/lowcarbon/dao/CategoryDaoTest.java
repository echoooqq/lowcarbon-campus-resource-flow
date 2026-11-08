package com.lowcarbon.dao;

import com.lowcarbon.config.DatabaseManager;
import com.lowcarbon.dao.impl.CategoryDaoImpl;
import com.lowcarbon.entity.Category;
import com.lowcarbon.exception.DaoException;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.*;

/**
 * CategoryDao 单元测试。
 * <p>
 * 使用固定临时文件数据库，避免 SQLite 内存模式连接不共享的问题。
 *
 * @author lowcarbon-team
 * @since 2026-11-09
 */
public class CategoryDaoTest {

    static {
        System.setProperty("lowcarbon.db.path", "lowcarbon_unit_test.db");
    }

    private static CategoryDao dao;

    @BeforeClass
    public static void setUpClass() {
        dao = new CategoryDaoImpl();
    }

    @Before
    public void setUp() {
        DatabaseManager.resetForTest();
    }

    // ==================== 基础查询 ====================

    @Test
    public void testFindById_Preloaded() {
        // schema.sql 预置了 4 个分类，ID 从 1 开始
        Category c1 = dao.findById(1L);
        assertNotNull("预置分类 ID=1 应存在", c1);
        assertEquals("教材", c1.getName());

        Category c2 = dao.findById(2L);
        assertNotNull("预置分类 ID=2 应存在", c2);
        assertEquals("数码配件", c2.getName());
    }

    @Test
    public void testFindById_Null() {
        assertNull("null ID 应返回 null", dao.findById(null));
    }

    @Test
    public void testFindById_NotExist() {
        assertNull("不存在的 ID 应返回 null", dao.findById(999L));
    }

    @Test
    public void testFindByName_Preloaded() {
        Category c = dao.findByName("教材");
        assertNotNull("按名称查询预置分类应存在", c);
        assertEquals(Long.valueOf(1L), c.getCategoryId());
    }

    @Test
    public void testFindByName_NotExist() {
        assertNull("不存在的名称应返回 null", dao.findByName("不存在的分类xxx"));
    }

    @Test
    public void testFindByName_NullOrBlank() {
        assertNull(dao.findByName(null));
        assertNull(dao.findByName(""));
        assertNull(dao.findByName("   "));
    }

    @Test
    public void testFindAll_Preloaded() {
        List<Category> list = dao.findAll();
        assertNotNull(list);
        assertTrue("schema.sql 预置 4 个分类，总数应 >= 4", list.size() >= 4);
    }

    @Test
    public void testCount_Preloaded() {
        int c = dao.count();
        assertTrue("预置分类数应 >= 4", c >= 4);
    }

    // ==================== 新增 ====================

    @Test
    public void testInsert() {
        Category cat = new Category("测试分类", "用于单元测试");
        Long id = dao.insert(cat);

        assertNotNull("insert 应返回自增 ID", id);
        assertNotNull("insert 后实体自身 ID 应被回填", cat.getCategoryId());
        assertEquals("insert 后回填 ID 应等于返回值", id, cat.getCategoryId());

        // 重新查出来验证
        Category fetched = dao.findById(id);
        assertNotNull("insert 后 findById 应能查到", fetched);
        assertEquals("测试分类", fetched.getName());
        assertEquals("用于单元测试", fetched.getDescription());
    }

    @Test
    public void testInsert_DuplicateName() {
        Category dup = new Category("教材", "重复名称");
        try {
            dao.insert(dup);
            fail("违反 UNIQUE 约束应抛 DaoException");
        } catch (DaoException expected) {
            // OK
        }
    }

    // ==================== 更新 ====================

    @Test
    public void testUpdate() {
        // 先插一个
        Category cat = new Category("更前分类", "更新前描述");
        Long id = dao.insert(cat);

        cat.setName("更新后分类");
        cat.setDescription("更新后描述");
        dao.update(cat);

        Category fetched = dao.findById(id);
        assertEquals("更新后分类", fetched.getName());
        assertEquals("更新后描述", fetched.getDescription());
    }

    @Test
    public void testUpdate_NoId() {
        Category cat = new Category("无ID分类", "test");
        try {
            dao.update(cat);
            fail("categoryId 为 null 应抛 DaoException");
        } catch (DaoException expected) {
            assertTrue(expected.getMessage().contains("categoryId"));
        }
    }

    // ==================== 删除 ====================

    @Test
    public void testDelete() {
        Category cat = new Category("待删除分类", "将被删除");
        Long id = dao.insert(cat);
        assertNotNull(id);

        dao.delete(id);
        assertNull("删除后 findById 应返回 null", dao.findById(id));
    }

    @Test
    public void testDelete_Null() {
        // 不应抛异常
        dao.delete(null);
    }
}
