package com.lowcarbon.strategy;

import com.lowcarbon.entity.Item;

import java.util.List;

/**
 * 价格区间筛选策略——按 [minPrice, maxPrice] 闭区间过滤物品。
 * <p>
 * 下界（minPrice < 0）和上界（maxPrice < 0）可以独立省略，负数表示不限制该端。
 *
 * @author lowcarbon-team
 * @since 2026-11-09
 */
public class PriceRangeFilterStrategy implements ItemFilterStrategy {

    /** 价格下限（含）；< 0 表示无下限。 */
    private final double minPrice;

    /** 价格上限（含）；< 0 表示无上限。 */
    private final double maxPrice;

    /**
     * 构造价格区间筛选策略。
     *
     * @param minPrice 价格下限（含）；传负数表示无下限
     * @param maxPrice 价格上限（含）；传负数表示无上限
     */
    public PriceRangeFilterStrategy(double minPrice, double maxPrice) {
        this.minPrice = minPrice;
        this.maxPrice = maxPrice;
    }

    @Override
    public List<Item> filter(List<Item> items) {
        boolean noLower = minPrice < 0;
        boolean noUpper = maxPrice < 0;
        if (noLower && noUpper) {
            return items; // 两端都不限，直通
        }
        return items.stream()
                .filter(item -> item != null && inRange(item.getPrice()))
                .toList();
    }

    private boolean inRange(double price) {
        if (minPrice >= 0 && price < minPrice) return false;
        if (maxPrice >= 0 && price > maxPrice) return false;
        return true;
    }
}
