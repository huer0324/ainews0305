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

```
properties
# Readwise API 配置
readwise.api.url=https://readwise.io/api/v3/list/
readwise.api.token=你的 Readwise API Token

# 目标推送地址
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

## 启动模式

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

## 节假日判断机制

程序会调用 [timor-api](https://timor-api.com/) 接口判断当天是否为工作日：

- **type = 0**：节假日 → 跳过执行
- **type = 1**：调休日（周末上班）→ 正常执行
- **type = 2**：普通工作日 → 正常执行

**降级策略**：如果 API 查询失败，自动降级为普通周末判断（周六日休息）

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

### 查看运行状态

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

## 注意事项

1. **配置文件安全**：`config.properties` 包含敏感信息，请勿提交到 Git 仓库
2. **节假日 API**：依赖第三方 API，网络异常时会自动降级为普通周末判断
3. **时区设置**：确保服务器时区正确，否则定时任务可能不准确
4. **日志管理**：定期清理 `output.log` 文件，避免占用过多磁盘空间


