package net.aetherealtech.payments.bankart;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.verification.LoggedRequest;

import net.aetherealtech.payments.bankart.model.ContinueScheduleRequest;
import net.aetherealtech.payments.bankart.model.Option;
import net.aetherealtech.payments.bankart.model.OptionsRequest;
import net.aetherealtech.payments.bankart.model.OptionsResponse;
import net.aetherealtech.payments.bankart.model.PayByLink;
import net.aetherealtech.payments.bankart.model.PaymentRequest;
import net.aetherealtech.payments.bankart.model.RegisterRequest;
import net.aetherealtech.payments.bankart.model.Schedule;
import net.aetherealtech.payments.bankart.model.SchedulePeriodUnit;
import net.aetherealtech.payments.bankart.model.ScheduleResponse;
import net.aetherealtech.payments.bankart.model.ScheduleStatus;
import net.aetherealtech.payments.bankart.model.StartScheduleRequest;
import net.aetherealtech.payments.bankart.model.UpdateScheduleRequest;

/** The schedule, options and incremental-authorization operations, asserted at the wire. */
class BankartClientScheduleTest extends GatewayTestBase {

    private static final String SCHEDULE_ID = "SC-1234-1234-1234-1234-1234-1234";

    private final ObjectMapper mapper = new ObjectMapper();

    private String schedulePath(String suffix) {
        return "/api/v3/schedule/" + API_KEY + "/" + suffix;
    }

    private void stub(String path, String body) {
        gateway.stubFor(post(urlEqualTo(path))
                .willReturn(aResponse().withStatus(200)
                        .withHeader("Content-Type", "application/json").withBody(body)));
    }

    @Test
    void startPostsToTheStartPathAndSignsIt() throws Exception {
        stub(schedulePath("start"), Fixtures.load("schedule-start-success.json"));

        ScheduleResponse response = client().startSchedule(
                StartScheduleRequest.builder("abcde01234abcde01234")
                        .every(6, SchedulePeriodUnit.MONTH, new BigDecimal("9.99"), "EUR")
                        .startDateTime("2019-09-30T01:00:00+00:00")
                        .build());

        LoggedRequest request = onlyRequest();
        assertThat(request.getUrl()).isEqualTo(schedulePath("start"));
        assertBasicAuth(request);
        assertSignature(request, "POST", schedulePath("start"));

        JsonNode body = mapper.readTree(request.getBodyAsString());
        assertThat(body.get("registrationUuid").asText()).isEqualTo("abcde01234abcde01234");
        assertThat(body.get("amount").asText()).isEqualTo("9.99");
        assertThat(body.get("amount").isTextual()).as("amounts are strings on the wire").isTrue();
        assertThat(body.get("periodLength").asInt()).isEqualTo(6);
        assertThat(body.get("periodUnit").asText()).isEqualTo("MONTH");

        assertThat(response.success()).isTrue();
        assertThat(response.scheduleId()).isEqualTo(SCHEDULE_ID);
        assertThat(response.oldStatus()).isEqualTo(ScheduleStatus.NON_EXISTING);
        assertThat(response.newStatus()).isEqualTo(ScheduleStatus.ACTIVE);
    }

    @Test
    void updateCarriesTheScheduleIdInThePath() throws Exception {
        String path = schedulePath(SCHEDULE_ID + "/update");
        stub(path, Fixtures.load("schedule-start-success.json"));

        client().updateSchedule(SCHEDULE_ID, UpdateScheduleRequest.ofAmount(new BigDecimal("19.99")));

        LoggedRequest request = onlyRequest();
        assertThat(request.getUrl()).isEqualTo(path);
        assertThat(request.getBodyAsString()).isEqualTo("{\"amount\":\"19.99\"}");
        assertSignature(request, "POST", path);
    }

    @Test
    void getIsTheOnlyScheduleOperationWithoutABody() {
        String path = schedulePath(SCHEDULE_ID + "/get");
        gateway.stubFor(get(urlEqualTo(path))
                .willReturn(aResponse().withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(Fixtures.load("schedule-get-active.json"))));

        ScheduleResponse response = client().showSchedule(SCHEDULE_ID);

        LoggedRequest request = onlyRequest();
        assertThat(request.getMethod().getName()).isEqualTo("GET");
        assertThat(request.getUrl()).isEqualTo(path);
        assertThat(request.getBodyAsString()).isEmpty();
        assertSignature(request, "GET", path);
        assertThat(response.newStatus()).isEqualTo(ScheduleStatus.ACTIVE);
        assertThat(response.scheduledAt()).isEqualTo("2019-09-30T12:00:00+00:00");
    }

