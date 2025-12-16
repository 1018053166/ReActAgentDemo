import axios from 'axios';

/**
 * Playwright 浏览器自动化工具
 * 支持远程模式（调用 Electron 的 CDP 端口 9222）
 */
export class PlaywrightTools {
  constructor() {
    this.remoteUrl = 'http://localhost:9222';
    this.sensitiveWords = ['政治', '色情', '暴力', '赌博'];
  }

  static getToolDefinitions() {
    return [
      {
        type: 'function',
        function: {
          name: 'navigate',
          description: '导航到指定URL',
          parameters: {
            type: 'object',
            properties: {
              url: { type: 'string', description: '目标URL' }
            },
            required: ['url']
          }
        }
      },
      {
        type: 'function',
        function: {
          name: 'click',
          description: '点击页面元素',
          parameters: {
            type: 'object',
            properties: {
              selector: { type: 'string', description: '元素选择器' }
            },
            required: ['selector']
          }
        }
      },
      {
        type: 'function',
        function: {
          name: 'fill',
          description: '填充表单字段',
          parameters: {
            type: 'object',
            properties: {
              selector: { type: 'string', description: '元素选择器' },
              value: { type: 'string', description: '填充的值' }
            },
            required: ['selector', 'value']
          }
        }
      },
      {
        type: 'function',
        function: {
          name: 'screenshot',
          description: '截取当前页面截图',
          parameters: {
            type: 'object',
            properties: {
              fileName: { type: 'string', description: '截图文件名（可选）' }
            }
          }
        }
      },
      {
        type: 'function',
        function: {
          name: 'getPageContent',
          description: '获取当前页面的文本内容',
          parameters: {
            type: 'object',
            properties: {}
          }
        }
      },
      {
        type: 'function',
        function: {
          name: 'getConsoleLogs',
          description: '获取浏览器控制台日志',
          parameters: {
            type: 'object',
            properties: {}
          }
        }
      }
    ];
  }

  async executeTool(toolName, args) {
    switch (toolName) {
      case 'navigate':
        return await this.navigate(args.url);
      case 'click':
        return await this.click(args.selector);
      case 'fill':
        return await this.fill(args.selector, args.value);
      case 'screenshot':
        return await this.screenshot(args.fileName);
      case 'getPageContent':
        return await this.getPageContent();
      case 'getConsoleLogs':
        return await this.getConsoleLogs();
      default:
        throw new Error(`Unknown tool: ${toolName}`);
    }
  }

  /**
   * 导航到 URL
   */
  async navigate(url) {
    try {
      const response = await axios.post(`${this.remoteUrl}/navigate`, { url });
      return `成功导航到: ${url}`;
    } catch (error) {
      return this.handleRemoteCall('navigate', { url });
    }
  }

  /**
   * 点击元素
   */
  async click(selector) {
    try {
      const response = await axios.post(`${this.remoteUrl}/click`, { selector });
      return `成功点击元素: ${selector}`;
    } catch (error) {
      return this.handleRemoteCall('click', { selector });
    }
  }

  /**
   * 填充表单
   */
  async fill(selector, value) {
    try {
      // 敏感词过滤
      if (this.containsSensitiveWords(value)) {
        throw new Error('输入内容包含敏感词');
      }
      
      const response = await axios.post(`${this.remoteUrl}/fill`, { selector, value });
      return `成功填充字段: ${selector}`;
    } catch (error) {
      return this.handleRemoteCall('fill', { selector, value });
    }
  }

  /**
   * 截图
   */
  async screenshot(fileName) {
    try {
      const name = fileName || `screenshot-${Date.now()}.png`;
      const response = await axios.post(`${this.remoteUrl}/screenshot`, { fileName: name });
      return `截图已保存: ${name}`;
    } catch (error) {
      return this.handleRemoteCall('screenshot', { fileName });
    }
  }

  /**
   * 获取页面内容
   */
  async getPageContent() {
    try {
      const response = await axios.get(`${this.remoteUrl}/content`);
      const content = response.data.content || '';
      
      // 智能压缩超长文本
      return this.compressText(content);
    } catch (error) {
      return this.handleRemoteCall('getPageContent', {});
    }
  }

  /**
   * 获取控制台日志
   */
  async getConsoleLogs() {
    try {
      const response = await axios.get(`${this.remoteUrl}/console-logs`);
      const logs = response.data.logs || [];
      
      if (logs.length === 0) {
        return '暂无控制台日志';
      }
      
      return `控制台日志:\n${logs.join('\n')}`;
    } catch (error) {
      return this.handleRemoteCall('getConsoleLogs', {});
    }
  }

  /**
   * 处理远程调用（当 HTTP 接口不可用时的降级处理）
   */
  handleRemoteCall(action, params) {
    return `浏览器自动化操作 [${action}] - 参数: ${JSON.stringify(params)}\n` +
           `提示: 请确保 Electron 客户端已启动并开启远程调试端口 9222`;
  }

  /**
   * 智能文本压缩（三段式采样）
   */
  compressText(text, maxLength = 3000) {
    if (!text || text.length <= maxLength) {
      return text;
    }

    const headRatio = 0.4;
    const middleRatio = 0.3;
    const tailRatio = 0.3;

    const headLength = Math.floor(maxLength * headRatio);
    const middleLength = Math.floor(maxLength * middleRatio);
    const tailLength = Math.floor(maxLength * tailRatio);

    const head = text.substring(0, headLength);
    const middleStart = Math.floor((text.length - middleLength) / 2);
    const middle = text.substring(middleStart, middleStart + middleLength);
    const tail = text.substring(text.length - tailLength);

    return `${head}

... [中间内容已省略 ${text.length - maxLength} 字符] ...

${middle}

... [继续省略] ...

${tail}`;
  }

  /**
   * 敏感词检测
   */
  containsSensitiveWords(text) {
    if (!text) return false;
    return this.sensitiveWords.some(word => text.includes(word));
  }
}
