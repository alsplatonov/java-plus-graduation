package stats;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.cloud.client.DefaultServiceInstance;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import stats.client.StatsClient;
import stats.dto.ViewStatsDto;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@TestPropertySource(properties = {
        "client.app-name=ewm-main-service"
})
class StatsClientIntegrationTest {

    @Autowired
    private Environment environment;

    @Test
    void clientShouldSendHitAndRetrieveStats() {
        int port = environment.getProperty("local.server.port", Integer.class);
        DiscoveryClient discoveryClient = new StubDiscoveryClient(port);
        StatsClient client = new StatsClient(
                discoveryClient,
                "stats-server",
                "ewm-main-service"
        );

        LocalDateTime now = LocalDateTime.now();

        client.hit("/events/42", "127.0.0.1");

        List<ViewStatsDto> stats = client.getStats(
                now.minusMinutes(1),
                now.plusMinutes(1),
                List.of("/events/42"),
                false);

        assertThat(stats).hasSize(1);
        assertThat(stats.getFirst().getHits()).isEqualTo(1);
    }

    private static final class StubDiscoveryClient implements DiscoveryClient {

        private final int port;

        private StubDiscoveryClient(int port) {
            this.port = port;
        }

        @Override
        public String description() {
            return "stub";
        }

        @Override
        public List<ServiceInstance> getInstances(String serviceId) {
            return List.of(new DefaultServiceInstance(serviceId + ":" + port, serviceId,
                    "localhost", port, false));
        }

        @Override
        public List<String> getServices() {
            return List.of("stats-server");
        }
    }
}
