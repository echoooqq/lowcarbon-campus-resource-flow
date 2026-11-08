package com.lowcarbon.observer;

/**
 * 物品状态变更的被观察者接口——观察者模式（Observer Pattern）的 Subject 抽象。
 * <p>
 * 定义观察者的注册、注销、通知接口。ItemService 将实现本接口，
 * 在 {@code transitionStatus()} 中触发 {@code notifyObservers}。
 *
 * @author lowcarbon-team
 * @since 2026-11-09
 */
public interface ItemStatusSubject {

    /**
     * 注册一个观察者（重复注册会被忽略，不会重复通知）。
     *
     * @param observer 观察者实例（非 null）
     */
    void addObserver(ItemStatusObserver observer);

    /**
     * 注销一个观察者。
     *
     * @param observer 观察者实例
     */
    void removeObserver(ItemStatusObserver observer);
}
