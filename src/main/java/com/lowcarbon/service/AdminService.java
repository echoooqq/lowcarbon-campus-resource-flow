package com.lowcarbon.service;

import com.lowcarbon.dao.ItemDao;
import com.lowcarbon.dao.UserDao;
import com.lowcarbon.entity.Item;
import com.lowcarbon.entity.ItemStatus;
import com.lowcarbon.entity.User;
import com.lowcarbon.entity.UserRole;
import com.lowcarbon.exception.BusinessException;
import com.lowcarbon.exception.DaoException;
import com.lowcarbon.factory.DaoFactory;
import com.lowcarbon.factory.ServiceFactory;
import com.lowcarbon.util.PasswordUtil;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * 管理员后台业务逻辑层（AdminService）。
 * <p>
 * 覆盖需求：A-01 删除用户、A-02 强制下架、A-03 角色管理、A-04 系统级信用分重置、A-05 一键导出统计。
 * <p>
 * 本类是"管理员专用门面 Service"—— 所有公开方法的第一个参数都是 {@code adminId}，
 * 内部先做管理员身份校验，再委托给具体 DAO 或其他 Service。
 * 这样 GUI 层只需要持有一个 AdminService 实例即可完成全部后台操作。
 *
 * @author lowcarbon-team
 * @since 2026-11-09
 */
public class AdminService {

    private static final Logger LOGGER = Logger.getLogger(AdminService.class.getName());

    /** 管理员最小信用分（管理员不能信用分过低以免被误降级）。 */
    public static final int MIN_ADMIN_CREDIT = 50;

    /** 单个管理员账号的最大同时在线（桌面单机场景，保留常量便于扩展）。 */
    public static final int MAX_ADMIN_SESSION = 1;

    private final UserDao userDao;
    private final ItemDao itemDao;
    private final UserService userService;
    private final ItemService itemService;
    private final StatisticsService statisticsService;

    public AdminService() {
        this.userDao = DaoFactory.getUserDao();
        this.itemDao = DaoFactory.getItemDao();
        // 依赖 Service 由 ServiceFactory 负责装配完整依赖链
        this.userService = ServiceFactory.getUserService();
        this.itemService = ServiceFactory.getItemService();
        this.statisticsService = ServiceFactory.getStatisticsService();
    }

    /**
     * 测试用注入构造器。所有 DAO 和依赖 Service 都通过构造器传入。
     */
    public AdminService(UserDao userDao, ItemDao itemDao,
                         UserService userService, ItemService itemService,
                         StatisticsService statisticsService) {
        this.userDao = userDao;
        this.itemDao = itemDao;
        this.userService = userService;
        this.itemService = itemService;
        this.statisticsService = statisticsService;
    }

    // ==================== 0. 权限校验（所有管理员操作的前置） ====================

    /**
     * 验证 adminId 是否为管理员。
     * <p>
     * 封装成独立方法便于测试替换 + 便于未来增加密码二次确认逻辑。
     *
     * @param adminId 待验证的用户 ID
     * @return 若验证通过返回管理员 User 对象（GUI 层可能需要展示管理员信息）
     * @throws BusinessException 非管理员或用户不存在
     */
    public User verifyAdmin(Long adminId) {
        if (adminId == null) {
            throw new BusinessException("管理员 ID 不能为空");
        }
        try {
            User admin = userDao.findById(adminId);
            if (admin == null) {
                throw new BusinessException("管理员账号不存在");
            }
            if (admin.getRole() != UserRole.ADMIN) {
                LOGGER.log(Level.WARNING, "非管理员尝试执行后台操作: userId={0}, role={1}",
                        new Object[]{adminId, admin.getRole()});
                throw new BusinessException("非管理员账号无权执行此操作");
            }
            return admin;
        } catch (DaoException e) {
            LOGGER.log(Level.SEVERE, "管理员校验失败: adminId=" + adminId, e);
            throw new BusinessException("管理员身份验证失败，请稍后再试", e);
        }
    }

    // ==================== 1. 用户管理 ====================

