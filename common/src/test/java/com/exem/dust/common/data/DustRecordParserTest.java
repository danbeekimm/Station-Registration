package com.exem.dust.common.data;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class DustRecordParserTest {

    private final DustRecordParser parser = new DustRecordParser();

    @TempDir
    Path tempDir;

    @Test
    void parsesIntegerStringsAndJsonNull() {
        var file = write("""
                [
                  {"날짜": "2023-03-01 00", "측정소명": "노원구", "측정소코드": "111311", "PM10": "23", "PM2.5": "11"},
                  {"날짜": "2023-03-01 01", "측정소명": "노원구", "측정소코드": "111311", "PM10": null, "PM2.5": "12.5"}
                ]
                """);

        var station = parser.parse(file).single();

        assertThat(station.stationCode()).isEqualTo("111311");
        assertThat(station.stationName()).isEqualTo("노원구");
        assertThat(station.records()).containsExactly(
                new DustRecord(LocalDateTime.of(2023, 3, 1, 0, 0), 23.0, 11.0),
                new DustRecord(LocalDateTime.of(2023, 3, 1, 1, 0), null, 12.5));
    }

    @Test
    void treatsEmptyBlankAndDashAsMissing() {
        var file = write("""
                [
                  {"날짜": "2023-03-01 00", "측정소명": "중구", "측정소코드": "111121", "PM10": "", "PM2.5": "  "},
                  {"날짜": "2023-03-01 01", "측정소명": "중구", "측정소코드": "111121", "PM10": "-", "PM2.5": "7"}
                ]
                """);

        var records = parser.parse(file).single().records();

        assertThat(records).extracting(DustRecord::pm10).containsExactly(null, null);
        assertThat(records).extracting(DustRecord::pm25).containsExactly(null, 7.0);
    }

    @Test
    void skipsRowWithNonNumericValueAndWarns() {
        var file = write("""
                [
                  {"날짜": "2023-03-01 00", "측정소명": "중구", "측정소코드": "111121", "PM10": "abc", "PM2.5": "7"},
                  {"날짜": "2023-03-01 01", "측정소명": "중구", "측정소코드": "111121", "PM10": "30", "PM2.5": "7"}
                ]
                """);

        var parsed = parser.parse(file);

        assertThat(parsed.single().records()).hasSize(1);
        assertThat(parsed.warnings()).singleElement().asString().contains("행 1");
    }

    @Test
    void normalizesHour24ToNextMidnightAndSortsByTime() {
        var file = write("""
                [
                  {"날짜": "2023-03-01 24", "측정소명": "중구", "측정소코드": "111121", "PM10": "1", "PM2.5": "1"},
                  {"날짜": "2023-03-01 23", "측정소명": "중구", "측정소코드": "111121", "PM10": "2", "PM2.5": "2"}
                ]
                """);

        var records = parser.parse(file).single().records();

        assertThat(records).extracting(DustRecord::measuredAt).containsExactly(
                LocalDateTime.of(2023, 3, 1, 23, 0),
                LocalDateTime.of(2023, 3, 2, 0, 0));
    }

    @Test
    void keepsFirstRowWhenTimeIsDuplicated() {
        var file = write("""
                [
                  {"날짜": "2023-03-01 00", "측정소명": "중구", "측정소코드": "111121", "PM10": "10", "PM2.5": "1"},
                  {"날짜": "2023-03-01 00", "측정소명": "중구", "측정소코드": "111121", "PM10": "99", "PM2.5": "1"}
                ]
                """);

        var parsed = parser.parse(file);

        assertThat(parsed.single().records()).singleElement().extracting(DustRecord::pm10).isEqualTo(10.0);
        assertThat(parsed.warnings()).singleElement().asString().contains("같은 시각");
    }

    @Test
    void groupsMixedStationsByCode() {
        var file = write("""
                [
                  {"날짜": "2023-03-01 00", "측정소명": "중구", "측정소코드": "111121", "PM10": "10", "PM2.5": "1"},
                  {"날짜": "2023-03-01 00", "측정소명": "노원구", "측정소코드": "111311", "PM10": "20", "PM2.5": "2"}
                ]
                """);

        var parsed = parser.parse(file);

        assertThat(parsed.stations()).containsOnlyKeys("111121", "111311");
        assertThat(parsed.station("111311").stationName()).isEqualTo("노원구");
        assertThatThrownBy(parsed::single).isInstanceOf(DustDataException.class).hasMessageContaining("--station-code");
        assertThatThrownBy(() -> parsed.station("999999")).isInstanceOf(DustDataException.class);
    }

    @Test
    void rejectsEmptyFile() {
        assertThatThrownBy(() -> parser.parse(write("   \n")))
                .isInstanceOf(DustDataException.class).hasMessageContaining("빈 파일");
    }

    @Test
    void rejectsNonArrayJson() {
        assertThatThrownBy(() -> parser.parse(write("{\"foo\": 1}")))
                .isInstanceOf(DustDataException.class).hasMessageContaining("JSON 배열");
    }

    @Test
    void parsesProvidedNowonFileWhenPresent() {
        var provided = Path.of("..", "data", "노원구.json");
        assumeTrue(Files.exists(provided), "제공 데이터 파일이 없는 환경");

        var parsed = parser.parse(provided);
        var station = parsed.single();

        assertThat(station.stationCode()).isEqualTo("111311");
        assertThat(station.records()).hasSize(744);
        assertThat(station.records().getFirst().measuredAt()).isEqualTo(LocalDateTime.of(2023, 3, 1, 0, 0));
        assertThat(station.records().getLast().measuredAt()).isEqualTo(LocalDateTime.of(2023, 3, 31, 23, 0));
        assertThat(parsed.warnings()).isEmpty();
    }

    private Path write(String json) {
        try {
            return Files.writeString(tempDir.resolve("sample.json"), json);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }
}
