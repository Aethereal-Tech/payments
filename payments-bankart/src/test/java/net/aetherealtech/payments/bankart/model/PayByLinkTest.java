package net.aetherealtech.payments.bankart.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;

import net.aetherealtech.payments.bankart.internal.Json;

/** Pay-by-link, and the one place the two published sources describe different objects. */
class PayByLinkTest {

    private final ObjectMapper mapper = Json.mapper();

    @Test
    void theRequestMatchesBothSources() throws Exception {
        assertThat(mapper.writeValueAsString(new PayByLink(false, 5)))
                .isEqualTo("{\"sendByEmail\":false,\"expirationInMinute\":5}");
        assertThat(mapper.writeValueAsString(PayByLink.byEmail(null))).isEqualTo("{\"sendByEmail\":true}");
        assertThat(mapper.writeValueAsString(new PayByLink(null, null))).isEqualTo("{}");
        assertThatIllegalArgumentException()
                .isThrownBy(() -> PayByLink.withoutEmail(0))
                .withMessageContaining("expirationInMinute");
    }

    @Test
    @DisplayName("payByLinkData is modelled as the prose docs publish it, not as the stale schema declares it")
    void theResponseFollowsTheProseDocs() throws Exception {
        // The OpenAPI schema says {payByLink, sendViaEmail} booleans; the prose documentation's own
        // table and example say {expiresAt, cancelUrl}. Only the latter carries anything the caller
        // does not already know, and a connector still sending the schema's shape simply reads null.
        String prose = """
                {"success":true,"transactionStatus":"PENDING","uuid":"u-1",
                 "payByLinkData":{"expiresAt":"2023-08-21 14:13:02 UTC",
                                  "cancelUrl":"https://gateway.ixopay.com/api/v3/payByLink/6ba2b0e0d02ac342cc41/cancel"}}""";
        String asDeclared = """
                {"success":true,"transactionStatus":"PENDING","uuid":"u-1",
                 "payByLinkData":{"payByLink":true,"sendViaEmail":false}}""";

        PayByLinkData data = mapper.readValue(prose, StatusResponse.class).payByLinkData();
        assertThat(data.expiresAt()).isEqualTo("2023-08-21 14:13:02 UTC");
        assertThat(data.cancelUrl())
                .isEqualTo("https://gateway.ixopay.com/api/v3/payByLink/6ba2b0e0d02ac342cc41/cancel");

        PayByLinkData stale = mapper.readValue(asDeclared, StatusResponse.class).payByLinkData();
        assertThat(stale.expiresAt()).isNull();
        assertThat(stale.cancelUrl()).isNull();
    }

    @Test
    @DisplayName("a status response carries schedules as a map, not as the single scheduleData a callback sends")
    void statusCarriesAScheduleMap() throws Exception {
        String json = """
                {"success":true,"transactionStatus":"FINISHED","uuid":"u-1",
                 "schedules":{"SC-1":{"scheduleId":"SC-1","scheduleStatus":"ACTIVE",
                                      "scheduledAt":"2019-09-30T12:00:00+00:00"}}}""";

        StatusResponse response = mapper.readValue(json, StatusResponse.class);

        assertThat(response.schedules()).containsOnlyKeys("SC-1");
        assertThat(response.schedules().get("SC-1").scheduleStatus()).isEqualTo(ScheduleStatus.ACTIVE);
        assertThat(mapper.readValue("{\"success\":true}", StatusResponse.class).schedules()).isEmpty();
    }
}
