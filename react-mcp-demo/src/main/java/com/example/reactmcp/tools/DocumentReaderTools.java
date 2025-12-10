package com.example.reactmcp.tools;

import dev.langchain4j.agent.tool.Tool;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.ss.usermodel.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

/**
 * Office 文档读取工具集
 * 支持读取 Word (.docx) 和 Excel (.xlsx) 文件
 */
@Component
public class DocumentReaderTools {
    
    private static final Logger log = LoggerFactory.getLogger(DocumentReaderTools.class);
    private static final String DEFAULT_BASE_PATH = System.getProperty("user.home");
    
    /**
     * 安全路径检查，防止目录遍历攻击
     */
    private Path sanitizePath(String pathStr) throws IOException {
        Path basePath = Paths.get(DEFAULT_BASE_PATH).toRealPath();
        Path requestedPath = basePath.resolve(pathStr).normalize();
        
        if (!requestedPath.startsWith(basePath)) {
            throw new SecurityException("Access denied: Path is outside allowed directory");
        }
        
        return requestedPath;
    }

    @Tool("读取 Word 文档(.docx)的完整文本内容，包括所有段落。参数: filePath - Word文件路径(相对于用户目录)")
    public String readWordDocument(String filePath) {
        log.info("");
        log.info("┌─────────────────────────────────────────────────────────────┐");
        log.info("│ 🔧 工具调用: read_word_document                       │");
        log.info("├─────────────────────────────────────────────────────────────┤");
        log.info("│ 📥 输入参数:");
        log.info("│    • filePath: {}", filePath);
        
        try {
            Path path = sanitizePath(filePath);
            
            if (!Files.exists(path)) {
                String error = "File not found: " + filePath;
                log.info("│ 📤 返回结果: 文件不存在");
                log.info("└─────────────────────────────────────────────────────────────┘");
                return error;
            }
            
            if (!Files.isRegularFile(path)) {
                String error = "Path is not a file: " + filePath;
                log.info("│ 📤 返回结果: 路径不是文件");
                log.info("└─────────────────────────────────────────────────────────────┘");
                return error;
            }
            
            // 检查文件扩展名
            String fileName = path.getFileName().toString().toLowerCase();
            if (!fileName.endsWith(".docx")) {
                String error = "Not a Word document (.docx): " + filePath;
                log.info("│ 📤 返回结果: 不是 Word 文档");
                log.info("└─────────────────────────────────────────────────────────────┘");
                return error;
            }
            
            StringBuilder content = new StringBuilder();
            
            try (FileInputStream fis = new FileInputStream(path.toFile());
                 XWPFDocument document = new XWPFDocument(fis)) {
                
                List<XWPFParagraph> paragraphs = document.getParagraphs();
                
                content.append(String.format("Word 文档: %s\n", filePath));
                content.append(String.format("总段落数: %d\n", paragraphs.size()));
                content.append("\n========== 文档内容 ==========\n\n");
                
                for (int i = 0; i < paragraphs.size(); i++) {
                    XWPFParagraph para = paragraphs.get(i);
                    String text = para.getText().trim();
                    if (!text.isEmpty()) {
                        content.append(text).append("\n");
                    }
                }
                
                content.append("\n========== 内容结束 ==========");
            }
            
            String result = content.toString();
            log.info("│ 📤 返回结果: 成功读取 Word 文档，共 {} 段落", 
                    result.split("\n").length - 4);
            log.info("└─────────────────────────────────────────────────────────────┘");
            log.info("");
            
            return result;
            
        } catch (SecurityException e) {
            log.error("│ ❌ 安全错误: {}", e.getMessage());
            log.info("└─────────────────────────────────────────────────────────────┘");
            return "Security error: " + e.getMessage();
        } catch (IOException e) {
            log.error("│ ❌ IO错误: {}", e.getMessage());
            log.info("└─────────────────────────────────────────────────────────────┘");
            return "IO error reading Word document: " + e.getMessage();
        } catch (Exception e) {
            log.error("│ ❌ 解析错误: {}", e.getMessage());
            log.info("└─────────────────────────────────────────────────────────────┘");
            return "Error parsing Word document: " + e.getMessage();
        }
    }

