package co.com.webgen.web.starter.apigateway.webgenapigateway.filters;

import co.com.webgen.web.starter.apigateway.webgenapigateway.models.TokenValidationModel;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;


@Component
public class TokenFilter extends AbstractGatewayFilterFactory<Object> {

    private final WebClient.Builder webClientBuilder;
    private WebClient webClient;

    @Value("${spring.cloud.gateway.routes[0].uri}")
    private String authBase;

    public TokenFilter(WebClient.Builder webClientBuilder) {
        this.webClientBuilder = webClientBuilder;
    }

    @PostConstruct
    private void init() {
        this.webClient = webClientBuilder.baseUrl(authBase).build();
    }

    @Override
    public GatewayFilter apply(Object config) {
        return (exchange, chain) -> {
            String authorizationHeader = exchange.getRequest().getHeaders().getFirst("Authorization");
            if (authorizationHeader != null && authorizationHeader.startsWith("Bearer ")) {
                return webClient.get()
                        .uri("/validation")
                        .header(HttpHeaders.AUTHORIZATION, authorizationHeader)
                        .retrieve()
                        .bodyToMono(Boolean.class)
                        .flatMap(response -> chain.filter(exchange))
                        .onErrorResume(Exception.class, ex -> handleRejectResponse(exchange, new TokenValidationModel(false, "Unauthorized")));
            } else {
                TokenValidationModel tokenStatus = new TokenValidationModel(false, "Unauthorized");
                return handleRejectResponse(exchange, tokenStatus);
            }
        };
    }

    private Mono<Void> handleRejectResponse(ServerWebExchange exchange, TokenValidationModel tokenStatus) {
        exchange.getResponse().getHeaders().add("Content-Type", "application/json");
        exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
        return exchange.getResponse().writeWith(Mono.just(exchange.getResponse()
                .bufferFactory()
                .wrap(tokenStatus.toString().getBytes(StandardCharsets.UTF_8))));
    }

}
