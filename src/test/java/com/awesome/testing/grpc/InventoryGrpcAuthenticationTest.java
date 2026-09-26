package com.awesome.testing.grpc;

import com.awesome.testing.security.JwtTokenProvider;
import io.grpc.Metadata;
import io.grpc.ServerCall;
import io.grpc.ServerCallHandler;
import io.grpc.Status;
import io.jsonwebtoken.JwtException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InventoryGrpcAuthenticationTest {

    private static final Metadata.Key<String> AUTHORIZATION =
            Metadata.Key.of("authorization", Metadata.ASCII_STRING_MARSHALLER);

    @Mock private JwtTokenProvider tokens;
    @Mock private ServerCall<String, String> call;
    @Mock private ServerCallHandler<String, String> next;

    @Test
    void missingBearerTokenClosesCallAndReturnsListener() {
        assertRejected(new Metadata(), Status.Code.UNAUTHENTICATED);
    }

    @Test
    void invalidBearerTokenClosesCallAndReturnsListener() {
        Metadata headers = headers("Bearer bad");
        when(tokens.validateToken("bad")).thenThrow(new JwtException("invalid"));

        assertRejected(headers, Status.Code.UNAUTHENTICATED);
    }

    @Test
    void clientTokenClosesCallAndReturnsListener() {
        Metadata headers = headers("Bearer client");
        when(tokens.getAuthentication("client")).thenReturn(new UsernamePasswordAuthenticationToken(
                "client", "", List.of(new SimpleGrantedAuthority("ROLE_CLIENT"))));

        assertRejected(headers, Status.Code.PERMISSION_DENIED);
    }

    private void assertRejected(Metadata headers, Status.Code expectedCode) {
        ServerCall.Listener<String> listener = new InventoryGrpcAuthentication(tokens)
                .interceptCall(call, headers, next);

        assertThat(listener).isNotNull();
        verify(call).close(argThat(status -> status.getCode() == expectedCode), any(Metadata.class));
        verifyNoInteractions(next);
    }

    private Metadata headers(String authorization) {
        Metadata headers = new Metadata();
        headers.put(AUTHORIZATION, authorization);
        return headers;
    }
}
