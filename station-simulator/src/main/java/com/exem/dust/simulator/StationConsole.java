package com.exem.dust.simulator;

/** 표준 출력 로그. 측정소 프로세스를 여러 개 띄워도 줄마다 [코드 이름] 접두어로 구분된다. */
final class StationConsole {

    private final String prefix;

    StationConsole(String stationCode, String stationName) {
        this.prefix = "[%s %s]".formatted(stationCode, stationName);
    }

    /** 진행 상황. 전송 1건마다 한 줄이 나온다. */
    void info(String message) {
        System.out.println(prefix + " " + message);
    }

    /** 복구 가능한 문제(재전송·재접속). 흐름 속에서 보이도록 표준 출력에 남긴다. */
    void warn(String message) {
        System.out.println(prefix + " [경고] " + message);
    }

    /** 실행을 끝내는 오류. 표준 오류로 보낸다. */
    void error(String message) {
        System.err.println(prefix + " [오류] " + message);
    }
}
