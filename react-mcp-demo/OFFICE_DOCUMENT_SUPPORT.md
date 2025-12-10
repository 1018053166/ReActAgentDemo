# Office 文档读取支持文档

## 概述

系统现已支持 **Word (.docx)** 和 **Excel (.xlsx)** 文档的智能读取功能，基于 Apache POI 库实现，与 ReAct 框架完美集成。

## 新增功能

### 1. Word 文档读取

#### 工具：readWordDocument

**功能描述**：
- 读取 Word 文档（.docx 格式）的完整文本内容
- 提取所有段落并按顺序返回
- 自动过滤空白段落

**参数**：
- `filePath` (String): Word 文件的相对路径（相对于用户主目录）

**示例用法**：
```
读取 Documents/项目报告.docx 的内容
读取 Desktop/会议纪要.docx
帮我看看 Downloads/简历.docx 里写了什么
```

**返回格式**：
```
Word 文档: Documents/项目报告.docx
总段落数: 15

========== 文档内容 ==========

第一段内容...
第二段内容...
...

========== 内容结束 ==========
```

**限制说明**：
- ✅ 支持：.docx (Office 2007+)
- ❌ 不支持：.doc (旧版 Office)
- ✅ 提取纯文本内容
- ❌ 不保留格式（字体、颜色、样式等）

### 2. Excel 表格读取

#### 工具：readExcelDocument

**功能描述**：
- 读取 Excel 表格（.xlsx 格式）的数据
- 支持读取所有工作表
- 可控制每个工作表的最大读取行数
- 自动识别数据类型（文本、数字、日期、公式等）

**参数**：
- `filePath` (String): Excel 文件的相对路径（相对于用户主目录）
- `maxRows` (int): 每个工作表最多读取的行数
  - 默认值：100
  - 最大值：1000
  - 建议：根据文件大小合理设置以提高性能

**示例用法**：
```
读取 Documents/销售数据.xlsx 的内容
读取 Desktop/考勤表.xlsx，最多读取 50 行
分析 Documents/财务报表.xlsx 中的数据
```

**返回格式**：
```
Excel 文档: Documents/销售数据.xlsx
工作表数量: 3
最多读取行数: 100

========================================
工作表 1: 2024年销售
========================================
行 1: 日期 | 产品名称 | 销售额 | 备注
行 2: 2024-01-01 | 产品A | 15000 | 促销活动
行 3: 2024-01-02 | 产品B | 28500 | 
...
(共读取 100 行)

========================================
工作表 2: 汇总数据
========================================
...
```

**数据类型处理**：
- 📝 文本：直接显示
- 🔢 数字：整数显示为整数，小数保留原格式
- 📅 日期：转换为日期格式字符串
- 🔣 公式：尝试计算结果，失败则显示公式本身
- ⬜ 空白：显示为空

**限制说明**：
- ✅ 支持：.xlsx (Excel 2007+)
- ❌ 不支持：.xls (旧版 Excel)
- ✅ 读取所有工作表
- ✅ 智能类型识别
- ⚠️ 大文件建议限制 maxRows

## 技术实现

### 依赖库

```xml
<!-- Apache POI 5.2.5 -->
<dependency>
    <groupId>org.apache.poi</groupId>
    <artifactId>poi-ooxml</artifactId>
    <version>5.2.5</version>
</dependency>
<dependency>
    <groupId>org.apache.poi</groupId>
    <artifactId>poi-scratchpad</artifactId>
    <version>5.2.5</version>
</dependency>
```

### 核心实现类

