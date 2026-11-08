package com.lowcarbon.gui.panel;

import com.lowcarbon.entity.Item;
import com.lowcarbon.entity.Transaction;
import com.lowcarbon.entity.TransactionStatus;
import com.lowcarbon.entity.User;
import com.lowcarbon.exception.BusinessException;
import com.lowcarbon.gui.MainFrame;
import com.lowcarbon.service.ItemService;
import com.lowcarbon.service.ReviewService;
import com.lowcarbon.service.TransactionService;
import com.lowcarbon.service.UserService;
import com.lowcarbon.util.Validator;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.DefaultComboBoxModel;
import javax.swing.DefaultListCellRenderer;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.table.DefaultTableModel;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Frame;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * 流转记录面板（T-01 ~ T-04）。
 * <p>
 * 功能：
 * <ul>
 *   <li>表格展示当前用户（作为买家或卖家）参与的全部流转记录</li>
 *   <li>每行根据状态 + 当前用户角色显示可用操作按钮</li>
 *   <li>顶部"发起意向"按钮 → 弹窗选择物品并发起流转（买家行为）</li>
 * </ul>
 * <p>
 * 操作按钮启用规则（业务规则全部在 Service 层做二次校验）：
 * <table>
 *   <tr><th>状态</th><th>角色</th><th>可用操作</th></tr>
 *   <tr><td>PENDING</td><td>卖家</td><td>同意 / 拒绝</td></tr>
 *   <tr><td>PENDING</td><td>买家</td><td>取消</td></tr>
 *   <tr><td>APPROVED</td><td>买家或卖家</td><td>完成 / 取消</td></tr>
 *   <tr><td>COMPLETED</td><td>双方之一 + 未评价</td><td>评价</td></tr>
 * </table>
 *
 * @author lowcarbon-team
 * @since 2026-11-09
 */
public class TransactionPanel extends JPanel {

    private static final Logger LOGGER = Logger.getLogger(TransactionPanel.class.getName());

    private static final String[] COLUMNS = {
            "记录ID", "物品", "我的角色", "对方", "状态", "备注", "创建时间", "完成时间"
    };

    private final MainFrame mainFrame;
    private final TransactionService txService;
    private final ItemService itemService;
    private final UserService userService;
    private final ReviewService reviewService;

    private final JTable txTable;
    private final DefaultTableModel tableModel;
    private final JLabel countLabel;
    private final JButton refreshButton;
    private final JButton initiateButton;
    private final JButton approveButton;
    private final JButton rejectButton;
    private final JButton completeButton;
    private final JButton cancelButton;
    private final JButton reviewButton;
    private final JButton detailButton;

    /** 当前表格数据对应的流转记录列表。 */
    private List<Transaction> currentRecords = new ArrayList<>();

    /** itemId → Item 缓存（用于解析物品名和卖家）。 */
    private final Map<Long, Item> itemCache = new HashMap<>();

    /** userId → User 缓存（用于解析对方用户名）。 */
    private final Map<Long, User> userCache = new HashMap<>();

    /** 已评价的 reviewId → recordId 映射（避免重复评价，异步刷新填充）。 */
    private final Map<Long, Boolean> reviewedRecordCache = new HashMap<>();

