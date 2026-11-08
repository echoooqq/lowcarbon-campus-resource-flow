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
import com.lowcarbon.factory.DaoFactory;
import com.lowcarbon.observer.ItemStatusObserver;
import com.lowcarbon.observer.ItemStatusSubject;
import com.lowcarbon.strategy.CategoryFilterStrategy;
import com.lowcarbon.strategy.CompositeFilterStrategy;
import com.lowcarbon.strategy.ItemFilterStrategy;
import com.lowcarbon.strategy.KeywordFilterStrategy;
import com.lowcarbon.strategy.PriceRangeFilterStrategy;
import com.lowcarbon.util.Validator;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * 物品模块业务逻辑层。
 * <p>
 * 覆盖需求：I-01 发布、I-02 修改、I-03 下架、I-04 状态流转、
 *          S-01 分类筛选、S-02 关键词搜索、S-03 价格区间、A-02 管理员强制下架。
 * <p>
 * 依赖：{@link ItemDao}（主）、{@link UserDao}（校验发布者）、{@link CategoryDao}（校验分类）。
 *
 * @author lowcarbon-team
 * @since 2026-11-09
 */
public class ItemService implements ItemStatusSubject {

    private static final Logger LOGGER = Logger.getLogger(ItemService.class.getName());

    private final ItemDao itemDao;
    private final UserDao userDao;
    private final CategoryDao categoryDao;

    /**
     * 物品状态变更观察者列表（CopyOnWriteArrayList 保证并发安全 + 遍历安全）。
     * <p>
     * 应用：观察者模式——transitionStatus 成功后逐一通知观察者。
     */
    private final List<ItemStatusObserver> observers = new CopyOnWriteArrayList<>();

    /**
     * 使用默认 DAO 实现构造。
     */
    public ItemService() {
        this.itemDao = DaoFactory.getItemDao();
        this.userDao = DaoFactory.getUserDao();
        this.categoryDao = DaoFactory.getCategoryDao();
    }

    /**
     * 使用指定 DAO 构造（便于单元测试注入 Mock）。
     */
    public ItemService(ItemDao itemDao, UserDao userDao, CategoryDao categoryDao) {
        this.itemDao = itemDao;
        this.userDao = userDao;
        this.categoryDao = categoryDao;
    }

    // ==================== 发布（I-01） ====================

    /**
     * 发布闲置物品。
     *
     * @param publisherId  发布者用户 ID
     * @param name         物品名称（必填，最多 50 字符）
     * @param categoryId   分类 ID（必填）
     * @param description  描述（可选，最多 500 字符）
     * @param price        价格（元）
     * @param tradeType    交易类型（SELL / DONATE / BORROW）
     * @param tags         标签（可选，逗号分隔）
     * @return 发布成功后的物品对象（带生成的 itemId）
     */
    public Item publish(Long publisherId, String name, Long categoryId,
                        String description, double price, TradeType tradeType,
                        String tags) {
        // 1. 校验发布者
        if (publisherId == null) {
            throw new BusinessException("发布者 ID 不能为空");
        }
        User publisher;
        try {
            publisher = userDao.findById(publisherId);
        } catch (DaoException e) {
            throw new BusinessException("发布物品失败，请稍后再试", e);
        }
        if (publisher == null) {
            throw new BusinessException("发布者不存在");
        }

        // 2. 校验分类
        if (categoryId == null) {
            throw new BusinessException("请选择物品分类");
        }
        try {
            Category category = categoryDao.findById(categoryId);
            if (category == null) {
                throw new BusinessException("所选分类不存在");
            }
        } catch (DaoException e) {
            throw new BusinessException("分类校验失败", e);
        }

        // 3. 校验物品名称
        String err = Validator.validateItemName(name);
        if (err != null) {
            throw new BusinessException(err);
        }

        // 4. 校验描述
        err = Validator.validateItemDescription(description);
        if (err != null) {
            throw new BusinessException(err);
        }

        // 5. 校验价格
        err = Validator.validatePrice(price);
        if (err != null) {
            throw new BusinessException(err);
        }

        // 6. 默认值兜底
        if (tradeType == null) {
            tradeType = TradeType.SELL;
        }

        // 7. 构造物品对象并持久化
        Item item = new Item(publisherId, name.trim(), categoryId,
                description == null ? null : description.trim(),
                price, tradeType);
        item.setStatus(ItemStatus.ON_SALE);
        item.setPublishTime(System.currentTimeMillis());
        item.setTagsStr(Validator.isBlank(tags) ? null : tags.trim());

        try {
            Long id = itemDao.insert(item);
            LOGGER.log(Level.INFO, "物品发布成功: itemId={0}, publisherId={1}, name={2}",
                    new Object[]{id, publisherId, name});
            return item;
        } catch (DaoException e) {
            LOGGER.log(Level.SEVERE, "发布物品失败: name=" + name, e);
            throw new BusinessException("发布物品失败，请稍后再试", e);
        }
    }

