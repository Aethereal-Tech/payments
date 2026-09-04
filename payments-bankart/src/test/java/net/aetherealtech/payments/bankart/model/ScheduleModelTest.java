package net.aetherealtech.payments.bankart.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import java.math.BigDecimal;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;

import net.aetherealtech.payments.bankart.Fixtures;
import net.aetherealtech.payments.bankart.internal.Json;

/** The schedule vocabulary, against the specification's own examples including their contradictions. */
class ScheduleModelTest {

    private final ObjectMapper mapper = Json.mapper();

    @Test
    @DisplayName("NON-EXISTING parses, though the ScheduleStatus enum does not declare it")
    void undeclaredButObservedStatus() {
        // The /schedule/{apiKey}/start success example answers oldStatus: NON-EXISTING, a value the
        // spec's own ScheduleStatus enum omits. Degrading it to UNKNOWN would lose the one thing the
        // field says: that this schedule did not exist until this call.
        assertThat(ScheduleStatus.fromWire("NON-EXISTING")).isEqualTo(ScheduleStatus.NON_EXISTING);
        assertThat(ScheduleStatus.NON_EXISTING.wireValue()).isEqualTo("NON-EXISTING");
    }

    @Test
    void scheduleStatusDegradesForAnythingElse() {
        assertThat(ScheduleStatus.fromWire("ACTIVE")).isEqualTo(ScheduleStatus.ACTIVE);
        assertThat(ScheduleStatus.fromWire("paused")).isEqualTo(ScheduleStatus.PAUSED);
        assertThat(ScheduleStatus.fromWire("CANCELLED")).isEqualTo(ScheduleStatus.CANCELLED);
        assertThat(ScheduleStatus.fromWire("ERROR")).isEqualTo(ScheduleStatus.ERROR);
        assertThat(ScheduleStatus.fromWire("CREATE-PENDING")).isEqualTo(ScheduleStatus.CREATE_PENDING);
        assertThat(ScheduleStatus.fromWire("SUSPENDED-BY-ISSUER")).isEqualTo(ScheduleStatus.UNKNOWN);
        assertThat(ScheduleStatus.fromWire(null)).isEqualTo(ScheduleStatus.UNKNOWN);
        assertThat(ScheduleStatus.CREATE_PENDING.wireValue()).isEqualTo("CREATE-PENDING");
    }

    @Test
    void periodUnitHasFourValuesAndDegrades() {
        assertThat(SchedulePeriodUnit.fromWire("MONTH")).isEqualTo(SchedulePeriodUnit.MONTH);
        assertThat(SchedulePeriodUnit.fromWire("day")).isEqualTo(SchedulePeriodUnit.DAY);
        assertThat(SchedulePeriodUnit.fromWire("WEEK")).isEqualTo(SchedulePeriodUnit.WEEK);
        assertThat(SchedulePeriodUnit.fromWire("YEAR")).isEqualTo(SchedulePeriodUnit.YEAR);
        assertThat(SchedulePeriodUnit.fromWire("HOUR")).isEqualTo(SchedulePeriodUnit.UNKNOWN);
        assertThat(SchedulePeriodUnit.fromWire(null)).isEqualTo(SchedulePeriodUnit.UNKNOWN);
        assertThat(SchedulePeriodUnit.YEAR.wireValue()).isEqualTo("YEAR");
    }

    @Test
    @DisplayName("the error path parses, though the declared schema forbids its fields")
    void errorFieldsUndeclaredBySchema() {
        ScheduleResponse response = read("schedule-error-7070.json");

        assertThat(response.success()).isFalse();
        assertThat(response.isError()).isTrue();
        assertThat(response.errorCode()).isEqualTo(7070);
        assertThat(response.errorMessage()).isEqualTo(
                "The status of the schedule is not valid for the requested operation");
        assertThat(response.oldStatus()).isEqualTo(ScheduleStatus.PAUSED);
        assertThat(response.newStatus()).isEqualTo(ScheduleStatus.PAUSED);
    }

