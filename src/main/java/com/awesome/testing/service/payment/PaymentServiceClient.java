package com.awesome.testing.service.payment;

import com.awesome.testing.dto.payment.PaymentRefundRequestDto;
import com.awesome.testing.dto.payment.PaymentRefundResponseDto;
import com.awesome.testing.internal.InternalApi;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.time.Duration;

/**
 * Calls payment-service's internal API with the shared payment service token
 * ({@code internal.service-tokens.payment}, the token payment-service also
 * presents to the backend).
 */
@Slf4j
public class PaymentServiceClient {

    static final String REFUNDS_PATH = "/internal/v1/payments/refunds";

    private final RestClient restClient;
    private final String serviceToken;

    public PaymentServiceClient(String baseUrl, String serviceToken, Duration timeout) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(timeout);
        requestFactory.setReadTimeout(timeout);
        this.restClient = RestClient.builder().baseUrl(baseUrl).requestFactory(requestFactory).build();
        this.serviceToken = serviceToken == null ? "" : serviceToken;
    }

    public RefundDelivery refund(PaymentRefundRequestDto request) {
        try {
            PaymentRefundResponseDto response = restClient.post()
                    .uri(REFUNDS_PATH)
                    .header(InternalApi.TOKEN_HEADER, serviceToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve()
                    .body(PaymentRefundResponseDto.class);
            return RefundDelivery.refunded(response == null ? null : response.refundId());
        } catch (RestClientResponseException e) {
            return classify(e.getStatusCode(), e.getResponseBodyAsString());
        } catch (RestClientException e) {
            return RefundDelivery.withOutcome(RefundDelivery.Outcome.RETRY, e.getClass().getSimpleName() + ": " + e.getMessage());
        }
    }

    static RefundDelivery classify(HttpStatusCode status, String body) {
        String detail = "HTTP " + status.value() + (body == null || body.isBlank() ? "" : " " + body);
        return switch (status.value()) {
            case 404 -> RefundDelivery.withOutcome(RefundDelivery.Outcome.NO_CAPTURED_PAYMENT, detail);
            case 400, 409, 422 -> RefundDelivery.withOutcome(RefundDelivery.Outcome.REJECTED, detail);
            default -> RefundDelivery.withOutcome(RefundDelivery.Outcome.RETRY, detail);
        };
    }
}
