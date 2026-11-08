package com.lowcarbon.service;

import com.lowcarbon.dao.UserDao;
import com.lowcarbon.entity.User;
import com.lowcarbon.entity.UserRole;
import com.lowcarbon.exception.BusinessException;
import com.lowcarbon.exception.DaoException;
import com.lowcarbon.factory.DaoFactory;
import com.lowcarbon.util.PasswordUtil;
import com.lowcarbon.util.Validator;

import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * 用户模块业务逻辑层。
 * <p>
 * 负责用户注册、登录、密码管理、信用分维护等核心业务，
 * 向上为 GUI 层提供服务，向下调用 {@link UserDao} 完成持久化。
 * <p>
 * 本类不做线程安全保证（桌面单用户场景）。
 *
 * @author lowcarbon-team
 * @since 2026-11-09
 */
public class UserService {

    private static final Logger LOGGER = Logger.getLogger(UserService.class.getName());

    /** 默认信用分。 */
    public static final int DEFAULT_CREDIT_SCORE = 100;

    private final UserDao userDao;

    /**
     * 使用默认 DAO 实现构造。
     */
    public UserService() {
        this.userDao = DaoFactory.getUserDao();
    }

    /**
     * 使用指定 DAO 构造（便于单元测试注入 Mock）。
     *
     * @param userDao 用户数据访问接口
     */
    public UserService(UserDao userDao) {
        this.userDao = userDao;
    }

    // ==================== 注册 ====================

    /**
     * 用户注册。
     * <p>
     * 校验顺序：输入合法性 → 密码一致性 → 用户名唯一性 → 加密存储。
     *
     * @param username  登录用户名
     * @param password  登录密码
     * @param confirm   确认密码
     * @return 注册成功后的用户对象（带生成的 userId）
     * @throws BusinessException 任意业务校验失败时抛出
     */
    public User register(String username, String password, String confirm) {
        // 1. 校验用户名
        String err = Validator.validateUsername(username);
        if (err != null) {
            throw new BusinessException(err);
        }
        // 2. 校验密码
        err = Validator.validatePassword(password);
        if (err != null) {
            throw new BusinessException(err);
        }
        // 3. 校验两次密码一致
        err = Validator.validatePasswordConfirm(password, confirm);
        if (err != null) {
            throw new BusinessException(err);
        }

        // 4. 检查用户名是否已存在
        try {
            User existing = userDao.findByUsername(username);
            if (existing != null) {
                throw new BusinessException("用户名 \"" + username + "\" 已被占用");
            }

            // 5. 生成盐值 + 哈希密码
            String salt = PasswordUtil.generateSalt();
            String hash = PasswordUtil.hashPassword(password, salt);

            // 6. 构造用户对象
            User user = new User(username, hash, salt);
            user.setRole(UserRole.USER);
            user.setCreditScore(DEFAULT_CREDIT_SCORE);
            user.setCreateTime(System.currentTimeMillis());

            // 7. 持久化
            Long newId = userDao.insert(user);
            LOGGER.log(Level.INFO, "新用户注册成功: userId={0}, username={1}",
                    new Object[]{newId, username});
            return user;
        } catch (DaoException e) {
            LOGGER.log(Level.SEVERE, "注册用户时数据层异常: username=" + username, e);
            throw new BusinessException("注册失败，请稍后再试", e);
        }
    }

    // ==================== 登录 ====================

    /**
     * 用户登录验证。
     *
     * @param username 登录用户名
     * @param password 登录密码（明文）
     * @return 登录成功的用户对象
     * @throws BusinessException 用户不存在或密码错误时抛出
     */
    public User login(String username, String password) {
        if (Validator.isBlank(username) || Validator.isBlank(password)) {
            throw new BusinessException("用户名和密码不能为空");
        }
        try {
            User user = userDao.findByUsername(username);
            if (user == null) {
                LOGGER.log(Level.WARNING, "登录失败：用户名不存在: username={0}", username);
                throw new BusinessException("用户名或密码错误");
            }
            if (!PasswordUtil.verifyPassword(password, user.getPasswordHash(), user.getSalt())) {
                LOGGER.log(Level.WARNING, "登录失败：密码错误: username={0}", username);
                throw new BusinessException("用户名或密码错误");
            }
            LOGGER.log(Level.INFO, "用户登录成功: userId={0}, username={1}",
                    new Object[]{user.getUserId(), username});
            return user;
        } catch (DaoException e) {
            LOGGER.log(Level.SEVERE, "登录时数据层异常: username=" + username, e);
            throw new BusinessException("登录失败，请稍后再试", e);
        }
    }

