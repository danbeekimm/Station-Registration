package com.exem.dust.simulator;

import com.exem.dust.common.data.DustDataException;
import com.exem.dust.common.data.DustRecordParser;
import com.exem.dust.common.data.StationData;

/**
 * 측정소 시뮬레이터 진입점. 데이터 파일 하나를 읽어 중앙 서버로 1건씩 전송한다.
 * <p>종료 코드: 0 전송 완료, 1 실행 불가(인자·파일·토큰 문제), 2 서버가 측정소를 거부(미등록·토큰 불일치).
 */
public final class StationSimulator {

    private StationSimulator() {
    }

    public static void main(String[] args) {
        System.exit(run(args).code());
    }

    static ExitCode run(String[] args) {
        SimulatorOptions options;
        try {
            options = SimulatorOptions.parse(args);
        } catch (IllegalArgumentException e) {
            System.err.println("오류: " + e.getMessage());
            System.err.println(SimulatorOptions.USAGE);
            return ExitCode.FAILURE;
        }
        if (options.help()) {
            System.out.println(SimulatorOptions.USAGE);
            return ExitCode.SUCCESS;
        }

        StationData station;
        try {
            var dustFile = new DustRecordParser().parse(options.file());
            dustFile.warnings().forEach(warning -> System.err.println("경고: " + warning));
            station = options.stationCode() == null ? dustFile.single() : dustFile.station(options.stationCode());
        } catch (DustDataException e) {
            System.err.println("오류: " + e.getMessage());
            return ExitCode.FAILURE;
        }

        var token = options.resolveToken(station.stationCode(), System.getenv());
        if (token.isEmpty()) {
            System.err.printf("오류: 측정소 %s의 토큰이 없습니다. --token 또는 환경변수 STATION_TOKEN_%s를 지정하세요.%n",
                    station.stationCode(), station.stationCode());
            return ExitCode.FAILURE;
        }

        var console = new StationConsole(station.stationCode(), station.stationName());
        return new TransmissionLoop(station, token.get(), options, console).run();
    }
}
