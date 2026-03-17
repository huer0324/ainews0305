import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * AI 新闻获取器 - 支持多源 API 聚合
 */
public class AiNewsFetcher {

    private static final Logger logger = Logger.getLogger(AiNewsFetcher.class.getName());
    private static final Properties config = new Properties();
    private static final List<NewsApiProvider> API_PROVIDERS;
    private static final List<String> TARGET_POST_URLS;
    private static final int SCHEDULER_HOUR;
    private static final int SCHEDULER_MINUTE;
    private static final int SCHEDULER_SECOND;
    private static final int SUMMARY_MAX_LENGTH;
    private static final int NEWS_MAX_COUNT;

    static {
        try (InputStream input = AiNewsFetcher.class.getClassLoader()
                .getResourceAsStream("config.properties")) {
            if (input == null) {
                logger.severe("无法找到配置文件 config.properties");
                throw new RuntimeException("无法找到配置文件");
            }
            config.load(input);
            
            // 从配置文件加载参数
            String apiConfigStr = config.getProperty("readwise.api.config");
            API_PROVIDERS = createApiProviders(apiConfigStr);
            TARGET_POST_URLS = createTargetUrls(config.getProperty("webhook.target.url"));
            SCHEDULER_HOUR = Integer.parseInt(config.getProperty("scheduler.hour", "8"));
            SCHEDULER_MINUTE = Integer.parseInt(config.getProperty("scheduler.minute", "0"));
            SCHEDULER_SECOND = Integer.parseInt(config.getProperty("scheduler.second", "0"));
            SUMMARY_MAX_LENGTH = Integer.parseInt(config.getProperty("summary.max.length", "300"));
            NEWS_MAX_COUNT = Integer.parseInt(config.getProperty("news.max.count", "3"));
            
            logger.info("配置文件加载成功，共配置 " + API_PROVIDERS.size() + " 个 API 提供者");
            for (NewsApiProvider provider : API_PROVIDERS) {
                logger.info("  - " + provider.getName() + ": " + provider.getUrl());
            }
            logger.info("共配置 " + TARGET_POST_URLS.size() + " 个推送目标地址");
            for (String url : TARGET_POST_URLS) {
                logger.info("  - " + url);
            }
        } catch (IOException e) {
            logger.log(Level.SEVERE, "读取配置文件失败", e);
            throw new RuntimeException("读取配置文件失败", e);
        }
    }

