package com.lowcarbon.service;

import com.lowcarbon.dao.CategoryDao;
import com.lowcarbon.dao.ItemDao;
import com.lowcarbon.dao.ReviewDao;
import com.lowcarbon.dao.TransactionDao;
import com.lowcarbon.dao.UserDao;
import com.lowcarbon.entity.Category;
import com.lowcarbon.entity.Item;
import com.lowcarbon.entity.ItemStatus;
import com.lowcarbon.entity.Review;
import com.lowcarbon.entity.Transaction;
import com.lowcarbon.entity.TransactionStatus;
import com.lowcarbon.entity.User;
import com.lowcarbon.exception.BusinessException;
import com.lowcarbon.exception.DaoException;
import com.lowcarbon.factory.DaoFactory;
import com.lowcarbon.util.CsvExporter;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * 统计与报表模块业务逻辑层。
 * <p>
 * 覆盖需求：S-05 统计总览、S-06 分类分布、S-07 热门 Top N、C-01 碳节约量估算、C-02 CSV 导出。
 * <p>
 * 设计决策：通过 DAO 拿全量列表 → Java Stream 内存聚合。
 * 桌面系统数据量在几百~几千量级，内存聚合足够快；DAO 接口保持简洁，Fake DAO 测试不必实现专用 countByXxx 方法。
 *
 * @author lowcarbon-team
 * @since 2026-11-09
 */
public class StatisticsService {

    private static final Logger LOGGER = Logger.getLogger(StatisticsService.class.getName());

    /** 每件成交物品估算节约碳排放量（kg CO₂e）。
     *  <p>
     *  依据：一件教科书从造纸→印刷→运输约产生 5-8kg CO₂，二手流转相当于节约这部分。
     *  取值 6kg/件作为合理估算值。 */
    public static final double KG_CO2E_PER_ITEM = 6.0;

    private final UserDao userDao;
    private final ItemDao itemDao;
    private final TransactionDao transactionDao;
    private final ReviewDao reviewDao;
    private final CategoryDao categoryDao;

    public StatisticsService() {
        this.userDao = DaoFactory.getUserDao();
        this.itemDao = DaoFactory.getItemDao();
        this.transactionDao = DaoFactory.getTransactionDao();
        this.reviewDao = DaoFactory.getReviewDao();
        this.categoryDao = DaoFactory.getCategoryDao();
    }

    /** 测试用注入构造器。 */
    public StatisticsService(UserDao userDao, ItemDao itemDao,
                              TransactionDao transactionDao, ReviewDao reviewDao,
                              CategoryDao categoryDao) {
        this.userDao = userDao;
        this.itemDao = itemDao;
        this.transactionDao = transactionDao;
        this.reviewDao = reviewDao;
        this.categoryDao = categoryDao;
    }

    // ==================== 1. 总览指标（S-05） ====================

    /**
     * 系统总览，返回 9 个核心指标。
     * <p>
     * 指标口径：
     * <ul>
     *   <li>用户总数：user 表全部</li>
     *   <li>物品总数：item 表全部</li>
     *   <li>在售物品数：item.status = ON_SALE</li>
     *   <li>成交物品数：item.status = SOLD（流转成功最终态）</li>
     *   <li>成交金额：SOLD 物品 price 求和（元）</li>
     *   <li>流转总数：transaction_record 全部</li>
     *   <li>已完成流转：status = COMPLETED</li>
     *   <li>评价总数：review 表全部</li>
     *   <li>平均评分：review.rating 平均值（所有已提交评价）</li>
     * </ul>
     *
     * @return 有序 Map（保持插入顺序 = 上面的口径顺序）
     */
    public Map<String, Object> overview() {
        List<User> users = users();
        List<Item> items = items();
        List<Transaction> txs = transactions();
        List<Review> reviews = reviews();

        List<Item> soldItems = items.stream()
                .filter(i -> i.getStatus() == ItemStatus.SOLD).toList();

        double totalAmount = soldItems.stream()
                .mapToDouble(Item::getPrice).sum();

        List<Transaction> completedTxs = txs.stream()
                .filter(t -> t.getStatus() == TransactionStatus.COMPLETED).toList();

        double avgRating = reviews.isEmpty() ? 0.0 : reviews.stream()
                .mapToInt(Review::getRating).average().orElse(0.0);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("用户总数", users.size());
        result.put("物品总数", items.size());
        result.put("在售物品数", items.stream().filter(i -> i.getStatus() == ItemStatus.ON_SALE).count());
        result.put("成交物品数", soldItems.size());
        result.put("成交金额(元)", round2(totalAmount));
        result.put("流转总数", txs.size());
        result.put("已完成流转", completedTxs.size());
        result.put("评价总数", reviews.size());
        result.put("平均评分", round2(avgRating));
        return result;
    }

