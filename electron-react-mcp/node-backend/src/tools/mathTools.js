/**
 * 数学运算工具
 */
class MathTools {
  /**
   * 获取所有工具定义（OpenAI Function Calling 格式）
   */
  static getToolDefinitions() {
    return [
      {
        type: 'function',
        function: {
          name: 'add',
          description: '计算两个数的和',
          parameters: {
            type: 'object',
            properties: {
              a: { type: 'number', description: '第一个数' },
              b: { type: 'number', description: '第二个数' }
            },
            required: ['a', 'b']
          }
        }
      },
      {
        type: 'function',
        function: {
          name: 'subtract',
          description: '计算两个数的差',
          parameters: {
            type: 'object',
            properties: {
              a: { type: 'number', description: '被减数' },
              b: { type: 'number', description: '减数' }
            },
            required: ['a', 'b']
          }
        }
      },
      {
        type: 'function',
        function: {
          name: 'multiply',
          description: '计算两个数的乘积',
          parameters: {
            type: 'object',
            properties: {
              a: { type: 'number', description: '第一个数' },
              b: { type: 'number', description: '第二个数' }
            },
            required: ['a', 'b']
          }
        }
      },
      {
        type: 'function',
        function: {
          name: 'divide',
          description: '计算两个数的商',
          parameters: {
            type: 'object',
            properties: {
              a: { type: 'number', description: '被除数' },
              b: { type: 'number', description: '除数（不能为0）' }
            },
            required: ['a', 'b']
          }
        }
      },
      {
        type: 'function',
        function: {
          name: 'squareRoot',
          description: '计算平方根',
          parameters: {
            type: 'object',
            properties: {
              number: { type: 'number', description: '要计算平方根的数（必须非负）' }
            },
            required: ['number']
          }
        }
      }
    ];
  }

  /**
   * 执行工具调用
   */
  static async executeTool(toolName, args) {
    switch (toolName) {
      case 'add':
        return this.add(args.a, args.b);
      case 'subtract':
        return this.subtract(args.a, args.b);
      case 'multiply':
        return this.multiply(args.a, args.b);
      case 'divide':
        return this.divide(args.a, args.b);
      case 'squareRoot':
        return this.squareRoot(args.number);
      default:
        throw new Error(`Unknown tool: ${toolName}`);
    }
  }

  static add(a, b) {
    return `计算结果: ${a} + ${b} = ${a + b}`;
  }

  static subtract(a, b) {
    return `计算结果: ${a} - ${b} = ${a - b}`;
  }

  static multiply(a, b) {
    return `计算结果: ${a} × ${b} = ${a * b}`;
  }

  static divide(a, b) {
    if (b === 0) {
      throw new Error('除数不能为0');
    }
    return `计算结果: ${a} ÷ ${b} = ${a / b}`;
  }

  static squareRoot(number) {
    if (number < 0) {
      throw new Error('不能计算负数的平方根');
    }
    return `计算结果: √${number} = ${Math.sqrt(number)}`;
  }
}

module.exports = { MathTools };
