const axios = require('axios');

/**
 * Playwright 浏览器自动化工具
 * 支持远程模式（调用 Electron 的 CDP 端口 9222）
 */
class PlaywrightTools {
  constructor() {
    this.remoteUrl = 'http://localhost:9222';
    this.sensitiveWords = ['政治', '色情', '暴力', '赌博'];
  }

  static getToolDefinitions() {
    return [
      // ========== 基础导航 ==========
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
          name: 'goBack',
          description: '浏览器后退到上一页',
          parameters: {
            type: 'object',
            properties: {}
          }
        }
      },
      {
        type: 'function',
        function: {
          name: 'goForward',
          description: '浏览器前进到下一页',
          parameters: {
            type: 'object',
            properties: {}
          }
        }
      },
      {
        type: 'function',
        function: {
          name: 'reload',
          description: '刷新当前页面',
          parameters: {
            type: 'object',
            properties: {}
          }
        }
      },
      
      // ========== 元素交互 ==========
      {
        type: 'function',
        function: {
          name: 'click',
          description: '点击页面元素',
          parameters: {
            type: 'object',
            properties: {
              selector: { type: 'string', description: '元素选择器（CSS Selector）' }
            },
            required: ['selector']
          }
        }
      },
      {
        type: 'function',
        function: {
          name: 'fill',
          description: '填充表单字段（支持敏感词过滤）',
          parameters: {
            type: 'object',
            properties: {
              selector: { type: 'string', description: '表单元素选择器' },
              value: { type: 'string', description: '要填充的内容' }
            },
            required: ['selector', 'value']
          }
        }
      },
      {
        type: 'function',
        function: {
          name: 'press',
          description: '按下键盘按键',
          parameters: {
            type: 'object',
            properties: {
              key: { type: 'string', description: '按键名称（如 Enter, Escape, Tab）' }
            },
            required: ['key']
          }
        }
      },
      {
        type: 'function',
        function: {
          name: 'hover',
          description: '鼠标悬停在元素上',
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
          name: 'select',
          description: '下拉框选择选项',
          parameters: {
            type: 'object',
            properties: {
              selector: { type: 'string', description: '下拉框选择器' },
              value: { type: 'string', description: '要选择的值' }
            },
            required: ['selector', 'value']
          }
        }
      },
      {
        type: 'function',
        function: {
          name: 'waitForSelector',
          description: '等待元素出现（最长30秒）',
          parameters: {
            type: 'object',
            properties: {
              selector: { type: 'string', description: '元素选择器' },
              timeout: { type: 'number', description: '超时时间（毫秒，默认30000）' }
            },
            required: ['selector']
          }
        }
      },
      
      // ========== 信息获取 ==========
      {
        type: 'function',
        function: {
          name: 'screenshot',
          description: '抓取当前页面截图',
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
          description: '获取页面可见文本内容（智能压缩）',
          parameters: {
            type: 'object',
            properties: {}
          }
        }
      },
      {
        type: 'function',
        function: {
          name: 'getPageUrl',
          description: '获取当前页面的URL',
          parameters: {
            type: 'object',
            properties: {}
          }
        }
      },
      {
        type: 'function',
        function: {
          name: 'getPageTitle',
          description: '获取当前页面的标题',
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
      case 'press':
        return await this.press(args.key);
      case 'hover':
        return await this.hover(args.selector);
      case 'select':
        return await this.select(args.selector, args.value);
      case 'goBack':
        return await this.goBack();
      case 'goForward':
        return await this.goForward();
      case 'reload':
        return await this.reload();
      case 'waitForSelector':
        return await this.waitForSelector(args.selector, args.timeout);
      case 'screenshot':
        return await this.screenshot(args.fileName);
      case 'getPageContent':
        return await this.getPageContent();
      case 'getPageUrl':
        return await this.getPageUrl();
      case 'getPageTitle':
        return await this.getPageTitle();
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
      console.log(`[Playwright] 尝试导航到: ${url}`);
      const response = await axios.get(`${this.remoteUrl}/browser/navigate?url=${encodeURIComponent(url)}`);
      console.log(`[Playwright] 导航成功: ${url}`);
      return `成功导航到: ${url}`;
    } catch (error) {
      console.error(`[Playwright] 导航失败:`, error.message);
      return this.handleRemoteCall('navigate', { url });
    }
  }

