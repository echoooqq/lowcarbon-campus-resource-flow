---
name: lowcarbon-service-layer
description: 生成低碳校园系统的 Service 层代码与单元测试。当用户要求执行 T3-3/T3-4/T3-5 或任何业务模块开发、创建 XxxService、写 Fake DAO 测试时触发。Do not use for DAO 层开发或 GUI 层开发。
---

# 低碳校园系统 — Service 层开发套路

本 Skill 沉淀自 UserService（T3-1）和 ItemService（T3-2）的开发经验。后续开发 TransactionService、ReviewService、StatisticsService、AdminService 时必须严格遵循。

---

## 一、Service 类骨架模板

```java
package com.lowcarbon.service;

import com.lowcarbon.dao.XxxDao;
import com.lowcarbon.dao.UserDao;
import com.lowcarbon.dao.impl.XxxDaoImpl;
import com.lowcarbon.dao.impl.UserDaoImpl;
import com.lowcarbon.entity.Xxx;
import com.lowcarbon.entity.User;
import com.lowcarbon.entity.UserRole;
import com.lowcarbon.exception.BusinessException;
import com.lowcarbon.exception.DaoException;
import com.lowcarbon.util.Validator;

import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * XXX 模块业务逻辑层。
 * <p>
 * 覆盖需求：X-XX 功能描述。
 *
 * @author lowcarbon-team
 * @since YYYY-MM-DD
 */
public class XxxService {

    private static final Logger LOGGER = Logger.getLogger(XxxService.class.getName());

    /** 常量示例 —— 默认值或边界值。 */
    public static final int DEFAULT_XXX = 100;

    private final XxxDao xxxDao;
    private final UserDao userDao;
    // 其他依赖 DAO...

    /**
     * 默认构造器：用真实 DAO（生产路径）。
     */
    public XxxService() {
        this.xxxDao = new XxxDaoImpl();
        this.userDao = new UserDaoImpl();
    }

    /**
     * 注入构造器（测试路径）。Fake DAO 从这里传入。
     */
    public XxxService(XxxDao xxxDao, UserDao userDao) {
        this.xxxDao = xxxDao;
        this.userDao = userDao;
    }

    // ==================== 业务方法... ====================
}
```

### 命名规范

| 元素 | 规范 |
|------|------|
| 包 | `com.lowcarbon.service`（无 impl 子包，Service 不分接口/实现） |
| 类名 | XxxService（**不是** XxxServiceImpl，Service 直接是类） |
| DAO 依赖 | 字段类型为接口（`UserDao`），构造器注入默认实现（`UserDaoImpl`） |

**为什么 Service 不分接口？** 本项目是单体桌面应用，没有替换 Service 实现的需求；DAO 分接口是因为 Fake DAO 测试需要隔离。Service 层直接用类 + 构造器注入 Fake DAO 即可。

---

## 二、业务方法通用结构

每个业务方法按以下顺序组织代码，**严格不跳步**：

```java
public R businessMethod(...) {
    // 1. 参数空值防御（只有必要时才写，全 null 检查放在 Validator 之后）
    if (param == null) {
        throw new BusinessException("参数不能为空");
    }

    // 2. 调 Validator 做输入合法性校验（用户名格式 / 密码强度 / 价格范围 / 描述长度 / 关键词注入防护）
    String err = Validator.validateXxx(param);
    if (err != null) {
        throw new BusinessException(err);
    }

    // 3. 查前置依赖（发布者存在？分类存在？操作者是管理员？目标物品属于该用户？）
    try {
        User operator = userDao.findById(operatorId);
        if (operator == null) {
            throw new BusinessException("操作者不存在");
        }
    } catch (DaoException e) {
        LOGGER.log(Level.SEVERE, "查询操作者失败: operatorId=" + operatorId, e);
        throw new BusinessException("操作失败，请稍后再试", e);
    }

    // 4. 业务前置条件（状态是否流转中？权限？重复操作？）
    if (item.getStatus() == ItemStatus.RESERVED) {
        throw new BusinessException("流转中的物品不允许修改");
    }

    // 5. DAO 写入/查询
    try {
        Long id = xxxDao.insert(entity);
        LOGGER.log(Level.INFO, "XXX 成功: id={0}", id);
        return entity;
    } catch (DaoException e) {
        LOGGER.log(Level.SEVERE, "XXX 失败: ...", e);
        throw new BusinessException("XXX 失败，请稍后再试", e);
    }
}
```

