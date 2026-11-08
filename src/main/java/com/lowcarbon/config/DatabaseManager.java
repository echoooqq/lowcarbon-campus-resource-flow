package com.lowcarbon.config;

import com.lowcarbon.exception.DaoException;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * SQLite 数据库连接管理器（单例模式）。
 * <p>
 * 职责：
 * <ol>
 *   <li>管理全局唯一的 JDBC Connection 生命周期</li>
 *   <li>程序启动时自动创建数据库文件并执行 schema.sql 建表</li>
 *   <li>每次新建 Connection 时开启外键检查</li>
 * </ol>
 *
 * <h3>数据库文件位置</h3>
 * <ul>
 *   <li>默认：{@code ./lowcarbon.db}（Jar 同级目录或项目根目录）</li>
 *   <li>可通过系统属性 {@code lowcarbon.db.path} 覆盖</li>
 * </ul>
 *
 * <h3>使用约定</h3>
 * DAO 层在 try-with-resources 中获取 Connection，用完即释放：
 * <pre>{@code
 * try (Connection conn = DatabaseManager.getInstance().getConnection()) {
 *     // 使用 PreparedStatement 操作数据库
 * }
 * }</pre>
 *
 * @author lowcarbon-team
 * @since 2026-11-08
 */
public final class DatabaseManager {

    private static final Logger LOGGER = Logger.getLogger(DatabaseManager.class.getName());

    /** 默认数据库文件名。 */
    private static final String DEFAULT_DB_FILE = "lowcarbon.db";

    /** schema SQL 脚本文件（优先从文件系统读取，fallback 到 classpath）。 */
    private static final String SCHEMA_FILE = "schema.sql";

    /** JDBC URL 模板：SQLite 嵌入式数据库。 */
    private static final String JDBC_URL_TEMPLATE = "jdbc:sqlite:%s?foreign_keys=on";

    /** 系统属性名，用于覆盖数据库文件路径。 */
    private static final String DB_PATH_PROPERTY = "lowcarbon.db.path";

    // ==================== 单例 ====================

    /** 懒加载单例（DCL）。 */
    private static volatile DatabaseManager instance;

    /** 数据库文件绝对路径。 */
    private final Path dbPath;

    /** JDBC URL（缓存，不包含运行时参数）。 */
    private final String jdbcUrl;

    /**
     * 私有构造器：计算数据库路径 + 自动初始化。
     */
    private DatabaseManager() {
        this.dbPath = resolveDbPath();
        this.jdbcUrl = String.format(JDBC_URL_TEMPLATE, dbPath.toAbsolutePath().toString());
        initializeDatabase();
    }

    /**
     * 获取单例实例。
     *
     * @return DatabaseManager 唯一实例
     */
    public static DatabaseManager getInstance() {
        if (instance == null) {
            synchronized (DatabaseManager.class) {
                if (instance == null) {
                    instance = new DatabaseManager();
                }
            }
        }
        return instance;
    }

    // ==================== 路径解析 ====================

    /**
     * 解析数据库文件的绝对路径。
     * <p>
     * 优先级：
     * <ol>
     *   <li>系统属性 {@code lowcarbon.db.path}</li>
     *   <li>当前工作目录下的 {@code lowcarbon.db}</li>
     * </ol>
     *
     * @return 数据库文件 Path
     */
    private Path resolveDbPath() {
        String override = System.getProperty(DB_PATH_PROPERTY);
        if (override != null && !override.isBlank()) {
            Path p = Paths.get(override);
            LOGGER.info("使用系统属性指定的数据库路径: " + p.toAbsolutePath());
            return p;
        }
        Path defaultPath = Paths.get(DEFAULT_DB_FILE);
        LOGGER.info("数据库文件位置: " + defaultPath.toAbsolutePath());
        return defaultPath;
    }

    // ==================== 初始化建表 ====================

