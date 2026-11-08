package com.lowcarbon.gui;

import com.lowcarbon.entity.User;
import com.lowcarbon.gui.panel.LoginPanel;
import com.lowcarbon.gui.panel.RegisterPanel;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridLayout;

/**
 * 系统主窗口（单窗口 + CardLayout 面板切换）。
 * <p>
 * 职责：
 * <ul>
 *   <li>作为所有业务面板的容器，通过 {@link CardLayout} 管理面板切换</li>
 *   <li>持有当前登录用户 {@link #currentUser}，登录成功后切换到主界面</li>
 *   <li>提供登出入口，登出后清空登录态并返回登录界面</li>
 * </ul>
 * <p>
 * 面板解耦约定：各 Panel 只持有 MainFrame 引用进行导航，面板之间不直接通信。
 *
 * @author lowcarbon-team
 * @since 2026-11-09
 */
public class MainFrame extends JFrame {

    /** 登录面板卡片名。 */
    public static final String CARD_LOGIN = "LOGIN";

    /** 注册面板卡片名。 */
    public static final String CARD_REGISTER = "REGISTER";

    /** 登录后主界面卡片名。 */
    public static final String CARD_HOME = "HOME";

    private static final String WINDOW_TITLE = "低碳校园资源循环流转桌面系统 v1.0.0";
    private static final int WINDOW_WIDTH = 960;
    private static final int WINDOW_HEIGHT = 640;

    private final CardLayout cardLayout;
    private final JPanel cardPanel;

    /** 当前登录用户；未登录时为 {@code null}。 */
    private User currentUser;

    /**
     * 构造主窗口：初始化窗口属性并注册登录 / 注册卡片。
     */
    public MainFrame() {
        super(WINDOW_TITLE);
        cardLayout = new CardLayout();
        cardPanel = new JPanel(cardLayout);

        // 注册登录 / 注册卡片（主界面卡片在登录成功后动态构建）
        cardPanel.add(new LoginPanel(this), CARD_LOGIN);
        cardPanel.add(new RegisterPanel(this), CARD_REGISTER);

        setContentPane(cardPanel);
        setSize(WINDOW_WIDTH, WINDOW_HEIGHT);
        setMinimumSize(getSize());
        setLocationRelativeTo(null);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

        showLogin();
    }

    // ==================== 导航 ====================

    /**
     * 切换到登录界面。
     */
    public void showLogin() {
        cardLayout.show(cardPanel, CARD_LOGIN);
    }

    /**
     * 切换到注册界面。
     */
    public void showRegister() {
        cardLayout.show(cardPanel, CARD_REGISTER);
    }

    /**
     * 登录成功回调：记录当前用户并切换到主界面。
     *
     * @param user 登录成功的用户
     */
    public void onLoginSuccess(User user) {
        this.currentUser = user;
        // 主界面携带用户信息，每次登录重建以保证数据新鲜
        JPanel homePanel = buildHomePanel(user);
        cardPanel.add(homePanel, CARD_HOME);
        cardLayout.show(cardPanel, CARD_HOME);
    }

    /**
     * 登出：清空当前登录用户，返回登录界面。
     */
    public void logout() {
        this.currentUser = null;
        showLogin();
    }

    /**
     * 获取当前登录用户。
     *
     * @return 当前登录用户；未登录返回 {@code null}
     */
    public User getCurrentUser() {
        return currentUser;
    }

    // ==================== 主界面占位（T4-2 起逐步替换为正式业务面板） ====================

    /**
     * 构建登录后的主界面占位面板。
     * <p>
     * 展示当前用户基本信息与登出入口，业务功能面板将在 T4-2 ~ T4-6 接入。
     *
     * @param user 当前登录用户
     * @return 主界面面板
     */
    private JPanel buildHomePanel(User user) {
        JPanel root = new JPanel(new BorderLayout(0, 20));
        root.setBorder(BorderFactory.createEmptyBorder(30, 40, 30, 40));

        JLabel titleLabel = new JLabel("低碳校园资源循环流转平台", SwingConstants.CENTER);
        titleLabel.setFont(titleLabel.getFont().deriveFont(Font.BOLD, 24f));
        root.add(titleLabel, BorderLayout.NORTH);

        // 用户信息区
        JPanel infoPanel = new JPanel(new GridLayout(0, 1, 0, 8));
        infoPanel.setBorder(BorderFactory.createTitledBorder("当前用户"));
        infoPanel.add(new JLabel("用户名：" + user.getUsername()));
        infoPanel.add(new JLabel("角色：" + (user.isAdmin() ? "管理员" : "普通用户")));
        infoPanel.add(new JLabel("信用分：" + user.getCreditScore()));
        infoPanel.add(new JLabel("（物品浏览、发布、流转、统计等功能模块将在后续版本接入）"));
        root.add(infoPanel, BorderLayout.CENTER);

        // 底部操作区
        JPanel bottomPanel = new JPanel(new FlowLayout(FlowLayout.CENTER));
        JButton logoutButton = new JButton("退出登录");
        logoutButton.addActionListener(e -> logout());
        bottomPanel.add(logoutButton);
        root.add(bottomPanel, BorderLayout.SOUTH);

        return root;
    }
}