    // ==================== 查询 ====================

    /**
     * 按用户 ID 查询。
     *
     * @param userId 用户 ID
     * @return 用户对象；不存在返回 null
     */
    public User findById(Long userId) {
        if (userId == null) {
            return null;
        }
        try {
            return userDao.findById(userId);
        } catch (DaoException e) {
            LOGGER.log(Level.SEVERE, "按 ID 查询用户异常: userId=" + userId, e);
            throw new BusinessException("查询用户失败", e);
        }
    }

    /**
     * 按用户名查询。
     *
     * @param username 登录用户名
     * @return 用户对象；不存在返回 null
     */
    public User findByUsername(String username) {
        if (Validator.isBlank(username)) {
            return null;
        }
        try {
            return userDao.findByUsername(username);
        } catch (DaoException e) {
            LOGGER.log(Level.SEVERE, "按用户名查询用户异常: username=" + username, e);
            throw new BusinessException("查询用户失败", e);
        }
    }

    /**
     * 查询全部用户（管理员后台使用）。
     *
     * @return 用户列表，按创建时间降序排列
     */
    public List<User> listAll() {
        try {
            return userDao.findAll();
        } catch (DaoException e) {
            LOGGER.log(Level.SEVERE, "查询全部用户异常", e);
            throw new BusinessException("获取用户列表失败", e);
        }
    }

    /**
     * 统计用户总数。
     *
     * @return 用户数
     */
    public int countUsers() {
        try {
            return userDao.count();
        } catch (DaoException e) {
            LOGGER.log(Level.SEVERE, "统计用户数异常", e);
            throw new BusinessException("统计用户数失败", e);
        }
    }

    // ==================== 密码管理 ====================

    /**
     * 修改密码。
     * <p>
     * 会生成新的盐值并重新哈希，避免沿用旧盐值。
     *
     * @param userId      用户 ID
     * @param oldPassword 旧密码（明文）
     * @param newPassword 新密码（明文）
     * @throws BusinessException 用户不存在、旧密码错误或新密码校验失败时抛出
     */
    public void changePassword(Long userId, String oldPassword, String newPassword) {
        if (userId == null) {
            throw new BusinessException("用户 ID 不能为空");
        }
        // 1. 查找用户
        User user;
        try {
            user = userDao.findById(userId);
        } catch (DaoException e) {
            LOGGER.log(Level.SEVERE, "修改密码时查询用户异常: userId=" + userId, e);
            throw new BusinessException("修改密码失败，请稍后再试", e);
        }
        if (user == null) {
            throw new BusinessException("用户不存在");
        }

        // 2. 验证旧密码
        if (!PasswordUtil.verifyPassword(oldPassword, user.getPasswordHash(), user.getSalt())) {
            throw new BusinessException("旧密码错误");
        }

        // 3. 校验新密码
        String err = Validator.validatePassword(newPassword);
        if (err != null) {
            throw new BusinessException("新密码校验失败：" + err);
        }
        if (oldPassword.equals(newPassword)) {
            throw new BusinessException("新密码不能与旧密码相同");
        }

        // 4. 生成新盐值 + 新哈希
        String newSalt = PasswordUtil.generateSalt();
        String newHash = PasswordUtil.hashPassword(newPassword, newSalt);
        user.setSalt(newSalt);
        user.setPasswordHash(newHash);

        // 5. 更新
        try {
            userDao.update(user);
            LOGGER.log(Level.INFO, "用户密码修改成功: userId={0}", userId);
        } catch (DaoException e) {
            LOGGER.log(Level.SEVERE, "修改密码时更新数据库异常: userId=" + userId, e);
            throw new BusinessException("修改密码失败，请稍后再试", e);
        }
    }

