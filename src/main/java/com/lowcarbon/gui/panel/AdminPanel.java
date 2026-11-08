package com.lowcarbon.gui.panel;

import com.lowcarbon.entity.Item;
import com.lowcarbon.entity.ItemStatus;
import com.lowcarbon.entity.User;
import com.lowcarbon.entity.UserRole;
import com.lowcarbon.exception.BusinessException;
import com.lowcarbon.gui.MainFrame;
import com.lowcarbon.service.AdminService;
import com.lowcarbon.service.ItemService;
import com.lowcarbon.util.Validator;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.DefaultComboBoxModel;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.ListSelectionModel;
import javax.swing.SwingConstants;
import javax.swing.SwingWorker;
import javax.swing.filechooser.FileSystemView;
import javax.swing.table.DefaultTableModel;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * 管理员后台面板（T4-6 / A-01 ~ A-05）。
 * <p>
 * 三标签页：
 * <ul>
 *   <li><b>用户管理</b>：表格展示全部用户，选中行可重置密码 / 变更角色 / 重置信用分 / 删除用户</li>
 *   <li><b>物品管理</b>：表格展示全部物品（含已下架），选中行可强制下架 / 重新上架</li>
 *   <li><b>统计导出</b>：快速一键 CSV 导出（复用 StatisticsService）</li>
 * </ul>
 * <p>
 * 本面板只允许管理员进入（MainFrame 侧边导航按当前用户角色隐藏），
 * 但 AdminService 内部会再次做 verifyAdmin 校验（防御性二次校验）。
 *
 * @author lowcarbon-team
 * @since 2026-11-09
 */
public class AdminPanel extends JPanel {

    private static final Logger LOGGER = Logger.getLogger(AdminPanel.class.getName());

    private final MainFrame mainFrame;
    private final AdminService adminService;
    private final ItemService itemService;

    // 用户管理
    private JTable userTable;
    private DefaultTableModel userTableModel;
    private JButton userRefreshButton;
    private JButton resetPasswordButton;
    private JButton changeRoleButton;
    private JButton resetCreditButton;
    private JButton deleteUserButton;

    // 物品管理
    private JTable itemTable;
    private DefaultTableModel itemTableModel;
    private JButton itemRefreshButton;
    private JButton forceOffShelfButton;
    private JButton relistButton;

    // 统计导出
    private JButton exportCsvButton;

    public AdminPanel(MainFrame mainFrame) {
        this.mainFrame = mainFrame;
        this.adminService = new AdminService();
        this.itemService = new ItemService();

        setLayout(new BorderLayout(4, 4));

        JLabel titleLabel = new JLabel("管理员后台", SwingConstants.CENTER);
        titleLabel.setFont(titleLabel.getFont().deriveFont(java.awt.Font.BOLD, 18f));
        add(titleLabel, BorderLayout.NORTH);

        javax.swing.JTabbedPane tabs = new javax.swing.JTabbedPane();
        tabs.addTab("用户管理", buildUserTab());
        tabs.addTab("物品管理", buildItemTab());
        tabs.addTab("统计导出", buildExportTab());
        add(tabs, BorderLayout.CENTER);
    }

    // ==================== 标签页构建 ====================

