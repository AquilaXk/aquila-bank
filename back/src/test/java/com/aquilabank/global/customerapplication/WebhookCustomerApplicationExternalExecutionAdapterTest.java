package com.aquilabank.global.customerapplication;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withAccepted;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;

import com.aquilabank.domain.customerapplication.model.CustomerApplicationDetails;
import com.aquilabank.domain.customerapplication.model.CustomerApplicationExecutionResult;
import com.aquilabank.domain.customerapplication.model.CustomerApplicationStatus;
import com.aquilabank.domain.customerapplication.model.CustomerApplicationType;
import com.aquilabank.global.config.CustomerApplicationExternalExecutionProperties;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class WebhookCustomerApplicationExternalExecutionAdapterTest {

  @Test
  void dispatchesConfiguredExternalApplicationAsPendingExternal() {
    RestClient.Builder builder = RestClient.builder();
    MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
    server
        .expect(requestTo("https://provider.example/bill-payment"))
        .andExpect(method(HttpMethod.POST))
        .andExpect(header("Authorization", "Bearer provider-secret"))
        .andExpect(header("Idempotency-Key", "CSA-001:req-execute"))
        .andExpect(jsonPath("$.applicationReference").value("CSA-001"))
        .andExpect(jsonPath("$.applicationType").value("BILL_PAYMENT"))
        .andExpect(jsonPath("$.userId").value(7))
        .andExpect(jsonPath("$.payload.billerCode").value("GIRO"))
        .andRespond(withAccepted());

    WebhookCustomerApplicationExternalExecutionAdapter adapter =
        new WebhookCustomerApplicationExternalExecutionAdapter(builder.build(), properties());

    CustomerApplicationExecutionResult result =
        adapter.execute(
            details(CustomerApplicationType.BILL_PAYMENT), "ops-executor", "req-execute");

    assertThat(result.status()).isEqualTo(CustomerApplicationStatus.PENDING_EXTERNAL);
    assertThat(result.reason()).isEqualTo("EXTERNAL_EXECUTION_DISPATCHED");
    assertThat(result.payload()).containsEntry("providerType", "WEBHOOK");
    server.verify();
  }

  @Test
  void failsWithoutConfiguredEndpointForApplicationType() {
    RestClient.Builder builder = RestClient.builder();
    MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
    WebhookCustomerApplicationExternalExecutionAdapter adapter =
        new WebhookCustomerApplicationExternalExecutionAdapter(builder.build(), properties());

    CustomerApplicationExecutionResult result =
        adapter.execute(
            details(CustomerApplicationType.LOAN_APPLICATION), "ops-executor", "req-loan");

    assertThat(result.status()).isEqualTo(CustomerApplicationStatus.FAILED);
    assertThat(result.reason()).isEqualTo("EXTERNAL_EXECUTION_PROVIDER_URL_MISSING");
    server.verify();
  }

  @Test
  void failsWhenExternalExecutionIsDisabled() {
    RestClient.Builder builder = RestClient.builder();
    MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
    WebhookCustomerApplicationExternalExecutionAdapter adapter =
        new WebhookCustomerApplicationExternalExecutionAdapter(
            builder.build(),
            new CustomerApplicationExternalExecutionProperties(
                false, "Authorization", "", "Idempotency-Key", 3000, 5000, Map.of()));

    CustomerApplicationExecutionResult result =
        adapter.execute(
            details(CustomerApplicationType.BILL_PAYMENT), "ops-executor", "req-disabled");

    assertThat(result.status()).isEqualTo(CustomerApplicationStatus.FAILED);
    assertThat(result.reason()).isEqualTo("EXTERNAL_EXECUTION_NOT_CONFIGURED");
    server.verify();
  }

  @Test
  void failsWhenProviderDispatchReturnsError() {
    RestClient.Builder builder = RestClient.builder();
    MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
    server.expect(requestTo("https://provider.example/bill-payment")).andRespond(withServerError());
    WebhookCustomerApplicationExternalExecutionAdapter adapter =
        new WebhookCustomerApplicationExternalExecutionAdapter(builder.build(), properties());

    CustomerApplicationExecutionResult result =
        adapter.execute(
            details(CustomerApplicationType.BILL_PAYMENT), "ops-executor", "req-provider-error");

    assertThat(result.status()).isEqualTo(CustomerApplicationStatus.FAILED);
    assertThat(result.reason()).isEqualTo("EXTERNAL_EXECUTION_PROVIDER_DISPATCH_FAILED");
    assertThat(result.payload()).containsEntry("error", "InternalServerError");
    server.verify();
  }

  private static CustomerApplicationExternalExecutionProperties properties() {
    return new CustomerApplicationExternalExecutionProperties(
        true,
        "Authorization",
        "Bearer provider-secret",
        "Idempotency-Key",
        3000,
        5000,
        Map.of(
            CustomerApplicationType.BILL_PAYMENT,
            new CustomerApplicationExternalExecutionProperties.EndpointProperties(
                "https://provider.example/bill-payment")));
  }

  private static CustomerApplicationDetails details(CustomerApplicationType type) {
    Instant now = Instant.parse("2026-05-14T00:00:00Z");
    return new CustomerApplicationDetails(
        "CSA-001",
        7L,
        101L,
        type,
        CustomerApplicationStatus.APPROVED,
        true,
        now,
        Map.of(
            "billerCode",
            "GIRO",
            "paymentNumber",
            "1234567890",
            "amountMinor",
            50_000L,
            "currencyCode",
            "KRW"),
        now,
        now,
        null,
        null,
        null,
        Map.of());
  }
}
