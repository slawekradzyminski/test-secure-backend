package com.awesome.testing.grpc;

import com.awesome.testing.dto.traffic.TrafficEventDto;
import com.awesome.testing.dto.user.Role;
import com.awesome.testing.grpc.proto.GetStockRequest;
import com.awesome.testing.grpc.proto.InventoryServiceGrpc;
import com.awesome.testing.repository.TrafficLogRepository;
import com.awesome.testing.service.InventoryService;
import com.awesome.testing.traffic.TrafficPublisher;
import io.grpc.Metadata;
import io.grpc.Status;
import io.grpc.stub.MetadataUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import java.time.Duration;
import java.util.List;
import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static com.awesome.testing.factory.UserFactory.getRandomUserWithRoles;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.Mockito.doThrow;

@TestPropertySource(properties = "spring.datasource.url=jdbc:h2:mem:grpctraffic;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE")
class GrpcProtocolTrafficTest extends AbstractInventoryGrpcTest {
    @Autowired private TrafficLogRepository logs;
    @Autowired private Queue<TrafficEventDto> events;
    @MockitoBean private TrafficPublisher publisher;
    @MockitoSpyBean private InventoryService failingInventory;
    private static final Metadata.Key<String> CORRELATION = Metadata.Key.of("x-correlation-id", Metadata.ASCII_STRING_MARSHALLER);

    @BeforeEach
    void clearTraffic() {
        logs.deleteAll();
        events.clear();
    }

    @Test
    void capturesSuccessfulRpcMetadataAndCorrelationWithoutMessageBodies() {
        // given
        var product = setupProduct();
        var session = UUID.randomUUID().toString();
        var token = admin();
        var trailers = new AtomicReference<Metadata>();
        var client = sessionStub(token, session).withInterceptors(
                MetadataUtils.newCaptureMetadataInterceptor(new AtomicReference<>(), trailers));
        var request = adjustment(product.getId(), 3).toBuilder().setReason("PRIVATE_REASON").build();

        // when
        var result = client.adjustStock(request);
        await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> assertThat(protocolEvents()).hasSize(1));
        var event = protocolEvents().getFirst();
        var entry = logs.findByCorrelationId(event.getProtocolDetails().correlationId()).orElseThrow();

        // then
        assertThat(result.getQuantityAfter()).isEqualTo(product.getStockQuantity() + 3);
        assertThat(event.getClientSessionId()).isEqualTo(session);
        assertThat(event.getPath()).isEqualTo("/awesome.inventory.v1.InventoryService/AdjustStock");
        assertThat(event.getStatus()).isZero();
        assertThat(event.getProtocolDetails().operation()).isEqualTo("AdjustStock");
        assertThat(event.getProtocolDetails().outcome()).isEqualTo("SUCCESS");
        assertThat(event.getProtocolDetails().codes()).containsExactly("OK");
        assertThat(trailers.get().get(CORRELATION)).isEqualTo(entry.getCorrelationId());
        assertThat(entry.getRequestHeaders()).isEqualTo("{}");
        assertThat(entry.getRequestBody()).isEqualTo("{}");
        assertThat(entry.getResponseBody()).doesNotContain("PRIVATE_REASON", token, request.getRequestId());
    }

    @Test
    void capturesAuthenticationAndAuthorizationRejectionsBeforeTheServiceRuns() {
        // given
        var session = UUID.randomUUID().toString();
        var customer = getToken(getRandomUserWithRoles(List.of(Role.ROLE_CLIENT)));
        var request = GetStockRequest.newBuilder().setProductId(1).build();

        // when
        assertStatus(Status.Code.UNAUTHENTICATED, () -> sessionStub(null, session).getStock(request));
        assertStatus(Status.Code.PERMISSION_DENIED, () -> sessionStub(customer, session).getStock(request));
        await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> assertThat(protocolEvents()).hasSize(2));

        // then
        assertThat(protocolEvents()).extracting(TrafficEventDto::getStatus).containsExactlyInAnyOrder(16, 7);
        assertThat(protocolEvents()).allSatisfy(event -> {
            assertThat(event.getProtocolDetails().outcome()).isEqualTo("ERROR");
            assertThat(event.getProtocolDetails().codes()).containsExactly(Status.fromCodeValue(event.getStatus()).getCode().name());
        });
    }

    @Test
    void capturesOnlyCanonicalErrorCodesNeverRawStatusDescriptions() {
        // given
        var product = setupProduct();
        var client = sessionStub(admin(), UUID.randomUUID().toString());
        doThrow(Status.INTERNAL.withDescription("PRIVATE_STATUS_DESCRIPTION").asRuntimeException())
                .when(failingInventory).get(product.getId(), 10);

        // when
        assertStatus(Status.Code.INTERNAL, () -> client.getStock(GetStockRequest.newBuilder().setProductId(product.getId()).build()));
        await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> assertThat(protocolEvents()).hasSize(1));
        var event = protocolEvents().getFirst();

        // then
        assertThat(event.getProtocolDetails().codes()).containsExactly("INTERNAL");
        assertThat(logs.findByCorrelationId(event.getProtocolDetails().correlationId()).orElseThrow().getResponseBody())
                .doesNotContain("PRIVATE_STATUS_DESCRIPTION");
    }

    @Test
    void missingAndInvalidSessionsDoNotCaptureTrafficOrChangeRpcResults() {
        // given
        var product = setupProduct();
        var token = admin();
        var request = GetStockRequest.newBuilder().setProductId(product.getId()).build();

        // when
        var absent = stub(token).getStock(request);
        var invalid = sessionStub(token, "short").getStock(request);

        // then
        assertThat(absent.getAvailableQuantity()).isEqualTo(product.getStockQuantity());
        assertThat(invalid).isEqualTo(absent);
        assertThat(protocolEvents()).isEmpty();
        assertThat(logs.findAll().stream().filter(row -> "GRPC".equals(row.getMethod()))).isEmpty();
    }

    private InventoryServiceGrpc.InventoryServiceBlockingStub sessionStub(String token, String session) {
        var metadata = new Metadata();
        metadata.put(Metadata.Key.of("x-client-session-id", Metadata.ASCII_STRING_MARSHALLER), session);
        return stub(token).withInterceptors(MetadataUtils.newAttachHeadersInterceptor(metadata));
    }

    private List<TrafficEventDto> protocolEvents() {
        return events.stream().filter(event -> event.getProtocolDetails() != null).toList();
    }
}
