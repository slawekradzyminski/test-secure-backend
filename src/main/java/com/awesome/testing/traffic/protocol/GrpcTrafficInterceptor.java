package com.awesome.testing.traffic.protocol;

import io.grpc.ForwardingServerCall;
import io.grpc.ForwardingServerCallListener;
import io.grpc.Context;
import io.grpc.Metadata;
import io.grpc.ServerCall;
import io.grpc.ServerCallHandler;
import io.grpc.ServerInterceptor;
import io.grpc.Status;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.grpc.server.GlobalServerInterceptor;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@Profile("grpc")
@GlobalServerInterceptor
@Order(Ordered.HIGHEST_PRECEDENCE)
@RequiredArgsConstructor
public class GrpcTrafficInterceptor implements ServerInterceptor {
    private static final Metadata.Key<String> SESSION = Metadata.Key.of("x-client-session-id", Metadata.ASCII_STRING_MARSHALLER);
    private static final Metadata.Key<String> CORRELATION = Metadata.Key.of("x-correlation-id", Metadata.ASCII_STRING_MARSHALLER);
    private final ProtocolTrafficRecorder recorder;

    @Override
    public <Q, S> ServerCall.Listener<Q> interceptCall(ServerCall<Q, S> call, Metadata headers, ServerCallHandler<Q, S> next) {
        String method = call.getMethodDescriptor().getFullMethodName();
        if (!"awesome.inventory.v1.InventoryService".equals(call.getMethodDescriptor().getServiceName())) {
            return next.startCall(call, headers);
        }
        ProtocolTrafficRecorder.Trace trace = recorder.start(headers.get(SESSION));
        if (trace == null) {
            return next.startCall(call, headers);
        }
        Context context = Context.current();
        java.util.function.Consumer<Status> finish = status -> recorder.complete(trace, "GRPC", "/" + method,
                status.getCode().value(), call.getMethodDescriptor().getBareMethodName(),
                status.isOk() ? "SUCCESS" : "ERROR", List.of(status.getCode().name()));
        ServerCall.Listener<Q> listener = next.startCall(new ForwardingServerCall.SimpleForwardingServerCall<>(call) {
            @Override
            public void close(Status status, Metadata trailers) {
                trailers.put(CORRELATION, trace.id());
                super.close(status, trailers);
                finish.accept(call.isCancelled() ? cancelled(context) : status);
            }
        }, headers);
        return new ForwardingServerCallListener.SimpleForwardingServerCallListener<>(listener) {
            @Override
            public void onCancel() {
                try {
                    super.onCancel();
                } finally {
                    finish.accept(cancelled(context));
                }
            }
        };
    }

    private static Status cancelled(Context context) {
        return context.getDeadline() != null && context.getDeadline().isExpired() ? Status.DEADLINE_EXCEEDED : Status.CANCELLED;
    }
}
