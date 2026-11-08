package com.lowcarbon.factory;

import com.lowcarbon.service.AdminService;
import com.lowcarbon.service.ItemService;
import com.lowcarbon.service.ReviewService;
import com.lowcarbon.service.StatisticsService;
import com.lowcarbon.service.TransactionService;
import com.lowcarbon.service.UserService;

/**
 * Service 工厂类——集中创建业务逻辑层实例。
 * <p>
 * 应用：工厂模式（Factory Pattern）。
 * 目的：
 * <ol>
 *   <li>解耦 GUI 层与 Service 具体依赖链，GUI 只需一行 {@code ServiceFactory.getXxxService()} 即可获取完整依赖的 Service 实例</li>
 *   <li>避免 Service 之间循环 new 的隐蔽风险（如 AdminService 之前在无参构造器里 new 了 UserService、ItemService、StatisticsService）</li>
 *   <li>依赖装配顺序在这里集中管理——基础 Service 先造，复合 Service 后造</li>
 * </ol>
 * <p>
 * 依赖装配顺序（按拓扑排序）：
 * <pre>
 *   UserService          ← 仅 UserDao
 *   ItemService          ← ItemDao, UserDao, CategoryDao
 *   TransactionService   ← TransactionDao, ItemDao, UserDao
 *   ReviewService        ← ReviewDao, TransactionDao, ItemDao, UserService
 *   StatisticsService    ← 5 个 DAO
 *   AdminService         ← UserDao, ItemDao, UserService, ItemService, StatisticsService
 * </pre>
 *
 * @author lowcarbon-team
 * @since 2026-11-09
 */
public final class ServiceFactory {

    private ServiceFactory() {
        // 工具类禁止实例化
    }

    /**
     * 创建 UserService（仅依赖 UserDao）。
     */
    public static UserService getUserService() {
        return new UserService(DaoFactory.getUserDao());
    }

    /**
     * 创建 ItemService（依赖 ItemDao + UserDao + CategoryDao）。
     */
    public static ItemService getItemService() {
        return new ItemService(
                DaoFactory.getItemDao(),
                DaoFactory.getUserDao(),
                DaoFactory.getCategoryDao()
        );
    }

    /**
     * 创建 TransactionService（依赖 TransactionDao + ItemDao + UserDao）。
     */
    public static TransactionService getTransactionService() {
        return new TransactionService(
                DaoFactory.getTransactionDao(),
                DaoFactory.getItemDao(),
                DaoFactory.getUserDao()
        );
    }

    /**
     * 创建 ReviewService（依赖 ReviewDao + TransactionDao + ItemDao + UserService）。
     */
    public static ReviewService getReviewService() {
        return new ReviewService(
                DaoFactory.getReviewDao(),
                DaoFactory.getTransactionDao(),
                DaoFactory.getItemDao(),
                getUserService()
        );
    }

    /**
     * 创建 StatisticsService（依赖全部 5 个 DAO）。
     */
    public static StatisticsService getStatisticsService() {
        return new StatisticsService(
                DaoFactory.getUserDao(),
                DaoFactory.getItemDao(),
                DaoFactory.getTransactionDao(),
                DaoFactory.getReviewDao(),
                DaoFactory.getCategoryDao()
        );
    }

    /**
     * 创建 AdminService（复合门面，依赖 UserDao + ItemDao + 3 个基础 Service）。
     */
    public static AdminService getAdminService() {
        return new AdminService(
                DaoFactory.getUserDao(),
                DaoFactory.getItemDao(),
                getUserService(),
                getItemService(),
                getStatisticsService()
        );
    }
}