    /**
     * 管理员重置用户密码（生成临时随机密码，返回明文让管理员告知用户）。
     * <p>
     * 不检查操作者角色（verifyAdmin 会拦），但会：
     * ① 目标用户必须存在；② 不能重置自己的密码（安全习惯）；③ 不能重置其他管理员密码
     * （避免多管理员互相锁死）。
     *
     * @param adminId   操作者 ID
     * @param targetId  待重置密码的用户 ID
     * @return 临时明文密码（仅本次返回，管理员应立即告知用户让其登录后修改）
     */
    public String resetPassword(Long adminId, Long targetId) {
        verifyAdmin(adminId);

        if (targetId == null) {
            throw new BusinessException("目标用户 ID 不能为空");
        }
        if (adminId.equals(targetId)) {
            throw new BusinessException("不能重置自己的密码");
        }

        User target = userOrThrow(targetId);
        // 管理员之间互相重置风险高——本项目单体可以放开，但保留一个警告
        if (target.getRole() == UserRole.ADMIN) {
            LOGGER.log(Level.WARNING, "管理员重置其他管理员密码: adminId={0}, targetId={1}",
                    new Object[]{adminId, targetId});
        }

        // 生成随机临时密码 + 新盐值
        String tempPwd = generateTempPassword();
        String salt = PasswordUtil.generateSalt();
        String hash = PasswordUtil.hashPassword(tempPwd, salt);

        target.setSalt(salt);
        target.setPasswordHash(hash);

        try {
            userDao.update(target);
            LOGGER.log(Level.INFO, "管理员重置密码: adminId={0}, targetId={1}",
                    new Object[]{adminId, targetId});
            return tempPwd;
        } catch (DaoException e) {
            throw new BusinessException("重置密码失败", e);
        }
    }

    /**
     * 角色变更（普通用户 ↔ 管理员）。
     * <p>
     * 规则：① 不能把自己降级为 USER（避免管理员操作失误）；② 管理员账号数不能为 0。
     *        当把最后一个管理员降级时抛出异常。
     *
     * @param adminId   操作者 ID
     * @param targetId  待变更用户 ID
     * @param newRole   目标角色
     */
    public void changeRole(Long adminId, Long targetId, UserRole newRole) {
        verifyAdmin(adminId);

        if (targetId == null || newRole == null) {
            throw new BusinessException("参数不能为空");
        }
        if (adminId.equals(targetId)) {
            throw new BusinessException("不能变更自己的角色");
        }

        User target = userOrThrow(targetId);
        if (target.getRole() == newRole) {
            return; // 幂等
        }

        // 降级到 USER 时必须确保仍有至少 1 个管理员
        if (newRole == UserRole.USER) {
            long remaining = countAdmins() - (target.getRole() == UserRole.ADMIN ? 1 : 0);
            if (remaining <= 0) {
                throw new BusinessException("系统至少需要保留 1 个管理员账号");
            }
        }

        // 管理员不能信用分过低（避免降级后无法升级回来）
        if (newRole == UserRole.ADMIN && target.getCreditScore() < MIN_ADMIN_CREDIT) {
            target.setCreditScore(MIN_ADMIN_CREDIT);
            LOGGER.log(Level.WARNING, "提升为管理员的用户信用分低于最小值，已自动补到 {0}", MIN_ADMIN_CREDIT);
        }

        target.setRole(newRole);
        try {
            userDao.update(target);
            LOGGER.log(Level.INFO, "管理员变更角色: adminId={0}, targetId={1}, {2} → {3}",
                    new Object[]{adminId, targetId, target.getRole(), newRole});
        } catch (DaoException e) {
            throw new BusinessException("角色变更失败", e);
        }
    }

    /**
     * 重置指定用户信用分到默认值 100。
     */
    public void resetCreditScore(Long adminId, Long targetId) {
        verifyAdmin(adminId);
        if (targetId == null) throw new BusinessException("目标用户 ID 不能为空");

        User target = userOrThrow(targetId);
        try {
            target.setCreditScore(UserService.DEFAULT_CREDIT_SCORE);
            userDao.update(target);
            LOGGER.log(Level.INFO, "管理员重置信用分: adminId={0}, targetId={1}",
                    new Object[]{adminId, targetId});
        } catch (DaoException e) {
            throw new BusinessException("重置信用分失败", e);
        }
    }

    /**
     * 删除用户（委托给 UserService，其内部已有管理员校验）。
     */
    public void deleteUser(Long adminId, Long targetId) {
        verifyAdmin(adminId);
        userService.deleteUser(targetId, adminId);
    }

