package utils;

import boss.BossConfig;
import okhttp3.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.Map;
import java.util.concurrent.*;
import java.util.function.Consumer;

@Slf4j
public class HttpUtils {
    public static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");
    private static final int DEFAULT_TIMEOUT = 30;
    private static final int MAX_RETRY_COUNT = 3;
    
    private static final String AUTHORIZATION_HEADER = "Authorization";
    private static final String MACHINE_ID_HEADER = "X-Machine-ID";
    private static String authorizationToken = null;

    private static final OkHttpClient CLIENT = new OkHttpClient.Builder()
            .connectTimeout(DEFAULT_TIMEOUT, TimeUnit.SECONDS)
            .readTimeout(DEFAULT_TIMEOUT, TimeUnit.SECONDS)
            .writeTimeout(DEFAULT_TIMEOUT, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .addInterceptor(chain -> {
                Request original = chain.request();
                Request.Builder requestBuilder = original.newBuilder();
                
                // 添加机器ID请求头
                requestBuilder.header(MACHINE_ID_HEADER, getMachineId());
                
                if (authorizationToken != null) {
                    requestBuilder.header(AUTHORIZATION_HEADER, authorizationToken);
                } else {
                    requestBuilder.header(AUTHORIZATION_HEADER, BossConfig.getInstance().getVipKey());
                }

                Request request = requestBuilder.build();
                log.debug("发送请求: {}", request.url());
                Response response = chain.proceed(request);
                log.debug("收到响应: {}, 状态码: {}", request.url(), response.code());
                return response;
            })
            .build();

    private static final ExecutorService EXECUTOR = new ThreadPoolExecutor(
            5, 
            20,
            60L, 
            TimeUnit.SECONDS,
            new LinkedBlockingQueue<>(100),
            new ThreadPoolExecutor.CallerRunsPolicy()
    );

    /**
     * 发送异步GET请求
     *
     * @param url 请求URL
     * @param callback 回调函数
     */
    public static void getAsync(String url, Consumer<String> callback) {
        Request request = new Request.Builder()
                .url(url)
                .get()
                .build();

        CLIENT.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                log.error("请求失败: {}", url, e);
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                try (ResponseBody body = response.body()) {
                    if (body != null) {
                        String result = body.string();
                        callback.accept(result);
                    }
                }
            }
        });
    }

    /**
     * 发送同步GET请求
     *
     * @param url 请求URL
     * @return 响应结果
     */
    public static String get(String url) throws IOException {
        Request request = new Request.Builder()
                .url(url)
                .get()
                .build();

        try (Response response = CLIENT.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("请求失败: " + response);
            }
            ResponseBody body = response.body();
            return body != null ? body.string() : null;
        }
    }

    /**
     * 发送POST请求
     *
     * @param url 请求URL
     * @param json JSON请求体
     * @return 响应结果
     */
    public static String post(String url, String json) throws IOException {
        RequestBody body = RequestBody.create(json, JSON);
        Request request = new Request.Builder()
                .url(url)
                .post(body)
                .build();

        try (Response response = CLIENT.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("请求失败: " + response);
            }
            ResponseBody responseBody = response.body();
            return responseBody != null ? responseBody.string() : null;
        }
    }

    /**
     * 发送POST请求（对象自动序列化为JSON）
     *
     * @param url 请求URL
     * @param obj 请求对象
     * @return 响应结果
     */
    public static String postJson(String url, Object obj) throws IOException {
        String json = OBJECT_MAPPER.writeValueAsString(obj);
        return post(url, json);
    }

    /**
     * 发送表单POST请求
     *
     * @param url 请求URL
     * @param formData 表单数据
     * @return 响应结果
     */
    public static String postForm(String url, Map<String, String> formData) throws IOException {
        FormBody.Builder formBuilder = new FormBody.Builder();
        formData.forEach(formBuilder::add);

        Request request = new Request.Builder()
                .url(url)
                .post(formBuilder.build())
                .build();

        try (Response response = CLIENT.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("请求失败: " + response);
            }
            ResponseBody responseBody = response.body();
            return responseBody != null ? responseBody.string() : null;
        }
    }

    /**
     * 下载文件
     *
     * @param url 文件URL
     * @param callback 进度回调
     * @return 文件字节数组
     */
    public static byte[] download(String url, Consumer<Integer> callback) throws IOException {
        Request request = new Request.Builder()
                .url(url)
                .build();

        try (Response response = CLIENT.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("下载失败: " + response);
            }

            ResponseBody body = response.body();
            if (body == null) {
                throw new IOException("响应体为空");
            }

            long contentLength = body.contentLength();
            byte[] buffer = new byte[8192];
            int bytesRead;
            byte[] result = new byte[(int) contentLength];
            int offset = 0;

            while ((bytesRead = body.byteStream().read(buffer)) != -1) {
                System.arraycopy(buffer, 0, result, offset, bytesRead);
                offset += bytesRead;
                if (callback != null) {
                    callback.accept((int) ((offset * 100L) / contentLength));
                }
            }

            return result;
        }
    }

    /**
     * 关闭HTTP客户端和线程池
     */
    public static void shutdown() {
        EXECUTOR.shutdown();
        try {
            if (!EXECUTOR.awaitTermination(60, TimeUnit.SECONDS)) {
                EXECUTOR.shutdownNow();
            }
        } catch (InterruptedException e) {
            EXECUTOR.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    public static String getMachineId() {
        try {
            // 获取主机名
            String hostname = InetAddress.getLocalHost().getHostName();

            // 获取MAC地址的后6位
            String macSuffix = "unknown";
            InetAddress ip = InetAddress.getLocalHost();
            NetworkInterface ni = NetworkInterface.getByInetAddress(ip);
            if (ni != null && ni.getHardwareAddress() != null) {
                byte[] mac = ni.getHardwareAddress();
                StringBuilder sb = new StringBuilder();
                for (int i = mac.length - 3; i < mac.length; i++) { // 取最后3字节
                    sb.append(String.format("%02X", mac[i]));
                }
                macSuffix = sb.toString();
            }

            // JVM进程ID
            String jvmName = ManagementFactory.getRuntimeMXBean().getName(); // 1234@hostname
            String pid = jvmName.split("@")[0];

            return hostname + "-" + macSuffix + "-" + pid;

        } catch (Exception e) {
            return "unknown-machine";
        }
    }

    /**
     * 设置Authorization请求头的值
     *
     * @param token Authorization token值
     */
    public static void setAuthorizationToken(String token) {
        authorizationToken = token;
    }
} 