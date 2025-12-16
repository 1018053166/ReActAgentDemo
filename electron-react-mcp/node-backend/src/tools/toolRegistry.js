const { MathTools } = require('./mathTools');
const { FileSystemTools } = require('./fileSystemTools');
const { DocumentReaderTools } = require('./documentReaderTools');
const { PlaywrightTools } = require('./playwrightTools');
const { CommandTools } = require('./commandTools');

/**
 * 工具注册器 - 统一管理所有工具
 */
class ToolRegistry {
  constructor() {
    this.playwrightTools = new PlaywrightTools();
    this.toolClasses = {
      math: MathTools,
      fileSystem: FileSystemTools,
      document: DocumentReaderTools,
      playwright: this.playwrightTools,
      command: CommandTools
    };
    // 工具响应最大长度限制
    this.maxResponseLength = 6000;
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
    allTools.push(...CommandTools.getToolDefinitions());
    
    return allTools;
  }

  /**
   * 执行工具调用
   */
  async executeTool(toolName, args) {
    let result;
    
    // 数学工具
    const mathTools = ['add', 'subtract', 'multiply', 'divide', 'squareRoot'];
    if (mathTools.includes(toolName)) {
      result = await MathTools.executeTool(toolName, args);
      return this.limitResponseLength(result, toolName);
    }

    // 文件系统工具
    const fileTools = ['readFile', 'writeFile', 'listDirectory', 'deleteFile', 'createDirectory'];
    if (fileTools.includes(toolName)) {
      result = await FileSystemTools.executeTool(toolName, args);
      return this.limitResponseLength(result, toolName);
    }

    // 文档读取工具
    const docTools = ['readWordDocument', 'readExcelDocument'];
    if (docTools.includes(toolName)) {
      result = await DocumentReaderTools.executeTool(toolName, args);
      return this.limitResponseLength(result, toolName);
    }

    // Playwright 工具
    const browserTools = [
      'navigate', 'click', 'fill', 'press', 'hover', 'select',
      'goBack', 'goForward', 'reload', 'waitForSelector', 'screenshot',
      'getPageUrl', 'getPageTitle', 'getConsoleLogs', 'getPageContent'
    ];
    if (browserTools.includes(toolName)) {
      result = await this.playwrightTools.executeTool(toolName, args);
      return this.limitResponseLength(result, toolName);
    }

    // 命令执行工具
    const commandTools = ['executeCommand', 'executeScript'];
    if (commandTools.includes(toolName)) {
      result = await CommandTools.executeTool(toolName, args);
      return this.limitResponseLength(result, toolName);
    }

    throw new Error(`Unknown tool: ${toolName}`);
  }

  /**
   * 限制响应长度，避免工具返回内容过长导致上下文超出
   */
  limitResponseLength(response, toolName) {
    if (!response || typeof response !== 'string') {
      return response;
    }
    
    if (response.length <= this.maxResponseLength) {
      return response;
    }
    
    // 超出限制，进行智能截取
    console.log(`[ToolRegistry] 工具 ${toolName} 响应过长 (${response.length} 字符)，进行截取`);
    
    const headLength = Math.floor(this.maxResponseLength * 0.6);
    const tailLength = Math.floor(this.maxResponseLength * 0.3);
    
    const head = response.substring(0, headLength);
    const tail = response.substring(response.length - tailLength);
    const omittedLength = response.length - headLength - tailLength;
    
    return `${head}

... [内容已截取，省略 ${omittedLength} 字符] ...

${tail}`;
  }
}

module.exports = { ToolRegistry };
