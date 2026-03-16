import com.fasterxml.jackson.databind.JsonNode;
import java.net.http.HttpClient;
import java.util.List;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * API 新闻获取器接口
 */
public interface NewsApiProvider {
    /**
     * 获取 API 名称
     */
    String getName();
    
    /**
     * 获取 API URL
     */
    String getUrl();
    
    /**
     * 获取认证 Token
     */
    String getToken();
    
    /**
     * 获取新闻数据
     */
    List<JsonNode> fetchNews(HttpClient client, ObjectMapper mapper, int newsMaxCount) throws Exception;
}
