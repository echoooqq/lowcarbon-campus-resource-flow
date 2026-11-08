package com.lowcarbon.dao;

import com.lowcarbon.entity.Item;
import com.lowcarbon.entity.ItemStatus;

import java.util.List;

/**
 * 闲置物品数据访问接口，定义对 {@code item} 表的 CRUD + 查询操作。
 *
 * @author lowcarbon-team
 * @since 2026-11-09
 */
public interface ItemDao {

    /**
     * 按物品 ID 查询。
     *
     * @param itemId 物品 ID
     * @return 物品对象，不存在返回 null
     */
    Item findById(Long itemId);

    /**
     * 查询全部物品（按发布时间倒序）。
     *
     * @return 物品列表
     */
    List<Item> findAll();

    /**
     * 按发布者 ID 查询（某人发布的所有物品）。
     *
     * @param publisherId 发布者用户 ID
     * @return 物品列表
     */
    List<Item> findByPublisherId(Long publisherId);

    /**
     * 按分类 ID 查询。
     *
     * @param categoryId 分类 ID
     * @return 物品列表
     */
    List<Item> findByCategoryId(Long categoryId);

    /**
     * 按状态查询。
     *
     * @param status 物品状态
     * @return 物品列表
     */
    List<Item> findByStatus(ItemStatus status);

    /**
     * 按关键词模糊搜索（匹配名称或描述）。
     *
     * @param keyword 搜索关键词
     * @return 匹配的物品列表
     */
    List<Item> findByKeyword(String keyword);

    /**
     * 新增物品。
     *
     * @param item 待新增物品（itemId 由数据库自增）
     * @return 数据库生成的自增 ID
     */
    Long insert(Item item);

    /**
     * 更新物品信息（描述、价格、标签等）。
     *
     * @param item 带更新内容的物品对象（必须已带 itemId）
     */
    void update(Item item);

    /**
     * 按物品 ID 删除记录。
     *
     * @param itemId 物品 ID
     */
    void delete(Long itemId);

    /**
     * 按价格区间查询（含端点，[min, max]，闭区间）。
     *
     * @param minPrice 最低价格（含）
     * @param maxPrice 最高价格（含）
     * @return 物品列表
     */
    List<Item> findByPriceRange(double minPrice, double maxPrice);

    /**
     * 按关键词 + 分类 + 价格区间组合筛选。
     * <p>
     * 任一参数为 null 或非正值时表示该维度不做筛选（除 keyword 外全部可选）。
     *
     * @param keyword    关键词（null 或空表示不按关键词筛）
     * @param categoryId 分类 ID（null 表示不按分类筛）
     * @param minPrice   价格下限（负数表示无下限）
     * @param maxPrice   价格上限（负数表示无上限）
     * @param status     状态（null 表示不限）
     * @return 筛选后的物品列表
     */
    List<Item> search(String keyword, Long categoryId, double minPrice, double maxPrice, ItemStatus status);

    /**
     * 单独更新物品状态（流转发生时用）。
     *
     * @param itemId 物品 ID
     * @param status 新状态
     */
    void updateStatus(Long itemId, ItemStatus status);

    /**
     * 统计物品总数。
     *
     * @return 物品数
     */
    int count();
}
