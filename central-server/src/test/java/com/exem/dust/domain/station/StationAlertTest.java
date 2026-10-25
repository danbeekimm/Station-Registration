package com.exem.dust.domain.station;

import com.exem.dust.domain.alert.AlertIssued;
import com.exem.dust.domain.alert.AlertRule;
import com.exem.dust.domain.measurement.Measurement;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class StationAlertTest {

    private static final String STATION = "111311";
    private static final LocalDateTime T0 = LocalDateTime.of(2023, 3, 1, 0, 0);

    @Test
    void gradesAreUniqueAndAscendingInSeverityOrder() {
        assertThat(AlertRule.BY_SEVERITY).extracting(AlertRule::grade).containsExactly(1, 2, 3, 4, 5);
    }

    @Test
    void thresholdIsInclusiveAndAlertStartsOnSecondHour() {
        var alerts = feed(pm10(150.0), pm10(150.0));

        assertThat(alerts.get(0)).isEmpty();
        assertThat(alerts.get(1)).contains(new AlertIssued(STATION, T0.plusHours(1), AlertRule.PM10_WATCH, 150.0, 2));
    }

    @Test
    void singleHourAboveThresholdDoesNotAlert() {
        assertThat(feed(pm10(200.0), pm10(100.0))).allSatisfy(alert -> assertThat(alert).isEmpty());
    }

    @Test
    void nullBreaksTheStreak() {
        var alerts = feed(pm10(200.0), pm10(null), pm10(200.0));

        assertThat(alerts).allSatisfy(alert -> assertThat(alert).isEmpty());
    }

    @Test
    void missingHourBreaksTheStreak() {
        var station = new Station(STATION);

        assertThat(station.accept(measurement(T0.plusHours(1), 200.0, null, null)).alert()).isEmpty();
        assertThat(station.accept(measurement(T0.plusHours(3), 200.0, null, null)).alert()).isEmpty();
        assertThat(station.accept(measurement(T0.plusHours(4), 200.0, null, null)).alert()).isPresent();
    }

    @Test
    void mostSevereRuleWinsWhenSeveralAreMet() {
        var alerts = feed(both(320.0, 160.0), both(320.0, 160.0));

        assertThat(alerts.get(1)).get().extracting(AlertIssued::rule).isEqualTo(AlertRule.PM25_WARNING);
    }

    @Test
    void escalatesFromWatchToWarningWhileKeepingEarlierRecords() {
        var alerts = feed(pm25(80.0), pm25(80.0), pm25(160.0), pm25(160.0));

        assertThat(alerts.get(1)).contains(new AlertIssued(STATION, T0.plusHours(1), AlertRule.PM25_WATCH, 80.0, 2));
        assertThat(alerts.get(2)).contains(new AlertIssued(STATION, T0.plusHours(2), AlertRule.PM25_WATCH, 160.0, 3));
        assertThat(alerts.get(3)).contains(new AlertIssued(STATION, T0.plusHours(3), AlertRule.PM25_WARNING, 160.0, 2));
    }

    @Test
    void recordsEveryHourWhileSustainedWithGrowingDuration() {
        var alerts = feed(pm10(160.0), pm10(160.0), pm10(160.0), pm10(160.0), pm10(160.0));

        assertThat(alerts.get(0)).isEmpty();
        assertThat(alerts.subList(1, 5)).extracting(alert -> alert.orElseThrow().sustainedHours()).containsExactly(2, 3, 4, 5);
        assertThat(alerts.subList(1, 5)).extracting(alert -> alert.orElseThrow().rule()).containsOnly(AlertRule.PM10_WATCH);
    }

    @Test
    void ozoneWatchNeedsOnlyOneHour() {
        var station = new Station(STATION);

        var alert = station.accept(measurement(T0, 10.0, 5.0, 0.12)).alert();

        assertThat(alert).contains(new AlertIssued(STATION, T0, AlertRule.O3_WATCH, 0.12, 1));
    }

    @Test
    void rejectsMeasurementOfAnotherStation() {
        var station = new Station(STATION);

        org.assertj.core.api.Assertions.assertThatThrownBy(
                        () -> station.accept(new Measurement("111121", 1, T0, 1.0, 1.0, null)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static List<Optional<AlertIssued>> feed(Reading... readings) {
        var station = new Station(STATION);
        var alerts = new ArrayList<Optional<AlertIssued>>();
        for (int hour = 0; hour < readings.length; hour++) {
            var reading = readings[hour];
            alerts.add(station.accept(measurement(T0.plusHours(hour), reading.pm10(), reading.pm25(), null)).alert());
        }
        return alerts;
    }

    private static Measurement measurement(LocalDateTime measuredAt, Double pm10, Double pm25, Double o3) {
        return new Measurement(STATION, 1, measuredAt, pm10, pm25, o3);
    }

    private record Reading(Double pm10, Double pm25) {
    }

    private static Reading pm10(Double value) {
        return new Reading(value, 10.0);
    }

    private static Reading pm25(Double value) {
        return new Reading(30.0, value);
    }

    private static Reading both(Double pm10, Double pm25) {
        return new Reading(pm10, pm25);
    }
}
