package com.lowcarbon.observer;

import com.lowcarbon.entity.ItemStatus;

/**
 * 物品状态变更观察者接口——观察者模式（Observer Pattern）的核心抽象。
 * <p>
 * 应用：观察者模式。
 * 目的：将"物品状态变更"这一事件的响应逻辑（日志、信用分联动、通知等）
 * 从 ItemService 主体中解耦出来。ItemService 只负责状态流转 + 通知观察者，
 * 具体的响应行为由各自的观察者实现类独立完成。
 * <p>
 * 新增响应动作时，只需新建一个实现类注册到 Subject，无需修改 ItemService 源码——
 * 开闭原则的典型落地。
 *
 * @author lowcarbon-team
 * @since 2026-11-09
 */
@FunctionalInterface
public interface ItemStatusObserver {

    /**
     * 物品状态变更时的回调。
     *
     * @param itemId    物品 ID
     * @param oldStatus 变更前状态
     * @param newStatus 变更后状态
     */
    void onStatusChanged(Long itemId, ItemStatus oldStatus, ItemStatus newStatus);
}
