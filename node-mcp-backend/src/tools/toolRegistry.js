import { MathTools } from './mathTools.js';
import { FileSystemTools } from './fileSystemTools.js';
import { DocumentReaderTools } from './documentReaderTools.js';
import { PlaywrightTools } from './playwrightTools.js';

/**
 * 工具注册器 - 统一管理所有工具
 */
export class ToolRegistry {
  constructor() {
    this.playwrightTools = new PlaywrightTools();
    this.toolClasses = {
      math: MathTools,
      fileSystem: FileSystemTools,
      document: DocumentReaderTools,
      playwright: this.playwrightTools
    };
  }

  /**
   * 获取所有工具定义
   */
  getAllToolDefinitions() {
    const allTools = [];
    
    allTools.push(...MathTools.getToolDefinitions());
    allTools.push(...FileSystemTools.getToolDefinitions());
    allTools.push(...DocumentReaderTools.getToolDefinitions());
    allTools.push(...PlaywrightTools.getToolDefinitions());
    
    return allTools;
  }

  /**
   * 执行工具调用
   */
  async executeTool(toolName, args) {
    // 数学工具
    const mathTools = ['add', 'subtract', 'multiply', 'divide', 'squareRoot'];
    if (mathTools.includes(toolName)) {
      return await MathTools.executeTool(toolName, args);
    }

    // 文件系统工具
    const fileTools = ['readFile', 'writeFile', 'listDirectory', 'deleteFile', 'createDirectory'];
    if (fileTools.includes(toolName)) {
      return await FileSystemTools.executeTool(toolName, args);
    }

    // 文档读取工具
    const docTools = ['readWordDocument', 'readExcelDocument'];
    if (docTools.includes(toolName)) {
      return await DocumentReaderTools.executeTool(toolName, args);
    }

    // Playwright 工具
    const browserTools = ['navigate', 'click', 'fill', 'screenshot', 'getPageContent', 'getConsoleLogs'];
    if (browserTools.includes(toolName)) {
      return await this.playwrightTools.executeTool(toolName, args);
    }

    throw new Error(`Unknown tool: ${toolName}`);
  }
}