### 关键规则（违反即返工）

1. **`DaoException` 必须被 Service 层捕获并包装为 `BusinessException`**。DAO 层向上抛出的任何 `DaoException` 都不能泄漏到 GUI 层。
2. **错误信息对用户友好、对攻击者脱敏**。例如登录失败提示"用户名或密码错误"（不区分用户不存在 vs 密码错），防止枚举攻击。
3. **`java.util.logging.Logger` 是唯一日志**。不能引入 Logback/Log4j。每个关键业务节点打 INFO，DAO 异常打 SEVERE，非法操作（状态机拒绝 / 权限拒绝）打 WARNING。
4. **所有时间字段用 `System.currentTimeMillis()`**，不要用 `new Date()`。实体构造器里时间字段留 0，Service 层填充。
5. **`Validator` 是唯一输入校验入口**。Service 层里禁止出现重复的长度检查 / 正则判断，统一调 Validator。

---

## 三、状态机设计模式

当业务涉及状态流转（物品状态、流转记录状态），在 Service 类里实现一个严格的状态机：

```java
public void transitionStatus(Long id, XxxStatus newStatus) {
    if (id == null || newStatus == null) throw new BusinessException("参数不能为空");
    Xxx entity = getOrThrow(id);
    XxxStatus oldStatus = entity.getStatus();

    if (!isValidTransition(oldStatus, newStatus)) {
        LOGGER.log(Level.WARNING, "非法状态流转: id={0}, {1} → {2}",
                new Object[]{id, oldStatus, newStatus});
        throw new BusinessException("非法的状态流转：" + label(oldStatus) + " → " + label(newStatus));
    }

    try {
        xxxDao.updateStatus(id, newStatus);
        LOGGER.log(Level.INFO, "XXX 状态变更: id={0}, {1} → {2}",
                new Object[]{id, oldStatus, newStatus});
    } catch (DaoException e) {
        throw new BusinessException("状态更新失败", e);
    }
}

private boolean isValidTransition(XxxStatus from, XxxStatus to) {
    if (from == to) return true;
    return switch (from) {
        case ON_SALE  -> to == RESERVED || to == OFF_SHELF;
        case RESERVED -> to == SOLD || to == ON_SALE || to == OFF_SHELF;
        case SOLD     -> false; // 终态
        case OFF_SHELF -> false; // 终态
    };
}

private static String label(XxxStatus s) {
    return switch (s) {
        case ON_SALE   -> "在售";
        case RESERVED  -> "已预订";
        case SOLD      -> "已成交";
        case OFF_SHELF -> "已下架";
    };
}
```

**设计要点：**
- `isValidTransition` 返回 `false` 后**拒绝流转**，不做静默接受
- 终态（`SOLD` / `OFF_SHELF`）必须 `false`，防止已成交/已下架物品被拉回在售
- `label` 方法用于日志和错误提示，方便调试
- 管理员的"强制下架"无视状态机限制，单独开方法（`adminTakeOffShelf`），日志级别用 `WARNING`

---

## 四、Fake DAO 实现模式（Service 层单元测试）

Service 层测试**不连接真实 SQLite**，对每个依赖 DAO 写一个静态内部类 Fake：

```java
private static class FakeUserDao implements UserDao {
    private final Map<Long, User> store = new HashMap<>();
    private long nextId = 1;

    @Override
    public User findById(Long userId) {
        return userId == null ? null : store.get(userId);
    }

    @Override
    public Long insert(User user) {
        Long id = nextId++;
        user.setUserId(id);
        store.put(id, user);
        return id;
    }

    // 其余接口方法...
}
```

