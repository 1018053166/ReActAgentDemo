package com.example.reactmcp.tools;

import com.example.reactmcp.model.ReActStepEvent;
import com.example.reactmcp.service.ReActEventPublisher;
import com.microsoft.playwright.*;
import com.microsoft.playwright.options.WaitForSelectorState;
import dev.langchain4j.agent.tool.Tool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.nio.file.Paths;
import java.util.Base64;
import java.util.List;
import java.util.ArrayList;
import java.util.stream.Collectors;

/**
 * Playwright MCP 工具集
 * 提供本地浏览器自动化能力
 */
@Component
public class PlaywrightMcpTools {
    
    private static final Logger log = LoggerFactory.getLogger(PlaywrightMcpTools.class);
    
    // 事件发布器（用于向 ReAct 框架发送实时事件）
    private final ReActEventPublisher eventPublisher;
    
    // 注入配置，决定是否使用远程浏览器模式
    @Value("${app.remote-browser.enabled:false}")
    private boolean remoteBrowserEnabled;
    
    @Value("${app.remote-browser.host:localhost}")
    private String remoteBrowserHost;
    
    @Value("${app.remote-browser.port:9222}")
    private int remoteBrowserPort;
    
    private Playwright playwright;
    private Browser browser;
    private BrowserContext context;
    private Page page;
    private String currentPageUrl; // 用于远程模式下跟踪当前页面URL
    private final List<ConsoleLogEntry> consoleLogs = new ArrayList<>();
    
    // 敏感词过滤列表（用于防止触发阿里云内容审查）
    private static final String[] SENSITIVE_KEYWORDS = {
        "政治", "宗教", "色情", "暴力", "恐怖", "赌博", "毒品", "违法", "犯罪",
        "敏感", "争议", "冲突", "战争", "军事", "间谍", "叛乱", "颠覆", "分裂",
        "抗议", "游行", "罢工", "骚乱", "暴乱", "恐怖主义", "极端主义", "民族矛盾",
        "领土争端", "国际纠纷", "外交风波", "政府丑闻", "官员腐败", "司法不公"
    };
    
    // 控制台日志条目
    private static class ConsoleLogEntry {
        final String type;
        final String text;
        final long timestamp;
        
        ConsoleLogEntry(String type, String text) {
            this.type = type;
            this.text = text;
            this.timestamp = System.currentTimeMillis();
        }
    }
    // 过滤敏感内容
    private String filterSensitiveContent(String content) {
        if (content == null || content.isEmpty()) {
            return content;
        }
        
        String filtered = content;
        for (String keyword : SENSITIVE_KEYWORDS) {
            filtered = filtered.replaceAll(keyword, "[敏感内容]");
        }
        
        // 注意：此处不再进行长度限制和截断处理
        // 分段读取将在调用层通过流式响应实现
        
        return filtered;
    }
    
    public PlaywrightMcpTools(ReActEventPublisher eventPublisher) {
        this.eventPublisher = eventPublisher;
    }
    
    @PostConstruct
    public void init() {
        log.info("🎭 初始化 Playwright MCP 工具...");
        
        // 如果启用了远程浏览器模式，则不需要初始化本地 Playwright
        if (remoteBrowserEnabled) {
            log.info("✅ 远程浏览器模式已启用，跳过本地 Playwright 初始化");
            return;
        }
        
        try {
            playwright = Playwright.create();
            log.info("✅ Playwright 实例创建成功");
        } catch (Exception e) {
            log.error("❌ Playwright 初始化失败: {}", e.getMessage());
        }
    }
    
    @PreDestroy
    public void cleanup() {
        log.info("🧹 清理 Playwright 资源...");
        
        // 如果启用了远程浏览器模式，则不需要清理本地资源
        if (remoteBrowserEnabled) {
            log.info("✅ 远程浏览器模式下无需清理本地资源");
            return;
        }
        
        closeBrowser();
        if (playwright != null) {
            playwright.close();
            log.info("✅ Playwright 资源已清理");
        }
    }
    
    @Tool("启动浏览器并打开指定网页。参数 url 是要访问的网址，headless 为 true 时无界面运行（默认 false 显示浏览器）")
    public String navigate(String url, Boolean headless) {
        log.info("");
        log.info("┌─────────────────────────────────────────────────────────────┐");
        log.info("│ 🌐 工具调用: navigate (打开网页)                         │");
        log.info("├─────────────────────────────────────────────────────────────┤");
        log.info("│ 📥 输入参数:");
        log.info("│    • url: {}", url);
        log.info("│    • headless: {}", headless == null ? false : headless);
        log.info("│    • remote mode: {}", remoteBrowserEnabled);
        
        try {
            // 如果启用了远程浏览器模式，则通过 Electron 控制内嵌浏览器
            if (remoteBrowserEnabled) {
                return navigateRemote(url);
            }
            
            // 本地模式：使用 Playwright 启动浏览器
            // 每次 navigate 都创建新的浏览器窗口，确保每个任务独立
            // 先关闭旧的浏览器窗口（如果存在）
            if (page != null) {
                try { 
                    page.close(); 
                    log.info("│ 🗑️  关闭旧的页面窗口");
                } catch (Exception ignored) {}
                page = null;
            }
            
            // 检查浏览器是否需要启动
            boolean needsLaunch = browser == null || !browser.isConnected();
            
            if (needsLaunch) {
                // 清理旧实例
                if (context != null) {
                    try { context.close(); } catch (Exception ignored) {}
                    context = null;
                }
                if (browser != null) {
                    try { browser.close(); } catch (Exception ignored) {}
                    browser = null;
                }
                
                // 启动新的浏览器实例（添加额外参数确保桌面模式）
                boolean isHeadless = headless != null && headless;
                browser = playwright.chromium().launch(new BrowserType.LaunchOptions()
                        .setHeadless(isHeadless)
                        .setArgs(java.util.Arrays.asList(
                                "--window-size=1920,1080",
                                "--disable-blink-features=AutomationControlled"
                        )));
                
                // 创建浏览器上下文，设置桌面浏览器 UA 和视口
                context = browser.newContext(new Browser.NewContextOptions()
                        .setViewportSize(1920, 1080)
                        .setUserAgent("Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"));
                log.info("│ 🚀 浏览器已启动（{}模式，1920x1080，桌面UA）", isHeadless ? "无头" : "有界面");
            }
            
            // 创建新的页面窗口（每次 navigate 都是新窗口）
            page = context.newPage();
            log.info("│ 🌐 创建新的页面窗口");
            
            // 清空旧日志并监听控制台事件
            consoleLogs.clear();
            page.onConsoleMessage(msg -> {
                consoleLogs.add(new ConsoleLogEntry(msg.type(), msg.text()));
            });
            
            // 导航到目标页面
            page.navigate(url);
            
            // 等待页面加载完成
            page.waitForLoadState();
            
            // 获取页面详细状态
            String pageState = getPageState();
            String result = String.format("成功打开页面\n%s", pageState);
            
            log.info("│ 📤 返回结果:");
            log.info("│    {}", result);
            log.info("└─────────────────────────────────────────────────────────────┘");
            log.info("");
            
            return result;
        } catch (Exception e) {
            String error = "导航失败: " + e.getMessage();
            log.error("│ ❌ 错误: {}", error, e);
            log.info("└─────────────────────────────────────────────────────────────┘");
            log.info("");
            
            // 发生错误时清理资源，下次重新启动
            closeBrowser();
            return error;
        }
    }
    
