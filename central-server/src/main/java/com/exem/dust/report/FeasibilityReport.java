package com.exem.dust.report;

import com.exem.dust.common.data.DustDataException;
import com.exem.dust.common.data.DustRecordParser;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

/**
 * 발령 가능성 검토 리포트. 서버를 띄우지 않고 데이터 파일만 읽어 Markdown을 표준 출력에 쓴다.
 * <pre>
 *   ./gradlew -q feasibility                  # data/*.json 전체
 *   ./gradlew -q feasibility --args="data/노원구.json"
 * </pre>
 */
public final class FeasibilityReport {

    private FeasibilityReport() {
    }

    public static void main(String[] args) {
        List<Path> files;
        try {
            files = args.length == 0 ? defaultDataFiles() : Arrays.stream(args).map(Path::of).toList();
        } catch (IOException e) {
            System.err.println("data 디렉터리를 읽을 수 없습니다: " + e.getMessage());
            System.exit(1);
            return;
        }
        if (files.isEmpty()) {
            System.err.println("분석할 데이터 파일이 없습니다. data/*.json을 두거나 파일 경로를 인자로 주세요.");
            System.exit(1);
        }

        var analyzer = new FeasibilityAnalyzer(new DustRecordParser());
        try {
            var results = files.stream().map(analyzer::analyze).toList();
            System.out.println(new FeasibilityMarkdown().render(results));
        } catch (DustDataException e) {
            System.err.println("분석 실패: " + e.getMessage());
            System.exit(1);
        }
    }

    private static List<Path> defaultDataFiles() throws IOException {
        var dataDir = Path.of("data");
        if (!Files.isDirectory(dataDir)) {
            return List.of();
        }
        try (Stream<Path> paths = Files.list(dataDir)) {
            return paths.filter(path -> path.toString().endsWith(".json"))
                    .sorted(Comparator.comparing(path -> path.getFileName().toString()))
                    .toList();
        }
    }
}