    public TransactionPanel(MainFrame mainFrame) {
        this.mainFrame = mainFrame;
        this.txService = new TransactionService();
        this.itemService = new ItemService();
        this.userService = new UserService();
        this.reviewService = new ReviewService();

        setLayout(new BorderLayout(5, 5));

        // 标题
        JLabel titleLabel = new JLabel("流转记录", SwingConstants.CENTER);
        titleLabel.setFont(titleLabel.getFont().deriveFont(java.awt.Font.BOLD, 18f));
        add(titleLabel, BorderLayout.NORTH);

        // 表格
        tableModel = new DefaultTableModel(COLUMNS, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
        txTable = new JTable(tableModel);
        txTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        txTable.setRowHeight(22);
        add(new JScrollPane(txTable), BorderLayout.CENTER);

        // 底部操作面板（两行）
        JPanel bottomPanel = new JPanel();
        bottomPanel.setLayout(new BoxLayout(bottomPanel, BoxLayout.Y_AXIS));
        JPanel row1 = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 4));
        JPanel row2 = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 4));

        refreshButton = new JButton("刷新");
        initiateButton = new JButton("发起意向");
        detailButton = new JButton("查看详情");
        approveButton = new JButton("卖家同意");
        rejectButton = new JButton("卖家拒绝");
        completeButton = new JButton("标记完成");
        cancelButton = new JButton("取消流转");
        reviewButton = new JButton("评价交易");

        row1.add(refreshButton);
        row1.add(initiateButton);
        row1.add(Box.createHorizontalStrut(12));
        row1.add(detailButton);

        row2.add(approveButton);
        row2.add(rejectButton);
        row2.add(Box.createHorizontalStrut(12));
        row2.add(completeButton);
        row2.add(cancelButton);
        row2.add(Box.createHorizontalStrut(12));
        row2.add(reviewButton);
        row2.add(Box.createHorizontalGlue());
        countLabel = new JLabel("共 0 条");
        row2.add(countLabel);

        bottomPanel.add(row1);
        bottomPanel.add(row2);
        bottomPanel.setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 4));
        add(bottomPanel, BorderLayout.SOUTH);

        // 事件
        refreshButton.addActionListener(e -> refresh());
        initiateButton.addActionListener(e -> showInitiateDialog());
        detailButton.addActionListener(e -> showDetail());
        approveButton.addActionListener(e -> transitionSelected("approve"));
        rejectButton.addActionListener(e -> transitionSelected("reject"));
        completeButton.addActionListener(e -> transitionSelected("complete"));
        cancelButton.addActionListener(e -> transitionSelected("cancel"));
        reviewButton.addActionListener(e -> openReviewDialog());

        txTable.getSelectionModel().addListSelectionListener(
                e -> { if (!e.getValueIsAdjusting()) updateActionButtons(); });
    }

    // ==================== 刷新 ====================

    public void refresh() {
        User currentUser = mainFrame.getCurrentUser();
        if (currentUser == null) return;
        refreshButton.setEnabled(false);
        new SwingWorker<List<Transaction>, Void>() {
            @Override
            protected List<Transaction> doInBackground() {
                return txService.listByUser(currentUser.getUserId());
            }

            @Override
            protected void done() {
                refreshButton.setEnabled(true);
                try {
                    fillTable(get());
                } catch (Exception e) {
                    handleException("查询流转记录失败", e);
                }
            }
        }.execute();
    }

    private void fillTable(List<Transaction> records) {
        currentRecords = records;
        tableModel.setRowCount(0);

        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm");
        User me = mainFrame.getCurrentUser();

        for (Transaction tx : records) {
            Item item = itemCache.computeIfAbsent(tx.getItemId(), id -> {
                try { return itemService.findById(id); }
                catch (BusinessException ignored) { return null; }
            });

            // 角色判定
            boolean isBuyer = me.getUserId().equals(tx.getInitiatorId());
            Long sellerId = item == null ? null : item.getPublisherId();
            boolean isSeller = sellerId != null && me.getUserId().equals(sellerId);
            String roleText = isBuyer ? "买家" : (isSeller ? "卖家" : "-");

            // 对方用户名
            User other;
            if (isBuyer) {
                other = getUserOrCache(sellerId);
            } else if (isSeller) {
                other = getUserOrCache(tx.getInitiatorId());
            } else {
                other = null;
            }

            tableModel.addRow(new Object[]{
                    tx.getRecordId(),
                    item == null ? "(物品已不存在)" : item.getName(),
                    roleText,
                    other == null ? "未知" : other.getUsername(),
                    txStatusLabel(tx.getStatus()),
                    tx.getRemark() == null ? "" : tx.getRemark(),
                    sdf.format(new java.util.Date(tx.getCreateTime())),
                    tx.getCompleteTime() == null ? ""
                            : sdf.format(new java.util.Date(tx.getCompleteTime()))
            });
        }
        countLabel.setText("共 " + records.size() + " 条");
        updateActionButtons();
    }

    private User getUserOrCache(Long userId) {
        if (userId == null) return null;
        return userCache.computeIfAbsent(userId, id -> {
            try { return userService.findById(id); }
            catch (BusinessException ignored) { return null; }
        });
    }

    // ==================== 按钮启用规则 ====================

    /**
     * 根据选中行的状态 + 当前用户角色，启用/禁用各操作按钮。
     */
    private void updateActionButtons() {
        int row = txTable.getSelectedRow();
        if (row < 0 || row >= currentRecords.size()) {
            approveButton.setEnabled(false);
            rejectButton.setEnabled(false);
            completeButton.setEnabled(false);
            cancelButton.setEnabled(false);
            reviewButton.setEnabled(false);
            return;
        }
        Transaction tx = currentRecords.get(row);
        User me = mainFrame.getCurrentUser();
        if (me == null) return;

        Item item = itemCache.get(tx.getItemId());
        Long sellerId = item == null ? null : item.getPublisherId();
        boolean isBuyer = me.getUserId().equals(tx.getInitiatorId());
        boolean isSeller = sellerId != null && me.getUserId().equals(sellerId);

        approveButton.setEnabled(tx.getStatus() == TransactionStatus.PENDING && isSeller);
        rejectButton.setEnabled(tx.getStatus() == TransactionStatus.PENDING && isSeller);
        cancelButton.setEnabled(tx.isActive() && (isBuyer || isSeller));
        completeButton.setEnabled(tx.getStatus() == TransactionStatus.APPROVED && (isBuyer || isSeller));
        reviewButton.setEnabled(tx.getStatus() == TransactionStatus.COMPLETED
                && (isBuyer || isSeller)
                && !reviewedRecordCache.containsKey(tx.getRecordId()));
    }

    // ==================== 发起意向 ====================

    /**
     * 弹出对话框让用户选择物品并发起意向。
     * <p>
     * 对话框列出所有 ON_SALE 且当前用户不是发布者的物品。
     */
    private void showInitiateDialog() {
        User me = mainFrame.getCurrentUser();
        if (me == null) return;

        // 异步加载可选物品列表
        initiateButton.setEnabled(false);
        new SwingWorker<List<Item>, Void>() {
            @Override
            protected List<Item> doInBackground() {
                return itemService.listOnSale();
            }

            @Override
            protected void done() {
                initiateButton.setEnabled(true);
                try {
                    List<Item> candidates = new ArrayList<>();
                    for (Item item : get()) {
                        if (!me.getUserId().equals(item.getPublisherId())) {
                            candidates.add(item);
                        }
                    }
                    if (candidates.isEmpty()) {
                        JOptionPane.showMessageDialog(TransactionPanel.this,
                                "当前没有可发起意向的在售物品",
                                "发起意向", JOptionPane.INFORMATION_MESSAGE);
                        return;
                    }
                    showInitiateDialogModal(candidates);
                } catch (Exception e) {
                    handleException("加载物品列表失败", e);
                }
            }
        }.execute();
    }

    private void showInitiateDialogModal(List<Item> candidates) {
        DefaultComboBoxModel<Item> model = new DefaultComboBoxModel<>();
        for (Item item : candidates) {
            model.addElement(item);
        }
        JComboBox<Item> combo = new JComboBox<>(model);
        combo.setRenderer(new DefaultListCellRenderer() {
            @Override
            public java.awt.Component getListCellRendererComponent(JList<?> list, Object value,
                                                                  int index, boolean isSelected,
                                                                  boolean cellHasFocus) {
                super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                if (value instanceof Item it) {
                    setText(String.format("%s  |  %.2f元  |  发布者ID:%d",
                            it.getName(), it.getPrice(), it.getPublisherId()));
                }
                return this;
            }
        });
        combo.setPreferredSize(new Dimension(380, 28));

        JTextArea remarkArea = new JTextArea(3, 38);
        remarkArea.setLineWrap(true);
        remarkArea.setWrapStyleWord(true);

        JPanel formPanel = new JPanel(new BorderLayout(0, 8));
        formPanel.add(new JLabel("选择物品："), BorderLayout.NORTH);
        formPanel.add(combo, BorderLayout.CENTER);
        JPanel remarkPanel = new JPanel(new BorderLayout(0, 4));
        remarkPanel.add(new JLabel("备注（可选）："), BorderLayout.NORTH);
        remarkPanel.add(new JScrollPane(remarkArea), BorderLayout.CENTER);
        formPanel.add(remarkPanel, BorderLayout.SOUTH);

        int ok = JOptionPane.showConfirmDialog(this, formPanel,
                "发起流转意向", JOptionPane.OK_CANCEL_OPTION);
        if (ok != JOptionPane.OK_OPTION) return;
        Item picked = (Item) combo.getSelectedItem();
        String remark = remarkArea.getText().trim();
        if (Validator.containsDangerousChars(remark)) {
            JOptionPane.showMessageDialog(this, "备注包含非法字符",
                    "输入错误", JOptionPane.WARNING_MESSAGE);
            return;
        }
        final Long itemId = picked.getItemId();
        final String finalRemark = remark.isEmpty() ? null : remark;

        initiateButton.setEnabled(false);
        new SwingWorker<Void, Void>() {
            @Override
            protected Void doInBackground() {
                txService.create(itemId, mainFrame.getCurrentUser().getUserId(), finalRemark);
                return null;
            }

            @Override
            protected void done() {
                initiateButton.setEnabled(true);
                try {
                    get();
                    JOptionPane.showMessageDialog(TransactionPanel.this,
                            "意向已发起，等待卖家确认",
                            "成功", JOptionPane.INFORMATION_MESSAGE);
                    refresh();
                } catch (Exception e) {
                    Throwable cause = e.getCause() != null ? e.getCause() : e;
                    if (cause instanceof BusinessException) {
                        JOptionPane.showMessageDialog(TransactionPanel.this, cause.getMessage(),
                                "发起失败", JOptionPane.WARNING_MESSAGE);
                    } else {
                        LOGGER.log(Level.SEVERE, "发起意向异常", e);
                        JOptionPane.showMessageDialog(TransactionPanel.this,
                                "系统异常，请稍后再试", "错误", JOptionPane.ERROR_MESSAGE);
                    }
                }
            }
        }.execute();
    }

    // ==================== 流转操作 ====================

    private void transitionSelected(String action) {
        int row = txTable.getSelectedRow();
        if (row < 0) return;
        Transaction tx = currentRecords.get(row);
        User me = mainFrame.getCurrentUser();
        if (me == null) return;

        // 二次确认（完成 / 取消）
        if ("complete".equals(action) || "cancel".equals(action) || "reject".equals(action)) {
            String confirmMsg = switch (action) {
                case "complete" -> "确认标记该流转已完成？完成后无法再改。";
                case "cancel"   -> "确认取消该流转？物品将重新上架。";
                case "reject"   -> "确认拒绝该申请？物品将重新上架。";
                default -> "确认执行该操作？";
            };
            int ok = JOptionPane.showConfirmDialog(this, confirmMsg,
                    "操作确认", JOptionPane.YES_NO_OPTION);
            if (ok != JOptionPane.YES_OPTION) return;
        }

        final Long recordId = tx.getRecordId();
        final Long operatorId = me.getUserId();
        final String finalAction = action;
        setOperationButtonsEnabled(false);
        new SwingWorker<Void, Void>() {
            @Override
            protected Void doInBackground() {
                switch (finalAction) {
                    case "approve" -> txService.approve(recordId, operatorId);
                    case "reject"  -> txService.reject(recordId, operatorId);
                    case "complete" -> txService.complete(recordId, operatorId);
                    case "cancel"  -> txService.cancel(recordId, operatorId);
                }
                return null;
            }

            @Override
            protected void done() {
                setOperationButtonsEnabled(true);
                try {
                    get();
                    JOptionPane.showMessageDialog(TransactionPanel.this,
                            "操作成功", "成功", JOptionPane.INFORMATION_MESSAGE);
                    refresh();
                } catch (Exception e) {
                    Throwable cause = e.getCause() != null ? e.getCause() : e;
                    if (cause instanceof BusinessException) {
                        JOptionPane.showMessageDialog(TransactionPanel.this, cause.getMessage(),
                                "操作失败", JOptionPane.WARNING_MESSAGE);
                    } else {
                        LOGGER.log(Level.SEVERE, "流转操作异常: " + finalAction, e);
                        JOptionPane.showMessageDialog(TransactionPanel.this,
                                "系统异常，请稍后再试", "错误", JOptionPane.ERROR_MESSAGE);
                    }
                }
            }
        }.execute();
    }

    private void setOperationButtonsEnabled(boolean enabled) {
        approveButton.setEnabled(enabled);
        rejectButton.setEnabled(enabled);
        completeButton.setEnabled(enabled);
        cancelButton.setEnabled(enabled);
    }

    // ==================== 详情 ====================

    private void showDetail() {
        int row = txTable.getSelectedRow();
        if (row < 0) {
            JOptionPane.showMessageDialog(this, "请先选择一条流转记录",
                    "提示", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        Transaction tx = currentRecords.get(row);
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm");
        Item item = itemCache.get(tx.getItemId());
        User me = mainFrame.getCurrentUser();
        boolean isBuyer = me.getUserId().equals(tx.getInitiatorId());
        Long sellerId = item == null ? null : item.getPublisherId();

        StringBuilder sb = new StringBuilder();
        sb.append("记录编号：").append(tx.getRecordId()).append("\n");
        sb.append("物品：").append(item == null ? "(已不存在)" : item.getName()).append("\n");
        sb.append("我的角色：").append(isBuyer ? "买家" : (sellerId != null && me.getUserId().equals(sellerId) ? "卖家" : "-")).append("\n");
        sb.append("状态：").append(txStatusLabel(tx.getStatus())).append("\n");
        sb.append("发起时间：").append(sdf.format(new java.util.Date(tx.getCreateTime()))).append("\n");
        sb.append("完成时间：").append(tx.getCompleteTime() == null ? "(未完成)" : sdf.format(new java.util.Date(tx.getCompleteTime()))).append("\n");
        sb.append("备注：").append(tx.getRemark() == null ? "(无)" : tx.getRemark()).append("\n");

        JTextArea area = new JTextArea(sb.toString());
        area.setEditable(false);
        area.setCaretPosition(0);
        JOptionPane.showMessageDialog(this, new JScrollPane(area),
                "流转详情", JOptionPane.INFORMATION_MESSAGE);
    }

    // ==================== 评价 ====================

    /**
     * 打开评价对话框（ReviewDialog）。
     */
    private void openReviewDialog() {
        int row = txTable.getSelectedRow();
        if (row < 0) return;
        Transaction tx = currentRecords.get(row);
        ReviewDialog dialog = new ReviewDialog(
                (Frame) SwingUtilities.getWindowAncestor(this),
                tx, mainFrame, reviewService, txService, itemService);
        dialog.setLocationRelativeTo(this);
        dialog.setVisible(true);
        if (dialog.isSubmitted()) {
            reviewedRecordCache.put(tx.getRecordId(), true);
            refresh();
        }
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

    private static String txStatusLabel(TransactionStatus s) {
        if (s == null) return "未知";
        return switch (s) {
            case PENDING   -> "待卖家确认";
            case APPROVED  -> "已同意";
            case REJECTED  -> "已拒绝";
            case COMPLETED -> "已完成";
            case CANCELLED -> "已取消";
        };
    }
}