    // ==================== 修改（I-02） ====================

    /**
     * 修改物品信息。
     * <p>
     * 只有物品的发布者本人可以修改；只有处于 ON_SALE / OFF_SHELF 状态的物品可以修改
     * （RESERVED / SOLD 视为流转中，禁止编辑以免数据不一致）。
     *
     * @param itemId      物品 ID
     * @param operatorId  操作者用户 ID（应为发布者）
     * @param newName     新名称（null 表示不修改）
     * @param newCategory 新分类 ID（null 表示不修改）
     * @param newDesc     新描述（null 表示不修改）
     * @param newPrice    新价格（-1 表示不修改）
     * @param newTags     新标签（null 表示不修改）
     */
    public void update(Long itemId, Long operatorId, String newName, Long newCategory,
                       String newDesc, double newPrice, String newTags) {
        if (itemId == null) {
            throw new BusinessException("物品 ID 不能为空");
        }
        if (operatorId == null) {
            throw new BusinessException("操作者 ID 不能为空");
        }

        // 1. 查物品
        Item item = getItemOrThrow(itemId);

        // 2. 权限：只能修改自己发布的
        if (!operatorId.equals(item.getPublisherId())) {
            throw new BusinessException("只能修改自己发布的物品");
        }

        // 3. 状态：流转中禁止编辑
        if (item.getStatus() == ItemStatus.RESERVED || item.getStatus() == ItemStatus.SOLD) {
            throw new BusinessException("流转中的物品不允许修改");
        }

        // 4. 逐项应用变更
        if (newName != null) {
            String err = Validator.validateItemName(newName);
            if (err != null) {
                throw new BusinessException("名称更新失败：" + err);
            }
            item.setName(newName.trim());
        }
        if (newCategory != null) {
            try {
                if (categoryDao.findById(newCategory) == null) {
                    throw new BusinessException("所选分类不存在");
                }
            } catch (DaoException e) {
                throw new BusinessException("分类校验失败", e);
            }
            item.setCategoryId(newCategory);
        }
        if (newDesc != null) {
            String err = Validator.validateItemDescription(newDesc);
            if (err != null) {
                throw new BusinessException("描述更新失败：" + err);
            }
            item.setDescription(newDesc.trim());
        }
        if (newPrice >= 0) {
            String err = Validator.validatePrice(newPrice);
            if (err != null) {
                throw new BusinessException("价格更新失败：" + err);
            }
            item.setPrice(newPrice);
        }
        if (newTags != null) {
            item.setTagsStr(Validator.isBlank(newTags) ? null : newTags.trim());
        }

        // 5. 持久化
        try {
            itemDao.update(item);
            LOGGER.log(Level.INFO, "物品修改成功: itemId={0}, operatorId={1}",
                    new Object[]{itemId, operatorId});
        } catch (DaoException e) {
            LOGGER.log(Level.SEVERE, "修改物品失败: itemId=" + itemId, e);
            throw new BusinessException("修改物品失败，请稍后再试", e);
        }
    }

