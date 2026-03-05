import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Properties;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

public class ReadwiseNewsFetcher {

    private static final Logger logger = Logger.getLogger(ReadwiseNewsFetcher.class.getName());
    private static final Properties config = new Properties();
    private static final String API_URL;
    private static final String TOKEN;
    private static final String TARGET_POST_URL;
    private static final int SCHEDULER_HOUR;
    private static final int SCHEDULER_MINUTE;
    private static final int SCHEDULER_SECOND;
    private static final int SUMMARY_MAX_LENGTH;
    private static final int NEWS_MAX_COUNT;

    static {
        try (InputStream input = ReadwiseNewsFetcher.class.getClassLoader()
                .getResourceAsStream("config.properties")) {
            if (input == null) {
                logger.severe("无法找到配置文件 config.properties");
                throw new RuntimeException("无法找到配置文件");
            }
            config.load(input);
            
            // 从配置文件加载参数
            API_URL = config.getProperty("readwise.api.url");
            TOKEN = config.getProperty("readwise.api.token");
            TARGET_POST_URL = config.getProperty("webhook.target.url");
            SCHEDULER_HOUR = Integer.parseInt(config.getProperty("scheduler.hour", "8"));
            SCHEDULER_MINUTE = Integer.parseInt(config.getProperty("scheduler.minute", "0"));
            SCHEDULER_SECOND = Integer.parseInt(config.getProperty("scheduler.second", "0"));
            SUMMARY_MAX_LENGTH = Integer.parseInt(config.getProperty("summary.max.length", "300"));
            NEWS_MAX_COUNT = Integer.parseInt(config.getProperty("news.max.count", "3"));
            
            logger.info("配置文件加载成功");
        } catch (IOException e) {
            logger.log(Level.SEVERE, "读取配置文件失败", e);
            throw new RuntimeException("读取配置文件失败", e);
        }
    }

    public static void main(String[] args) {
        logger.info("启动 Readwise News Fetcher...");

        // 启动直接发送一次
        try {
            sendAiNews();
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

    private static void sendAiNews() throws Exception {
        logger.info("开始获取 AI 新闻资讯...");
    
        HttpClient client = HttpClient.newHttpClient();
        ObjectMapper mapper = new ObjectMapper();
    
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(API_URL))
                .header("Authorization", "Token " + TOKEN)
                .GET()
                .build();
    
        HttpResponse<String> response =
                client.send(request, HttpResponse.BodyHandlers.ofString());
    
        if (response.statusCode() != 200) {
            throw new RuntimeException("API 请求失败，状态码：" + response.statusCode());
        }
    
        JsonNode root = mapper.readTree(response.body());
        JsonNode results = root.get("results");
    
        if (results == null || results.size() == 0) {
            logger.warning("未获取到任何新闻数据");
            return;
        }
    
        StringBuilder stringBuilder = new StringBuilder("AI 新闻资讯：\n");
        int count = 0;
        for (int i = 1; i < results.size() && count < NEWS_MAX_COUNT; i++) {
            JsonNode item = results.get(i);
    
            String title = getText(item, "title");
            String url = getText(item, "source_url");
            String content = getText(item, "summary");
            String summary = generateSummary(content);
    
            if (title.isEmpty() || url.isEmpty()) {
                logger.warning("跳过无效数据：第" + i + "条");
                continue;
            }
    
            count++;
            stringBuilder.append(count).append(".");
            stringBuilder.append("标题：").append(title).append("\n");
            if (!"无内容".equals(summary) && !summary.isEmpty()) {
                stringBuilder.append("摘要：").append(summary).append("\n");
            }
            stringBuilder.append("链接：").append(url).append("\n").append("\n");
        }
    
        if (count == 0) {
            logger.warning("没有有效的新闻数据可发送");
            return;
        }
    
        logger.info("生成新闻内容：" + stringBuilder.toString());
            
        ObjectNode postData = mapper.createObjectNode();
        postData.put("content", stringBuilder.toString());
        postToTarget(client, mapper.writeValueAsString(postData));
            
        logger.info("新闻推送完成");
    }

    private static void postToTarget(HttpClient client, String json) throws Exception {
        logger.info("开始推送到目标地址...");
    
        HttpRequest postRequest = HttpRequest.newBuilder()
                .uri(URI.create(TARGET_POST_URL))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .build();
    
        HttpResponse<String> postResponse =
                client.send(postRequest, HttpResponse.BodyHandlers.ofString());
    
        logger.info("POST 状态码：" + postResponse.statusCode());
            
        if (postResponse.statusCode() != 200) {
            logger.warning("推送失败，响应结果：" + postResponse.body());
            throw new RuntimeException("推送失败，状态码：" + postResponse.statusCode());
        }
            
        logger.info("推送成功，响应结果：" + postResponse.body());
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
        return text.length() <= SUMMARY_MAX_LENGTH
                ? text
                : text.substring(0, SUMMARY_MAX_LENGTH) + "...";
    }
}