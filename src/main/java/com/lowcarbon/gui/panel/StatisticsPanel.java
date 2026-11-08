package com.lowcarbon.gui.panel;

import com.lowcarbon.exception.BusinessException;
import com.lowcarbon.gui.MainFrame;
import com.lowcarbon.service.StatisticsService;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.ListSelectionModel;
import javax.swing.SwingConstants;
import javax.swing.SwingWorker;
import javax.swing.filechooser.FileSystemView;
import javax.swing.table.DefaultTableModel;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.io.File;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * 统计报表面板（S-05 ~ S-07、C-01、C-02）。
 * <p>
 * 功能：
 * <ul>
 *   <li>三标签页布局：总览指标 / 分布统计 / Top N 热门</li>
 *   <li>所有统计由 {@link StatisticsService} 一次性聚合，GUI 层只负责展示</li>
 *   <li>一键 CSV 导出（包含全部统计指标）</li>
 * </ul>
 *
 * @author lowcarbon-team
 * @since 2026-11-09
 */
public class StatisticsPanel extends JPanel {

    private static final Logger LOGGER = Logger.getLogger(StatisticsPanel.class.getName());

    private final MainFrame mainFrame;
    private final StatisticsService statsService;

    private final JButton refreshButton;
    private final JButton exportButton;
    private final JLabel loadingLabel;
    private final JTabbedPane tabPane;

    /** 总览卡片：指标名 → JLabel 数值展示。 */
    private final Map<String, JLabel> overviewValueLabels = new java.util.LinkedHashMap<>();

    /** 低碳卡片：指标名 → JLabel 数值展示。 */
    private final Map<String, JLabel> carbonValueLabels = new java.util.LinkedHashMap<>();

    /** 用户画像：指标名 → JLabel 数值展示。 */
    private final Map<String, JLabel> userProfileValueLabels = new java.util.LinkedHashMap<>();

    private JTable itemStatusTable;
    private JTable txStatusTable;
    private JTable itemsByCategoryTable;
    private JTable amountByCategoryTable;
    private JTable topSellersTable;
    private JTable topReviewersTable;
    private JTable topReservedTable;

