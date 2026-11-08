# AGENTS.md — 低碳校园资源循环流转桌面系统 项目规范

> **用途**：本文件是项目所有开发任务的"宪法"，后续任何代码/文档产出必须遵守本文件的约定。
> **参考依据**：`2026软件构造分组实验v3.docx`（《软件构造》分组实验要求）
> **最后更新**：2026-11-08

---

## 一、项目概述

| 项 | 值 |
|----|----|
| 项目名称 | 低碳校园资源循环流转桌面系统（LowCarbon Campus System） |
| 课程 | 《软件构造》软件工程专业课程分组实验 |
| 开发周期 | 8 周（当前已过部分时间，一人赶工） |
| 交付要求 | 可部署 Jar 包 + 数据库脚本 + 完整实验报告 + JUnit 测试用例 |
| 强制覆盖 | 模块化、面向对象、数据持久化、GUI、重构、数据库编程、设计模式（≥3种）、JUnit、打包交付 |

### 禁止事项

- ❌ 禁止仅做简单 CRUD，必须体现防御性编程、异常处理、日志、静态分析
- ❌ 禁止跳过 UML 建模、JUnit 测试、代码重构
- ❌ 禁止不使用设计模式（文档强制 ≥3 种）
- ❌ 禁止偏离本文件定义的技术栈和规范

---

## 二、技术栈锁定

| 分类 | 技术 | 版本 | 说明 |
|------|------|------|------|
| 语言 | Java | 21 LTS | 使用 `java.util.*` 标准库 |
| 构建 | Maven | 3.9+ | 标准目录结构 |
| GUI | Swing + WindowBuilder | — | 事件驱动，不依赖第三方 GUI 库 |
| 数据库 | SQLite | 3.x | 嵌入式，使用 `org.xerial:sqlite-jdbc` |
| 测试 | JUnit | 4.13.2 | 单元测试 + 集成测试 |
| 加密 | JDK 自带 `MessageDigest` | — | 密码 SHA-256 加盐存储 |
| 日志 | JDK 自带 `java.util.logging` | — | 不引入 Logback/Log4j，保持轻量 |

### Maven 依赖清单

```xml
<dependencies>
    <!-- SQLite JDBC -->
    <dependency>
        <groupId>org.xerial</groupId>
        <artifactId>sqlite-jdbc</artifactId>
        <version>3.45.1.0</version>
    </dependency>
    <!-- JUnit 4 -->
    <dependency>
        <groupId>junit</groupId>
        <artifactId>junit</artifactId>
        <version>4.13.2</version>
        <scope>test</scope>
    </dependency>
</dependencies>
```

---

## 三、目录结构

```
lowcarbon-system/
├── pom.xml
├── AGENTS.md
├── schema.sql                          # 数据库初始化脚本
├── run.bat                             # Windows 启动脚本（可选）
├── docs/
│   ├── 需求规格说明书.md
│   ├── UML类图.puml                    # PlantUML 源码
│   ├── 时序图.puml                     # PlantUML 源码（3 张核心流程）
│   ├── 数据库设计.md
│   ├── 模块化划分.md
│   ├── 设计模式应用说明.md
│   └── 课程设计报告.md
├── src/
│   ├── main/java/com/lowcarbon/
│   │   ├── Main.java                  # 程序入口
│   │   ├── config/
│   │   │   └── DatabaseManager.java   # 单例数据库连接管理
│   │   ├── entity/                     # 实体类（POJO）
│   │   │   ├── User.java
│   │   │   ├── Item.java
│   │   │   ├── Category.java
│   │   │   ├── Transaction.java
│   │   │   └── Review.java
│   │   ├── dao/                        # 数据访问层（接口 + JDBC 实现）
│   │   │   ├── UserDao.java
│   │   │   ├── impl/UserDaoImpl.java
│   │   │   ├── ItemDao.java
│   │   │   ├── impl/ItemDaoImpl.java
│   │   │   ├── CategoryDao.java
│   │   │   ├── impl/CategoryDaoImpl.java
│   │   │   ├── TransactionDao.java
│   │   │   ├── impl/TransactionDaoImpl.java
│   │   │   ├── ReviewDao.java
│   │   │   └── impl/ReviewDaoImpl.java
│   │   ├── service/                    # 业务逻辑层
│   │   │   ├── UserService.java
│   │   │   ├── ItemService.java
│   │   │   ├── TransactionService.java
│   │   │   ├── ReviewService.java
│   │   │   ├── StatisticsService.java
│   │   │   └── AdminService.java
│   │   ├── gui/                        # GUI 界面层
│   │   │   ├── MainFrame.java
│   │   │   ├── panel/
│   │   │   │   ├── LoginPanel.java
│   │   │   │   ├── RegisterPanel.java
│   │   │   │   ├── ItemListPanel.java
│   │   │   │   ├── ItemPublishPanel.java
│   │   │   │   ├── ItemManagePanel.java
│   │   │   │   ├── TransactionPanel.java
│   │   │   │   ├── ReviewPanel.java
│   │   │   │   ├── StatisticsPanel.java
│   │   │   │   └── AdminPanel.java
│   │   ├── util/                       # 工具类
│   │   │   ├── PasswordUtil.java       # SHA-256 加盐加密
│   │   │   ├── CsvExporter.java        # CSV 导出
│   │   │   └── Validator.java          # 输入合法性校验
│   │   └── exception/                  # 自定义异常
│   │       ├── BusinessException.java
│   │       └── DaoException.java
│   └── test/java/com/lowcarbon/
│       ├── dao/                        # DAO 层单元测试
│       ├── service/                    # Service 层单元测试
│       └── integration/                # 集成测试
└── target/
```

