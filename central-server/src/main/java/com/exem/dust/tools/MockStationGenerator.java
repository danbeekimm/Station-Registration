package com.exem.dust.tools;

import com.exem.dust.common.data.DustDataException;
import com.exem.dust.common.data.DustRecord;
import com.exem.dust.common.data.DustRecordParser;
import com.exem.dust.common.data.StationData;
import com.exem.dust.domain.maintenance.Equipment;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

/**
 * 미등록 측정소 데모용 가상 데이터 생성기.
 * 제공된 측정소 파일들의 시간별 값을 평균한 뒤 ±10% 잡음을 섞어, 같은 형식(JSON 배열, 한글 키, 정수 문자열, 결측은 JSON null)의
 * 744행 파일을 만든다. 실제 관측값이 아니며 "코드 수정 없이 새 측정소를 등록하는 절차"를 보이기 위한 데이터다.
 * 시드가 고정된 MT19937을 쓰므로 같은 입력에서 항상 바이트 단위로 같은 파일이 나온다.
 * <pre>
 *   ./gradlew makeMockStation --code=111191 --name=은평구
 *   ./gradlew makeMockStation --code=111191 --name=은평구 --out=data/은평구.json --sources=data/노원구.json,data/중구.json,data/강서구.json
 * </pre>
 */
public final class MockStationGenerator {

    static final double NOISE_RATIO = 0.10;
    static final long RANDOM_SEED = 20230301L;
    static final List<Path> DEFAULT_SOURCES = List.of(Path.of("data/노원구.json"), Path.of("data/중구.json"), Path.of("data/강서구.json"));

    /** 점검(결측) 구간. 시작·종료 시각 포함. */
    record MaintenanceWindow(LocalDateTime start, LocalDateTime end, Set<Equipment> equipment) {
        /** 이 시각에 이 장비가 점검 중인가. */
        boolean covers(LocalDateTime at, Equipment target) {
            return !at.isBefore(start) && !at.isAfter(end) && equipment.contains(target);
        }
    }

    static final List<MaintenanceWindow> MAINTENANCE_WINDOWS = List.of(
            new MaintenanceWindow(LocalDateTime.of(2023, 3, 14, 9, 0), LocalDateTime.of(2023, 3, 14, 11, 0), EnumSet.of(Equipment.PM25)),
            new MaintenanceWindow(LocalDateTime.of(2023, 3, 27, 2, 0), LocalDateTime.of(2023, 3, 27, 3, 0), EnumSet.of(Equipment.PM10, Equipment.PM25)));

    /** 생성된 한 행. 농도는 제공 데이터처럼 정수 문자열이고 결측은 null이다. */
    record MockRow(LocalDateTime measuredAt, String stationName, String stationCode, String pm10, String pm25) {
    }

    private static final DateTimeFormatter HOUR_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH");

    private final DustRecordParser parser;

    public MockStationGenerator(DustRecordParser parser) {
        this.parser = parser;
    }

    /** 인자 해석 → 생성 → 파일 쓰기. 출력 디렉터리가 없으면 만든다. */
    public static void main(String[] args) {
        var options = parse(args);
        var code = require(options, "code");
        var name = require(options, "name");
        var out = Path.of(options.getOrDefault("out", "data/" + name + ".json"));
        var sources = options.containsKey("sources")
                ? Arrays.stream(options.get("sources").split(",")).map(String::strip).map(Path::of).toList()
                : DEFAULT_SOURCES;
        try {
            var rows = new MockStationGenerator(new DustRecordParser()).generate(code, name, sources);
            if (out.getParent() != null) {
                Files.createDirectories(out.getParent());
            }
            Files.writeString(out, toProvidedFormatJson(rows), StandardCharsets.UTF_8);
            System.out.printf("%s: %d행 생성 (%s %s)%n", out, rows.size(), name, code);
        } catch (DustDataException | IOException | IllegalStateException e) {
            System.err.println("생성 실패: " + e.getMessage());
            System.exit(1);
        }
    }

