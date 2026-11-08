package com.lowcarbon;

import com.lowcarbon.config.DatabaseManager;
import com.lowcarbon.gui.MainFrame;

import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * 低碳校园资源循环流转桌面系统 - 程序入口。
 * <p>
 * 启动流程：
 * <ol>
 *   <li>初始化数据库（建表 / 校验表结构），失败则弹窗提示并退出</li>
 *   <li>在事件派发线程（EDT）中启动 {@link MainFrame} 主窗口</li>
 * </ol>
 *
 * @author lowcarbon-team
 * @since 2026-11-08
 */
public final class Main {

    private static final Logger LOGGER = Logger.getLogger(Main.class.getName());

    private Main() {
        // 禁止实例化
    }

    /**
     * 程序入口。
     *
     * @param args 命令行参数（当前未使用）
     */
    public static void main(String[] args) {
        // --- 数据库初始化（GUI 启动前完成，失败快速退出） ---
        try {
            DatabaseManager dbm = DatabaseManager.getInstance();
            LOGGER.info("数据库就绪: " + dbm.getDbPath());
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "数据库初始化失败", e);
            JOptionPane.showMessageDialog(null,
                    "数据库初始化失败，系统无法启动：\n" + e.getMessage(),
                    "启动错误", JOptionPane.ERROR_MESSAGE);
            System.exit(1);
        }

        // --- GUI 启动（Swing 组件必须在 EDT 中创建） ---
        SwingUtilities.invokeLater(() -> {
            setSystemLookAndFeel();
            new MainFrame().setVisible(true);
            LOGGER.info("主窗口启动完成");
        });
    }

    /**
     * 设置系统原生外观，失败时回退到默认外观（不影响启动）。
     */
    private static void setSystemLookAndFeel() {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception e) {
            LOGGER.warning("设置系统外观失败，使用默认外观: " + e.getMessage());
        }
    }
}