    /**
     * 程序启动时自动执行：若数据库文件不存在或表不全，则执行 schema.sql。
     */
    private void initializeDatabase() {
        // 确保父目录存在（相对路径时父目录即当前目录，无需处理；绝对路径时需创建）
        Path parent = dbPath.toAbsolutePath().getParent();
        if (parent != null && !Files.exists(parent)) {
            try {
                Files.createDirectories(parent);
            } catch (IOException e) {
                throw new DaoException("无法创建数据库目录: " + parent, e);
            }
        }

        boolean isNewDb = !Files.exists(dbPath);
        if (isNewDb) {
            LOGGER.info("首次运行，创建数据库: " + dbPath.toAbsolutePath());
        } else {
            LOGGER.info("数据库已存在，检查表结构完整性...");
        }

        // 执行 schema.sql（CREATE TABLE IF NOT EXISTS 保证幂等）
        List<String> statements = loadSchemaStatements();
        try (Connection conn = DriverManager.getConnection(jdbcUrl);
             Statement stmt = conn.createStatement()) {

            // SQLite JDBC 开启外键（URL 参数或显式执行均可，这里双保险）
            stmt.execute("PRAGMA foreign_keys = ON");

            for (String sql : statements) {
                if (!sql.isBlank() && !sql.startsWith("--")) {
                    stmt.execute(sql);
                }
            }
            LOGGER.info("数据库初始化完成。表结构已就绪。");

        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "数据库初始化失败！", e);
            throw new DaoException("数据库初始化失败: " + e.getMessage(), e);
        }
    }

    /**
     * 加载 schema.sql 并按分号分割为独立 SQL 语句。
     * <p>
     * 读取优先级：
     * <ol>
     *   <li>文件系统：项目根目录 / Jar 同级目录下的 schema.sql</li>
     *   <li>classpath：如果打了 fat-jar，schema.sql 也可以放入 resources</li>
     * </ol>
     *
     * @return SQL 语句列表（已去除空行和注释）
     */
    private List<String> loadSchemaStatements() {
        String content = null;

        // 1. 尝试从文件系统读取（开发阶段和部署阶段都适用）
        Path schemaOnDisk = dbPath.toAbsolutePath().getParent().resolve(SCHEMA_FILE);
        if (Files.exists(schemaOnDisk)) {
            try {
                content = Files.readString(schemaOnDisk, StandardCharsets.UTF_8);
                LOGGER.info("从文件系统加载 schema.sql: " + schemaOnDisk.toAbsolutePath());
            } catch (IOException e) {
                LOGGER.warning("读取文件系统 schema.sql 失败，尝试 classpath: " + e.getMessage());
            }
        }

        // 2. Fallback：从 classpath 读取
        if (content == null) {
            try (InputStream in = getClass().getClassLoader().getResourceAsStream(SCHEMA_FILE)) {
                if (in != null) {
                    try (BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
                        content = reader.lines().collect(Collectors.joining("\n"));
                    }
                    LOGGER.info("从 classpath 加载 schema.sql");
                } else {
                    LOGGER.severe("未找到 schema.sql！请确保项目根目录存在该文件。");
                    throw new DaoException("schema.sql 文件未找到");
                }
            } catch (IOException e) {
                throw new DaoException("读取 schema.sql 失败", e);
            }
        }

        // 3. 按分号分割，去除注释和空块
        return splitAndClean(content);
    }

    /**
     * 按分号分割 SQL 脚本，去除单行注释（-- ...）、多行注释（/* ... *&#47;）、空语句。
     *
     * @param raw 原始 SQL 脚本
     * @return 清洗后的独立语句列表
     */
    private List<String> splitAndClean(String raw) {
        // 先去掉多行注释
        String cleaned = raw.replaceAll("/\\*.*?\\*/", "");
        // 按分号分割（注意字符串内的分号不会被误分，schema.sql 里没有字符串字面量）
        String[] parts = cleaned.split(";");
        List<String> result = new ArrayList<>();
        for (String part : parts) {
            // 去掉单行注释
            String stmt = part.replaceAll("--[^\n]*", "").trim();
            if (!stmt.isEmpty()) {
                result.add(stmt);
            }
        }
        return result;
    }

    // ==================== 对外接口 ====================

    /**
     * 获取一个新的 JDBC Connection（每次调用返回新实例）。
     * <p>
     * 调用方必须在 try-with-resources 中使用，用完即关：
     * <pre>{@code
     * try (Connection conn = DatabaseManager.getInstance().getConnection()) { ... }
     * }</pre>
     *
     * @return 已开启外键检查的 Connection
     * @throws DaoException 若获取连接失败
     */
    public Connection getConnection() {
        try {
            Connection conn = DriverManager.getConnection(jdbcUrl);
            // 双保险开启外键
            try (Statement stmt = conn.createStatement()) {
                stmt.execute("PRAGMA foreign_keys = ON");
            }
            return conn;
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "获取数据库连接失败", e);
            throw new DaoException("获取数据库连接失败: " + e.getMessage(), e);
        }
    }

    /**
     * 返回数据库文件路径（仅用于日志 / 调试）。
     *
     * @return 数据库文件的绝对路径
     */
    public Path getDbPath() {
        return dbPath.toAbsolutePath();
    }
}
