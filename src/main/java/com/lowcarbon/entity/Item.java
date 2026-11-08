package com.lowcarbon.entity;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 闲置物品实体类，对应数据库 {@code item} 表。
 * <p>
 * 字段映射：
 * <ul>
 *   <li>{@code item_id}      → {@link #itemId}</li>
 *   <li>{@code publisher_id} → {@link #publisherId}</li>
 *   <li>{@code name}         → {@link #name}</li>
 *   <li>{@code category_id}  → {@link #categoryId}</li>
 *   <li>{@code description}  → {@link #description}</li>
 *   <li>{@code price}        → {@link #price}</li>
 *   <li>{@code trade_type}   → {@link #tradeType}（通过 {@link TradeType} 枚举表达）</li>
 *   <li>{@code status}       → {@link #status}（通过 {@link ItemStatus} 枚举表达）</li>
 *   <li>{@code tags}         → {@link #tagsStr}（逗号分隔字符串，可通过 {@link #getTagList()} 转为 List）</li>
 *   <li>{@code publish_time} → {@link #publishTime}</li>
 * </ul>
 *
 * @author lowcarbon-team
 * @since 2026-11-08
 */
public class Item {

    /** 物品 ID（主键，自增）。 */
    private Long itemId;

    /** 发布者用户 ID（外键 → user.user_id）。 */
    private Long publisherId;

    /** 物品名称。 */
    private String name;

    /** 分类 ID（外键 → category.category_id）。 */
    private Long categoryId;

    /** 物品详细描述（可选）。 */
    private String description;

    /** 价格（单位：元，默认 0.0 表示免费）。 */
    private double price;

    /** 交易类型。 */
    private TradeType tradeType;

    /** 当前状态。 */
    private ItemStatus status;

    /** 标签字符串（逗号分隔，数据库存 VARCHAR）。 */
    private String tagsStr;

    /** 发布时间（Unix 毫秒时间戳）。 */
    private long publishTime;

    // ==================== 构造器 ====================

    /**
     * 无参构造器。
     */
    public Item() {
    }

    /**
     * 发布物品时使用的构造器（itemId 由数据库自增，status 默认 ON_SALE，
     * publishTime 默认由 Service 层填充）。
     */
    public Item(Long publisherId, String name, Long categoryId, String description,
                double price, TradeType tradeType) {
        this.publisherId = publisherId;
        this.name = name;
        this.categoryId = categoryId;
        this.description = description;
        this.price = price;
        this.tradeType = tradeType;
        this.status = ItemStatus.ON_SALE;
    }

    /**
     * 全参构造器。
     */
    public Item(Long itemId, Long publisherId, String name, Long categoryId,
                String description, double price, TradeType tradeType,
                ItemStatus status, String tagsStr, long publishTime) {
        this.itemId = itemId;
        this.publisherId = publisherId;
        this.name = name;
        this.categoryId = categoryId;
        this.description = description;
        this.price = price;
        this.tradeType = tradeType;
        this.status = status;
        this.tagsStr = tagsStr;
        this.publishTime = publishTime;
    }

    // ==================== Getter / Setter ====================

    public Long getItemId() {
        return itemId;
    }

    public void setItemId(Long itemId) {
        this.itemId = itemId;
    }

    public Long getPublisherId() {
        return publisherId;
    }

    public void setPublisherId(Long publisherId) {
        this.publisherId = publisherId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Long getCategoryId() {
        return categoryId;
    }

    public void setCategoryId(Long categoryId) {
        this.categoryId = categoryId;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public double getPrice() {
        return price;
    }

    public void setPrice(double price) {
        this.price = price;
    }

    public TradeType getTradeType() {
        return tradeType;
    }

    public void setTradeType(TradeType tradeType) {
        this.tradeType = tradeType;
    }

    public ItemStatus getStatus() {
        return status;
    }

    public void setStatus(ItemStatus status) {
        this.status = status;
    }

    public String getTagsStr() {
        return tagsStr;
    }

    public void setTagsStr(String tagsStr) {
        this.tagsStr = tagsStr;
    }

    public long getPublishTime() {
        return publishTime;
    }

    public void setPublishTime(long publishTime) {
        this.publishTime = publishTime;
    }

    // ==================== 标签便捷方法 ====================

    /**
     * 将 {@link #tagsStr}（逗号分隔）解析为标签列表。
     *
     * @return 不可变的标签列表；若 tagsStr 为 null/空则返回空列表
     */
    public List<String> getTagList() {
        if (tagsStr == null || tagsStr.isBlank()) {
            return Collections.emptyList();
        }
        return Arrays.stream(tagsStr.split(","))
                .map(String::trim)
                .filter(t -> !t.isEmpty())
                .collect(Collectors.toList());
    }

    /**
     * 根据标签列表设置 {@link #tagsStr}（逗号分隔）。
     *
     * @param tags 标签列表
     */
    public void setTagList(List<String> tags) {
        if (tags == null || tags.isEmpty()) {
            this.tagsStr = null;
        } else {
            this.tagsStr = tags.stream()
                    .map(String::trim)
                    .filter(t -> !t.isEmpty())
                    .collect(Collectors.joining(","));
        }
    }

    // ==================== equals / hashCode / toString ====================

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Item other)) {
            return false;
        }
        return Objects.equals(itemId, other.itemId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(itemId);
    }

    @Override
    public String toString() {
        return "Item{"
                + "itemId=" + itemId
                + ", name='" + name + '\''
                + ", price=" + price
                + ", tradeType=" + tradeType
                + ", status=" + status
                + ", publisherId=" + publisherId
                + ", categoryId=" + categoryId
                + '}';
    }
}
