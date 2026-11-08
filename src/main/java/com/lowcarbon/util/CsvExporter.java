package com.lowcarbon.util;

import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * CSV 导出工具类。
 * <p>
 * 提供将二维字符串表导出为 CSV 文件的能力，用于统计报表导出。
 * 自动处理字段内包含逗号/引号/换行的情况（按 RFC 4180 加引号并转义）。
 *
 * @author lowcarbon-team
 * @since 2026-11-09
 */
public final class CsvExporter {

    private static final Logger LOGGER = Logger.getLogger(CsvExporter.class.getName());

    private static final char FIELD_SEPARATOR = ',';
    private static final char QUOTE = '"';

    private CsvExporter() {
        // 工具类禁止实例化
    }

    /**
     * 导出 CSV 到指定路径（UTF-8 带 BOM，确保 Excel 正确识别中文）。
     * <p>
     * 覆盖写入。不存在的父目录自动创建。
     *
     * @param path    目标文件路径（.csv 结尾）
     * @param header  表头行（列名）
     * @param rows    数据行列表
     * @throws IOException 写入失败时
     */
    public static void export(Path path, List<String> header, List<List<String>> rows) throws IOException {
        if (path == null || header == null || rows == null) {
            throw new IllegalArgumentException("参数不能为 null");
        }
        // 确保父目录存在
        Path parent = path.getParent();
        if (parent != null && !Files.exists(parent)) {
            Files.createDirectories(parent);
        }

        try (OutputStream os = Files.newOutputStream(path, StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE)) {
            // 写 UTF-8 BOM，Excel 打开中文 CSV 需要
            os.write(new byte[]{(byte) 0xEF, (byte) 0xBB, (byte) 0xBF});

            try (PrintWriter writer = new PrintWriter(
                    new OutputStreamWriter(os, StandardCharsets.UTF_8), false)) {
                // 表头
                writer.println(formatRow(header));
                // 数据行
                for (List<String> row : rows) {
                    writer.println(formatRow(row));
                }
            }
        }
        LOGGER.log(Level.INFO, "CSV 导出成功: path={0}, rows={1}",
                new Object[]{path, rows.size()});
    }

    /**
     * 格式化一行：各字段 CSV 编码后用逗号连接。
     */
    private static String formatRow(List<String> fields) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < fields.size(); i++) {
            if (i > 0) sb.append(FIELD_SEPARATOR);
            sb.append(escape(fields.get(i)));
        }
        return sb.toString();
    }

    /**
     * 单个字段的 CSV 转义（RFC 4180）：
     * 若含逗号/引号/换行/回车，则整体加引号，内部引号加倍。
     */
    private static String escape(String field) {
        if (field == null) {
            return "";
        }
        boolean needsQuote = field.indexOf(FIELD_SEPARATOR) >= 0
                || field.indexOf(QUOTE) >= 0
                || field.indexOf('\n') >= 0
                || field.indexOf('\r') >= 0;
        if (!needsQuote) {
            return field;
        }
        // 内部引号加倍
        String doubled = field.replace(String.valueOf(QUOTE), String.valueOf(QUOTE) + QUOTE);
        return QUOTE + doubled + QUOTE;
    }
}
