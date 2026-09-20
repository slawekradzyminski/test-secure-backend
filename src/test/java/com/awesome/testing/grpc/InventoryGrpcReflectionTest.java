package com.awesome.testing.grpc;

import com.awesome.testing.dto.user.Role;
import io.grpc.Metadata;
import io.grpc.Status;
import io.grpc.reflection.v1.ServerReflectionGrpc;
import io.grpc.reflection.v1.ServerReflectionRequest;
import io.grpc.reflection.v1.ServerReflectionResponse;
import io.grpc.stub.MetadataUtils;
import io.grpc.stub.StreamObserver;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestPropertySource;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static com.awesome.testing.factory.UserFactory.getRandomUserWithRoles;
import static org.assertj.core.api.Assertions.assertThat;

@TestPropertySource(properties = "spring.grpc.server.reflection.enabled=true")
class InventoryGrpcReflectionTest extends AbstractInventoryGrpcTest {
    @Test
    void optionalReflectionStillRequiresAnAdministratorToken() throws Exception {
        // given
        var token = admin();
        var customer = getToken(getRandomUserWithRoles(List.of(Role.ROLE_CLIENT)));

        // when
        var allowed = discover(token);
        var anonymous = discover(null);
        var forbidden = discover(customer);

        // then
        assertThat(allowed.status()).isEqualTo(Status.Code.OK);
        assertThat(allowed.services()).contains("awesome.inventory.v1.InventoryService", "grpc.health.v1.Health");
        assertThat(anonymous.status()).isEqualTo(Status.Code.UNAUTHENTICATED);
        assertThat(forbidden.status()).isEqualTo(Status.Code.PERMISSION_DENIED);
    }

    private Discovery discover(String token) throws Exception {
        var stub = ServerReflectionGrpc.newStub(channel).withDeadlineAfter(5, TimeUnit.SECONDS);
        if (token != null) {
            var headers = new Metadata();
            headers.put(Metadata.Key.of("authorization", Metadata.ASCII_STRING_MARSHALLER), "Bearer " + token);
            stub = stub.withInterceptors(MetadataUtils.newAttachHeadersInterceptor(headers));
        }
        var result = new CompletableFuture<Discovery>();
        var request = stub.serverReflectionInfo(new StreamObserver<ServerReflectionResponse>() {
            @Override
            public void onNext(ServerReflectionResponse response) {
                result.complete(new Discovery(Status.Code.OK, response.getListServicesResponse().getServiceList()
                        .stream().map(service -> service.getName()).toList()));
            }

            @Override
            public void onError(Throwable failure) {
                result.complete(new Discovery(Status.fromThrowable(failure).getCode(), List.of()));
            }

            @Override
            public void onCompleted() { }
        });
        request.onNext(ServerReflectionRequest.newBuilder().setListServices("").build());
        request.onCompleted();
        return result.get(5, TimeUnit.SECONDS);
    }

    private record Discovery(Status.Code status, List<String> services) { }
}
