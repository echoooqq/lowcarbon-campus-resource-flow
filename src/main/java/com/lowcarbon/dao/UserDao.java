package com.lowcarbon.dao;

import com.lowcarbon.entity.User;

import java.util.List;

/**
 * 用户数据访问接口，定义对 {@code user} 表的 CRUD 操作。
 *
 * @author lowcarbon-team
 * @since 2026-11-09
 */
public interface UserDao {

    /**
     * 按用户 ID 查询。
     *
     * @param userId 用户 ID
     * @return 用户对象，不存在返回 null
     */
    User findById(Long userId);

    /**
     * 按用户名查询（登录时使用）。
     *
     * @param username 登录用户名
     * @return 用户对象，不存在返回 null
     */
    User findByUsername(String username);

    /**
     * 查询全部用户。
     *
     * @return 用户列表
     */
    List<User> findAll();

    /**
     * 新增用户。
     *
     * @param user 待新增用户（userId 由数据库自增）
     * @return 数据库生成的自增 ID
     */
    Long insert(User user);

    /**
     * 更新用户信息（密码、角色、信用分等）。
     *
     * @param user 带更新内容的用户对象（必须已带 userId）
     */
    void update(User user);

    /**
     * 按用户 ID 删除记录。
     *
     * @param userId 用户 ID
     */
    void delete(Long userId);

    /**
     * 统计用户总数。
     *
     * @return 用户数
     */
    int count();
}
