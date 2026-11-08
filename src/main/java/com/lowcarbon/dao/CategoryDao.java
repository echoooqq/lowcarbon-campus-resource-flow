package com.lowcarbon.dao;

import com.lowcarbon.entity.Category;

import java.util.List;

/**
 * 物品分类数据访问接口，定义对 {@code category} 表的 CRUD 操作。
 *
 * @author lowcarbon-team
 * @since 2026-11-09
 */
public interface CategoryDao {

    /**
     * 按分类 ID 查询。
     *
     * @param categoryId 分类 ID
     * @return 分类对象，不存在返回 null
     */
    Category findById(Long categoryId);

    /**
     * 按分类名称查询（名称唯一）。
     *
     * @param name 分类名称
     * @return 分类对象，不存在返回 null
     */
    Category findByName(String name);

    /**
     * 查询全部分类。
     *
     * @return 分类列表
     */
    List<Category> findAll();

    /**
     * 新增分类。
     *
     * @param category 待新增分类（categoryId 由数据库自增）
     * @return 数据库生成的自增 ID
     */
    Long insert(Category category);

    /**
     * 更新分类信息。
     *
     * @param category 带更新内容的分类对象（必须已带 categoryId）
     */
    void update(Category category);

    /**
     * 按分类 ID 删除记录。
     *
     * @param categoryId 分类 ID
     */
    void delete(Long categoryId);

    /**
     * 统计分类总数。
     *
     * @return 分类数
     */
    int count();
}