    /** 低碳指标：累计节约碳排放量（kg CO₂e）。 */
    public Map<String, Object> carbonImpact() {
        List<Item> soldItems = items().stream()
                .filter(i -> i.getStatus() == ItemStatus.SOLD).toList();

        double savedKg = soldItems.size() * KG_CO2E_PER_ITEM;

        // 补充一个"相当于少种/多种多少棵树"的直观指标（一棵成年树年吸碳 ~21kg）
        double treesEquivalent = savedKg / 21.0;

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("成交物品数", soldItems.size());
        result.put("累计节约碳排放(kg CO₂e)", round2(savedKg));
        result.put("相当于成年树木年吸碳(棵)", round2(treesEquivalent));
        return result;
    }

    /**
     * 用户画像：平均信用分 + 信用分布（低于 80 / 80-120 / 高于 120 的人数）。
     */
    public Map<String, Object> userProfile() {
        List<User> users = users();
        double avgCredit = users.isEmpty() ? 0.0 : users.stream()
                .mapToInt(User::getCreditScore).average().orElse(0.0);

        long low = users.stream().filter(u -> u.getCreditScore() < 80).count();
        long mid = users.stream().filter(u -> u.getCreditScore() >= 80 && u.getCreditScore() <= 120).count();
        long high = users.stream().filter(u -> u.getCreditScore() > 120).count();

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("用户总数", users.size());
        result.put("平均信用分", round2(avgCredit));
        result.put("低信用(<80)", low);
        result.put("中信用(80~120)", mid);
        result.put("高信用(>120)", high);
        return result;
    }

    // ==================== 2. 分布统计（S-06） ====================

    /**
     * 物品按状态分布。
     * <p>返回 Map: key = 状态中文标签，value = 数量。按"在售 → 已预订 → 已成交 → 已下架"顺序。 */
    public Map<String, Long> itemStatusDistribution() {
        Map<String, Long> result = new LinkedHashMap<>();
        // 按状态分组计数
        List<Item> items = items();
        Map<ItemStatus, Long> grouped = items.stream()
                .collect(Collectors.groupingBy(Item::getStatus, Collectors.counting()));
        // 固定顺序输出，防止 Enum.values() 顺序被破坏
        for (ItemStatus s : ItemStatus.values()) {
            result.put(itemStatusLabel(s), grouped.getOrDefault(s, 0L));
        }
        return result;
    }

    /** 流转按状态分布（固定顺序：待确认 → 已同意 → 已拒绝 → 已完成 → 已取消）。 */
    public Map<String, Long> transactionStatusDistribution() {
        List<Transaction> txs = transactions();
        Map<TransactionStatus, Long> grouped = txs.stream()
                .collect(Collectors.groupingBy(Transaction::getStatus, Collectors.counting()));

        Map<String, Long> result = new LinkedHashMap<>();
        for (TransactionStatus s : TransactionStatus.values()) {
            result.put(txStatusLabel(s), grouped.getOrDefault(s, 0L));
        }
        return result;
    }

    /**
     * 各分类下物品数（S-06）。
     * <p>按物品数降序。包含所有分类（物品数为 0 的也列出）。 */
    public Map<String, Long> itemsByCategory() {
        List<Category> categories = categories();
        List<Item> items = items();

        // 先按 category_id 聚合
        Map<Long, Long> countsByCatId = items.stream()
                .collect(Collectors.groupingBy(Item::getCategoryId, Collectors.counting()));

        // 组装 + 按数量降序
        Map<String, Long> result = new LinkedHashMap<>();
        categories.stream()
                .sorted(Comparator.<Category, Long>comparing(c -> countsByCatId.getOrDefault(c.getCategoryId(), 0L)).reversed())
                .forEach(c -> result.put(c.getName(), countsByCatId.getOrDefault(c.getCategoryId(), 0L)));
        return result;
    }

