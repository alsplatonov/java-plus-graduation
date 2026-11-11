package ru.practicum.ewm.stats.collector;

import com.netflix.appinfo.ApplicationInfoManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.devh.boot.grpc.server.event.GrpcServerStartedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * gRPC-сервер слушает случайный порт (grpc.server.port: 0), а Eureka знает только HTTP-порт.
 * После старта gRPC-сервера публикуем его реальный порт в метаданных Eureka,
 * по которому его находит клиент с адресом discovery:///collector
 */

@Slf4j
@Component
@RequiredArgsConstructor
public class GrpcPortRegistrar {

    private final ApplicationInfoManager applicationInfoManager;

    @EventListener
    public void onGrpcServerStarted(GrpcServerStartedEvent event) {
        applicationInfoManager.registerAppMetadata(Map.of("gRPC_port", String.valueOf(event.getServer().getPort())));
        log.info("gRPC-порт {} опубликован в метаданных Eureka", event.getServer().getPort());
    }
}
