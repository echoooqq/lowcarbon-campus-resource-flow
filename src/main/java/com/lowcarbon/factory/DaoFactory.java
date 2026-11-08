package com.lowcarbon.factory;

import com.lowcarbon.dao.CategoryDao;
import com.lowcarbon.dao.ItemDao;
import com.lowcarbon.dao.ReviewDao;
import com.lowcarbon.dao.TransactionDao;
import com.lowcarbon.dao.UserDao;
import com.lowcarbon.dao.impl.CategoryDaoImpl;
import com.lowcarbon.dao.impl.ItemDaoImpl;
import com.lowcarbon.dao.impl.ReviewDaoImpl;
import com.lowcarbon.dao.impl.TransactionDaoImpl;
import com.lowcarbon.dao.impl.UserDaoImpl;

/**
 * DAO 工厂类——集中创建 DAO 接口实现实例。
 * <p>
 * 应用：工厂模式（Factory Pattern）。
 * 目的：解耦 Service 层与 DAO 具体实现类，Service 不再直接 {@code new} DAO impl，
 * 而是通过本工厂获取接口引用。未来如果 DAO 实现需要替换（如从 SQLite 切换到 MySQL），
 * 只需在本工厂里改动一行代码。
 * <p>
 * 本工厂采用简单工厂（Simple Factory）变体，不做 DAO 实例的缓存——
 * DAO 是无状态的轻量对象，每次创建成本可忽略；缓存反而可能带来连接泄漏风险。
 *
 * @author lowcarbon-team
 * @since 2026-11-09
 */
public final class DaoFactory {

    private DaoFactory() {
        // 工具类禁止实例化
    }

    /**
     * 创建用户 DAO。
     *
     * @return UserDao 接口引用
     */
    public static UserDao getUserDao() {
        return new UserDaoImpl();
    }

    /**
     * 创建物品 DAO。
     *
     * @return ItemDao 接口引用
     */
    public static ItemDao getItemDao() {
        return new ItemDaoImpl();
    }

    /**
     * 创建分类 DAO。
     *
     * @return CategoryDao 接口引用
     */
    public static CategoryDao getCategoryDao() {
        return new CategoryDaoImpl();
    }

    /**
     * 创建流转记录 DAO。
     *
     * @return TransactionDao 接口引用
     */
    public static TransactionDao getTransactionDao() {
        return new TransactionDaoImpl();
    }

    /**
     * 创建评价 DAO。
     *
     * @return ReviewDao 接口引用
     */
    public static ReviewDao getReviewDao() {
        return new ReviewDaoImpl();
    }
}