    // ==================== 信用分管理 ====================

    /**
     * 调整用户信用分。
     * <p>
     * 信用分会被夹紧到 {@link Validator#MIN_CREDIT_SCORE} ~ {@link Validator#MAX_CREDIT_SCORE} 之间。
     *
     * @param userId 用户 ID
     * @param delta  调整量（正数加分，负数扣分）
     * @throws BusinessException 用户不存在时抛出
     */
    public void adjustCreditScore(Long userId, int delta) {
        if (userId == null) {
            throw new BusinessException("用户 ID 不能为空");
        }
        try {
            User user = userDao.findById(userId);
            if (user == null) {
                throw new BusinessException("用户不存在");
            }
            int newScore = clampCreditScore(user.getCreditScore() + delta);
            user.setCreditScore(newScore);
            userDao.update(user);
            LOGGER.log(Level.INFO, "用户信用分调整: userId={0}, delta={1}, 新值={2}",
                    new Object[]{userId, delta, newScore});
        } catch (DaoException e) {
            LOGGER.log(Level.SEVERE, "调整信用分异常: userId=" + userId, e);
            throw new BusinessException("信用分更新失败，请稍后再试", e);
        }
    }

    /**
     * 直接设置信用分（管理员操作）。
     *
     * @param userId      用户 ID
     * @param creditScore 目标信用分
     * @throws BusinessException 用户不存在或信用分越界时抛出
     */
    public void setCreditScore(Long userId, int creditScore) {
        String err = Validator.validateCreditScore(creditScore);
        if (err != null) {
            throw new BusinessException(err);
        }
        if (userId == null) {
            throw new BusinessException("用户 ID 不能为空");
        }
        try {
            User user = userDao.findById(userId);
            if (user == null) {
                throw new BusinessException("用户不存在");
            }
            user.setCreditScore(creditScore);
            userDao.update(user);
            LOGGER.log(Level.INFO, "用户信用分被管理员设置: userId={0}, score={1}",
                    new Object[]{userId, creditScore});
        } catch (DaoException e) {
            LOGGER.log(Level.SEVERE, "设置信用分异常: userId=" + userId, e);
            throw new BusinessException("信用分更新失败，请稍后再试", e);
        }
    }

    // ==================== 删除用户（管理员） ====================

    /**
     * 删除用户（管理员操作）。
     *
     * @param userId      待删除用户 ID
     * @param operatorId  执行删除的管理员 ID（用于审计日志）
     * @throws BusinessException 用户不存在或操作者非管理员时抛出
     */
    public void deleteUser(Long userId, Long operatorId) {
        if (userId == null) {
            throw new BusinessException("待删除用户 ID 不能为空");
        }
        if (operatorId == null) {
            throw new BusinessException("操作者 ID 不能为空");
        }
        try {
            // 验证操作者身份
            User operator = userDao.findById(operatorId);
            if (operator == null || operator.getRole() != UserRole.ADMIN) {
                throw new BusinessException("仅管理员可执行删除操作");
            }
            // 不能删自己
            if (operatorId.equals(userId)) {
                throw new BusinessException("不能删除自己的账号");
            }
            // 确认目标存在
            User target = userDao.findById(userId);
            if (target == null) {
                throw new BusinessException("目标用户不存在");
            }
            userDao.delete(userId);
            LOGGER.log(Level.INFO, "管理员删除用户: targetId={0}, operatorId={1}",
                    new Object[]{userId, operatorId});
        } catch (DaoException e) {
            LOGGER.log(Level.SEVERE, "删除用户异常: userId=" + userId, e);
            throw new BusinessException("删除用户失败，请稍后再试", e);
        }
    }

    // ==================== 辅助方法 ====================

    /**
     * 将信用分夹紧到合法区间。
     *
     * @param score 原始值
     * @return 夹紧后的值
     */
    private int clampCreditScore(int score) {
        return Math.max(Validator.MIN_CREDIT_SCORE, Math.min(Validator.MAX_CREDIT_SCORE, score));
    }
}
