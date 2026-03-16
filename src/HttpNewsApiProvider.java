import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * 通用 HTTP API 实现类
 */
public class HttpNewsApiProvider implements NewsApiProvider {
    private final String name;
    private final String url;
    private final String token;
    private static final Logger logger = Logger.getLogger(HttpNewsApiProvider.class.getName());
    
    public HttpNewsApiProvider(String name, String url, String token) {
        this.name = name;
        this.url = url;
        this.token = token;
    }
    
    @Override
    public String getName() {
        return name;
    }
    
    @Override
    public String getUrl() {
        return url;
    }
    
    @Override
    public String getToken() {
        return token;
    }
    
    @Override
    public List<JsonNode> fetchNews(HttpClient client, ObjectMapper mapper, int newsMaxCount) throws Exception {
        List<JsonNode> results = new ArrayList<>();
        
        logger.info("正在从 " + name + " 获取数据：" + url);

        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(url));
        if (token != null && !"".equals(token)) {
            builder.header("Authorization", "Token " + token);
        }
        HttpRequest request = builder.GET().build();

        HttpResponse<String> response =
                client.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            logger.warning(name + " API 请求失败，状态码：" + response.statusCode());
            return results;
        }

        JsonNode root = mapper.readTree(response.body());
        JsonNode resultsNode = root.get("results");

        if (resultsNode != null && resultsNode.size() > 0) {
            // 只获取前 NEWS_MAX_COUNT 条数据
            int countToFetch = Math.min(resultsNode.size(), newsMaxCount);
            for (int i = 0; i < countToFetch; i++) {
                results.add(resultsNode.get(i));
            }
            logger.info("从 " + name + " 获取到 " + countToFetch + " 条数据（共 " + resultsNode.size() + " 条）");
        } else {
            logger.warning(name + " API 返回空数据");
        }
        
        return results;
    }
}
