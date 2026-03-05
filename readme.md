# Readwise News Fetcher

一个基于 Java 的定时任务工具，用于从 Readwise API 获取 AI 新闻资讯，并推送到指定的 Webhook 地址。

## 功能特性

- ✅ 定时获取 Readwise AI 新闻资讯
- ✅ 自动推送至企业微信/云之翼等 Webhook 平台
- ✅ 支持自定义定时任务执行时间
- ✅ 智能摘要生成（可配置最大长度）
- ✅ 可配置新闻获取数量
- ✅ 完善的日志记录
- ✅ JVM 优雅关闭处理

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

1. **启动时立即执行一次**：程序启动后会立即获取并推送一次新闻
2. **定时调度**：根据配置的时间每天定时执行
3. **获取新闻**：调用 Readwise API 获取最新资讯
4. **数据处理**：提取标题、链接和摘要
5. **推送消息**：将格式化后的新闻推送到 Webhook 地址

## 启用与关闭
启用：在项目根目录下运行 `javaw -jar ainews0305.jar` 命令启动程序。
关闭：运行 `jps -l` 查看进行的 Java 进程ID，然后运行 `taskkill /PID <你的进程ID> /F` 关闭程序。


