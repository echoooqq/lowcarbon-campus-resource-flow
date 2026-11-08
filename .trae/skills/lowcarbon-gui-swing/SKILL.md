---
name: lowcarbon-gui-swing
description: Build Swing GUI panels for lowcarbon-system. Use when implementing T4-x GUI tasks like login register item browse publish manage flow review stats or admin. Do not use for DAO or Service layer work.
---

# LowCarbon Swing GUI 开发工作流

## 适用范围

为 lowcarbon-system 低碳校园桌面系统编写 Swing GUI 面板（`gui/panel/`），并接入 `MainFrame` 的 CardLayout 外壳。触发词：T4-1 ~ T4-6、登录注册、物品大厅、发布物品、我的物品、流转、评价、统计、管理员后台。

## 前置检查清单

开始写任何代码前，按顺序读完这 4 个文件：

1. `AGENTS.md` — 项目技术栈、目录结构、命名约定、设计模式规划、交付标准（所有 GUI 代码必须遵守）
2. `src/main/java/com/lowcarbon/gui/MainFrame.java` — 现有卡片名、侧边导航、已有面板引用，避免重名或漏接
3. 目标面板依赖的 Service 类（如 T4-2 读 `ItemService.java`）— 确认可用的查询/写入 API，以及哪些参数需要 GUI 传
4. `src/main/java/com/lowcarbon/util/Validator.java` — 事件层预校验只能复用这里的规则，不能在 Panel 里写一套新的校验

## 标准开发步骤（三次 T4-x 均按此完成）

### Step 1 — 创建 Panel 类（`gui/panel/XxxPanel.java`）

**构造器**
- 入参只放 `MainFrame`（用于导航回调）和必要的 Service 实例
- 布局：GridBagLayout 用于表单、BorderLayout 用于表格+按钮组合、BoxLayout 用于垂直按钮栈
- 所有文本为中文

**异步加载**
- 所有涉及 JDBC 的操作（DAO 查询、Service 调用）必须放在 `SwingWorker.doInBackground()` 里，禁止直接在事件监听里调用
- 长时间运行期间禁用相关按钮，防止重复提交（`submitButton.setEnabled(false)`）

**事件层校验（仅做快速非业务规则校验）**
- 非空检查、格式正则、危险字符（`Validator.containsDangerousChars`）、价格数字解析
- 业务规则校验（密码长度、状态流转合法性、发布者归属）**一律下沉到 Service 层**
- 弹窗用 `JOptionPane.showMessageDialog(this, msg, title, JOptionPane.WARNING_MESSAGE)`
- 键盘回车触发主操作：密码框回车登录、搜索框回车查询、确认密码框回车提交注册

**异常处理**
- `done()` 里先 `get()`，catch `getCause()` 还原 Service 层抛出的 `BusinessException`，友好提示其 message
- 非 BusinessException 的一律记 SEVERE 日志 + 显示"系统异常请稍后再试"
- 示例（每次都写）：
  ```java
  try { get(); ok }
  catch (Exception e) {
    Throwable cause = e.getCause() != null ? e.getCause() : e;
    if (cause instanceof BusinessException) {
      JOptionPane.showMessageDialog(this, cause.getMessage(), title, JOptionPane.WARNING_MESSAGE);
    } else {
      LOGGER.log(Level.SEVERE, title, e);
      JOptionPane.showMessageDialog(this, "系统异常，请稍后再试", "错误", JOptionPane.ERROR_MESSAGE);
    }
  }
  ```

**表格**
- `DefaultTableModel` 子类覆写 `isCellEditable` 恒 false（只读）
- `ListSelectionModel.SINGLE_SELECTION`
- 双击行或按钮触发详情查看
- 关联信息（如发布者用户名）从其他 Service 异步查询，缓存防止 N+1

### Step 2 — 接入 MainFrame 外壳

**卡片注册**
- 在 MainFrame 里声明卡片名常量：`public static final String CONTENT_XXX = "XXX";`
- `buildMainShell()` 里 `contentPanel.add(new XxxPanel(this), CONTENT_XXX);`
- 每个登录态重建外壳，新实例保证数据新鲜

