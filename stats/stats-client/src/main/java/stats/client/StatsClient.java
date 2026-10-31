package stats.client;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import stats.dto.EndpointHitDto;
import stats.dto.ViewStatsDto;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Slf4j
@Component
public class StatsClient {

    private static final DateTimeFormatter DATE_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private static final int MAX_ATTEMPTS = 3;
    private static final long RETRY_DELAY_MS = 1000L;

    private final DiscoveryClient discoveryClient;
    private final String statsServiceId;
    private final String appName;

    public StatsClient(DiscoveryClient discoveryClient,
                       @Value("${client.stats-service-id:stats-server}") String statsServiceId,
                       @Value("${client.app-name}") String appName) {
        this.discoveryClient = discoveryClient;
        this.statsServiceId = statsServiceId;
        this.appName = appName;
    }

    private RestClient restClient() {
        ServiceInstance instance = null;
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            List<ServiceInstance> instances = discoveryClient.getInstances(statsServiceId);
            if (!instances.isEmpty()) {
                instance = instances.getFirst();
                break;
            }
            if (attempt < MAX_ATTEMPTS) {
                try {
                    Thread.sleep(RETRY_DELAY_MS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }
        if (instance == null) {
            throw new IllegalStateException(
                    "Сервис статистики '" + statsServiceId + "' не найден в службе обнаружения");
        }
        return RestClient.builder()
                .baseUrl(instance.getUri().toString())
                .build();
    }

    public void hit(String uri, String ip) {
        EndpointHitDto hitDto = new EndpointHitDto(
                appName,
                uri,
                ip,
                LocalDateTime.now()
        );

        try {
            restClient().post()
                    .uri("/hit")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(hitDto)
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientException | IllegalStateException e) {
            log.warn("Не удалось отправить статистику по uri {}: {}", uri, e.getMessage());
        }
    }

    public List<ViewStatsDto> getStats(LocalDateTime start, LocalDateTime end,
                                       List<String> uris, boolean unique) {
        try {
            ViewStatsDto[] result = restClient().get()
                    .uri(uriBuilder -> {
                        uriBuilder.path("/stats")
                                .queryParam("start", start.format(DATE_FORMATTER))
                                .queryParam("end", end.format(DATE_FORMATTER))
                                .queryParam("unique", unique);
                        if (uris != null && !uris.isEmpty()) {
                            uriBuilder.queryParam("uris", String.join(",", uris));
                        }
                        return uriBuilder.build();
                    })
                    .retrieve()
                    .body(ViewStatsDto[].class);

            return result == null ? List.of() : List.of(result);
        } catch (RestClientException | IllegalStateException e) {
            log.warn("Не удалось получить статистику: {}", e.getMessage());
            return List.of();
        }
    }
}