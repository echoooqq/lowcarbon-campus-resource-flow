package com.lowcarbon.strategy;

import com.lowcarbon.entity.Item;

import java.util.List;

/**
 * 分类筛选策略——只保留指定分类下的物品。
 * <p>
 * 如果 categoryId 为 null，则不做筛选。
 *
 * @author lowcarbon-team
 * @since 2026-11-09
 */
public class CategoryFilterStrategy implements ItemFilterStrategy {

    private final Long categoryId;

    /**
     * 构造分类筛选策略。
     *
     * @param categoryId 目标分类 ID；null 表示不按分类筛
     */
    public CategoryFilterStrategy(Long categoryId) {
        this.categoryId = categoryId;
    }

    @Override
    public List<Item> filter(List<Item> items) {
        if (categoryId == null) {
            return items;
        }
        return items.stream()
                .filter(item -> item != null && categoryId.equals(item.getCategoryId()))
                .toList();
    }
}
