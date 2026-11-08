package com.lowcarbon.gui.panel;

import com.lowcarbon.entity.Category;
import com.lowcarbon.entity.Item;
import com.lowcarbon.entity.ItemStatus;
import com.lowcarbon.entity.TradeType;
import com.lowcarbon.entity.User;
import com.lowcarbon.exception.BusinessException;
import com.lowcarbon.gui.MainFrame;
import com.lowcarbon.service.ItemService;
import com.lowcarbon.util.Validator;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.DefaultComboBoxModel;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingConstants;
import javax.swing.SwingWorker;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * 物品发布 / 编辑面板。
 * <p>
 * 两种使用场景：
 * <ul>
 *   <li><b>新建</b>：{@link #ItemPublishPanel(MainFrame)} 无参，底部按钮显示"发布物品"；
 *       成功后自动清空表单，触发物品管理面板刷新。</li>
 *   <li><b>编辑</b>：调用 {@link #editItem(Item)} 传入已有物品；表单预填，
 *       底部按钮显示"保存修改"，仅允许修改未成交/未下架的物品。</li>
 * </ul>
 * <p>
 * 说明：Service 层通过 {@code operatorId} 严格校验权限，GUI 层不做越权假设。
 *
 * @author lowcarbon-team
 * @since 2026-11-09
 */
public class ItemPublishPanel extends JPanel {

    private static final Logger LOGGER = Logger.getLogger(ItemPublishPanel.class.getName());

    private final MainFrame mainFrame;
    private final ItemService itemService;

    // 表单字段
    private final JTextField nameField;
    private final JComboBox<Category> categoryCombo;
    private final JTextField priceField;
    private final JComboBox<TradeType> tradeTypeCombo;
    private final JTextField tagsField;
    private final JTextArea descArea;

    private final JButton submitButton;
    private final JButton cancelButton;

    /** 编辑模式下当前编辑的物品；新建模式为 null。 */
    private Item editingItem;

    /** 分类下拉框模型（{@code categoryId} 为 null 的哨兵项已在面板初始化时移除）。 */
    private DefaultComboBoxModel<Category> categoryModel;

    /**
     * 构造发布面板（新建模式）。
     *
     * @param mainFrame 主窗口引用
     */
    public ItemPublishPanel(MainFrame mainFrame) {
        this.mainFrame = mainFrame;
        this.itemService = new ItemService();

        setLayout(new BorderLayout(0, 10));
        setBorder(BorderFactory.createEmptyBorder(16, 24, 16, 24));

        // 标题
        JLabel titleLabel = new JLabel("发布闲置物品", SwingConstants.CENTER);
        titleLabel.setFont(titleLabel.getFont().deriveFont(java.awt.Font.BOLD, 18f));
        add(titleLabel, BorderLayout.NORTH);

        // 表单区
        JPanel formPanel = new JPanel();
        formPanel.setLayout(new BoxLayout(formPanel, BoxLayout.Y_AXIS));
        add(formPanel, BorderLayout.CENTER);

        nameField = createRow(formPanel, "物品名称 *");
        categoryCombo = createCategoryCombo(formPanel);
        priceField = createRow(formPanel, "价格（元，0 表示免费）");
        tradeTypeCombo = createTradeTypeCombo(formPanel);
        tagsField = createRow(formPanel, "标签（逗号分隔，可选）");

        // 描述：多行
        JLabel descLabel = new JLabel("详细描述（可选，500 字以内）");
        descLabel.setAlignmentX(LEFT_ALIGNMENT);
        formPanel.add(descLabel);
        descArea = new JTextArea(5, 40);
        descArea.setLineWrap(true);
        descArea.setWrapStyleWord(true);
        descArea.setMaximumSize(new Dimension(Integer.MAX_VALUE, 120));
        formPanel.add(descArea);

        formPanel.add(Box.createVerticalStrut(12));

        // 底部按钮
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 12, 0));
        submitButton = new JButton("发布物品");
        cancelButton = new JButton("清空");
        buttonPanel.add(submitButton);
        buttonPanel.add(cancelButton);
        add(buttonPanel, BorderLayout.SOUTH);

        submitButton.addActionListener(e -> doSubmit());
        cancelButton.addActionListener(e -> clearForm());

        // 初始化分类下拉框（Service 异步加载）
        loadCategories();
    }

    // ==================== 编辑模式 ====================

    /**
     * 进入编辑模式：预填表单，按钮改为"保存修改"。
     *
     * @param item 待编辑物品；若物品状态为 RESERVED/SOLD 则抛出异常（防御）
     */
    public void editItem(Item item) {
        if (item == null) {
            clearForm();
            return;
        }
        if (item.getStatus() == ItemStatus.RESERVED || item.getStatus() == ItemStatus.SOLD) {
            JOptionPane.showMessageDialog(this,
                    "流转中的物品（已预订 / 已成交）不允许修改",
                    "无法编辑", JOptionPane.WARNING_MESSAGE);
            return;
        }
        editingItem = item;
        nameField.setText(item.getName());
        selectCategory(item.getCategoryId());
        priceField.setText(String.format("%.2f", item.getPrice()));
        tradeTypeCombo.setSelectedItem(item.getTradeType() == null ? TradeType.SELL : item.getTradeType());
        tagsField.setText(item.getTagsStr() == null ? "" : item.getTagsStr());
        descArea.setText(item.getDescription() == null ? "" : item.getDescription());
        descArea.setCaretPosition(0);
        submitButton.setText("保存修改");
    }

    /**
     * 从编辑模式退出，恢复新建模式。
     */
    public void exitEditMode() {
        editingItem = null;
        submitButton.setText("发布物品");
    }

    // ==================== 提交（发布 / 保存修改） ====================

    private void doSubmit() {
        User currentUser = mainFrame.getCurrentUser();
        if (currentUser == null) {
            JOptionPane.showMessageDialog(this, "请先登录",
                    "提示", JOptionPane.WARNING_MESSAGE);
            return;
        }

        // --- 事件层参数预校验 ---
        String name = nameField.getText().trim();
        String err = Validator.validateItemName(name);
        if (err != null) {
            JOptionPane.showMessageDialog(this, err, "输入错误", JOptionPane.WARNING_MESSAGE);
            nameField.requestFocus();
            return;
        }

        Category selected = (Category) categoryCombo.getSelectedItem();
        if (selected == null || selected.getCategoryId() == null) {
            JOptionPane.showMessageDialog(this, "请选择物品分类",
                    "输入错误", JOptionPane.WARNING_MESSAGE);
            return;
        }
        Long categoryId = selected.getCategoryId();

        double price = parsePrice(priceField.getText().trim());
        if (price == Double.MIN_VALUE) {
            return; // parsePrice 已弹窗
        }

        final TradeType tradeType;
        TradeType picked = (TradeType) tradeTypeCombo.getSelectedItem();
        tradeType = (picked == null) ? TradeType.SELL : picked;

        final String tags;
        String tagsRaw = tagsField.getText().trim();
        tags = tagsRaw.isEmpty() ? null : tagsRaw;

        final String description;
        String descRaw = descArea.getText().trim();
        description = descRaw.isEmpty() ? null : descRaw;
        err = Validator.validateItemDescription(description);
        if (err != null) {
            JOptionPane.showMessageDialog(this, "详细描述：" + err,
                    "输入错误", JOptionPane.WARNING_MESSAGE);
            descArea.requestFocus();
            return;
        }

        // --- 异步提交 ---
        boolean isEdit = editingItem != null;
        submitButton.setEnabled(false);
        final boolean finalIsEdit = isEdit;
        new SwingWorker<Void, Void>() {
            @Override
            protected Void doInBackground() {
                if (finalIsEdit) {
                    itemService.update(editingItem.getItemId(), currentUser.getUserId(),
                            name, categoryId, description, price, tags);
                } else {
                    itemService.publish(currentUser.getUserId(), name, categoryId,
                            description, price, tradeType, tags);
                }
                return null;
            }

            @Override
            protected void done() {
                submitButton.setEnabled(true);
                try {
                    get();
                    String msg = finalIsEdit ? "物品修改成功" : "物品发布成功";
                    JOptionPane.showMessageDialog(ItemPublishPanel.this, msg,
                            "成功", JOptionPane.INFORMATION_MESSAGE);
                    // 成功后回到新建模式，供连续发布使用
                    exitEditMode();
                } catch (Exception e) {
                    Throwable cause = e.getCause() != null ? e.getCause() : e;
                    if (cause instanceof BusinessException) {
                        JOptionPane.showMessageDialog(ItemPublishPanel.this, cause.getMessage(),
                                "操作失败", JOptionPane.WARNING_MESSAGE);
                    } else {
                        LOGGER.log(Level.SEVERE, "物品提交发生未知异常", e);
                        JOptionPane.showMessageDialog(ItemPublishPanel.this,
                                "系统异常，请稍后再试",
                                "错误", JOptionPane.ERROR_MESSAGE);
                    }
                }
            }
        }.execute();
    }

    // ==================== 辅助 UI 构建 ====================

    /**
     * 在指定面板中追加一行"标签 + 输入框"并返回输入框。
     */
    private JTextField createRow(JPanel container, String labelText) {
        JLabel label = new JLabel(labelText);
        label.setAlignmentX(LEFT_ALIGNMENT);
        label.setMaximumSize(new Dimension(Integer.MAX_VALUE, 22));
        JTextField field = new JTextField();
        field.setMaximumSize(new Dimension(Integer.MAX_VALUE, 28));
        container.add(label);
        container.add(field);
        container.add(Box.createVerticalStrut(4));
        return field;
    }

    private JComboBox<Category> createCategoryCombo(JPanel container) {
        JLabel label = new JLabel("物品分类 *");
        label.setAlignmentX(LEFT_ALIGNMENT);
        JComboBox<Category> combo = new JComboBox<>();
        combo.setRenderer((list, value, index, isSelected, cellHasFocus) ->
                new JLabel(value == null ? "" : value.getName()));
        combo.setMaximumSize(new Dimension(Integer.MAX_VALUE, 28));
        label.setMaximumSize(new Dimension(Integer.MAX_VALUE, 22));
        container.add(label);
        container.add(combo);
        container.add(Box.createVerticalStrut(4));
        categoryModel = (DefaultComboBoxModel<Category>) combo.getModel();
        return combo;
    }

    private JComboBox<TradeType> createTradeTypeCombo(JPanel container) {
        JLabel label = new JLabel("交易类型 *");
        label.setAlignmentX(LEFT_ALIGNMENT);
        JComboBox<TradeType> combo = new JComboBox<>(TradeType.values());
        combo.setRenderer((list, value, index, isSelected, cellHasFocus) ->
                new JLabel(tradeTypeLabel(value)));
        combo.setSelectedItem(TradeType.SELL);
        combo.setMaximumSize(new Dimension(Integer.MAX_VALUE, 28));
        label.setMaximumSize(new Dimension(Integer.MAX_VALUE, 22));
        container.add(label);
        container.add(combo);
        container.add(Box.createVerticalStrut(4));
        return combo;
    }

    /**
     * 异步加载分类下拉框。
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
                    categoryModel.removeAllElements();
                    for (Category c : get()) {
                        categoryModel.addElement(c);
                    }
                } catch (Exception e) {
                    LOGGER.log(Level.WARNING, "加载分类列表失败", e);
                }
            }
        }.execute();
    }

    /**
     * 在下拉框中选中指定分类 ID（编辑模式预填时使用）。
     */
    private void selectCategory(Long categoryId) {
        if (categoryId == null) {
            return;
        }
        for (int i = 0; i < categoryModel.getSize(); i++) {
            Category c = categoryModel.getElementAt(i);
            if (categoryId.equals(c.getCategoryId())) {
                categoryCombo.setSelectedIndex(i);
                return;
            }
        }
    }

    /**
     * 清空表单并回到新建模式。
     */
    private void clearForm() {
        nameField.setText("");
        priceField.setText("");
        tagsField.setText("");
        descArea.setText("");
        if (categoryCombo.getItemCount() > 0) {
            categoryCombo.setSelectedIndex(0);
        }
        tradeTypeCombo.setSelectedItem(TradeType.SELL);
        exitEditMode();
    }

    /**
     * 解析价格输入；非法输入弹窗提示并返回 {@link Double#MIN_VALUE}。
     */
    private double parsePrice(String text) {
        if (text.isEmpty()) {
            return 0.0; // 留空视为免费
        }
        try {
            double value = Double.parseDouble(text);
            String err = Validator.validatePrice(value);
            if (err != null) {
                JOptionPane.showMessageDialog(this, "价格：" + err,
                        "输入错误", JOptionPane.WARNING_MESSAGE);
                return Double.MIN_VALUE;
            }
            return value;
        } catch (NumberFormatException e) {
            JOptionPane.showMessageDialog(this, "价格必须是数字",
                    "输入错误", JOptionPane.WARNING_MESSAGE);
            return Double.MIN_VALUE;
        }
    }

    private static String tradeTypeLabel(TradeType type) {
        return switch (type) {
            case SELL   -> "出售";
            case DONATE -> "捐赠";
            case BORROW -> "借用";
        };
    }
}
