# LLM 提供商配置指南

## 概述

本项目支持多种 LLM 提供商，可以通过配置文件轻松切换：

- **Qwen（阿里云 DashScope）**：默认模型
- **OpenAI 协议**：支持 OpenAI 官方 API 或私有化部署的兼容服务

通过 `application.yml` 中的 `langchain4j.provider` 配置项即可切换，无需修改代码。

---

## 配置方式

### 方式一：使用 Qwen 模型（默认）

```yaml
langchain4j:
  provider: qwen  # 选择 Qwen 提供商
  max-messages: 10
  
  qwen:
    api-key: sk-your-dashscope-api-key
    model-name: qwen3-max
```

**说明：**
- `provider: qwen` - 启用 Qwen 模型
- `api-key` - 阿里云 DashScope API Key
- `model-name` - 可选值：`qwen3-max`, `qwen2-72b`, `qwen-plus` 等

**获取 API Key：**  
访问 [阿里云 DashScope](https://dashscope.aliyun.com/) 注册并获取 API Key

---

### 方式二：使用 OpenAI 官方 API

```yaml
langchain4j:
  provider: openai  # 切换到 OpenAI 提供商
  max-messages: 10
  
  openai:
    base-url: https://api.openai.com/v1
    api-key: sk-your-openai-api-key
    model-name: gpt-4o-mini
```

**说明：**
- `provider: openai` - 启用 OpenAI 模型
- `base-url` - OpenAI API 地址（官方地址）
- `api-key` - OpenAI API Key
- `model-name` - 可选值：`gpt-4o`, `gpt-4o-mini`, `gpt-3.5-turbo` 等

**获取 API Key：**  
访问 [OpenAI Platform](https://platform.openai.com/api-keys) 创建 API Key

---

### 方式三：使用私有化 OpenAI 协议服务

```yaml
langchain4j:
  provider: openai  # 使用 OpenAI 协议
  max-messages: 10
  
  openai:
    base-url: http://your-llm-gateway.company.com/v1  # 私有化网关地址
    api-key: sk-your-private-key                      # 私有化服务密钥
    model-name: qwen2-72b-instruct                    # 私有化服务中的模型名
```

**典型私有化服务示例：**

1. **vLLM 部署**
   ```yaml
   base-url: http://10.0.0.12:8081/v1
   model-name: qwen2-72b-instruct
   ```

2. **Ollama 服务**
   ```yaml
   base-url: http://localhost:11434/v1
   model-name: llama3
   ```

3. **内部 API 网关**
   ```yaml
   base-url: https://llm-gateway.internal.company.com/v1
   model-name: custom-llm-model
   ```

**注意事项：**
- `base-url` 必须包含 `/v1` 路径（OpenAI 协议标准）
- `model-name` 填写私有化服务中实际配置的模型 ID
- `api-key` 使用私有化服务提供的认证密钥

---

## 配置参数说明

### 通用配置

| 参数 | 说明 | 默认值 | 必填 |
|------|------|--------|------|
| `provider` | 模型提供商（qwen/openai） | qwen | 是 |
| `max-messages` | 消息窗口大小（上下文管理） | 10 | 是 |

### Qwen 配置（provider=qwen）

| 参数 | 说明 | 默认值 | 必填 |
|------|------|--------|------|
| `qwen.api-key` | DashScope API Key | - | 是 |
| `qwen.model-name` | Qwen 模型名称 | qwen3-max | 否 |

### OpenAI 配置（provider=openai）

| 参数 | 说明 | 默认值 | 必填 |
|------|------|--------|------|
| `openai.base-url` | OpenAI API 地址 | https://api.openai.com/v1 | 是 |
| `openai.api-key` | OpenAI API Key | - | 是 |
| `openai.model-name` | 模型名称 | gpt-4o-mini | 否 |

---

## 切换示例

### 从 Qwen 切换到私有化 OpenAI

**修改前（Qwen）：**
```yaml
langchain4j:
  provider: qwen
  max-messages: 10
  qwen:
    api-key: sk-dashscope-key-xxx
    model-name: qwen3-max
```

**修改后（私有化 OpenAI）：**
```yaml
langchain4j:
  provider: openai  # ← 只需修改这一行
  max-messages: 10
  openai:
    base-url: http://10.0.0.12:8081/v1
    api-key: sk-private-key-xxx
    model-name: qwen2-72b-instruct
```

**重启服务：**
```bash
# 重新编译并启动
cd react-mcp-demo
mvn clean package -DskipTests
java -jar target/react-mcp-demo-0.0.1-SNAPSHOT.jar
```

配置生效后，日志中会看到：
```
[INFO] Initializing ChatLanguageModel with provider: openai
[INFO] Creating OpenAiChatModel: baseUrl=http://10.0.0.12:8081/v1, model=qwen2-72b-instruct
```

---

## 常见问题

### 1. 切换提供商后报错 "Unsupported LLM provider"

**原因：** `provider` 配置值不正确

**解决：** 确保 `provider` 只能是 `qwen` 或 `openai`（小写）

```yaml
langchain4j:
  provider: openai  # ✅ 正确
  # provider: OpenAI  # ❌ 错误（大小写敏感）
```

---

### 2. OpenAI 模式下报错 "Connection refused"

**原因：** `base-url` 配置错误或私有化服务未启动

**排查步骤：**

1. 确认服务是否可访问：
   ```bash
   curl http://your-gateway.com/v1/models
   ```

2. 检查 `base-url` 格式：
   ```yaml
   base-url: http://10.0.0.12:8081/v1  # ✅ 正确（包含 /v1）
   # base-url: http://10.0.0.12:8081   # ❌ 错误（缺少 /v1）
   ```

---

### 3. 私有化服务返回 "Model not found"

**原因：** `model-name` 与私有化服务中的模型 ID 不匹配

**解决：** 查询私有化服务支持的模型列表

```bash
# 查询可用模型
curl http://your-gateway.com/v1/models \
  -H "Authorization: Bearer sk-your-key"
```

确保 `model-name` 填写返回列表中的某个模型 ID。

---

### 4. API Key 认证失败

**Qwen 模式：**
- 确认 API Key 格式：`sk-` 开头
- 验证 Key 是否有效：访问 [DashScope 控制台](https://dashscope.console.aliyun.com/)

**OpenAI 模式：**
- 官方 API：确认 Key 格式 `sk-proj-...` 或 `sk-...`
- 私有化服务：联系管理员获取正确的认证密钥

---

## 技术架构

### 依赖说明

项目使用 langchain4j 提供的多提供商支持：

```xml
<!-- Qwen 模型支持 -->
<dependency>
    <groupId>dev.langchain4j</groupId>
    <artifactId>langchain4j-dashscope</artifactId>
    <version>0.35.0</version>
</dependency>

<!-- OpenAI 协议支持 -->
<dependency>
    <groupId>dev.langchain4j</groupId>
    <artifactId>langchain4j-open-ai</artifactId>
    <version>0.35.0</version>
</dependency>
```

### 配置类结构

```java
// LangchainProperties.java - 配置属性类
@ConfigurationProperties(prefix = "langchain4j")
public class LangchainProperties {
    private String provider;           // qwen | openai
    private QwenConfig qwen;           // Qwen 配置
    private OpenAiConfig openai;       // OpenAI 配置
}

// LangchainConfig.java - Bean 配置类
@Configuration
public class LangchainConfig {
    @Bean
    public ChatLanguageModel baseChatLanguageModel() {
        // 根据 provider 自动选择创建对应的模型
        if ("openai".equals(provider)) {
            return createOpenAiChatModel();
        } else {
            return createQwenChatModel();
        }
    }
}
```

---

## 性能对比

| 提供商 | 响应速度 | 中文能力 | Token 成本 | 适用场景 |
|--------|---------|----------|-----------|---------|
| **Qwen** | ⭐⭐⭐⭐⭐ 快 | ⭐⭐⭐⭐⭐ 极强 | ⭐⭐⭐⭐⭐ 低 | 中文场景、内部应用 |
| **OpenAI** | ⭐⭐⭐⭐ 较快 | ⭐⭐⭐⭐ 强 | ⭐⭐⭐ 中等 | 英文场景、通用应用 |
| **私有化** | ⭐⭐⭐⭐⭐ 极快 | 取决于模型 | ⭐⭐⭐⭐⭐ 免费 | 离线部署、数据安全 |

---

## 最佳实践

### 开发环境

```yaml
# application-dev.yml
langchain4j:
  provider: qwen  # 使用 Qwen 快速开发
  max-messages: 5  # 降低 token 消耗
```

### 生产环境

```yaml
# application-prod.yml
langchain4j:
  provider: openai  # 使用私有化服务
  max-messages: 10
  openai:
    base-url: ${LLM_GATEWAY_URL}  # 从环境变量读取
    api-key: ${LLM_API_KEY}
    model-name: ${LLM_MODEL_NAME}
```

### 测试环境

```yaml
# application-test.yml
langchain4j:
  provider: qwen
  max-messages: 3  # 最小上下文，加速测试
```

---

## 相关文档

- [langchain4j 官方文档](https://docs.langchain4j.dev/)
- [阿里云 DashScope](https://help.aliyun.com/zh/dashscope/)
- [OpenAI API 文档](https://platform.openai.com/docs/api-reference)
- [项目架构对比文档](./ARCHITECTURE_COMPARISON.md)

---

**文档版本：** v1.0  
**最后更新：** 2024-12-15  
**维护者：** ReAct MCP Team