---

## 四、代码风格与命名约定

### 通用规则

| 项 | 规则 |
|----|------|
| 缩进 | 4 空格，不使用 Tab |
| 编码 | UTF-8 |
| 换行符 | LF（Unix 风格） |
| 每行最大长度 | 120 字符 |
| 花括号 | K&R 风格（左花括号不换行） |
| 空行 | 方法之间 1 空行，逻辑块之间 1 空行 |

### 命名规范

| 类型 | 规范 | 示例 |
|------|------|------|
| 包名 | 全小写，点分隔 | `com.lowcarbon.dao.impl` |
| 类名 | UpperCamelCase | `UserService`, `DatabaseManager` |
| 接口 | 同类名（不加 I 前缀） | `UserDao`, `ItemService` |
| 方法 | lowerCamelCase，动词开头 | `findById()`, `calculateCarbonSaved()` |
| 变量 | lowerCamelCase | `userId`, `itemList` |
| 常量 | UPPER_SNAKE_CASE | `MAX_PASSWORD_LENGTH`, `DB_URL` |
| 数据库表 | lower_snake_case | `user`, `item`, `transaction_record` |
| 数据库字段 | lower_snake_case | `user_id`, `publish_time` |

### 注释规范

- 类必须有 Javadoc（类职责说明、作者、创建日期）
- public 方法必须有 Javadoc（参数、返回值、异常）
- 复杂逻辑行内加 `//` 注释
- 不写无意义注释（`i++ // i加1`）

### 防御性编程要求

- 所有 DAO 方法捕获 `SQLException`，包装为 `DaoException` 向上抛出
- 所有 Service 方法捕获异常，记录日志，向上抛出 `BusinessException` 或返回友好错误信息
- 所有用户输入必须经过 `Validator` 校验（长度、格式、空值、SQL 注入防护）
- 数据库操作使用 **PreparedStatement**，**绝对禁止**拼接 SQL 字符串
- 关键资源（Connection、ResultSet、PreparedStatement）使用 try-with-resources 自动关闭

---

## 五、设计模式使用规划

> **文档强制要求至少 3 种设计模式**，以下是提前规划好的落地位置，后续按此执行。

| 模式 | 应用位置 | 目的 |
|------|----------|------|
| **单例（Singleton）** | `DatabaseManager` | 全局唯一数据库连接管理器，避免重复创建连接 |
| **工厂（Factory）** | `DaoFactory`（后续 T5-1 引入） | 集中创建 DAO 实例，解耦 Service 与 DAO 具体实现 |
| **策略（Strategy）** | 物品筛选功能（`ItemService` 的筛选逻辑） | 支持按分类/价格/关键词多种筛选方式，可扩展 |
| **观察者（Observer）** | 流转状态变更通知 | 物品状态变更时通知关心的模块（如信用分更新、日志记录） |

> 实际落地时可根据代码结构微调，但最终必须确保 **≥3 种**设计模式有实际代码应用 + 文档说明。

---

## 六、数据库 Schema 约定

### 数据库位置

- 开发阶段：`./lowcarbon.db`（项目根目录）
- 打包后：Jar 同级目录

### 表清单（共 5 张表）

| 表名 | 说明 | 关键字段 |
|------|------|----------|
| `user` | 用户表 | user_id (PK), username (UNIQUE), password_hash, salt, role, credit_score, create_time |
| `item` | 闲置物品表 | item_id (PK), publisher_id (FK→user), name, category_id (FK→category), description, price, trade_type, status, tags, publish_time |
| `category` | 物品分类表 | category_id (PK), name, description |
| `transaction_record` | 流转记录表 | record_id (PK), item_id (FK→item), buyer_id (FK→user), status, create_time, complete_time |
| `review` | 评价表 | review_id (PK), record_id (FK→transaction_record), reviewer_id (FK→user), rating, comment, create_time |

### 通用字段约束

- 所有时间字段存 **Unix 时间戳（毫秒）**，Java 侧用 `System.currentTimeMillis()`
- 所有字符串字段 **非空时 NOT NULL**，可选字段允许 NULL
- 密码存储为 `SHA256(原文 + 盐值)`，盐值随机生成并存 `user` 表
- 所有表必须包含 `create_time` 字段

