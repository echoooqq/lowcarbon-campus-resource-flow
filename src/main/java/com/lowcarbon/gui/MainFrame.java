package com.lowcarbon.gui;

import com.lowcarbon.entity.Item;
import com.lowcarbon.entity.User;
import com.lowcarbon.gui.panel.ItemListPanel;
import com.lowcarbon.gui.panel.ItemManagePanel;
import com.lowcarbon.gui.panel.ItemPublishPanel;
import com.lowcarbon.gui.panel.LoginPanel;
import com.lowcarbon.gui.panel.RegisterPanel;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;

/**
 * 系统主窗口（单窗口 + CardLayout 面板切换）。
 * <p>
 * 职责：
 * <ul>
 *   <li>作为所有业务面板的容器，通过 {@link CardLayout} 管理面板切换</li>
 *   <li>持有当前登录用户 {@link #currentUser}，登录成功后切换到主界面外壳</li>
 *   <li>主界面外壳 = 顶栏（用户信息 + 退出登录）+ 侧边导航 + 内容区</li>
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

    /** 内容区卡片名：物品浏览大厅。 */
    public static final String CONTENT_ITEM_LIST = "ITEM_LIST";

    /** 内容区卡片名：物品发布 / 编辑。 */
    public static final String CONTENT_ITEM_PUBLISH = "ITEM_PUBLISH";

    /** 内容区卡片名：我发布的物品管理。 */
    public static final String CONTENT_ITEM_MANAGE = "ITEM_MANAGE";

    private static final String WINDOW_TITLE = "低碳校园资源循环流转桌面系统 v1.0.0";
    private static final int WINDOW_WIDTH = 1100;
    private static final int WINDOW_HEIGHT = 680;

    private final CardLayout cardLayout;
    private final JPanel cardPanel;

    /** 主界面内容区布局（物品大厅 / 发布 / 管理等功能面板）。 */
    private CardLayout contentLayout;

    /** 主界面内容区面板。 */
    private JPanel contentPanel;

    /** 物品浏览面板。 */
    private ItemListPanel itemListPanel;

    /** 物品发布 / 编辑面板。 */
    private ItemPublishPanel itemPublishPanel;

    /** 我发布的物品管理面板。 */
    private ItemManagePanel itemManagePanel;

    /** 当前登录用户；未登录时为 {@code null}。 */
    private User currentUser;

    /**
     * 构造主窗口：初始化窗口属性并注册登录 / 注册卡片。
     */
    public MainFrame() {
        super(WINDOW_TITLE);
        cardLayout = new CardLayout();
        cardPanel = new JPanel(cardLayout);

        // 注册登录 / 注册卡片（主界面外壳在登录成功后动态构建）
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
     * 登录成功回调：记录当前用户，构建主界面外壳并切换。
     *
     * @param user 登录成功的用户
     */
    public void onLoginSuccess(User user) {
        this.currentUser = user;
        // 主界面外壳携带用户信息，每次登录重建以保证数据新鲜
        JPanel shell = buildMainShell(user);
        cardPanel.add(shell, CARD_HOME);
        cardLayout.show(cardPanel, CARD_HOME);
        // 默认展示物品大厅并加载最新数据
        showItemList();
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

    /**
     * 导航到物品浏览大厅，并刷新列表数据。
     */
    public void showItemList() {
        if (contentLayout == null || itemListPanel == null) {
            return;
        }
        itemPublishPanel.exitEditMode(); // 离开发布面板时放弃编辑中的未保存状态
        contentLayout.show(contentPanel, CONTENT_ITEM_LIST);
        itemListPanel.refresh();
    }

    /**
     * 导航到物品发布表单（新建模式）。
     */
    public void showItemPublish() {
        if (contentLayout == null || itemPublishPanel == null) {
            return;
        }
        itemPublishPanel.exitEditMode();
        contentLayout.show(contentPanel, CONTENT_ITEM_PUBLISH);
    }

    /**
     * 导航到物品发布面板并进入编辑模式（编辑已有物品）。
     *
     * @param item 待编辑物品
     */
    public void showItemPublishForEdit(Item item) {
        if (contentLayout == null || itemPublishPanel == null) {
            return;
        }
        contentLayout.show(contentPanel, CONTENT_ITEM_PUBLISH);
        itemPublishPanel.editItem(item);
    }

    /**
     * 导航到"我发布的物品"管理面板，并刷新列表。
     */
    public void showItemManage() {
        if (contentLayout == null || itemManagePanel == null) {
            return;
        }
        itemPublishPanel.exitEditMode();
        contentLayout.show(contentPanel, CONTENT_ITEM_MANAGE);
        itemManagePanel.refresh();
    }

    // ==================== 主界面外壳 ====================

    /**
     * 构建登录后的主界面外壳：顶栏 + 侧边导航 + 内容区（CardLayout）。
     * <p>
     * 内容区面板按任务逐步接入：T4-2 物品大厅，T4-3 发布 + 我的管理，T4-4 ~ T4-6 后续接入。
     *
     * @param user 当前登录用户
     * @return 主界面外壳面板
     */
    private JPanel buildMainShell(User user) {
        JPanel root = new JPanel(new BorderLayout());

        // ---------- 顶栏 ----------
        JPanel topBar = new JPanel(new BorderLayout());
        topBar.setBorder(BorderFactory.createEmptyBorder(8, 12, 8, 12));
        JLabel titleLabel = new JLabel("低碳校园资源循环流转平台");
        titleLabel.setFont(titleLabel.getFont().deriveFont(Font.BOLD, 18f));
        topBar.add(titleLabel, BorderLayout.WEST);

        JPanel userPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 12, 0));
        String roleText = user.isAdmin() ? "管理员" : "普通用户";
        userPanel.add(new JLabel("欢迎，" + user.getUsername()
                + "（" + roleText + "，信用分：" + user.getCreditScore() + "）"));
        JButton logoutButton = new JButton("退出登录");
        logoutButton.addActionListener(e -> logout());
        userPanel.add(logoutButton);
        topBar.add(userPanel, BorderLayout.EAST);
        root.add(topBar, BorderLayout.NORTH);

        // ---------- 侧边导航 ----------
        JPanel navPanel = new JPanel();
        navPanel.setLayout(new BoxLayout(navPanel, BoxLayout.Y_AXIS));
        navPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        navPanel.setPreferredSize(new Dimension(130, 0));

        JButton navItemList = createNavButton("物品大厅");
        navItemList.addActionListener(e -> showItemList());
        navPanel.add(navItemList);

        JButton navItemPublish = createNavButton("发布物品");
        navItemPublish.addActionListener(e -> showItemPublish());
        navPanel.add(navItemPublish);

        JButton navItemManage = createNavButton("我的物品");
        navItemManage.addActionListener(e -> showItemManage());
        navPanel.add(navItemManage);

        navPanel.add(Box.createVerticalGlue());
        root.add(navPanel, BorderLayout.WEST);

        // ---------- 内容区 ----------
        contentLayout = new CardLayout();
        contentPanel = new JPanel(contentLayout);

        itemListPanel = new ItemListPanel(this);
        contentPanel.add(itemListPanel, CONTENT_ITEM_LIST);

        itemPublishPanel = new ItemPublishPanel(this);
        contentPanel.add(itemPublishPanel, CONTENT_ITEM_PUBLISH);

        itemManagePanel = new ItemManagePanel(this);
        contentPanel.add(itemManagePanel, CONTENT_ITEM_MANAGE);

        root.add(contentPanel, BorderLayout.CENTER);

        return root;
    }

    /**
     * 创建侧边导航按钮（统一尺寸与对齐）。
     */
    private JButton createNavButton(String text) {
        JButton button = new JButton(text);
        button.setAlignmentX(Component.CENTER_ALIGNMENT);
        button.setMaximumSize(new Dimension(110, 34));
        button.setHorizontalAlignment(SwingConstants.CENTER);
        return button;
    }
}
