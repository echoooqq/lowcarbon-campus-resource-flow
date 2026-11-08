package com.lowcarbon.gui.panel;

import com.lowcarbon.entity.Category;
import com.lowcarbon.entity.Item;
import com.lowcarbon.entity.ItemStatus;
import com.lowcarbon.entity.TradeType;
import com.lowcarbon.entity.User;
import com.lowcarbon.exception.BusinessException;
import com.lowcarbon.gui.MainFrame;
import com.lowcarbon.service.ItemService;
import com.lowcarbon.service.UserService;
import com.lowcarbon.util.Validator;

import javax.swing.DefaultComboBoxModel;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.SwingWorker;
import javax.swing.table.DefaultTableModel;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * 物品浏览大厅面板（S-01 ~ S-04）。
 * <p>
 * 功能：
 * <ul>
 *   <li>表格展示在售物品（名称/分类/价格/交易类型/状态/发布人/信用分/标签/发布时间）</li>
 *   <li>组合筛选：关键词模糊搜索（S-02）+ 分类筛选（S-01）+ 价格区间（S-03）</li>
 *   <li>双击行或点击"查看详情"查看物品完整信息</li>
 * </ul>
 * <p>
 * 说明：收藏功能（S-05）因数据库 Schema 未包含收藏表，本期不实现。
 * 所有数据库操作均在 {@link SwingWorker} 中异步执行，避免阻塞事件派发线程。
 *
 * @author lowcarbon-team
 * @since 2026-11-09
 */
public class ItemListPanel extends JPanel {

    private static final Logger LOGGER = Logger.getLogger(ItemListPanel.class.getName());

    /** 表格列名。 */
    private static final String[] COLUMNS = {
            "ID", "名称", "分类", "价格(元)", "交易类型", "状态", "发布人", "信用分", "标签", "发布时间"
    };

    /** 分类下拉框"全部"选项的哨兵对象（categoryId 为 null 表示不筛选）。 */
    private static final Category ALL_CATEGORIES = new Category(null, "全部分类", null);

    private final MainFrame mainFrame;
    private final ItemService itemService;
    private final UserService userService;

    private final JTextField keywordField;
    private final JComboBox<Category> categoryCombo;
    private final JTextField minPriceField;
    private final JTextField maxPriceField;
    private final JTable itemTable;
    private final DefaultTableModel tableModel;
    private final JLabel countLabel;
    private final JButton searchButton;
    private final JButton refreshButton;
    private final JButton detailButton;

    /** 当前表格数据对应的物品列表（行索引与表格行一致）。 */
    private List<Item> currentItems = new ArrayList<>();