### Fake DAO 实现规则

| 项 | 规则 |
|----|------|
| 存储 | `Map<Long, Entity>` 内存 Map，自增 ID 用 `nextId++` |
| 位置 | 测试文件内的 `private static class`，**不要**放到独立文件 |
| 命名 | `FakeXxxDao`（如 FakeUserDao、FakeItemDao） |
| 查询条件 | 用 Java 代码实现（循环 + `equals`），**不拼 SQL** |
| 可选功能 | 如果要测 DAO 异常传播，加 `setNextXxxException(DaoException)` 注入点 |

### 测试骨架

```java
@Before
public void setUp() {
    fakeXxxDao = new FakeXxxDao();
    fakeUserDao = new FakeUserDao();
    service = new XxxService(fakeXxxDao, fakeUserDao);

    // 预置测试用户/分类
    User u = new User("testuser", "h", "s");
    u.setRole(UserRole.USER);
    u.setCreateTime(System.currentTimeMillis());
    fakeUserDao.insert(u);
}

@Test
public void testPublish_Success() {
    // 正向路径
}

@Test
public void testPublish_UserNotExist() {
    BusinessException ex = assertThrows(BusinessException.class,
            () -> service.publish(99999L, "物品", ...));
    assertTrue(ex.getMessage().contains("用户不存在"));
}
```

### 测试覆盖清单（每个 Service 类必须覆盖）

| 类别 | 测试数占比 | 例子 |
|------|-----------|------|
| 正向路径 | ~40% | 发布/查询/修改全部正确输入 |
| 输入校验 | ~25% | 空值 / 格式错误 / 长度超限 / 价格越界 |
| 权限校验 | ~15% | 非发布者修改 / 非管理员删除 / 不能删自己 |
| 状态机 | ~10% | 合法流转 + 非法流转（终态回退、ON_SALE 跳 SOLD） |
| 异常传播 | ~10% | Fake DAO 注入 DaoException → 验证 Service 包装为 BusinessException |

---

## 五、DAO 层扩展原则

当 Service 层需要 DAO 层新能力（如组合筛选、价格区间查询）：

1. **先看 ItemDao.search() 的实现**（动态拼接 SQL + `WHERE 1=1` 模板），复用这个模式
2. **新增方法前先想是否已有方法能通过 Service 层组合实现**——Service 层多次调用 findXxx + Java 内存过滤也行，但 DAO 层动态 SQL 一次查完更高效
3. **永远加 PreparedStatement 参数绑定**，禁止字符串拼接 SQL
4. **写完 DAO 扩展后**，跑一次 `ItemDaoTest` + `UserDaoTest` 确认接口变更没破坏旧测试

---

## 六、DAO 层手动运行 classpath 配置（无 Maven 时）

项目 Maven 位于 `D:\软件构造\tools\maven-3.9.16\bin\mvn.cmd`，Java 21 位于 `C:\Program Files\BellSoft\LibericaJDK-21-Full\bin\`。

手动运行 DAO 层真实 SQLite 测试需要 7 个 jar：

```
sqlite-jdbc-3.45.1.0.jar    → org.sqlite.JDBC
slf4j-api-1.7.36.jar       → sqlite-jdbc 运行时依赖
slf4j-simple-2.0.12.jar    → slf4j 的简单实现
junit-4.13.2.jar
hamcrest-core-1.3.jar
target\classes              → 主代码
target\test-classes         → 测试代码
```

任何时候 `ClassNotFoundException: org/slf4j/LoggerFactory` 都是漏了 slf4j 那两个 jar，加上就行。

---

## 七、提交规范

每个 Service 类完成后，Git 提交信息格式：

```
T3-{N} {模块}模块Service：XxxService + Fake DAO 单元测试
```

示例：`T3-3 流转与评价模块Service：TransactionService/ReviewService`
