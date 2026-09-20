package com.awesome.testing.grpc;

import com.awesome.testing.controller.exception.CustomException;
import com.awesome.testing.security.JwtTokenProvider;
import io.grpc.Context;
import io.grpc.Contexts;
import io.grpc.Metadata;
import io.grpc.ServerCall;
import io.grpc.ServerCallHandler;
import io.grpc.ServerInterceptor;
import io.grpc.Status;
import io.jsonwebtoken.JwtException;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.grpc.server.GlobalServerInterceptor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.stereotype.Component;

/** Native calls use their own scoped identity; servlet authentication does not apply. */
@Component
@Profile("grpc")
@GlobalServerInterceptor
@RequiredArgsConstructor
public class InventoryGrpcAuthentication implements ServerInterceptor {
    private static final Metadata.Key<String> AUTHORIZATION = Metadata.Key.of("authorization", Metadata.ASCII_STRING_MARSHALLER);
    private static final Context.Key<Authentication> IDENTITY = Context.key("inventory-identity");
    private final JwtTokenProvider tokens;

    @Override
    public <Q, S> ServerCall.Listener<Q> interceptCall(ServerCall<Q, S> call, Metadata headers,
                                                      ServerCallHandler<Q, S> next) {
        Authentication identity;
        try {
            String authorization = headers.get(AUTHORIZATION);
            if (authorization == null || !authorization.startsWith("Bearer ")) {
                return reject(call, Status.UNAUTHENTICATED.withDescription("Bearer access token required"));
            }
            String token = authorization.substring(7);
            tokens.validateToken(token);
            identity = tokens.getAuthentication(token);
        } catch (CustomException | AuthenticationException | JwtException | IllegalArgumentException exception) {
            return reject(call, Status.UNAUTHENTICATED.withDescription("Invalid or expired access token"));
        }
        if (identity.getAuthorities().stream().noneMatch(authority -> "ROLE_ADMIN".equals(authority.getAuthority()))) {
            return reject(call, Status.PERMISSION_DENIED.withDescription("Administrator role required"));
        }
        return Contexts.interceptCall(Context.current().withValue(IDENTITY, identity), call, headers, next);
    }

    static String actor() {
        Authentication identity = IDENTITY.get();
        if (identity == null) {
            throw Status.UNAUTHENTICATED.withDescription("Bearer access token required").asRuntimeException();
        }
        return identity.getName();
    }

    private static <Q, S> ServerCall.Listener<Q> reject(ServerCall<Q, S> call, Status status) {
        call.close(status, new Metadata());
        return new ServerCall.Listener<>() { };
    }
}
