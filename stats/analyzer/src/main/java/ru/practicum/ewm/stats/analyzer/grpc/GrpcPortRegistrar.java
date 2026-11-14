package ru.practicum.ewm.stats.analyzer.grpc;

import com.netflix.appinfo.ApplicationInfoManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.devh.boot.grpc.server.event.GrpcServerStartedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Публикует порт gRPC-сервера в метаданных Eureka,
 * чтобы клиент с адресом discovery:///analyzer мог его найти
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class GrpcPortRegistrar {

    private final ApplicationInfoManager applicationInfoManager;

    @EventListener
    public void onGrpcServerStarted(GrpcServerStartedEvent event) {
        int port = event.getServer().getPort();
        applicationInfoManager.registerAppMetadata(Map.of("gRPC_port", String.valueOf(port)));
        log.info("gRPC-порт {} опубликован в метаданных Eureka", port);
    }
}