    /** 各分类下成交金额（仅 SOLD 物品，按 category 聚合 price 求和，降序）。 */
    public Map<String, Double> amountByCategory() {
        List<Item> soldItems = items().stream()
                .filter(i -> i.getStatus() == ItemStatus.SOLD).toList();
        List<Category> categories = categories();
        Map<Long, String> idToName = categories.stream()
                .collect(Collectors.toMap(Category::getCategoryId, Category::getName));

        // categoryId → 金额求和
        Map<Long, Double> byCat = soldItems.stream()
                .collect(Collectors.groupingBy(Item::getCategoryId,
                        Collectors.summingDouble(Item::getPrice)));

        return categories.stream()
                .sorted(Comparator.<Category, Double>comparing(c -> byCat.getOrDefault(c.getCategoryId(), 0.0)).reversed())
                .collect(Collectors.toMap(
                        c -> idToName.getOrDefault(c.getCategoryId(), "未知分类"),
                        c -> round2(byCat.getOrDefault(c.getCategoryId(), 0.0)),
                        (a, b) -> a,
                        LinkedHashMap::new));
    }

    // ==================== 3. Top N 热门（S-07） ====================

    /**
     * 热门卖家：成交物品最多的 Top N 用户。
     * <p>
     * 口径：item.status = SOLD 的物品，按 publisher_id 分组计数。
     *
     * @param n Top N 数量
     * @return List of  [rank, username, soldCount, totalAmount]
     */
    public List<String[]> topSellers(int n) {
        if (n <= 0) n = 10;
        List<Item> soldItems = items().stream()
                .filter(i -> i.getStatus() == ItemStatus.SOLD).toList();
        List<User> users = users();
        Map<Long, String> idToName = users.stream()
                .collect(Collectors.toMap(User::getUserId, User::getUsername, (a, b) -> a));

        Map<Long, List<Item>> grouped = soldItems.stream()
                .collect(Collectors.groupingBy(Item::getPublisherId));

        return grouped.entrySet().stream()
                .sorted(Comparator.comparingInt((Map.Entry<Long, List<Item>> e) -> e.getValue().size()).reversed())
                .limit(n)
                .map(e -> {
                    double amt = e.getValue().stream().mapToDouble(Item::getPrice).sum();
                    return new String[]{
                            String.valueOf(e.getKey()),
                            idToName.getOrDefault(e.getKey(), "未知用户"),
                            String.valueOf(e.getValue().size()),
                            String.valueOf(round2(amt))
                    };
                }).toList();
    }

    /** 热门评价者：发出评价最多的 Top N 用户。 */
    public List<String[]> topReviewers(int n) {
        if (n <= 0) n = 10;
        List<Review> reviews = reviews();
        List<User> users = users();
        Map<Long, String> idToName = users.stream()
                .collect(Collectors.toMap(User::getUserId, User::getUsername, (a, b) -> a));

        Map<Long, Long> grouped = reviews.stream()
                .collect(Collectors.groupingBy(Review::getReviewerId, Collectors.counting()));

        return grouped.entrySet().stream()
                .sorted(Comparator.comparingLong((Map.Entry<Long, Long> e) -> e.getValue()).reversed())
                .limit(n)
                .map(e -> new String[]{
                        String.valueOf(e.getKey()),
                        idToName.getOrDefault(e.getKey(), "未知用户"),
                        String.valueOf(e.getValue())
                }).toList();
    }

    /**
     * 热门物品：被预定最多（PENDING+APPROVED）的 Top N。
     * <p>口径：transaction_record 中 status ∈ {PENDING, APPROVED}，按 item_id 分组计数。
     *
     * @return List of [itemId, itemName, category, reservedCount]
     */
    public List<String[]> topReservedItems(int n) {
        if (n <= 0) n = 10;
        List<Transaction> activeTxs = transactions().stream()
                .filter(t -> t.getStatus() == TransactionStatus.PENDING
                        || t.getStatus() == TransactionStatus.APPROVED).toList();
        List<Item> items = items();
        List<Category> cats = categories();

        Map<Long, String> itemIdToName = items.stream()
                .collect(Collectors.toMap(Item::getItemId, Item::getName, (a, b) -> a));
        Map<Long, Long> itemIdToCatId = items.stream()
                .collect(Collectors.toMap(Item::getItemId, Item::getCategoryId, (a, b) -> a));
        Map<Long, String> catIdToName = cats.stream()
                .collect(Collectors.toMap(Category::getCategoryId, Category::getName, (a, b) -> a));

        Map<Long, Long> grouped = activeTxs.stream()
                .collect(Collectors.groupingBy(Transaction::getItemId, Collectors.counting()));

        return grouped.entrySet().stream()
                .sorted(Comparator.comparingLong((Map.Entry<Long, Long> e) -> e.getValue()).reversed())
                .limit(n)
                .map(e -> new String[]{
                        String.valueOf(e.getKey()),
                        itemIdToName.getOrDefault(e.getKey(), "未知物品"),
                        catIdToName.getOrDefault(itemIdToCatId.get(e.getKey()), "未知分类"),
                        String.valueOf(e.getValue())
                }).toList();
    }

