/**
 * 记忆系统测试脚本
 */

async function testMemorySystem() {
  console.log('📝 测试任务记忆系统\n');

  // 测试1: 简单任务（不会被记录）
  console.log('测试1: 简单任务 - 计算 10 + 5');
  const response1 = await fetch('http://localhost:8080/react/solve?task=计算%2010%20+%205');
  const result1 = await response1.json();
  console.log('结果:', result1.result);
  console.log('');

  // 等待一下
  await new Promise(resolve => setTimeout(resolve, 2000));

  // 测试2: 复杂任务（会被记录）
  console.log('测试2: 复杂任务 - 在百度搜索 JavaScript 教程');
  const response2 = await fetch(encodeURI('http://localhost:8080/react/solve?task=打开百度搜索 JavaScript 教程'));
  const result2 = await response2.json();
  console.log('结果:', result2.result);
  console.log('');

  // 等待一下
  await new Promise(resolve => setTimeout(resolve, 2000));

  // 查看记忆统计
  console.log('📊 记忆统计信息:');
  const statsResponse = await fetch('http://localhost:8080/memory/stats');
  const stats = await statsResponse.json();
  console.log(JSON.stringify(stats, null, 2));
  console.log('');

  // 测试3: 相似任务（应该引用历史记忆）
  console.log('测试3: 相似任务 - 在百度搜索 Python 教程');
  const response3 = await fetch(encodeURI('http://localhost:8080/react/solve?task=在百度搜索 Python 教程'));
  const result3 = await response3.json();
  console.log('结果:', result3.result);
  console.log('');

  // 最终统计
  console.log('📊 最终记忆统计:');
  const finalStatsResponse = await fetch('http://localhost:8080/memory/stats');
  const finalStats = await finalStatsResponse.json();
  console.log(JSON.stringify(finalStats, null, 2));
}

// 运行测试
testMemorySystem().catch(console.error);
