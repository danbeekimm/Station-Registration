package com.exem.dust.tools;

import com.exem.dust.common.data.DustRecordParser;
import com.exem.dust.tools.dto.MockRow;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class MockStationGeneratorTest {

    private static final Path DATA = Path.of("..", "data");

    private final MockStationGenerator generator = new MockStationGenerator(new DustRecordParser());

    @TempDir
    Path tempDir;

    @Test
    void regeneratesCommittedEunpyeongFileByteForByte() throws Exception {
        var committed = DATA.resolve("은평구.json");
        assumeTrue(Files.exists(committed), "제공 데이터 파일이 없는 환경");

        var rows = generator.generate("111191", "은평구", sources());
        var regenerated = tempDir.resolve("은평구.json");
        Files.writeString(regenerated, MockStationGenerator.toProvidedFormatJson(rows), StandardCharsets.UTF_8);

        assertThat(Files.readAllBytes(regenerated)).isEqualTo(Files.readAllBytes(committed));
    }

    @Test
    void generatedRowsFollowProvidedShapeAndMaintenanceWindows() {
        assumeTrue(Files.exists(DATA.resolve("노원구.json")), "제공 데이터 파일이 없는 환경");

        var rows = generator.generate("111191", "은평구", sources());

        assertThat(rows).hasSize(744);
        assertThat(rows.getFirst().measuredAt()).isEqualTo(LocalDateTime.of(2023, 3, 1, 0, 0));
        assertThat(rows).allSatisfy(row -> {
            assertThat(row.stationCode()).isEqualTo("111191");
            assertThat(row.stationName()).isEqualTo("은평구");
        });
        assertThat(rows.stream().filter(row -> row.pm10() == null).map(MockRow::measuredAt))
                .containsExactly(LocalDateTime.of(2023, 3, 27, 2, 0), LocalDateTime.of(2023, 3, 27, 3, 0));
        assertThat(rows.stream().filter(row -> row.pm25() == null).count()).isEqualTo(5);
        assertThat(rows.stream().filter(row -> row.pm10() != null).map(row -> Integer.parseInt(row.pm10())))
                .allMatch(value -> value >= 1 && value <= 2000);
    }

    @Test
    void outputParsesBackWithTheSameParser() throws Exception {
        assumeTrue(Files.exists(DATA.resolve("노원구.json")), "제공 데이터 파일이 없는 환경");
        var rows = generator.generate("999999", "테스트구", sources());
        var written = Files.writeString(tempDir.resolve("mock.json"), MockStationGenerator.toProvidedFormatJson(rows), StandardCharsets.UTF_8);

        var parsed = new DustRecordParser().parse(written).single();

        assertThat(parsed.stationCode()).isEqualTo("999999");
        assertThat(parsed.records()).hasSize(744);
        assertThat(parsed.records().stream().filter(record -> record.pm25() == null).count()).isEqualTo(5);
    }

    private static List<Path> sources() {
        return List.of(DATA.resolve("노원구.json"), DATA.resolve("중구.json"), DATA.resolve("강서구.json"));
    }
}
