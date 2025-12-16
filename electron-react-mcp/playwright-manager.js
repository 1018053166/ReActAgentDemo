/**
 * PlaywrightManager - 统一管理 Playwright 连接到 Electron BrowserView
 * 通过 CDP (Chrome DevTools Protocol) 连接到 Electron 的远程调试端口
 */

const { chromium } = require('playwright');

class PlaywrightManager {
  constructor() {
    this.browser = null;
    this.context = null;
    this.page = null;
    this.connected = false;
    this.cdpPort = 9223;  // Electron 远程调试端口
  }

  /**
   * 通过 CDP 连接到 Electron BrowserView
   * @param {number} port - 远程调试端口，默认 9222
   */
  async connect(port = 9222) {
    if (this.connected && this.page) {
      console.log('[PlaywrightManager] 已连接，复用现有连接');
      return this.page;
    }

    this.cdpPort = port;
    
    try {
      console.log(`[PlaywrightManager] 连接到 CDP 端口: ${port}`);
      
      // 通过 CDP 连接到 Electron
      this.browser = await chromium.connectOverCDP(`http://127.0.0.1:${port}`);
      
      // 获取所有上下文
      const contexts = this.browser.contexts();
      console.log(`[PlaywrightManager] 找到 ${contexts.length} 个浏览器上下文`);
      
      if (contexts.length === 0) {
        throw new Error('没有找到浏览器上下文');
      }

      // 使用第一个上下文（通常是 BrowserView）
      this.context = contexts[0];
      
      // 获取所有页面
      const pages = this.context.pages();
      console.log(`[PlaywrightManager] 找到 ${pages.length} 个页面`);
      
      if (pages.length === 0) {
        throw new Error('没有找到页面');
      }

      // 查找 BrowserView 对应的页面（排除 file:// 和 devtools:// 协议）
      this.page = pages.find(p => {
        const pageUrl = p.url();
        return !pageUrl.startsWith('file://') && 
               !pageUrl.startsWith('devtools://') && 
               !pageUrl.startsWith('about:');
      }) || pages[pages.length - 1];
      
      console.log(`[PlaywrightManager] 已连接到页面: ${this.page.url()}`);
      this.connected = true;
      
      // 监听断开事件
      this.browser.on('disconnected', () => {
        console.log('[PlaywrightManager] 连接已断开');
        this.connected = false;
        this.page = null;
        this.context = null;
        this.browser = null;
      });

      return this.page;
    } catch (error) {
      console.error('[PlaywrightManager] 连接失败:', error.message);
      this.connected = false;
      throw error;
    }
  }

  /**
   * 确保已连接，如果未连接则尝试连接
   */
  async ensureConnected() {
    if (!this.connected || !this.page) {
      await this.connect(this.cdpPort);
    }
    return this.page;
  }

  /**
   * 刷新页面引用（当页面导航后可能需要）
   */
  async refreshPage() {
    if (!this.context) {
      throw new Error('未连接到浏览器上下文');
    }
    
    const pages = this.context.pages();
    this.page = pages.find(p => !p.url().startsWith('file://')) || pages[pages.length - 1];
    return this.page;
  }

  /**
   * 导航到指定 URL
   */
  async navigate(url, options = {}) {
    const page = await this.ensureConnected();
    const result = await page.goto(url, {
      waitUntil: options.waitUntil || 'domcontentloaded',
      timeout: options.timeout || 30000
    });
    return {
      url: page.url(),
      title: await page.title(),
      status: result ? result.status() : null
    };
  }

  /**
   * 点击元素
   */
  async click(selector, options = {}) {
    const page = await this.ensureConnected();
    await page.click(selector, {
      timeout: options.timeout || 10000,
      force: options.force || false
    });
    return { success: true, selector };
  }

  /**
   * 填充输入框
   */
  async fill(selector, text, options = {}) {
    const page = await this.ensureConnected();
    await page.fill(selector, text, {
      timeout: options.timeout || 10000
    });
    return { success: true, selector, text };
  }

  /**
   * 按键操作
   */
  async press(key) {
    const page = await this.ensureConnected();
    await page.keyboard.press(key);
    return { success: true, key };
  }

  /**
   * 获取页面信息
   */
  async getPageInfo() {
    const page = await this.ensureConnected();
    return {
      url: page.url(),
      title: await page.title()
    };
  }

  /**
   * 获取页面 URL
   */
  async getPageUrl() {
    const page = await this.ensureConnected();
    return page.url();
  }