    /** 列出系统所有用户（按信用分降序）。 */
    public List<User> listAllUsers(Long adminId) {
        verifyAdmin(adminId);
        try {
            List<User> users = userDao.findAll();
            users.sort((a, b) -> Integer.compare(b.getCreditScore(), a.getCreditScore()));
            return users;
        } catch (DaoException e) {
            throw new BusinessException("查询用户列表失败", e);
        }
    }

    /** 统计管理员账号数。 */
    public int countAdmins() {
        try {
            return (int) userDao.findAll().stream()
                    .filter(u -> u.getRole() == UserRole.ADMIN).count();
        } catch (DaoException e) {
            throw new BusinessException("统计管理员数失败", e);
        }
    }

    // ==================== 2. 物品管理 ====================

    /**
     * 管理员强制下架（委托给 ItemService.adminTakeOffShelf）。
     */
    public void forceOffShelf(Long adminId, Long itemId, String reason) {
        verifyAdmin(adminId);
        itemService.adminTakeOffShelf(itemId, adminId, reason);
    }

    /** 管理员视角查看所有物品（含所有状态 + 已下架）。 */
    public List<Item> listAllItems(Long adminId) {
        verifyAdmin(adminId);
        return itemService.listAll();
    }

    /**
     * 管理员把 OFF_SHELF 物品重新上架为 ON_SALE。
     * <p>
     * 普通发布者没有"重新上架"按钮（ItemService 里没这个方法），
     * 管理员拥有这个特权。只允许 OFF_SHELF → ON_SALE。
     */
    public void adminRelistItem(Long adminId, Long itemId) {
        verifyAdmin(adminId);
        if (itemId == null) throw new BusinessException("物品 ID 不能为空");

        Item item = itemOrThrow(itemId);
        if (item.getStatus() != ItemStatus.OFF_SHELF) {
            throw new BusinessException("只有已下架物品可以重新上架，当前状态："
                    + AdminService.itemStatusLabel(item.getStatus()));
        }
        try {
            itemDao.updateStatus(itemId, ItemStatus.ON_SALE);
            LOGGER.log(Level.INFO, "管理员重新上架物品: adminId={0}, itemId={1}",
                    new Object[]{adminId, itemId});
        } catch (DaoException e) {
            throw new BusinessException("重新上架失败", e);
        }
    }

    // ==================== 3. 统计与导出（A-05） ====================

    /** 管理员查看系统总览（直接委托给 StatisticsService）。 */
    public Map<String, Object> overview(Long adminId) {
        verifyAdmin(adminId);
        return statisticsService.overview();
    }

    /** 管理员导出 CSV 到指定路径。 */
    public void exportCsv(Long adminId, Path filePath) {
        verifyAdmin(adminId);
        try {
            statisticsService.exportAll(filePath);
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "管理员导出 CSV 失败: path=" + filePath, e);
            throw new BusinessException("导出 CSV 失败：" + e.getMessage(), e);
        }
    }

    /** String 路径重载。 */
    public void exportCsv(Long adminId, String filePath) {
        exportCsv(adminId, Paths.get(filePath));
    }

    // ==================== 辅助 ====================

    private User userOrThrow(Long userId) {
        try {
            User u = userDao.findById(userId);
            if (u == null) throw new BusinessException("用户不存在");
            return u;
        } catch (DaoException e) {
            throw new BusinessException("查询用户失败", e);
        }
    }

    private Item itemOrThrow(Long itemId) {
        try {
            Item it = itemDao.findById(itemId);
            if (it == null) throw new BusinessException("物品不存在");
            return it;
        } catch (DaoException e) {
            throw new BusinessException("查询物品失败", e);
        }
    }

    /** 生成临时随机密码（8 字母数字混合，去掉易混字符）。 */
    private static final char[] TEMP_CHARS = "abcdefghjkmnpqrstuvwxyz23456789".toCharArray();
    private String generateTempPassword() {
        java.util.Random rnd = new java.util.Random();
        StringBuilder sb = new StringBuilder(8);
        for (int i = 0; i < 8; i++) {
            sb.append(TEMP_CHARS[rnd.nextInt(TEMP_CHARS.length)]);
        }
        return sb.toString();
    }

    static String itemStatusLabel(ItemStatus s) {
        return switch (s) {
            case ON_SALE   -> "在售";
            case RESERVED  -> "已预订";
            case SOLD      -> "已成交";
            case OFF_SHELF -> "已下架";
        };
    }
}