    /**
     * 构造物品浏览面板。
     *
     * @param mainFrame 主窗口引用（预留用于后续导航，如跳转到流转发起）
     */
    public ItemListPanel(MainFrame mainFrame) {
        this.mainFrame = mainFrame;
        this.itemService = new ItemService();
        this.userService = new UserService();

        setLayout(new BorderLayout(5, 5));

        // ==================== 顶部筛选区 ====================
        JPanel filterPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 8));
        filterPanel.add(new JLabel("关键词："));
        keywordField = new JTextField(12);
        filterPanel.add(keywordField);

        filterPanel.add(new JLabel("分类："));
        categoryCombo = new JComboBox<>(new DefaultComboBoxModel<>());
        categoryCombo.setRenderer((list, value, index, isSelected, cellHasFocus) ->
                new JLabel(value == null ? "" : value.getName()));
        filterPanel.add(categoryCombo);

        filterPanel.add(new JLabel("价格："));
        minPriceField = new JTextField(5);
        filterPanel.add(minPriceField);
        filterPanel.add(new JLabel("~"));
        maxPriceField = new JTextField(5);
        filterPanel.add(maxPriceField);

        searchButton = new JButton("搜索");
        JButton resetButton = new JButton("重置");
        filterPanel.add(searchButton);
        filterPanel.add(resetButton);
        add(filterPanel, BorderLayout.NORTH);

        // ==================== 中部表格 ====================
        tableModel = new DefaultTableModel(COLUMNS, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false; // 表格只读
            }
        };
        itemTable = new JTable(tableModel);
        itemTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        itemTable.setRowHeight(22);
        itemTable.getColumnModel().getColumn(0).setPreferredWidth(40);
        itemTable.getColumnModel().getColumn(1).setPreferredWidth(140);
        itemTable.getColumnModel().getColumn(8).setPreferredWidth(120);
        add(new JScrollPane(itemTable), BorderLayout.CENTER);

        // ==================== 底部操作区 ====================
        JPanel bottomPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 6));
        refreshButton = new JButton("刷新");
        detailButton = new JButton("查看详情");
        countLabel = new JLabel("共 0 条");
        bottomPanel.add(refreshButton);
        bottomPanel.add(detailButton);
        bottomPanel.add(countLabel);
        add(bottomPanel, BorderLayout.SOUTH);

        // ==================== 事件绑定 ====================
        searchButton.addActionListener(e -> doSearch());
        resetButton.addActionListener(e -> doReset());
        refreshButton.addActionListener(e -> doSearch());
        detailButton.addActionListener(e -> showSelectedDetail());
        // 关键词框回车触发搜索；双击行查看详情
        keywordField.addActionListener(e -> doSearch());
        itemTable.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseClicked(java.awt.event.MouseEvent e) {
                if (e.getClickCount() == 2) {
                    showSelectedDetail();
                }
            }
        });

        // 初始加载分类下拉框 + 全部在售物品
        loadCategories();
        refresh();
    }

    // ==================== 数据加载 ====================

    /**
     * 刷新列表（按当前筛选条件重新查询）。供外部（MainFrame 导航到此面板时）调用。
     */
    public void refresh() {
        doSearch();
    }

    /**
     * 异步加载分类下拉框（面板构造时执行一次）。
     */
    private void loadCategories() {
        new SwingWorker<List<Category>, Void>() {
            @Override
            protected List<Category> doInBackground() {
                return itemService.listAllCategories();
            }

            @Override
            protected void done() {
                try {
                    DefaultComboBoxModel<Category> model =
                            (DefaultComboBoxModel<Category>) categoryCombo.getModel();
                    model.removeAllElements();
                    model.addElement(ALL_CATEGORIES);
                    for (Category c : get()) {
                        model.addElement(c);
                    }
                } catch (Exception e) {
                    LOGGER.log(Level.WARNING, "加载分类列表失败", e);
                }
            }
        }.execute();
    }

    /**
     * 按当前筛选条件异步查询在售物品并填充表格。
     */
    private void doSearch() {
        // 解析价格区间（事件层参数校验）
        double minPrice = parsePrice(minPriceField.getText().trim(), "最低价格");
        double maxPrice = parsePrice(maxPriceField.getText().trim(), "最高价格");
        if (minPrice == Double.MIN_VALUE || maxPrice == Double.MIN_VALUE) {
            return; // parsePrice 已弹窗提示
        }
        if (minPrice >= 0 && maxPrice >= 0 && minPrice > maxPrice) {
            JOptionPane.showMessageDialog(this, "最低价格不能大于最高价格",
                    "输入错误", JOptionPane.WARNING_MESSAGE);
            return;
        }

        String keyword = keywordField.getText().trim();
        if (Validator.containsDangerousChars(keyword)) {
            JOptionPane.showMessageDialog(this, "关键词包含非法字符",
                    "输入错误", JOptionPane.WARNING_MESSAGE);
            return;
        }
        Category selected = (Category) categoryCombo.getSelectedItem();
        Long categoryId = (selected == null) ? null : selected.getCategoryId();

        setButtonsEnabled(false);
        new SwingWorker<List<Item>, Void>() {
            @Override
            protected List<Item> doInBackground() {
                return itemService.searchOnSale(
                        keyword.isEmpty() ? null : keyword, categoryId, minPrice, maxPrice);
            }

            @Override
            protected void done() {
                setButtonsEnabled(true);
                try {
                    fillTable(get());
                } catch (Exception e) {
                    handleException("查询物品失败", e);
                }
            }
        }.execute();
    }

    /**
     * 重置筛选条件并重新加载全部在售物品。
     */
    private void doReset() {
        keywordField.setText("");
        minPriceField.setText("");
        maxPriceField.setText("");
        if (categoryCombo.getItemCount() > 0) {
            categoryCombo.setSelectedIndex(0);
        }
        doSearch();
    }

    /**
     * 将物品列表填充到表格（发布人信息通过 UserService 解析，带本地缓存避免重复查询）。
     */
    private void fillTable(List<Item> items) {
        currentItems = items;
        tableModel.setRowCount(0);

        Map<Long, User> publisherCache = new HashMap<>();
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm");
        for (Item item : items) {
            User publisher = publisherCache.computeIfAbsent(item.getPublisherId(),
                    id -> {
                        try {
                            return userService.findById(id);
                        } catch (BusinessException e) {
                            return null;
                        }
                    });
            tableModel.addRow(new Object[]{
                    item.getItemId(),
                    item.getName(),
                    resolveCategoryName(item.getCategoryId()),
                    String.format("%.2f", item.getPrice()),
                    tradeTypeLabel(item.getTradeType()),
                    item.getStatus().getLabel(),
                    publisher == null ? "未知" : publisher.getUsername(),
                    publisher == null ? "-" : publisher.getCreditScore(),
                    item.getTagsStr() == null ? "" : item.getTagsStr(),
                    sdf.format(new Date(item.getPublishTime()))
            });
        }
        countLabel.setText("共 " + items.size() + " 条");
    }

    /**
     * 从分类下拉框模型中解析分类名称（避免额外查询）。
     */
    private String resolveCategoryName(Long categoryId) {
        DefaultComboBoxModel<Category> model =
                (DefaultComboBoxModel<Category>) categoryCombo.getModel();
        for (int i = 0; i < model.getSize(); i++) {
            Category c = model.getElementAt(i);
            if (c.getCategoryId() != null && c.getCategoryId().equals(categoryId)) {
                return c.getName();
            }
        }
        return "未知";
    }

    // ==================== 详情查看 ====================

    /**
     * 显示选中物品的完整详情弹窗。
     */
    private void showSelectedDetail() {
        int row = itemTable.getSelectedRow();
        if (row < 0 || row >= currentItems.size()) {
            JOptionPane.showMessageDialog(this, "请先在表格中选择一条物品记录",
                    "提示", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        Item item = currentItems.get(row);
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm");

        JTextArea area = new JTextArea(12, 42);
        area.setEditable(false);
        area.setLineWrap(true);
        area.setWrapStyleWord(true);
        area.setText(
                "物品编号：" + item.getItemId() + "\n"
                + "名　　称：" + item.getName() + "\n"
                + "分　　类：" + resolveCategoryName(item.getCategoryId()) + "\n"
                + "价　　格：" + String.format("%.2f", item.getPrice()) + " 元\n"
                + "交易类型：" + tradeTypeLabel(item.getTradeType()) + "\n"
                + "状　　态：" + item.getStatus().getLabel() + "\n"
                + "标　　签：" + (item.getTagsStr() == null ? "无" : item.getTagsStr()) + "\n"
                + "发布时间：" + sdf.format(new Date(item.getPublishTime())) + "\n"
                + "------------------------------\n"
                + "详细描述：\n" + (item.getDescription() == null ? "（无）" : item.getDescription()));
        area.setCaretPosition(0);

        JOptionPane.showMessageDialog(this, new JScrollPane(area),
                "物品详情 - " + item.getName(), JOptionPane.INFORMATION_MESSAGE);
    }

    // ==================== 辅助方法 ====================

    /**
     * 解析价格输入；空白返回 -1（不筛选），非法输入弹窗提示并返回 {@link Double#MIN_VALUE}。
     */
    private double parsePrice(String text, String fieldLabel) {
        if (text.isEmpty()) {
            return -1;
        }
        try {
            double value = Double.parseDouble(text);
            String err = Validator.validatePrice(value);
            if (err != null) {
                JOptionPane.showMessageDialog(this, fieldLabel + "：" + err,
                        "输入错误", JOptionPane.WARNING_MESSAGE);
                return Double.MIN_VALUE;
            }
            return value;
        } catch (NumberFormatException e) {
            JOptionPane.showMessageDialog(this, fieldLabel + "必须是数字",
                    "输入错误", JOptionPane.WARNING_MESSAGE);
            return Double.MIN_VALUE;
        }
    }

    /**
     * 统一异常处理：业务异常友好提示，未知异常记录日志。
     */
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

    /**
     * 查询期间禁用按钮，防止重复提交。
     */
    private void setButtonsEnabled(boolean enabled) {
        searchButton.setEnabled(enabled);
        refreshButton.setEnabled(enabled);
        detailButton.setEnabled(enabled);
    }

    /**
     * 交易类型中文标签。
     */
    private static String tradeTypeLabel(TradeType type) {
        if (type == null) {
            return "未知";
        }
        return switch (type) {
            case SELL   -> "出售";
            case DONATE -> "捐赠";
            case BORROW -> "借用";
        };
    }

}
