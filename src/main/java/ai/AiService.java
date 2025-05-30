package ai;

import io.github.cdimascio.dotenv.Dotenv;
import lombok.extern.slf4j.Slf4j;
import org.json.JSONArray;
import org.json.JSONObject;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.*;

/**
 * @author loks666
 * 项目链接: <a href="https://github.com/loks666/get_jobs">https://github.com/loks666/get_jobs</a>
 */
@Slf4j
public class AiService {

    private static final Dotenv dotenv = Dotenv.load();
    private static final String BASE_URL = dotenv.get("BASE_URL") + "/v1/chat/completions";
    private static final String API_KEY = dotenv.get("API_KEY");
    private static final String MODEL = dotenv.get("MODEL");


    public static String sendRequest(String content) {
        // 设置超时时间，单位：秒
        int timeoutInSeconds = 60;  // 你可以修改这个变量来设置超时时间

        // 创建 HttpClient 实例并设置超时
        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(timeoutInSeconds))  // 设置连接超时
                .build();

        // 构建 JSON 请求体
        JSONObject requestData = new JSONObject();
        requestData.put("model", MODEL);
        requestData.put("temperature", 0.5);

        // 添加消息内容
        JSONArray messages = new JSONArray();
        JSONObject message = new JSONObject();
        message.put("role", "user");
        message.put("content", content);
        messages.put(message);

        requestData.put("messages", messages);

        // 构建 HTTP 请求
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + API_KEY)
                .POST(HttpRequest.BodyPublishers.ofString(requestData.toString()))
                .build();

        // 创建线程池用于执行请求
        ExecutorService executor = Executors.newSingleThreadExecutor();
        Callable<HttpResponse<String>> task = () -> client.send(request, HttpResponse.BodyHandlers.ofString());

        // 提交请求并控制超时
        Future<HttpResponse<String>> future = executor.submit(task);
        try {
            // 使用 future.get 设置超时
            HttpResponse<String> response = future.get(timeoutInSeconds, TimeUnit.SECONDS);

            if (response.statusCode() == 200) {
                // 解析响应体
                log.info(response.body());
                JSONObject responseObject = new JSONObject(response.body());
                String requestId = responseObject.getString("id");
                long created = responseObject.getLong("created");
                String model = responseObject.getString("model");

                // 解析返回的内容
                JSONObject messageObject = responseObject.getJSONArray("choices")
                        .getJSONObject(0)
                        .getJSONObject("message");
                String responseContent = messageObject.getString("content");

                // 解析 usage 部分
                JSONObject usageObject = responseObject.getJSONObject("usage");
                int promptTokens = usageObject.getInt("prompt_tokens");
                int completionTokens = usageObject.getInt("completion_tokens");
                int totalTokens = usageObject.getInt("total_tokens");

                // 格式化时间
                LocalDateTime createdTime = Instant.ofEpochSecond(created)
                        .atZone(ZoneId.systemDefault())
                        .toLocalDateTime();
                DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
                String formattedTime = createdTime.format(formatter);

                log.info("请求ID: {}, 创建时间: {}, 模型名: {}, 提示词: {}, 补全: {}, 总用量: {}", requestId, formattedTime, model, promptTokens, completionTokens, totalTokens);
                return responseContent;
            } else {
                log.error("AI请求失败！状态码: {}", response.statusCode());
            }
        } catch (TimeoutException e) {
            log.error("请求超时！超时设置为 {} 秒", timeoutInSeconds);
        } catch (Exception e) {
            log.error("AI请求异常！", e);
        } finally {
            executor.shutdownNow();  // 关闭线程池
        }
        return "";
    }

}
