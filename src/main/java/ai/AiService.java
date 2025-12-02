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


    public static void main(String[] args) {
        System.out.println(cleanBossDesc("""
                .EwyXFHpFfseN{display:inline-block;width:0.1px;height:0.1px;overflow:hidden;visibility: hidden;}.FxpRjMznwNS{display:inline-block;font-size:0!important;width:1em;height:1em;visibility:hidden;line-height:0;}.QTsRdnap{display:inline-block;font-size:0!important;width:1em;height:1em;visibility:hidden;line-height:0;}.spBzTCGii{display:inline-block;font-size:0!important;width:1em;height:1em;visibility:hidden;line-height:0;}.DXpfskbRdfn{display:inline-block;width:0.1px;height:0.1px;overflow:hidden;visibility: hidden;}.snNcSPFFs{font-style:normal;font-weight:normal}.zjziXGAdnjK{font-style:normal;font-weight:normal}.CjmzfkfTmx{font-style:normal;font-weight:normal}.YYTWRZHhrm{font-style:normal;font-weight:normal}.cfAzXEKs{font-style:normal;font-weight:normal}岗位职责：
                kanzhun一、boss产品+AI的实BOSS直聘施与落地能力
                1、负责公司产品+AI的实施与落地，熟悉各基础大模型性能，熟练应用大模型关键技术，面向隧道股份各需求，协同规划产品落地路径，具备实施能力；
                2、负责聚焦场景和产品的大模型相关的训练工作，包括：需求分析、功能设计、数据集构建、模型训练、评估及优化等；
                3、熟悉包括RAG、指令数据构建、Prompt工程、模型Fine-tuning、Prompt Engineering等环节，实现大模型技术在领域内垂直场景的落地应用；
                4、熟悉NLP、CV、多模态等领域大模型结构、算法，具备追踪大模型领域内前沿的技术研究成果，包括但不局限预训练、强化学习、知识增强、分布式训练等，同时提出创新思路来推动升级的能力；
                5、具备优化计算、存储和网络性能的能力，以满足业务系统的资源需求，并设定具体的性能优化目标，如延迟、吞吐量、资源利用率等；在满足性能需求的前提下，优化计算、存储和网络资源的使用，降低总成本；
                6、对业务系统的效果进行持续调优，通过数据分析和系统改进，提升系统的性能和用户体验。
                二、项目管理与协作
                1、参与制定核心业务项目计划和需求分析，确保项目按时交付和达到高质量标准。
                带领项目成员进行端对端开发，制定项目计划、分配任务并指导项目成员完成开发工作。
                2、与跨部门团队紧密合作，包括开发人员、测试人员、产品经理等，共同推动项目的顺利进行。
                四、技术能力提升
                1、负责相关技术文档的撰写与整理。
                2、协同团队成员进行技术分享，促进***学习于经验交流。
                3、建立公司知识库，沉淀技术文档、***实践、案例分享等，方便企业内部日常学习与参考。
                
                任职资格：
                一、教育背景
                本科及以上学历，电子工程、计算机科学、人工智能等相关领域专业。
                二、工作经验
                1、具备3年以上AI相关开发经验或5年以上软件开发经验（优秀者可适当放宽工作年限要求）。
                2、具备Rerank、Embedding、Langchain、RAG等服务开发及部署经验者优先。
                具备大模型应用开发经验，在智能问答、代码review、代码续写、测试用例生成等方向有成功经验者优先。
                4、有大型互联网公司大规模机器学习平台相关研发落地经验者优先。
                三、专业知识
                1、熟悉主流大模型如GPT、Gemini、LLaMA、ChatGLM等及其原理，并能进行针对性模型开发工作；
                2、了解深度学习等技术，熟悉大模型训练、推理、量化和部署者优先；
                3、了解主流AI应用框架者（如TensorFlow、PyTorch、longchain等）优先；
                4、熟悉JAVA/C++/Go/Python任一语言，有完整的项目开发经验，具备核心模块设计和维护经验。有一定的算法工程化能力，能够实现算法/模型的工程化与应用部署；具有NLP相关技术经验者更佳；
                熟悉Agent框架，有优化能力，包括planing、action、tools use、memory等核心Agent能力的提升者优先；、了解深度学习等技术，熟悉大模型训练、推理、量化和部署者优先；
                四、能力要求
                1、具备良好的问题解决能力和逻辑思维，能独立分析并解决技术难题。
                2、具备良好的团队合作精神和沟通能力，能在跨部门协作中发挥积极作用。
                3、具备良好的学习能力和适应能力，能够快速掌握新技术和新知识。
                4、具备较强的抗压能力，能够适应一定频率的出差与加班，满足工作中的紧急任务需求。"""));
        try {
            // 示例：发送请求
            String content = "你好";
            String response = sendRequest(content);
            System.out.println("AI回复: " + response);
        } catch (Exception e) {
            log.error("AI异常！");
        }
    }

    public static String cleanBossDesc(String raw) {
        return raw.replaceAll("kanzhun|BOSS直聘|来自BOSS直聘", "")
                .replaceAll("[\\u200b-\\u200d\\uFEFF]", "")
                .replaceAll("<[^>]+>", "") // 如果有HTML标签就用
                .replaceAll("\\s+", " ")
                .trim();
    }
}
