package com.awesome.testing.traffic.protocol;

import com.awesome.testing.dto.traffic.TrafficEventDto;
import com.awesome.testing.grpc.proto.InventoryServiceGrpc;
import com.awesome.testing.traffic.TrafficLogService;
import io.grpc.Context;
import io.grpc.Deadline;
import java.util.concurrent.Executors;
import io.grpc.Metadata;
import io.grpc.ServerCall;
import io.grpc.ServerCallHandler;
import io.grpc.Status;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GrpcTrafficInterceptorTest {
    @Test
    @SuppressWarnings("unchecked")
    void cancellationWithoutServerCloseIsRecordedAndStillReachesTheServiceListener() {
        // given
        var events = new ConcurrentLinkedQueue<TrafficEventDto>();
        var recorder = new ProtocolTrafficRecorder(mock(TrafficLogService.class), events, JsonMapper.builder().build());
        var interceptor = new GrpcTrafficInterceptor(recorder);
        var call = (ServerCall<Object, Object>) mock(ServerCall.class);
        when(call.getMethodDescriptor()).thenReturn((io.grpc.MethodDescriptor) InventoryServiceGrpc.getGetStockMethod());
        var headers = new Metadata();
        headers.put(Metadata.Key.of("x-client-session-id", Metadata.ASCII_STRING_MARSHALLER), UUID.randomUUID().toString());
        var next = (ServerCallHandler<Object, Object>) mock(ServerCallHandler.class);
        var serviceListener = (ServerCall.Listener<Object>) mock(ServerCall.Listener.class);
        when(next.startCall(any(), any())).thenReturn(serviceListener);

        // when
        interceptor.interceptCall(call, headers, next).onCancel();

        // then
        org.mockito.Mockito.verify(serviceListener).onCancel();
        assertThat(events).singleElement().satisfies(event -> {
            assertThat(event.getStatus()).isEqualTo(Status.Code.CANCELLED.value());
            assertThat(event.getProtocolDetails().outcome()).isEqualTo("ERROR");
        });
    }

    @Test
    @SuppressWarnings("unchecked")
    void healthMethodsRemainUncapturedAndKeepTheirListener() {
        // given
        var events = new ConcurrentLinkedQueue<TrafficEventDto>();
        var recorder = new ProtocolTrafficRecorder(mock(TrafficLogService.class), events, JsonMapper.builder().build());
        var interceptor = new GrpcTrafficInterceptor(recorder);
        var call = (ServerCall<Object, Object>) mock(ServerCall.class);
        when(call.getMethodDescriptor()).thenReturn((io.grpc.MethodDescriptor) io.grpc.health.v1.HealthGrpc.getCheckMethod());
        var headers = new Metadata();
        headers.put(Metadata.Key.of("x-client-session-id", Metadata.ASCII_STRING_MARSHALLER), UUID.randomUUID().toString());
        var next = (ServerCallHandler<Object, Object>) mock(ServerCallHandler.class);
        var serviceListener = (ServerCall.Listener<Object>) mock(ServerCall.Listener.class);
        when(next.startCall(call, headers)).thenReturn(serviceListener);

        // when
        var result = interceptor.interceptCall(call, headers, next);

        // then
        assertThat(result).isSameAs(serviceListener);
        assertThat(events).isEmpty();
    }

    @Test
    @SuppressWarnings("unchecked")
    void expiredDeadlineWinsOverTheLateServerSuccessAndIsRecordedOnce() {
        // given
        var events = new ConcurrentLinkedQueue<TrafficEventDto>();
        var recorder = new ProtocolTrafficRecorder(mock(TrafficLogService.class), events, JsonMapper.builder().build());
        var interceptor = new GrpcTrafficInterceptor(recorder);
        var call = (ServerCall<Object, Object>) mock(ServerCall.class);
        when(call.getMethodDescriptor()).thenReturn((io.grpc.MethodDescriptor) InventoryServiceGrpc.getGetStockMethod());
        when(call.isCancelled()).thenReturn(true);
        var headers = new Metadata();
        headers.put(Metadata.Key.of("x-client-session-id", Metadata.ASCII_STRING_MARSHALLER), UUID.randomUUID().toString());
        var next = (ServerCallHandler<Object, Object>) mock(ServerCallHandler.class);
        doAnswer(invocation -> {
            ServerCall<Object, Object> wrapped = invocation.getArgument(0);
            wrapped.close(Status.OK, new Metadata());
            return new ServerCall.Listener<>() { };
        }).when(next).startCall(any(), any());
        var scheduler = Executors.newSingleThreadScheduledExecutor();
        var context = Context.current().withDeadline(Deadline.after(-1, TimeUnit.SECONDS), scheduler);
        var previous = context.attach();

        // when
        try {
            interceptor.interceptCall(call, headers, next).onCancel();
        } finally {
            context.detach(previous);
            context.close();
            scheduler.shutdownNow();
        }

        // then
        assertThat(events).singleElement().satisfies(event -> {
            assertThat(event.getStatus()).isEqualTo(Status.Code.DEADLINE_EXCEEDED.value());
            assertThat(event.getProtocolDetails().codes()).containsExactly("DEADLINE_EXCEEDED");
        });
    }
}