    @Test
    @DisplayName("registrationUuid is read although the schema names the field registrationId")
    void bothRegistrationSpellings() {
        ScheduleResponse fromExample = read("schedule-start-success.json");

        assertThat(fromExample.registrationUuid()).isEqualTo("abcde01234abcde01234");
        assertThat(fromExample.registrationId()).isNull();
        assertThat(fromExample.registration()).contains("abcde01234abcde01234");
        assertThat(fromExample.oldStatus()).isEqualTo(ScheduleStatus.NON_EXISTING);
        assertThat(fromExample.newStatus()).isEqualTo(ScheduleStatus.ACTIVE);
        assertThat(fromExample.scheduledAt()).isEqualTo("2019-09-30T12:00:00+00:00");

        ScheduleResponse asDeclared = new ScheduleResponse(true, "SC-1", "REG-1", null,
                ScheduleStatus.ACTIVE, ScheduleStatus.ACTIVE, null, null, null, null);
        assertThat(asDeclared.registration()).contains("REG-1");

        ScheduleResponse neither = new ScheduleResponse(true, "SC-1", null, null, null, null, null, null, null, null);
        assertThat(neither.registration()).isEmpty();
    }

    @Test
    void pauseAndCancelAnswerWithoutAScheduledAt() {
        assertThat(read("schedule-paused.json").newStatus()).isEqualTo(ScheduleStatus.PAUSED);
        assertThat(read("schedule-paused.json").scheduledAt()).isNull();
        assertThat(read("schedule-cancelled.json").newStatus()).isEqualTo(ScheduleStatus.CANCELLED);
        assertThat(read("schedule-continued.json").scheduledAt()).isEqualTo("2019-10-05T14:26:11+00:00");
        assertThat(read("schedule-error-7040.json").errorCode()).isEqualTo(7040);
    }

    @Test
    void startScheduleSerialisesTheDocumentedExampleBody() throws Exception {
        StartScheduleRequest request = StartScheduleRequest.builder("abcde01234abcde01234")
                .every(6, SchedulePeriodUnit.MONTH, new BigDecimal("9.99"), "EUR")
                .startDateTime("2019-09-30T01:00:00+00:00")
                .merchantMetaData("merchantRelevantData")
                .callbackUrl("https://example.com/callback")
                .build();

        assertThat(mapper.writeValueAsString(request))
                .contains("\"registrationUuid\":\"abcde01234abcde01234\"")
                .contains("\"amount\":\"9.99\"")
                .contains("\"currency\":\"EUR\"")
                .contains("\"periodLength\":6")
                .contains("\"periodUnit\":\"MONTH\"")
                .contains("\"startDateTime\":\"2019-09-30T01:00:00+00:00\"")
                .contains("\"merchantMetaData\":\"merchantRelevantData\"")
                .contains("\"callbackUrl\":\"https://example.com/callback\"");
    }

    @Test
    @DisplayName("an update body carries only what is being changed")
    void updateScheduleOmitsEverythingElse() throws Exception {
        assertThat(mapper.writeValueAsString(UpdateScheduleRequest.ofAmount(new BigDecimal("9.99"))))
                .isEqualTo("{\"amount\":\"9.99\"}");

        UpdateScheduleRequest full = UpdateScheduleRequest.builder()
                .registrationUuid("abcde01234abcde01234")
                .amount(new BigDecimal("19.99")).currency("EUR")
                .periodLength(1).periodUnit(SchedulePeriodUnit.YEAR)
                .startDateTime("2020-01-01T00:00:00+00:00")
                .merchantMetaData("m").callbackUrl("https://example.com/cb")
                .build();

        assertThat(mapper.writeValueAsString(full))
                .contains("\"periodUnit\":\"YEAR\"")
                .contains("\"registrationUuid\":\"abcde01234abcde01234\"")
                .contains("\"callbackUrl\":\"https://example.com/cb\"")
                .contains("\"merchantMetaData\":\"m\"")
                .contains("\"startDateTime\":\"2020-01-01T00:00:00+00:00\"");
    }