    /** 원본들을 같은 시각끼리 묶어 가상 행을 만든다. 행 수나 시각이 어긋나면 실패한다. */
    public List<MockRow> generate(String stationCode, String stationName, List<Path> sources) {
        List<StationData> stations = sources.stream().map(path -> parser.parse(path).single()).toList();
        int rowCount = stations.getFirst().records().size();
        if (stations.stream().anyMatch(station -> station.records().size() != rowCount)) {
            throw new IllegalStateException("원본 파일들의 행 수가 다릅니다: "
                    + stations.stream().map(station -> station.stationName() + "=" + station.records().size()).toList());
        }

        var random = new Mt19937(RANDOM_SEED);
        var rows = new ArrayList<MockRow>(rowCount);
        for (int hour = 0; hour < rowCount; hour++) {
            int index = hour;
            List<DustRecord> hourly = stations.stream().map(station -> station.records().get(index)).toList();
            var measuredAt = hourly.getFirst().measuredAt();
            if (hourly.stream().anyMatch(record -> !record.measuredAt().equals(measuredAt))) {
                throw new IllegalStateException("원본 파일들의 시각이 어긋납니다: " + measuredAt);
            }
            // 점검 시각은 난수를 소비하지 않는다. 그래야 구간을 바꿔도 다른 시각의 값이 그대로다
            var pm10 = inMaintenance(measuredAt, Equipment.PM10) ? null : blended(hourly, DustRecord::pm10, random);
            var pm25 = inMaintenance(measuredAt, Equipment.PM25) ? null : blended(hourly, DustRecord::pm25, random);
            rows.add(new MockRow(measuredAt, stationName, stationCode, pm10, pm25));
        }
        return List.copyOf(rows);
    }

    /** 설정된 점검 구간 중 하나라도 이 시각·장비를 덮는가. */
    private static boolean inMaintenance(LocalDateTime at, Equipment equipment) {
        return MAINTENANCE_WINDOWS.stream().anyMatch(window -> window.covers(at, equipment));
    }

    /** 결측이 아닌 값들의 평균에 [0.9, 1.1) 배율을 곱해 정수로 반올림한다(짝수 반올림). 최솟값 1. */
    private static String blended(List<DustRecord> hourly, Function<DustRecord, Double> concentration, Mt19937 random) {
        double sum = 0.0;
        int count = 0;
        for (var record : hourly) {
            var value = concentration.apply(record);
            if (value != null) {
                sum += value;
                count++;
            }
        }
        if (count == 0) {
            return null;
        }
        double mean = sum / count;
        double noisy = mean * random.nextUniform(1 - NOISE_RATIO, 1 + NOISE_RATIO);
        return String.valueOf(Math.max(1L, (long) Math.rint(noisy)));
    }

    /**
     * 제공 파일과 같은 모양(2칸 들여쓰기, 키 순서, "key": value, 마지막 개행)으로 쓴다.
     * 라이브러리의 pretty printer 기본값과 미세하게 달라 비교가 어긋나지 않도록 형식을 직접 맞추고, 문자열 이스케이프만 Jackson에 맡긴다.
     */
    static String toProvidedFormatJson(List<MockRow> rows) {
        var mapper = JsonMapper.builder().build();
        Function<String, String> quoted = value -> value == null ? "null" : mapper.writeValueAsString(value);
        var out = new StringBuilder("[\n");
        for (int i = 0; i < rows.size(); i++) {
            var row = rows.get(i);
            out.append("  {\n")
                    .append("    \"날짜\": ").append(quoted.apply(HOUR_FORMAT.format(row.measuredAt()))).append(",\n")
                    .append("    \"측정소명\": ").append(quoted.apply(row.stationName())).append(",\n")
                    .append("    \"측정소코드\": ").append(quoted.apply(row.stationCode())).append(",\n")
                    .append("    \"PM10\": ").append(quoted.apply(row.pm10())).append(",\n")
                    .append("    \"PM2.5\": ").append(quoted.apply(row.pm25())).append('\n')
                    .append("  }").append(i + 1 < rows.size() ? ",\n" : "\n");
        }
        return out.append("]\n").toString();
    }

    /** --key=value 인자를 맵으로 바꾼다. 다른 형식은 사용법을 보여 주고 끝낸다. */
    private static Map<String, String> parse(String[] args) {
        var options = new HashMap<String, String>();
        for (var argument : args) {
            int equalsAt = argument.indexOf('=');
            if (!argument.startsWith("--") || equalsAt < 3) {
                fail("인자는 --key=value 형식이어야 합니다: " + argument);
            }
            options.put(argument.substring(2, equalsAt), argument.substring(equalsAt + 1));
        }
        return options;
    }

    /** 필수 옵션이 비어 있으면 사용법과 함께 끝낸다. */
    private static String require(Map<String, String> options, String key) {
        var value = options.get(key);
        if (value == null || value.isBlank()) {
            fail("--" + key + " 값이 필요합니다");
        }
        return value;
    }

    /** 오류와 사용법을 표준 오류에 쓰고 종료 코드 1로 끝낸다. */
    private static void fail(String message) {
        System.err.println(message);
        System.err.println("사용법: ./gradlew makeMockStation --code=<측정소코드> --name=<이름> [--out=<파일>] [--sources=<원본,...>]");
        System.exit(1);
    }
}