    @Test
    @DisplayName("pause and cancel send an empty object, which is the only body their schema permits")
    void pauseAndCancelSendAnEmptyObject() {
        stub(schedulePath(SCHEDULE_ID + "/pause"), Fixtures.load("schedule-paused.json"));

        assertThat(client().pauseSchedule(SCHEDULE_ID).newStatus()).isEqualTo(ScheduleStatus.PAUSED);
        LoggedRequest paused = onlyRequest();
        assertThat(paused.getBodyAsString()).isEqualTo("{}");
        assertSignature(paused, "POST", schedulePath(SCHEDULE_ID + "/pause"));

        gateway.resetRequests();
        stub(schedulePath(SCHEDULE_ID + "/cancel"), Fixtures.load("schedule-cancelled.json"));

        assertThat(client().cancelSchedule(SCHEDULE_ID).newStatus()).isEqualTo(ScheduleStatus.CANCELLED);
        assertThat(onlyRequest().getBodyAsString()).isEqualTo("{}");
    }

    @Test
    void continuePostsTheDate() {
        String path = schedulePath(SCHEDULE_ID + "/continue");
        stub(path, Fixtures.load("schedule-continued.json"));

        ScheduleResponse response = client().continueSchedule(
                SCHEDULE_ID, ContinueScheduleRequest.at("2019-09-30T01:00:00+00:00"));

        assertThat(onlyRequest().getBodyAsString())
                .isEqualTo("{\"continueDateTime\":\"2019-09-30T01:00:00+00:00\"}");
        assertThat(response.oldStatus()).isEqualTo(ScheduleStatus.PAUSED);
        assertThat(response.newStatus()).isEqualTo(ScheduleStatus.ACTIVE);
    }

    @Test
    @DisplayName("a schedule failure comes back as a response, not as an exception")
    void scheduleFailuresAreNotRaised() {
        stub(schedulePath(SCHEDULE_ID + "/pause"), Fixtures.load("schedule-error-7070.json"));

        ScheduleResponse response = client().pauseSchedule(SCHEDULE_ID);

        // Throwing here would discard oldStatus/newStatus, which is the only thing that makes
        // "the status of the schedule is not valid for the requested operation" actionable.
        assertThat(response.isError()).isTrue();
        assertThat(response.errorCode()).isEqualTo(7070);
        assertThat(response.oldStatus()).isEqualTo(ScheduleStatus.PAUSED);
    }

    @Test
    void scheduleIdIsPathEncoded() {
        String path = schedulePath("SC%20WITH%20SPACES/get");
        gateway.stubFor(get(urlEqualTo(path))
                .willReturn(aResponse().withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(Fixtures.load("schedule-get-active.json"))));

        client().showSchedule("SC WITH SPACES");

        assertThat(onlyRequest().getUrl()).isEqualTo(path);
    }

    // ------------------------------------------------------------------ embedded schedule

    @Test
    @DisplayName("a debit-with-register carries the schedule inline")
    void debitCarriesAnEmbeddedSchedule() throws Exception {
        stubTransaction("debit", "{\"success\":true,\"uuid\":\"u\",\"returnType\":\"FINISHED\"}");

        client().debit(PaymentRequest.builder("tx-1", new BigDecimal("9.99"), "EUR")
                .withRegister(true)
                .schedule(Schedule.every(1, SchedulePeriodUnit.MONTH, new BigDecimal("9.99"), "EUR")
                        .startingAt("2019-09-30T01:00:00+00:00"))
                .payByLink(PayByLink.byEmail(5))
                .build());

        JsonNode body = mapper.readTree(onlyRequest().getBodyAsString());
        assertThat(body.get("withRegister").asBoolean()).isTrue();
        assertThat(body.get("schedule").get("periodUnit").asText()).isEqualTo("MONTH");
        assertThat(body.get("schedule").get("periodLength").asInt()).isEqualTo(1);
        assertThat(body.get("schedule").get("amount").asText()).isEqualTo("9.99");
        assertThat(body.get("schedule").get("startDateTime").asText()).isEqualTo("2019-09-30T01:00:00+00:00");
        assertThat(body.get("payByLink").get("sendByEmail").asBoolean()).isTrue();
        assertThat(body.get("payByLink").get("expirationInMinute").asInt()).isEqualTo(5);
    }

    @Test
    void registerCarriesAnEmbeddedScheduleToo() throws Exception {
        stubTransaction("register", "{\"success\":true,\"uuid\":\"u\",\"returnType\":\"FINISHED\"}");

        client().register(RegisterRequest.builder("tx-2")
                .schedule(Schedule.every(1, SchedulePeriodUnit.YEAR, new BigDecimal("99.00"), "EUR"))
                .payByLink(PayByLink.withoutEmail(null))
                .build());

        JsonNode body = mapper.readTree(onlyRequest().getBodyAsString());
        assertThat(body.get("schedule").get("periodUnit").asText()).isEqualTo("YEAR");
        assertThat(body.get("payByLink").get("sendByEmail").asBoolean()).isFalse();
        assertThat(body.get("payByLink").has("expirationInMinute")).isFalse();
    }

    // ------------------------------------------------------------------ incremental authorization