    // ==================== 下架（I-03） ====================

    /**
     * 发布者主动下架物品。
     * <p>
     * 条件：物品属于该发布者，且状态为 ON_SALE（RESERVED/SOLD/OFF_SHELF 不允许再下架）。
     *
     * @param itemId     物品 ID
     * @param operatorId 操作者 ID（必须是发布者）
     */
    public void takeOffShelf(Long itemId, Long operatorId) {
        if (itemId == null) {
            throw new BusinessException("物品 ID 不能为空");
        }
        if (operatorId == null) {
            throw new BusinessException("操作者 ID 不能为空");
        }

        Item item = getItemOrThrow(itemId);
        if (!operatorId.equals(item.getPublisherId())) {
            throw new BusinessException("只能下架自己发布的物品");
        }
        if (item.getStatus() != ItemStatus.ON_SALE) {
            throw new BusinessException("当前状态不允许下架：" + statusLabel(item.getStatus()));
        }

        try {
            itemDao.updateStatus(itemId, ItemStatus.OFF_SHELF);
            LOGGER.log(Level.INFO, "发布者下架物品: itemId={0}, operatorId={1}",
                    new Object[]{itemId, operatorId});
        } catch (DaoException e) {
            throw new BusinessException("下架失败，请稍后再试", e);
        }
    }

    /**
     * 管理员强制下架违规物品（A-02）。
     * <p>
     * 管理员下架无视物品当前状态（RESERVED 的物品可能是欺诈，管理员有权强撤）。
     *
     * @param itemId      物品 ID
     * @param adminId     管理员 ID
     * @param reason      下架原因（用于日志）
     */
    public void adminTakeOffShelf(Long itemId, Long adminId, String reason) {
        if (itemId == null) {
            throw new BusinessException("物品 ID 不能为空");
        }
        if (adminId == null) {
            throw new BusinessException("管理员 ID 不能为空");
        }

        // 权限校验
        try {
            User admin = userDao.findById(adminId);
            if (admin == null || admin.getRole() != UserRole.ADMIN) {
                throw new BusinessException("仅管理员可执行强制下架");
            }
        } catch (DaoException e) {
            throw new BusinessException("权限校验失败", e);
        }

        // 目标物品存在
        Item item = getItemOrThrow(itemId);

        try {
            itemDao.updateStatus(itemId, ItemStatus.OFF_SHELF);
            LOGGER.log(Level.WARNING, "管理员强制下架物品: itemId={0}, name={1}, publisherId={2}, adminId={3}, reason={4}",
                    new Object[]{itemId, item.getName(), item.getPublisherId(), adminId, reason == null ? "未说明" : reason});
        } catch (DaoException e) {
            throw new BusinessException("强制下架失败，请稍后再试", e);
        }
    }

    // ==================== 状态流转（I-04） ====================

    /**
     * 物品状态流转（内部使用，供 TransactionService 调用）。
     * <p>
     * 合法流转：
     * <pre>
     *   ON_SALE   → RESERVED  （买家发起意向）
     *   ON_SALE   → OFF_SHELF （发布者主动下架 / 管理员强制下架）
     *   RESERVED  → SOLD      （交易完成）
     *   RESERVED  → ON_SALE   （买家取消意向，物品重新上架）
     * </pre>
     *
     * @param itemId    物品 ID
     * @param newStatus 目标状态
     * @throws BusinessException 非法流转或 DAO 异常时
     */
    public void transitionStatus(Long itemId, ItemStatus newStatus) {
        if (itemId == null || newStatus == null) {
            throw new BusinessException("参数不能为空");
        }
        Item item = getItemOrThrow(itemId);
        ItemStatus oldStatus = item.getStatus();

        if (!isValidTransition(oldStatus, newStatus)) {
            LOGGER.log(Level.WARNING, "非法状态流转: itemId={0}, {1} → {2}",
                    new Object[]{itemId, oldStatus, newStatus});
            throw new BusinessException("非法的状态流转："
                    + statusLabel(oldStatus) + " → " + statusLabel(newStatus));
        }

        try {
            itemDao.updateStatus(itemId, newStatus);
            LOGGER.log(Level.INFO, "物品状态变更: itemId={0}, {1} → {2}",
                    new Object[]{itemId, oldStatus, newStatus});

            // 观察者模式：状态变更成功后逐一通知观察者
            notifyObservers(itemId, oldStatus, newStatus);
        } catch (DaoException e) {
            throw new BusinessException("状态更新失败，请稍后再试", e);
        }
    }