**侧边导航**
- 复用 `createNavButton(text)` 创建导航按钮
- 每个导航按钮绑定 `e -> showXxx()` 调用对应公共方法

**导航方法约定**
```java
public void showXxx() {
  if (contentLayout == null || xxxPanel == null) return;
  contentLayout.show(contentPanel, CONTENT_XXX);
  xxxPanel.refresh(); // 若面板有数据刷新需求
}
```
- 离开需要清理状态的面板（如编辑中）时调用 `xxxPanel.exitEditMode()`

### Step 3 — 冒烟验证

- 在 `target/smoke/` 创建临时 harness（运行后删除）
- 流程：`DatabaseManager.getInstance()` → Service 造数据 → `MainFrame` 启动 → `onLoginSuccess(user)` → 自动导航到目标面板 → 运行 10-15 秒无异常
- 外部脚本启动 Java 进程，等待足够时间让 EDT 中所有 `SwingWorker` 跑完，然后检查 stderr 里的 Exception

### Step 4 — 编译 + 测试 + 提交

**编译**：必须设置 `JAVA_HOME=<项目根>/tools/jdk-21` 否则 surefire 会用系统默认的 Java 8 分叉失败
```powershell
$env:JAVA_HOME = "d:\软件构造\tools\jdk-21"
& "d:\软件构造\tools\maven-3.9.16\bin\mvn.cmd" clean compile
```

**测试**：`mvn test` 应全部通过，新增 Service API 对应的单元测试（Fake DAO）

**提交**：Git commit 遵循 `AGENTS.md` 第七节格式 — `T阶段-编号 任务名：简述`

## 必须避免的陷阱

| 陷阱 | 现象 | 根治方法 |
|------|------|----------|
| **匿名 SwingWorker 引用非 effectively final 变量** | javac 报"无法在静态上下文中引用非静态变量 x" | 进入 SwingWorker 前对所有用到的局部变量做 final 声明；通过中间变量 + 三元/构造表达式赋值 |
| **clearForm 与 exitEditMode 互相调用导致 StackOverflowError** | EDT 中栈溢出 | clearForm 只负责清空字段；exitEditMode 只负责切换按钮文字和 null 化状态，两者零依赖 |
| **mvn surefire 用系统默认 Java 8 分叉** | `class file version 65.0, this version recognizes up to 52.0` | 每次跑 mvn 测试前设置 JAVA_HOME 到 `tools/jdk-21` |
| **Panel 之间直接通信** | MainFrame 导航断掉某面板依赖 | 面板之间零直接引用，全部通过 MainFrame 的公共导航方法交互 |
| **GUI 层做业务规则校验** | Service 层重复校验，规则分散 | 事件层只做参数格式校验，业务规则（状态流转、发布者归属、密码策略）一律下沉 Service |
| **JDBC 在 EDT 线程同步调用** | 表格卡顿、Swing 无响应 | 所有 Service 调用包进 `SwingWorker.doInBackground()` |
| **方法声明返回类型与实际 `return` 值类型不一致** | `javac 类型不兼容: JScrollPane 无法转换为 JPanel` | 构建 `new JScrollPane(root)` 包裹面板后返回值就是 JScrollPane，方法签名必须同步声明为 `private JScrollPane xxxTab()`，不能还是 `JPanel`；同理其他 Swing 包装组件 |

## 项目特定约定速查

- **构建**：Maven 3.9.16 自带在 `tools/maven-3.9.16`，JDK 21 自带在 `tools/jdk-21`
- **数据库**：SQLite，文件 `lowcarbon.db`，启动时 `DatabaseManager` 自动建表
- **日志**：JDK 自带 `java.util.logging.Logger`，不引入 Logback/Log4j
- **异常**：Service 层抛 `BusinessException`（业务错误）或包装 `DaoException`
- **密码**：`PasswordUtil.hashPassword(raw, salt)` + `verifyPassword`，GUI 层不关心细节
- **信用分**：默认 100，范围 0 ~ 200，`Validator.MIN_CREDIT_SCORE / MAX_CREDIT_SCORE`
- **设计模式**：单例（DatabaseManager）、工厂（DaoFactory 后续）、策略、观察者 —— GUI 层主要用到 CardLayout 导航模式
