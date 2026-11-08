package com.lowcarbon;

import com.lowcarbon.config.DatabaseManager;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;

/**
 * 低碳校园资源循环流转桌面系统 - 程序入口。
 * <p>
 * 启动流程：数据库初始化 →（后续）MainFrame GUI 启动。
 *
 * @author lowcarbon-team
 * @since 2026-11-08
 */
public final class Main {

    private Main() {
        // 禁止实例化
    }

    /**
     * 程序入口。
     *
     * @param args 命令行参数（当前未使用）
     */
    public static void main(String[] args) {
        System.out.println("========================================");
        System.out.println("  低碳校园资源循环流转桌面系统 v1.0.0");
        System.out.println("  LowCarbon Campus System");
        System.out.println("========================================");
        System.out.println("JDK: " + System.getProperty("java.version"));
        System.out.println("编码: " + System.getProperty("file.encoding"));
        System.out.println("工作目录: " + System.getProperty("user.dir"));

        // --- 数据库初始化 ---
        System.out.println("\n[DB] 正在初始化数据库...");
        DatabaseManager dbm = DatabaseManager.getInstance();
        System.out.println("[DB] 数据库文件: " + dbm.getDbPath());

        try (Connection conn = dbm.getConnection();
             Statement stmt = conn.createStatement()) {

            // 验证表是否存在
            ResultSet rs = stmt.executeQuery(
                    "SELECT name FROM sqlite_master WHERE type='table' " +
                    "AND name IN ('user','category','item','transaction_record','review')");
            int tableCount = 0;
            StringBuilder tables = new StringBuilder();
            while (rs.next()) {
                tables.append(rs.getString("name")).append("  ");
                tableCount++;
            }
            System.out.println("[DB] 表就绪（共 " + tableCount + " 张）: " + tables);

            // 验证预置分类
            rs = stmt.executeQuery("SELECT COUNT(*) FROM category");
            if (rs.next()) {
                System.out.println("[DB] 预置分类数: " + rs.getInt(1));
            }

            System.out.println("\n✅ 系统启动成功（数据层就绪）");

        } catch (Exception e) {
            System.err.println("\n❌ 数据库验证失败: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
