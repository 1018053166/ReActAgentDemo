import { exec } from 'child_process';
import { promisify } from 'util';
import os from 'os';
import fs from 'fs/promises';
import path from 'path';

const execAsync = promisify(exec);

/**
 * 命令执行工具 - 支持跨平台命令和脚本执行
 */
export class CommandTools {
  static getToolDefinitions() {
    return [
      {
        type: 'function',
        function: {
          name: 'executeCommand',
          description: '执行系统命令（简单命令，如 ls、ps、curl 等）',
          parameters: {
            type: 'object',
            properties: {
              command: { 
                type: 'string', 
                description: '要执行的命令' 
              },
              workDir: { 
                type: 'string', 
                description: '工作目录（可选）' 
              }
            },
            required: ['command']
          }
        }
      },
      {
        type: 'function',
        function: {
          name: 'executeScript',
          description: '执行脚本代码（支持 shell、bash、python、node 等）',
          parameters: {
            type: 'object',
            properties: {
              code: { 
                type: 'string', 
                description: '脚本代码内容' 
              },
              language: { 
                type: 'string', 
                description: '脚本语言类型：shell、bash、python、node',
                enum: ['shell', 'bash', 'python', 'node']
              },
              workDir: { 
                type: 'string', 
                description: '工作目录（可选）' 
              }
            },
            required: ['code', 'language']
          }
        }
      }
    ];
  }

  static async executeTool(toolName, args) {
    switch (toolName) {
      case 'executeCommand':
        return await this.executeCommand(args.command, args.workDir);
      case 'executeScript':
        return await this.executeScript(args.code, args.language, args.workDir);
      default:
        throw new Error(`Unknown tool: ${toolName}`);
    }
  }

  /**
   * 执行简单命令
   */
  static async executeCommand(command, workDir) {
    try {
      // 安全检查
      if (this.isDangerousCommand(command)) {
        return `⚠️ 危险命令已拦截: ${command}\n提示: 不允许执行删除、格式化等危险操作`;
      }

      const options = {
        timeout: 30000, // 30秒超时
        maxBuffer: 1024 * 1024 // 1MB 输出限制
      };

      if (workDir) {
        options.cwd = workDir;
      }

      console.log(`[CommandTools] 执行命令: ${command}`);
      const { stdout, stderr } = await execAsync(command, options);
      
      let result = '';
      if (stdout) result += stdout;
      if (stderr) result += `\n[STDERR]\n${stderr}`;
      
      return result || '命令执行成功（无输出）';
    } catch (error) {
      return `命令执行失败: ${error.message}`;
    }
  }

  /**
   * 执行脚本代码
   */
  static async executeScript(code, language, workDir) {
    try {
      // 安全检查
      if (this.isDangerousCode(code)) {
        return `⚠️ 危险代码已拦截\n提示: 检测到潜在的危险操作`;
      }

      // 创建临时脚本文件
      const tmpDir = os.tmpdir();
      const scriptExt = this.getScriptExtension(language);
      const scriptPath = path.join(tmpDir, `mcp-script-${Date.now()}${scriptExt}`);
      
      await fs.writeFile(scriptPath, code, 'utf-8');
      console.log(`[CommandTools] 创建脚本: ${scriptPath}`);

      // 根据语言选择执行器
      const executor = this.getExecutor(language, scriptPath);
      
      const options = {
        timeout: 60000, // 脚本允许更长时间
        maxBuffer: 2 * 1024 * 1024, // 2MB 输出
        cwd: workDir || tmpDir
      };

      console.log(`[CommandTools] 执行 ${language} 脚本`);
      const { stdout, stderr } = await execAsync(executor, options);

      // 清理临时文件
      await fs.unlink(scriptPath).catch(() => {});

      let result = '';
      if (stdout) result += stdout;
      if (stderr) result += `\n[STDERR]\n${stderr}`;
      
      return result || '脚本执行成功（无输出）';
    } catch (error) {
      return `脚本执行失败: ${error.message}`;
    }
  }

  /**
   * 获取脚本文件扩展名
   */
  static getScriptExtension(language) {
    const extensions = {
      'shell': '.sh',
      'bash': '.sh',
      'python': '.py',
      'node': '.js'
    };
    return extensions[language] || '.txt';
  }

  /**
   * 获取脚本执行器
   */
  static getExecutor(language, scriptPath) {
    const platform = os.platform();
    
    switch (language) {
      case 'shell':
      case 'bash':
        return platform === 'win32' 
          ? `bash "${scriptPath}"` 
          : `bash "${scriptPath}"`;
      case 'python':
        return `python3 "${scriptPath}"`;
      case 'node':
        return `node "${scriptPath}"`;
      default:
        throw new Error(`不支持的脚本语言: ${language}`);
    }
  }

  /**
   * 检查是否为危险命令
   */
  static isDangerousCommand(command) {
    const dangerousPatterns = [
      /rm\s+-rf\s+\//,          // 删除根目录
      /mkfs/,                    // 格式化
      /dd\s+if=/,               // 危险的 dd 操作
      />\/dev\/sd/,             // 写入磁盘
      /fork.*bomb/,             // fork炸弹
      /sudo\s+rm/,              // sudo删除
      /shutdown/,               // 关机
      /reboot/,                 // 重启
      /init\s+0/,               // 关机
      /pkill\s+-9\s+init/       // 杀死init
    ];

    return dangerousPatterns.some(pattern => pattern.test(command));
  }

  /**
   * 检查是否为危险代码
   */
  static isDangerousCode(code) {
    const dangerousPatterns = [
      /import\s+os.*system.*rm\s+-rf/,
      /exec.*rm\s+-rf/,
      /eval.*dangerous/,
      /subprocess.*shell.*rm/,
      /__import__.*os.*system/
    ];

    return dangerousPatterns.some(pattern => pattern.test(code));
  }

  /**
   * 获取系统信息
   */
  static getSystemInfo() {
    return {
      platform: os.platform(),
      arch: os.arch(),
      shell: process.env.SHELL || 'unknown',
      nodeVersion: process.version
    };
  }
}
