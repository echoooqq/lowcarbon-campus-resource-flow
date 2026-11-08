package com.lowcarbon;

/**
 * 低碳校园资源循环流转桌面系统 - 程序入口。
 * <p>
 * 暂时只打印一条启动信息，待 GUI 模块（T4-1）完成后替换为 MainFrame 启动。
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
    }
}