    @Test
    void requestsRejectWhatTheGatewayWouldRejectAtADistance() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> StartScheduleRequest.builder(" ").build())
                .withMessageContaining("registrationUuid");
        assertThatIllegalArgumentException()
                .isThrownBy(() -> StartScheduleRequest.builder("reg").amount(new BigDecimal("1.00001")).build());
        assertThatIllegalArgumentException()
                .isThrownBy(() -> UpdateScheduleRequest.ofAmount(new BigDecimal("1.00001")));
        assertThatIllegalArgumentException()
                .isThrownBy(() -> ContinueScheduleRequest.at(""))
                .withMessageContaining("continueDateTime");
    }

    @Test
    void continueScheduleCarriesOnlyItsDate() throws Exception {
        assertThat(mapper.writeValueAsString(ContinueScheduleRequest.at("2019-09-30T01:00:00+00:00")))
                .isEqualTo("{\"continueDateTime\":\"2019-09-30T01:00:00+00:00\"}");
    }

    @Test
    @DisplayName("the embedded Schedule requires the amount and cadence the standalone request may omit")
    void embeddedScheduleIsStricter() throws Exception {
        Schedule schedule = Schedule.every(1, SchedulePeriodUnit.MONTH, new BigDecimal("9.99"), "EUR")
                .startingAt("2019-09-30T01:00:00+00:00")
                .withCallbackUrl("https://example.com/callback")
                .withMerchantMetaData("m");

        assertThat(mapper.writeValueAsString(schedule))
                .contains("\"amount\":\"9.99\"")
                .contains("\"periodLength\":1")
                .contains("\"periodUnit\":\"MONTH\"")
                .contains("\"startDateTime\":\"2019-09-30T01:00:00+00:00\"")
                .contains("\"callbackUrl\":\"https://example.com/callback\"")
                .contains("\"merchantMetaData\":\"m\"");

        assertThatNullPointerException()
                .isThrownBy(() -> Schedule.every(1, SchedulePeriodUnit.MONTH, null, "EUR"));
        assertThatNullPointerException()
                .isThrownBy(() -> new Schedule(BigDecimal.ONE, "EUR", 1, null, null, null, null));
        assertThatNullPointerException()
                .isThrownBy(() -> new Schedule(BigDecimal.ONE, "EUR", null, SchedulePeriodUnit.DAY, null, null, null));
        assertThatIllegalArgumentException()
                .isThrownBy(() -> Schedule.every(1, SchedulePeriodUnit.MONTH, BigDecimal.ONE, ""));
        assertThatIllegalArgumentException()
                .isThrownBy(() -> Schedule.every(-1, SchedulePeriodUnit.MONTH, BigDecimal.ONE, "EUR"))
                .withMessageContaining("periodLength");
        assertThatIllegalArgumentException()
                .isThrownBy(() -> Schedule.every(1, SchedulePeriodUnit.MONTH, new BigDecimal("1.00001"), "EUR"));
    }

    @Test
    void scheduleDataReadsOffATransactionResponse() throws Exception {
        String json = """
                {
                  "success": true,
                  "uuid": "abcde12345abcde12345",
                  "returnType": "FINISHED",
                  "scheduleData": {
                    "scheduleId": "SC-1234-1234-1234-1234-1234-1234",
                    "scheduleStatus": "ACTIVE",
                    "scheduledAt": "2019-09-30T12:00:00+00:00",
                    "merchantMetaData": "merchantRelevantData"
                  }
                }""";

        ScheduleData data = mapper.readValue(json, TransactionResponse.class).scheduleData();

        assertThat(data.scheduleId()).isEqualTo("SC-1234-1234-1234-1234-1234-1234");
        assertThat(data.scheduleStatus()).isEqualTo(ScheduleStatus.ACTIVE);
        assertThat(data.scheduledAt()).isEqualTo("2019-09-30T12:00:00+00:00");
        assertThat(data.merchantMetaData()).isEqualTo("merchantRelevantData");
    }

    private ScheduleResponse read(String fixture) {
        try {
            return mapper.readValue(Fixtures.load(fixture), ScheduleResponse.class);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