    // ==================== 查询 ====================

    /**
     * 按 ID 查询。
     *
     * @return 物品对象；不存在返回 null
     */
    public Item findById(Long itemId) {
        if (itemId == null) {
            return null;
        }
        try {
            return itemDao.findById(itemId);
        } catch (DaoException e) {
            throw new BusinessException("查询物品失败", e);
        }
    }

    /**
     * 浏览大厅：只展示 ON_SALE 状态物品（S-04）。
     *
     * @return 在售物品列表，按发布时间倒序
     */
    public List<Item> listOnSale() {
        try {
            return itemDao.findByStatus(ItemStatus.ON_SALE);
        } catch (DaoException e) {
            throw new BusinessException("获取物品列表失败", e);
        }
    }

    /**
     * 按发布者查询（某人的全部物品，管理自己发布的物品）。
     */
    public List<Item> listByPublisher(Long publisherId) {
        if (publisherId == null) {
            return List.of();
        }
        try {
            return itemDao.findByPublisherId(publisherId);
        } catch (DaoException e) {
            throw new BusinessException("查询发布者物品失败", e);
        }
    }

    /**
     * 组合筛选（S-01 / S-02 / S-03 统一入口）。
     * <p>
     * 只返回 ON_SALE 状态物品（大厅浏览场景）。
     *
     * @param keyword    关键词（null/空 = 不筛）
     * @param categoryId 分类（null = 不筛）
     * @param minPrice   价格下限（负数 = 无）
     * @param maxPrice   价格上限（负数 = 无）
     * @return 在售物品列表（按发布时间倒序）
     */
    public List<Item> searchOnSale(String keyword, Long categoryId,
                                   double minPrice, double maxPrice) {
        try {
            // 策略模式：先从 DAO 拉全部在售物品，再通过组合策略链在内存中精筛
            List<Item> onSaleItems = itemDao.findByStatus(ItemStatus.ON_SALE);
            ItemFilterStrategy filter = new CompositeFilterStrategy(
                    new KeywordFilterStrategy(keyword),
                    new CategoryFilterStrategy(categoryId),
                    new PriceRangeFilterStrategy(minPrice, maxPrice)
            );
            return filter.filter(onSaleItems);
        } catch (DaoException e) {
            throw new BusinessException("物品筛选失败", e);
        }
    }

    /**
     * 按分类筛选（S-01）。
     */
    public List<Item> listByCategory(Long categoryId) {
        if (categoryId == null) {
            return List.of();
        }
        try {
            List<Item> onSaleItems = itemDao.findByStatus(ItemStatus.ON_SALE);
            return new CategoryFilterStrategy(categoryId).filter(onSaleItems);
        } catch (DaoException e) {
            throw new BusinessException("分类筛选失败", e);
        }
    }

    /**
     * 关键词搜索（S-02）。
     */
    public List<Item> searchByKeyword(String keyword) {
        if (Validator.isBlank(keyword)) {
            return List.of();
        }
        try {
            List<Item> onSaleItems = itemDao.findByStatus(ItemStatus.ON_SALE);
            return new KeywordFilterStrategy(keyword).filter(onSaleItems);
        } catch (DaoException e) {
            throw new BusinessException("关键词搜索失败", e);
        }
    }

