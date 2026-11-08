package com.lowcarbon.gui.panel;

import com.lowcarbon.entity.Category;
import com.lowcarbon.entity.Item;
import com.lowcarbon.entity.ItemStatus;
import com.lowcarbon.entity.TradeType;
import com.lowcarbon.entity.User;
import com.lowcarbon.exception.BusinessException;
import com.lowcarbon.gui.MainFrame;
import com.lowcarbon.service.ItemService;

import javax.swing.DefaultComboBoxModel;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.ListSelectionModel;
import javax.swing.SwingConstants;
import javax.swing.SwingWorker;
import javax.swing.table.DefaultTableModel;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * "我发布的物品"管理面板（T4-3 I-02 修改 / I-03 下架）。
 * <p>
 * 功能：
 * <ul>
 *   <li>表格展示当前用户发布的全部物品（所有状态）</li>
 *   <li>选中行可执行：编辑（跳转至 {@link ItemPublishPanel} 编辑模式）、主动下架</li>
 *   <li>每 5 秒自动检查当前状态，RESERVED/SOLD 的物品自动禁用编辑按钮</li>
 * </ul>
 *
 * @author lowcarbon-team
 * @since 2026-11-09
 */
public class ItemManagePanel extends JPanel {

    private static final Logger LOGGER = Logger.getLogger(ItemManagePanel.class.getName());

    /** 表格列名。 */
    private static final String[] COLUMNS = {
            "ID", "名称", "分类", "价格(元)", "交易类型", "状态", "标签", "发布时间"
    };

    private final MainFrame mainFrame;
    private final ItemService itemService;

    private final JTable itemTable;
    private final DefaultTableModel tableModel;
    private final JLabel countLabel;
    private final JButton refreshButton;
    private final JButton editButton;
    private final JButton takeOffShelfButton;

    /** 当前表格数据对应的物品列表（行索引与表格行一致）。 */
    private List<Item> currentItems = new ArrayList<>();

    /**
     * 构造物品管理面板。
     *
     * @param mainFrame 主窗口引用（用于导航到发布面板的编辑模式）
     */
    public ItemManagePanel(MainFrame mainFrame) {
        this.mainFrame = mainFrame;
        this.itemService = new ItemService();

        setLayout(new BorderLayout(5, 5));

        // 标题
        JLabel titleLabel = new JLabel("我发布的物品", SwingConstants.CENTER);
        titleLabel.setFont(titleLabel.getFont().deriveFont(java.awt.Font.BOLD, 18f));
        add(titleLabel, BorderLayout.NORTH);

        // 表格（只读）
        tableModel = new DefaultTableModel(COLUMNS, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
        itemTable = new JTable(tableModel);
        itemTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        itemTable.setRowHeight(22);
        itemTable.getColumnModel().getColumn(0).setPreferredWidth(40);
        itemTable.getColumnModel().getColumn(1).setPreferredWidth(140);
        add(new JScrollPane(itemTable), BorderLayout.CENTER);

        // 底部操作区
        JPanel bottomPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 6));
        refreshButton = new JButton("刷新");
        editButton = new JButton("编辑");
        takeOffShelfButton = new JButton("下架");
        countLabel = new JLabel("共 0 条");
        bottomPanel.add(refreshButton);
        bottomPanel.add(editButton);
        bottomPanel.add(takeOffShelfButton);
        bottomPanel.add(countLabel);
        add(bottomPanel, BorderLayout.SOUTH);