    @Tool("读取 Excel 表格(.xlsx)的内容，返回所有工作表的数据。参数: filePath - Excel文件路径(相对于用户目录), maxRows - 每个工作表最多读取的行数(默认100)")
    public String readExcelDocument(String filePath, int maxRows) {
        log.info("");
        log.info("┌─────────────────────────────────────────────────────────────┐");
        log.info("│ 🔧 工具调用: read_excel_document                      │");
        log.info("├─────────────────────────────────────────────────────────────┤");
        log.info("│ 📥 输入参数:");
        log.info("│    • filePath: {}", filePath);
        log.info("│    • maxRows: {}", maxRows);
        
        try {
            Path path = sanitizePath(filePath);
            
            if (!Files.exists(path)) {
                String error = "File not found: " + filePath;
                log.info("│ 📤 返回结果: 文件不存在");
                log.info("└─────────────────────────────────────────────────────────────┘");
                return error;
            }
            
            if (!Files.isRegularFile(path)) {
                String error = "Path is not a file: " + filePath;
                log.info("│ 📤 返回结果: 路径不是文件");
                log.info("└─────────────────────────────────────────────────────────────┘");
                return error;
            }
            
            // 检查文件扩展名
            String fileName = path.getFileName().toString().toLowerCase();
            if (!fileName.endsWith(".xlsx")) {
                String error = "Not an Excel document (.xlsx): " + filePath;
                log.info("│ 📤 返回结果: 不是 Excel 文档");
                log.info("└─────────────────────────────────────────────────────────────┘");
                return error;
            }
            
            // 设置默认值和限制
            int effectiveMaxRows = maxRows <= 0 ? 100 : Math.min(maxRows, 1000);
            
            StringBuilder content = new StringBuilder();
            
            try (FileInputStream fis = new FileInputStream(path.toFile());
                 XSSFWorkbook workbook = new XSSFWorkbook(fis)) {
                
                int numberOfSheets = workbook.getNumberOfSheets();
                
                content.append(String.format("Excel 文档: %s\n", filePath));
                content.append(String.format("工作表数量: %d\n", numberOfSheets));
                content.append(String.format("最多读取行数: %d\n\n", effectiveMaxRows));
                
                for (int sheetIndex = 0; sheetIndex < numberOfSheets; sheetIndex++) {
                    Sheet sheet = workbook.getSheetAt(sheetIndex);
                    String sheetName = sheet.getSheetName();
                    
                    content.append("========================================\n");
                    content.append(String.format("工作表 %d: %s\n", sheetIndex + 1, sheetName));
                    content.append("========================================\n");
                    
                    int rowCount = 0;
                    int totalRows = sheet.getPhysicalNumberOfRows();
                    
                    for (Row row : sheet) {
                        if (rowCount >= effectiveMaxRows) {
                            content.append(String.format("... (省略剩余 %d 行)\n", totalRows - rowCount));
                            break;
                        }
                        
                        List<String> cellValues = new ArrayList<>();
                        for (Cell cell : row) {
                            cellValues.add(getCellValueAsString(cell));
                        }
                        
                        // 只显示非空行
                        if (!cellValues.stream().allMatch(String::isEmpty)) {
                            content.append(String.format("行 %d: %s\n", 
                                    row.getRowNum() + 1, 
                                    String.join(" | ", cellValues)));
                            rowCount++;
                        }
                    }
                    
                    content.append(String.format("(共读取 %d 行)\n\n", rowCount));
                }
            }
            
            String result = content.toString();
            log.info("│ 📤 返回结果: 成功读取 Excel 文档");
            log.info("└─────────────────────────────────────────────────────────────┘");
            log.info("");
            
            return result;
            
        } catch (SecurityException e) {
            log.error("│ ❌ 安全错误: {}", e.getMessage());
            log.info("└─────────────────────────────────────────────────────────────┘");
            return "Security error: " + e.getMessage();
        } catch (IOException e) {
            log.error("│ ❌ IO错误: {}", e.getMessage());
            log.info("└─────────────────────────────────────────────────────────────┘");
            return "IO error reading Excel document: " + e.getMessage();
        } catch (Exception e) {
            log.error("│ ❌ 解析错误: {}", e.getMessage());
            log.info("└─────────────────────────────────────────────────────────────┘");
            return "Error parsing Excel document: " + e.getMessage();
        }
    }
    
    /**
     * 获取单元格的字符串值
     */
    private String getCellValueAsString(Cell cell) {
        if (cell == null) {
            return "";
        }
        
        switch (cell.getCellType()) {
            case STRING:
                return cell.getStringCellValue();
            case NUMERIC:
                if (DateUtil.isCellDateFormatted(cell)) {
                    return cell.getDateCellValue().toString();
                } else {
                    double numericValue = cell.getNumericCellValue();
                    // 如果是整数，不显示小数点
                    if (numericValue == Math.floor(numericValue)) {
                        return String.valueOf((long) numericValue);
                    } else {
                        return String.valueOf(numericValue);
                    }
                }
            case BOOLEAN:
                return String.valueOf(cell.getBooleanCellValue());
            case FORMULA:
                try {
                    return String.valueOf(cell.getNumericCellValue());
                } catch (Exception e) {
                    try {
                        return cell.getStringCellValue();
                    } catch (Exception ex) {
                        return cell.getCellFormula();
                    }
                }
            case BLANK:
                return "";
            default:
                return "";
        }
    }
}