[DocumentReaderTools.java](file:///Users/work/projects/MCP/react-mcp-demo/src/main/java/com/example/reactmcp/tools/DocumentReaderTools.java)

**关键特性**：
1. **安全路径检查**：继承文件系统工具的安全机制
2. **格式验证**：严格检查文件扩展名
3. **智能解析**：
   - Word：提取所有段落文本
   - Excel：多工作表、多数据类型支持
4. **异常处理**：完善的错误处理和日志记录

### 工具注册

工具已自动注册到 ReAct Agent：

```java
@Bean
public McpAssistant mcpAssistant(ChatLanguageModel chatLanguageModel, 
                                 McpTools mcpTools,
                                 FileSystemTools fileSystemTools,
                                 DocumentReaderTools documentReaderTools) {
    return AiServices.builder(McpAssistant.class)
            .chatLanguageModel(chatLanguageModel)
            .chatMemory(MessageWindowChatMemory.withMaxMessages(10))
            .tools(mcpTools, fileSystemTools, documentReaderTools)
            .build();
}
```

## 使用场景

### 场景 1：文档内容分析

**用户**：读取 Documents/会议纪要.docx 并总结要点

**AI 执行流程**：
```
Thought: 用户需要读取 Word 文档并总结内容。
         我先调用 readWordDocument 读取文档内容。

Action: readWordDocument("Documents/会议纪要.docx")

Observation: 成功读取文档，包含 20 个段落的会议内容。
            内容包括：会议时间、参会人员、讨论议题、决议事项等。
            现在我可以基于这些内容进行总结。

Final Answer: 
会议纪要要点总结：
1. 会议时间：2024年12月5日
2. 参会人员：张三、李四、王五
3. 主要议题：
   - Q4 销售目标达成情况
   - 2025 年战略规划
4. 决议事项：...
```

### 场景 2：数据表格分析

**用户**：分析 Documents/销售数据.xlsx 中各产品的销售趋势

**AI 执行流程**：
```
Thought: 用户需要分析 Excel 表格中的销售数据。
         我需要读取 Excel 文件，限制读取行数以提高效率。

Action: readExcelDocument("Documents/销售数据.xlsx", 200)

Observation: Excel 文件包含 2 个工作表。
            工作表1"月度销售"有 150 行数据，包含产品名称、销售额等字段。
            工作表2"产品分类"有 10 行汇总数据。
            现在我可以分析销售趋势。

Final Answer:
根据销售数据分析：
1. 产品A：呈上升趋势，Q4 增长 25%
2. 产品B：保持稳定...
```

### 场景 3：文档对比

**用户**：对比 v1.docx 和 v2.docx 的差异

**AI 执行流程**：
```
Thought: 需要读取两个 Word 文档，然后对比内容差异。

Action: readWordDocument("Documents/v1.docx")

Observation: 读取到 v1 文档内容，共 15 段。

Action: readWordDocument("Documents/v2.docx")

Observation: 读取到 v2 文档内容，共 18 段。
            两个文档段落数量不同，需要详细对比。

Final Answer:
两个文档的主要差异：
1. v2 新增了 3 个段落...
2. 第 5 段内容有修改...
```

## 前端界面

### 新增示例按钮

页面已添加文档读取示例：

```html
<span class="example-tag" onclick="setTask('读取 Documents/报告.docx 的内容')">
    文档: 读取Word
</span>
<span class="example-tag" onclick="setTask('读取 Documents/数据.xlsx 的内容，最多50行')">
    文档: 读取Excel
</span>
```

### 工具调用显示

前端增强了文档工具的展示：

```javascript
'readWordDocument': `读取 Word 文档: ${params.filePath}`,
'readExcelDocument': `读取 Excel 表格: ${params.filePath} (最多 ${params.maxRows || 100} 行)`
```

## 性能优化建议

### 1. Excel 大文件处理

对于包含大量数据的 Excel 文件：

```
✅ 推荐：读取 Documents/大文件.xlsx，最多 100 行
❌ 避免：读取整个 Documents/大文件.xlsx（可能很慢）
```

### 2. 合理设置 maxRows

根据实际需求设置：
- **快速预览**：maxRows = 10-20
- **常规分析**：maxRows = 50-100（默认）
- **详细分析**：maxRows = 200-500
- **完整数据**：maxRows = 1000（上限）

### 3. 分批处理

对于超大 Excel 文件，考虑分工作表处理：

```
先读取第一个工作表，分析后再决定是否需要读取其他工作表
```

## 错误处理

### 常见错误及解决方案

#### 1. 文件不存在
```
错误：File not found: Documents/报告.docx
解决：检查文件路径是否正确，确认文件在用户主目录下
```

#### 2. 格式不支持
```
错误：Not a Word document (.docx): Documents/报告.doc
解决：将旧版 .doc 文件另存为 .docx 格式
```

#### 3. 文件损坏
```
错误：Error parsing Word document: ...
解决：尝试在 Office 中修复文件，或重新保存
```

#### 4. 权限问题
```
错误：Security error: Access denied
解决：确保文件路径在用户主目录内
```

## 安全机制

### 路径安全

继承文件系统工具的安全检查：

```java
private Path sanitizePath(String pathStr) throws IOException {
    Path basePath = Paths.get(DEFAULT_BASE_PATH).toRealPath();
    Path requestedPath = basePath.resolve(pathStr).normalize();
    
    if (!requestedPath.startsWith(basePath)) {
        throw new SecurityException("Access denied");
    }
    return requestedPath;
}
```

### 文件类型验证

严格检查文件扩展名：

```java
if (!fileName.endsWith(".docx")) {
    return "Not a Word document (.docx): " + filePath;
}

if (!fileName.endsWith(".xlsx")) {
    return "Not an Excel document (.xlsx): " + filePath;
}
```

### 大小限制

通过 maxRows 参数限制 Excel 读取量：
- 默认：100 行
- 最大：1000 行

## 完整工具列表

系统现在支持以下工具类别：

### 📊 数学计算工具
- add, subtract, multiply, divide

### 📁 文件系统工具
- readFile, writeFile, listFiles
- createDirectory, deleteFile
- copyFile, moveFile

### 📄 Office 文档工具（新增）
- **readWordDocument** - Word 文档读取
- **readExcelDocument** - Excel 表格读取

## 测试示例

### 测试 1：读取 Word 文档

1. 准备测试文件：在 `~/Documents` 创建一个 test.docx 文件
2. 在界面输入：`读取 Documents/test.docx 的内容`
3. 观察 AI 的执行过程和返回结果

### 测试 2：读取 Excel 表格

1. 准备测试文件：在 `~/Documents` 创建一个 data.xlsx 文件
2. 在界面输入：`读取 Documents/data.xlsx，最多 20 行`
3. 查看表格数据是否正确解析

### 测试 3：综合应用

```
读取 Documents/报告.docx 和 Documents/数据.xlsx，
总结报告内容并结合表格数据进行分析
```

## 后续扩展计划

### 功能增强
- [ ] 支持 PDF 文档读取
- [ ] 支持 PPT 演示文稿读取
- [ ] 支持 Excel 写入功能
- [ ] 支持 Word 格式保留（粗体、斜体等）
- [ ] 支持图片提取

### 性能优化
- [ ] 流式读取大文件
- [ ] 缓存机制
- [ ] 并发读取优化

### 用户体验
- [ ] 文档预览功能
- [ ] 进度条显示
- [ ] 分页浏览支持

## 总结

通过集成 Apache POI，系统现已具备强大的 Office 文档处理能力。结合 ReAct 框架的智能决策能力，可以实现文档内容分析、数据提取、报告生成等丰富的应用场景，为用户提供更加便捷的文档处理体验。

---

**启动应用**：
```bash
cd /Users/work/projects/MCP/react-mcp-demo
mvn spring-boot:run
```

**访问地址**：http://localhost:8080

**开始使用**：点击"文档: 读取Word"或"文档: 读取Excel"示例按钮开始体验！🎉
