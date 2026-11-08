package com.lowcarbon.gui.panel;

import com.lowcarbon.entity.Item;
import com.lowcarbon.entity.Transaction;
import com.lowcarbon.entity.User;
import com.lowcarbon.exception.BusinessException;
import com.lowcarbon.service.ItemService;
import com.lowcarbon.service.ReviewService;
import com.lowcarbon.service.TransactionService;
import com.lowcarbon.util.Validator;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.SwingWorker;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Frame;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * 评价对话框（R-01 / R-02）。
 * <p>
 * 用于对 COMPLETED 状态的流转提交评价：1-5 星 + 文字评论（可选）。
 * 评价提交成功后，Service 层自动更新双方信用分。
 *
 * @author lowcarbon-team
 * @since 2026-11-09
 */
public class ReviewDialog extends JDialog {

    private static final Logger LOGGER = Logger.getLogger(ReviewDialog.class.getName());

    private final Transaction tx;
    private final com.lowcarbon.gui.MainFrame mainFrame;
    private final ReviewService reviewService;
    private final TransactionService txService;
    private final ItemService itemService;

    private final JComboBox<Integer> ratingCombo;
    private final JTextArea commentArea;
    private final JButton submitButton;

    /** 外部判断评价是否已提交成功。 */
    private boolean submitted;

    public ReviewDialog(Frame parent, Transaction tx,
                        com.lowcarbon.gui.MainFrame mainFrame,
                        ReviewService reviewService,
                        TransactionService txService,
                        ItemService itemService) {
        super(parent, "评价交易", true);
        this.tx = tx;
        this.mainFrame = mainFrame;
        this.reviewService = reviewService;
        this.txService = txService;
        this.itemService = itemService;

        setSize(480, 360);
        setMinimumSize(new Dimension(420, 320));
        setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);

        JPanel root = new JPanel(new BorderLayout(8, 8));
        root.setBorder(BorderFactory.createEmptyBorder(14, 16, 14, 16));
        setContentPane(root);

        // 标题（展示物品 + 对方）
        JPanel header = new JPanel();
        header.setLayout(new BoxLayout(header, BoxLayout.Y_AXIS));
        header.setBorder(BorderFactory.createTitledBorder("评价对象"));
        header.add(new JLabel(buildHeaderText()));
        root.add(header, BorderLayout.NORTH);

        // 中间表单
        JPanel formPanel = new JPanel();
        formPanel.setLayout(new BoxLayout(formPanel, BoxLayout.Y_AXIS));

        // 评分
        JLabel ratingLabel = new JLabel("评分（1 星最差，5 星最佳）：");
        ratingLabel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 22));
        formPanel.add(ratingLabel);
        Integer[] stars = {1, 2, 3, 4, 5};
        ratingCombo = new JComboBox<>(stars);
        ratingCombo.setSelectedItem(5);
        ratingCombo.setMaximumSize(new Dimension(200, 28));
        formPanel.add(ratingCombo);
        formPanel.add(Box.createVerticalStrut(10));

        // 评论
        JLabel commentLabel = new JLabel("评价内容（可选）：");
        commentLabel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 22));
        formPanel.add(commentLabel);
        commentArea = new JTextArea(5, 36);
        commentArea.setLineWrap(true);
        commentArea.setWrapStyleWord(true);
        formPanel.add(new JScrollPane(commentArea));
        root.add(formPanel, BorderLayout.CENTER);

        // 按钮
        JPanel buttonPanel = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.RIGHT, 8, 0));
        JButton cancelButton = new JButton("取消");
        submitButton = new JButton("提交评价");
        buttonPanel.add(cancelButton);
        buttonPanel.add(submitButton);
        root.add(buttonPanel, BorderLayout.SOUTH);

        cancelButton.addActionListener(e -> dispose());
        submitButton.addActionListener(e -> doSubmit());
    }

    private String buildHeaderText() {
        Item item = itemService.findById(tx.getItemId());
        StringBuilder sb = new StringBuilder();
        sb.append("记录 #").append(tx.getRecordId());
        sb.append(" | 物品：").append(item == null ? "(已不存在)" : item.getName());
        return sb.toString();
    }

    /** 外部轮询评价是否已提交成功（dispose 后调用）。 */
    public boolean isSubmitted() {
        return submitted;
    }

    private void doSubmit() {
        User me = mainFrame.getCurrentUser();
        if (me == null) {
            JOptionPane.showMessageDialog(this, "请先登录", "提示", JOptionPane.WARNING_MESSAGE);
            return;
        }

        Integer rating = (Integer) ratingCombo.getSelectedItem();
        if (rating == null) {
            JOptionPane.showMessageDialog(this, "请选择评分", "输入错误", JOptionPane.WARNING_MESSAGE);
            return;
        }

        // 评论长度校验（允许为空）
        String comment = commentArea.getText().trim();
        if (!comment.isEmpty() && comment.length() > Validator.MAX_COMMENT_LENGTH) {
            JOptionPane.showMessageDialog(this,
                    "评价内容最多 " + Validator.MAX_COMMENT_LENGTH + " 字",
                    "输入错误", JOptionPane.WARNING_MESSAGE);
            commentArea.requestFocus();
            return;
        }
        if (Validator.containsDangerousChars(comment)) {
            JOptionPane.showMessageDialog(this, "评价内容包含非法字符",
                    "输入错误", JOptionPane.WARNING_MESSAGE);
            return;
        }
        final String finalComment = comment.isEmpty() ? null : comment;

        submitButton.setEnabled(false);
        new SwingWorker<Void, Void>() {
            @Override
            protected Void doInBackground() {
                reviewService.submit(tx.getRecordId(), me.getUserId(), rating, finalComment);
                return null;
            }

            @Override
            protected void done() {
                submitButton.setEnabled(true);
                try {
                    get();
                    JOptionPane.showMessageDialog(ReviewDialog.this,
                            "评价已提交，感谢您的反馈！\n对方信用分已自动更新。",
                            "评价成功", JOptionPane.INFORMATION_MESSAGE);
                    submitted = true;
                    dispose();
                } catch (Exception e) {
                    Throwable cause = e.getCause() != null ? e.getCause() : e;
                    if (cause instanceof BusinessException) {
                        JOptionPane.showMessageDialog(ReviewDialog.this, cause.getMessage(),
                                "提交失败", JOptionPane.WARNING_MESSAGE);
                    } else {
                        LOGGER.log(Level.SEVERE, "提交评价异常", e);
                        JOptionPane.showMessageDialog(ReviewDialog.this,
                                "系统异常，请稍后再试", "错误", JOptionPane.ERROR_MESSAGE);
                    }
                }
            }
        }.execute();
    }
}
