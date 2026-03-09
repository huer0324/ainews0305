# Readwise News Fetcher

一个基于 Java 的定时任务工具，用于从 Readwise API 获取 AI 新闻资讯，并推送到指定的 Webhook 地址。支持工作日和节假日智能判断，仅在工作日发送消息。

## 功能特性

- ✅ 定时获取 Readwise AI 新闻资讯
- ✅ 自动推送至企业微信/云之翼等 Webhook 平台
- ✅ **智能节假日判断**：自动识别法定节假日、调休日（调用 API）
- ✅ **仅工作日发送**：周末和节假日自动跳过，调休日正常发送
- ✅ 支持自定义定时任务执行时间
- ✅ **双模式运行**：支持测试模式和正式模式
- ✅ 智能摘要生成（可配置最大长度）
- ✅ 可配置新闻获取数量
- ✅ 完善的日志记录
- ✅ JVM 优雅关闭处理
- ✅ API 降级容错：节假日查询失败时自动降级为普通周末判断

## 项目结构

```
ainews0305/
├── src/
│   ├── ReadwiseNewsFetcher.java    # 主程序
│   └── config.properties           # 配置文件
├── ainews0305.iml
└── README.md
```
## 环境要求

- Java 11 或更高版本（使用 HttpClient API）
- Jackson 库（用于 JSON 处理）
- Readwise API Token
- Webhook 接收地址

## 配置说明

编辑 `src/config.properties` 文件：

```properties
# Readwise API 配置
readwise.api.url=https://readwise.io/api/v3/list/
readwise.api.token=你的 Readwise API Token

# 目标推送地址（企业微信/云之翼等 Webhook）
webhook.target.url=你的 Webhook 接收地址

# 定时任务配置 (每天 8:00:00 执行)
scheduler.hour=8
scheduler.minute=0
scheduler.second=0

# 内容配置
summary.max.length=300        # 摘要最大长度
news.max.count=3              # 每次获取的新闻数量
```
## 工作流程

1. **启动时立即执行一次**（仅测试模式）：程序启动后会立即获取并推送一次新闻
2. **定时调度**：根据配置的时间每天定时执行
3. **节假日判断**：查询 API 判断是否为工作日（考虑调休）
4. **智能跳过**：如果是周末或节假日，自动跳过不发送
5. **获取新闻**：调用 Readwise API 获取最新资讯
6. **数据处理**：提取标题、链接和摘要
7. **推送消息**：将格式化后的新闻推送到 Webhook 地址

## 快速开始

### 1. 编译打包

```bash
# 在项目根目录下执行
javac -d out src/ReadwiseNewsFetcher.java
jar cfm ainews0305.jar MANIFEST.MF -C out .
```

### 2. 配置参数

编辑 `src/config.properties` 文件，填入你的 API Token 和 Webhook 地址

### 3. 启动程序

**测试模式**（验证功能）：
```bash
java -jar ainews0305.jar 0
```

**正式运行**（后台服务）：
```bash
nohup java -jar ainews0305.jar 1 > output.log 2>&1 &
```

---

## 启动模式详解

### 测试模式（参数 0）
```bash
java -jar ainews0305.jar 0
```
- 启动后立即执行一次任务
- 输出到控制台，不实际推送
- 用于验证配置和功能

### 正式模式（参数 1）
```bash
nohup java -jar ainews0305.jar 1 > output.log 2>&1 &
```
- 后台运行，不立即执行
- 按照定时任务配置运行
- 仅在工作日发送消息
- 日志输出到 `output.log` 文件

---

## 节假日判断机制详解

### API 接口说明

程序调用 [timor-api](https://timor-api.com/) 提供的免费节假日查询接口：

| 返回类型 | 说明 | 程序行为 |
|---------|------|---------|
| type = 0 | 法定节假日 | ❌ 跳过不发送 |
| type = 1 | 调休日（周末上班） | ✅ 正常发送 |
| type = 2 | 普通工作日 | ✅ 正常发送 |
| type = -1 | 普通周末 | ❌ 跳过不发送 |

### 降级策略

当遇到以下情况时，自动降级为本地周末判断：
- 网络异常无法访问 API
- API 服务不可用
- 响应数据格式错误

**降级逻辑**：仅判断是否为周六或周日，不考虑调休安排

### 示例日志输出

```
[INFO] 节假日判断结果：工作日 (劳动节调休)
[INFO] 节假日判断结果：节假日 (国庆节)
[WARNING] 查询节假日 API 失败，降级为普通周末判断
```

## 启用与关闭

### 启动方式

#### Windows 系统

**测试模式**（立即执行一次，输出到控制台）：
```cmd
java -jar ainews0305.jar 0
```

**正式模式**（后台运行，日志输出到文件）：
```cmd
nohup java -jar ainews0305.jar 1 > output.log 2>&1 &
```

或者使用 PowerShell 后台运行：
```powershell
Start-Process javaw -ArgumentList "-jar","ainews0305.jar","1" -WindowStyle Hidden
```

#### Linux/Mac 系统

**测试模式**：
```bash
java -jar ainews0305.jar 0
```

**正式模式**（后台运行）：
```bash
nohup java -jar ainews0305.jar 1 > output.log 2>&1 &
```

---

## 运维管理

### 查看进程状态

```bash
# 查看 Java 进程
jps -l

# 或者使用 ps 命令
ps aux | grep ainews0305
```

### 关闭程序

```bash
# Windows
taskkill /PID <进程 ID> /F

# Linux/Mac
kill -9 <进程 ID>
```

### 查看日志

```bash
# 实时查看日志输出
tail -f output.log
```

---

## 常见问题 FAQ

### Q1: 如何确认程序是否正常运行？
查看日志文件 `output.log`，应该能看到类似输出：
```
[INFO] 启动 Readwise News Fetcher... [正式模式]
[INFO] 节假日判断结果：工作日
[INFO] 距离下次执行还有：XXXX 秒
[INFO] 定时任务已启动
```

### Q2: 为什么周末没有收到消息？
程序已启用智能节假日判断，周末和节假日会自动跳过。如需在周末测试，请使用测试模式（参数 0）。

### Q3: 如何修改定时任务执行时间？
编辑 `config.properties`，调整以下参数：
```properties
scheduler.hour=9      # 改为 9 点
scheduler.minute=30   # 改为 30 分
```

### Q4: 配置文件包含敏感信息怎么办？
确保 `config.properties` 已添加到 `.gitignore`，避免提交到代码仓库。

---

## 注意事项与最佳实践

### 安全与配置

1. **配置文件安全**：`config.properties` 包含敏感信息，必须添加到 `.gitignore`
2. **时区设置**：确保服务器时区正确（推荐使用 UTC+8 北京时间）

### 运维与维护

3. **日志管理**：定期清理或归档 `output.log`，建议配置日志轮转
4. **监控告警**：可在服务器上配置监控脚本，检测进程存活状态

### API 依赖

5. **节假日 API**：依赖第三方服务，极端情况下可能影响判断准确性
6. **Readwise API**：需确保 API Token 有效，注意调用频率限制

### 性能优化

7. **资源占用**：程序内存占用约 50-100MB，适合长期运行
8. **优雅关闭**：建议使用 `kill` 而非 `kill -9`，确保数据完整性