---

## 七、提交与版本管理规范

> 虽然一人开发，但仍需体现文档要求的"代码库提交记录"。

| 项 | 规则 |
|----|------|
| 版本控制 | Git |
| 主分支 | `main` |
| 提交粒度 | 每个任务完成后提交一次 |
| 提交信息格式 | `T{阶段}-{编号} {任务名}：简述` |
| 示例 | `T0-2 设计文档：完成需求规格、类图、时序图、数据库设计` |

---

## 八、任务编号体系

> 完整任务清单见项目执行方案（22 个任务）。以下是快速索引：

### Phase 0：紧急准备
- **T0-1** → 创建 AGENTS.md 规范（当前任务 ✅）
- **T0-2** → 设计文档快速产出（需求+类图+时序图+数据库+模块划分）

### Phase 1：项目骨架
- **T1-1** → Maven 项目初始化
- **T1-2** → SQLite 数据库初始化

### Phase 2：数据层
- **T2-1** → 全部实体类
- **T2-2** → DAO 接口 + 实现
- **T2-3** → 数据层单元测试

### Phase 3：业务逻辑层
- **T3-1** → 用户模块业务
- **T3-2** → 物品模块业务
- **T3-3** → 流转与评价模块
- **T3-4** → 统计模块业务
- **T3-5** → 管理员后台业务

### Phase 4：GUI 开发
- **T4-1** → 主窗口 + 登录注册
- **T4-2** → 物品浏览与搜索
- **T4-3** → 物品发布与管理
- **T4-4** → 流转记录与评价
- **T4-5** → 统计报表界面
- **T4-6** → 管理员后台界面

### Phase 5：设计模式 + 重构
- **T5-1** → 引入至少 3 种设计模式
- **T5-2** → 代码坏味道消除

### Phase 6：集成 + 交付
- **T6-1** → 系统集成 + 集成测试
- **T6-2** → 打包可执行 Jar
- **T6-3** → 部署说明文档
- **T6-4** → 课程设计报告

---

## 九、每个任务的验收标准

每个任务完成后，必须满足：

1. **代码可编译**：`mvn compile` 无错误
2. **测试可运行**：`mvn test` 通过（如果该任务涉及测试）
3. **符合规范**：代码风格、命名、目录结构符合本文件第三、四节
4. **无硬编码 SQL**：所有数据库操作用 PreparedStatement
5. **有异常处理**：DAO 层、Service 层有明确的异常捕获和日志记录
6. **文档同步**：如果任务影响设计，同步更新 `docs/` 下对应文档

---

## 十、交付物清单（最终必须全部提交）

> 来自作业要求文档第七节

| 序号 | 交付物 | 对应任务 |
|------|--------|----------|
| 1 | 课程设计报告 | T6-4 |
| 2 | 完整源代码工程（Maven 项目） | Phase 1-5 全部 |
| 3 | JUnit 全部单元测试用例 | T2-3 + Phase 3 各 Service 测试 |
| 4 | SQLite 数据库初始化脚本 | T1-2 |
| 5 | 可直接部署运行的 Jar 包 | T6-2 |
| 6 | 用户部署使用说明书 | T6-3 |
| 7 | 小组工作记录（代码提交截图、分工说明） | 手动整理 |
| 8 | 演示 PPT 和视频 | 手动制作 |

---

## 十一、课程大纲覆盖检查点

> 每完成一个 Phase 后，对照此表检查是否覆盖。

| 章节 | 检查点 | 对应任务 |
|------|--------|----------|
| 第一章 软件开发概述 | 需求分析、项目计划、迭代开发理解 | T0-2 |
| 第二章 模块化软件构造 | 模块分解、接口划分、编码规范 | T0-2 + Phase 1-3 |
| 第三章 面向对象软件构造 | 抽象封装、ADT、UML 类图、继承多态 | T0-2 + T2-1 |
| 第四章 数据处理的软件构造 | 文件 IO、CSV 导出、防御性编程、异常处理 | T3-4 + 全局 |
| 第五章 用户交互的软件构造 | 菜单交互、模块集成、集成测试、代码走查 | Phase 4 + T6-1 |
| 第六章 软件重构与交付 | 坏味道识别、重构、打包交付 | Phase 5 + T6-2 |
| 第七章 GUI 软件构造 | 事件驱动、Swing/WindowBuilder | Phase 4 |
| 第八章 应用数据库 | SQLite、JDBC、CRUD、事务 | T1-2 + T2-2 |
| 第九章 基于复用的软件构造 | ≥3 种设计模式、框架复用 | T5-1 |

---

> **记住**：本文件是所有任务的约束条件。任何时候产出物与本文件冲突，以本文件为准，除非你显式要求修改。