        // 事件
        refreshButton.addActionListener(e -> refresh());
        editButton.addActionListener(e -> editSelected());
        takeOffShelfButton.addActionListener(e -> takeOffSelected());
    }

    // ==================== 刷新 ====================

    /**
     * 刷新当前用户发布的物品列表。供外部（MainFrame 导航到此面板时）调用。
     */
    public void refresh() {
        User currentUser = mainFrame.getCurrentUser();
        if (currentUser == null) {
            return;
        }
        refreshButton.setEnabled(false);
        new SwingWorker<List<Item>, Void>() {
            @Override
            protected List<Item> doInBackground() {
                return itemService.listByPublisher(currentUser.getUserId());
            }

            @Override
            protected void done() {
                refreshButton.setEnabled(true);
                try {
                    fillTable(get());
                } catch (Exception e) {
                    handleException("查询我的物品失败", e);
                }
            }
        }.execute();
    }

    /** 分类缓存（首次 fillTable 时异步加载一次）。 */
    private List<Category> categoryCache;

    private void fillTable(List<Item> items) {
        currentItems = items;
        tableModel.setRowCount(0);

        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm");
        for (Item item : items) {
            tableModel.addRow(new Object[]{
                    item.getItemId(),
                    item.getName(),
                    resolveCategoryName(item.getCategoryId()),
                    String.format("%.2f", item.getPrice()),
                    tradeTypeLabel(item.getTradeType()),
                    statusLabel(item.getStatus()),
                    item.getTagsStr() == null ? "" : item.getTagsStr(),
                    sdf.format(new Date(item.getPublishTime()))
            });
        }
        countLabel.setText("共 " + items.size() + " 条");
        // 懒加载分类缓存（不阻塞本次渲染）
        if (categoryCache == null) {
            loadCategoryCache();
        }
    }

    private String resolveCategoryName(Long categoryId) {
        if (categoryId == null || categoryCache == null) {
            return "未知";
        }
        for (Category c : categoryCache) {
            if (categoryId.equals(c.getCategoryId())) {
                return c.getName();
            }
        }
        return "未知";
    }

    /**
     * 异步加载分类缓存；加载完若当前面板仍显示，触发一次表格刷新以补全分类名。
     */
    private void loadCategoryCache() {
        new SwingWorker<List<Category>, Void>() {
            @Override
            protected List<Category> doInBackground() {
                return itemService.listAllCategories();
            }

            @Override
            protected void done() {
                try {
                    categoryCache = get();
                    // 重新 fill 一次以补全此前显示为"未知"的分类名
                    fillTable(currentItems);
                } catch (Exception e) {
                    LOGGER.log(Level.WARNING, "加载分类缓存失败", e);
                }
            }
        }.execute();
    }

    // ==================== 操作 ====================

    private void editSelected() {
        int row = itemTable.getSelectedRow();
        if (row < 0 || row >= currentItems.size()) {
            JOptionPane.showMessageDialog(this, "请先选择一条物品记录",
                    "提示", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        Item item = currentItems.get(row);
        if (item.getStatus() == ItemStatus.RESERVED || item.getStatus() == ItemStatus.SOLD) {
            JOptionPane.showMessageDialog(this,
                    "流转中的物品（已预订 / 已成交）不允许修改",
                    "无法编辑", JOptionPane.WARNING_MESSAGE);
            return;
        }
        mainFrame.showItemPublishForEdit(item);
    }

    private void takeOffSelected() {
        int row = itemTable.getSelectedRow();
        if (row < 0 || row >= currentItems.size()) {
            JOptionPane.showMessageDialog(this, "请先选择一条物品记录",
                    "提示", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        Item item = currentItems.get(row);
        User currentUser = mainFrame.getCurrentUser();
        if (currentUser == null) {
            return;
        }

        // UI 快速校验：仅在售物品允许下架（Service 会再次校验）
        if (item.getStatus() != ItemStatus.ON_SALE) {
            JOptionPane.showMessageDialog(this,
                    "当前状态不允许下架：" + statusLabel(item.getStatus()),
                    "无法下架", JOptionPane.WARNING_MESSAGE);
            return;
        }

        int confirm = JOptionPane.showConfirmDialog(this,
                "确定要下架「" + item.getName() + "」吗？\n下架后物品将不再出现在浏览大厅。",
                "下架确认", JOptionPane.YES_NO_OPTION);
        if (confirm != JOptionPane.YES_OPTION) {
            return;
        }

        takeOffShelfButton.setEnabled(false);
        new SwingWorker<Void, Void>() {
            @Override
            protected Void doInBackground() {
                itemService.takeOffShelf(item.getItemId(), currentUser.getUserId());
                return null;
            }

            @Override
            protected void done() {
                takeOffShelfButton.setEnabled(true);
                try {
                    get();
                    JOptionPane.showMessageDialog(ItemManagePanel.this, "物品已下架",
                            "成功", JOptionPane.INFORMATION_MESSAGE);
                    refresh();
                } catch (Exception e) {
                    Throwable cause = e.getCause() != null ? e.getCause() : e;
                    if (cause instanceof BusinessException) {
                        JOptionPane.showMessageDialog(ItemManagePanel.this, cause.getMessage(),
                                "操作失败", JOptionPane.WARNING_MESSAGE);
                    } else {
                        LOGGER.log(Level.SEVERE, "下架物品异常", e);
                        JOptionPane.showMessageDialog(ItemManagePanel.this,
                                "系统异常，请稍后再试",
                                "错误", JOptionPane.ERROR_MESSAGE);
                    }
                }
            }
        }.execute();
    }

    // ==================== 异常与标签 ====================

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

    private static String tradeTypeLabel(TradeType type) {
        if (type == null) return "未知";
        return switch (type) {
            case SELL   -> "出售";
            case DONATE -> "捐赠";
            case BORROW -> "借用";
        };
    }

    private static String statusLabel(ItemStatus status) {
        if (status == null) return "未知";
        return switch (status) {
            case ON_SALE   -> "在售";
            case RESERVED  -> "已预订";
            case SOLD      -> "已成交";
            case OFF_SHELF -> "已下架";
        };
    }
}
