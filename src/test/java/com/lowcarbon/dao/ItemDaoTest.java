package com.lowcarbon.dao;

import com.lowcarbon.config.DatabaseManager;
import com.lowcarbon.dao.impl.CategoryDaoImpl;
import com.lowcarbon.dao.impl.ItemDaoImpl;
import com.lowcarbon.dao.impl.UserDaoImpl;
import com.lowcarbon.entity.*;
import com.lowcarbon.exception.DaoException;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.*;

/**
 * ItemDao 单元测试。
 *
 * @author lowcarbon-team
 * @since 2026-11-09
 */
public class ItemDaoTest {

    static {
        System.setProperty("lowcarbon.db.path", "lowcarbon_unit_test.db");
    }

    private static ItemDao dao;
    private static UserDao userDao;
    private static Long testUserId;
    private static Long testCategoryId;

    @BeforeClass
    public static void setUpClass() {
        dao = new ItemDaoImpl();
        userDao = new UserDaoImpl();
        prepareFixtures();
    }

    @Before
    public void setUp() {
        prepareFixtures();
    }

    /** 重置数据库 + 准备 ItemDaoTest 所需的测试用户和分类。 */
    private static void prepareFixtures() {
        DatabaseManager.resetForTest();

        // 准备测试用户
        User u = new User("item_publisher", "h_pub", "s_pub");
        u.setRole(UserRole.USER);
        u.setCreateTime(System.currentTimeMillis());
        testUserId = userDao.insert(u);

        // 拿预置分类 ID（教材）
        Category cat = new CategoryDaoImpl().findByName("教材");
        assertNotNull("预置分类应存在", cat);
        testCategoryId = cat.getCategoryId();
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
    public void testFindByPublisherId_Empty() {
        assertTrue(dao.findByPublisherId(testUserId).isEmpty());
        assertTrue(dao.findByPublisherId(null).isEmpty());
    }

    @Test
    public void testFindByKeyword_Empty() {
        assertTrue(dao.findByKeyword(null).isEmpty());
        assertTrue(dao.findByKeyword("").isEmpty());
        assertTrue(dao.findByKeyword("   ").isEmpty());
    }

    @Test
    public void testFindByStatus_Empty() {
        assertTrue(dao.findByStatus(null).isEmpty());
        assertTrue(dao.findByStatus(ItemStatus.ON_SALE).isEmpty());
    }

    // ==================== 新增 ====================

    @Test
    public void testInsert() {
        Item item = new Item(testUserId, "Java 核心技术 卷I", testCategoryId,
                "九成新，有少量笔记", 25.0, TradeType.SELL);
        item.setPublishTime(System.currentTimeMillis());

        Long id = dao.insert(item);
        assertNotNull(id);
        assertEquals(id, item.getItemId());

        assertEquals(1, dao.count());
    }

    @Test
    public void testInsert_Defaults() {
        Item item = new Item(testUserId, "二手耳机", testCategoryId,
                null, 0.0, null); // tradeType 传 null 验证默认值
        item.setPublishTime(System.currentTimeMillis());

        Long id = dao.insert(item);
        Item fetched = dao.findById(id);
        assertNotNull(fetched);
        // 枚举默认值
        assertEquals(TradeType.SELL, fetched.getTradeType());
        assertEquals(ItemStatus.ON_SALE, fetched.getStatus());
    }

    @Test
    public void testInsert_WithTags() {
        Item item = new Item(testUserId, "带标签物品", testCategoryId,
                "测试 tagsStr", 10.0, TradeType.DONATE);
        item.setPublishTime(System.currentTimeMillis());
        item.setTagsStr("新,包邮,自取");

        Long id = dao.insert(item);
        Item fetched = dao.findById(id);
        assertEquals("新,包邮,自取", fetched.getTagsStr());
        assertNotNull(fetched.getTagList());
        assertEquals(3, fetched.getTagList().size());
        assertTrue(fetched.getTagList().contains("新"));
    }

    // ==================== 查询 ====================

    @Test
    public void testFindAll_WithData() {
        Item item = new Item(testUserId, "查询测试物品", testCategoryId,
                "desc", 10.0, TradeType.SELL);
        item.setPublishTime(System.currentTimeMillis());
        dao.insert(item);

        List<Item> all = dao.findAll();
        assertFalse(all.isEmpty());
    }

    @Test
    public void testFindByPublisherId() {
        Item item = new Item(testUserId, "发布者物品", testCategoryId,
                "desc", 10.0, TradeType.SELL);
        item.setPublishTime(System.currentTimeMillis());
        Long id = dao.insert(item);

        List<Item> items = dao.findByPublisherId(testUserId);
        assertTrue(items.stream().anyMatch(i -> id.equals(i.getItemId())));
    }

    @Test
    public void testFindByCategoryId() {
        Item item = new Item(testUserId, "分类物品", testCategoryId,
                "desc", 10.0, TradeType.SELL);
        item.setPublishTime(System.currentTimeMillis());
        Long id = dao.insert(item);

        List<Item> items = dao.findByCategoryId(testCategoryId);
        assertTrue(items.stream().anyMatch(i -> id.equals(i.getItemId())));
    }

    @Test
    public void testFindByStatus() {
        Item item = new Item(testUserId, "状态物品", testCategoryId,
                "desc", 10.0, TradeType.SELL);
        item.setPublishTime(System.currentTimeMillis());
        Long id = dao.insert(item);

        List<Item> items = dao.findByStatus(ItemStatus.ON_SALE);
        assertTrue(items.stream().anyMatch(i -> id.equals(i.getItemId())));
    }

    @Test
    public void testFindByKeyword_Name() {
        Item item = new Item(testUserId, "Python 编程指南", testCategoryId,
                "desc", 30.0, TradeType.SELL);
        item.setPublishTime(System.currentTimeMillis());
        Long id = dao.insert(item);

        List<Item> hits = dao.findByKeyword("Python");
        assertTrue(hits.stream().anyMatch(i -> id.equals(i.getItemId())));

        List<Item> noHits = dao.findByKeyword("不存在的关键字_xxx");
        assertFalse(noHits.stream().anyMatch(i -> id.equals(i.getItemId())));
    }

    @Test
    public void testFindByKeyword_Description() {
        Item item = new Item(testUserId, "描述搜索测试", testCategoryId,
                "这是一个含有笔记本电脑关键词的描述", 5.0, TradeType.SELL);
        item.setPublishTime(System.currentTimeMillis());
        Long id = dao.insert(item);

        List<Item> hits = dao.findByKeyword("笔记本电脑");
        assertTrue(hits.stream().anyMatch(i -> id.equals(i.getItemId())));
    }

    // ==================== 更新 ====================

    @Test
    public void testUpdate() {
        Item item = new Item(testUserId, "更新前", testCategoryId,
                "旧描述", 10.0, TradeType.SELL);
        item.setPublishTime(System.currentTimeMillis());
        Long id = dao.insert(item);

        item.setName("更新后");
        item.setDescription("新描述");
        item.setPrice(99.0);
        item.setTradeType(TradeType.DONATE);
        dao.update(item);

        Item fetched = dao.findById(id);
        assertEquals("更新后", fetched.getName());
        assertEquals("新描述", fetched.getDescription());
        assertEquals(99.0, fetched.getPrice(), 0.001);
        assertEquals(TradeType.DONATE, fetched.getTradeType());
    }

    @Test
    public void testUpdate_NoId() {
        Item item = new Item(testUserId, "无ID", testCategoryId,
                null, 0.0, TradeType.SELL);
        try {
            dao.update(item);
            fail("itemId 为 null 应抛 DaoException");
        } catch (DaoException expected) {
            assertTrue(expected.getMessage().contains("itemId"));
        }
    }

    // ==================== updateStatus ====================

    @Test
    public void testUpdateStatus() {
        Item item = new Item(testUserId, "状态变更测试", testCategoryId,
                null, 0.0, TradeType.SELL);
        item.setPublishTime(System.currentTimeMillis());
        Long id = dao.insert(item);

        assertEquals(ItemStatus.ON_SALE, dao.findById(id).getStatus());

        dao.updateStatus(id, ItemStatus.RESERVED);
        assertEquals(ItemStatus.RESERVED, dao.findById(id).getStatus());

        dao.updateStatus(id, ItemStatus.SOLD);
        assertEquals(ItemStatus.SOLD, dao.findById(id).getStatus());
    }

    @Test
    public void testUpdateStatus_Null() {
        // 不应抛异常
        dao.updateStatus(null, ItemStatus.ON_SALE);
        dao.updateStatus(1L, null);
    }

    // ==================== 删除 ====================

    @Test
    public void testDelete() {
        Item item = new Item(testUserId, "待删除物品", testCategoryId,
                null, 0.0, TradeType.SELL);
        item.setPublishTime(System.currentTimeMillis());
        Long id = dao.insert(item);
        assertNotNull(dao.findById(id));

        dao.delete(id);
        assertNull(dao.findById(id));
    }

    @Test
    public void testDelete_Null() {
        dao.delete(null);
    }
}
