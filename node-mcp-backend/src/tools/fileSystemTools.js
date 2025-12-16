import fs from 'fs/promises';
import path from 'path';

/**
 * 文件系统工具
 */
export class FileSystemTools {
  static getToolDefinitions() {
    return [
      {
        type: 'function',
        function: {
          name: 'readFile',
          description: '读取文件内容',
          parameters: {
            type: 'object',
            properties: {
              filePath: { type: 'string', description: '文件路径' }
            },
            required: ['filePath']
          }
        }
      },
      {
        type: 'function',
        function: {
          name: 'writeFile',
          description: '写入文件内容',
          parameters: {
            type: 'object',
            properties: {
              filePath: { type: 'string', description: '文件路径' },
              content: { type: 'string', description: '文件内容' }
            },
            required: ['filePath', 'content']
          }
        }
      },
      {
        type: 'function',
        function: {
          name: 'listDirectory',
          description: '列出目录内容',
          parameters: {
            type: 'object',
            properties: {
              directoryPath: { type: 'string', description: '目录路径' }
            },
            required: ['directoryPath']
          }
        }
      },
      {
        type: 'function',
        function: {
          name: 'deleteFile',
          description: '删除文件',
          parameters: {
            type: 'object',
            properties: {
              filePath: { type: 'string', description: '文件路径' }
            },
            required: ['filePath']
          }
        }
      },
      {
        type: 'function',
        function: {
          name: 'createDirectory',
          description: '创建目录',
          parameters: {
            type: 'object',
            properties: {
              directoryPath: { type: 'string', description: '目录路径' }
            },
            required: ['directoryPath']
          }
        }
      }
    ];
  }

  static async executeTool(toolName, args) {
    switch (toolName) {
      case 'readFile':
        return await this.readFile(args.filePath);
      case 'writeFile':
        return await this.writeFile(args.filePath, args.content);
      case 'listDirectory':
        return await this.listDirectory(args.directoryPath);
      case 'deleteFile':
        return await this.deleteFile(args.filePath);
      case 'createDirectory':
        return await this.createDirectory(args.directoryPath);
      default:
        throw new Error(`Unknown tool: ${toolName}`);
    }
  }

  static async readFile(filePath) {
    try {
      const content = await fs.readFile(filePath, 'utf-8');
      return `文件内容:\n${content}`;
    } catch (error) {
      throw new Error(`读取文件失败: ${error.message}`);
    }
  }

  static async writeFile(filePath, content) {
    try {
      await fs.writeFile(filePath, content, 'utf-8');
      return `文件写入成功: ${filePath}`;
    } catch (error) {
      throw new Error(`写入文件失败: ${error.message}`);
    }
  }

  static async listDirectory(directoryPath) {
    try {
      const files = await fs.readdir(directoryPath);
      return `目录内容:\n${files.join('\n')}`;
    } catch (error) {
      throw new Error(`读取目录失败: ${error.message}`);
    }
  }

  static async deleteFile(filePath) {
    try {
      await fs.unlink(filePath);
      return `文件删除成功: ${filePath}`;
    } catch (error) {
      throw new Error(`删除文件失败: ${error.message}`);
    }
  }

  static async createDirectory(directoryPath) {
    try {
      await fs.mkdir(directoryPath, { recursive: true });
      return `目录创建成功: ${directoryPath}`;
    } catch (error) {
      throw new Error(`创建目录失败: ${error.message}`);
    }
  }
}