  /**
   * 获取页面标题
   */
  async getPageTitle() {
    const page = await this.ensureConnected();
    return await page.title();
  }

  /**
   * 获取可见文本
   */
  async getVisibleText(selector = 'body') {
    const page = await this.ensureConnected();
    return await page.locator(selector).innerText();
  }

  /**
   * 获取 HTML 内容
   */
  async getVisibleHtml(selector = 'html', cleanHtml = false) {
    const page = await this.ensureConnected();
    
    if (cleanHtml) {
      return await page.evaluate((sel) => {
        const container = document.querySelector(sel) || document.documentElement;
        const clone = container.cloneNode(true);
        clone.querySelectorAll('script, style, link, meta').forEach(el => el.remove());
        return clone.outerHTML;
      }, selector);
    }
    
    return await page.locator(selector).innerHTML();
  }

  /**
   * 执行 JavaScript
   */
  async executeJs(script) {
    const page = await this.ensureConnected();
    return await page.evaluate(script);
  }

  /**
   * 截图
   */
  async screenshot(options = {}) {
    const page = await this.ensureConnected();
    
    const screenshotOptions = {
      type: 'png',
      fullPage: options.fullPage || false
    };

    if (options.selector) {
      const element = page.locator(options.selector);
      return await element.screenshot(screenshotOptions);
    }
    
    return await page.screenshot(screenshotOptions);
  }

  /**
   * 悬停
   */
  async hover(selector) {
    const page = await this.ensureConnected();
    await page.hover(selector);
    return { success: true, selector };
  }

  /**
   * 选择下拉框
   */
  async select(selector, value) {
    const page = await this.ensureConnected();
    await page.selectOption(selector, value);
    return { success: true, selector, value };
  }

  /**
   * 等待元素
   */
  async waitForSelector(selector, options = {}) {
    const page = await this.ensureConnected();
    await page.waitForSelector(selector, {
      timeout: options.timeout || 10000,
      state: options.state || 'visible'
    });
    return { success: true, selector };
  }

  /**
   * 获取所有输入框信息（调试用）
   */
  async getInputs() {
    const page = await this.ensureConnected();
    return await page.evaluate(() => {
      const inputs = Array.from(document.querySelectorAll('input'));
      return inputs.map(input => ({
        tagName: input.tagName,
        type: input.type,
        name: input.name,
        id: input.id,
        className: input.className,
        placeholder: input.placeholder,
        ariaLabel: input.getAttribute('aria-label'),
        visible: input.offsetParent !== null,
        disabled: input.disabled
      }));
    });
  }

  /**
   * 分析页面结构
   */
  async analyzePage() {
    const page = await this.ensureConnected();
    return await page.evaluate(() => {
      const pageInfo = {
        url: window.location.href,
        title: document.title,
        timestamp: new Date().toISOString()
      };
      
      const headings = Array.from(document.querySelectorAll('h1, h2, h3, h4, h5, h6')).map(h => ({
        tag: h.tagName.toLowerCase(),
        text: h.textContent.trim().substring(0, 100),
        id: h.id || null
      }));
      
      const forms = Array.from(document.querySelectorAll('form')).map(form => ({
        action: form.action,
        method: form.method,
        inputCount: form.querySelectorAll('input, textarea, select').length
      }));
      
      const links = Array.from(document.querySelectorAll('a[href]')).slice(0, 20).map(link => ({
        text: link.textContent.trim().substring(0, 50),
        href: link.href
      }));
      
      const inputs = Array.from(document.querySelectorAll('input, textarea')).map(input => ({
        type: input.type || 'text',
        name: input.name || input.id || '',
        placeholder: input.placeholder || ''
      }));
      
      const buttons = Array.from(document.querySelectorAll('button, input[type="button"], input[type="submit"]')).map(btn => ({
        text: btn.textContent.trim() || btn.value || '',
        type: btn.type || 'button'
      }));
      
      return { pageInfo, headings, forms, links, inputs, buttons };
    });
  }

  /**
   * 断开连接
   */
  async disconnect() {
    if (this.browser) {
      await this.browser.close();
      this.browser = null;
      this.context = null;
      this.page = null;
      this.connected = false;
      console.log('[PlaywrightManager] 已断开连接');
    }
  }

  /**
   * 检查连接状态
   */
  isConnected() {
    return this.connected && this.page !== null;
  }
}

// 导出单例
const playwrightManager = new PlaywrightManager();

module.exports = { PlaywrightManager, playwrightManager };
