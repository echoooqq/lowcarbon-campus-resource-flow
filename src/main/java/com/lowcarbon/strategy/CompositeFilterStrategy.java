package com.lowcarbon.strategy;

import com.lowcarbon.entity.Item;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * 组合筛选策略——将多个子策略按顺序链式叠加（AND 语义，每个策略依次缩小结果集）。
 * <p>
 * 应用：策略模式 + 组合模式（Composite），一个 {@code CompositeFilterStrategy} 可以嵌套另一个
 * {@code CompositeFilterStrategy}，形成任意深度的筛选管道。
 * <p>
 * 如果子策略列表为空，直接返回原输入列表（no-op）。
 *
 * @author lowcarbon-team
 * @since 2026-11-09
 */
public class CompositeFilterStrategy implements ItemFilterStrategy {

    /** 子策略列表（按执行顺序）。 */
    private final List<ItemFilterStrategy> children;

    /**
     * 可变参数构造。
     *
     * @param strategies 子策略（可空、可多个）
     */
    public CompositeFilterStrategy(ItemFilterStrategy... strategies) {
        this.children = strategies == null
                ? Collections.emptyList()
                : new ArrayList<>(Arrays.asList(strategies));
    }

    /**
     * List 构造。
     *
     * @param strategies 子策略列表
     */
    public CompositeFilterStrategy(List<ItemFilterStrategy> strategies) {
        this.children = strategies == null
                ? Collections.emptyList()
                : new ArrayList<>(strategies);
    }

    @Override
    public List<Item> filter(List<Item> items) {
        List<Item> current = items;
        for (ItemFilterStrategy child : children) {
            if (child != null) {
                current = child.filter(current);
            }
        }
        return current;
    }
}
