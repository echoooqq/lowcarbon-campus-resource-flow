package com.lowcarbon.service;

import com.lowcarbon.dao.CategoryDao;
import com.lowcarbon.dao.ItemDao;
import com.lowcarbon.dao.UserDao;
import com.lowcarbon.entity.Category;
import com.lowcarbon.entity.Item;
import com.lowcarbon.entity.ItemStatus;
import com.lowcarbon.entity.TradeType;
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
 * ItemService 单元测试。
 * <p>
 * 使用 Fake DAO 隔离数据库依赖，聚焦业务规则校验。
 *
 * @author lowcarbon-team
 * @since 2026-11-09
 */
public class ItemServiceTest {

    private ItemService service;
    private FakeItemDao fakeItemDao;
    private FakeUserDao fakeUserDao;
    private FakeCategoryDao fakeCategoryDao;

    // 预置数据
    private Long normalUserId;
    private Long adminUserId;
    private Long categoryId;

    @Before
    public void setUp() {
        fakeItemDao = new FakeItemDao();
        fakeUserDao = new FakeUserDao();
        fakeCategoryDao = new FakeCategoryDao();

        service = new ItemService(fakeItemDao, fakeUserDao, fakeCategoryDao);

        // 预置普通用户
        User u = new User("alice", "h", "s");
        u.setRole(UserRole.USER);
        u.setCreateTime(System.currentTimeMillis());
        fakeUserDao.insert(u);
        normalUserId = u.getUserId();

        // 预置管理员
        User admin = new User("admin_user", "h2", "s2");
        admin.setRole(UserRole.ADMIN);
        admin.setCreateTime(System.currentTimeMillis());
        fakeUserDao.insert(admin);
        adminUserId = admin.getUserId();

        // 预置分类
        Category cat = new Category("教材", "课本");
        fakeCategoryDao.insert(cat);
        categoryId = cat.getCategoryId();
    }

    // ==================== 发布（I-01） ====================

    @Test
    public void testPublish_Success() {
        Item item = service.publish(normalUserId, "高等数学", categoryId,
                "九成新", 30.0, TradeType.SELL, "包邮,自取");

        assertNotNull(item.getItemId());
        assertEquals("高等数学", item.getName());
        assertEquals(normalUserId, item.getPublisherId());
        assertEquals(ItemStatus.ON_SALE, item.getStatus());
        assertEquals(30.0, item.getPrice(), 0.001);
        assertEquals("包邮,自取", item.getTagsStr());
        assertTrue(item.getPublishTime() > 0);
    }

