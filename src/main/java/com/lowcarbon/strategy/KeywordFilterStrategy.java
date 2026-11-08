package com.lowcarbon.strategy;

import com.lowcarbon.entity.Item;

import java.util.List;
import java.util.Locale;

/**
 * 关键词筛选策略——匹配物品名称或描述中包含指定关键词（大小写不敏感）。
 * <p>
 * 如果关键词为 null 或空白，则不做任何筛选，直接返回输入列表。
 *
 * @author lowcarbon-team
 * @since 2026-11-09
 */
public class KeywordFilterStrategy implements ItemFilterStrategy {

    /** 关键词（小写后用于比较）。 */
    private final String keywordLower;

    /**
     * 构造关键词筛选策略。
     *
     * @param keyword 原始关键词；null 或空白表示不按关键词筛
     */
    public KeywordFilterStrategy(String keyword) {
        this.keywordLower = (keyword == null || keyword.isBlank())
                ? null
                : keyword.trim().toLowerCase(Locale.ROOT);
    }

    @Override
    public List<Item> filter(List<Item> items) {
        if (keywordLower == null) {
            return items; // 无关键词，直通
        }
        return items.stream()
                .filter(this::matches)
                .toList();
    }

    /** 判断单个物品是否命中关键词（名称或描述包含即可）。 */
    private boolean matches(Item item) {
        if (item == null) return false;
        String name = item.getName();
        if (name != null && name.toLowerCase(Locale.ROOT).contains(keywordLower)) {
            return true;
        }
        String desc = item.getDescription();
        return desc != null && desc.toLowerCase(Locale.ROOT).contains(keywordLower);
    }
}