    /**
     * 远程模式下的导航实现（与 Electron BrowserView 协同工作）
     */
    private String navigateRemote(String url) {
        try {
            log.info("│ 🌐 使用远程浏览器模式");
            
            // 构造远程控制 URL
            String remoteUrl = String.format("http://%s:%d/browser/navigate?url=%s", 
                remoteBrowserHost, remoteBrowserPort, java.net.URLEncoder.encode(url, "UTF-8"));
            
            // 发送 HTTP 请求到 Electron 应用
            java.net.HttpURLConnection connection = (java.net.HttpURLConnection) new java.net.URL(remoteUrl).openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(5000);
            connection.setReadTimeout(10000);
            
            int responseCode = connection.getResponseCode();
            if (responseCode == 200) {
                // 读取响应
                java.io.BufferedReader reader = new java.io.BufferedReader(
                    new java.io.InputStreamReader(connection.getInputStream()));
                StringBuilder response = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    response.append(line);
                }
                reader.close();
                
                // 解析响应，确保导航真正成功后再设置 currentPageUrl
                String responseStr = response.toString();
                if (responseStr.contains("\"success\":true")) {
                    // 在远程模式下，设置 currentPageUrl 以便后续的工具调用可以正确检查状态
                    currentPageUrl = url;
                    
                    log.info("│ ✅ 远程导航成功");
                    log.info("│ 📤 返回结果:");
                    log.info("│    {}", response.toString());
                    log.info("└─────────────────────────────────────────────────────────────┘");
                    log.info("");
                    
                    return response.toString();
                } else {
                    String error = "远程导航失败: " + responseStr;
                    log.error("│ ❌ {}", error);
                    log.info("└─────────────────────────────────────────────────────────────┘");
                    log.info("");
                    return error;
                }
            } else {
                String error = "远程导航失败，HTTP 状态码: " + responseCode;
                log.error("│ ❌ {}", error);
                log.info("└─────────────────────────────────────────────────────────────┘");
                log.info("");
                return error;
            }
        } catch (Exception e) {
            String error = "远程导航异常: " + e.getMessage();
            log.error("│ ❌ {}", error, e);
            log.info("└─────────────────────────────────────────────────────────────┘");
            log.info("");
            return error;
        }
    }
    
    @Tool("点击页面上的元素。参数 selector 是元素选择器（支持 CSS、文本、role 等），比如 'button', 'text=提交', '#submit-btn'")
    public String click(String selector) {
        log.info("");
        log.info("┌─────────────────────────────────────────────────────────────┐");
        log.info("│ 👆 工具调用: click (点击元素)                            │");
        log.info("├─────────────────────────────────────────────────────────────┤");
        log.info("│ 📥 输入参数:");
        log.info("│    • selector: {}", selector);
        
        try {
            // 在远程模式下检查页面是否已打开
            if (remoteBrowserEnabled) {
                if (currentPageUrl == null || currentPageUrl.isEmpty()) {
                    return "错误: 请先使用 navigate 工具打开网页";
                }
                // 远程模式下调用远程点击方法
                return clickRemote(selector);
            }
            
            // 本地模式检查
            if (page == null || context == null || browser == null || !browser.isConnected()) {
                return "错误: 请先使用 navigate 工具打开网页";
            }
            
            // 智能处理百度搜索按钮：尝试多个可能的选择器
            String[] possibleSelectors = {
                selector,  // 原始选择器
                "#chat-submit-button",  // 百度 AI 搜索按钮（最新版）
                "#su",     // 传统搜索按钮
                "button.sc-btn",  // AI 搜索按钮（旧版）
                "button[type='submit']",  // 提交按钮
                "text=百度一下",  // 文本匹配
                "button",  // 兜底：第一个按钮
            };
            
            String actualSelector = null;
            Exception lastException = null;
            
            // 尝试找到可点击的按钮
            for (String s : possibleSelectors) {
                try {
                    page.waitForSelector(s, new Page.WaitForSelectorOptions()
                            .setState(WaitForSelectorState.ATTACHED)
                            .setTimeout(2000));
                    
                    // 检查是否可见
                    Boolean isVisible = (Boolean) page.evaluate(
                        "(selector) => { " +
                        "  const el = document.querySelector(selector); " +
                        "  if (!el) return false; " +
                        "  const style = window.getComputedStyle(el); " +
                        "  return style.display !== 'none' && style.visibility !== 'hidden'; " +
                        "}",
                        s
                    );
                    
                    if (Boolean.TRUE.equals(isVisible)) {
                        actualSelector = s;
                        log.info("│ 🎯 找到可点击元素: {}", s);
                        break;
                    } else {
                        // 元素存在但不可见，标记为候选（后面用 JS 点击）
                        if (actualSelector == null) {
                            actualSelector = s;
                            log.info("│ ⚠️  元素不可见，将使用 JS 点击: {}", s);
                        }
                    }
                } catch (Exception e) {
                    lastException = e;
                    continue;
                }
            }
            
            if (actualSelector == null) {
                String error = "未找到可点击的元素。尝试的选择器: " + String.join(", ", possibleSelectors);
                if (lastException != null) {
                    error += "。最后错误: " + lastException.getMessage();
                }
                log.error("│ ❌ {}", error);
                log.info("└─────────────────────────────────────────────────────────────┘");
                log.info("");
                return error;
            }
            
            // 记录点击前的 URL
            String beforeUrl = page.url();
            
            // 智能点击：先尝试正常点击，失败则用 JS 绕过
            try {
                page.click(actualSelector, new Page.ClickOptions().setTimeout(3000));
                log.info("│ ✅ 正常点击成功");
            } catch (Exception clickError) {
                log.info("│ ⚠️  正常点击失败，使用 JS 绕过");
                // 强制显示并点击
                page.evaluate(
                    "(selector) => { " +
                    "  const el = document.querySelector(selector); " +
                    "  if (el) { " +
                    "    el.style.display = 'inline-block'; " +
                    "    el.style.visibility = 'visible'; " +
                    "    el.click(); " +
                    "  } " +
                    "}",
                    actualSelector
                );
            }
            
            // 等待可能的页面变化
            page.waitForTimeout(1000);
            
            String afterUrl = page.url();
            boolean urlChanged = !beforeUrl.equals(afterUrl);
            
            // 获取点击后的页面状态
            String pageState = getPageState();
            String result = String.format("成功点击元素: %s%s\n%s", 
                actualSelector,
                urlChanged ? " (页面已跳转)" : "",
                pageState);
            
            log.info("│ 📤 返回结果:");
            log.info("│    {}", result);
            log.info("└─────────────────────────────────────────────────────────────┘");
            log.info("");
            
            return result;
        } catch (Exception e) {
            String error = "点击失败: " + e.getMessage();
            log.error("│ ❌错误: {}", error);
            log.info("└─────────────────────────────────────────────────────────────┘");
            log.info("");
            return error;
        }
    }
    
    /**
     * 远程模式下的点击实现（与 Electron BrowserView 协同工作）
     */
    private String clickRemote(String selector) {
        try {
            log.info("│ 👆 使用远程浏览器模式");
            
            // 构造远程控制 URL
            String remoteUrl = String.format("http://%s:%d/browser/click?selector=%s", 
                remoteBrowserHost, remoteBrowserPort, java.net.URLEncoder.encode(selector, "UTF-8"));
            
            // 发送 HTTP 请求到 Electron 应用
            java.net.HttpURLConnection connection = (java.net.HttpURLConnection) new java.net.URL(remoteUrl).openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(5000);
            connection.setReadTimeout(10000);
            
            int responseCode = connection.getResponseCode();
            if (responseCode == 200) {
                // 读取响应
                java.io.BufferedReader reader = new java.io.BufferedReader(
                    new java.io.InputStreamReader(connection.getInputStream()));
                StringBuilder response = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    response.append(line);
                }
                reader.close();
                
                // 在远程模式下，点击操作可能会导致页面跳转，尝试更新 currentPageUrl
                // 只有在点击成功的情况下才更新 URL
                String responseStr = response.toString();
                if (responseStr.contains("\"success\":true")) {
                    updateCurrentPageUrlRemote();
                    
                    log.info("│ ✅ 远程点击成功");
                    log.info("│ 📤 返回结果:");
                    log.info("│    {}", response.toString());
                    log.info("└─────────────────────────────────────────────────────────────┘");
                    log.info("");
                    
                    return response.toString();
                } else {
                    String error = "远程点击失败: " + responseStr;
                    log.error("│ ❌ {}", error);
                    log.info("└─────────────────────────────────────────────────────────────┘");
                    log.info("");
                    return error;
                }
            } else {
                String error = "远程点击失败，HTTP 状态码: " + responseCode;
                log.error("│ ❌ {}", error);
                log.info("└─────────────────────────────────────────────────────────────┘");
                log.info("");
                return error;
            }
        } catch (Exception e) {
            String error = "远程点击异常: " + e.getMessage();
            log.error("│ ❌ {}", error, e);
            log.info("└─────────────────────────────────────────────────────────────┘");
            log.info("");
            return error;
        }
    }
    
    @Tool("在输入框中输入文本。参数 selector 是输入框选择器，text 是要输入的内容")
    public String fill(String selector, String text) {
        log.info("");
        log.info("┌─────────────────────────────────────────────────────────────┐");
        log.info("│ ⌨️  工具调用: fill (输入文本)                            │");
        log.info("├─────────────────────────────────────────────────────────────┤");
        log.info("│ 📥 输入参数:");
        log.info("│    • selector: {}", selector);
        log.info("│    • text: {}", text);
        
        try {
            // 在远程模式下检查页面是否已打开
            if (remoteBrowserEnabled) {
                if (currentPageUrl == null || currentPageUrl.isEmpty()) {
                    return "错误: 请先使用 navigate 工具打开网页";
                }
                // 远程模式下调用远程输入方法
                return fillRemote(selector, text);
            }
            
            // 本地模式检查
            if (page == null || context == null || browser == null || !browser.isConnected()) {
                return "错误: 请先使用 navigate 工具打开网页";
            }
            
            // 智能处理百度搜索框：先尝试多个可能的选择器
            String[] possibleSelectors = {
                selector,  // 原始选择器
                "#chat-textarea",  // 百度 AI 搜索框（最新版）
                "#kw",     // 传统搜索框
                "textarea.sc-input",  // AI 搜索框（旧版）
                "textarea[placeholder*='搜索']",  // 模糊匹配
                "input[name='wd']",   // 旧版输入框
                "textarea",  // 兜底：第一个 textarea
                "input[type='text']",  // 兜底：第一个文本输入框
            };
            
            String actualSelector = null;
            Exception lastException = null;
            
            // 尝试找到可见的输入框
            for (String s : possibleSelectors) {
                try {
                    // 尝试等待元素可见（2秒超时）
                    page.waitForSelector(s, new Page.WaitForSelectorOptions()
                            .setState(WaitForSelectorState.ATTACHED)
                            .setTimeout(2000));
                    
                    // 检查是否真的可见（排除 hidden 元素）
                    Boolean isVisible = (Boolean) page.evaluate(
                        "(selector) => { " +
                        "  const el = document.querySelector(selector); " +
                        "  if (!el) return false; " +
                        "  const style = window.getComputedStyle(el); " +
                        "  return style.display !== 'none' && style.visibility !== 'hidden' && style.opacity !== '0'; " +
                        "}",
                        s
                    );
                    
                    if (Boolean.TRUE.equals(isVisible)) {
                        actualSelector = s;
                        log.info("│ 🎯 找到可见输入框: {}", s);
                        break;
                    } else {
                        // 元素存在但不可见，标记为候选（后面用 JS 操作）
                        if (actualSelector == null) {
                            actualSelector = s;
                            log.info("│ ⚠️  输入框不可见，将使用 JS 操作: {}", s);
                        }
                    }
                } catch (Exception e) {
                    lastException = e;
                    continue;  // 尝试下一个选择器
                }
            }
            
            if (actualSelector == null) {
                String error = "未找到可见的输入框。尝试的选择器: " + String.join(", ", possibleSelectors);
                if (lastException != null) {
                    error += "。最后错误: " + lastException.getMessage();
                }
                log.error("│ ❌ {}", error);
                log.info("└─────────────────────────────────────────────────────────────┘");
                log.info("");
                return error;
            }
            
            // 记录输入前的 URL
            String beforeUrl = page.url();
            
            // 智能输入：先尝试正常操作，失败则用 JS 绕过
            try {
                page.click(actualSelector, new Page.ClickOptions().setTimeout(3000));  // 先点击聚焦
                page.fill(actualSelector, "");  // 清空
                page.type(actualSelector, text);  // 逐字输入
                log.info("│ ✅ 正常输入成功");
            } catch (Exception fillError) {
                log.info("│ ⚠️  正常输入失败，使用 JS 绕过");
                // 强制显示并输入（使用 Map 传参）
                java.util.Map<String, Object> args = new java.util.HashMap<>();
                args.put("selector", actualSelector);
                args.put("value", text);
                page.evaluate(
                    "(args) => { " +
                    "  const el = document.querySelector(args.selector); " +
                    "  if (el) { " +
                    "    el.style.display = 'block'; " +
                    "    el.style.visibility = 'visible'; " +
                    "    el.style.opacity = '1'; " +
                    "    el.value = args.value; " +
                    "    el.dispatchEvent(new Event('input', { bubbles: true })); " +
                    "    el.dispatchEvent(new Event('change', { bubbles: true })); " +
                    "  } " +
                    "}",
                    args
                );
            }
            
            // 等待可能的页面变化（例如自动提示、自动跳转）
            page.waitForTimeout(1000);
            
            String afterUrl = page.url();
            boolean urlChanged = !beforeUrl.equals(afterUrl);
            
            // 获取输入后的页面状态
            String pageState = getPageState();
            String result = String.format("成功在 %s 中输入: %s%s\n%s", 
                actualSelector, 
                text,
                urlChanged ? " (页面已跳转)" : "",
                pageState);
            
            log.info("│ 📤 返回结果:");
            log.info("│    {}", result);
            log.info("└─────────────────────────────────────────────────────────────┘");
            log.info("");
            
            return result;
        } catch (Exception e) {
            String error = "输入失败: " + e.getMessage();
            log.error("│ ❌错误: {}", error);
            log.info("└─────────────────────────────────────────────────────────────┘");
            log.info("");
            return error;
        }
    }
    
    /**
     * 远程模式下的输入实现（与 Electron BrowserView 协同工作）
     */
    private String fillRemote(String selector, String text) {
        try {
            log.info("│ ⌨️  使用远程浏览器模式");
            
            // 构造远程控制 URL
            String remoteUrl = String.format("http://%s:%d/browser/fill?selector=%s&text=%s", 
                remoteBrowserHost, remoteBrowserPort, 
                java.net.URLEncoder.encode(selector, "UTF-8"),
                java.net.URLEncoder.encode(text, "UTF-8"));
            
            // 发送 HTTP 请求到 Electron 应用
            java.net.HttpURLConnection connection = (java.net.HttpURLConnection) new java.net.URL(remoteUrl).openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(5000);
            connection.setReadTimeout(10000);
            
            int responseCode = connection.getResponseCode();
            if (responseCode == 200) {
                // 读取响应
                java.io.BufferedReader reader = new java.io.BufferedReader(
                    new java.io.InputStreamReader(connection.getInputStream()));
                StringBuilder response = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    response.append(line);
                }
                reader.close();
                
                // 只有在输入成功的情况下才返回成功信息
                String responseStr = response.toString();
                if (responseStr.contains("\"success\":true")) {
                    log.info("│ ✅ 远程输入成功");
                    log.info("│ 📤 返回结果:");
                    log.info("│    {}", response.toString());
                    log.info("└─────────────────────────────────────────────────────────────┘");
                    log.info("");
                    
                    return response.toString();
                } else {
                    String error = "远程输入失败: " + responseStr;
                    log.error("│ ❌ {}", error);
                    log.info("└─────────────────────────────────────────────────────────────┘");
                    log.info("");
                    return error;
                }
            } else {
                String error = "远程输入失败，HTTP 状态码: " + responseCode;
                log.error("│ ❌ {}", error);
                log.info("└─────────────────────────────────────────────────────────────┘");
                log.info("");
                return error;
            }
        } catch (Exception e) {
            String error = "远程输入异常: " + e.getMessage();
            log.error("│ ❌ {}", error, e);
            log.info("└─────────────────────────────────────────────────────────────┘");
            log.info("");
            return error;
        }
    }
    
    @Tool("获取页面上元素的文本内容。参数 selector 是元素选择器，chunked 为 true 时分段返回（默认 false）")
    public String getText(String selector, Boolean chunked) {
        log.info("");
        log.info("┌─────────────────────────────────────────────────────────────┐");
        log.info("│ 📄 工具调用: getText (获取文本)                          │");
        log.info("├─────────────────────────────────────────────────────────────┤");
        log.info("│ 📥 输入参数:");
        log.info("│    • selector: {}", selector);
        log.info("│    • chunked: {}", chunked != null ? chunked : false);
        
        try {
            // 在远程模式下检查页面是否已打开
            if (remoteBrowserEnabled) {
                if (currentPageUrl == null || currentPageUrl.isEmpty()) {
                    return "错误: 请先使用 navigate 工具打开网页";
                }
                // 远程模式下调用远程截图方法
                return screenshotRemote(false, selector);
            }
            
            // 本地模式检查
            if (page == null) {
                return "错误: 请先使用 navigate 工具打开网页";
            }
            
            // 如果启用分段读取模式
            if (chunked != null && chunked) {
                List<String> chunks = getTextInChunks(selector, 2000); // 每块2000字符
                if (chunks.isEmpty()) {
                    return "未获取到文本内容或内容为空";
                }
                
                // 发送分段内容到前端
                for (int i = 0; i < chunks.size(); i++) {
                    String chunkResult = String.format("[分段 %d/%d] 元素文本内容: %s", i+1, chunks.size(), chunks.get(i));
                    eventPublisher.publish(ReActStepEvent.observation(chunkResult));
                    log.info("│ 📤 返回结果 (分段 {}/{}):", i+1, chunks.size());
                    log.info("│    {}", chunks.get(i).length() > 100 ? chunks.get(i).substring(0, 100) + "..." : chunks.get(i));
                }
                
                log.info("└─────────────────────────────────────────────────────────────┘");
                log.info("");
                return "文本内容已分段发送完成";
            }
            
            // 默认模式：一次性返回全部内容
            String text = page.textContent(selector);
            String result = String.format("元素文本内容: %s", text);
            
            log.info("│ 📤 返回结果:");
            log.info("│    {}", result);
            log.info("└─────────────────────────────────────────────────────────────┘");
            log.info("");
            
            return result;
        } catch (Exception e) {
            String error = "获取文本失败: " + e.getMessage();
            log.error("│ ❌ 错误: {}", error);
            log.info("└─────────────────────────────────────────────────────────────┘");
            log.info("");
            return error;
        }
    }
    
    @Tool("截取当前页面的屏幕截图。参数 path 是保存截图的文件路径（可选，默认返回 base64）")
    public String screenshot(String path) {
        log.info("");
        log.info("┌─────────────────────────────────────────────────────────────┐");
        log.info("│ 📸 工具调用: screenshot (截图)                           │");
        log.info("├─────────────────────────────────────────────────────────────┤");
        log.info("│ 📥 输入参数:");
        log.info("│    • path: {}", path == null ? "无（返回 base64）" : path);
        
        try {
            // 在远程模式下检查页面是否已打开
            if (remoteBrowserEnabled) {
                if (currentPageUrl == null || currentPageUrl.isEmpty()) {
                    return "错误: 请先使用 navigate 工具打开网页";
                }
                // 远程模式下暂不支持此功能，返回提示信息
                return "错误: 远程模式下暂不支持截图功能";
            }
            
            if (page == null) {
                return "错误: 请先使用 navigate 工具打开网页";
            }
            
            byte[] screenshot;
            if (path != null && !path.isEmpty()) {
                screenshot = page.screenshot(new Page.ScreenshotOptions().setPath(Paths.get(path)));
                String result = String.format("截图已保存到: %s", path);
                
                log.info("│ 📤 返回结果:");
                log.info("│    {}", result);
                log.info("└─────────────────────────────────────────────────────────────┘");
                log.info("");
                
                return result;
            } else {
                screenshot = page.screenshot();
                String base64 = Base64.getEncoder().encodeToString(screenshot);
                String result = "截图 Base64（前50字符）: " + base64.substring(0, Math.min(50, base64.length())) + "...";
                
                log.info("│ 📤 返回结果:");
                log.info("│    {}", result);
                log.info("└─────────────────────────────────────────────────────────────┘");
                log.info("");
                
                return "截图已生成（Base64 长度: " + base64.length() + " 字符）";
            }
        } catch (Exception e) {
            String error = "截图失败: " + e.getMessage();
            log.error("│ ❌ 错误: {}", error);
            log.info("└─────────────────────────────────────────────────────────────┘");
            log.info("");
            return error;
        }
    }
    
    @Tool("等待指定毫秒数。参数 milliseconds 是等待时间（毫秒）")
    public String waitTime(Integer milliseconds) {
        log.info("");
        log.info("┌─────────────────────────────────────────────────────────────┐");
        log.info("│ ⏱️  工具调用: waitTime (等待)                            │");
        log.info("├─────────────────────────────────────────────────────────────┤");
        log.info("│ 📥 输入参数:");
        log.info("│    • milliseconds: {}", milliseconds);
        
        try {
            // 在远程模式下检查页面是否已打开
            if (remoteBrowserEnabled) {
                if (currentPageUrl == null || currentPageUrl.isEmpty()) {
                    return "错误: 请先使用 navigate 工具打开网页";
                }
            } else {
                // 本地模式检查
                if (page == null) {
                    return "错误: 请先使用 navigate 工具打开网页";
                }
            }
            
            // 执行等待
            if (remoteBrowserEnabled) {
                // 远程模式下使用简单的线程睡眠
                Thread.sleep(milliseconds != null ? milliseconds : 1000);
            } else {
                // 本地模式下使用 Playwright 的等待方法
                page.waitForTimeout(milliseconds != null ? milliseconds : 1000);
            }
            
            String result = String.format("已等待 %d 毫秒", milliseconds != null ? milliseconds : 1000);
            
            log.info("│ 📤 返回结果:");
            log.info("│    {}", result);
            log.info("└─────────────────────────────────────────────────────────────┘");
            log.info("");
            
            return result;
        } catch (Exception e) {
            String error = "等待失败: " + e.getMessage();
            log.error("│ ❌ 错误: {}", error);
            log.info("└─────────────────────────────────────────────────────────────┘");
            log.info("");
            return error;
        }
    }
    
    @Tool("获取当前页面的 URL 和标题信息")
    public String getPageInfo() {
        log.info("");
        log.info("┌─────────────────────────────────────────────────────────────┐");
        log.info("│ ℹ️  工具调用: getPageInfo (获取页面信息)                 │");
        log.info("├─────────────────────────────────────────────────────────────┤");
        
        try {
            // 在远程模式下检查页面是否已打开
            if (remoteBrowserEnabled) {
                if (currentPageUrl == null || currentPageUrl.isEmpty()) {
                    return "错误: 请先使用 navigate 工具打开网页";
                }
                // 远程模式下调用远程获取页面信息方法
                return getPageInfoRemote();
            }
            
            // 本地模式检查
            if (page == null) {
                return "错误: 请先使用 navigate 工具打开网页";
            }
            
            String url = page.url();
            String title = page.title();
            String result = String.format("当前页面 URL: %s, 标题: %s", url, title);
            
            log.info("│ 📤 返回结果:");
            log.info("│    URL: {}", url);
            log.info("│    标题: {}", title);
            log.info("└─────────────────────────────────────────────────────────────┘");
            log.info("");
            
            return result;
        } catch (Exception e) {
            String error = "获取页面信息失败: " + e.getMessage();
            log.error("│ ❌ 错误: {}", error);
            log.info("└─────────────────────────────────────────────────────────────┘");
            log.info("");
            return error;
        }
    }
    
    @Tool("分析当前页面并返回详细信息，包括 URL、标题、可用的输入框和按钮。用于了解页面当前状态")
    public String analyzePage() {
        log.info("");
        log.info("┌─────────────────────────────────────────────────────────────┐");
        log.info("│ 🔍 工具调用: analyzePage (分析页面)                  │");
        log.info("├─────────────────────────────────────────────────────────────┤");
        
        try {
            // 在远程模式下检查页面是否已打开
            if (remoteBrowserEnabled) {
                if (currentPageUrl == null || currentPageUrl.isEmpty()) {
                    return "错误: 请先使用 navigate 工具打开网页";
                }
                // 远程模式下调用远程页面分析方法
                return analyzePageRemote();
            }
            
            // 本地模式检查
            if (page == null) {
                return "错误: 请先使用 navigate 工具打开网页";
            }
            
            String result = getPageState();
            
            log.info("│ 📤 返回结果:");
            log.info("│    {}", result);
            log.info("└─────────────────────────────────────────────────────────────┘");
            log.info("");
            
            return result;
        } catch (Exception e) {
            String error = "分析页面失败: " + e.getMessage();
            log.error("│ ❌ 错误: {}", error);
            log.info("└─────────────────────────────────────────────────────────────┘");
            log.info("");
            return error;
        }
    }
    
    /**
     * 分段读取页面文本内容，避免一次性返回过长内容
     * @param selector 元素选择器
     * @param chunkSize 每个数据块的大小（字符数）
     * @return 分段后的文本内容列表
     */
    private List<String> getTextInChunks(String selector, int chunkSize) {
        List<String> chunks = new ArrayList<>();
        
        try {
            // 获取完整的文本内容
            String fullText = page.textContent(selector);
            if (fullText == null || fullText.isEmpty()) {
                return chunks;
            }
            
            // 过滤敏感内容
            fullText = filterSensitiveContent(fullText);
            
            // 按指定大小分段
            for (int i = 0; i < fullText.length(); i += chunkSize) {
                int end = Math.min(i + chunkSize, fullText.length());
                chunks.add(fullText.substring(i, end));
            }
        } catch (Exception e) {
            log.error("分段读取文本失败: {}", e.getMessage());
        }
        
        return chunks;
    }


    @Tool("关闭浏览器并释放资源")
    public String closeBrowser() {
        log.info("");
        log.info("┌─────────────────────────────────────────────────────────────┐");
        log.info("│ 🚪 工具调用: closeBrowser (关闭浏览器)                   │");
        log.info("├─────────────────────────────────────────────────────────────┤");
        
        try {
            if (page != null) {
                page.close();
                page = null;
            }
            if (context != null) {
                context.close();
                context = null;
            }
            if (browser != null) {
                browser.close();
                browser = null;
            }
            
            String result = "浏览器已关闭";
            
            log.info("│ 📤 返回结果:");
            log.info("│    {}", result);
            log.info("└─────────────────────────────────────────────────────────────┘");
            log.info("");
            
            return result;
        } catch (Exception e) {
            String error = "关闭浏览器失败: " + e.getMessage();
            log.error("│ ❌ 错误: {}", error);
            log.info("└─────────────────────────────────────────────────────────────┘");
            log.info("");
            return error;
        }
    }
    
    @Tool("执行 JavaScript 代码并返回结果。参数 script 是要执行的 JavaScript 代码")
    public String evaluate(String script) {
        log.info("");
        log.info("┌─────────────────────────────────────────────────────────────┐");
        log.info("│ 🔧 工具调用: evaluate (执行 JS)                          │");
        log.info("├─────────────────────────────────────────────────────────────┤");
        log.info("│ 📥 输入参数:");
        log.info("│    • script: {}", script);
        
        try {
            // 在远程模式下检查页面是否已打开
            if (remoteBrowserEnabled) {
                if (currentPageUrl == null || currentPageUrl.isEmpty()) {
                    return "错误: 请先使用 navigate 工具打开网页";
                }
                // 远程模式下调用远程执行JS方法
                return evaluateRemote(script);
            }
            
            // 本地模式检查
            if (page == null) {
                return "错误: 请先使用 navigate 工具打开网页";
            }
            
            Object result = page.evaluate(script);
            String resultStr = String.format("JavaScript 执行结果: %s", result);
            
            log.info("│ 📤 返回结果:");
            log.info("│    {}", resultStr);
            log.info("└─────────────────────────────────────────────────────────────┘");
            log.info("");
            
            return resultStr;
        } catch (Exception e) {
            String error = "执行 JavaScript 失败: " + e.getMessage();
            log.error("│ ❌ 错误: {}", error);
            log.info("└─────────────────────────────────────────────────────────────┘");
            log.info("");
            return error;
        }
    }
    
    /**
     * 获取当前页面的详细状态（内部辅助方法）
     * 返回: URL、标题、可见的输入框、按钮等信息
     */
    private String getPageState() {
        try {
            if (page == null) {
                return "页面未加载";
            }
            
            String url = page.url();
            String title = page.title();
            
            // 检测页面上的关键元素
            @SuppressWarnings("unchecked")
            java.util.Map<String, Object> elements = (java.util.Map<String, Object>) page.evaluate(
                "() => { " +
                "  const result = { inputs: [], textareas: [], buttons: [] }; " +
                "  " +
                "  try { " +
                "    document.querySelectorAll('input[type=text], input[type=search], input:not([type])').forEach(el => { " +
                "      try { " +
                "        const style = window.getComputedStyle(el); " +
                "        if (style.display !== 'none' && style.visibility !== 'hidden') { " +
                "          result.inputs.push({ " +
                "            id: el.id || '', " +
                "            name: el.name || '', " +
                "            placeholder: el.placeholder || '' " +
                "          }); " +
                "        } " +
                "      } catch (e) { } " +
                "    }); " +
                "  } catch (e) { } " +
                "  " +
                "  try { " +
                "    document.querySelectorAll('textarea').forEach(el => { " +
                "      try { " +
                "        const style = window.getComputedStyle(el); " +
                "        if (style.display !== 'none' && style.visibility !== 'hidden') { " +
                "          result.textareas.push({ " +
                "            id: el.id || '', " +
                "            placeholder: el.placeholder || '' " +
                "          }); " +
                "        } " +
                "      } catch (e) { } " +
                "    }); " +
                "  } catch (e) { } " +
                "  " +
                "  try { " +
                "    document.querySelectorAll('button, input[type=submit]').forEach(el => { " +
                "      try { " +
                "        const style = window.getComputedStyle(el); " +
                "        if (style.display !== 'none' && style.visibility !== 'hidden') { " +
                "          let text = ''; " +
                "          if (el.tagName === 'BUTTON' && el.textContent) { " +
                "            text = el.textContent.trim().substring(0, 20); " +
                "          } else if (el.tagName === 'INPUT' && el.value) { " +
                "            text = el.value.trim().substring(0, 20); " +
                "          } " +
                "          result.buttons.push({ " +
                "            id: el.id || '', " +
                "            text: text " +
                "          }); " +
                "        } " +
                "      } catch (e) { } " +
                "    }); " +
                "  } catch (e) { } " +
                "  " +
                "  result.inputs = result.inputs.slice(0, 3); " +
                "  result.textareas = result.textareas.slice(0, 3); " +
                "  result.buttons = result.buttons.slice(0, 5); " +
                "  " +
                "  return result; " +
                "}"
            );
            
            StringBuilder state = new StringBuilder();
            state.append("当前 URL: ").append(url).append("\n");
            state.append("页面标题: ").append(title).append("\n");
            
            // 输入框信息
            @SuppressWarnings("unchecked")
            java.util.List<java.util.Map<String, String>> inputs = 
                (java.util.List<java.util.Map<String, String>>) elements.get("inputs");
            if (inputs != null && !inputs.isEmpty()) {
                state.append("可用输入框: ");
                for (java.util.Map<String, String> input : inputs) {
                    if (input.get("id") != null && !input.get("id").isEmpty()) {
                        state.append("#").append(input.get("id")).append(" ");
                    } else if (input.get("name") != null && !input.get("name").isEmpty()) {
                        state.append("[name=\"").append(input.get("name")).append("\"] ");
                    }
                }
                state.append("\n");
            }
            
            // textarea 信息
            @SuppressWarnings("unchecked")
            java.util.List<java.util.Map<String, String>> textareas = 
                (java.util.List<java.util.Map<String, String>>) elements.get("textareas");
            if (textareas != null && !textareas.isEmpty()) {
                state.append("可用文本区: ");
                for (java.util.Map<String, String> textarea : textareas) {
                    if (textarea.get("id") != null && !textarea.get("id").isEmpty()) {
                        state.append("#").append(textarea.get("id")).append(" ");
                    }
                }
                state.append("\n");
            }
            
            // 按钮信息
            @SuppressWarnings("unchecked")
            java.util.List<java.util.Map<String, String>> buttons = 
                (java.util.List<java.util.Map<String, String>>) elements.get("buttons");
            if (buttons != null && !buttons.isEmpty()) {
                state.append("可用按钮: ");
                for (java.util.Map<String, String> button : buttons) {
                    String buttonInfo = "";
                    if (button.get("id") != null && !button.get("id").isEmpty()) {
                        buttonInfo = "#" + button.get("id");
                    } else if (button.get("text") != null && !button.get("text").isEmpty()) {
                        buttonInfo = "\"" + button.get("text") + "\"";
                    }
                    if (!buttonInfo.isEmpty()) {
                        state.append(buttonInfo).append(" ");
                    }
                }
                state.append("\n");
            }
            
            return state.toString().trim();
        } catch (Exception e) {
            return "无法获取页面状态: " + e.getMessage();
        }
    }
    
    @Tool("对当前页面或指定元素截图。参数 fullPage 为 true 时截取整页（默认 false），selector 可指定元素选择器，返回 base64 格式图片")
    public String screenshot(Boolean fullPage, String selector) {
        log.info("");
        log.info("┌─────────────────────────────────────────────────────────────┐");
        log.info("│ 📸 工具调用: screenshot (截图)                           │");
        log.info("├─────────────────────────────────────────────────────────────┤");
        log.info("│ 📥 输入参数:");
        log.info("│    • fullPage: {}", fullPage == null ? false : fullPage);
        if (selector != null) {
            log.info("│    • selector: {}", selector);
        }
        
        try {
            if (page == null) {
                return "错误: 请先使用 navigate 工具打开网页";
            }
            
            byte[] screenshotBytes;
            // 确保 fullPage 参数是布尔类型
            boolean isFullPage = false;
            if (fullPage != null) {
                isFullPage = fullPage;
            }
            
            if (selector != null && !selector.isEmpty()) {
                // 元素截图
                Locator element = page.locator(selector);
                screenshotBytes = element.screenshot();
                log.info("│ ✅ 元素截图成功: {}", selector);
            } else if (isFullPage) {
                // 全页截图
                screenshotBytes = page.screenshot(new Page.ScreenshotOptions().setFullPage(true));
                log.info("│ ✅ 全页截图成功");
            } else {
                // 视口截图
                screenshotBytes = page.screenshot();
                log.info("│ ✅ 视口截图成功");
            }
            
            String base64 = Base64.getEncoder().encodeToString(screenshotBytes);
            String result = "data:image/png;base64," + base64;
            
            log.info("│ 📤 返回结果:");
            log.info("│    截图大小: {} KB", screenshotBytes.length / 1024);
            log.info("└─────────────────────────────────────────────────────────────┘");
            log.info("");
            
            return result;
        } catch (Exception e) {
            String error = "截图失败: " + e.getMessage();
            log.error("│ ❌ 错误: {}", error);
            log.info("└─────────────────────────────────────────────────────────────┘");
            log.info("");
            return error;
        }
    }
    
    @Tool("获取浏览器控制台日志。参数 type 可选 'all', 'error', 'warning', 'log', 'info' 等，limit 限制返回条数（默认 50）")
    public String getConsoleLogs(String type, Integer limit) {
        log.info("");
        log.info("┌─────────────────────────────────────────────────────────────┐");
        log.info("│ 📊 工具调用: getConsoleLogs (获取控制台日志)         │");
        log.info("├─────────────────────────────────────────────────────────────┤");
        log.info("│ 📥 输入参数:");
        log.info("│    • type: {}", type == null ? "all" : type);
        log.info("│    • limit: {}", limit == null ? 50 : limit);
        
        try {
            if (page == null) {
                return "错误: 请先使用 navigate 工具打开网页";
            }
            
            // 从内存中获取已收集的控制台日志
            String filterType = type == null ? "all" : type.toLowerCase();
            int maxLimit = limit == null ? 50 : limit;
            
            java.util.List<ConsoleLogEntry> filteredLogs = consoleLogs.stream()
                .filter(entry -> "all".equals(filterType) || entry.type.equalsIgnoreCase(filterType))
                .collect(java.util.stream.Collectors.toList());
            
            // 只返回最后 N 条
            int fromIndex = Math.max(0, filteredLogs.size() - maxLimit);
            filteredLogs = filteredLogs.subList(fromIndex, filteredLogs.size());
            
            if (filteredLogs.isEmpty()) {
                String msg = String.format("暂无%s类型的控制台日志（总共收集 %d 条日志）", 
                    "all".equals(filterType) ? "任何" : filterType,
                    consoleLogs.size());
                
                log.info("│ 📤 返回结果:");
                log.info("│    {}", msg);
                log.info("└─────────────────────────────────────────────────────────────┘");
                log.info("");
                return msg;
            }
            
            StringBuilder result = new StringBuilder();
            result.append(String.format("控制台日志（总计 %d 条，类型: %s）:\n", 
                filteredLogs.size(), filterType));
            result.append("──────────────────────────────\n");
            
            for (int i = 0; i < filteredLogs.size(); i++) {
                ConsoleLogEntry entry = filteredLogs.get(i);
                result.append(String.format("[%d] [%s] %s\n", 
                    i + 1, 
                    entry.type.toUpperCase(), 
                    entry.text));
            }
            
            log.info("│ 📤 返回结果:");
            log.info("│    日志条数: {} (总共收集: {})", filteredLogs.size(), consoleLogs.size());
            log.info("└─────────────────────────────────────────────────────────────┘");
            log.info("");
            
            return result.toString();
        } catch (Exception e) {
            String error = "获取控制台日志失败: " + e.getMessage();
            log.error("│ ❌ 错误: {}", error);
            log.info("└─────────────────────────────────────────────────────────────┘");
            log.info("");
            return error;
        }
    }
    
    @Tool("鼠标悬停在指定元素上，触发悬停事件。参数 selector 是元素选择器")
    public String hover(String selector) {
        log.info("");
        log.info("┌─────────────────────────────────────────────────────────────┐");
        log.info("│ 🎯 工具调用: hover (鼠标悬停)                            │");
        log.info("├─────────────────────────────────────────────────────────────┤");
        log.info("│ 📥 输入参数:");
        log.info("│    • selector: {}", selector);
        
        try {
            if (page == null) {
                return "错误: 请先使用 navigate 工具打开网页";
            }
            
            // 智能悬停：先尝试正常 hover，失败则用 JS
            try {
                page.hover(selector, new Page.HoverOptions().setTimeout(3000));
                log.info("│ ✅ 正常悬停成功");
            } catch (Exception hoverError) {
                log.info("│ ⚠️  正常悬停失败，使用 JS 绕过");
                page.evaluate(
                    "(selector) => { " +
                    "  const el = document.querySelector(selector); " +
                    "  if (el) { " +
                    "    el.dispatchEvent(new MouseEvent('mouseover', { bubbles: true })); " +
                    "    el.dispatchEvent(new MouseEvent('mouseenter', { bubbles: true })); " +
                    "  } " +
                    "}",
                    selector
                );
            }
            
            String result = "成功悬停在元素: " + selector;
            
            log.info("│ 📤 返回结果:");
            log.info("│    {}", result);
            log.info("└─────────────────────────────────────────────────────────────┘");
            log.info("");
            
            return result;
        } catch (Exception e) {
            String error = "悬停失败: " + e.getMessage();
            log.error("│ ❌ 错误: {}", error);
            log.info("└─────────────────────────────────────────────────────────────┘");
            log.info("");
            return error;
        }
    }
    
    @Tool("选择下拉框（select 元素）的选项。参数 selector 是 select 元素选择器，value 是要选择的选项值")
    public String select(String selector, String value) {
        log.info("");
        log.info("┌─────────────────────────────────────────────────────────────┐");
        log.info("│ 🔽 工具调用: select (选择下拉框)                         │");
        log.info("├─────────────────────────────────────────────────────────────┤");
        log.info("│ 📥 输入参数:");
        log.info("│    • selector: {}", selector);
        log.info("│    • value: {}", value);
        
        try {
            if (page == null) {
                return "错误: 请先使用 navigate 工具打开网页";
            }
            
            // 使用 Playwright 的 selectOption 方法
            page.selectOption(selector, value);
            
            String result = String.format("成功选择下拉框 %s 的选项: %s", selector, value);
            
            log.info("│ 📤 返回结果:");
            log.info("│    {}", result);
            log.info("└─────────────────────────────────────────────────────────────┘");
            log.info("");
            
            return result;
        } catch (Exception e) {
            String error = "选择下拉框失败: " + e.getMessage();
            log.error("│ ❌ 错误: {}", error);
            log.info("└─────────────────────────────────────────────────────────────┘");
            log.info("");
            return error;
        }
    }
    
    @Tool("上传文件到文件输入框。参数 selector 是 input[type='file'] 元素选择器，filePath 是文件的绝对路径")
    public String uploadFile(String selector, String filePath) {
        log.info("");
        log.info("┌─────────────────────────────────────────────────────────────┐");
        log.info("│ 📂 工具调用: uploadFile (上传文件)                       │");
        log.info("├─────────────────────────────────────────────────────────────┤");
        log.info("│ 📥 输入参数:");
        log.info("│    • selector: {}", selector);
        log.info("│    • filePath: {}", filePath);
        
        try {
            if (page == null) {
                return "错误: 请先使用 navigate 工具打开网页";
            }
            
            // 验证文件是否存在
            java.nio.file.Path path = Paths.get(filePath);
            if (!java.nio.file.Files.exists(path)) {
                return "错误: 文件不存在: " + filePath;
            }
            
            // 使用 Playwright 的 setInputFiles 方法
            page.setInputFiles(selector, path);
            
            String result = String.format("成功上传文件到 %s: %s", selector, filePath);
            
            log.info("│ 📤 返回结果:");
            log.info("│    {}", result);
            log.info("└─────────────────────────────────────────────────────────────┘");
            log.info("");
            
            return result;
        } catch (Exception e) {
            String error = "上传文件失败: " + e.getMessage();
            log.error("│ ❌ 错误: {}", error);
            log.info("└─────────────────────────────────────────────────────────────┘");
            log.info("");
            return error;
        }
    }
    
    @Tool("获取页面可见文本内容，排除隐藏元素。返回所有可见文本")
    public String getVisibleText() {
        log.info("");
        log.info("┌─────────────────────────────────────────────────────────────┐");
        log.info("│ 📄 工具调用: getVisibleText (获取可见文本)              │");
        log.info("├─────────────────────────────────────────────────────────────┤");
        
        try {
            // 在远程模式下检查页面是否已打开
            if (remoteBrowserEnabled) {
                if (currentPageUrl == null || currentPageUrl.isEmpty()) {
                    return "错误: 请先使用 navigate 工具打开网页";
                }
                // 远程模式下调用远程获取可见文本方法
                return getVisibleTextRemote();
            }
            
            // 本地模式检查
            if (page == null) {
                return "错误: 请先使用 navigate 工具打开网页";
            }
            
            // 使用 JavaScript 提取所有可见文本
            String visibleText = (String) page.evaluate(
                "() => { " +
                "  function isVisible(el) { " +
                "    const style = window.getComputedStyle(el); " +
                "    return style.display !== 'none' && style.visibility !== 'hidden' && style.opacity !== '0'; " +
                "  } " +
                "  " +
                "  function getText(node) { " +
                "    let text = ''; " +
                "    if (node.nodeType === Node.TEXT_NODE) { " +
                "      return node.textContent.trim(); " +
                "    } " +
                "    if (node.nodeType === Node.ELEMENT_NODE && isVisible(node)) { " +
                "      for (let child of node.childNodes) { " +
                "        text += getText(child) + ' '; " +
                "      } " +
                "    } " +
                "    return text; " +
                "  } " +
                "  " +
                "  return getText(document.body).replace(/\\s+/g, ' ').trim(); " +
                "}"
            );
            
            log.info("│ 📤 返回结果:");
            log.info("│    文本长度: {} 字符", visibleText.length());
            log.info("└─────────────────────────────────────────────────────────────┘");
            log.info("");
            
            // 过滤敏感内容后再返回
            return filterSensitiveContent(visibleText);
        } catch (Exception e) {
            String error = "获取可见文本失败: " + e.getMessage();
            log.error("│ ❌ 错误: {}", error);
            log.info("└─────────────────────────────────────────────────────────────┘");
            log.info("");
            return error;
        }
    }
    
    @Tool("获取页面 HTML 内容。参数 selector 可限制到特定元素，cleanHtml=true 移除 script/style/comment 等干扰内容")
    public String getVisibleHtml(String selector, Boolean cleanHtml) {
        log.info("");
        log.info("┌─────────────────────────────────────────────────────────────┐");
        log.info("│ 📝 工具调用: getVisibleHtml (获取HTML)                   │");
        log.info("├─────────────────────────────────────────────────────────────┤");
        log.info("│ 📥 输入参数:");
        if (selector != null) {
            log.info("│    • selector: {}", selector);
        }
        log.info("│    • cleanHtml: {}", cleanHtml == null ? false : cleanHtml);
        
        try {
            // 在远程模式下检查页面是否已打开
            if (remoteBrowserEnabled) {
                if (currentPageUrl == null || currentPageUrl.isEmpty()) {
                    return "错误: 请先使用 navigate 工具打开网页";
                }
                // 远程模式下调用远程获取HTML方法
                boolean shouldClean = cleanHtml != null ? cleanHtml : false;
                return getVisibleHtmlRemote(selector, shouldClean);
            }
            
            // 本地模式检查
            if (page == null) {
                return "错误: 请先使用 navigate 工具打开网页";
            }
            
            // 确保 cleanHtml 参数是布尔类型
            boolean shouldClean = false;
            if (cleanHtml != null) {
                shouldClean = cleanHtml;
            }
            
            // 使用 JavaScript 获取 HTML
            java.util.Map<String, Object> args = new java.util.HashMap<>();
            args.put("selector", selector);
            args.put("cleanHtml", shouldClean);
            
            String html = (String) page.evaluate(
                "(args) => { " +
                "  let container = args.selector ? document.querySelector(args.selector) : document.documentElement; " +
                "  if (!container) return ''; " +
                "  " +
                "  let clone = container.cloneNode(true); " +
                "  " +
                "  if (args.cleanHtml) { " +
                "    clone.querySelectorAll('script').forEach(el => el.remove()); " +
                "    clone.querySelectorAll('style').forEach(el => el.remove()); " +
                "    clone.querySelectorAll('meta').forEach(el => el.remove()); " +
                "    clone.querySelectorAll('link[rel=\"stylesheet\"]').forEach(el => el.remove()); " +
                "  } " +
                "  " +
                "  return clone.outerHTML; " +
                "}",
                args
            );
            
            log.info("│ 📤 返回结果:");
            log.info("│    HTML 长度: {} 字符", html.length());
            log.info("└─────────────────────────────────────────────────────────────┘");
            log.info("");
            
            // 过滤敏感内容后再返回
            return filterSensitiveContent(html);
        } catch (Exception e) {
            String error = "获取 HTML 失败: " + e.getMessage();
            log.error("│ ❌ 错误: {}", error);
            log.info("└─────────────────────────────────────────────────────────────┘");
            log.info("");
            return error;
        }
    }
    
    @Tool("点击链接并自动切换到新打开的标签页。参数 selector 是链接元素选择器")
    public String clickAndSwitchTab(String selector) {
        log.info("");
        log.info("┌─────────────────────────────────────────────────────────────┐");
        log.info("│ 💪 工具调用: clickAndSwitchTab (点击并切换标签页)       │");
        log.info("├─────────────────────────────────────────────────────────────┤");
        log.info("│ 📥 输入参数:");
        log.info("│    • selector: {}", selector);
        
        try {
            if (page == null || context == null) {
                return "错误: 请先使用 navigate 工具打开网页";
            }
            
            // 监听新标签页事件
            Page[] newPage = new Page[1];
            context.onPage(p -> {
                newPage[0] = p;
                log.info("│ 🆕 检测到新标签页");
            });
            
            // 点击链接
            page.click(selector);
            
            // 等待新标签页打开（最多等待 5 秒）
            int maxWait = 50; // 5秒
            int waited = 0;
            while (newPage[0] == null && waited < maxWait) {
                try {
                    Thread.sleep(100);
                    waited++;
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
            
            if (newPage[0] == null) {
                return "错误: 点击后未打开新标签页，可能不是 target=\"_blank\" 链接";
            }
            
            // 切换到新标签页
            page = newPage[0];
            page.waitForLoadState();
            
            String result = String.format("成功切换到新标签页: %s", page.url());
            
            log.info("│ 📤 返回结果:");
            log.info("│    {}", result);
            log.info("└─────────────────────────────────────────────────────────────┘");
            log.info("");
            
            return result;
        } catch (Exception e) {
            String error = "点击并切换标签页失败: " + e.getMessage();
            log.error("│ ❌ 错误: {}", error);
            log.info("└─────────────────────────────────────────────────────────────┘");
            log.info("");
            return error;
        }
    }
    
    @Tool("在 iframe 中点击元素。参数 iframeSelector 是 iframe 选择器，selector 是 iframe 内部元素选择器")
    public String iframeClick(String iframeSelector, String selector) {
        log.info("");
        log.info("┌─────────────────────────────────────────────────────────────┐");
        log.info("│ 🔲 工具调用: iframeClick (iframe中点击)                  │");
        log.info("├─────────────────────────────────────────────────────────────┤");
        log.info("│ 📥 输入参数:");
        log.info("│    • iframeSelector: {}", iframeSelector);
        log.info("│    • selector: {}", selector);
        
        try {
            if (page == null) {
                return "错误: 请先使用 navigate 工具打开网页";
            }
            
            // 获取 iframe
            FrameLocator frameLocator = page.frameLocator(iframeSelector);
            
            // 在 iframe 中点击元素
            frameLocator.locator(selector).click();
            
            String result = String.format("成功在 iframe %s 中点击元素: %s", iframeSelector, selector);
            
            log.info("│ 📤 返回结果:");
            log.info("│    {}", result);
            log.info("└─────────────────────────────────────────────────────────────┘");
            log.info("");
            
            return result;
        } catch (Exception e) {
            String error = "iframe 中点击失败: " + e.getMessage();
            log.error("│ ❌ 错误: {}", error);
            log.info("└─────────────────────────────────────────────────────────────┘");
            log.info("");
            return error;
        }
    }
    
    @Tool("在 iframe 中填写输入框。参数 iframeSelector 是 iframe 选择器，selector 是 iframe 内部输入框选择器，text 是要填写的文本")
    public String iframeFill(String iframeSelector, String selector, String text) {
        log.info("");
        log.info("┌─────────────────────────────────────────────────────────────┐");
        log.info("│ 🔲 工具调用: iframeFill (iframe中填写)                   │");
        log.info("├─────────────────────────────────────────────────────────────┤");
        log.info("│ 📥 输入参数:");
        log.info("│    • iframeSelector: {}", iframeSelector);
        log.info("│    • selector: {}", selector);
        log.info("│    • text: {}", text);
        
        try {
            if (page == null) {
                return "错误: 请先使用 navigate 工具打开网页";
            }
            
            // 获取 iframe
            FrameLocator frameLocator = page.frameLocator(iframeSelector);
            
            // 在 iframe 中填写输入框
            frameLocator.locator(selector).fill(text);
            
            String result = String.format("成功在 iframe %s 中填写 %s: %s", iframeSelector, selector, text);
            
            log.info("│ 📤 返回结果:");
            log.info("│    {}", result);
            log.info("└─────────────────────────────────────────────────────────────┘");
            log.info("");
            
            return result;
        } catch (Exception e) {
            String error = "iframe 中填写失败: " + e.getMessage();
            log.error("│ ❌ 错误: {}", error);
            log.info("└─────────────────────────────────────────────────────────────┘");
            log.info("");
            return error;
        }
    }
    
    /**
     * 远程模式下获取页面信息实现
     */
    private String getPageInfoRemote() {
        try {
            log.info("│ ℹ️  使用远程浏览器模式");
            
            // 构造远程控制 URL
            String remoteUrl = String.format("http://%s:%d/browser/getPageInfo", 
                remoteBrowserHost, remoteBrowserPort);
            
            // 发送 HTTP 请求到 Electron 应用
            java.net.HttpURLConnection connection = (java.net.HttpURLConnection) new java.net.URL(remoteUrl).openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(5000);
            connection.setReadTimeout(10000);
            
            int responseCode = connection.getResponseCode();
            if (responseCode == 200) {
                // 读取响应
                java.io.BufferedReader reader = new java.io.BufferedReader(
                    new java.io.InputStreamReader(connection.getInputStream()));
                StringBuilder response = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    response.append(line);
                }
                reader.close();
                
                // 更新当前页面 URL
                updateCurrentPageUrlRemote();
                
                log.info("│ ✅ 远程获取页面信息成功");
                log.info("│ 📤 返回结果:");
                log.info("│    {}", response.toString());
                log.info("└─────────────────────────────────────────────────────────────┘");
                log.info("");
                
                return response.toString();
            } else {
                String error = "远程获取页面信息失败，HTTP 状态码: " + responseCode;
                log.error("│ ❌ {}", error);
                log.info("└─────────────────────────────────────────────────────────────┘");
                log.info("");
                return error;
            }
        } catch (Exception e) {
            String error = "远程获取页面信息异常: " + e.getMessage();
            log.error("│ ❌ {}", error, e);
            log.info("└─────────────────────────────────────────────────────────────┘");
            log.info("");
            return error;
        }
    }
    
    /**
     * 远程模式下更新当前页面 URL
     */
    private void updateCurrentPageUrlRemote() {
        try {
            // 构造远程控制 URL
            String remoteUrl = String.format("http://%s:%d/browser/getPageInfo", 
                remoteBrowserHost, remoteBrowserPort);
            
            // 发送 HTTP 请求到 Electron 应用
            java.net.HttpURLConnection connection = (java.net.HttpURLConnection) new java.net.URL(remoteUrl).openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(5000);
            connection.setReadTimeout(10000);
            
            int responseCode = connection.getResponseCode();
            if (responseCode == 200) {
                // 读取响应
                java.io.BufferedReader reader = new java.io.BufferedReader(
                    new java.io.InputStreamReader(connection.getInputStream()));
                StringBuilder response = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    response.append(line);
                }
                reader.close();
                
                // 解析 JSON 响应获取当前 URL
                // 简化处理，实际应该使用 JSON 解析库
                String responseStr = response.toString();
                if (responseStr.contains("\"url\":\"")) {
                    int start = responseStr.indexOf("\"url\":\"") + 7;
                    int end = responseStr.indexOf("\"", start);
                    if (end > start) {
                        String currentUrl = responseStr.substring(start, end);
                        currentPageUrl = java.net.URLDecoder.decode(currentUrl, "UTF-8");
                    }
                }
            }
        } catch (Exception e) {
            log.warn("更新远程页面 URL 失败: {}", e.getMessage());
        }
    }
    
    /**
     * 远程模式下获取页面可见文本实现
     */
    private String getVisibleTextRemote() {
        try {
            log.info("│ ℹ️  使用远程浏览器模式获取可见文本");
            
            // 构造远程控制 URL
            String remoteUrl = String.format("http://%s:%d/browser/getVisibleText", 
                remoteBrowserHost, remoteBrowserPort);
            
            // 发送 HTTP 请求到 Electron 应用
            java.net.HttpURLConnection connection = (java.net.HttpURLConnection) new java.net.URL(remoteUrl).openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(5000);
            connection.setReadTimeout(10000);
            
            int responseCode = connection.getResponseCode();
            if (responseCode == 200) {
                // 读取响应
                java.io.BufferedReader reader = new java.io.BufferedReader(
                    new java.io.InputStreamReader(connection.getInputStream()));
                StringBuilder response = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    response.append(line);
                }
                reader.close();
                
                // 更新当前页面 URL
                updateCurrentPageUrlRemote();
                
                log.info("│ ✅ 远程获取可见文本成功");
                log.info("│ 📤 返回结果:");
                log.info("│    {}", response.toString());
                log.info("└─────────────────────────────────────────────────────────────┘");
                log.info("");
                
                return response.toString();
            } else {
                String error = "远程获取可见文本失败，HTTP 状态码: " + responseCode;
                log.error("│ ❌ {}", error);
                log.info("└─────────────────────────────────────────────────────────────┘");
                log.info("");
                return error;
            }
        } catch (Exception e) {
            String error = "远程获取可见文本异常: " + e.getMessage();
            log.error("│ ❌ {}", error, e);
            log.info("└─────────────────────────────────────────────────────────────┘");
            log.info("");
            return error;
        }
    }
    
    /**
     * 远程模式下获取页面 HTML 实现方法
     */
    private String getVisibleHtmlRemote(String selector, boolean shouldClean) {
        try {
            log.info("│ ℹ️  使用远程浏览器模式获取页面 HTML");
            
            // 构造远程控制 URL，包含参数
            String remoteUrl = String.format("http://%s:%d/browser/getVisibleHtml?selector=%s&clean=%b", 
                remoteBrowserHost, remoteBrowserPort, 
                java.net.URLEncoder.encode(selector != null ? selector : "", "UTF-8"), 
                shouldClean);
            
            // 发送 HTTP 请求到 Electron 应用
            java.net.HttpURLConnection connection = (java.net.HttpURLConnection) new java.net.URL(remoteUrl).openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(5000);
            connection.setReadTimeout(10000);
            
            int responseCode = connection.getResponseCode();
            if (responseCode == 200) {
                // 读取响应
                java.io.BufferedReader reader = new java.io.BufferedReader(
                    new java.io.InputStreamReader(connection.getInputStream()));
                StringBuilder response = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    response.append(line);
                }
                reader.close();
                
                // 更新当前页面 URL
                updateCurrentPageUrlRemote();
                
                log.info("│ ✅ 远程获取页面 HTML 成功");
                log.info("│ 📤 返回结果:");
                log.info("│    {}", response.toString());
                log.info("└─────────────────────────────────────────────────────────────┘");
                log.info("");
                
                return response.toString();
            } else {
                String error = "远程获取页面 HTML 失败，HTTP 状态码: " + responseCode;
                log.error("│ ❌ {}", error);
                log.info("└─────────────────────────────────────────────────────────────┘");
                log.info("");
                return error;
            }
        } catch (Exception e) {
            String error = "远程获取页面 HTML 异常: " + e.getMessage();
            log.error("│ ❌ {}", error, e);
            log.info("└─────────────────────────────────────────────────────────────┘");
            log.info("");
            return error;
        }
    }
    
    /**
     * 远程模式下执行 JavaScript 实现方法
     */
    private String evaluateRemote(String script) {
        try {
            log.info("│ ℹ️  使用远程浏览器模式执行 JavaScript");
            
            // 构造远程控制 URL
            String remoteUrl = String.format("http://%s:%d/browser/executeJs", 
                remoteBrowserHost, remoteBrowserPort);
            
            // 发送 HTTP POST 请求到 Electron 应用
            java.net.HttpURLConnection connection = (java.net.HttpURLConnection) new java.net.URL(remoteUrl).openConnection();
            connection.setRequestMethod("POST");
            connection.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
            connection.setConnectTimeout(5000);
            connection.setReadTimeout(10000);
            connection.setDoOutput(true);
            
            // 构造 JSON 请求体
            String jsonBody = String.format("{\"script\": \"%s\"}", 
                script.replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r"));
            
            // 发送请求
            try (java.io.OutputStream os = connection.getOutputStream()) {
                byte[] input = jsonBody.getBytes("utf-8");
                os.write(input, 0, input.length);
            }
            
            int responseCode = connection.getResponseCode();
            if (responseCode == 200) {
                // 读取响应
                java.io.BufferedReader reader = new java.io.BufferedReader(
                    new java.io.InputStreamReader(connection.getInputStream()));
                StringBuilder response = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    response.append(line);
                }
                reader.close();
                
                // 更新当前页面 URL
                updateCurrentPageUrlRemote();
                
                log.info("│ ✅ 远程执行 JavaScript 成功");
                log.info("│ 📤 返回结果:");
                log.info("│    {}", response.toString());
                log.info("└─────────────────────────────────────────────────────────────┘");
                log.info("");
                
                return response.toString();
            } else {
                String error = "远程执行 JavaScript 失败，HTTP 状态码: " + responseCode;
                log.error("│ ❌ {}", error);
                log.info("└─────────────────────────────────────────────────────────────┘");
                log.info("");
                return error;
            }
        } catch (Exception e) {
            String error = "远程执行 JavaScript 异常: " + e.getMessage();
            log.error("│ ❌ {}", error, e);
            log.info("└─────────────────────────────────────────────────────────────┘");
            log.info("");
            return error;
        }
    }
    
    /**
     * 远程模式下分析页面实现方法
     */
    private String analyzePageRemote() {
        try {
            log.info("│ ℹ️  使用远程浏览器模式分析页面");
            
            // 构造远程控制 URL
            String remoteUrl = String.format("http://%s:%d/browser/analyzePage", 
                remoteBrowserHost, remoteBrowserPort);
            
            // 发送 HTTP 请求到 Electron 应用
            java.net.HttpURLConnection connection = (java.net.HttpURLConnection) new java.net.URL(remoteUrl).openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(5000);
            connection.setReadTimeout(10000);
            
            int responseCode = connection.getResponseCode();
            if (responseCode == 200) {
                // 读取响应
                java.io.BufferedReader reader = new java.io.BufferedReader(
                    new java.io.InputStreamReader(connection.getInputStream()));
                StringBuilder response = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    response.append(line);
                }
                reader.close();
                
                // 更新当前页面 URL
                updateCurrentPageUrlRemote();
                
                log.info("│ ✅ 远程分析页面成功");
                log.info("│ 📤 返回结果:");
                log.info("│    {}", response.toString());
                log.info("└─────────────────────────────────────────────────────────────┘");
                log.info("");
                
                return response.toString();
            } else {
                String error = "远程分析页面失败，HTTP 状态码: " + responseCode;
                log.error("│ ❌ {}", error);
                log.info("└─────────────────────────────────────────────────────────────┘");
                log.info("");
                return error;
            }
        } catch (Exception e) {
            String error = "远程分析页面异常: " + e.getMessage();
            log.error("│ ❌ {}", error, e);
            log.info("└─────────────────────────────────────────────────────────────┘");
            log.info("");
            return error;
        }
    }
    
    /**
     * 远程模式下截图实现方法
     */
    private String screenshotRemote(Boolean fullPage, String selector) {
        try {
            log.info("│ ℹ️  使用远程浏览器模式截图");
            
            // 构造远程控制 URL，包含参数
            String remoteUrl = String.format("http://%s:%d/browser/screenshot?fullPage=%b&selector=%s", 
                remoteBrowserHost, remoteBrowserPort, 
                fullPage != null ? fullPage : false,
                java.net.URLEncoder.encode(selector != null ? selector : "", "UTF-8"));
            
            // 发送 HTTP 请求到 Electron 应用
            java.net.HttpURLConnection connection = (java.net.HttpURLConnection) new java.net.URL(remoteUrl).openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(5000);
            connection.setReadTimeout(15000); // 截图可能需要更长时间
            
            int responseCode = connection.getResponseCode();
            if (responseCode == 200) {
                // 读取响应（base64 图片数据）
                java.io.BufferedReader reader = new java.io.BufferedReader(
                    new java.io.InputStreamReader(connection.getInputStream()));
                StringBuilder response = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    response.append(line);
                }
                reader.close();
                
                // 更新当前页面 URL
                updateCurrentPageUrlRemote();
                
                log.info("│ ✅ 远程截图成功");
                log.info("│ 📤 返回结果:");
                log.info("│    base64 图片数据");
                log.info("└─────────────────────────────────────────────────────────────┘");
                log.info("");
                
                return response.toString();
            } else {
                String error = "远程截图失败，HTTP 状态码: " + responseCode;
                log.error("│ ❌ {}", error);
                log.info("└─────────────────────────────────────────────────────────────┘");
                log.info("");
                return error;
            }
        } catch (Exception e) {
            String error = "远程截图异常: " + e.getMessage();
            log.error("│ ❌ {}", error, e);
            log.info("└─────────────────────────────────────────────────────────────┘");
            log.info("");
            return error;
        }
    }
}