    @Test
    public void testPublish_PublisherNotExist() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.publish(99999L, "n", categoryId, null, 0, TradeType.SELL, null));
        assertTrue(ex.getMessage().contains("发布者不存在"));
    }

    @Test
    public void testPublish_CategoryNotExist() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.publish(normalUserId, "n", 99999L, null, 0, TradeType.SELL, null));
        assertTrue(ex.getMessage().contains("分类不存在"));
    }

    @Test
    public void testPublish_BlankName() {
        assertThrows(BusinessException.class,
                () -> service.publish(normalUserId, "", categoryId, null, 0, TradeType.SELL, null));
        assertThrows(BusinessException.class,
                () -> service.publish(normalUserId, "   ", categoryId, null, 0, TradeType.SELL, null));
    }

    @Test
    public void testPublish_TooLongName() {
        String longName = "a".repeat(51);
        assertThrows(BusinessException.class,
                () -> service.publish(normalUserId, longName, categoryId, null, 0, TradeType.SELL, null));
    }

    @Test
    public void testPublish_NegativePrice() {
        assertThrows(BusinessException.class,
                () -> service.publish(normalUserId, "n", categoryId, null, -1.0, TradeType.SELL, null));
    }

    @Test
    public void testPublish_NullTradeType_DefaultsToSell() {
        Item item = service.publish(normalUserId, "无类型物品", categoryId, null, 0, null, null);
        assertEquals(TradeType.SELL, item.getTradeType());
    }

    @Test
    public void testPublish_NullPublisherId() {
        assertThrows(BusinessException.class,
                () -> service.publish(null, "n", categoryId, null, 0, TradeType.SELL, null));
    }

    // ==================== 修改（I-02） ====================

    @Test
    public void testUpdate_Success() {
        Item item = publishItem("待修改物品");
        Long itemId = item.getItemId();

        service.update(itemId, normalUserId, "已修改物品", null, null, 49.9, null);

        Item updated = fakeItemDao.findById(itemId);
        assertEquals("已修改物品", updated.getName());
        assertEquals(49.9, updated.getPrice(), 0.001);
    }

    @Test
    public void testUpdate_OnlyName() {
        Item item = publishItem("原名称");
        Long itemId = item.getItemId();

        service.update(itemId, normalUserId, "新名称", null, null, -1, null);

        Item updated = fakeItemDao.findById(itemId);
        assertEquals("新名称", updated.getName());
        assertEquals(item.getPrice(), updated.getPrice(), 0.001); // 价格未变
    }

    @Test
    public void testUpdate_NotOwner() {
        // 构造一个不同 publisher 的物品
        Item other = new Item(9999L, "他人的物品", categoryId, null, 10, TradeType.SELL);
        other.setPublishTime(System.currentTimeMillis());
        fakeItemDao.insert(other);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.update(other.getItemId(), normalUserId, "恶意修改", null, null, -1, null));
        assertTrue(ex.getMessage().contains("只能修改自己发布的"));
    }

    @Test
    public void testUpdate_ReservedItem() {
        Item item = publishItem("流转中");
        Long itemId = item.getItemId();
        fakeItemDao.updateStatus(itemId, ItemStatus.RESERVED);

        assertThrows(BusinessException.class,
                () -> service.update(itemId, normalUserId, "新名称", null, null, -1, null));
    }

    @Test
    public void testUpdate_SoldItem() {
        Item item = publishItem("已成交");
        Long itemId = item.getItemId();
        fakeItemDao.updateStatus(itemId, ItemStatus.SOLD);

        assertThrows(BusinessException.class,
                () -> service.update(itemId, normalUserId, "新名称", null, null, -1, null));
    }

    @Test
    public void testUpdate_ItemNotExist() {
        assertThrows(BusinessException.class,
                () -> service.update(99999L, normalUserId, "n", null, null, -1, null));
    }

    // ==================== 下架（I-03） ====================

    @Test
    public void testTakeOffShelf_Success() {
        Item item = publishItem("在售物品");
        Long itemId = item.getItemId();

        service.takeOffShelf(itemId, normalUserId);

        Item updated = fakeItemDao.findById(itemId);
        assertEquals(ItemStatus.OFF_SHELF, updated.getStatus());
    }

    @Test
    public void testTakeOffShelf_NotOwner() {
        Item other = new Item(9999L, "他人的物品", categoryId, null, 10, TradeType.SELL);
        other.setPublishTime(System.currentTimeMillis());
        fakeItemDao.insert(other);

        assertThrows(BusinessException.class,
                () -> service.takeOffShelf(other.getItemId(), normalUserId));
    }

    @Test
    public void testTakeOffShelf_AlreadyReserved() {
        Item item = publishItem("已被预定");
        Long itemId = item.getItemId();
        fakeItemDao.updateStatus(itemId, ItemStatus.RESERVED);

        assertThrows(BusinessException.class,
                () -> service.takeOffShelf(itemId, normalUserId));
    }

    @Test
    public void testTakeOffShelf_AlreadyOffShelf() {
        Item item = publishItem("已下架");
        Long itemId = item.getItemId();
        fakeItemDao.updateStatus(itemId, ItemStatus.OFF_SHELF);

        assertThrows(BusinessException.class,
                () -> service.takeOffShelf(itemId, normalUserId));
    }

    // ==================== 管理员强制下架（A-02） ====================

    @Test
    public void testAdminTakeOffShelf_Success() {
        Item item = publishItem("违规物品");
        Long itemId = item.getItemId();

        service.adminTakeOffShelf(itemId, adminUserId, "内容违规");

        assertEquals(ItemStatus.OFF_SHELF, fakeItemDao.findById(itemId).getStatus());
    }

    @Test
    public void testAdminTakeOffShelf_CanForceOffReserved() {
        Item item = publishItem("欺诈物品");
        Long itemId = item.getItemId();
        fakeItemDao.updateStatus(itemId, ItemStatus.RESERVED);

        // 管理员可以强制下架 RESERVED 物品
        service.adminTakeOffShelf(itemId, adminUserId, "涉嫌欺诈");
        assertEquals(ItemStatus.OFF_SHELF, fakeItemDao.findById(itemId).getStatus());
    }

    @Test
    public void testAdminTakeOffShelf_NotAdmin() {
        Item item = publishItem("物品");

        assertThrows(BusinessException.class,
                () -> service.adminTakeOffShelf(item.getItemId(), normalUserId, null));
    }

    // ==================== 状态流转（I-04） ====================

    @Test
    public void testTransition_OnSaleToReserved() {
        Item item = publishItem("正常流转");
        Long itemId = item.getItemId();

        service.transitionStatus(itemId, ItemStatus.RESERVED);
        assertEquals(ItemStatus.RESERVED, fakeItemDao.findById(itemId).getStatus());
    }

    @Test
    public void testTransition_ReservedToSold() {
        Item item = publishItem();
        Long itemId = item.getItemId();
        fakeItemDao.updateStatus(itemId, ItemStatus.RESERVED);

        service.transitionStatus(itemId, ItemStatus.SOLD);
        assertEquals(ItemStatus.SOLD, fakeItemDao.findById(itemId).getStatus());
    }

    @Test
    public void testTransition_ReservedBackToOnSale() {
        Item item = publishItem();
        Long itemId = item.getItemId();
        fakeItemDao.updateStatus(itemId, ItemStatus.RESERVED);

        service.transitionStatus(itemId, ItemStatus.ON_SALE);
        assertEquals(ItemStatus.ON_SALE, fakeItemDao.findById(itemId).getStatus());
    }

    @Test
    public void testTransition_SoldIsTerminal() {
        Item item = publishItem();
        Long itemId = item.getItemId();
        fakeItemDao.updateStatus(itemId, ItemStatus.SOLD);

        assertThrows(BusinessException.class,
                () -> service.transitionStatus(itemId, ItemStatus.ON_SALE));
    }

    @Test
    public void testTransition_OffShelfIsTerminal() {
        Item item = publishItem();
        Long itemId = item.getItemId();
        fakeItemDao.updateStatus(itemId, ItemStatus.OFF_SHELF);

        assertThrows(BusinessException.class,
                () -> service.transitionStatus(itemId, ItemStatus.ON_SALE));
    }

    @Test
    public void testTransition_IllegalOnSaleToSold() {
        Item item = publishItem();
        Long itemId = item.getItemId();

        assertThrows(BusinessException.class,
                () -> service.transitionStatus(itemId, ItemStatus.SOLD));
    }

    // ==================== 查询 ====================

    @Test
    public void testListOnSale() {
        publishItem("a");
        publishItem("b");
        Item c = publishItem("c");
        fakeItemDao.updateStatus(c.getItemId(), ItemStatus.OFF_SHELF);

        List<Item> list = service.listOnSale();
        assertEquals(2, list.size());
    }

    @Test
    public void testListByPublisher() {
        publishItem("发布1");
        publishItem("发布2");

        List<Item> list = service.listByPublisher(normalUserId);
        assertEquals(2, list.size());
    }

    @Test
    public void testListByPublisher_Null() {
        assertTrue(service.listByPublisher(null).isEmpty());
    }

    @Test
    public void testSearchOnSale_ByKeyword() {
        publishItem("Java 编程");
        publishItem("Python 入门");
        publishItem("耳机");

        List<Item> list = service.searchOnSale("Java", null, -1, -1);
        assertEquals(1, list.size());
        assertEquals("Java 编程", list.get(0).getName());
    }

    @Test
    public void testSearchOnSale_ByCategory() {
        publishItem("教材");

        List<Item> list = service.searchOnSale(null, categoryId, -1, -1);
        assertEquals(1, list.size());
    }

    @Test
    public void testSearchOnSale_ByPriceRange() {
        publishItem("便宜", 5);
        publishItem("中等", 50);
        publishItem("贵", 200);

        List<Item> list = service.searchOnSale(null, null, 10, 100);
        assertEquals(1, list.size());
        assertEquals("中等", list.get(0).getName());
    }

    @Test
    public void testSearchOnSale_Combined() {
        publishItem("高等数学 上", 30);
        publishItem("高等数学 下", 35);
        publishItem("耳机", 50); // 不同分类
        publishItem("高等数学 贵", 500); // 超出价格

        List<Item> list = service.searchOnSale("数学", categoryId, 0, 100);
        assertEquals(2, list.size());
    }

    @Test
    public void testSearchByKeyword() {
        publishItem("Python 核心技术");
        publishItem("Java 思想");

        List<Item> list = service.searchByKeyword("Python");
        assertEquals(1, list.size());
    }

    @Test
    public void testSearchByKeyword_Blank() {
        assertTrue(service.searchByKeyword("").isEmpty());
        assertTrue(service.searchByKeyword(null).isEmpty());
    }

    @Test
    public void testFindById_NotExist() {
        assertNull(service.findById(null));
        assertNull(service.findById(99999L));
    }

    @Test
    public void testCountItems() {
        assertEquals(0, service.countItems());
        publishItem("x");
        assertEquals(1, service.countItems());
    }

    // ==================== 辅助 ====================

    private Item publishItem(String name, double price) {
        return service.publish(normalUserId, name, categoryId, "desc",
                price, TradeType.SELL, null);
    }

    private Item publishItem(String name) {
        return publishItem(name, 10.0);
    }

    private Item publishItem() {
        return publishItem("测试物品", 10.0);
    }

    // ==================== Fake DAO ====================

    private static class FakeItemDao implements ItemDao {
        private final Map<Long, Item> store = new HashMap<>();
        private long nextId = 1;

        @Override
        public Item findById(Long itemId) {
            return itemId == null ? null : store.get(itemId);
        }

        @Override
        public List<Item> findAll() {
            return new ArrayList<>(store.values());
        }

        @Override
        public List<Item> findByPublisherId(Long publisherId) {
            List<Item> r = new ArrayList<>();
            for (Item i : store.values()) {
                if (publisherId.equals(i.getPublisherId())) {
                    r.add(i);
                }
            }
            return r;
        }

        @Override
        public List<Item> findByCategoryId(Long categoryId) {
            List<Item> r = new ArrayList<>();
            for (Item i : store.values()) {
                if (categoryId.equals(i.getCategoryId())) {
                    r.add(i);
                }
            }
            return r;
        }

        @Override
        public List<Item> findByStatus(ItemStatus status) {
            List<Item> r = new ArrayList<>();
            for (Item i : store.values()) {
                if (status.equals(i.getStatus())) {
                    r.add(i);
                }
            }
            return r;
        }

        @Override
        public List<Item> findByKeyword(String keyword) {
            List<Item> r = new ArrayList<>();
            String kw = keyword == null ? "" : keyword.toLowerCase();
            for (Item i : store.values()) {
                String n = i.getName() == null ? "" : i.getName().toLowerCase();
                String d = i.getDescription() == null ? "" : i.getDescription().toLowerCase();
                if (n.contains(kw) || d.contains(kw)) {
                    r.add(i);
                }
            }
            return r;
        }

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
        public void delete(Long itemId) {
            store.remove(itemId);
        }

        @Override
        public void updateStatus(Long itemId, ItemStatus status) {
            Item item = store.get(itemId);
            if (item != null) {
                item.setStatus(status);
            }
        }

        @Override
        public List<Item> findByPriceRange(double minPrice, double maxPrice) {
            List<Item> r = new ArrayList<>();
            for (Item i : store.values()) {
                if (i.getPrice() >= minPrice && i.getPrice() <= maxPrice) r.add(i);
            }
            return r;
        }

        @Override
        public List<Item> search(String keyword, Long categoryId, double minPrice,
                                  double maxPrice, ItemStatus status) {
            List<Item> result = new ArrayList<>();
            String kw = keyword == null ? null : keyword.toLowerCase();
            for (Item i : store.values()) {
                if (kw != null && !kw.isBlank()) {
                    String n = i.getName() == null ? "" : i.getName().toLowerCase();
                    String d = i.getDescription() == null ? "" : i.getDescription().toLowerCase();
                    if (!n.contains(kw) && !d.contains(kw)) continue;
                }
                if (categoryId != null && !categoryId.equals(i.getCategoryId())) continue;
                if (minPrice >= 0 && i.getPrice() < minPrice) continue;
                if (maxPrice >= 0 && i.getPrice() > maxPrice) continue;
                if (status != null && status != i.getStatus()) continue;
                result.add(i);
            }
            return result;
        }

        @Override
        public int count() {
            return store.size();
        }
    }

    private static class FakeUserDao implements UserDao {
        private final Map<Long, User> store = new HashMap<>();
        private long nextId = 1;

        @Override
        public User findById(Long userId) {
            return userId == null ? null : store.get(userId);
        }

        @Override
        public User findByUsername(String username) {
            if (username == null) return null;
            for (User u : store.values()) if (username.equals(u.getUsername())) return u;
            return null;
        }

        @Override
        public List<User> findAll() {
            return new ArrayList<>(store.values());
        }

        @Override
        public Long insert(User user) {
            Long id = nextId++;
            user.setUserId(id);
            store.put(id, user);
            return id;
        }

        @Override
        public void update(User user) {
            if (user.getUserId() == null) throw new DaoException("userId is null");
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

    private static class FakeCategoryDao implements CategoryDao {
        private final Map<Long, Category> store = new HashMap<>();
        private long nextId = 1;

        @Override
        public Category findById(Long categoryId) {
            return categoryId == null ? null : store.get(categoryId);
        }

        @Override
        public Category findByName(String name) {
            if (name == null) return null;
            for (Category c : store.values()) if (name.equals(c.getName())) return c;
            return null;
        }

        @Override
        public List<Category> findAll() {
            return new ArrayList<>(store.values());
        }

        @Override
        public Long insert(Category category) {
            Long id = nextId++;
            category.setCategoryId(id);
            store.put(id, category);
            return id;
        }

        @Override
        public void update(Category category) {
            if (category.getCategoryId() == null) throw new DaoException("categoryId is null");
            store.put(category.getCategoryId(), category);
        }

        @Override
        public void delete(Long categoryId) {
            store.remove(categoryId);
        }

        @Override
        public int count() {
            return store.size();
        }
    }
}
