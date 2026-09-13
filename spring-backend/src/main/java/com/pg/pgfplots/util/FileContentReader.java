package com.pg.pgfplots.util;

import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * 数据集文件内容解析，对应 Node 的 {@code readFileContent}。
 * <ul>
 *   <li>.xlsx/.xls：解析首个工作表为表格文本</li>
 *   <li>.csv：UTF-8 文本，前缀「CSV文件内容：」</li>
 *   <li>其他：UTF-8 文本，前缀「文件内容：」</li>
 * </ul>
 */
@Component
@RequiredArgsConstructor
public class FileContentReader {

    private static final long MAX_SIZE = 100_000_000L;

    private final FileStorage fileStorage;
    private final SystemLogWriter systemLogWriter;

    /**
     * 读取并格式化文件内容。
     *
     * @param storedPath 数据库中的 file_path
     * @throws IllegalStateException 文件不存在 / 过大 / 解析失败
     */
    public String read(String storedPath) {
        Path path = fileStorage.resolveStored(storedPath);
        if (path == null || !Files.exists(path)) {
            throw new IllegalStateException("文件不存在");
        }

        long size;
        try {
            size = Files.size(path);
        } catch (IOException e) {
            throw new IllegalStateException("解析文件失败: " + e.getMessage());
        }
        if (size > MAX_SIZE) {
            throw new IllegalStateException("文件过大，最大支持 " + MAX_SIZE + " 字节");
        }

        String name = path.getFileName().toString().toLowerCase();
        try {
            if (name.endsWith(".xlsx") || name.endsWith(".xls")) {
                return readExcel(path);
            }
            String data = Files.readString(path, StandardCharsets.UTF_8);
            return name.endsWith(".csv") ? ("CSV文件内容：\n" + data) : ("文件内容：\n" + data);
        } catch (Exception e) {
            systemLogWriter.error("[CHAT] 解析文件失败: " + e.getMessage());
            throw new IllegalStateException("解析文件失败: " + e.getMessage());
        }
    }

    /** 解析 Excel 首个工作表，输出「行N: [单元格,...]」格式。 */
    private String readExcel(Path path) throws IOException {
        try (Workbook workbook = WorkbookFactory.create(path.toFile())) {
            Sheet sheet = workbook.getSheetAt(0);
            StringBuilder sb = new StringBuilder("Excel文件内容（解析为表格格式）：\n");
            for (int r = 0; r <= sheet.getLastRowNum(); r++) {
                Row row = sheet.getRow(r);
                List<String> cells = new ArrayList<>();
                if (row != null) {
                    for (int c = row.getFirstCellNum(); c < row.getLastCellNum(); c++) {
                        cells.add(renderCell(row.getCell(c)));
                    }
                }
                sb.append("行").append(r + 1).append(": [").append(String.join(",", cells)).append("]\n");
            }
            return sb.toString();
        }
    }

    /** 单元格渲染为近似 JSON.stringify 的文本。 */
    private String renderCell(Cell cell) {
        if (cell == null) {
            return "null";
        }
        CellType type = cell.getCellType() == CellType.FORMULA ? CellType.NUMERIC : cell.getCellType();
        switch (type) {
            case STRING:
                return quote(cell.getStringCellValue());
            case NUMERIC: {
                double d = cell.getNumericCellValue();
                if (d == Math.rint(d) && !Double.isInfinite(d) && Math.abs(d) < 1e15) {
                    return String.valueOf((long) d);
                }
                return String.valueOf(d);
            }
            case BOOLEAN:
                return String.valueOf(cell.getBooleanCellValue());
            default:
                return "null";
        }
    }

    private String quote(String value) {
        if (value == null) {
            return "null";
        }
        StringBuilder sb = new StringBuilder("\"");
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> sb.append(c);
            }
        }
        return sb.append('"').toString();
    }
}