    private JScrollPane buildUserTab() {
        JPanel root = new JPanel(new BorderLayout(4, 4));
        root.setBorder(BorderFactory.createEmptyBorder(6, 6, 6, 6));

        String[] cols = {"用户ID", "用户名", "角色", "信用分", "创建时间"};
        userTableModel = new DefaultTableModel(cols, 0) {
            @Override public boolean isCellEditable(int r, int c) { return false; }
        };
        userTable = new JTable(userTableModel);
        userTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        userTable.setRowHeight(22);
        root.add(new JScrollPane(userTable), BorderLayout.CENTER);

        JPanel bottom = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 4));
        userRefreshButton = new JButton("刷新用户");
        resetPasswordButton = new JButton("重置密码");
        changeRoleButton = new JButton("变更角色");
        resetCreditButton = new JButton("重置信用分");
        deleteUserButton = new JButton("删除用户");
        bottom.add(userRefreshButton);
        bottom.add(Box.createHorizontalStrut(10));
        bottom.add(resetPasswordButton);
        bottom.add(changeRoleButton);
        bottom.add(resetCreditButton);
        bottom.add(deleteUserButton);
        root.add(bottom, BorderLayout.SOUTH);

        userRefreshButton.addActionListener(e -> refreshUsers());
        resetPasswordButton.addActionListener(e -> resetPassword());
        changeRoleButton.addActionListener(e -> changeRole());
        resetCreditButton.addActionListener(e -> resetCredit());
        deleteUserButton.addActionListener(e -> deleteUser());

        return new JScrollPane(root);
    }

    private JScrollPane buildItemTab() {
        JPanel root = new JPanel(new BorderLayout(4, 4));
        root.setBorder(BorderFactory.createEmptyBorder(6, 6, 6, 6));

        String[] cols = {"物品ID", "名称", "分类", "价格(元)", "交易类型", "状态", "发布者ID", "发布时间"};
        itemTableModel = new DefaultTableModel(cols, 0) {
            @Override public boolean isCellEditable(int r, int c) { return false; }
        };
        itemTable = new JTable(itemTableModel);
        itemTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        itemTable.setRowHeight(22);
        root.add(new JScrollPane(itemTable), BorderLayout.CENTER);

        JPanel bottom = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 4));
        itemRefreshButton = new JButton("刷新物品");
        forceOffShelfButton = new JButton("强制下架");
        relistButton = new JButton("重新上架");
        bottom.add(itemRefreshButton);
        bottom.add(Box.createHorizontalStrut(10));
        bottom.add(forceOffShelfButton);
        bottom.add(relistButton);
        root.add(bottom, BorderLayout.SOUTH);

        itemRefreshButton.addActionListener(e -> refreshItems());
        forceOffShelfButton.addActionListener(e -> forceOffShelf());
        relistButton.addActionListener(e -> relistItem());

        return new JScrollPane(root);
    }

    private JScrollPane buildExportTab() {
        JPanel root = new JPanel();
        root.setLayout(new BoxLayout(root, BoxLayout.Y_AXIS));
        root.setBorder(BorderFactory.createEmptyBorder(16, 20, 16, 20));

        JLabel header = new JLabel("管理员一键导出统计 CSV（A-05）");
        header.setFont(header.getFont().deriveFont(java.awt.Font.BOLD, 14f));
        header.setAlignmentX(LEFT_ALIGNMENT);
        root.add(header);
        root.add(Box.createVerticalStrut(8));

        JTextArea hint = new JTextArea(4, 50);
        hint.setEditable(false);
        hint.setLineWrap(true);
        hint.setWrapStyleWord(true);
        hint.setText("导出内容覆盖全部统计指标：\n"
                + "  · 系统总览（9 项）\n"
                + "  · 低碳指标 + 用户画像\n"
                + "  · 物品 / 流转状态分布、分类分布\n"
                + "  · Top 10 热门卖家 / 评价者 / 预定物品\n"
                + "\n点击下方按钮选择保存路径，文件命名建议 lowcarbon_stats_时间戳.csv");
        hint.setBackground(root.getBackground());
        hint.setAlignmentX(LEFT_ALIGNMENT);
        hint.setMaximumSize(new Dimension(Integer.MAX_VALUE, 150));
        root.add(hint);
        root.add(Box.createVerticalStrut(12));

        exportCsvButton = new JButton("选择路径并导出 CSV");
        exportCsvButton.setAlignmentX(LEFT_ALIGNMENT);
        root.add(exportCsvButton);
        root.add(Box.createVerticalGlue());

        exportCsvButton.addActionListener(e -> doExportCsv());

        return new JScrollPane(root);
    }

    // ==================== 刷新 ====================

    public void refresh() {
        refreshUsers();
        refreshItems();
    }

    private void refreshUsers() {
        User me = mainFrame.getCurrentUser();
        if (me == null) return;
        userRefreshButton.setEnabled(false);
        new SwingWorker<List<User>, Void>() {
            @Override
            protected List<User> doInBackground() {
                return adminService.listAllUsers(me.getUserId());
            }

            @Override
            protected void done() {
                userRefreshButton.setEnabled(true);
                try {
                    fillUserTable(get());
                } catch (Exception e) {
                    handleException("查询用户失败", e);
                }
            }
        }.execute();
    }

    private void refreshItems() {
        User me = mainFrame.getCurrentUser();
        if (me == null) return;
        itemRefreshButton.setEnabled(false);
        new SwingWorker<List<Item>, Void>() {
            @Override
            protected List<Item> doInBackground() {
                return adminService.listAllItems(me.getUserId());
            }

            @Override
            protected void done() {
                itemRefreshButton.setEnabled(true);
                try {
                    fillItemTable(get());
                } catch (Exception e) {
                    handleException("查询物品失败", e);
                }
            }
        }.execute();
    }

    private void fillUserTable(List<User> users) {
        userTableModel.setRowCount(0);
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm");
        for (User u : users) {
            userTableModel.addRow(new Object[]{
                    u.getUserId(),
                    u.getUsername(),
                    u.getRole() == UserRole.ADMIN ? "管理员" : "普通用户",
                    u.getCreditScore(),
                    sdf.format(new java.util.Date(u.getCreateTime()))
            });
        }
    }

    private void fillItemTable(List<Item> items) {
        itemTableModel.setRowCount(0);
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm");
        for (Item it : items) {
            itemTableModel.addRow(new Object[]{
                    it.getItemId(),
                    it.getName(),
                    it.getCategoryId(),
                    String.format("%.2f", it.getPrice()),
                    it.getTradeType() == null ? "" : switch (it.getTradeType()) {
                        case SELL -> "出售"; case DONATE -> "捐赠"; case BORROW -> "借用";
                    },
                    itemStatusLabel(it.getStatus()),
                    it.getPublisherId(),
                    sdf.format(new java.util.Date(it.getPublishTime()))
            });
        }
    }

    // ==================== 用户操作 ====================

    private User selectedUser() {
        int row = userTable.getSelectedRow();
        if (row < 0) return null;
        Long userId = (Long) userTableModel.getValueAt(row, 0);
        User u = new User();
        u.setUserId(userId);
        return u;
    }

    private void resetPassword() {
        User sel = selectedUser();
        if (sel == null) return;
        User me = mainFrame.getCurrentUser();
        final Long targetId = sel.getUserId();
        int confirm = JOptionPane.showConfirmDialog(this,
                "确定重置用户 \"" + userTableModel.getValueAt(userTable.getSelectedRow(), 1)
                        + "\" 的密码？\n重置后会生成临时随机密码，需要立即告知用户。",
                "重置密码确认", JOptionPane.YES_NO_OPTION);
        if (confirm != JOptionPane.YES_OPTION) return;

        resetPasswordButton.setEnabled(false);
        new SwingWorker<String, Void>() {
            @Override
            protected String doInBackground() {
                return adminService.resetPassword(me.getUserId(), targetId);
            }
            @Override
            protected void done() {
                resetPasswordButton.setEnabled(true);
                try {
                    String tempPwd = get();
                    JOptionPane.showMessageDialog(AdminPanel.this,
                            "密码已重置！\n请立即告知用户以下临时密码：\n\n    " + tempPwd
                                    + "\n\n用户登录后请尽快修改。",
                            "重置成功", JOptionPane.INFORMATION_MESSAGE);
                } catch (Exception e) {
                    handleException("重置密码失败", e);
                }
            }
        }.execute();
    }

    private void changeRole() {
        User sel = selectedUser();
        if (sel == null) return;
        User me = mainFrame.getCurrentUser();
        final Long targetId = sel.getUserId();
        String username = (String) userTableModel.getValueAt(userTable.getSelectedRow(), 1);

        DefaultComboBoxModel<UserRole> model = new DefaultComboBoxModel<>(UserRole.values());
        JComboBox<UserRole> combo = new JComboBox<>(model);
        combo.setRenderer((list, v, i, s, f) -> new JLabel(
                v == UserRole.ADMIN ? "管理员" : "普通用户"));
        JOptionPane panel = new JOptionPane();
        int ok = JOptionPane.showConfirmDialog(this,
                new Object[]{"选择新角色（用户 \"" + username + "\"）：", combo},
                "变更角色", JOptionPane.OK_CANCEL_OPTION);
        if (ok != JOptionPane.OK_OPTION) return;
        final UserRole newRole = (UserRole) combo.getSelectedItem();

        changeRoleButton.setEnabled(false);
        new SwingWorker<Void, Void>() {
            @Override protected Void doInBackground() {
                adminService.changeRole(me.getUserId(), targetId, newRole);
                return null;
            }
            @Override protected void done() {
                changeRoleButton.setEnabled(true);
                try {
                    get();
                    JOptionPane.showMessageDialog(AdminPanel.this, "角色变更成功",
                            "成功", JOptionPane.INFORMATION_MESSAGE);
                    refreshUsers();
                } catch (Exception e) {
                    handleException("变更角色失败", e);
                }
            }
        }.execute();
    }

    private void resetCredit() {
        User sel = selectedUser();
        if (sel == null) return;
        User me = mainFrame.getCurrentUser();
        final Long targetId = sel.getUserId();
        String username = (String) userTableModel.getValueAt(userTable.getSelectedRow(), 1);
        int confirm = JOptionPane.showConfirmDialog(this,
                "确定将用户 \"" + username + "\" 的信用分重置为 100？",
                "重置信用分", JOptionPane.YES_NO_OPTION);
        if (confirm != JOptionPane.YES_OPTION) return;

        resetCreditButton.setEnabled(false);
        new SwingWorker<Void, Void>() {
            @Override protected Void doInBackground() {
                adminService.resetCreditScore(me.getUserId(), targetId);
                return null;
            }
            @Override protected void done() {
                resetCreditButton.setEnabled(true);
                try {
                    get();
                    JOptionPane.showMessageDialog(AdminPanel.this, "信用分已重置",
                            "成功", JOptionPane.INFORMATION_MESSAGE);
                    refreshUsers();
                } catch (Exception e) {
                    handleException("重置信用分失败", e);
                }
            }
        }.execute();
    }

    private void deleteUser() {
        User sel = selectedUser();
        if (sel == null) return;
        User me = mainFrame.getCurrentUser();
        final Long targetId = sel.getUserId();
        String username = (String) userTableModel.getValueAt(userTable.getSelectedRow(), 1);
        int confirm = JOptionPane.showConfirmDialog(this,
                "确定删除用户 \"" + username + "\"？此操作不可撤销。\n"
                        + "该用户发布的物品、相关流转记录和评价都将级联删除。",
                "删除用户确认", JOptionPane.YES_NO_OPTION);
        if (confirm != JOptionPane.YES_OPTION) return;

        deleteUserButton.setEnabled(false);
        new SwingWorker<Void, Void>() {
            @Override protected Void doInBackground() {
                adminService.deleteUser(me.getUserId(), targetId);
                return null;
            }
            @Override protected void done() {
                deleteUserButton.setEnabled(true);
                try {
                    get();
                    JOptionPane.showMessageDialog(AdminPanel.this, "用户已删除",
                            "成功", JOptionPane.INFORMATION_MESSAGE);
                    refreshUsers();
                } catch (Exception e) {
                    handleException("删除用户失败", e);
                }
            }
        }.execute();
    }

    // ==================== 物品操作 ====================

    private Item selectedItem() {
        int row = itemTable.getSelectedRow();
        if (row < 0) return null;
        Long itemId = (Long) itemTableModel.getValueAt(row, 0);
        Item item = new Item();
        item.setItemId(itemId);
        item.setStatus(itemStatusFromLabel(
                (String) itemTableModel.getValueAt(row, 5)));
        return item;
    }

    private ItemStatus itemStatusFromLabel(String label) {
        if (label == null) return null;
        return switch (label) {
            case "在售" -> ItemStatus.ON_SALE;
            case "已预订" -> ItemStatus.RESERVED;
            case "已成交" -> ItemStatus.SOLD;
            case "已下架" -> ItemStatus.OFF_SHELF;
            default -> null;
        };
    }

    private void forceOffShelf() {
        Item sel = selectedItem();
        if (sel == null) return;
        User me = mainFrame.getCurrentUser();
        final Long itemId = sel.getItemId();

        JTextArea reasonArea = new JTextArea(3, 36);
        reasonArea.setLineWrap(true);
        reasonArea.setWrapStyleWord(true);
        int ok = JOptionPane.showConfirmDialog(this,
                new Object[]{"请输入强制下架原因（会记录到系统日志）：", new JScrollPane(reasonArea)},
                "强制下架物品 #" + itemId, JOptionPane.OK_CANCEL_OPTION);
        if (ok != JOptionPane.OK_OPTION) return;
        String reason = reasonArea.getText().trim();
        if (Validator.containsDangerousChars(reason)) {
            JOptionPane.showMessageDialog(this, "下架原因包含非法字符",
                    "输入错误", JOptionPane.WARNING_MESSAGE);
            return;
        }
        if (reason.isEmpty()) reason = "管理员强制下架";
        final String finalReason = reason;

        forceOffShelfButton.setEnabled(false);
        new SwingWorker<Void, Void>() {
            @Override protected Void doInBackground() {
                adminService.forceOffShelf(me.getUserId(), itemId, finalReason);
                return null;
            }
            @Override protected void done() {
                forceOffShelfButton.setEnabled(true);
                try {
                    get();
                    JOptionPane.showMessageDialog(AdminPanel.this, "物品已强制下架",
                            "成功", JOptionPane.INFORMATION_MESSAGE);
                    refreshItems();
                } catch (Exception e) {
                    handleException("强制下架失败", e);
                }
            }
        }.execute();
    }

    private void relistItem() {
        Item sel = selectedItem();
        if (sel == null) return;
        if (sel.getStatus() != ItemStatus.OFF_SHELF) {
            JOptionPane.showMessageDialog(this,
                    "只有已下架物品可以重新上架，当前状态：" + itemStatusLabel(sel.getStatus()),
                    "无法上架", JOptionPane.WARNING_MESSAGE);
            return;
        }
        User me = mainFrame.getCurrentUser();
        final Long itemId = sel.getItemId();
        String name = (String) itemTableModel.getValueAt(itemTable.getSelectedRow(), 1);
        int confirm = JOptionPane.showConfirmDialog(this,
                "确定将已下架物品 \"" + name + "\" 重新上架？",
                "重新上架", JOptionPane.YES_NO_OPTION);
        if (confirm != JOptionPane.YES_OPTION) return;

        relistButton.setEnabled(false);
        new SwingWorker<Void, Void>() {
            @Override protected Void doInBackground() {
                adminService.adminRelistItem(me.getUserId(), itemId);
                return null;
            }
            @Override protected void done() {
                relistButton.setEnabled(true);
                try {
                    get();
                    JOptionPane.showMessageDialog(AdminPanel.this, "物品已重新上架",
                            "成功", JOptionPane.INFORMATION_MESSAGE);
                    refreshItems();
                } catch (Exception e) {
                    handleException("重新上架失败", e);
                }
            }
        }.execute();
    }

    // ==================== CSV 导出 ====================

    private void doExportCsv() {
        User me = mainFrame.getCurrentUser();
        if (me == null) return;

        JFileChooser chooser = new JFileChooser(FileSystemView.getFileSystemView().getHomeDirectory());
        chooser.setDialogTitle("管理员导出 CSV");
        chooser.setSelectedFile(new java.io.File("lowcarbon_admin_"
                + new java.text.SimpleDateFormat("yyyyMMdd_HHmmss").format(new java.util.Date())
                + ".csv"));
        int choice = chooser.showSaveDialog(this);
        if (choice != JFileChooser.APPROVE_OPTION) return;

        java.io.File file = chooser.getSelectedFile();
        exportCsvButton.setEnabled(false);
        final Long adminId = me.getUserId();
        final java.nio.file.Path finalPath = file.toPath();
        new SwingWorker<Void, Void>() {
            @Override protected Void doInBackground() {
                adminService.exportCsv(adminId, finalPath);
                return null;
            }
            @Override protected void done() {
                exportCsvButton.setEnabled(true);
                try {
                    get();
                    JOptionPane.showMessageDialog(AdminPanel.this,
                            "导出成功：\n" + finalPath.toAbsolutePath(),
                            "导出成功", JOptionPane.INFORMATION_MESSAGE);
                } catch (Exception e) {
                    handleException("导出 CSV 失败", e);
                }
            }
        }.execute();
    }

    // ==================== 辅助 ====================

    private void handleException(String title, Exception e) {
        Throwable cause = e.getCause() != null ? e.getCause() : e;
        if (cause instanceof BusinessException) {
            JOptionPane.showMessageDialog(this, cause.getMessage(),
                    title, JOptionPane.WARNING_MESSAGE);
        } else {
            LOGGER.log(Level.SEVERE, title, e);
            JOptionPane.showMessageDialog(this, "系统异常，请稍后再试",
                    "错误", JOptionPane.ERROR_MESSAGE);
        }
    }

    /** AdminService.itemStatusLabel 包可见不可直接调用，本地同实现一份。 */
    private static String itemStatusLabel(ItemStatus s) {
        return switch (s) {
            case ON_SALE   -> "在售";
            case RESERVED  -> "已预订";
            case SOLD      -> "已成交";
            case OFF_SHELF -> "已下架";
        };
    }
}
