package com.exem.dust.simulator;

/** 표준 출력 로그. 측정소 프로세스를 여러 개 띄워도 줄마다 [코드 이름] 접두어로 구분된다. */
final class StationConsole {

    private final String prefix;

    StationConsole(String stationCode, String stationName) {
        this.prefix = "[%s %s]".formatted(stationCode, stationName);
    }

    void info(String message) {
        System.out.println(prefix + " " + message);
    }

    void warn(String message) {
        System.out.println(prefix + " [경고] " + message);
    }

    void error(String message) {
        System.err.println(prefix + " [오류] " + message);
    }
}