  /**
   * 点击元素
   */
  async click(selector) {
    try {
      console.log(`[Playwright] 尝试点击元素: ${selector}`);
      const response = await axios.get(`${this.remoteUrl}/browser/click?selector=${encodeURIComponent(selector)}`);
      console.log(`[Playwright] 点击成功: ${selector}`);
      return `成功点击元素: ${selector}`;
    } catch (error) {
      console.error(`[Playwright] 点击失败:`, error.message);
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
      
      console.log(`[Playwright] 尝试填充字段: ${selector} = ${value}`);
      const response = await axios.get(`${this.remoteUrl}/browser/fill?selector=${encodeURIComponent(selector)}&text=${encodeURIComponent(value)}`);
      console.log(`[Playwright] 填充成功: ${selector}`);
      return `成功填充字段: ${selector}`;
    } catch (error) {
      console.error(`[Playwright] 填充失败:`, error.message);
      return this.handleRemoteCall('fill', { selector, value });
    }
  }

  /**
   * 截图
   */
  async screenshot(fileName) {
    try {
      const name = fileName || `screenshot-${Date.now()}.png`;
      const response = await axios.get(`${this.remoteUrl}/browser/screenshot?fileName=${encodeURIComponent(name)}`);
      return `截图已保存: ${name}`;
    } catch (error) {
      return this.handleRemoteCall('screenshot', { fileName });
    }
  }

  /**
   * 获取页面内容（智能压缩版）
   */
  async getPageContent() {
    try {
      const response = await axios.get(`${this.remoteUrl}/browser/getVisibleText`);
      const content = response.data.result || '';
      
      // 智能压缩页面内容
      return this.compressPageContent(content);
    } catch (error) {
      return this.handleRemoteCall('getPageContent', {});
    }
  }

  /**
   * 智能页面内容压缩
   * - 提取关键信息（标题、链接、主要文本）
   * - 移除冗余空白和重复内容
   * - 限制总长度
   */
  compressPageContent(content, maxLength = 4000) {
    if (!content) return '页面内容为空';
    
    // 1. 清理文本
    let cleaned = content
      .replace(/\s+/g, ' ')                    // 多个空白合并为一个
      .replace(/(\n\s*){3,}/g, '\n\n')          // 多个空行合并
      .replace(/[\x00-\x08\x0b\x0c\x0e-\x1f]/g, '') // 移除控制字符
      .trim();
    
    // 2. 如果内容还是太长，进行智能截取
    if (cleaned.length > maxLength) {
      // 提取前部内容（通常包含标题和导航）
      const headPart = cleaned.substring(0, Math.floor(maxLength * 0.5));
      
      // 提取中间关键内容
      const middleStart = Math.floor(cleaned.length * 0.3);
      const middlePart = cleaned.substring(middleStart, middleStart + Math.floor(maxLength * 0.3));
      
      // 提取尾部内容
      const tailPart = cleaned.substring(cleaned.length - Math.floor(maxLength * 0.2));
      
      cleaned = `[页面内容摘要 - 原始长度: ${content.length} 字符]\n\n` +
                `=== 页面开头 ===\n${headPart}\n\n` +
                `=== 页面中部 ===\n${middlePart}\n\n` +
                `=== 页面尾部 ===\n${tailPart}`;
    }
    
    return cleaned;
  }

  /**
   * 按下键盘按键
   */
  async press(key) {
    try {
      console.log(`[Playwright] 尝试按下按键: ${key}`);
      const response = await axios.get(`${this.remoteUrl}/browser/press?key=${encodeURIComponent(key)}`);
      console.log(`[Playwright] 按键成功: ${key}`);
      return `成功按下按键: ${key}`;
    } catch (error) {
      console.error(`[Playwright] 按键失败:`, error.message);
      return this.handleRemoteCall('press', { key });
    }
  }

