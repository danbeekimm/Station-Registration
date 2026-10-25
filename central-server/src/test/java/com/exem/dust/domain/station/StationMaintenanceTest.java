package com.exem.dust.domain.station;

import com.exem.dust.domain.maintenance.Equipment;
import com.exem.dust.domain.maintenance.MaintenanceChange;
import com.exem.dust.domain.maintenance.MaintenanceWindow;
import com.exem.dust.domain.measurement.Measurement;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class StationMaintenanceTest {

    private static final String STATION = "111311";
    private static final LocalDateTime T0 = LocalDateTime.of(2023, 3, 1, 3, 0);

    @Test
    void singleNullHourBecomesOneWindowEndedWhenMeasurementResumes() {
        var changes = feed(both(null, 10.0), both(20.0, 10.0));

        assertThat(changes).containsExactly(
                new MaintenanceChange.Started(new MaintenanceWindow(STATION, Equipment.PM10, T0, null, 1)),
                new MaintenanceChange.Ended(new MaintenanceWindow(STATION, Equipment.PM10, T0, T0.plusHours(1), 1)));
    }

    @Test
    void consecutiveNullsExtendTheSameWindow() {
        var changes = feed(both(null, 10.0), both(null, 10.0), both(null, 10.0), both(20.0, 10.0));

        assertThat(changes).extracting(change -> change.window().hours()).containsExactly(1, 2, 3, 3);
        assertThat(changes.getLast()).isInstanceOf(MaintenanceChange.Ended.class);
        assertThat(changes.getLast().window().endedAt()).isEqualTo(T0.plusHours(3));
        assertThat(changes).extracting(change -> change.window().startedAt()).containsOnly(T0);
    }

    @Test
    void nullNumberNullMakesTwoWindows() {
        var changes = feed(both(null, 10.0), both(20.0, 10.0), both(null, 10.0));

        assertThat(changes).filteredOn(MaintenanceChange.Started.class::isInstance)
                .extracting(change -> change.window().startedAt()).containsExactly(T0, T0.plusHours(2));
    }

    @Test
    void equipmentIsTrackedSeparately() {
        var changes = feed(both(null, 10.0), both(null, null), both(20.0, 10.0));

        assertThat(changes).filteredOn(change -> change.window().equipment() == Equipment.PM10)
                .extracting(change -> change.window().hours()).containsExactly(1, 2, 2);
        assertThat(changes).filteredOn(change -> change.window().equipment() == Equipment.PM25)
                .containsExactly(
                        new MaintenanceChange.Started(new MaintenanceWindow(STATION, Equipment.PM25, T0.plusHours(1), null, 1)),
                        new MaintenanceChange.Ended(new MaintenanceWindow(STATION, Equipment.PM25, T0.plusHours(1), T0.plusHours(2), 1)));
    }

    @Test
    void trailingNullLeavesWindowOpen() {
        var changes = feed(both(20.0, 10.0), both(null, 10.0), both(null, 10.0));

        assertThat(changes.getLast()).isInstanceOf(MaintenanceChange.Extended.class);
        assertThat(changes.getLast().window().isOpen()).isTrue();
    }

    @Test
    void missingHourClosesOpenWindowAtLastNullPlusOneHour() {
        var station = new Station(STATION);
        station.accept(measurement(T0, null, 10.0));
        station.accept(measurement(T0.plusHours(1), null, 10.0));

        var changes = station.accept(measurement(T0.plusHours(3), 20.0, 10.0)).maintenanceChanges();

        assertThat(changes).containsExactly(
                new MaintenanceChange.Ended(new MaintenanceWindow(STATION, Equipment.PM10, T0, T0.plusHours(2), 2)));
    }

    @Test
    void missingHourFollowedByNullStartsNewWindow() {
        var station = new Station(STATION);
        station.accept(measurement(T0, null, 10.0));

        var changes = station.accept(measurement(T0.plusHours(2), null, 10.0)).maintenanceChanges();

        assertThat(changes).containsExactly(
                new MaintenanceChange.Ended(new MaintenanceWindow(STATION, Equipment.PM10, T0, T0.plusHours(1), 1)),
                new MaintenanceChange.Started(new MaintenanceWindow(STATION, Equipment.PM10, T0.plusHours(2), null, 1)));
    }

    @Test
    void replayingStoredMeasurementsRestoresOpenWindowSoNextNullExtendsIt() {
        var restored = new Station(STATION);
        restored.accept(measurement(T0, null, 10.0));
        restored.accept(measurement(T0.plusHours(1), null, 10.0));

        var afterReconnect = restored.accept(measurement(T0.plusHours(2), null, 10.0)).maintenanceChanges();

        assertThat(afterReconnect).singleElement().isInstanceOf(MaintenanceChange.Extended.class);
        assertThat(afterReconnect.getFirst().window().hours()).isEqualTo(3);
    }

    private static List<MaintenanceChange> feed(Reading... readings) {
        var station = new Station(STATION);
        var changes = new ArrayList<MaintenanceChange>();
        for (int hour = 0; hour < readings.length; hour++) {
            changes.addAll(station.accept(measurement(T0.plusHours(hour), readings[hour].pm10(), readings[hour].pm25())).maintenanceChanges());
        }
        return changes;
    }

    private static Measurement measurement(LocalDateTime measuredAt, Double pm10, Double pm25) {
        return new Measurement(STATION, 1, measuredAt, pm10, pm25, null);
    }

    private record Reading(Double pm10, Double pm25) {
    }

    private static Reading both(Double pm10, Double pm25) {
        return new Reading(pm10, pm25);
    }
}
