package com.exem.dust.common.data;

import com.exem.dust.common.data.dto.RawDustRow;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * 제공 데이터(JSON 배열, 한글 키, 문자열 값)를 {@link DustFile}로 바꾼다.
 * <p>행 단위 문제(시각 형식 오류, 숫자가 아닌 농도, 같은 시각 중복)는 그 행만 건너뛰고 경고로 남긴다.
 * 파일 단위 문제(빈 파일, JSON 아님)는 {@link DustDataException}으로 끝낸다.
 */
public final class DustRecordParser {

    private static final TypeReference<List<RawDustRow>> ROW_LIST = new TypeReference<>() {
    };

    private final JsonMapper mapper = JsonMapper.builder()
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .build();

    /** 파일 전체를 읽어 측정소 코드별로 묶는다. 행 단위 문제는 경고로 남기고, 쓸 수 있는 행이 하나도 없으면 실패한다. */
    public DustFile parse(Path file) {
        var rows = readRows(file);
        var warnings = new ArrayList<String>();
        var stations = new LinkedHashMap<String, StationBuilder>();

        for (int index = 0; index < rows.size(); index++) {
            var row = rows.get(index);
            int lineNumber = index + 1;
            if (row.stationCode() == null || row.stationCode().isBlank()) {
                warnings.add("행 %d: 측정소코드가 없어 건너뜀".formatted(lineNumber));
                continue;
            }
            LocalDateTime measuredAt;
            Double pm10;
            Double pm25;
            try {
                measuredAt = parseMeasuredAt(row.measuredAt());
                pm10 = parseConcentration(row.pm10());
                pm25 = parseConcentration(row.pm25());
            } catch (DateTimeException | NumberFormatException e) {
                warnings.add("행 %d (%s): 값을 해석할 수 없어 건너뜀 - %s".formatted(lineNumber, row.measuredAt(), e.getMessage()));
                continue;
            }
            stations.computeIfAbsent(row.stationCode(), code -> new StationBuilder(code, row.stationName()))
                    .add(new DustRecord(measuredAt, pm10, pm25), lineNumber, warnings);
        }

        if (stations.isEmpty()) {
            throw new DustDataException(file + ": 사용할 수 있는 행이 없습니다");
        }
        var built = new LinkedHashMap<String, StationData>();
        stations.forEach((code, builder) -> built.put(code, builder.build()));
        return new DustFile(file, Map.copyOf(built), List.copyOf(warnings));
    }

    /** 파일을 원본 행 목록으로 바꾼다. 빈 파일과 JSON 배열이 아닌 입력은 여기서 걸러진다. */
    private List<RawDustRow> readRows(Path file) {
        byte[] content;
        try {
            content = Files.readAllBytes(file);
        } catch (IOException e) {
            throw new DustDataException(file + ": 파일을 읽을 수 없습니다", e);
        }
        if (new String(content, java.nio.charset.StandardCharsets.UTF_8).isBlank()) {
            throw new DustDataException(file + ": 빈 파일입니다");
        }
        try {
            return mapper.readValue(content, ROW_LIST);
        } catch (JacksonException e) {
            throw new DustDataException(file + ": JSON 배열 형식이 아닙니다 - " + e.getOriginalMessage(), e);
        }
    }

    /** "yyyy-MM-dd HH". 하루의 마지막 시각을 24시로 적는 공공데이터 관행이 있어 다음 날 00시로 정규화한다. */
    static LocalDateTime parseMeasuredAt(String raw) {
        if (raw == null) {
            throw new DateTimeException("날짜가 없습니다");
        }
        var parts = raw.strip().split(" ");
        if (parts.length != 2) {
            throw new DateTimeException("날짜 형식이 'yyyy-MM-dd HH'가 아닙니다: " + raw);
        }
        var date = LocalDate.parse(parts[0]);
        int hour = Integer.parseInt(parts[1]);
        return hour == 24 ? date.plusDays(1).atStartOfDay() : date.atTime(hour, 0);
    }

    /** JSON null, 빈 문자열, 공백, "-"는 결측(장비 점검)이다. 그 밖에 숫자가 아니면 NumberFormatException. */
    static Double parseConcentration(String raw) {
        if (raw == null) {
            return null;
        }
        var trimmed = raw.strip();
        if (trimmed.isEmpty() || trimmed.equals("-")) {
            return null;
        }
        return Double.valueOf(trimmed);
    }

    /** 측정소 하나의 행을 모아 시각순·중복 제거된 {@link StationData}를 만든다. */
    private static final class StationBuilder {
        private final String stationCode;
        private final String stationName;
        private final TreeMap<LocalDateTime, DustRecord> byTime = new TreeMap<>();

        StationBuilder(String stationCode, String stationName) {
            this.stationCode = stationCode;
            this.stationName = stationName;
        }

        /** 같은 시각이 다시 오면 먼저 온 행을 지키고 경고를 남긴다. */
        void add(DustRecord record, int lineNumber, List<String> warnings) {
            if (byTime.putIfAbsent(record.measuredAt(), record) != null) {
                warnings.add("행 %d (%s %s): 같은 시각이 다시 나와 뒤 행을 버림"
                        .formatted(lineNumber, stationCode, record.measuredAt()));
            }
        }

        /** 시각 오름차순으로 확정한다(TreeMap 순서). */
        StationData build() {
            return new StationData(stationCode, stationName, List.copyOf(byTime.values()));
        }
    }
}
