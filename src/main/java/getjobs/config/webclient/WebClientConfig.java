package getjobs.config.webclient;

import io.netty.channel.ChannelOption;
import io.netty.handler.timeout.ReadTimeoutHandler;
import io.netty.handler.timeout.WriteTimeoutHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ExchangeFilterFunction;
import org.springframework.web.reactive.function.client.ExchangeStrategies;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.netty.http.client.HttpClient;
import reactor.netty.resources.ConnectionProvider;

import java.time.Duration;

@Configuration
public class WebClientConfig {

        @Bean
        public WebClient defaultWebClient(WebClient.Builder builder) {
                // 连接池（Reactor Netty）
                ConnectionProvider provider = ConnectionProvider.builder("default-pool")
                                .maxConnections(500)
                                .pendingAcquireMaxCount(1000)
                                .pendingAcquireTimeout(Duration.ofSeconds(2))
                                .maxIdleTime(Duration.ofSeconds(30))
                                .maxLifeTime(Duration.ofMinutes(5))
                                .lifo() // 或 fifo()
                                .build();

                HttpClient httpClient = HttpClient.create(provider)
                                .compress(true) // 开启 gzip
                                .responseTimeout(Duration.ofSeconds(5)) // 整体响应超时（含首包）
                                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 2000) // 连接超时
                                .doOnConnected(conn -> conn
                                                .addHandlerLast(new ReadTimeoutHandler(5))
                                                .addHandlerLast(new WriteTimeoutHandler(5)))
                // .proxy(typeSpec ->
                // typeSpec.type(ProxyProvider.Proxy.HTTP).host("proxy").port(8080))
                // .secure(sslSpec -> ...) // 如需自签证书、信任库
                ;

                // 统一日志 / 追踪 / Header / 错误映射
                ExchangeFilterFunction logFilter = ExchangeFilterFunction.ofRequestProcessor(req -> {
                        // 简易日志，生产建议使用自定义 logger + 脱敏
                        return Mono.just(ClientRequest.from(req).build());
                });

                ExchangeStrategies strategies = ExchangeStrategies.builder()
                                .codecs(c -> {
                                        // 提升内存缓冲上限（默认 256KB）
                                        c.defaultCodecs().maxInMemorySize(8 * 1024 * 1024);
                                        // 如需自定义 ObjectMapper：
                                        // c.defaultCodecs().jackson2JsonDecoder(new Jackson2JsonDecoder(customMapper));
                                        // c.defaultCodecs().jackson2JsonEncoder(new Jackson2JsonEncoder(customMapper));
                                })
                                .build();

                return builder
                                .clientConnector(new ReactorClientHttpConnector(httpClient))
                                .exchangeStrategies(strategies)
                                .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                                .defaultHeader(HttpHeaders.USER_AGENT,
                                                "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/127.0.0.0 Safari/537.36")
                                .filter(logFilter)
                                .build();
        }
}