    public StatisticsPanel(MainFrame mainFrame) {
        this.mainFrame = mainFrame;
        this.statsService = new StatisticsService();

        setLayout(new BorderLayout(4, 4));

        // 标题
        JLabel titleLabel = new JLabel("统计报表", SwingConstants.CENTER);
        titleLabel.setFont(titleLabel.getFont().deriveFont(java.awt.Font.BOLD, 18f));
        add(titleLabel, BorderLayout.NORTH);

        // 顶部操作条
        JPanel topBar = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 2));
        refreshButton = new JButton("刷新统计");
        exportButton = new JButton("导出 CSV");
        loadingLabel = new JLabel("");
        topBar.add(refreshButton);
        topBar.add(exportButton);
        topBar.add(loadingLabel);
        add(topBar, BorderLayout.NORTH);

        // TabbedPane
        tabPane = new JTabbedPane();
        tabPane.addTab("总览", buildOverviewTab());
        tabPane.addTab("分布统计", buildDistributionTab());
        tabPane.addTab("Top N 热门", buildTopNTab());
        add(tabPane, BorderLayout.CENTER);

        refreshButton.addActionListener(e -> refresh());
        exportButton.addActionListener(e -> doExport());
    }

    // ==================== 标签页构建 ====================

    private JScrollPane buildOverviewTab() {
        JPanel root = new JPanel();
        root.setLayout(new BoxLayout(root, BoxLayout.Y_AXIS));

        // --- 系统总览 ---
        JPanel overviewCard = new JPanel(new GridBagLayout());
        overviewCard.setBorder(BorderFactory.createTitledBorder("一、系统总览"));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(4, 10, 4, 10);
        gbc.fill = GridBagConstraints.HORIZONTAL;

        String[] overviewKeys = {
                "用户总数", "物品总数", "在售物品数", "成交物品数",
                "成交金额(元)", "流转总数", "已完成流转", "评价总数", "平均评分"
        };
        for (int i = 0; i < overviewKeys.length; i++) {
            gbc.gridx = 0;
            gbc.gridy = i;
            overviewCard.add(new JLabel(overviewKeys[i] + "："), gbc);
            gbc.gridx = 1;
            JLabel val = new JLabel("--");
            val.setFont(val.getFont().deriveFont(java.awt.Font.BOLD, 14f));
            overviewCard.add(val, gbc);
            overviewValueLabels.put(overviewKeys[i], val);
        }
        root.add(overviewCard);
        root.add(Box.createVerticalStrut(10));

        // --- 低碳指标 ---
        JPanel carbonCard = new JPanel(new GridBagLayout());
        carbonCard.setBorder(BorderFactory.createTitledBorder("二、低碳指标（C-01）"));
        String[] carbonKeys = {
                "成交物品数",
                "累计节约碳排放(kg CO₂e)",
                "相当于成年树木年吸碳(棵)"
        };
        gbc.gridy = 0;
        for (int i = 0; i < carbonKeys.length; i++) {
            gbc.gridx = 0;
            gbc.gridy = i;
            carbonCard.add(new JLabel(carbonKeys[i] + "："), gbc);
            gbc.gridx = 1;
            JLabel val = new JLabel("--");
            val.setFont(val.getFont().deriveFont(java.awt.Font.BOLD, 14f));
            carbonCard.add(val, gbc);
            carbonValueLabels.put(carbonKeys[i], val);
        }
        root.add(carbonCard);
        root.add(Box.createVerticalStrut(10));

        // --- 用户画像 ---
        JPanel profileCard = new JPanel(new GridBagLayout());
        profileCard.setBorder(BorderFactory.createTitledBorder("三、用户画像"));
        String[] profileKeys = {
                "用户总数", "平均信用分",
                "低信用(<80)", "中信用(80~120)", "高信用(>120)"
        };
        gbc.gridy = 0;
        for (int i = 0; i < profileKeys.length; i++) {
            gbc.gridx = 0;
            gbc.gridy = i;
            profileCard.add(new JLabel(profileKeys[i] + "："), gbc);
            gbc.gridx = 1;
            JLabel val = new JLabel("--");
            val.setFont(val.getFont().deriveFont(java.awt.Font.BOLD, 14f));
            profileCard.add(val, gbc);
            userProfileValueLabels.put(profileKeys[i], val);
        }
        root.add(profileCard);
        root.add(Box.createVerticalGlue());

        return new JScrollPane(root);
    }

    private JScrollPane buildDistributionTab() {
        JPanel root = new JPanel();
        root.setLayout(new BoxLayout(root, BoxLayout.Y_AXIS));
        root.setBorder(BorderFactory.createEmptyBorder(6, 6, 6, 6));

        itemStatusTable = createTwoColTable(new String[]{"状态", "数量"});
        txStatusTable = createTwoColTable(new String[]{"状态", "数量"});
        itemsByCategoryTable = createTwoColTable(new String[]{"分类", "物品数"});
        amountByCategoryTable = createTwoColTable(new String[]{"分类", "成交金额(元)"});

        root.add(wrapTitled("物品状态分布", new JScrollPane(itemStatusTable)));
        root.add(Box.createVerticalStrut(8));
        root.add(wrapTitled("流转状态分布", new JScrollPane(txStatusTable)));
        root.add(Box.createVerticalStrut(8));
        root.add(wrapTitled("各分类物品数", new JScrollPane(itemsByCategoryTable)));
        root.add(Box.createVerticalStrut(8));
        root.add(wrapTitled("各分类成交金额", new JScrollPane(amountByCategoryTable)));
        root.add(Box.createVerticalGlue());

        return new JScrollPane(root);
    }

    private JScrollPane buildTopNTab() {
        JPanel root = new JPanel();
        root.setLayout(new BoxLayout(root, BoxLayout.Y_AXIS));
        root.setBorder(BorderFactory.createEmptyBorder(6, 6, 6, 6));

        topSellersTable = createTable(new String[]{"排名", "用户ID", "用户名", "成交件数", "成交金额"});
        topReviewersTable = createTable(new String[]{"排名", "用户ID", "用户名", "评价次数"});
        topReservedTable = createTable(new String[]{"排名", "物品ID", "物品名称", "所属分类", "预定次数"});

        root.add(wrapTitled("热门卖家 Top 10", new JScrollPane(topSellersTable)));
        root.add(Box.createVerticalStrut(8));
        root.add(wrapTitled("热门评价者 Top 10", new JScrollPane(topReviewersTable)));
        root.add(Box.createVerticalStrut(8));
        root.add(wrapTitled("热门预定物品 Top 10", new JScrollPane(topReservedTable)));
        root.add(Box.createVerticalGlue());

        return new JScrollPane(root);
    }

    // ==================== 刷新 ====================

    /**
     * 异步刷新全部统计数据。供外部（导航到本面板时）调用。
     */
    public void refresh() {
        refreshButton.setEnabled(false);
        exportButton.setEnabled(false);
        loadingLabel.setText("加载中...");
        new SwingWorker<AggregatedStats, Void>() {
            @Override
            protected AggregatedStats doInBackground() {
                AggregatedStats s = new AggregatedStats();
                s.overview = statsService.overview();
                s.carbon = statsService.carbonImpact();
                s.profile = statsService.userProfile();
                s.itemStatus = statsService.itemStatusDistribution();
                s.txStatus = statsService.transactionStatusDistribution();
                s.byCat = statsService.itemsByCategory();
                s.amountByCat = statsService.amountByCategory();
                s.topSellers = statsService.topSellers(10);
                s.topReviewers = statsService.topReviewers(10);
                s.topReserved = statsService.topReservedItems(10);
                return s;
            }

            @Override
            protected void done() {
                refreshButton.setEnabled(true);
                exportButton.setEnabled(true);
                loadingLabel.setText("");
                try {
                    applyData(get());
                } catch (Exception e) {
                    Throwable cause = e.getCause() != null ? e.getCause() : e;
                    if (cause instanceof BusinessException) {
                        JOptionPane.showMessageDialog(StatisticsPanel.this, cause.getMessage(),
                                "加载失败", JOptionPane.WARNING_MESSAGE);
                    } else {
                        LOGGER.log(Level.SEVERE, "加载统计数据异常", e);
                        JOptionPane.showMessageDialog(StatisticsPanel.this, "系统异常，请稍后再试",
                                "错误", JOptionPane.ERROR_MESSAGE);
                    }
                }
            }
        }.execute();
    }

    private void applyData(AggregatedStats s) {
        // 总览数值
        for (Map.Entry<String, JLabel> e : overviewValueLabels.entrySet()) {
            Object v = s.overview.get(e.getKey());
            e.getValue().setText(v == null ? "--" : String.valueOf(v));
        }
        for (Map.Entry<String, JLabel> e : carbonValueLabels.entrySet()) {
            Object v = s.carbon.get(e.getKey());
            e.getValue().setText(v == null ? "--" : String.valueOf(v));
        }
        for (Map.Entry<String, JLabel> e : userProfileValueLabels.entrySet()) {
            Object v = s.profile.get(e.getKey());
            e.getValue().setText(v == null ? "--" : String.valueOf(v));
        }

        fillTwoColTable(itemStatusTable, s.itemStatus);
        fillTwoColTable(txStatusTable, s.txStatus);
        fillTwoColTable(itemsByCategoryTable, s.byCat);
        fillTwoColTable(amountByCategoryTable, s.amountByCat);

        fillTopSellersTable(topSellersTable, s.topSellers);
        fillTopReviewersTable(topReviewersTable, s.topReviewers);
        fillTopReservedTable(topReservedTable, s.topReserved);
    }

    // ==================== CSV 导出 ====================

    private void doExport() {
        JFileChooser chooser = new JFileChooser(FileSystemView.getFileSystemView().getHomeDirectory());
        chooser.setDialogTitle("导出统计报表");
        chooser.setSelectedFile(new File("lowcarbon_stats_"
                + new java.text.SimpleDateFormat("yyyyMMdd_HHmmss").format(new java.util.Date())
                + ".csv"));
        int choice = chooser.showSaveDialog(this);
        if (choice != JFileChooser.APPROVE_OPTION) return;

        File file = chooser.getSelectedFile();
        exportButton.setEnabled(false);
        new SwingWorker<Void, Void>() {
            @Override
            protected Void doInBackground() {
                statsService.exportAll(file.toPath());
                return null;
            }

            @Override
            protected void done() {
                exportButton.setEnabled(true);
                try {
                    get();
                    JOptionPane.showMessageDialog(StatisticsPanel.this,
                            "导出成功：\n" + file.getAbsolutePath(),
                            "导出成功", JOptionPane.INFORMATION_MESSAGE);
                } catch (Exception e) {
                    Throwable cause = e.getCause() != null ? e.getCause() : e;
                    if (cause instanceof BusinessException) {
                        JOptionPane.showMessageDialog(StatisticsPanel.this, cause.getMessage(),
                                "导出失败", JOptionPane.WARNING_MESSAGE);
                    } else {
                        LOGGER.log(Level.SEVERE, "CSV 导出异常", e);
                        JOptionPane.showMessageDialog(StatisticsPanel.this,
                                "系统异常，请稍后再试", "错误", JOptionPane.ERROR_MESSAGE);
                    }
                }
            }
        }.execute();
    }

    // ==================== 表格构建与填充 ====================

    private JTable createTwoColTable(String[] colNames) {
        return createTable(colNames);
    }

    private JTable createTable(String[] colNames) {
        DefaultTableModel model = new DefaultTableModel(colNames, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
        JTable table = new JTable(model);
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.setRowHeight(20);
        table.setAutoResizeMode(JTable.AUTO_RESIZE_SUBSEQUENT_COLUMNS);
        table.setPreferredScrollableViewportSize(new Dimension(500, 150));
        return table;
    }

    private void fillTwoColTable(JTable table, Map<String, ? extends Number> data) {
        DefaultTableModel model = (DefaultTableModel) table.getModel();
        model.setRowCount(0);
        for (Map.Entry<String, ? extends Number> e : data.entrySet()) {
            model.addRow(new Object[]{e.getKey(), e.getValue()});
        }
    }

    private void fillTopSellersTable(JTable table, List<String[]> data) {
        DefaultTableModel model = (DefaultTableModel) table.getModel();
        model.setRowCount(0);
        int rank = 1;
        for (String[] row : data) {
            model.addRow(new Object[]{rank++, row[0], row[1], row[2], row[3]});
        }
    }

    private void fillTopReviewersTable(JTable table, List<String[]> data) {
        DefaultTableModel model = (DefaultTableModel) table.getModel();
        model.setRowCount(0);
        int rank = 1;
        for (String[] row : data) {
            model.addRow(new Object[]{rank++, row[0], row[1], row[2]});
        }
    }

    private void fillTopReservedTable(JTable table, List<String[]> data) {
        DefaultTableModel model = (DefaultTableModel) table.getModel();
        model.setRowCount(0);
        int rank = 1;
        for (String[] row : data) {
            model.addRow(new Object[]{rank++, row[0], row[1], row[2], row[3]});
        }
    }

    // ==================== 辅助 ====================

    private JPanel wrapTitled(String title, java.awt.Component component) {
        JPanel p = new JPanel(new BorderLayout());
        p.setBorder(BorderFactory.createTitledBorder(title));
        p.add(component, BorderLayout.CENTER);
        return p;
    }

    /** 聚合一次刷新所需的全部数据（避免 Service 层重复遍历 DAO）。 */
    private static class AggregatedStats {
        Map<String, Object> overview;
        Map<String, Object> carbon;
        Map<String, Object> profile;
        Map<String, Long> itemStatus;
        Map<String, Long> txStatus;
        Map<String, Long> byCat;
        Map<String, Double> amountByCat;
        List<String[]> topSellers;
        List<String[]> topReviewers;
        List<String[]> topReserved;
    }
}
