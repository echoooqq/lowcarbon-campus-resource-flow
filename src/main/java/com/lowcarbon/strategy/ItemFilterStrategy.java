package com.lowcarbon.strategy;

import com.lowcarbon.entity.Item;

import java.util.List;

/**
 * 物品筛选策略接口——策略模式（Strategy Pattern）的核心抽象。
 * <p>
 * 应用：策略模式。
 * 目的：将不同维度的物品筛选逻辑（关键词、分类、价格区间、状态等）封装为独立策略，
 * 允许运行时自由组合、替换、扩展筛选维度。新增筛选条件时只需新增一个实现类，
 * 无需修改现有代码——符合开闭原则。
 * <p>
 * 典型用法：
 * <pre>{@code
 * List<Item> all = itemDao.findByStatus(ItemStatus.ON_SALE);
 * ItemFilterStrategy chain = new CompositeFilterStrategy(
 *     new KeywordFilterStrategy(kw),
 *     new CategoryFilterStrategy(catId),
 *     new PriceRangeFilterStrategy(min, max)
 * );
 * List<Item> result = chain.filter(all);
 * }</pre>
 *
 * @author lowcarbon-team
 * @since 2026-11-09
 */
@FunctionalInterface
public interface ItemFilterStrategy {

    /**
     * 对物品列表进行筛选。
     *
     * @param items 原始物品列表（非 null）
     * @return 筛选后的列表；实现类可以返回原列表的子集或完全不同的集合，
     *         但不应修改输入列表本身
     */
    List<Item> filter(List<Item> items);
}
