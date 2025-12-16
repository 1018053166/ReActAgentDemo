/**
 * ReAct 步骤事件模型
 */
class ReActStepEvent {
  constructor(type, stepNumber, content, timestamp) {
    this.type = type;
    this.stepNumber = stepNumber;
    this.content = content;
    this.timestamp = timestamp || new Date().toISOString();
  }

  static thought(stepNumber, content) {
    return new ReActStepEvent('thought', stepNumber, content);
  }

  static action(stepNumber, content) {
    return new ReActStepEvent('action', stepNumber, content);
  }

  static observation(stepNumber, content) {
    return new ReActStepEvent('observation', stepNumber, content);
  }

  static finalAnswer(content) {
    return new ReActStepEvent('final_answer', null, content);
  }

  static error(content) {
    return new ReActStepEvent('error', null, content);
  }
}

module.exports = { ReActStepEvent };