    @Test
    void incrementalAuthorizationPostsToItsOwnPath() throws Exception {
        stubTransaction("incrementalAuthorization",
                "{\"success\":true,\"uuid\":\"u-inc\",\"returnType\":\"FINISHED\"}");

        client().incrementalAuthorization(
                net.aetherealtech.payments.bankart.model.IncrementalAuthorizationRequest
                        .builder("tx-3", "bcdef23456bcdef23456", new BigDecimal("5.00"), "EUR")
                        .referenceSchemeLifecycleIdentifier("LC-1")
                        .additionalId1("a1").additionalId2("a2")
                        .extraData(Map.of("k", "v"))
                        .merchantMetaData("m")
                        .redirectUrls("https://e/s", "https://e/c", "https://e/e", "https://e/cb")
                        .description("Extra night")
                        .transactionIndicator(
                                net.aetherealtech.payments.bankart.model.TransactionIndicator.CARDONFILE)
                        .language("en")
                        .build());

        LoggedRequest request = onlyRequest();
        assertThat(request.getUrl()).isEqualTo(transactionPath("incrementalAuthorization"));
        assertSignature(request, "POST", transactionPath("incrementalAuthorization"));

        JsonNode body = mapper.readTree(request.getBodyAsString());
        assertThat(body.get("merchantTransactionId").asText()).isEqualTo("tx-3");
        assertThat(body.get("referenceUuid").asText()).isEqualTo("bcdef23456bcdef23456");
        assertThat(body.get("amount").asText()).isEqualTo("5.00");
        assertThat(body.get("referenceSchemeLifecycleIdentifier").asText()).isEqualTo("LC-1");
        assertThat(body.get("transactionIndicator").asText()).isEqualTo("CARDONFILE");
        assertThat(body.get("callbackUrl").asText()).isEqualTo("https://e/cb");
        assertThat(body.get("description").asText()).isEqualTo("Extra night");
        assertThat(body.get("language").asText()).isEqualTo("en");
        assertThat(body.get("extraData").get("k").asText()).isEqualTo("v");
    }

    @Test
    void incrementalAuthorizationHasAShorthand() throws Exception {
        stubTransaction("incrementalAuthorization",
                "{\"success\":true,\"uuid\":\"u-inc\",\"returnType\":\"FINISHED\"}");

        client().incrementalAuthorization(
                net.aetherealtech.payments.bankart.model.IncrementalAuthorizationRequest
                        .of("tx-4", "ref", new BigDecimal("1.00"), "EUR"));

        assertThat(mapper.readTree(onlyRequest().getBodyAsString()).has("description")).isFalse();
    }

    // ------------------------------------------------------------------ options

    @Test
    @DisplayName("the options identifier is a path segment, never a body field")
    void optionsNameIsAPathSegment() throws Exception {
        String path = "/api/v3/options/" + API_KEY + "/bankList";
        stub(path, Fixtures.load("options-map.json"));

        OptionsResponse response = client().options("bankList", OptionsRequest.of(
                Map.of("firstParam", "firstValue")));

        LoggedRequest request = onlyRequest();
        assertThat(request.getUrl()).isEqualTo(path);
        JsonNode body = mapper.readTree(request.getBodyAsString());
        assertThat(body.has("optionsName")).isFalse();
        assertThat(body.has("identifier")).isFalse();
        assertThat(body.get("parameters").get("firstParam").asText()).isEqualTo("firstValue");

        assertThat(response.success()).isTrue();
        assertThat(response.options()).containsExactly(
                new Option("bank1", "Bank One"),
                new Option("bank2", "Bank Two"),
                new Option("bank3", "Bank Three"),
                new Option("bank4", "Bank Four"));
    }

    @Test
    @DisplayName("options parses both the map the example sends and the array the schema declares")
    void optionsAcceptsEitherShape() {
        String path = "/api/v3/options/" + API_KEY + "/bankList";
        stub(path, Fixtures.load("options-array.json"));

        assertThat(client().options("bankList", OptionsRequest.empty()).options())
                .containsExactly(new Option("bank1", "Bank One"), new Option("bank2", "Bank Two"));
    }

    @Test
    void optionsReportsItsFailureUnderEitherSpelling() {
        String path = "/api/v3/options/" + API_KEY + "/bankList";
        stub(path, Fixtures.load("options-error.json"));

        OptionsResponse response = client().options("bankList", OptionsRequest.empty());

        assertThat(response.success()).isFalse();
        assertThat(response.options()).isEmpty();
        assertThat(response.failure()).contains("Given identifier 'someIdentifier' is invalid.");
        assertThat(new OptionsResponse(false, null, "declared spelling", null).failure())
                .contains("declared spelling");
        assertThat(new OptionsResponse(true, null, null, null).failure()).isEmpty();
    }

    @Test
    void optionsToleratesAnOptionsFieldThatIsNeitherShape() {
        String path = "/api/v3/options/" + API_KEY + "/bankList";
        stub(path, "{\"success\":true,\"options\":\"unexpected\"}");

        assertThat(client().options("bankList", OptionsRequest.empty()).options()).isEmpty();

        gateway.resetRequests();
        stub(path, "{\"success\":true,\"options\":null}");
        assertThat(client().options("bankList", OptionsRequest.empty()).options()).isEmpty();
    }
}
