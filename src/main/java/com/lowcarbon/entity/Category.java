package com.lowcarbon.entity;

import java.util.Objects;

/**
 * 物品分类实体类，对应数据库 {@code category} 表。
 * <p>
 * 字段映射：
 * <ul>
 *   <li>{@code category_id} → {@link #categoryId}</li>
 *   <li>{@code name}        → {@link #name}</li>
 *   <li>{@code description} → {@link #description}</li>
 * </ul>
 *
 * @author lowcarbon-team
 * @since 2026-11-08
 */
public class Category {

    /** 分类 ID（主键，自增）。 */
    private Long categoryId;

    /** 分类名称（唯一）。 */
    private String name;

    /** 分类描述（可选）。 */
    private String description;

    // ==================== 构造器 ====================

    /**
     * 无参构造器。
     */
    public Category() {
    }

    /**
     * 创建新分类时使用的构造器（categoryId 由数据库自增）。
     *
     * @param name        分类名称
     * @param description 分类描述（可 null）
     */
    public Category(String name, String description) {
        this.name = name;
        this.description = description;
    }

    /**
     * 全参构造器。
     */
    public Category(Long categoryId, String name, String description) {
        this.categoryId = categoryId;
        this.name = name;
        this.description = description;
    }

    // ==================== Getter / Setter ====================

    public Long getCategoryId() {
        return categoryId;
    }

    public void setCategoryId(Long categoryId) {
        this.categoryId = categoryId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    // ==================== equals / hashCode / toString ====================

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Category other)) {
            return false;
        }
        return Objects.equals(categoryId, other.categoryId)
                && Objects.equals(name, other.name);
    }

    @Override
    public int hashCode() {
        return Objects.hash(categoryId, name);
    }

    @Override
    public String toString() {
        return "Category{"
                + "categoryId=" + categoryId
                + ", name='" + name + '\''
                + ", description='" + description + '\''
                + '}';
    }
}
