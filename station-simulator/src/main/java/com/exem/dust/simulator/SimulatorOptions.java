package com.exem.dust.simulator;

import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;

/**
 * 실행 인자. {@code --key value}와 {@code --key=value} 두 형식을 받는다.
 *
 * @param stationCode 파일에 측정소가 여럿일 때 고르는 코드. 보통 파일당 측정소 하나라 생략한다
 * @param token       null이면 환경변수에서 찾는다({@link #resolveToken})
 */
record SimulatorOptions(
        Path file,
        String stationCode,
        String token,
        ServerEndpoint server,
        Duration interval,
        Duration ackTimeout,
        boolean help) {

    static final String USAGE = """
            사용법: station-simulator --file <측정소 JSON> [옵션]
              --file <path>            원본 데이터 파일 (필수)
              --station-code <code>    파일에 측정소가 여럿일 때 담당 측정소 코드
              --token <token>          인증 토큰. 생략 시 환경변수 STATION_TOKEN_<코드>, STATION_TOKEN 순으로 찾음
              --server <host:port>     중앙 서버 주소 (기본 localhost:9000)
              --interval-ms <n>        전송 간격 밀리초 (기본 1000). 빠른 검증은 10
              --ack-timeout-ms <n>     ACK 대기 시간 밀리초 (기본 3000)
              --help                   이 도움말
            종료 코드: 0 전송 완료 / 1 실행 불가 / 2 서버가 측정소를 거부(미등록·토큰 불일치)""";

    private static final ServerEndpoint DEFAULT_SERVER = new ServerEndpoint("localhost", 9000);
    private static final Duration DEFAULT_INTERVAL = Duration.ofMillis(1000);
    private static final Duration DEFAULT_ACK_TIMEOUT = Duration.ofMillis(3000);

    static SimulatorOptions parse(String[] args) {
        Path file = null;
        String stationCode = null;
        String token = null;
        var server = DEFAULT_SERVER;
        var interval = DEFAULT_INTERVAL;
        var ackTimeout = DEFAULT_ACK_TIMEOUT;
        boolean help = false;

        for (int index = 0; index < args.length; index++) {
            var argument = args[index];
            if (argument.equals("--help") || argument.equals("-h")) {
                help = true;
                continue;
            }
            String key;
            String value;
            int equalsAt = argument.indexOf('=');
            if (equalsAt > 0) {
                key = argument.substring(0, equalsAt);
                value = argument.substring(equalsAt + 1);
            } else {
                key = argument;
                if (index + 1 >= args.length) {
                    throw new IllegalArgumentException(key + " 옵션에 값이 없습니다");
                }
                value = args[++index];
            }
            switch (key) {
                case "--file" -> file = Path.of(value);
                case "--station-code" -> stationCode = value;
                case "--token" -> token = value;
                case "--server" -> server = ServerEndpoint.parse(value);
                case "--interval-ms" -> interval = Duration.ofMillis(parsePositive(key, value));
                case "--ack-timeout-ms" -> ackTimeout = Duration.ofMillis(parsePositive(key, value));
                default -> throw new IllegalArgumentException("알 수 없는 옵션: " + key);
            }
        }
        if (!help && file == null) {
            throw new IllegalArgumentException("--file 옵션은 필수입니다");
        }
        return new SimulatorOptions(file, stationCode, token, server, interval, ackTimeout, help);
    }

    /** 우선순위: --token 인자 → STATION_TOKEN_<측정소코드> → STATION_TOKEN. 빈 값은 없는 것으로 본다. */
    Optional<String> resolveToken(String resolvedStationCode, Map<String, String> environment) {
        if (hasText(token)) {
            return Optional.of(token);
        }
        var perStation = environment.get("STATION_TOKEN_" + resolvedStationCode);
        if (hasText(perStation)) {
            return Optional.of(perStation);
        }
        var shared = environment.get("STATION_TOKEN");
        return hasText(shared) ? Optional.of(shared) : Optional.empty();
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private static long parsePositive(String key, String value) {
        try {
            long parsed = Long.parseLong(value);
            if (parsed <= 0) {
                throw new IllegalArgumentException(key + " 값은 1 이상이어야 합니다: " + value);
            }
            return parsed;
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(key + " 값이 숫자가 아닙니다: " + value);
        }
    }
}