    // ==================== 4. CSV 导出（C-02） ====================

    /**
     * 一键导出全部统计指标到 CSV。
     *
     * @param filePath 目标路径（如 "./stats_20261109.csv"）
     */
    public void exportAll(Path filePath) {
        try {
            List<String> header = new ArrayList<>();
            header.add("分类");
            header.add("指标");
            header.add("数值");

            List<List<String>> rows = new ArrayList<>();

            addSection(rows, "一、系统总览", overview());
            addSection(rows, "二、低碳指标", carbonImpact());
            addSection(rows, "三、用户画像", userProfile());

            addDistributionRows(rows, "四、物品状态分布", itemStatusDistribution());
            addDistributionRows(rows, "五、流转状态分布", transactionStatusDistribution());
            addDistributionRows(rows, "六、分类物品数", itemsByCategory());
            addDistributionRows(rows, "七、分类成交额", amountByCategory());

            addTopNRows(rows, "八、热门卖家 Top10", topSellers(10),
                    new String[]{"用户ID", "用户名", "成交件数", "成交金额"});
            addTopNRows(rows, "九、热门评价者 Top10", topReviewers(10),
                    new String[]{"用户ID", "用户名", "评价次数"});
            addTopNRows(rows, "十、热门预定物品 Top10", topReservedItems(10),
                    new String[]{"物品ID", "物品名称", "所属分类", "预定次数"});

            CsvExporter.export(filePath, header, rows);
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "CSV 导出失败: path=" + filePath, e);
            throw new BusinessException("导出 CSV 失败：" + e.getMessage(), e);
        }
    }

    /** 便捷重载：String 路径自动转 Path。 */
    public void exportAll(String filePath) {
        exportAll(Paths.get(filePath));
    }

    // ==================== 辅助 ====================

    private void addSection(List<List<String>> rows, String sectionName, Map<String, Object> data) {
        for (Map.Entry<String, Object> e : data.entrySet()) {
            List<String> row = new ArrayList<>(3);
            row.add(sectionName);
            row.add(e.getKey());
            row.add(String.valueOf(e.getValue()));
            rows.add(row);
        }
    }

    private void addDistributionRows(List<List<String>> rows, String sectionName,
                                     Map<String, ? extends Number> data) {
        for (Map.Entry<String, ? extends Number> e : data.entrySet()) {
            List<String> row = new ArrayList<>(3);
            row.add(sectionName);
            row.add(e.getKey());
            row.add(String.valueOf(e.getValue()));
            rows.add(row);
        }
    }

    private void addTopNRows(List<List<String>> rows, String sectionName,
                             List<String[]> data, String[] subHeader) {
        // 子表头
        rows.add(List.of(sectionName, String.join(" | ", subHeader), ""));
        for (String[] row : data) {
            rows.add(List.of(sectionName, String.join(" | ", row), ""));
        }
    }

    private static double round2(double v) {
        return Math.round(v * 100.0) / 100.0;
    }

    private static String itemStatusLabel(ItemStatus s) {
        return switch (s) {
            case ON_SALE   -> "在售";
            case RESERVED  -> "已预订";
            case SOLD      -> "已成交";
            case OFF_SHELF -> "已下架";
        };
    }

    private static String txStatusLabel(TransactionStatus s) {
        return switch (s) {
            case PENDING   -> "待确认";
            case APPROVED  -> "已同意";
            case REJECTED  -> "已拒绝";
            case COMPLETED -> "已完成";
            case CANCELLED -> "已取消";
        };
    }

    // ==================== DAO 调用（统一 catch + 转 BusinessException） ====================

    private List<User> users() {
        try { return userDao.findAll(); }
        catch (DaoException e) { throw new BusinessException("获取用户列表失败", e); }
    }

    private List<Item> items() {
        try { return itemDao.findAll(); }
        catch (DaoException e) { throw new BusinessException("获取物品列表失败", e); }
    }

    private List<Transaction> transactions() {
        try { return transactionDao.findAll(); }
        catch (DaoException e) { throw new BusinessException("获取流转记录失败", e); }
    }

    private List<Review> reviews() {
        try { return reviewDao.findAll(); }
        catch (DaoException e) { throw new BusinessException("获取评价列表失败", e); }
    }

    private List<Category> categories() {
        try { return categoryDao.findAll(); }
        catch (DaoException e) { throw new BusinessException("获取分类列表失败", e); }
    }
}
