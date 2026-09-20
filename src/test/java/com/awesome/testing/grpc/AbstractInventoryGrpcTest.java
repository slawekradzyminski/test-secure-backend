package com.awesome.testing.grpc;

import com.awesome.testing.dto.user.Role;
import com.awesome.testing.endpoints.AbstractEcommerceTest;
import com.awesome.testing.grpc.proto.AdjustStockRequest;
import com.awesome.testing.grpc.proto.InventoryServiceGrpc;
import com.awesome.testing.service.InventoryService;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.Metadata;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.stub.MetadataUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.grpc.server.lifecycle.GrpcServerLifecycle;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static com.awesome.testing.factory.UserFactory.getRandomUserWithRoles;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@ActiveProfiles({"test", "grpc"})
@TestPropertySource(properties = {
        "spring.grpc.server.port=0",
        "spring.datasource.url=jdbc:h2:mem:grpctests;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE"
})
abstract class AbstractInventoryGrpcTest extends AbstractEcommerceTest {
    @Autowired
    private GrpcServerLifecycle server;
    @Autowired
    protected InventoryService inventory;
    @Autowired
    protected ObjectMapper mapper;
    protected ManagedChannel channel;

    @BeforeEach
    void connect() {
        channel = ManagedChannelBuilder.forAddress("127.0.0.1", server.getPort()).usePlaintext().build();
    }

    @AfterEach
    void disconnect() throws InterruptedException {
        channel.shutdownNow().awaitTermination(5, TimeUnit.SECONDS);
    }

    protected String admin() {
        return getToken(getRandomUserWithRoles(List.of(Role.ROLE_ADMIN)));
    }

    protected InventoryServiceGrpc.InventoryServiceBlockingStub stub(String token) {
        var client = InventoryServiceGrpc.newBlockingStub(channel).withDeadlineAfter(5, TimeUnit.SECONDS);
        if (token == null) {
            return client;
        }
        var headers = new Metadata();
        headers.put(Metadata.Key.of("authorization", Metadata.ASCII_STRING_MARSHALLER), "Bearer " + token);
        return client.withInterceptors(MetadataUtils.newAttachHeadersInterceptor(headers));
    }

    protected AdjustStockRequest adjustment(long productId, int delta) {
        return AdjustStockRequest.newBuilder().setProductId(productId).setDelta(delta)
                .setReason("Restock").setRequestId(UUID.randomUUID().toString()).build();
    }

    protected void assertStatus(Status.Code expected, Runnable call) {
        assertThatThrownBy(call::run).isInstanceOfSatisfying(StatusRuntimeException.class,
                failure -> assertThat(failure.getStatus().getCode()).isEqualTo(expected));
    }
}