  /**
   * 鼠标悬停
   */
  async hover(selector) {
    try {
      console.log(`[Playwright] 尝试悬停元素: ${selector}`);
      const response = await axios.get(`${this.remoteUrl}/browser/hover?selector=${encodeURIComponent(selector)}`);
      console.log(`[Playwright] 悬停成功: ${selector}`);
      return `成功悬停元素: ${selector}`;
    } catch (error) {
      console.error(`[Playwright] 悬停失败:`, error.message);
      return this.handleRemoteCall('hover', { selector });
    }
  }

  /**
   * 下拉框选择
   */
  async select(selector, value) {
    try {
      console.log(`[Playwright] 尝试选择选项: ${selector} = ${value}`);
      const response = await axios.get(`${this.remoteUrl}/browser/select?selector=${encodeURIComponent(selector)}&value=${encodeURIComponent(value)}`);
      console.log(`[Playwright] 选择成功: ${selector}`);
      return `成功选择选项: ${selector} = ${value}`;
    } catch (error) {
      console.error(`[Playwright] 选择失败:`, error.message);
      return this.handleRemoteCall('select', { selector, value });
    }
  }

  /**
   * 浏览器后退
   */
  async goBack() {
    try {
      console.log(`[Playwright] 尝试后退`);
      const response = await axios.get(`${this.remoteUrl}/browser/goBack`);
      console.log(`[Playwright] 后退成功`);
      return `成功后退到上一页`;
    } catch (error) {
      console.error(`[Playwright] 后退失败:`, error.message);
      return this.handleRemoteCall('goBack', {});
    }
  }

  /**
   * 浏览器前进
   */
  async goForward() {
    try {
      console.log(`[Playwright] 尝试前进`);
      const response = await axios.get(`${this.remoteUrl}/browser/goForward`);
      console.log(`[Playwright] 前进成功`);
      return `成功前进到下一页`;
    } catch (error) {
      console.error(`[Playwright] 前进失败:`, error.message);
      return this.handleRemoteCall('goForward', {});
    }
  }

  /**
   * 刷新页面
   */
  async reload() {
    try {
      console.log(`[Playwright] 尝试刷新页面`);
      const response = await axios.get(`${this.remoteUrl}/browser/reload`);
      console.log(`[Playwright] 刷新成功`);
      return `页面刷新成功`;
    } catch (error) {
      console.error(`[Playwright] 刷新失败:`, error.message);
      return this.handleRemoteCall('reload', {});
    }
  }

  /**
   * 等待元素出现
   */
  async waitForSelector(selector, timeout = 30000) {
    try {
      console.log(`[Playwright] 等待元素: ${selector}`);
      const response = await axios.get(`${this.remoteUrl}/browser/waitFor?selector=${encodeURIComponent(selector)}&timeout=${timeout}`);
      console.log(`[Playwright] 元素已出现: ${selector}`);
      return `元素已出现: ${selector}`;
    } catch (error) {
      console.error(`[Playwright] 等待超时:`, error.message);
      return this.handleRemoteCall('waitForSelector', { selector, timeout });
    }
  }

  /**
   * 获取页面URL
   */
  async getPageUrl() {
    try {
      const response = await axios.get(`${this.remoteUrl}/browser/getPageInfo`);
      const url = response.data.result?.url || '';
      return `当前页面URL: ${url}`;
    } catch (error) {
      return this.handleRemoteCall('getPageUrl', {});
    }
  }

  /**
   * 获取页面标题
   */
  async getPageTitle() {
    try {
      const response = await axios.get(`${this.remoteUrl}/browser/getPageInfo`);
      const title = response.data.result?.title || '';
      return `当前页面标题: ${title}`;
    } catch (error) {
      return this.handleRemoteCall('getPageTitle', {});
    }
  }

  /**
   * 获取控制台日志
   */
  async getConsoleLogs() {
    try {
      const response = await axios.get(`${this.remoteUrl}/browser/console-logs`);
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

module.exports = { PlaywrightTools };