    /**
     * 价格区间筛选（S-03）。
     */
    public List<Item> listByPriceRange(double minPrice, double maxPrice) {
        try {
            List<Item> onSaleItems = itemDao.findByStatus(ItemStatus.ON_SALE);
            return new PriceRangeFilterStrategy(minPrice, maxPrice).filter(onSaleItems);
        } catch (DaoException e) {
            throw new BusinessException("价格区间筛选失败", e);
        }
    }

    /**
     * 查询全部物品分类（供 GUI 下拉框使用，如浏览筛选、发布表单）。
     *
     * @return 分类列表
     */
    public List<Category> listAllCategories() {
        try {
            return categoryDao.findAll();
        } catch (DaoException e) {
            throw new BusinessException("获取分类列表失败", e);
        }
    }

    /**
     * 管理员视角：查询全部物品（含所有状态）。
     */
    public List<Item> listAll() {
        try {
            return itemDao.findAll();
        } catch (DaoException e) {
            throw new BusinessException("获取全部物品失败", e);
        }
    }

    /**
     * 统计物品总数。
     */
    public int countItems() {
        try {
            return itemDao.count();
        } catch (DaoException e) {
            throw new BusinessException("统计失败", e);
        }
    }

    // ==================== 内部辅助 ====================

    /**
     * 获取物品或抛出"不存在"异常。
     */
    private Item getItemOrThrow(Long itemId) {
        try {
            Item item = itemDao.findById(itemId);
            if (item == null) {
                throw new BusinessException("物品不存在");
            }
            return item;
        } catch (DaoException e) {
            throw new BusinessException("查询物品失败", e);
        }
    }

    /**
     * 判断状态流转是否合法。
     */
    private boolean isValidTransition(ItemStatus from, ItemStatus to) {
        if (from == to) {
            return true; // 允许自环（防御性）
        }
        return switch (from) {
            case ON_SALE  -> to == ItemStatus.RESERVED || to == ItemStatus.OFF_SHELF;
            case RESERVED -> to == ItemStatus.SOLD || to == ItemStatus.ON_SALE || to == ItemStatus.OFF_SHELF;
            case SOLD     -> false; // 终态
            case OFF_SHELF -> false; // 终态（后续如需"重新上架"再开放）
        };
    }

    /**
     * 状态标签（用于日志和错误提示）。
     */
    private static String statusLabel(ItemStatus s) {
        return s == null ? "未知" : switch (s) {
            case ON_SALE  -> "在售";
            case RESERVED -> "已预订";
            case SOLD     -> "已成交";
            case OFF_SHELF -> "已下架";
        };
    }

    // ==================== 观察者模式（Observer Pattern） ====================

    @Override
    public void addObserver(ItemStatusObserver observer) {
        if (observer != null && !observers.contains(observer)) {
            observers.add(observer);
        }
    }

    @Override
    public void removeObserver(ItemStatusObserver observer) {
        if (observer != null) {
            observers.remove(observer);
        }
    }

    /**
     * 通知所有观察者。
     * <p>
     * 观察者如果抛出异常，只记录 WARNING 日志，不中断其余观察者的通知，
     * 也不影响主业务逻辑的成功返回——Subject 对 Observer 是松散耦合的。
     *
     * @param itemId    物品 ID
     * @param oldStatus 变更前状态
     * @param newStatus 变更后状态
     */
    private void notifyObservers(Long itemId, ItemStatus oldStatus, ItemStatus newStatus) {
        for (ItemStatusObserver observer : observers) {
            try {
                observer.onStatusChanged(itemId, oldStatus, newStatus);
            } catch (Exception ex) {
                LOGGER.log(Level.WARNING,
                        "观察者执行异常，跳过当前观察者（不影响主流程）。observer="
                                + observer.getClass().getSimpleName()
                                + ", itemId=" + itemId, ex);
            }
        }
    }
}