    public static void main(String[] args) {
        logger.info("启动 Readwise News Fetcher...");
        // 启动直接发送一次
        try {
//            sendAiNews();
        } catch (Exception e) {
            logger.log(Level.SEVERE, "初始执行失败", e);
        }

        // 定时调度发送
        ScheduledExecutorService scheduler =
                Executors.newSingleThreadScheduledExecutor();

        // 添加 JVM 关闭钩子，确保资源正确释放
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            logger.info("正在关闭调度器...");
            scheduler.shutdown();
            try {
                if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                    logger.warning("强制关闭调度器");
                    scheduler.shutdownNow();
                }
            } catch (InterruptedException e) {
                logger.log(Level.SEVERE, "关闭调度器时中断", e);
                scheduler.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }));

        Runnable task = () -> {
            try {
                // 检查是否为工作日
                if (!isWorkDay()) {
                    logger.info("今天不是工作日，跳过执行");
                    return;
                }
                logger.info("开始执行定时任务...");
                sendAiNews();
                logger.info("定时任务执行完成");
            } catch (Exception e) {
                logger.log(Level.SEVERE, "执行定时任务失败", e);
            }
        };

        long initialDelay = computeInitialDelay();
        long period = TimeUnit.DAYS.toSeconds(1);

        scheduler.scheduleAtFixedRate(task,
                initialDelay,
                period,
                TimeUnit.SECONDS);
        
        logger.info("定时任务已启动，下次执行时间：" + LocalDateTime.now().plusSeconds(initialDelay));
    }

    private static long computeInitialDelay() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime nextRun =
                now.withHour(SCHEDULER_HOUR).withMinute(SCHEDULER_MINUTE).withSecond(SCHEDULER_SECOND);

        if (now.compareTo(nextRun) >= 0) {
            nextRun = nextRun.plusDays(1);
        }

        long delay = Duration.between(now, nextRun).getSeconds();
        logger.info("距离下次执行还有：" + delay + "秒");
        return delay;
    }

    private static boolean isWorkDay() {
        try {
            String dateStr = java.time.LocalDate.now().toString(); // 格式：yyyy-MM-dd
            HttpClient client = HttpClient.newHttpClient();
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("https://publicapi.xiaoai.me/holiday/day?" + dateStr))
                    .GET()
                    .build();
            
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            
            if (response.statusCode() == 200) {
                ObjectMapper mapper = new ObjectMapper();
                JsonNode root = mapper.readTree(response.body());

                // 是否休息，0 为不休息，1 为休息
                int rest = root.get("data").get(0).get("rest").asInt();
                boolean isWorkDay = rest == 0;
                logger.info("休息日判断结果：" + (isWorkDay ? "工作日" : "休息日"));
                return isWorkDay;
            }
        } catch (Exception e) {
            logger.warning("查询节假日 API 失败，降级为普通周末判断：" + e.getMessage());
            // 降级处理：如果是周末则返回 false
            DayOfWeek today = LocalDateTime.now().getDayOfWeek();
            return today != DayOfWeek.SATURDAY && today != DayOfWeek.SUNDAY;
        }
        
        // 默认返回 true（工作日）
        return true;
    }

    /**
     * 创建 API 提供者列表
     */
    private static List<NewsApiProvider> createApiProviders(String configStr) {
        List<NewsApiProvider> providers = new ArrayList<>();
        
        if (configStr == null || configStr.trim().isEmpty()) {
            logger.warning("未配置 API Providers，使用默认值");
            providers.add(new HttpNewsApiProvider("Readwise", "https://readwise.io/api/v3/list/", ""));
            return providers;
        }
        
        // 格式：name1|url1|token1,name2|url2|token2
        String[] parts = configStr.split(",");
        for (String part : parts) {
            String trimmedPart = part.trim();
            if (!trimmedPart.isEmpty()) {
                String[] items = trimmedPart.split("\\|");
                if (items.length >= 3) {
                    // name|url|token
                    providers.add(new HttpNewsApiProvider(items[0].trim(), items[1].trim(), items[2].trim()));
                } else if (items.length == 2) {
                    // name|url
                    String name = items[0].trim();
                    String url = items[1].trim();
                    providers.add(new HttpNewsApiProvider(name, url, ""));
                } else if (items.length == 1) {
                    // 只配置了 URL
                    String url = items[0].trim();
                    String name = extractApiName(url);
                    providers.add(new HttpNewsApiProvider(name, url, ""));
                }
            }
        }
        
        if (providers.isEmpty()) {
            providers.add(new HttpNewsApiProvider("Readwise", "https://readwise.io/api/v3/list/", ""));
        }
        
        return providers;
    }
    
    /**
     * 创建目标推送地址列表
     */
    private static List<String> createTargetUrls(String configStr) {
        List<String> urls = new ArrayList<>();
        
        if (configStr == null || configStr.trim().isEmpty()) {
            logger.warning("未配置推送目标地址");
            return urls;
        }
        
        // 支持逗号或分号分隔多个 URL
        String[] parts = configStr.split("[,;]");
        for (String part : parts) {
            String trimmedPart = part.trim();
            if (!trimmedPart.isEmpty()) {
                urls.add(trimmedPart);
            }
        }
        
        if (urls.isEmpty()) {
            logger.warning("有效的推送目标地址为空");
        } else {
            logger.info("成功加载 " + urls.size() + " 个推送目标地址");
        }
        
        return urls;
    }
    
    /**
     * 从 URL 提取 API 名称
     */
    private static String extractApiName(String url) {
        try {
            java.net.URI uri = new java.net.URI(url);
            String host = uri.getHost();
            if (host != null) {
                // 移除 www.前缀和.com/.io 等后缀
                return host.replaceFirst("^www\\.", "").split("\\.")[0].toUpperCase();
            }
        } catch (Exception e) {
            // 忽略异常，返回默认名称
        }
        return "API-" + System.currentTimeMillis();
    }

    private static void sendAiNews() throws Exception {
        logger.info("开始获取 AI 新闻资讯...");

        HttpClient client = HttpClient.newHttpClient();
        ObjectMapper mapper = new ObjectMapper();
        List<JsonNode> allResults = new ArrayList<>();

        // 从多个 API 提供者获取数据
        for (NewsApiProvider provider : API_PROVIDERS) {
            try {
                List<JsonNode> results = provider.fetchNews(client, mapper, NEWS_MAX_COUNT);
                allResults.addAll(results);
            } catch (Exception e) {
                logger.severe(provider.getName() + " 获取数据失败：" + e.getMessage());
                // 继续处理其他 API
            }
        }

        if (allResults.isEmpty()) {
            logger.warning("所有 API 都未获取到有效数据");
            return;
        }

        logger.info("总共获取到 " + allResults.size() + " 条原始数据");

        // 去重（根据 title + source_url 判断）
        List<JsonNode> uniqueResults = deduplicateNews(allResults);
        logger.info("去重后剩余 " + uniqueResults.size() + " 条数据");

        // 按 published_date 倒序排序（最新的在前）
        uniqueResults.sort((o1, o2) -> {
            String date1 = getText(o1, "published_date");
            String date2 = getText(o2, "published_date");
            // 倒序排序：日期越新越靠前
            return date2.compareTo(date1);
        });

        StringBuilder stringBuilder = new StringBuilder("AI 新闻资讯：\n");
        int count = 0;
        for (JsonNode item : uniqueResults) {
            if (count >= NEWS_MAX_COUNT) {
                break;
            }

            String title = getText(item, "title");
            String url = getText(item, "source_url");
            String content = getText(item, "summary");
            
            // 根据 URL 来源选择不同的摘要生成方式
            String summary;
            if (url.contains("bestblogs")) {
                // 对于 bestblogs 来源，提取一句话摘要
                summary = extractOneSentenceSummary(content);
            } else {
                // 其他来源使用普通摘要生成
                summary = generateSummary(content);
            }

            if (title.isEmpty() || url.isEmpty()) {
                logger.warning("跳过无效数据：" + title);
                continue;
            }

            count++;
            stringBuilder.append(count).append(".");
            stringBuilder.append("标题：").append(title).append("\n");
            if (!"无内容".equals(summary) && !summary.isEmpty()) {
                stringBuilder.append("摘要：").append(summary).append("\n");
            }
            stringBuilder.append("链接：").append(url).append("\n");
            if (count < NEWS_MAX_COUNT) {
                stringBuilder.append("\n");
            }
        }

        if (count == 0) {
            logger.warning("没有有效的新闻数据可发送");
            return;
        }

        logger.info("生成新闻内容：" + stringBuilder);

        ObjectNode postData = mapper.createObjectNode();
        postData.put("content", stringBuilder.toString());
        postToAllTargets(client, mapper.writeValueAsString(postData));
        logger.info("新闻推送完成");
    }

    /**
     * 推送至所有目标地址
     */
    private static void postToAllTargets(HttpClient client, String json) throws Exception {
        if (TARGET_POST_URLS.isEmpty()) {
            logger.warning("没有配置推送目标地址");
            return;
        }
        
        logger.info("开始推送到 " + TARGET_POST_URLS.size() + " 个目标地址...");
        
        int successCount = 0;
        int failCount = 0;
        
        for (String url : TARGET_POST_URLS) {
            try {
                logger.info("正在推送到：" + url);
                
                HttpRequest postRequest = HttpRequest.newBuilder()
                        .uri(URI.create(url))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(json))
                        .build();
            
                HttpResponse<String> postResponse =
                        client.send(postRequest, HttpResponse.BodyHandlers.ofString());
            
                logger.info("推送到 " + url + " 的状态码：" + postResponse.statusCode());
                    
                if (postResponse.statusCode() != 200) {
                    logger.warning("推送到 " + url + " 失败，响应结果：" + postResponse.body());
                    failCount++;
                } else {
                    logger.info("推送到 " + url + " 成功");
                    successCount++;
                }
            } catch (Exception e) {
                logger.severe("推送到 " + url + " 时发生异常：" + e.getMessage());
                failCount++;
            }
        }
        
        logger.info("推送完成统计：成功 " + successCount + "/" + TARGET_POST_URLS.size() + 
                   "，失败 " + failCount + "/" + TARGET_POST_URLS.size());
        
        if (failCount > 0) {
            throw new RuntimeException("部分推送目标失败，失败数：" + failCount);
        }
    }

    private static List<JsonNode> deduplicateNews(List<JsonNode> allResults) {
        List<JsonNode> uniqueResults = new ArrayList<>();
        java.util.Set<String> seenKeys = new java.util.HashSet<>();
        
        for (JsonNode node : allResults) {
            String title = getText(node, "title");
            String url = getText(node, "source_url");
            
            // 使用 title + source_url 作为唯一键
            String uniqueKey = title + "|||" + url;
            
            if (!seenKeys.contains(uniqueKey)) {
                seenKeys.add(uniqueKey);
                uniqueResults.add(node);
            }
        }
        
        return uniqueResults;
    }

    private static String getText(JsonNode node, String field) {
        if (node == null) {
            return "";
        }
        return node.has(field) && !node.get(field).isNull()
                ? node.get(field).asText()
                : "";
    }

    private static String generateSummary(String text) {
        if (text == null || text.isEmpty()) {
            return "无内容";
        }
        // 去除空行（多个连续换行），但保留单个换行符
        String cleanedText = text.replaceAll("\r?\n[ \t]*\r?\n", "\n").trim();
        return cleanedText.length() <= SUMMARY_MAX_LENGTH
                ? cleanedText
                : cleanedText.substring(0, SUMMARY_MAX_LENGTH) + "...";
    }

    /**
     * 从文本中提取一句话摘要
     * @param text 输入文本
     * @return 提取的摘要内容
     */
    private static String extractOneSentenceSummary(String text) {
        if (text == null || text.isEmpty()) {
            return "";
        }
        
        try {
            // 匹配"一句话摘要"后面的内容（直到遇到"详细摘要"或文本结束）
            // 使用 [\s\S] 匹配包括换行符在内的所有字符
            java.util.regex.Pattern pattern = java.util.regex.Pattern.compile(
                "一句话摘要\\s*([\\s\\S]*?)(?:详细摘要|$)",
                java.util.regex.Pattern.MULTILINE
            );
            java.util.regex.Matcher matcher = pattern.matcher(text);
            
            if (matcher.find()) {
                // 提取内容并去除首尾空白（包括换行符）
                String summary = matcher.group(1).trim();
                logger.info("提取到一句话摘要：" + summary);
                return summary;
            }
        } catch (Exception e) {
            logger.warning("正则表达式提取失败：" + e.getMessage());
        }
        return text;
    }
}