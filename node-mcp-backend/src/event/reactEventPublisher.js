/**
 * ReAct 事件发布订阅器
 */
export class ReActEventPublisher {
  constructor() {
    this.listeners = [];
  }

  /**
   * 注册事件监听器
   */
  registerListener(listener) {
    this.listeners.push(listener);
  }

  /**
   * 移除事件监听器
   */
  unregisterListener(listener) {
    const index = this.listeners.indexOf(listener);
    if (index > -1) {
      this.listeners.splice(index, 1);
    }
  }

  /**
   * 清除所有监听器
   */
  clearListeners() {
    this.listeners = [];
  }

  /**
   * 发布事件到所有监听器
   */
  publish(event) {
    this.listeners.forEach(listener => {
      try {
        listener(event);
      } catch (error) {
        console.error('Event listener error:', error);
      }
    });
  }
}

// 全局事件发布器单例
export const globalEventPublisher = new ReActEventPublisher();
