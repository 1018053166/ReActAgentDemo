import mammoth from 'mammoth';
import xlsx from 'xlsx';
import fs from 'fs/promises';

/**
 * 文档读取工具
 */
export class DocumentReaderTools {
  static getToolDefinitions() {
    return [
      {
        type: 'function',
        function: {
          name: 'readWordDocument',
          description: '读取 Word 文档内容（.docx格式）',
          parameters: {
            type: 'object',
            properties: {
              filePath: { type: 'string', description: 'Word文档路径' }
            },
            required: ['filePath']
          }
        }
      },
      {
        type: 'function',
        function: {
          name: 'readExcelDocument',
          description: '读取 Excel 文档内容（.xlsx格式）',
          parameters: {
            type: 'object',
            properties: {
              filePath: { type: 'string', description: 'Excel文档路径' },
              sheetName: { type: 'string', description: '工作表名称（可选）' }
            },
            required: ['filePath']
          }
        }
      }
    ];
  }

  static async executeTool(toolName, args) {
    switch (toolName) {
      case 'readWordDocument':
        return await this.readWordDocument(args.filePath);
      case 'readExcelDocument':
        return await this.readExcelDocument(args.filePath, args.sheetName);
      default:
        throw new Error(`Unknown tool: ${toolName}`);
    }
  }

  /**
   * 读取 Word 文档
   */
  static async readWordDocument(filePath) {
    try {
      const buffer = await fs.readFile(filePath);
      const result = await mammoth.extractRawText({ buffer });
      
      if (!result.value || result.value.trim().length === 0) {
        return 'Word文档为空或无法提取文本内容';
      }
      
      return `Word文档内容:\n${result.value}`;
    } catch (error) {
      throw new Error(`读取Word文档失败: ${error.message}`);
    }
  }

  /**
   * 读取 Excel 文档
   */
  static async readExcelDocument(filePath, sheetName) {
    try {
      const buffer = await fs.readFile(filePath);
      const workbook = xlsx.read(buffer, { type: 'buffer' });
      
      // 如果指定了工作表名称，读取该工作表；否则读取第一个工作表
      const targetSheetName = sheetName || workbook.SheetNames[0];
      
      if (!workbook.Sheets[targetSheetName]) {
        throw new Error(`工作表 "${targetSheetName}" 不存在`);
      }
      
      const worksheet = workbook.Sheets[targetSheetName];
      const jsonData = xlsx.utils.sheet_to_json(worksheet, { header: 1 });
      
      if (!jsonData || jsonData.length === 0) {
        return 'Excel文档为空';
      }
      
      // 格式化输出
      let result = `Excel文档内容 (工作表: ${targetSheetName}):\n`;
      result += `可用工作表: ${workbook.SheetNames.join(', ')}\n\n`;
      
      // 转换为文本表格格式
      jsonData.forEach((row, index) => {
        result += `第${index + 1}行: ${row.join(' | ')}\n`;
      });
      
      return result;
    } catch (error) {
      throw new Error(`读取Excel文档失败: ${error.message}`);
    }
  }
}
